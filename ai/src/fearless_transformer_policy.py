"""
Fearless Draft (全局 BP) AI Policy Network - Transformer Architecture
Implements the system specification:
1. Zero Human Bias: Entity Embeddings for Region, Team, and 10 Position-based Player IDs.
2. Pure Pre-Match Decisions: Zero in-game telemetry (no KDA, DPM, CSD@15, gold diff).
3. Dynamic Action Masking: Strictly sets logits of fearless_locked, current_bans, current_picks to -1e9.
4. Roster Agnostic: Dynamically splices 10 player ID embeddings by role position.
"""

import math
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader


class FearlessTransformerPolicy(nn.Module):
    """
    Transformer-based Decision Model for Fearless Draft Next-Action Prediction.
    """
    def __init__(
        self,
        num_champions=174,
        num_patches=30,
        num_regions=8,
        num_teams=80,
        num_players=600,
        d_model=128,
        nhead=4,
        num_layers=2,
        dim_feedforward=256,
        dropout=0.1,
    ):
        super().__init__()
        self.num_champions = num_champions
        self.d_model = d_model

        # 1. Environment / Context Embeddings
        self.patch_embed = nn.Embedding(num_patches, 32)
        self.region_embed = nn.Embedding(num_regions, 32)
        self.game_num_embed = nn.Embedding(6, 16) # 1..5
        self.side_embed = nn.Embedding(2, 16) # 0: Blue, 1: Red
        self.step_embed = nn.Embedding(21, 32) # 1..20

        context_dim = 32 + 32 + 16 + 16 + 32 # 128
        self.context_proj = nn.Linear(context_dim, d_model)

        # 2. Entity Identification Embeddings (Team & 10 Players Roster)
        self.team_embed = nn.Embedding(num_teams, 32)
        self.player_embed = nn.Embedding(num_players, 32)
        
        # 2 teams (64) + 10 players (320) = 384
        roster_dim = 32 * 2 + 32 * 10 
        self.roster_proj = nn.Linear(roster_dim, d_model)

        # 3. Sequence / Champion Embeddings (Champ2Vec)
        self.champ_embed = nn.Embedding(num_champions + 1, d_model, padding_idx=0) # 0 is empty slot
        self.pos_encoder = nn.Embedding(22, d_model)

        # 4. Multi-Head Attention / Transformer Decoder Layers
        decoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=nhead,
            dim_feedforward=dim_feedforward,
            dropout=dropout,
            batch_first=True,
        )
        self.transformer = nn.TransformerEncoder(decoder_layer, num_layers=num_layers)

        # 5. Output Projection
        self.head = nn.Sequential(
            nn.Linear(d_model * 3, 256),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(256, num_champions),
        )

    def forward(
        self,
        patch_id,
        region_id,
        game_number,
        side_id,
        step_index,
        blue_team_id,
        red_team_id,
        blue_roster_ids,  # [batch, 5] (top, jng, mid, bot, sup)
        red_roster_ids,   # [batch, 5] (top, jng, mid, bot, sup)
        draft_sequence,   # [batch, seq_len] (champion indices 1..174)
    ):
        batch_size = patch_id.size(0)

        # (a) Context Representation
        p = self.patch_embed(patch_id)
        r = self.region_embed(region_id)
        g = self.game_num_embed(game_number)
        s = self.side_embed(side_id)
        st = self.step_embed(step_index)
        ctx = torch.cat([p, r, g, s, st], dim=-1)
        ctx_rep = self.context_proj(ctx) # [batch, d_model]

        # (b) Entity / Roster Representation (Roster Agnostic)
        b_team = self.team_embed(blue_team_id)
        r_team = self.team_embed(red_team_id)
        b_players = self.player_embed(blue_roster_ids).view(batch_size, -1) # [batch, 160]
        r_players = self.player_embed(red_roster_ids).view(batch_size, -1) # [batch, 160]
        roster_all = torch.cat([b_team, r_team, b_players, r_players], dim=-1)
        roster_rep = self.roster_proj(roster_all) # [batch, d_model]

        # (c) Sequence Multi-Head Attention
        seq_len = draft_sequence.size(1)
        positions = torch.arange(seq_len, device=draft_sequence.device).unsqueeze(0).expand(batch_size, -1)
        seq_tokens = self.champ_embed(draft_sequence) + self.pos_encoder(positions)
        seq_attn = self.transformer(seq_tokens) # [batch, seq_len, d_model]
        seq_rep = seq_attn.mean(dim=1) # [batch, d_model]

        # (d) Fuse & Predict Raw Logits
        fused = torch.cat([ctx_rep, roster_rep, seq_rep], dim=-1) # [batch, d_model * 3]
        raw_logits = self.head(fused) # [batch, num_champions]
        return raw_logits

    def forward_with_dynamic_mask(
        self,
        patch_id,
        region_id,
        game_number,
        side_id,
        step_index,
        blue_team_id,
        red_team_id,
        blue_roster_ids,
        red_roster_ids,
        draft_sequence,
        action_mask, # [batch, num_champions] (1.0 = legal, 0.0 = illegal)
    ):
        """
        Applies Dynamic Action Masking: illegal champions get logit = -1e9
        Guaranteeing 100% legal compliance with Fearless Draft and current match constraints.
        """
        raw_logits = self.forward(
            patch_id, region_id, game_number, side_id, step_index,
            blue_team_id, red_team_id, blue_roster_ids, red_roster_ids,
            draft_sequence,
        )
        masked_logits = torch.where(
            action_mask > 0.5,
            raw_logits,
            torch.tensor(-1e9, device=raw_logits.device, dtype=raw_logits.dtype),
        )
        return masked_logits


