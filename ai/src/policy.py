"""
LoL Draft AI - Draft Policy & Intent Predictor (Python AI Mirror)
Predicts enemy next-action intent (pick/ban) and recommends optimal counter picks
based on Max-WinRate Gain simulation, flaw mitigation, and flex defense.
"""

from dataclasses import dataclass, field
from enum import Enum
import math
from typing import Dict, List, Optional, Set, Tuple

try:
    from .flex_analyzer import FlexPickAnalyzer, Role
except ImportError:
    from flex_analyzer import FlexPickAnalyzer, Role


class ActionType(str, Enum):
    BAN = "BAN"
    PICK = "PICK"


class Side(str, Enum):
    BLUE = "BLUE"
    RED = "RED"


@dataclass
class ChampionIntentCandidate:
    champion_id: str
    probability: float
    intent_score: float
    predicted_role: Optional[Role] = None
    meta_score: float = 0.0
    player_mastery_score: float = 0.0
    composition_fit_score: float = 0.0
    counter_denial_score: float = 0.0
    rationale: str = ""


@dataclass
class IntentPredictionResult:
    turn_number: int
    acting_side: Side
    action_type: ActionType
    predictions: List[ChampionIntentCandidate]


@dataclass
class PickRecommendation:
    champion_id: str
    recommended_role: Role
    win_rate_gain: float
    predicted_win_rate: float
    base_win_rate: float
    synergy_score: float = 0.0
    counter_score: float = 0.0
    flaws_resolved: List[str] = field(default_factory=list)
    flaws_introduced: List[str] = field(default_factory=list)
    reasons: List[str] = field(default_factory=list)


@dataclass
class RecommendationReport:
    target_side: Side
    turn_number: int
    base_win_rate: float
    recommendations: List[PickRecommendation]
    evaluated_candidate_count: int = 0
    latency_ms: float = 0.0


class DraftPolicyEngine:
    CLASSIC_DUOS = {
        ("lucian", "nami"): 0.85,
        ("xayah", "rakan"): 0.85,
        ("kalista", "renata glasc"): 0.85,
        ("caitlyn", "lux"): 0.80,
        ("draven", "nautilus"): 0.85,
        ("samira", "rell"): 0.80,
    }

    def __init__(self):
        self.flex_analyzer = FlexPickAnalyzer()

    def predict_intent(
        self,
        turn_number: int,
        acting_side: Side,
        action_type: ActionType,
        selected_champions: Set[str],
        patch_meta: Optional[Dict[str, Dict]] = None,
        player_signatures: Optional[Dict[Role, List[str]]] = None,
        team_locked_roles: Optional[Set[Role]] = None,
        team_picks: Optional[List[str]] = None,
        top_n: int = 3,
    ) -> IntentPredictionResult:
        if team_locked_roles is None:
            team_locked_roles = set()

        candidates = [
            champ for champ in self.flex_analyzer.KNOWN_PRIMARY_ROLES.keys()
            if champ not in {s.strip().lower() for s in selected_champions}
        ]

        scored: List[ChampionIntentCandidate] = []
        is_ban = action_type == ActionType.BAN
        team_pick_slugs = {p.strip().lower() for p in (team_picks or [])}

        for champ in candidates:
            slug = champ.strip().lower()
            flex = self.flex_analyzer.analyze_champion(slug, team_existing_roles=team_locked_roles)
            meta_data = patch_meta.get(slug, {}) if patch_meta else {}

            presence = meta_data.get("presence_rate", 0.3)
            tier = meta_data.get("tier", "T2")
            tier_mult = {"T0": 1.0, "T1": 0.8, "T2": 0.6, "T3": 0.4, "T4": 0.2}.get(tier, 0.5)
            meta_score = (tier_mult * 0.6) + (presence * 0.4)

            mastery_score = 0.0
            if player_signatures:
                for role, sigs in player_signatures.items():
                    if any(s.strip().lower() == slug for s in sigs):
                        mastery_score = 0.90

            fit_score = 0.0
            if not is_ban:
                vacant = [r for r in Role if r not in team_locked_roles]
                if any(flex.role_probabilities.get(r, 0.0) >= 0.20 for r in vacant):
                    fit_score += 0.40
                elif flex.primary_role in team_locked_roles:
                    fit_score -= 0.30

            duo_score = 0.0
            matched_partner = None
            if not is_ban and team_pick_slugs:
                for ally in team_pick_slugs:
                    pair1 = (ally, slug)
                    pair2 = (slug, ally)
                    if pair1 in self.CLASSIC_DUOS:
                        duo_score = self.CLASSIC_DUOS[pair1]
                        matched_partner = ally.title()
                        break
                    elif pair2 in self.CLASSIC_DUOS:
                        duo_score = self.CLASSIC_DUOS[pair2]
                        matched_partner = ally.title()
                        break

            if is_ban:
                total_score = meta_score * 0.50 + mastery_score * 0.35 + fit_score * 0.15
            elif duo_score > 0:
                total_score = duo_score * 0.55 + meta_score * 0.20 + mastery_score * 0.15 + fit_score * 0.10
            else:
                total_score = meta_score * 0.35 + mastery_score * 0.30 + fit_score * 0.25

            rationale_parts = []
            if matched_partner:
                rationale_parts.append(f"Bot Duo Synergy with {matched_partner}")
            rationale_parts.append(f"Meta: {tier}")
            if mastery_score > 0:
                rationale_parts.append(f"Mastery: {mastery_score:.1f}")

            scored.append(
                ChampionIntentCandidate(
                    champion_id=champ.title(),
                    probability=0.0,
                    intent_score=round(total_score, 4),
                    predicted_role=flex.primary_role,
                    meta_score=round(meta_score, 4),
                    player_mastery_score=round(mastery_score, 4),
                    composition_fit_score=round(fit_score, 4),
                    rationale="; ".join(rationale_parts),
                )
            )

        total_pool_score = sum(c.intent_score for c in scored)
        if total_pool_score > 0:
            for c in scored:
                c.probability = round(c.intent_score / total_pool_score, 4)
        else:
            uniform = 1.0 / len(scored) if scored else 0.0
            for c in scored:
                c.probability = round(uniform, 4)

        top_candidates = sorted(scored, key=lambda x: x.probability, reverse=True)[:top_n]

        return IntentPredictionResult(
            turn_number=turn_number,
            acting_side=acting_side,
            action_type=action_type,
            predictions=top_candidates,
        )
