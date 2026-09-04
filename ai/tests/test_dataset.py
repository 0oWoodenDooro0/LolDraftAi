import pytest
import numpy as np
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../src"))

import dataset


def test_dataset_features_have_no_zero_variance_dimensions():
    """Verify that all 52 feature columns, especially meta, synergy, matchup, and dominance, have active non-zero variance."""
    X, y = dataset.load_oracles_elixir_dataset(max_games=200)
    assert X.shape[0] >= 50, f"Expected at least 50 games loaded, got {X.shape[0]}"
    assert X.shape[1] == 52, f"Expected 52 feature dimensions, got {X.shape[1]}"
    
    variances = np.var(X, axis=0)
    
    # Specific indices that were previously constant zero:
    # 29: d_tier
    # 32: d_mwr
    # 35: d_syn
    # 36: d_matchup
    # 38: d_dom
    zero_var_indices = [i for i, v in enumerate(variances) if v < 1e-6]
    
    assert 29 not in zero_var_indices, f"delta_meta_tier (index 29) has zero variance!"
    assert 32 not in zero_var_indices, f"delta_meta_winrate (index 32) has zero variance!"
    assert 35 not in zero_var_indices, f"delta_synergy (index 35) has zero variance!"
    assert 36 not in zero_var_indices, f"delta_matchup (index 36) has zero variance!"
    assert 38 not in zero_var_indices, f"delta_dominance (index 38) has zero variance!"


def test_empirical_dataset_symmetric_augmentation():
    """Verify that empirical dataset produces 21-dim vectors with exact 50-50 symmetric label distribution."""
    X, y = dataset.load_empirical_dataset(max_games=200, augment_symmetric=True)
    assert X.shape[0] >= 50
    assert X.shape[1] == 21
    # Symmetric augmentation means sum(y) / len(y) == 0.50 exactly
    assert abs(np.mean(y) - 0.50) < 1e-5

