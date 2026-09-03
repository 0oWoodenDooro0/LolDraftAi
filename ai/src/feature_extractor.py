import numpy as np

# Baseline champion dictionary aligned with ChampionTagRegistry.kt
CHAMPION_PROFILES = {
    "aatrox": {"laning": 8.0, "engage": 7.0, "disengage": 5.0, "waveclear": 7.5, "late": 7.0, "phys": 0.95, "magic": 0.05, "true": 0.0, "durability": 7.5, "cc": 1.8, "tags": ["juggernaut", "early_bully"]},
    "renekton": {"laning": 8.8, "engage": 6.8, "disengage": 4.5, "waveclear": 7.5, "late": 5.5, "phys": 0.90, "magic": 0.10, "true": 0.0, "durability": 7.8, "cc": 1.5, "tags": ["juggernaut", "early_bully"]},
    "k'sante": {"laning": 7.5, "engage": 7.8, "disengage": 6.5, "waveclear": 6.0, "late": 7.8, "phys": 0.60, "magic": 0.20, "true": 0.20, "durability": 9.0, "cc": 2.2, "tags": ["warden_tank", "tank"]},
    "ksante": {"laning": 7.5, "engage": 7.8, "disengage": 6.5, "waveclear": 6.0, "late": 7.8, "phys": 0.60, "magic": 0.20, "true": 0.20, "durability": 9.0, "cc": 2.2, "tags": ["warden_tank", "tank"]},
    "sejuani": {"laning": 5.5, "engage": 9.0, "disengage": 6.5, "waveclear": 5.0, "late": 7.2, "phys": 0.20, "magic": 0.80, "true": 0.0, "durability": 9.2, "cc": 3.2, "tags": ["vanguard_tank", "tank", "hard_engage"]},
    "vi": {"laning": 6.0, "engage": 9.2, "disengage": 4.0, "waveclear": 6.0, "late": 6.5, "phys": 0.85, "magic": 0.15, "true": 0.0, "durability": 7.5, "cc": 2.5, "tags": ["diver", "hard_engage"]},
    "orianna": {"laning": 7.8, "engage": 7.5, "disengage": 7.2, "waveclear": 8.8, "late": 8.5, "phys": 0.05, "magic": 0.95, "true": 0.0, "durability": 4.5, "cc": 1.75, "tags": ["burst_mage", "battlemage", "waveclear_stall"]},
    "azir": {"laning": 7.5, "engage": 7.8, "disengage": 8.0, "waveclear": 9.0, "late": 9.2, "phys": 0.0, "magic": 1.0, "true": 0.0, "durability": 4.8, "cc": 1.5, "tags": ["battlemage", "hyper_carry", "waveclear_stall"]},
    "varus": {"laning": 8.5, "engage": 6.8, "disengage": 5.2, "waveclear": 7.8, "late": 7.8, "phys": 0.70, "magic": 0.30, "true": 0.0, "durability": 4.0, "cc": 2.0, "tags": ["marksman", "poke"]},
    "kai'sa": {"laning": 6.5, "engage": 6.0, "disengage": 5.5, "waveclear": 7.0, "late": 9.0, "phys": 0.50, "magic": 0.40, "true": 0.10, "durability": 4.5, "cc": 0.0, "tags": ["marksman", "hyper_carry"]},
    "kaisa": {"laning": 6.5, "engage": 6.0, "disengage": 5.5, "waveclear": 7.0, "late": 9.0, "phys": 0.50, "magic": 0.40, "true": 0.10, "durability": 4.5, "cc": 0.0, "tags": ["marksman", "hyper_carry"]},
    "nautilus": {"laning": 6.5, "engage": 9.5, "disengage": 6.0, "waveclear": 4.0, "late": 6.8, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 8.8, "cc": 3.5, "tags": ["vanguard_tank", "tank", "hard_engage"]},
    "rell": {"laning": 6.0, "engage": 9.5, "disengage": 6.5, "waveclear": 4.0, "late": 7.0, "phys": 0.10, "magic": 0.90, "true": 0.0, "durability": 8.5, "cc": 3.2, "tags": ["vanguard_tank", "tank", "hard_engage"]},
}

DEFAULT_CHAMP = {"laning": 6.5, "engage": 6.0, "disengage": 5.5, "waveclear": 6.5, "late": 7.0, "phys": 0.5, "magic": 0.5, "true": 0.0, "durability": 6.0, "cc": 1.0, "tags": []}

