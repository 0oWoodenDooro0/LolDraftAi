"""
LoL Draft AI - Eval Bar Algorithm (Python AI Mirror)
Provides symmetric log-odds mapping between draft win probability and chess-style evaluation score.
"""

import math
from typing import Optional, Dict, Any

DEFAULT_SCALE = 1.5
DEFAULT_MAX_EVAL = 10.0
EPSILON = 1e-7


def format_eval_score(score: float) -> str:
    rounded = round(score, 1)
    if abs(rounded) < 1e-9:
        return "0.0"
    elif rounded > 0.0:
        return f"+{rounded:.1f}"
    else:
        return f"-{abs(rounded):.1f}"


def resolve_lead_category(score: float) -> str:
    if abs(score) < 0.05:
        return "EVEN"
    elif score > 1.5:
        return "BLUE_DECISIVE"
    elif score > 0.4:
        return "BLUE_ADVANTAGE"
    elif score > 0.0:
        return "SLIGHT_BLUE"
    elif score < -1.5:
        return "RED_DECISIVE"
    elif score < -0.4:
        return "RED_ADVANTAGE"
    else:
        return "SLIGHT_RED"


def winrate_to_eval_bar(
    blue_win_rate: float,
    scale: float = DEFAULT_SCALE,
    max_eval: float = DEFAULT_MAX_EVAL,
) -> Dict[str, Any]:
    """
    Converts blue win probability P into a symmetric log-odds Eval Bar score.
    """
    clamped_p = max(0.0, min(1.0, blue_win_rate))
    blue_pct = clamped_p * 100.0
    red_pct = 100.0 - blue_pct

    safe_p = max(EPSILON, min(1.0 - EPSILON, blue_win_rate))
    if abs(clamped_p - 0.50) < 1e-9:
        score = 0.0
    else:
        logit = math.log(safe_p / (1.0 - safe_p))
        score = max(-max_eval, min(max_eval, scale * logit))

    favored_side = "BLUE" if score > 1e-6 else ("RED" if score < -1e-6 else None)
    formatted = format_eval_score(score)
    category = resolve_lead_category(score)

    return {
        "score": round(score, 4),
        "formatted_score": formatted,
        "favored_side": favored_side,
        "blue_bar_percentage": round(blue_pct, 2),
        "red_bar_percentage": round(red_pct, 2),
        "lead_category": category,
    }


def eval_bar_to_winrate(score: float, scale: float = DEFAULT_SCALE) -> float:
    """
    Inverts an Eval Bar score back into blue win probability.
    """
    logit = score / scale
    return 1.0 / (1.0 + math.exp(-logit))
