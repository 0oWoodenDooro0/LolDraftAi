import pytest
import numpy as np
import sys
import os

# Ensure src is in sys.path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../src"))

import feature_extractor


def test_full_champion_database_coverage():
    """Verify that at least 168 canonical champions are loaded from champion_tags.json."""
    loaded = feature_extractor.CHAMPION_PROFILES
    assert len(loaded) >= 168, f"Expected at least 168 champions loaded, found {len(loaded)}"
    
    # Spot check several diverse champions
    for name in ["Aatrox", "Hwei", "Smolder", "Aurora", "Briar", "Ambessa", "K'Sante", "Naafiri", "Thresh", "Orianna"]:
        slug = feature_extractor._slugify(name)
        assert slug in loaded, f"Champion '{name}' (slug: {slug}) must be loaded in CHAMPION_PROFILES"
        prof = loaded[slug]
        assert prof["durability"] > 0
        assert prof["laning"] > 0
        assert prof["late"] > 0
        assert sum([prof["phys"], prof["magic"], prof["true"]]) == pytest.approx(1.0, abs=0.05)


def test_partial_draft_role_prior_imputation():
    """Verify that partial draft with 1 extreme tank does not distort average durability."""
    # 1 tank (Poppy durability ~8.8) on Blue, empty on Red
    vec = feature_extractor.extract_features(["Poppy"], [])
    
    # b_dur is at index 21, r_dur at 22, d_dur at 23
    b_dur = vec[21]
    r_dur = vec[22]
    d_dur = vec[23]
    
    assert 6.0 <= b_dur <= 7.2, f"Blue durability should be moderated by remaining 4 priors (expected 6.0..7.2), got {b_dur}"
    assert 5.5 <= r_dur <= 6.2, f"Red durability with 0 picks should sit near baseline prior (expected 5.5..6.2), got {r_dur}"
    assert d_dur < 1.5, f"Delta durability should be moderate for 1 pick, got {d_dur}"


def test_dynamic_meta_synergy_and_matchup_features():
    """Verify that dynamic meta, synergy, matchup, and dominance features are properly computed."""
    meta_stats = {
        "aatrox": {"tier": 4.0, "winrate": 0.58},
        "sejuani": {"tier": 3.0, "winrate": 0.54},
        "ksante": {"tier": 1.0, "winrate": 0.44},
        "vi": {"tier": 1.0, "winrate": 0.45},
    }
    synergies = {
        ("aatrox", "sejuani"): 1.5,
    }
    matchups = {
        ("aatrox", "ksante"): 2.3,
    }
    
    blue_champs = ["Aatrox", "Sejuani"]
    red_champs = ["K'Sante", "Vi"]
    
    vec = feature_extractor.extract_features(
        blue_champs=blue_champs,
        red_champs=red_champs,
        meta_stats=meta_stats,
        synergies=synergies,
        matchups=matchups,
        blue_dominance=7.5,
        red_dominance=4.5,
    )
    
    # d_tier is at index 29: Blue has T0/T1 (avg 3.5), Red has T3 (avg 1.0) -> delta > 0
    d_tier = vec[29]
    assert d_tier > 0.0, f"Expected positive d_tier, got {d_tier}"
    
    # d_mwr is at index 32: Blue has 0.56 vs Red 0.445 -> delta > 0
    d_mwr = vec[32]
    assert d_mwr > 0.0, f"Expected positive d_mwr, got {d_mwr}"
    
    # d_syn is at index 35: Blue has synergy 1.5, Red has 0.0 -> delta == 1.5
    d_syn = vec[35]
    assert d_syn == pytest.approx(1.5, abs=0.01), f"Expected d_syn == 1.5, got {d_syn}"
    
    # d_matchup is at index 36: Aatrox counters K'Sante by +2.3
    d_matchup = vec[36]
    assert d_matchup == pytest.approx(2.3, abs=0.01), f"Expected d_matchup == 2.3, got {d_matchup}"
    
    # d_dom is at index 38: 7.5 - 4.5 = 3.0
    d_dom = vec[38]
    assert d_dom == pytest.approx(3.0, abs=0.01), f"Expected d_dom == 3.0, got {d_dom}"
