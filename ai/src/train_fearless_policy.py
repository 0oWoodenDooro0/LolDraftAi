"""
Training pipeline for Fearless Draft Transformer Policy Network.
Parses actual Oracle's Elixir match draft sequences and trains the model end-to-end
with Dynamic Action Masking, evaluating Top-1, Top-3, Top-5 accuracy.
"""

import os
import re
import json
import math
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.model_selection import train_test_split
from torch.utils.data import DataLoader

from fearless_transformer_policy import (
    FearlessTransformerPolicy,
    FearlessDraftDataset,
    compute_topk_accuracy,
)


def slugify(name):
    if not isinstance(name, str):
        return ""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def load_dataset_from_csv(csv_path, max_games=3000):
    print(f"Loading match data from {csv_path}...")
    df = pd.read_csv(
        csv_path,
        usecols=[
            "gameid", "league", "patch", "game", "side", "position",
            "playername", "teamname", "champion",
            "ban1", "ban2", "ban3", "ban4", "ban5",
        ],
        low_memory=False,
    )

    # Clean strings
    df["patch"] = df["patch"].fillna("16.01").astype(str)
    df["league"] = df["league"].fillna("LCK").astype(str)
    df["teamname"] = df["teamname"].fillna("UnknownTeam").astype(str)
    df["playername"] = df["playername"].fillna("UnknownPlayer").astype(str)
    df["champion"] = df["champion"].fillna("").astype(str)

    # Build vocabularies
    all_champions = sorted(list(set(df["champion"].apply(slugify).unique()) - {""}))
    champ2idx = {c: i + 1 for i, c in enumerate(all_champions)} # 1-indexed, 0 is padding
    idx2champ = {i + 1: c for i, c in enumerate(all_champions)}
    num_champions = len(champ2idx) + 1

    patches = sorted(list(df["patch"].unique()))
    patch2idx = {p: i for i, p in enumerate(patches)}

    leagues = sorted(list(df["league"].unique()))
    league2idx = {l: i for i, l in enumerate(leagues)}

    teams = sorted(list(df["teamname"].unique()))
    team2idx = {t: i for i, t in enumerate(teams)}

    players = sorted(list(df["playername"].unique()))
    player2idx = {p: i for i, p in enumerate(players)}

    print(f"Vocabularies built: {len(all_champions)} champions, {len(leagues)} leagues, {len(teams)} teams, {len(players)} players.")

    # Group by gameid
    games = df.groupby("gameid")
    samples = []

    count = 0
    for gameid, group in games:
        if count >= max_games:
            break

        blue_rows = group[group["side"] == "Blue"]
        red_rows = group[group["side"] == "Red"]

        if len(blue_rows) < 5 or len(red_rows) < 5:
            continue

        patch_val = group["patch"].iloc[0]
        league_val = group["league"].iloc[0]
        game_num = int(group["game"].iloc[0]) if pd.notnull(group["game"].iloc[0]) else 1

        b_team = blue_rows["teamname"].iloc[0]
        r_team = red_rows["teamname"].iloc[0]

        # Extract 5 roster players in order: top, jng, mid, bot, sup
        pos_order = ["top", "jng", "mid", "bot", "sup"]
        def get_roster(rows):
            r = []
            for pos in pos_order:
                matching = rows[rows["position"].str.lower() == pos]
                if len(matching) > 0:
                    r.append(player2idx.get(matching["playername"].iloc[0], 0))
                else:
                    r.append(0)
            return r

        blue_roster = get_roster(blue_rows)
        red_roster = get_roster(red_rows)

        # Extract Bans & Picks
        # Standard Pro BP Turn Order (20 steps):
        # Turns 1-6: Phase 1 Bans (B1, R1, B2, R2, B3, R3)
        # Turns 7-12: Phase 1 Picks (B1, R1, R2, B2, B3, R4)
        # Turns 13-16: Phase 2 Bans (R4, B4, R5, B5)
        # Turns 17-20: Phase 2 Picks (R4, B4, B5, R5)
        b_bans = [slugify(b) for b in blue_rows[["ban1", "ban2", "ban3", "ban4", "ban5"]].iloc[0] if pd.notnull(b)]
        r_bans = [slugify(b) for b in red_rows[["ban1", "ban2", "ban3", "ban4", "ban5"]].iloc[0] if pd.notnull(b)]
        b_picks = [slugify(c) for c in blue_rows["champion"] if c]
        r_picks = [slugify(c) for c in red_rows["champion"] if c]

        # Build turn sequence
        turns = []
        # Phase 1 Bans
        if len(b_bans) >= 1 and b_bans[0] in champ2idx: turns.append((1, 0, 0, b_bans[0])) # Step 1, Blue, Ban
        if len(r_bans) >= 1 and r_bans[0] in champ2idx: turns.append((2, 1, 0, r_bans[0]))
        if len(b_bans) >= 2 and b_bans[1] in champ2idx: turns.append((3, 0, 0, b_bans[1]))
        if len(r_bans) >= 2 and r_bans[1] in champ2idx: turns.append((4, 1, 0, r_bans[1]))
        if len(b_bans) >= 3 and b_bans[2] in champ2idx: turns.append((5, 0, 0, b_bans[2]))
        if len(r_bans) >= 3 and r_bans[2] in champ2idx: turns.append((6, 1, 0, r_bans[2]))

        # Phase 1 Picks
        if len(b_picks) >= 1 and b_picks[0] in champ2idx: turns.append((7, 0, 1, b_picks[0])) # Step 7, Blue, Pick
        if len(r_picks) >= 1 and r_picks[0] in champ2idx: turns.append((8, 1, 1, r_picks[0]))
        if len(r_picks) >= 2 and r_picks[1] in champ2idx: turns.append((9, 1, 1, r_picks[1]))
        if len(b_picks) >= 2 and b_picks[2] in champ2idx: turns.append((10, 0, 1, b_picks[2]))
        if len(b_picks) >= 3 and b_picks[3] in champ2idx: turns.append((11, 0, 1, b_picks[3]))
        if len(r_picks) >= 3 and r_picks[2] in champ2idx: turns.append((12, 1, 1, r_picks[2]))

        # Phase 2 Bans
        if len(r_bans) >= 4 and r_bans[3] in champ2idx: turns.append((13, 1, 0, r_bans[3]))
        if len(b_bans) >= 4 and b_bans[3] in champ2idx: turns.append((14, 0, 0, b_bans[3]))
        if len(r_bans) >= 5 and r_bans[4] in champ2idx: turns.append((15, 1, 0, r_bans[4]))
        if len(b_bans) >= 5 and b_bans[4] in champ2idx: turns.append((16, 0, 0, b_bans[4]))

        # Phase 2 Picks
        if len(r_picks) >= 4 and r_picks[3] in champ2idx: turns.append((17, 1, 1, r_picks[3]))
        if len(b_picks) >= 4 and b_picks[3] in champ2idx: turns.append((18, 0, 1, b_picks[3]))
        if len(b_picks) >= 5 and b_picks[4] in champ2idx: turns.append((19, 0, 1, b_picks[4]))
        if len(r_picks) >= 5 and r_picks[4] in champ2idx: turns.append((20, 1, 1, r_picks[4]))

        # Generate sample for each step
        used_champs = set()
        seq_champs = []
        for step_idx, side, is_pick, target_slug in turns:
            target_idx = champ2idx[target_slug]

            # Construct dynamic action mask
            mask = np.ones(num_champions, dtype=np.float32)
            mask[0] = 0.0 # 0 is padding, illegal
            for u in used_champs:
                if u in champ2idx:
                    mask[champ2idx[u]] = 0.0

            # Pad draft sequence up to 21 tokens
            padded_seq = (seq_champs + [0] * 21)[:21]

            samples.append({
                "patch_id": patch2idx.get(patch_val, 0),
                "region_id": league2idx.get(league_val, 0),
                "game_number": min(5, max(1, game_num)),
                "side_id": side,
                "step_index": min(20, max(1, step_idx)),
                "blue_team_id": team2idx.get(b_team, 0),
                "red_team_id": team2idx.get(r_team, 0),
                "blue_roster_ids": blue_roster,
                "red_roster_ids": red_roster,
                "draft_sequence": padded_seq,
                "action_mask": mask,
                "target_champion": target_idx,
            })

            used_champs.add(target_slug)
            seq_champs.append(target_idx)

        count += 1

    print(f"Loaded {len(samples)} training/validation step samples across {count} games.")
    meta = {
        "num_champions": num_champions,
        "num_patches": max(len(patch2idx) + 5, 30),
        "num_regions": max(len(league2idx) + 5, 10),
        "num_teams": max(len(team2idx) + 5, 100),
        "num_players": max(len(player2idx) + 5, 1000),
        "champ2idx": champ2idx,
        "idx2champ": idx2champ,
    }
    return samples, meta


