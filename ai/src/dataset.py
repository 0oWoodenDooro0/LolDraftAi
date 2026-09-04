import os
import pandas as pd
import numpy as np
from feature_extractor import extract_features, _slugify
from collections import defaultdict

def precompute_meta_tables(df):
    """
    Computes champion meta tiers, pair synergies, head-to-head lane matchups, and team dominance.
    """
    # 1. Champion Meta Stats (Win rate & Tier)
    champ_stats = {}
    grouped_champ = df.groupby("champion")
    for champ, group in grouped_champ:
        picks = len(group)
        wins = group["result"].sum()
        wr = wins / picks if picks > 0 else 0.50
        if wr >= 0.54 and picks >= 10:
            tier = 4.0 # T0
        elif wr >= 0.51:
            tier = 3.0 # T1
        elif wr >= 0.48:
            tier = 2.0 # T2
        elif wr >= 0.45:
            tier = 1.0 # T3
        else:
            tier = 0.0 # T4
        slug = _slugify(champ)
        champ_stats[slug] = {"tier": tier, "winrate": float(wr), "picks": picks}

    # 2. Team Early Dominance & Overall Win Rate
    team_stats = {}
    if "golddiffat15" in df.columns:
        for team, group in df.groupby("teamname"):
            gds = group["golddiffat15"].dropna()
            mean_gd = gds.mean() if len(gds) > 0 else 0.0
            # Dominance scaled around 5.0 (every 1000 gold diff is ~1.0)
            dom = float(np.clip(5.0 + mean_gd / 1000.0, 1.0, 9.5))
            team_wr = float(group["result"].mean())
            team_stats[team] = {"dominance": dom, "winrate": team_wr}

    # 3. Synergy & Matchup Counters from games
    pair_counts = defaultdict(int)
    pair_wins = defaultdict(int)
    matchup_counts = defaultdict(int)
    matchup_wins = defaultdict(int)

    # Group by gameid
    games = df.groupby("gameid")
    for _, group in games:
        blue_rows = group[group["side"].str.lower() == "blue"]
        red_rows = group[group["side"].str.lower() == "red"]
        if len(blue_rows) != 5 or len(red_rows) != 5:
            continue

        b_champs = [_slugify(c) for c in blue_rows["champion"]]
        r_champs = [_slugify(c) for c in red_rows["champion"]]
        b_res = int(blue_rows["result"].iloc[0])

        # Blue synergies
        for i in range(len(b_champs)):
            for j in range(i + 1, len(b_champs)):
                c1, c2 = sorted([b_champs[i], b_champs[j]])
                pair_counts[(c1, c2)] += 1
                if b_res == 1:
                    pair_wins[(c1, c2)] += 1

        # Red synergies
        for i in range(len(r_champs)):
            for j in range(i + 1, len(r_champs)):
                c1, c2 = sorted([r_champs[i], r_champs[j]])
                pair_counts[(c1, c2)] += 1
                if b_res == 0:
                    pair_wins[(c1, c2)] += 1

        # Head to head by position
        b_pos_map = {_slugify(p): _slugify(c) for p, c in zip(blue_rows["position"], blue_rows["champion"])}
        r_pos_map = {_slugify(p): _slugify(c) for p, c in zip(red_rows["position"], red_rows["champion"])}
        for pos, bc in b_pos_map.items():
            if pos in r_pos_map:
                rc = r_pos_map[pos]
                matchup_counts[(bc, rc)] += 1
                if b_res == 1:
                    matchup_wins[(bc, rc)] += 1

    # Calculate final synergy scores with Bayesian smoothing
    synergies = {}
    for (c1, c2), count in pair_counts.items():
        if count >= 3:
            joint_wr = pair_wins[(c1, c2)] / count
            c1_wr = champ_stats.get(c1, {}).get("winrate", 0.50)
            c2_wr = champ_stats.get(c2, {}).get("winrate", 0.50)
            expected = (c1_wr + c2_wr) / 2.0
            # synergy delta in range roughly -1.5 to +1.5
            synergies[(c1, c2)] = float(np.clip((joint_wr - expected) * 4.0, -2.5, 2.5))

    # Calculate final matchup counter scores
    matchups = {}
    for (bc, rc), count in matchup_counts.items():
        if count >= 2:
            wr = matchup_wins[(bc, rc)] / count
            matchups[(bc, rc)] = float(np.clip((wr - 0.50) * 4.0, -3.0, 3.0))

    return champ_stats, synergies, matchups, team_stats

