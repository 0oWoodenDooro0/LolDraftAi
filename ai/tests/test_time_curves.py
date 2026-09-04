import pytest
import numpy as np
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../src"))

import time_curves
import calibrate_time_curves


def test_calibrate_time_curves_from_dataset():
    """Verify that empirical calibration runs on match data and yields valid parameters."""
    calib = calibrate_time_curves.calibrate_parameters(max_games=500)
    assert "early_weights" in calib
    assert "late_weights" in calib
    assert "inflection_points" in calib
    
    # Inflection points should be near 15-16 min (plate fall) and 28-30 min (Baron/Elder phase)
    t_early, t_late = calib["inflection_points"]
    assert 13.0 <= t_early <= 17.5, f"t_early out of expected bounds: {t_early}"
    assert 26.0 <= t_late <= 32.0, f"t_late out of expected bounds: {t_late}"
    
    # Early signal weights should be positive
    ew = calib["early_weights"]
    assert ew["laning"] > 0.0
    assert ew["early_bully"] > 0.0


def test_time_curve_prediction_with_calibrated_trajectory():
    """Verify that early bully comp falls off late and late hypercarry comp scales up."""
    # Bully comp: strong early laning, negative late scaling
    bully_curve = time_curves.predict_time_curve(
        baseline_blue_win_rate=0.52,
        laning_delta=2.0,
        late_scaling_delta=-2.0,
        early_bully_delta=2,
        hyper_carry_delta=-2,
    )
    
    assert bully_curve["early_game_win_rate"] > bully_curve["late_game_win_rate"]
    assert "Falloff" in bully_curve["trajectory_summary"] or "Early" in bully_curve["trajectory_summary"]
    
    # Scaling comp: weak early laning, positive late scaling
    scaling_curve = time_curves.predict_time_curve(
        baseline_blue_win_rate=0.48,
        laning_delta=-2.0,
        late_scaling_delta=2.5,
        early_bully_delta=-2,
        hyper_carry_delta=2,
        durability_delta=1.5,
        cc_delta=2.0,
    )
    
    assert scaling_curve["late_game_win_rate"] > scaling_curve["early_game_win_rate"]
    assert "Scaling" in scaling_curve["trajectory_summary"] or "Inversion" in scaling_curve["trajectory_summary"]