def run():
    csv_path = "/workspace/data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv"
    if not os.path.exists(csv_path):
        csv_path = "data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv"

    samples, meta = load_dataset_from_csv(csv_path, max_games=1200)

    train_samples, val_samples = train_test_split(samples, test_size=0.15, random_state=42)
    print(f"Train samples: {len(train_samples)}, Validation samples: {len(val_samples)}")

    train_loader = DataLoader(FearlessDraftDataset(train_samples), batch_size=64, shuffle=True)
    val_loader = DataLoader(FearlessDraftDataset(val_samples), batch_size=64, shuffle=False)

    print("Initializing FearlessTransformerPolicy model...")
    model = FearlessTransformerPolicy(
        num_champions=meta["num_champions"],
        num_patches=meta["num_patches"],
        num_regions=meta["num_regions"],
        num_teams=meta["num_teams"],
        num_players=meta["num_players"],
        d_model=128,
        nhead=4,
        num_layers=2,
    )

    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=1e-4)
    criterion = nn.CrossEntropyLoss()

    print("\n================ STARTING TRAINING (Stage 2 Sequence Fine-Tuning) ================")
    num_epochs = 6
    for epoch in range(1, num_epochs + 1):
        model.train()
        total_loss = 0.0
        for batch in train_loader:
            optimizer.zero_grad()
            logits = model.forward_with_dynamic_mask(
                patch_id=batch["patch_id"],
                region_id=batch["region_id"],
                game_number=batch["game_number"],
                side_id=batch["side_id"],
                step_index=batch["step_index"],
                blue_team_id=batch["blue_team_id"],
                red_team_id=batch["red_team_id"],
                blue_roster_ids=batch["blue_roster_ids"],
                red_roster_ids=batch["red_roster_ids"],
                draft_sequence=batch["draft_sequence"],
                action_mask=batch["action_mask"],
            )
            loss = criterion(logits, batch["target_champion"])
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        # Validation
        model.eval()
        val_logits_list = []
        val_targets_list = []
        with torch.no_grad():
            for batch in val_loader:
                logits = model.forward_with_dynamic_mask(
                    patch_id=batch["patch_id"],
                    region_id=batch["region_id"],
                    game_number=batch["game_number"],
                    side_id=batch["side_id"],
                    step_index=batch["step_index"],
                    blue_team_id=batch["blue_team_id"],
                    red_team_id=batch["red_team_id"],
                    blue_roster_ids=batch["blue_roster_ids"],
                    red_roster_ids=batch["red_roster_ids"],
                    draft_sequence=batch["draft_sequence"],
                    action_mask=batch["action_mask"],
                )
                val_logits_list.append(logits)
                val_targets_list.append(batch["target_champion"])

        all_logits = torch.cat(val_logits_list, dim=0)
        all_targets = torch.cat(val_targets_list, dim=0)
        acc = compute_topk_accuracy(all_logits, all_targets, ks=(1, 3, 5))
        avg_loss = total_loss / max(1, len(train_loader))

        print(
            f"Epoch {epoch:02d}/{num_epochs:02d} | "
            f"Loss: {avg_loss:.4f} | "
            f"Val Top-1: {acc['Top-1']*100:.2f}% | "
            f"Val Top-3: {acc['Top-3']*100:.2f}% | "
            f"Val Top-5: {acc['Top-5']*100:.2f}%"
        )

    # Save model weights & meta
    save_path = "models/fearless_transformer_policy.pt"
    if not os.path.exists("models"):
        os.makedirs("models", exist_ok=True)
    torch.save(model.state_dict(), save_path)
    with open("models/fearless_meta.json", "w") as f:
        json.dump({
            "num_champions": meta["num_champions"],
            "champ2idx": meta["champ2idx"],
        }, f, indent=2)
    print(f"\nModel successfully saved to {save_path}!")


if __name__ == "__main__":
    run()