def load_oracles_elixir_dataset(csv_path="/workspace/data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv", max_games=5000):
    """
    Parses Oracle's Elixir match data into 52-dimensional features and Blue win labels.
    Enriches features with dynamic meta tiers, synergies, lane matchups, and team dominance.
    """
    if not os.path.exists(csv_path):
        print(f"Warning: CSV file not found at {csv_path}. Generating synthetic dataset for demonstration.")
        return generate_synthetic_dataset(1000)

    print(f"Loading Oracle's Elixir match data from {csv_path}...")
    
    usecols = ["gameid", "side", "position", "champion", "result", "teamname", "golddiffat15"]
    df = pd.read_csv(csv_path, usecols=usecols, low_memory=False)
    
    # Filter only player rows (position not 'team') and non-empty champion
    df = df[df["position"].str.lower() != "team"]
    df = df[df["champion"].notna() & (df["champion"] != "")]
    
    meta_stats, synergies, matchups, team_stats = precompute_meta_tables(df)
    
    # Group by gameid
    games = df.groupby("gameid")
    
    X_list = []
    y_list = []
    
    count = 0
    for gameid, group in games:
        blue_rows = group[group["side"].str.lower() == "blue"]
        red_rows = group[group["side"].str.lower() == "red"]
        
        blue_champs = blue_rows["champion"].tolist()
        red_champs = red_rows["champion"].tolist()
        
        if len(blue_champs) == 5 and len(red_champs) == 5:
            blue_result = int(blue_rows["result"].iloc[0])
            blue_roles = blue_rows["position"].tolist()
            red_roles = red_rows["position"].tolist()
            
            b_team = blue_rows["teamname"].iloc[0] if "teamname" in blue_rows else None
            r_team = red_rows["teamname"].iloc[0] if "teamname" in red_rows else None
            
            b_info = team_stats.get(b_team, {})
            r_info = team_stats.get(r_team, {})
            b_wr = b_info.get("winrate", 0.50)
            r_wr = r_info.get("winrate", 0.50)
            b_dom = b_info.get("dominance", 5.0)
            r_dom = r_info.get("dominance", 5.0)
            
            vec = extract_features(
                blue_champs=blue_champs,
                red_champs=red_champs,
                blue_winrate=b_wr,
                red_winrate=r_wr,
                meta_stats=meta_stats,
                synergies=synergies,
                matchups=matchups,
                blue_roles=blue_roles,
                red_roles=red_roles,
                blue_dominance=b_dom,
                red_dominance=r_dom,
            )
            X_list.append(vec)
            y_list.append(blue_result)
            
            count += 1
            if max_games and count >= max_games:
                break
                
    print(f"Successfully processed {len(X_list)} complete 5v5 matches with dynamic meta/synergy/matchup features.")
    
    if not X_list:
        return generate_synthetic_dataset(1000)
        
    return np.array(X_list, dtype=np.float32), np.array(y_list, dtype=np.int32)

def generate_synthetic_dataset(num_samples=1000):
    champions = ["Aatrox", "Renekton", "K'Sante", "Sejuani", "Vi", "Orianna", "Azir", "Varus", "Kai'Sa", "Nautilus", "Rell"]
    X_list = []
    y_list = []
    
    rng = np.random.default_rng(42)
    for _ in range(num_samples):
        b_champs = rng.choice(champions, size=5, replace=False).tolist()
        r_champs = rng.choice(champions, size=5, replace=False).tolist()
        
        vec = extract_features(b_champs, r_champs)
        # Prob based on laning + late + side bias
        logit = (vec[10] * 0.1) + (vec[14] * 0.1) + vec[39] * 2.0
        prob = 1.0 / (1.0 + np.exp(-logit))
        label = int(rng.random() < prob)
        
        X_list.append(vec)
        y_list.append(label)
        
    return np.array(X_list, dtype=np.float32), np.array(y_list, dtype=np.int32)


def load_empirical_dataset(
    csv_path="/workspace/data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv",
    max_games=None,
    augment_symmetric=True,
    include_partial_drafts=False,
):
    """
    Loads matches from Oracle's Elixir into 21-dimensional objective empirical draft vectors.
    Optionally applies symmetric data augmentation (Blue<->Red, label=1-y) and partial draft slices.
    """
    from empirical_feature_extractor import EmpiricalFeatureExtractor

    extractor = EmpiricalFeatureExtractor()

    if not os.path.exists(csv_path):
        candidates = [
            csv_path,
            "data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv",
            "../data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv",
        ]
        for c in candidates:
            if os.path.exists(c):
                csv_path = c
                break

    if not os.path.exists(csv_path):
        print(f"Warning: CSV file not found at {csv_path}. Generating synthetic empirical dataset.")
        # Mock fallback
        X = np.zeros((100, 21), dtype=np.float32)
        y = np.array([0, 1] * 50, dtype=np.int32)
        return X, y

    usecols = ["gameid", "side", "position", "champion", "result"]
    df = pd.read_csv(csv_path, usecols=usecols, low_memory=False)
    df = df[df["position"].str.lower() != "team"]
    df = df[df["champion"].notna() & (df["champion"] != "")]

    games = df.groupby("gameid")

    X_list = []
    y_list = []
    count = 0

    for _, group in games:
        blue_rows = group[group["side"].str.lower() == "blue"]
        red_rows = group[group["side"].str.lower() == "red"]

        if len(blue_rows) == 5 and len(red_rows) == 5:
            blue_champs = blue_rows["champion"].tolist()
            red_champs = red_rows["champion"].tolist()
            blue_result = int(blue_rows["result"].iloc[0])

            # 1. Full match
            vec = extractor.extract(blue_champs, red_champs)
            X_list.append(vec)
            y_list.append(blue_result)

            if augment_symmetric:
                vec_sym = extractor.extract(red_champs, blue_champs)
                X_list.append(vec_sym)
                y_list.append(1 - blue_result)

            # 2. Optional partial draft slices (e.g. 1 pick vs 1 pick, 3 picks vs 3 picks)
            if include_partial_drafts:
                for k in [1, 3]:
                    vec_part = extractor.extract(blue_champs[:k], red_champs[:k])
                    X_list.append(vec_part)
                    y_list.append(blue_result)

                    if augment_symmetric:
                        vec_part_sym = extractor.extract(red_champs[:k], blue_champs[:k])
                        X_list.append(vec_part_sym)
                        y_list.append(1 - blue_result)

            count += 1
            if max_games and count >= max_games:
                break

    return np.array(X_list, dtype=np.float32), np.array(y_list, dtype=np.int32)

