import os
import pandas as pd
import numpy as np
from feature_extractor import extract_features

def load_oracles_elixir_dataset(csv_path="/workspace/data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv", max_games=5000):
    """
    Parses Oracle's Elixir match data into 52-dimensional features and Blue win labels.
    """
    if not os.path.exists(csv_path):
        print(f"Warning: CSV file not found at {csv_path}. Generating synthetic dataset for demonstration.")
        return generate_synthetic_dataset(1000)

    print(f"Loading Oracle's Elixir match data from {csv_path}...")
    
    # Read only required columns to be memory efficient and fast
    usecols = ["gameid", "side", "position", "champion", "result", "teamname"]
    df = pd.read_csv(csv_path, usecols=usecols, low_memory=False)
    
    # Filter only player rows (position not 'team') and non-empty champion
    df = df[df["position"].str.lower() != "team"]
    df = df[df["champion"].notna() & (df["champion"] != "")]
    
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
            # Result for Blue: 1 if won, 0 if lost
            blue_result = int(blue_rows["result"].iloc[0])
            
            vec = extract_features(blue_champs, red_champs)
            X_list.append(vec)
            y_list.append(blue_result)
            
            count += 1
            if max_games and count >= max_games:
                break
                
    print(f"Successfully processed {len(X_list)} complete 5v5 matches.")
    
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
