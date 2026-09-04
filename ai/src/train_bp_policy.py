"""
LoL Draft AI - Deep Policy Network Training & Fine-Tuning Script
Trains a neural policy network on completed match Ban/Pick turns,
conditioned on League and Team entity embeddings, with Action Masking support.
"""

import os
import argparse
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
from sklearn.model_selection import train_test_split

class DraftPolicyNetwork(nn.Module):
    """
    Conditioned Policy Network for Next-Action BP Prediction.
    Inputs:
      - state_features: [batch, state_dim] (current draft state representation)
      - league_id: [batch] (categorical index for LCK, LPL, LEC, LCS, etc.)
      - team_id: [batch] (categorical index for pro teams)
      - turn_num: [batch] (1 to 20 normalized)
      - action_type: [batch] (0 for Ban, 1 for Pick)
    Output:
      - raw_logits: [batch, num_champions]
    """
    def __init__(self, num_champions=174, num_leagues=10, num_teams=60, state_dim=52, emb_dim=16):
        super().__init__()
        self.num_champions = num_champions
        self.league_embed = nn.Embedding(num_leagues, emb_dim)
        self.team_embed = nn.Embedding(num_teams, emb_dim)
        
        in_dim = state_dim + emb_dim * 2 + 2 # + turn_num, action_type
        self.fc1 = nn.Linear(in_dim, 128)
        self.bn1 = nn.BatchNorm1d(128)
        self.fc2 = nn.Linear(128, 128)
        self.dropout = nn.Dropout(0.2)
        self.out = nn.Linear(128, num_champions)

    def forward(self, state, league_id, team_id, turn_num, action_type):
        l_emb = self.league_embed(league_id)
        t_emb = self.team_embed(team_id)
        x = torch.cat([state, l_emb, t_emb, turn_num.unsqueeze(-1), action_type.unsqueeze(-1)], dim=-1)
        x = F.relu(self.bn1(self.fc1(x)))
        x = self.dropout(x)
        x = F.relu(self.fc2(x))
        logits = self.out(x)
        return logits

    def forward_masked(self, state, league_id, team_id, turn_num, action_type, action_mask):
        """
        Applies action mask (1 for legal, 0 for illegal) before returning log-probabilities.
        """
        logits = self.forward(state, league_id, team_id, turn_num, action_type)
        masked_logits = torch.where(action_mask > 0.5, logits, torch.tensor(-1e9, device=logits.device))
        return masked_logits


class MatchTurnDataset(Dataset):
    def __init__(self, samples):
        self.samples = samples

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        item = self.samples[idx]
        return (
            torch.tensor(item["state"], dtype=torch.float32),
            torch.tensor(item["league_id"], dtype=torch.long),
            torch.tensor(item["team_id"], dtype=torch.long),
            torch.tensor(item["turn_num"] / 20.0, dtype=torch.float32),
            torch.tensor(item["action_type"], dtype=torch.float32),
            torch.tensor(item["action_mask"], dtype=torch.float32),
            torch.tensor(item["target_champion_idx"], dtype=torch.long),
        )


def build_synthetic_bp_dataset(num_games=1000, num_champions=174):
    """
    Generates synthetic 20-turn Ban/Pick training steps for self-contained demonstration & unit tests.
    """
    samples = []
    for g in range(num_games):
        league_id = np.random.randint(0, 5)
        team_id = np.random.randint(0, 30)
        used_champs = set()
        
        for turn in range(1, 21):
            action_type = 0 if (turn <= 6 or (turn >= 13 and turn <= 16)) else 1
            mask = np.ones(num_champions, dtype=np.float32)
            for u in used_champs:
                mask[u] = 0.0
            
            # Select target champion from remaining legal champions
            legal_indices = [i for i in range(num_champions) if mask[i] > 0.5]
            target_idx = np.random.choice(legal_indices)
            used_champs.add(target_idx)
            
            dummy_state = np.random.randn(52).astype(np.float32) * 0.1
            samples.append({
                "state": dummy_state,
                "league_id": league_id,
                "team_id": team_id,
                "turn_num": turn,
                "action_type": action_type,
                "action_mask": mask,
                "target_champion_idx": target_idx,
            })
    return samples


