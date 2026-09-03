"""
LoL Draft AI - Flex Pick Analyzer (Python AI Mirror)
Identifies multi-role flex picks, computes Bayesian conditional role distributions,
and provides defensive strategy advice against enemy flex selections.
"""

from dataclasses import dataclass, field
from enum import Enum
import math
from typing import Dict, List, Optional, Set


class Role(str, Enum):
    TOP = "TOP"
    JUNGLE = "JUNGLE"
    MID = "MID"
    BOT = "BOT"
    SUPPORT = "SUPPORT"


class FlexThreatLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


@dataclass
class RoleProbability:
    role: Role
    probability: float
    pro_games: int = 0
    sample_share: float = 0.0


@dataclass
class FlexAnalysisResult:
    champion_id: str
    is_flex: bool
    role_probabilities: Dict[Role, float]
    primary_role: Role
    secondary_roles: List[Role] = field(default_factory=list)
    flex_entropy: float = 0.0
    confidence: float = 1.0


@dataclass
class FlexDefenseAdvice:
    target_champion: str
    threat_level: FlexThreatLevel
    candidate_roles: List[RoleProbability]
    tactical_warnings: List[str]
    counter_strategies: List[str]
    recommended_dual_counters: List[str] = field(default_factory=list)


class FlexPickAnalyzer:
    DEFAULT_FLEX_PRIORS: Dict[str, Dict[Role, float]] = {
        "rumble": {Role.TOP: 0.45, Role.MID: 0.45, Role.JUNGLE: 0.10},
        "poppy": {Role.TOP: 0.35, Role.JUNGLE: 0.45, Role.SUPPORT: 0.20},
        "corki": {Role.MID: 0.70, Role.BOT: 0.30},
        "tristana": {Role.MID: 0.55, Role.BOT: 0.45},
        "maokai": {Role.SUPPORT: 0.50, Role.JUNGLE: 0.35, Role.TOP: 0.15},
        "gragas": {Role.TOP: 0.45, Role.JUNGLE: 0.30, Role.MID: 0.25},
        "nautilus": {Role.SUPPORT: 0.85, Role.MID: 0.15},
        "jayce": {Role.TOP: 0.55, Role.MID: 0.45},
        "lucian": {Role.BOT: 0.75, Role.MID: 0.25},
        "k'sante": {Role.TOP: 0.85, Role.MID: 0.15},
        "karma": {Role.SUPPORT: 0.70, Role.MID: 0.20, Role.TOP: 0.10},
        "seraphine": {Role.SUPPORT: 0.50, Role.BOT: 0.30, Role.MID: 0.20},
        "neeko": {Role.MID: 0.55, Role.SUPPORT: 0.45},
        "yasuo": {Role.MID: 0.65, Role.TOP: 0.20, Role.BOT: 0.15},
        "vayne": {Role.BOT: 0.75, Role.TOP: 0.25},
        "renekton": {Role.TOP: 0.85, Role.MID: 0.15},
        "smolder": {Role.BOT: 0.70, Role.MID: 0.30},
        "galio": {Role.MID: 0.65, Role.SUPPORT: 0.35},
        "pantheon": {Role.SUPPORT: 0.45, Role.MID: 0.35, Role.TOP: 0.20},
    }

    KNOWN_PRIMARY_ROLES: Dict[str, Role] = {
        "blitzcrank": Role.SUPPORT,
        "thresh": Role.SUPPORT,
        "darius": Role.TOP,
        "garen": Role.TOP,
        "jinx": Role.BOT,
        "vayne": Role.BOT,
        "ashe": Role.BOT,
        "caitlyn": Role.BOT,
        "ezreal": Role.BOT,
        "lucian": Role.BOT,
        "kai'sa": Role.BOT,
        "varus": Role.BOT,
        "leona": Role.SUPPORT,
        "nami": Role.SUPPORT,
        "lulu": Role.SUPPORT,
        "alistar": Role.SUPPORT,
        "braum": Role.SUPPORT,
        "rakan": Role.SUPPORT,
        "pyke": Role.SUPPORT,
        "senna": Role.SUPPORT,
        "tahm kench": Role.SUPPORT,
        "lee sin": Role.JUNGLE,
        "viego": Role.JUNGLE,
        "xin zhao": Role.JUNGLE,
        "sejuani": Role.JUNGLE,
        "jarvan iv": Role.JUNGLE,
        "orianna": Role.MID,
        "syndra": Role.MID,
        "ahri": Role.MID,
        "leblanc": Role.MID,
        "azir": Role.MID,
        "aatrox": Role.TOP,
        "renekton": Role.TOP,
        "k'sante": Role.TOP,
        "jax": Role.TOP,
        "fiora": Role.TOP,
        "camille": Role.TOP,
        "malphite": Role.TOP,
        "gnar": Role.TOP,
    }

    KNOWN_DUAL_COUNTERS: Dict[str, List[str]] = {
        "rumble": ["Galio", "Renekton", "K'Sante", "Jayce"],
        "poppy": ["Olaf", "Gwen", "Trundle", "Morgana"],
        "corki": ["Lucian", "Tristana", "Jayce", "Syndra"],
        "tristana": ["Corki", "Yasuo", "Ashe", "Syndra"],
        "maokai": ["Olaf", "Sylas", "Trundle", "Braum"],
        "gragas": ["Aatrox", "Camille", "Sylas", "Olaf"],
    }

    def __init__(self, flex_probability_threshold: float = 0.15):
        self.flex_probability_threshold = flex_probability_threshold

    def is_flex_pick(self, champion_id: str, patch_meta_dist: Optional[Dict[Role, int]] = None) -> bool:
        return self.analyze_champion(champion_id, patch_meta_dist).is_flex

    def analyze_champion(
        self,
        champion_id: str,
        patch_meta_dist: Optional[Dict[Role, int]] = None,
        team_existing_roles: Optional[Set[Role]] = None,
    ) -> FlexAnalysisResult:
        if team_existing_roles is None:
            team_existing_roles = set()

        slug = champion_id.strip().lower()
        primary_role = self.KNOWN_PRIMARY_ROLES.get(slug, Role.MID)

        base_priors: Dict[Role, float] = {}
        if slug in self.DEFAULT_FLEX_PRIORS:
            base_priors = dict(self.DEFAULT_FLEX_PRIORS[slug])
        else:
            base_priors = {primary_role: 1.0}

        # Blend with empirical distribution if provided
        if patch_meta_dist:
            total_games = sum(patch_meta_dist.values())
            if total_games > 0:
                emp_dist = {r: count / total_games for r, count in patch_meta_dist.items()}
                emp_weight = min(0.9, max(0.5, total_games / (total_games + 20.0)))
                prior_weight = 1.0 - emp_weight
                all_roles = set(base_priors.keys()) | set(emp_dist.keys())
                blended = {}
                for r in all_roles:
                    blended[r] = (emp_dist.get(r, 0.0) * emp_weight) + (base_priors.get(r, 0.0) * prior_weight)
                base_priors = blended

        # Ensure all 5 roles have entries
        for r in Role:
            base_priors.setdefault(r, 0.0)

        # Contextual conditioning (clamp taken roles to 0)
        cond_map = dict(base_priors)
        for r in team_existing_roles:
            cond_map[r] = 0.0

        rem_sum = sum(cond_map.values())
        if rem_sum > 0.0001:
            norm_dist = {r: round(p / rem_sum, 4) for r, p in cond_map.items()}
        else:
            available_roles = [r for r in Role if r not in team_existing_roles]
            if available_roles:
                p_eq = round(1.0 / len(available_roles), 4)
                norm_dist = {r: (p_eq if r in available_roles else 0.0) for r in Role}
            else:
                norm_dist = {r: (1.0 if r == primary_role else 0.0) for r in Role}

        # Normalize sum to 1.0 exactly
        diff = 1.0 - sum(norm_dist.values())
        max_role = max(norm_dist.items(), key=lambda x: x[1])[0]
        norm_dist[max_role] = round(norm_dist[max_role] + diff, 4)

        viable_roles = {r: p for r, p in norm_dist.items() if p >= self.flex_probability_threshold}
        is_flex = len(viable_roles) >= 2

        # Shannon entropy
        entropy = 0.0
        for p in norm_dist.values():
            if p > 0.0001:
                entropy -= p * math.log(p)
        max_entropy = math.log(5.0)
        normalized_entropy = round(min(1.0, max(0.0, entropy / max_entropy)), 4)

        secondary_roles = [r for r, p in norm_dist.items() if r != primary_role and p >= self.flex_probability_threshold]

        return FlexAnalysisResult(
            champion_id=champion_id,
            is_flex=is_flex,
            role_probabilities=norm_dist,
            primary_role=primary_role,
            secondary_roles=secondary_roles,
            flex_entropy=normalized_entropy,
            confidence=1.0,
        )

    def generate_defense_advice(
        self,
        opponent_picks: List[str],
        locked_roles: Optional[Set[Role]] = None,
    ) -> List[FlexDefenseAdvice]:
        if locked_roles is None:
            locked_roles = set()

        advice_list = []
        for pick in opponent_picks:
            analysis = self.analyze_champion(pick, team_existing_roles=locked_roles)
            if not analysis.is_flex:
                continue

            viable_roles = [
                RoleProbability(r, p)
                for r, p in sorted(analysis.role_probabilities.items(), key=lambda x: x[1], reverse=True)
                if p >= self.flex_probability_threshold
            ]

            threat = (
                FlexThreatLevel.CRITICAL
                if (analysis.flex_entropy >= 0.6 or len(viable_roles) >= 3)
                else (FlexThreatLevel.HIGH if analysis.flex_entropy >= 0.4 else FlexThreatLevel.MEDIUM)
            )

            role_str = "/".join(rp.role.value for rp in viable_roles)
            slug = pick.strip().lower()
            counters = self.KNOWN_DUAL_COUNTERS.get(slug, ["Galio", "Renekton", "K'Sante"])

            advice_list.append(
                FlexDefenseAdvice(
                    target_champion=pick,
                    threat_level=threat,
                    candidate_roles=viable_roles,
                    tactical_warnings=[
                        f"Opponent locked {pick} with multi-lane flex potential ({role_str}).",
                        "Do not commit to a rigid lane counter until opponent's final lane assignments are revealed.",
                    ],
                    counter_strategies=[
                        "Draft versatile multi-lane neutralizers or retain your own flex pick in response.",
                        "Defer vulnerable single-lane counter-picks to Phase 2.",
                    ],
                    recommended_dual_counters=counters,
                )
            )

        return advice_list
