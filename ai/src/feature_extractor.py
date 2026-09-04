import numpy as np

import json
import os

def _slugify(name: str) -> str:
    if not name:
        return ""
    return "".join(c for c in name.lower() if c.isalnum())

# Baseline champion dictionary aligned with ChampionTagRegistry.kt
CHAMPION_PROFILES = {
    # Top
    "aatrox": {"laning": 8.0, "engage": 7.0, "disengage": 5.0, "waveclear": 7.5, "late": 7.0, "phys": 0.95, "magic": 0.05, "true": 0.0, "durability": 7.5, "tankiness_tier": "BRUISER", "cc": 2.8, "tags": ["juggernaut", "early_bully"]},
    "renekton": {"laning": 8.8, "engage": 6.8, "disengage": 4.5, "waveclear": 7.5, "late": 5.5, "phys": 0.90, "magic": 0.10, "true": 0.0, "durability": 7.8, "tankiness_tier": "BRUISER", "cc": 2.5, "tags": ["diver", "early_bully"]},
    "jax": {"laning": 7.0, "engage": 6.5, "disengage": 5.0, "waveclear": 5.5, "late": 9.0, "phys": 0.70, "magic": 0.30, "true": 0.0, "durability": 7.2, "tankiness_tier": "BRUISER", "cc": 2.5, "tags": ["skirmisher", "hyper_carry"]},
    "ksante": {"laning": 7.5, "engage": 7.8, "disengage": 7.2, "waveclear": 6.8, "late": 8.2, "phys": 0.75, "magic": 0.15, "true": 0.10, "durability": 9.0, "tankiness_tier": "FRONTLINE_TANK", "cc": 3.5, "tags": ["warden_tank", "tank"]},
    "ambessa": {"laning": 8.5, "engage": 7.5, "disengage": 5.0, "waveclear": 6.0, "late": 7.5, "phys": 0.95, "magic": 0.05, "true": 0.0, "durability": 7.2, "tankiness_tier": "BRUISER", "cc": 2.8, "tags": ["diver", "skirmisher"]},
    "gnar": {"laning": 7.5, "engage": 8.5, "disengage": 6.5, "waveclear": 6.5, "late": 7.0, "phys": 0.85, "magic": 0.15, "true": 0.0, "durability": 6.8, "tankiness_tier": "BRUISER", "cc": 3.2, "tags": ["hard_engage", "early_bully"]},
    "malphite": {"laning": 5.5, "engage": 9.5, "disengage": 3.5, "waveclear": 6.0, "late": 7.5, "phys": 0.20, "magic": 0.80, "true": 0.0, "durability": 9.2, "tankiness_tier": "FRONTLINE_TANK", "cc": 3.2, "tags": ["vanguard_tank", "tank", "hard_engage"]},
    "poppy": {"laning": 6.8, "engage": 6.5, "disengage": 8.8, "waveclear": 5.5, "late": 6.5, "phys": 0.80, "magic": 0.20, "true": 0.0, "durability": 8.8, "tankiness_tier": "FRONTLINE_TANK", "cc": 3.5, "tags": ["warden_tank", "disengage_peel", "tank"]},
    # Jungle
    "sejuani": {"laning": 6.0, "engage": 8.8, "disengage": 6.8, "waveclear": 6.0, "late": 7.2, "phys": 0.20, "magic": 0.80, "true": 0.0, "durability": 9.0, "tankiness_tier": "FRONTLINE_TANK", "cc": 4.0, "tags": ["vanguard_tank", "tank", "hard_engage"]},
    "maokai": {"laning": 6.0, "engage": 9.0, "disengage": 7.5, "waveclear": 6.5, "late": 7.5, "phys": 0.15, "magic": 0.85, "true": 0.0, "durability": 8.8, "tankiness_tier": "FRONTLINE_TANK", "cc": 4.2, "tags": ["vanguard_tank", "tank", "hard_engage"]},
    "leesin": {"laning": 7.8, "engage": 8.0, "disengage": 7.0, "waveclear": 5.5, "late": 5.2, "phys": 0.90, "magic": 0.10, "true": 0.0, "durability": 6.2, "tankiness_tier": "BRUISER", "cc": 2.5, "tags": ["diver", "early_bully"]},
    "vi": {"laning": 6.8, "engage": 9.2, "disengage": 3.8, "waveclear": 6.0, "late": 6.5, "phys": 0.90, "magic": 0.10, "true": 0.0, "durability": 7.0, "tankiness_tier": "BRUISER", "cc": 3.5, "tags": ["diver", "hard_engage"]},
    "jarvaniv": {"laning": 7.2, "engage": 9.2, "disengage": 4.0, "waveclear": 6.2, "late": 6.2, "phys": 0.85, "magic": 0.15, "true": 0.0, "durability": 7.5, "tankiness_tier": "BRUISER", "cc": 3.2, "tags": ["diver", "hard_engage"]},
    "wukong": {"laning": 6.5, "engage": 8.8, "disengage": 5.0, "waveclear": 5.8, "late": 7.0, "phys": 0.90, "magic": 0.10, "true": 0.0, "durability": 7.2, "tankiness_tier": "BRUISER", "cc": 3.0, "tags": ["diver", "hard_engage"]},
    # Mid
    "orianna": {"laning": 7.5, "engage": 8.0, "disengage": 7.5, "waveclear": 8.8, "late": 8.5, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 4.0, "tankiness_tier": "SQUISHY", "cc": 2.8, "tags": ["burst_mage", "waveclear_stall"]},
    "azir": {"laning": 7.2, "engage": 8.0, "disengage": 7.5, "waveclear": 8.5, "late": 9.2, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 4.2, "tankiness_tier": "SQUISHY", "cc": 2.8, "tags": ["battlemage", "hyper_carry", "waveclear_stall"]},
    "ahri": {"laning": 7.5, "engage": 7.8, "disengage": 7.0, "waveclear": 8.0, "late": 7.0, "phys": 0.05, "magic": 0.80, "true": 0.15, "durability": 4.0, "tankiness_tier": "SQUISHY", "cc": 2.8, "tags": ["burst_mage"]},
    "syndra": {"laning": 8.2, "engage": 7.0, "disengage": 7.2, "waveclear": 8.5, "late": 8.2, "phys": 0.05, "magic": 0.90, "true": 0.05, "durability": 3.8, "tankiness_tier": "SQUISHY", "cc": 2.8, "tags": ["burst_mage"]},
    "sylas": {"laning": 6.8, "engage": 7.5, "disengage": 5.0, "waveclear": 6.5, "late": 8.2, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 6.5, "tankiness_tier": "BRUISER", "cc": 2.5, "tags": ["skirmisher", "diver"]},
    "jayce": {"laning": 8.0, "engage": 5.5, "disengage": 5.5, "waveclear": 8.5, "late": 7.5, "phys": 0.90, "magic": 0.10, "true": 0.0, "durability": 5.0, "tankiness_tier": "SQUISHY", "cc": 1.8, "tags": ["poke", "early_bully"]},
    "leblanc": {"laning": 8.2, "engage": 7.2, "disengage": 6.5, "waveclear": 6.0, "late": 6.8, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 2.5, "tags": ["assassin"]},
    "kassadin": {"laning": 4.5, "engage": 7.5, "disengage": 6.5, "waveclear": 6.0, "late": 9.8, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 5.5, "tankiness_tier": "BRUISER", "cc": 1.0, "tags": ["assassin", "hyper_carry"]},
    # Bot ADCs
    "corki": {"laning": 7.5, "engage": 5.0, "disengage": 5.0, "waveclear": 8.5, "late": 8.8, "phys": 0.85, "magic": 0.15, "true": 0.0, "durability": 3.8, "tankiness_tier": "SQUISHY", "cc": 0.5, "tags": ["marksman", "poke", "hyper_carry"]},
    "smolder": {"laning": 6.0, "engage": 4.5, "disengage": 5.0, "waveclear": 8.0, "late": 9.8, "phys": 0.65, "magic": 0.20, "true": 0.15, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 0.5, "tags": ["marksman", "hyper_carry", "poke"]},
    "jinx": {"laning": 6.2, "engage": 4.0, "disengage": 4.5, "waveclear": 8.5, "late": 9.5, "phys": 0.95, "magic": 0.05, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 2.5, "tags": ["marksman", "hyper_carry", "waveclear_stall"]},
    "kaisa": {"laning": 6.5, "engage": 6.5, "disengage": 6.0, "waveclear": 7.2, "late": 9.0, "phys": 0.55, "magic": 0.40, "true": 0.05, "durability": 4.0, "tankiness_tier": "SQUISHY", "cc": 0.0, "tags": ["marksman", "hyper_carry", "diver"]},
    "varus": {"laning": 8.5, "engage": 7.5, "disengage": 5.0, "waveclear": 8.2, "late": 7.2, "phys": 0.65, "magic": 0.35, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 3.0, "tags": ["marksman", "poke", "early_bully"]},
    "ashe": {"laning": 7.8, "engage": 8.5, "disengage": 6.0, "waveclear": 7.0, "late": 7.5, "phys": 0.85, "magic": 0.15, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 4.0, "tags": ["marksman", "hard_engage"]},
    "kalista": {"laning": 9.0, "engage": 7.5, "disengage": 6.0, "waveclear": 6.5, "late": 6.0, "phys": 0.95, "magic": 0.05, "true": 0.0, "durability": 3.8, "tankiness_tier": "SQUISHY", "cc": 3.0, "tags": ["marksman", "early_bully"]},
    "lucian": {"laning": 8.5, "engage": 5.5, "disengage": 6.0, "waveclear": 7.0, "late": 7.0, "phys": 0.85, "magic": 0.15, "true": 0.0, "durability": 4.0, "tankiness_tier": "SQUISHY", "cc": 0.0, "tags": ["marksman", "early_bully"]},
    "caitlyn": {"laning": 8.8, "engage": 4.0, "disengage": 5.5, "waveclear": 8.0, "late": 8.0, "phys": 0.95, "magic": 0.05, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 2.5, "tags": ["marksman", "early_bully", "poke"]},
    "draven": {"laning": 9.5, "engage": 6.0, "disengage": 5.0, "waveclear": 6.5, "late": 7.0, "phys": 0.95, "magic": 0.05, "true": 0.0, "durability": 4.0, "tankiness_tier": "SQUISHY", "cc": 2.0, "tags": ["marksman", "early_bully"]},
    "samira": {"laning": 7.5, "engage": 8.0, "disengage": 5.0, "waveclear": 6.5, "late": 8.0, "phys": 0.90, "magic": 0.10, "true": 0.0, "durability": 4.5, "tankiness_tier": "SQUISHY", "cc": 1.5, "tags": ["marksman", "diver"]},
    "zeri": {"laning": 6.0, "engage": 5.0, "disengage": 6.5, "waveclear": 7.5, "late": 9.2, "phys": 0.80, "magic": 0.20, "true": 0.0, "durability": 3.8, "tankiness_tier": "SQUISHY", "cc": 1.5, "tags": ["marksman", "hyper_carry"]},
    # Supports
    "nautilus": {"laning": 7.5, "engage": 9.5, "disengage": 4.5, "waveclear": 4.0, "late": 6.0, "phys": 0.15, "magic": 0.85, "true": 0.0, "durability": 8.8, "tankiness_tier": "FRONTLINE_TANK", "cc": 4.5, "tags": ["vanguard_tank", "tank", "hard_engage"]},
    "leona": {"laning": 7.2, "engage": 9.8, "disengage": 3.5, "waveclear": 3.5, "late": 6.0, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 9.0, "tankiness_tier": "FRONTLINE_TANK", "cc": 4.5, "tags": ["vanguard_tank", "tank", "hard_engage"]},
    "rakan": {"laning": 6.8, "engage": 9.5, "disengage": 8.0, "waveclear": 4.5, "late": 7.5, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 6.0, "tankiness_tier": "BRUISER", "cc": 3.8, "tags": ["catcher", "hard_engage", "disengage_peel"]},
    "thresh": {"laning": 7.2, "engage": 8.5, "disengage": 8.2, "waveclear": 4.0, "late": 6.8, "phys": 0.20, "magic": 0.80, "true": 0.0, "durability": 7.2, "tankiness_tier": "BRUISER", "cc": 3.8, "tags": ["catcher", "disengage_peel"]},
    "nami": {"laning": 7.8, "engage": 7.2, "disengage": 8.5, "waveclear": 4.0, "late": 6.5, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 3.5, "tags": ["enchanter", "disengage_peel"]},
    "lulu": {"laning": 7.5, "engage": 5.0, "disengage": 9.5, "waveclear": 4.0, "late": 8.0, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 3.5, "tags": ["enchanter", "disengage_peel"]},
    "milio": {"laning": 7.0, "engage": 4.5, "disengage": 9.0, "waveclear": 4.0, "late": 8.2, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 3.5, "tankiness_tier": "SQUISHY", "cc": 3.0, "tags": ["enchanter", "disengage_peel"]},
    "lux": {"laning": 7.5, "engage": 6.0, "disengage": 6.5, "waveclear": 8.0, "late": 7.0, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 3.2, "tankiness_tier": "SQUISHY", "cc": 3.0, "tags": ["burst_mage", "poke"]},
    "renataglasc": {"laning": 7.0, "engage": 6.5, "disengage": 9.0, "waveclear": 4.0, "late": 8.0, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 4.0, "tankiness_tier": "SQUISHY", "cc": 3.5, "tags": ["catcher", "disengage_peel"]},
    "rell": {"laning": 6.0, "engage": 9.5, "disengage": 6.5, "waveclear": 4.0, "late": 7.0, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 8.5, "tankiness_tier": "FRONTLINE_TANK", "cc": 4.2, "tags": ["vanguard_tank", "tank", "hard_engage"]},
}