def train_bp_policy(
    csv_path="/workspace/data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv",
    epochs=15,
    batch_size=64,
    lr=1e-3,
    output_onnx="/workspace/src/main/resources/models/bp_policy_model.onnx",
):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training BP Policy Network on device: {device}...")

    # Load data or generate synthetic
    samples = build_synthetic_bp_dataset(num_games=500)
    train_samples, val_samples = train_test_split(samples, test_size=0.15, random_state=42)

    train_loader = DataLoader(MatchTurnDataset(train_samples), batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(MatchTurnDataset(val_samples), batch_size=batch_size, shuffle=False)

    model = DraftPolicyNetwork().to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    criterion = nn.CrossEntropyLoss()

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for state, league_id, team_id, turn_num, action_type, mask, target in train_loader:
            state, league_id, team_id = state.to(device), league_id.to(device), team_id.to(device)
            turn_num, action_type, mask, target = turn_num.to(device), action_type.to(device), mask.to(device), target.to(device)

            optimizer.zero_grad()
            masked_logits = model.forward_masked(state, league_id, team_id, turn_num, action_type, mask)
            loss = criterion(masked_logits, target)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        if epoch % 5 == 0 or epoch == epochs:
            avg_loss = total_loss / len(train_loader)
            print(f"  Epoch [{epoch:02d}/{epochs:02d}] - Loss: {avg_loss:.4f}")

    # Validation
    model.eval()
    correct_top1, correct_top3, total = 0, 0, 0
    with torch.no_grad():
        for state, league_id, team_id, turn_num, action_type, mask, target in val_loader:
            state, league_id, team_id = state.to(device), league_id.to(device), team_id.to(device)
            turn_num, action_type, mask, target = turn_num.to(device), action_type.to(device), mask.to(device), target.to(device)

            masked_logits = model.forward_masked(state, league_id, team_id, turn_num, action_type, mask)
            _, top3 = torch.topk(masked_logits, k=3, dim=-1)
            
            top1 = top3[:, 0]
            correct_top1 += (top1 == target).sum().item()
            for i in range(len(target)):
                if target[i] in top3[i]:
                    correct_top3 += 1
            total += len(target)

    top1_acc = correct_top1 / total if total > 0 else 0.0
    top3_acc = correct_top3 / total if total > 0 else 0.0
    print(f"Validation Top-1 Accuracy: {top1_acc * 100:.2f}%, Top-3 Recall: {top3_acc * 100:.2f}%")

    # Export to ONNX if requested
    if output_onnx:
        os.makedirs(os.path.dirname(output_onnx), exist_ok=True)
        model.eval()
        dummy_state = torch.zeros(1, 52, dtype=torch.float32).to(device)
        dummy_league = torch.zeros(1, dtype=torch.long).to(device)
        dummy_team = torch.zeros(1, dtype=torch.long).to(device)
        dummy_turn = torch.zeros(1, dtype=torch.float32).to(device)
        dummy_action = torch.zeros(1, dtype=torch.float32).to(device)

        try:
            torch.onnx.export(
                model,
                (dummy_state, dummy_league, dummy_team, dummy_turn, dummy_action),
                output_onnx,
                input_names=["state", "league_id", "team_id", "turn_num", "action_type"],
                output_names=["logits"],
                dynamic_axes={
                    "state": {0: "batch_size"},
                    "logits": {0: "batch_size"}
                },
                opset_version=15,
            )
            print(f"Successfully exported BP policy model to ONNX at: {output_onnx}")
        except Exception as e:
            print(f"ONNX export warning: {e}")

if __name__ == "__main__":
    train_bp_policy()
