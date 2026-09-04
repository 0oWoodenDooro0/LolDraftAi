import torch
import torch.nn as nn
from typing import Optional

class HybridDraftModel(nn.Module):
    """
    Fused Objective Match Statistics + Learned Champion Embeddings.
    
    Inputs:
        x: FloatTensor of shape [batch_size, 10 + num_empirical_features]
           - x[:, 0:5]: Blue champion IDs (1..num_champions, 0 for empty)
           - x[:, 5:10]: Red champion IDs (1..num_champions, 0 for empty)
           - x[:, 10:]: Empirical differential features (GD15, DPM, objectives, synergy, counter)
    Outputs:
        Win probability of Blue team in [0.0, 1.0] with strict anti-symmetric guarantee:
        P(Swapped) = 1 - P(Original).
    """
    def __init__(
        self,
        num_champions: int = 171,
        embedding_dim: int = 16,
        num_empirical_features: int = 12,
        hidden_dim: int = 64,
        dropout: float = 0.1
    ):
        super().__init__()
        self.num_champions = num_champions
        self.embedding_dim = embedding_dim
        self.num_empirical_features = num_empirical_features
        
        # Champion embedding (0 = empty slot / unpicked)
        self.embedding = nn.Embedding(num_champions, embedding_dim, padding_idx=0)
        
        # Differential representation dimension = embedding_dim + num_empirical_features
        input_dim = embedding_dim + num_empirical_features
        
        # Core feed-forward network
        self.net = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.LeakyReLU(0.1),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.LeakyReLU(0.1),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim // 2, 1)
        )

    def compute_logits(self, x: torch.Tensor) -> torch.Tensor:
        # Separate champion IDs and empirical features
        blue_ids = x[:, 0:5].long()
        red_ids = x[:, 5:10].long()
        empirical = x[:, 10:]
        
        # Embed champion sets (sum pooling; empty slots 0 have zero embedding)
        blue_emb = self.embedding(blue_ids).sum(dim=1)  # [B, embedding_dim]
        red_emb = self.embedding(red_ids).sum(dim=1)    # [B, embedding_dim]
        team_diff = blue_emb - red_emb                  # [B, embedding_dim]
        
        # Combined differential vector
        combined_diff = torch.cat([team_diff, empirical], dim=-1) # [B, input_dim]
        
        # Exact anti-symmetrization: f(v) = 0.5 * (net(v) - net(-v))
        # Guarantees f(-v) == -f(v) identically, ensuring 0 side bias
        logit = 0.5 * (self.net(combined_diff) - self.net(-combined_diff))
        return logit

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        logit = self.compute_logits(x)
        p = torch.sigmoid(logit)
        return torch.cat([1.0 - p, p], dim=-1)