def get_champion_profile(name: str):
    if not name:
        return DEFAULT_CHAMP
    slug = name.lower().replace(" ", "").replace("'", "")
    return CHAMPION_PROFILES.get(slug, DEFAULT_CHAMP)

def extract_features(blue_champs, red_champs, blue_winrate=0.5, red_winrate=0.5, blue_bias=0.03):
    """
    Extracts the exact 52-dimensional feature vector aligned with Kotlin DraftFeatureExtractor.
    """
    b_profs = [get_champion_profile(c) for c in blue_champs]
    r_profs = [get_champion_profile(c) for c in red_champs]
    
    # 0..4 Blue radar
    b_laning = np.mean([p["laning"] for p in b_profs]) if b_profs else 5.0
    b_engage = np.mean([p["engage"] for p in b_profs]) if b_profs else 5.0
    b_disengage = np.mean([p["disengage"] for p in b_profs]) if b_profs else 5.0
    b_waveclear = np.mean([p["waveclear"] for p in b_profs]) if b_profs else 5.0
    b_late = np.mean([p["late"] for p in b_profs]) if b_profs else 5.0
    
    # 5..9 Red radar
    r_laning = np.mean([p["laning"] for p in r_profs]) if r_profs else 5.0
    r_engage = np.mean([p["engage"] for p in r_profs]) if r_profs else 5.0
    r_disengage = np.mean([p["disengage"] for p in r_profs]) if r_profs else 5.0
    r_waveclear = np.mean([p["waveclear"] for p in r_profs]) if r_profs else 5.0
    r_late = np.mean([p["late"] for p in r_profs]) if r_profs else 5.0
    
    # 10..14 Radar delta
    d_laning = b_laning - r_laning
    d_engage = b_engage - r_engage
    d_disengage = b_disengage - r_disengage
    d_waveclear = b_waveclear - r_waveclear
    d_late = b_late - r_late
    
    # 15..17 Blue damage
    b_phys = np.mean([p["phys"] for p in b_profs]) if b_profs else 0.5
    b_magic = np.mean([p["magic"] for p in b_profs]) if b_profs else 0.5
    b_true = np.mean([p["true"] for p in b_profs]) if b_profs else 0.0
    
    # 18..20 Red damage
    r_phys = np.mean([p["phys"] for p in r_profs]) if r_profs else 0.5
    r_magic = np.mean([p["magic"] for p in r_profs]) if r_profs else 0.5
    r_true = np.mean([p["true"] for p in r_profs]) if r_profs else 0.0
    
    # 21..23 Durability
    b_dur = np.mean([p["durability"] for p in b_profs]) if b_profs else 5.0
    r_dur = np.mean([p["durability"] for p in r_profs]) if r_profs else 5.0
    d_dur = b_dur - r_dur
    
    # 24..26 CC Score
    b_cc = np.sum([p["cc"] for p in b_profs]) if b_profs else 0.0
    r_cc = np.sum([p["cc"] for p in r_profs]) if r_profs else 0.0
    d_cc = b_cc - r_cc
    
    # 27..29 Meta Tier (Default neutral 2.0)
    b_tier = 2.0
    r_tier = 2.0
    d_tier = 0.0
    
    # 30..32 Meta Winrate
    b_mwr = 0.50
    r_mwr = 0.50
    d_mwr = 0.0
    
    # 33..35 Synergy
    b_syn = 0.0
    r_syn = 0.0
    d_syn = 0.0
    
    # 36 Matchup Counter
    d_matchup = 0.0
    
    # 37..38 Team Rating & Dominance Delta
    d_team = blue_winrate - red_winrate
    d_dom = 0.0
    
    # 39..41 Side Advantage
    side_bias = blue_bias
    b_side_pref = 0.0
    r_side_pref = 0.0
    
    # 42..46 Blue Archetypes
    def count_archetypes(profs):
        counts = {"tank": 0, "marksman": 0, "mage": 0, "assassin": 0, "enchanter": 0}
        for p in profs:
            t = p.get("tags", [])
            if "tank" in t or "vanguard_tank" in t or "warden_tank" in t or "juggernaut" in t or p["durability"] >= 8.5:
                counts["tank"] += 1
            if "marksman" in t or "hyper_carry" in t:
                counts["marksman"] += 1
            if "burst_mage" in t or "battlemage" in t:
                counts["mage"] += 1
            if "assassin" in t or "diver" in t:
                counts["assassin"] += 1
            if "enchanter" in t:
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