class FearlessDraftDataset(Dataset):
    """
    Dataset loader for Fearless Draft sequences.
    """
    def __init__(self, samples):
        self.samples = samples

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        s = self.samples[idx]
        return {
            "patch_id": torch.tensor(s["patch_id"], dtype=torch.long),
            "region_id": torch.tensor(s["region_id"], dtype=torch.long),
            "game_number": torch.tensor(s["game_number"], dtype=torch.long),
            "side_id": torch.tensor(s["side_id"], dtype=torch.long),
            "step_index": torch.tensor(s["step_index"], dtype=torch.long),
            "blue_team_id": torch.tensor(s["blue_team_id"], dtype=torch.long),
            "red_team_id": torch.tensor(s["red_team_id"], dtype=torch.long),
            "blue_roster_ids": torch.tensor(s["blue_roster_ids"], dtype=torch.long),
            "red_roster_ids": torch.tensor(s["red_roster_ids"], dtype=torch.long),
            "draft_sequence": torch.tensor(s["draft_sequence"], dtype=torch.long),
            "action_mask": torch.tensor(s["action_mask"], dtype=torch.float32),
            "target_champion": torch.tensor(s["target_champion"], dtype=torch.long),
        }


def compute_topk_accuracy(logits, targets, ks=(1, 3, 5)):
    """
    Evaluates Top-1, Top-3, Top-5 accuracy on legal candidates.
    """
    res = {}
    max_k = max(ks)
    _, top_indices = logits.topk(max_k, dim=1, largest=True, sorted=True)
    targets_exp = targets.view(-1, 1).expand_as(top_indices)
    correct = (top_indices == targets_exp)
    for k in ks:
        correct_k = correct[:, :k].any(dim=1).float().mean().item()
        res[f"Top-{k}"] = correct_k
    return res


def train_fearless_model(
    model,
    train_loader,
    val_loader,
    epochs=5,
    lr=1e-3,
    device="cpu",
):
    """
    Stage 2 Fine-Tuning: End-to-end training with Cross-Entropy Loss and Dynamic Action Masking.
    """
    model.to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    criterion = nn.CrossEntropyLoss()

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for batch in train_loader:
            optimizer.zero_grad()
            logits = model.forward_with_dynamic_mask(
                patch_id=batch["patch_id"].to(device),
                region_id=batch["region_id"].to(device),
                game_number=batch["game_number"].to(device),
                side_id=batch["side_id"].to(device),
                step_index=batch["step_index"].to(device),
                blue_team_id=batch["blue_team_id"].to(device),
                red_team_id=batch["red_team_id"].to(device),
                blue_roster_ids=batch["blue_roster_ids"].to(device),
                red_roster_ids=batch["red_roster_ids"].to(device),
                draft_sequence=batch["draft_sequence"].to(device),
                action_mask=batch["action_mask"].to(device),
            )
            loss = criterion(logits, batch["target_champion"].to(device))
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
                    patch_id=batch["patch_id"].to(device),
                    region_id=batch["region_id"].to(device),
                    game_number=batch["game_number"].to(device),
                    side_id=batch["side_id"].to(device),
                    step_index=batch["step_index"].to(device),
                    blue_team_id=batch["blue_team_id"].to(device),
                    red_team_id=batch["red_team_id"].to(device),
                    blue_roster_ids=batch["blue_roster_ids"].to(device),
                    red_roster_ids=batch["red_roster_ids"].to(device),
                    draft_sequence=batch["draft_sequence"].to(device),
                    action_mask=batch["action_mask"].to(device),
                )
                val_logits_list.append(logits)
                val_targets_list.append(batch["target_champion"].to(device))

        all_logits = torch.cat(val_logits_list, dim=0)
        all_targets = torch.cat(val_targets_list, dim=0)
        metrics = compute_topk_accuracy(all_logits, all_targets, ks=(1, 3, 5))
        avg_loss = total_loss / max(1, len(train_loader))
        print(f"Epoch {epoch:02d} | Train Loss: {avg_loss:.4f} | Val Top-1: {metrics['Top-1']*100:.1f}% | Top-3: {metrics['Top-3']*100:.1f}% | Top-5: {metrics['Top-5']*100:.1f}%")
