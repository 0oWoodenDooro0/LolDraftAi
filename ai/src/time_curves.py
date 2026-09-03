"""
LoL Draft AI - Time-Horizon Win-Rate Curves (Python AI Mirror)
Predicts early, mid, and late game win rates and generates smooth time-horizon trajectories.
"""

import math
from typing import List, Dict, Any, Optional
from eval_bar import winrate_to_eval_bar, DEFAULT_SCALE

TIME_INTERVALS = [10, 15, 20, 25, 30, 35, 40]


def resolve_phase(minute: int) -> str:
    if minute <= 15:
        return "EARLY_GAME"
    elif minute <= 25:
        return "MID_GAME"
    elif minute <= 35:
        return "LATE_GAME"
    else:
        return "ULTRA_LATE"


def generate_trajectory_summary(early_wr: float, late_wr: float) -> str:
    if early_wr >= 0.52 and late_wr <= 0.48:
        return "Early Bully -> Late Falloff"
    elif early_wr <= 0.48 and late_wr >= 0.52:
        return "Early Deficit -> Late Scaling Inversion"
    elif early_wr >= 0.52 and late_wr >= 0.52:
        return "Sustained Blue Composition Dominance"
    elif early_wr <= 0.48 and late_wr <= 0.48:
        return "Sustained Red Composition Dominance"
    else:
        return "Evenly Contested Trajectory"


def predict_time_curve(
    baseline_blue_win_rate: float,
    laning_delta: float = 0.0,
    late_scaling_delta: float = 0.0,
    early_bully_delta: int = 0,
    hyper_carry_delta: int = 0,
    early_dominance_delta: float = 0.0,
    matchup_delta: float = 0.0,
    durability_delta: float = 0.0,
    cc_delta: float = 0.0,
    scale: float = DEFAULT_SCALE,
) -> Dict[str, Any]:
    safe_base = max(0.01, min(0.99, baseline_blue_win_rate))
    base_logit = math.log(safe_base / (1.0 - safe_base))

    early_signal = (
        laning_delta * 0.12
        + early_bully_delta * 0.10
        + early_dominance_delta * 0.04
        + matchup_delta * 0.08
    )
    late_signal = (
        late_scaling_delta * 0.12
        + hyper_carry_delta * 0.10
        + durability_delta * 0.03
        + cc_delta * 0.03
    )

    points = []
    for minute in TIME_INTERVALS:
        w_early = 1.0 / (1.0 + math.exp((minute - 16.0) / 4.0))
        w_late = 1.0 / (1.0 + math.exp(-(minute - 27.0) / 4.5))

        shift = early_signal * (w_early - 0.35) + late_signal * (w_late - 0.35)
        logit = base_logit + shift
        blue_wr = round(max(0.01, min(0.99, 1.0 / (1.0 + math.exp(-logit)))), 4)
        red_wr = round(1.0 - blue_wr, 4)

        eval_bar = winrate_to_eval_bar(blue_wr, scale=scale)
        phase = resolve_phase(minute)

        points.append({
            "minute": minute,
            "blue_win_rate": blue_wr,
            "red_win_rate": red_wr,
            "eval_bar": eval_bar,
            "dominant_phase": phase,
        })

    min10 = next(p["blue_win_rate"] for p in points if p["minute"] == 10)
    min15 = next(p["blue_win_rate"] for p in points if p["minute"] == 15)
    min20 = next(p["blue_win_rate"] for p in points if p["minute"] == 20)
    min25 = next(p["blue_win_rate"] for p in points if p["minute"] == 25)
    min30 = next(p["blue_win_rate"] for p in points if p["minute"] == 30)
    min35 = next(p["blue_win_rate"] for p in points if p["minute"] == 35)
    min40 = next(p["blue_win_rate"] for p in points if p["minute"] == 40)

    early_wr = round((min10 + min15) / 2.0, 4)
    mid_wr = round((min20 + min25) / 2.0, 4)
    late_wr = round((min30 + min35) / 2.0, 4)
    ultra_late_wr = round(min40, 4)

    summary = generate_trajectory_summary(early_wr, late_wr)

    return {
        "points": points,
        "early_game_win_rate": early_wr,
        "mid_game_win_rate": mid_wr,
        "late_game_win_rate": late_wr,
        "ultra_late_win_rate": ultra_late_wr,
        "trajectory_summary": summary,
    }
