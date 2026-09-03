"""
LoL Draft AI - Composition Flaw Detector (Python AI Mirror)
Detects structural flaws in team draft compositions:
1. Damage Profile (Full AD / Lack AP / Full AP / Lack AD)
2. Frontline & Engage (No Tank / No Hard CC / Lack Engage)
3. Waveclear Deficit
4. Tempo Disconnect & Power Spike Mismatch
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import List, Dict, Any, Optional
import numpy as np

try:
    from .feature_extractor import get_champion_profile
except ImportError:
    from feature_extractor import get_champion_profile


class FlawCategory(str, Enum):
    DAMAGE_PROFILE = "DAMAGE_PROFILE"
    ENGAGE_FRONTLINE = "ENGAGE_FRONTLINE"
    WAVECLEAR = "WAVECLEAR"
    TEMPO_DISCONNECT = "TEMPO_DISCONNECT"


class FlawSeverity(str, Enum):
    INFO = "INFO"
    WARNING = "WARNING"
    CRITICAL = "CRITICAL"


@dataclass
class CompositionFlaw:
    id: str
    category: FlawCategory
    severity: FlawSeverity
    title: str
    description: str
    affected_side: str
    current_picks_count: int
    suggestion: str
    metrics: Dict[str, float] = field(default_factory=dict)


@dataclass
class CompositionFlawReport:
    side: str
    picks: List[str]
    flaws: List[CompositionFlaw] = field(default_factory=list)
    has_critical_flaws: bool = False
    flaw_count_by_category: Dict[str, int] = field(default_factory=dict)
    overall_health_score: float = 100.0


def detect_composition_flaws(
    picks: List[str],
    side: str = "BLUE",
    physical_warning: float = 0.80,
    physical_critical: float = 0.88,
    magic_deficit: float = 0.15,
    min_picks: int = 3,
) -> CompositionFlawReport:
    """
    Evaluates picks dynamically for team composition flaws.
    """
    if not picks or len(picks) < min_picks:
        return CompositionFlawReport(
            side=side,
            picks=picks,
            flaws=[],
            has_critical_flaws=False,
            flaw_count_by_category={},
            overall_health_score=100.0,
        )

    profiles = [get_champion_profile(p) for p in picks]
    pick_count = len(picks)
    flaws: List[CompositionFlaw] = []

    # 1. Damage Profile Flaws
    avg_phys = float(np.mean([p.get("phys", 0.5) for p in profiles]))
    avg_magic = float(np.mean([p.get("magic", 0.5) for p in profiles]))

    if avg_phys >= physical_warning or avg_magic <= magic_deficit:
        severity = FlawSeverity.CRITICAL if (pick_count >= 5 and avg_phys >= physical_critical) else FlawSeverity.WARNING
        flaws.append(
            CompositionFlaw(
                id="FLAW_ALL_PHYSICAL",
                category=FlawCategory.DAMAGE_PROFILE,
                severity=severity,
                title="全物理傷害陣容 (菜刀隊)",
                description=f"隊伍物理傷害佔比高達 {int(avg_phys * 100)}%，缺乏法術傷害威脅。",
                affected_side=side,
                current_picks_count=pick_count,
                suggestion="建議後續補充 AP 輸出點位平衡傷害。",
                metrics={"physicalRatio": avg_phys, "magicRatio": avg_magic},
            )
        )
    elif avg_magic < 0.20 and avg_phys >= 0.75:
        flaws.append(
            CompositionFlaw(
                id="FLAW_LACK_MAGIC_DAMAGE",
                category=FlawCategory.DAMAGE_PROFILE,
                severity=FlawSeverity.WARNING,
                title="缺乏法術傷害 (AP 匱乏)",
                description=f"法術傷害佔比僅 {int(avg_magic * 100)}%。",
                affected_side=side,
                current_picks_count=pick_count,
                suggestion="建議補齊高 AP 爆發或混傷英雄。",
                metrics={"magicRatio": avg_magic},
            )
        )

    # 2. Engage & Frontline Flaws
    avg_durability = float(np.mean([p.get("durability", 5.0) for p in profiles]))
    total_cc = float(sum(p.get("cc", 0.0) for p in profiles))
    avg_engage = float(np.mean([p.get("engage", 5.0) for p in profiles]))
    has_tank = any("tank" in p.get("tags", []) or "vanguard_tank" in p.get("tags", []) or "warden_tank" in p.get("tags", []) for p in profiles)

    if not has_tank and avg_durability < 4.5:
        severity = FlawSeverity.CRITICAL if pick_count >= 5 else FlawSeverity.WARNING
        flaws.append(
            CompositionFlaw(
                id="FLAW_NO_FRONTLINE",
                category=FlawCategory.ENGAGE_FRONTLINE,
                severity=severity,
                title="缺乏前排坦度 (全脆皮陣容)",
                description=f"缺乏重裝前排坦克，平均坦度評分僅 {round(avg_durability, 2)}。",
                affected_side=side,
                current_picks_count=pick_count,
                suggestion="建議補強前排坦克或重裝戰士。",
                metrics={"durabilityScore": round(avg_durability, 2)},
            )
        )

    if total_cc < 1.0 and pick_count >= 3:
        severity = FlawSeverity.CRITICAL if pick_count >= 5 and total_cc < 2.0 else FlawSeverity.WARNING
        flaws.append(
            CompositionFlaw(
                id="FLAW_NO_HARD_CC",
                category=FlawCategory.ENGAGE_FRONTLINE,
                severity=severity,
                title="缺乏先手硬控 (缺乏穩定 CC)",
                description=f"硬控時長僅 {round(total_cc, 2)} 秒。",
                affected_side=side,
                current_picks_count=pick_count,
                suggestion="建議選擇具備穩定控制的英雄。",
                metrics={"hardCcSeconds": round(total_cc, 2)},
            )
        )

    # 3. Waveclear Deficit
    avg_waveclear = float(np.mean([p.get("waveclear", 5.0) for p in profiles]))
    has_waveclear_tag = any("waveclear_stall" in p.get("tags", []) for p in profiles)
    if avg_waveclear < 5.0 and not has_waveclear_tag:
        flaws.append(
            CompositionFlaw(
                id="FLAW_WAVECLEAR_DEFICIT",
                category=FlawCategory.WAVECLEAR,
                severity=FlawSeverity.CRITICAL if pick_count >= 5 and avg_waveclear < 4.5 else FlawSeverity.WARNING,
                title="清線防守劣勢 (Waveclear Deficit)",
                description=f"清線能力評分僅 {round(avg_waveclear, 2)}，守塔被動。",
                affected_side=side,
                current_picks_count=pick_count,
                suggestion="建議補齊長手 AOE 清線英雄。",
                metrics={"waveclear": round(avg_waveclear, 2)},
            )
        )

    # Health score calculation
    critical_count = sum(1 for f in flaws if f.severity == FlawSeverity.CRITICAL)
    warning_count = sum(1 for f in flaws if f.severity == FlawSeverity.WARNING)
    raw_health = 100.0 - (critical_count * 25.0) - (warning_count * 10.0)
    health_score = round(max(0.0, min(100.0, raw_health)), 1)
    cat_counts = {}
    for f in flaws:
        cat_counts[f.category.value] = cat_counts.get(f.category.value, 0) + 1

    return CompositionFlawReport(
        side=side,
        picks=picks,
        flaws=flaws,
        has_critical_flaws=critical_count > 0,
        flaw_count_by_category=cat_counts,
        overall_health_score=health_score,
    )