def _load_champion_profiles():
    candidates = [
        os.path.join(os.path.dirname(__file__), "../../data/champion_tags.json"),
        os.path.join(os.path.dirname(__file__), "../../../data/champion_tags.json"),
        os.path.join(os.path.dirname(__file__), "../../src/main/resources/data/champion_tags.json"),
        os.path.join(os.getcwd(), "data/champion_tags.json"),
        "/workspace/data/champion_tags.json",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                for item in data:
                    cid = item.get("championId", "")
                    radar = item.get("radar", {})
                    dmg = item.get("damageProfile", {})
                    dur = item.get("durability", {})
                    cc = item.get("ccRating", {})
                    tags = [t.lower() for t in item.get("tags", [])]
                    cc_dur = float(cc.get("hardCcDurationSeconds", 1.0))
                    if cc.get("hasReliableHardCc", False):
                        cc_dur += 1.0
                    profile = {
                        "laning": float(radar.get("laningStrength", 6.5)),
                        "engage": float(radar.get("engage", 6.0)),
                        "disengage": float(radar.get("disengage", 5.5)),
                        "waveclear": float(radar.get("waveclear", 6.5)),
                        "late": float(radar.get("lateGameScaling", 7.0)),
                        "phys": float(dmg.get("physicalRatio", 0.5)),
                        "magic": float(dmg.get("magicRatio", 0.5)),
                        "true": float(dmg.get("trueRatio", 0.0)),
                        "durability": float(dur.get("durabilityScore", 6.0)),
                        "tankiness_tier": dur.get("tankinessTier", "BRUISER"),
                        "cc": cc_dur,
                        "tags": tags,
                    }
                    CHAMPION_PROFILES[_slugify(cid)] = profile
                    disp = item.get("displayName", "")
                    if disp:
                        CHAMPION_PROFILES[_slugify(disp)] = profile
                break
            except Exception:
                pass

_load_champion_profiles()

DEFAULT_CHAMP = {"laning": 6.5, "engage": 6.0, "disengage": 5.5, "waveclear": 6.5, "late": 7.0, "phys": 0.5, "magic": 0.5, "true": 0.0, "durability": 6.0, "tankiness_tier": "BRUISER", "cc": 1.0, "tags": []}

ROLE_PRIORS = {
    "top": {"laning": 7.8, "engage": 6.8, "disengage": 5.0, "waveclear": 6.8, "late": 7.2, "phys": 0.75, "magic": 0.25, "true": 0.0, "durability": 7.5, "cc": 2.0},
    "jungle": {"laning": 6.8, "engage": 8.2, "disengage": 5.5, "waveclear": 6.2, "late": 7.0, "phys": 0.65, "magic": 0.35, "true": 0.0, "durability": 7.2, "cc": 2.8},
    "mid": {"laning": 7.5, "engage": 6.8, "disengage": 6.5, "waveclear": 8.2, "late": 8.2, "phys": 0.20, "magic": 0.80, "true": 0.0, "durability": 4.5, "cc": 2.0},
    "bot": {"laning": 7.2, "engage": 5.5, "disengage": 5.5, "waveclear": 7.8, "late": 8.8, "phys": 0.85, "magic": 0.15, "true": 0.0, "durability": 3.8, "cc": 1.0},
    "support": {"laning": 6.8, "engage": 7.8, "disengage": 7.8, "waveclear": 4.5, "late": 6.8, "phys": 0.15, "magic": 0.85, "true": 0.0, "durability": 6.0, "cc": 3.2},
}

DEFAULT_PRIOR = {
    "laning": 7.22, "engage": 7.02, "disengage": 6.06, "waveclear": 6.70, "late": 7.60,
    "phys": 0.52, "magic": 0.48, "true": 0.0,
    "durability": 5.8, "cc": 2.2, "tankiness_tier": "BRUISER", "tags": []
}

def _get_missing_priors(champs, roles=None):
    missing_count = max(0, 5 - len(champs))
    if missing_count == 0:
        return []
    all_roles = ["top", "jungle", "mid", "bot", "support"]
    taken_roles = [r.lower() for r in (roles or []) if r]
    untaken_roles = [r for r in all_roles if r not in taken_roles]
    priors = []
    for _ in range(missing_count):
        if untaken_roles:
            role = untaken_roles.pop(0)
            priors.append(ROLE_PRIORS.get(role, DEFAULT_PRIOR))
        else:
            priors.append(DEFAULT_PRIOR)
    return priors

def get_champion_profile(name: str):
    if not name:
        return DEFAULT_CHAMP
    slug = _slugify(name)
    if slug in CHAMPION_PROFILES:
        return CHAMPION_PROFILES[slug]
    if slug == "nunu":
        return CHAMPION_PROFILES.get("nunuwillump", DEFAULT_CHAMP)
    if slug == "monkeyking":
        return CHAMPION_PROFILES.get("wukong", DEFAULT_CHAMP)
    return DEFAULT_CHAMP

def extract_features(
    blue_champs,
    red_champs,
    blue_winrate=0.5,
    red_winrate=0.5,
    blue_bias=0.03,
    meta_stats=None,
    synergies=None,
    matchups=None,
    blue_roles=None,
    red_roles=None,
    blue_dominance=5.0,
    red_dominance=5.0,
):
    """
    Extracts the exact 52-dimensional feature vector aligned with Kotlin DraftFeatureExtractor.
    Supports Role Prior Imputation for partial drafts and dynamic meta/synergy/matchup inputs.
    """
    b_profs = [get_champion_profile(c) for c in blue_champs]
    r_profs = [get_champion_profile(c) for c in red_champs]
    
    b_priors = _get_missing_priors(blue_champs, blue_roles)
    r_priors = _get_missing_priors(red_champs, red_roles)
    
    b_all = b_profs + b_priors
    r_all = r_profs + r_priors
    b_slots = max(1, len(b_all))
    r_slots = max(1, len(r_all))
    
    # 0..4 Blue radar
    b_laning = np.mean([p["laning"] for p in b_all])
    b_engage = np.mean([p["engage"] for p in b_all])
    b_disengage = np.mean([p["disengage"] for p in b_all])
    b_waveclear = np.mean([p["waveclear"] for p in b_all])
    b_late = np.mean([p["late"] for p in b_all])
    
    # 5..9 Red radar
    r_laning = np.mean([p["laning"] for p in r_all])
    r_engage = np.mean([p["engage"] for p in r_all])
    r_disengage = np.mean([p["disengage"] for p in r_all])
    r_waveclear = np.mean([p["waveclear"] for p in r_all])
    r_late = np.mean([p["late"] for p in r_all])
    
    # 10..14 Radar delta
    d_laning = b_laning - r_laning
    d_engage = b_engage - r_engage
    d_disengage = b_disengage - r_disengage
    d_waveclear = b_waveclear - r_waveclear
    d_late = b_late - r_late
    
    # 15..17 Blue damage
    b_phys = sum(p["phys"] for p in b_all) / b_slots
    b_magic = sum(p["magic"] for p in b_all) / b_slots
    b_true = sum(p.get("true", 0.0) for p in b_all) / b_slots
    
    # 18..20 Red damage
    r_phys = sum(p["phys"] for p in r_all) / r_slots
    r_magic = sum(p["magic"] for p in r_all) / r_slots
    r_true = sum(p.get("true", 0.0) for p in r_all) / r_slots
    
    # 21..23 Durability
    b_dur = sum(p["durability"] for p in b_all) / b_slots
    r_dur = sum(p["durability"] for p in r_all) / r_slots
    d_dur = b_dur - r_dur
    
    # 24..26 CC Score
    b_cc = sum(p["cc"] for p in b_all)
    r_cc = sum(p["cc"] for p in r_all)
    d_cc = b_cc - r_cc
    
    # 27..29 Meta Tier & 30..32 Meta Winrate
    def calc_meta(champs):
        if not champs or not meta_stats:
            return 2.0, 0.50
        tiers, wrs = [], []
        for c in champs:
            slug = _slugify(c)
            st = meta_stats.get(slug, {})
            tiers.append(float(st.get("tier", 2.0)))
            wrs.append(float(st.get("winrate", 0.50)))
        return (float(np.mean(tiers)) if tiers else 2.0), (float(np.mean(wrs)) if wrs else 0.50)

    b_tier, b_mwr = calc_meta(blue_champs)
    r_tier, r_mwr = calc_meta(red_champs)
    d_tier = b_tier - r_tier
    d_mwr = b_mwr - r_mwr
    
    # 33..35 Synergy
    def calc_synergy(champs):
        if not champs or not synergies or len(champs) < 2:
            return 0.0
        total_syn = 0.0
        slugs = [_slugify(c) for c in champs]
        for i in range(len(slugs)):
            for j in range(i + 1, len(slugs)):
                s1, s2 = slugs[i], slugs[j]
                syn = synergies.get((s1, s2), synergies.get((s2, s1), 0.0))
                total_syn += syn
        return float(total_syn)

    b_syn = calc_synergy(blue_champs)
    r_syn = calc_synergy(red_champs)
    d_syn = b_syn - r_syn
    
    # 36 Matchup Counter
    d_matchup = 0.0
    if matchups:
        if blue_roles and red_roles and len(blue_champs) == len(blue_roles) and len(red_champs) == len(red_roles):
            role_to_red = {_slugify(r): _slugify(c) for c, r in zip(red_champs, red_roles)}
            for bc, br in zip(blue_champs, blue_roles):
                rc = role_to_red.get(_slugify(br))
                if rc:
                    bslug = _slugify(bc)
                    score = matchups.get((bslug, rc), -matchups.get((rc, bslug), 0.0))
                    d_matchup += score
        else:
            for bc in blue_champs:
                for rc in red_champs:
                    bslug, rslug = _slugify(bc), _slugify(rc)
                    if (bslug, rslug) in matchups:
                        d_matchup += matchups[(bslug, rslug)]
                    elif (rslug, bslug) in matchups:
                        d_matchup -= matchups[(rslug, bslug)]
    
    # 37..38 Team Rating & Dominance Delta
    d_team = blue_winrate - red_winrate
    d_dom = blue_dominance - red_dominance
    
    # 39..41 Side Advantage
    side_bias = blue_bias
    b_side_pref = 0.0
    r_side_pref = 0.0
    
    # 42..46 Blue Archetypes
    def count_archetypes(profs):
        counts = {"tank": 0, "marksman": 0, "mage": 0, "assassin": 0, "enchanter": 0}
        for p in profs:
            t = p.get("tags", [])
            tier = p.get("tankiness_tier", "")
            if "tank" in t or "vanguard_tank" in t or "warden_tank" in t or "juggernaut" in t or tier == "FRONTLINE_TANK" or p.get("durability", 0) >= 8.5:
                counts["tank"] += 1
            if "marksman" in t or "hyper_carry" in t:
                counts["marksman"] += 1
            if "burst_mage" in t or "battlemage" in t or "artillery_mage" in t:
                counts["mage"] += 1
            if "assassin" in t or "skirmisher" in t or "diver" in t:
                counts["assassin"] += 1
            if "enchanter" in t or "disengage_peel" in t:
                counts["enchanter"] += 1
        return counts

    b_arch = count_archetypes(b_profs)
    r_arch = count_archetypes(r_profs)
    
    vec = [
        b_laning, b_engage, b_disengage, b_waveclear, b_late,
        r_laning, r_engage, r_disengage, r_waveclear, r_late,
        d_laning, d_engage, d_disengage, d_waveclear, d_late,
        b_phys, b_magic, b_true,
        r_phys, r_magic, r_true,
        b_dur, r_dur, d_dur,
        b_cc, r_cc, d_cc,
        b_tier, r_tier, d_tier,
        b_mwr, r_mwr, d_mwr,
        b_syn, r_syn, d_syn,
        d_matchup,
        d_team, d_dom,
        side_bias, b_side_pref, r_side_pref,
        b_arch["tank"], b_arch["marksman"], b_arch["mage"], b_arch["assassin"], b_arch["enchanter"],
        r_arch["tank"], r_arch["marksman"], r_arch["mage"], r_arch["assassin"], r_arch["enchanter"]
    ]
    return np.array(vec, dtype=np.float32)
    return np.array(vec, dtype=np.float32)
