import pytest
import torch
import numpy as np
from src.hybrid_draft_model import HybridDraftModel

def test_model_forward_shape():
    # 170 champions, padding_idx=0 -> num_embeddings=171
    # 10 champions (5 Blue, 5 Red) + 12 empirical differential features = 22 input dims
    model = HybridDraftModel(num_champions=171, embedding_dim=16, num_empirical_features=12)
    model.eval()

    batch_size = 4
    # Columns 0..4: Blue champ IDs (1..170)
    # Columns 5..9: Red champ IDs (1..170)
    # Columns 10..21: empirical features (floats)
    dummy_input = torch.zeros(batch_size, 22, dtype=torch.float32)
    # Set blue picks: 1, 2, 3, 4, 5
    dummy_input[:, 0:5] = torch.tensor([[1, 2, 3, 4, 5]])
    # Set red picks: 6, 7, 8, 9, 10
    dummy_input[:, 5:10] = torch.tensor([[6, 7, 8, 9, 10]])
    # Empirical features
    dummy_input[:, 10:22] = torch.randn(batch_size, 12)

    with torch.no_grad():
        output = model(dummy_input)

    # Model output should be win probability in [0, 1] of shape [batch_size, 1] or [batch_size]
    assert output.shape == (batch_size, 2)
    probs = output.numpy()
    assert np.all(probs >= 0.0) and np.all(probs <= 1.0)
    assert np.allclose(probs.sum(axis=1), 1.0, atol=1e-5)

def test_model_handles_partial_drafts():
    # Partial draft: some slots are 0 (unpicked)
    model = HybridDraftModel(num_champions=174, embedding_dim=16, num_empirical_features=11)
    model.eval()

    # Blue has 2 picks, Red has 1 pick
    partial_input = torch.zeros(1, 21, dtype=torch.float32)
    partial_input[0, 0:2] = torch.tensor([10, 20])
    partial_input[0, 5:6] = torch.tensor([30])

    with torch.no_grad():
        output = model(partial_input)

    prob = output[0, 1].item()
    assert 0.0 <= prob <= 1.0

def test_symmetry_property():
    # If Blue and Red are mirrored, symmetric augmentation or model produces opposite logit
    torch.manual_seed(42)
    model = HybridDraftModel(num_champions=174, embedding_dim=16, num_empirical_features=11)
    model.eval()

    # Draft A: Blue = [1, 2, 3, 4, 5], Red = [6, 7, 8, 9, 10]
    draft_a = torch.zeros(1, 21, dtype=torch.float32)
    draft_a[0, 0:5] = torch.tensor([1, 2, 3, 4, 5])
    draft_a[0, 5:10] = torch.tensor([6, 7, 8, 9, 10])
    draft_a[0, 10:21] = torch.tensor([0.05, 100.0, 2.0, 50.0, -20.0, 10.0, 0.1, -0.1, 0.05, 0.02, 50.0])

    # Draft B: Swapped Blue and Red, negated differentials
    draft_b = torch.zeros(1, 21, dtype=torch.float32)
    draft_b[0, 0:5] = torch.tensor([6, 7, 8, 9, 10])
    draft_b[0, 5:10] = torch.tensor([1, 2, 3, 4, 5])
    draft_b[0, 10:21] = - draft_a[0, 10:21]

    with torch.no_grad():
        prob_a = model(draft_a)[0, 1].item()
        prob_b = model(draft_b)[0, 1].item()

    # Prob A + Prob B should be identically 1.0
    assert abs((prob_a + prob_b) - 1.0) < 1e-5
