import pytest
import numpy as np
from src.empirical_feature_extractor import EmpiricalFeatureExtractor

def test_feature_extractor_vector_length():
    extractor = EmpiricalFeatureExtractor()
    # 5 Blue picks, 5 Red picks
    blue = ["Aatrox", "Sejuani", "Ahri", "Varus", "Nautilus"]
    red = ["Renekton", "Vi", "Azir", "Kai'Sa", "Rell"]
    
    vec = extractor.extract(blue, red)
    # 10 champion IDs + 11 differential features = 21 features
    assert len(vec) == 21
    # Check Blue IDs are > 0
    assert np.all(vec[0:5] > 0)
    # Check Red IDs are > 0
    assert np.all(vec[5:10] > 0)

def test_feature_extractor_partial_draft():
    extractor = EmpiricalFeatureExtractor()
    # Only 1 pick each
    blue = ["Aatrox"]
    red = ["Renekton"]
    
    vec = extractor.extract(blue, red)
    assert len(vec) == 21
    assert vec[0] > 0
    assert np.all(vec[1:5] == 0.0)
    assert vec[5] > 0
    assert np.all(vec[6:10] == 0.0)

def test_feature_extractor_antisymmetry():
    extractor = EmpiricalFeatureExtractor()
    blue = ["Aatrox", "Sejuani", "Ahri", "Varus", "Nautilus"]
    red = ["Renekton", "Vi", "Azir", "Kai'Sa", "Rell"]
    
    vec_orig = extractor.extract(blue, red)
    vec_swap = extractor.extract(red, blue)
    
    # Blue and Red IDs are swapped
    np.testing.assert_array_equal(vec_orig[0:5], vec_swap[5:10])
    np.testing.assert_array_equal(vec_orig[5:10], vec_swap[0:5])
    
    # All 11 differential features (index 10..20) must be exactly negated
    diff_orig = vec_orig[10:]
    diff_swap = vec_swap[10:]
    np.testing.assert_allclose(diff_orig, -diff_swap, atol=1e-5)
