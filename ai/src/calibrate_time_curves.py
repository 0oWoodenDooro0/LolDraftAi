import os
import pandas as pd
import numpy as np
from sklearn.linear_model import LogisticRegression
from feature_extractor import get_champion_profile, _slugify

def calibrate_parameters(csv_path="/workspace/data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv", max_games=4000):
    """
    Empirically calibrates early/late game signal weights and turning points
    using pro matches (GD15, First Tower/Dragon, and game length quantiles).
    """
    if not os.path.exists(csv_path):
        return {
            "inflection_points": (15.5, 28.5),
            "early_weights": {"laning": 0.12, "early_bully": 0.10, "matchup": 0.08, "dominance": 0.04},
            "late_weights": {"late_scaling": 0.12, "hyper_carry": 0.10, "durability": 0.03, "cc": 0.03},
        }

    usecols = ["gameid", "side", "position", "champion", "result", "gamelength", "golddiffat15", "firsttower", "firstdragon"]
    df = pd.read_csv(csv_path, usecols=usecols, low_memory=False)
    
    # Filter player rows
    df = df[df["position"].str.lower() != "team"]
    df = df[df["champion"].notna()]
    
    games = df.groupby("gameid")
    
    early_X, early_y = [], []
    late_X, late_y = [], []
    lengths = []
    
    count = 0
    for _, group in games:
        b_rows = group[group["side"].str.lower() == "blue"]
        r_rows = group[group["side"].str.lower() == "red"]
        if len(b_rows) != 5 or len(r_rows) != 5:
            continue
            
        b_champs = b_rows["champion"].tolist()
        r_champs = r_rows["champion"].tolist()
        b_profs = [get_champion_profile(c) for c in b_champs]
        r_profs = [get_champion_profile(c) for c in r_champs]
        
        # Signals
        b_laning = np.mean([p["laning"] for p in b_profs])
        r_laning = np.mean([p["laning"] for p in r_profs])
        d_laning = b_laning - r_laning
        
        b_bully = sum(1 for p in b_profs if "early_bully" in p.get("tags", []))
        r_bully = sum(1 for p in r_profs if "early_bully" in p.get("tags", []))
        d_bully = b_bully - r_bully
        
        b_late = np.mean([p["late"] for p in b_profs])
        r_late = np.mean([p["late"] for p in r_profs])
        d_late = b_late - r_late
        
        b_carry = sum(1 for p in b_profs if "hyper_carry" in p.get("tags", []))
        r_carry = sum(1 for p in r_profs if "hyper_carry" in p.get("tags", []))
        d_carry = b_carry - r_carry
        
        b_dur = np.mean([p["durability"] for p in b_profs])
        r_dur = np.mean([p["durability"] for p in r_profs])
        d_dur = b_dur - r_dur
        
        b_cc = sum(p["cc"] for p in b_profs)
        r_cc = sum(p["cc"] for p in r_profs)
        d_cc = b_cc - r_cc
        
        glen_mins = b_rows["gamelength"].iloc[0] / 60.0
        lengths.append(glen_mins)
        b_res = int(b_rows["result"].iloc[0])
        
        # Early target: GD15 > 0 or won first objectives
        gd15 = b_rows["golddiffat15"].iloc[0] if "golddiffat15" in b_rows and pd.notna(b_rows["golddiffat15"].iloc[0]) else 0.0
        ft = b_rows["firsttower"].iloc[0] if "firsttower" in b_rows and pd.notna(b_rows["firsttower"].iloc[0]) else 0.0
        early_lead = 1 if (gd15 > 0 or ft == 1) else 0
        
        early_X.append([d_laning, d_bully])
        early_y.append(early_lead)
        
        # Late target: For matches lasting > 30 minutes, evaluate win outcome against late composition
        if glen_mins >= 30.0:
            late_X.append([d_late, d_carry, d_dur, d_cc])
            late_y.append(b_res)
            
        count += 1
        if max_games and count >= max_games:
            break

    # Quantiles of game duration determine inflection points
    q25 = float(np.percentile(lengths, 25)) if lengths else 27.5
    # Early phase inflection around plate drop (~15.5m), late inflection around ~28.5m
    t_early = 15.5
    t_late = round(float(np.clip(q25 + 1.0, 27.5, 30.0)), 1)
    
    # Fit logistic models to extract normalized empirical weights
    lr_early = LogisticRegression(C=1.0)
    lr_early.fit(early_X, early_y)
    e_coef = lr_early.coef_[0]
    
    w_laning = float(np.clip(e_coef[0] * 0.15, 0.08, 0.18))
    w_bully = float(np.clip(e_coef[1] * 0.15, 0.06, 0.15))
    
    if len(late_X) > 50:
        lr_late = LogisticRegression(C=1.0)
        lr_late.fit(late_X, late_y)
        l_coef = lr_late.coef_[0]
        w_late = float(np.clip(l_coef[0] * 0.12, 0.08, 0.18))
        w_carry = float(np.clip(l_coef[1] * 0.10, 0.06, 0.14))
        w_dur = float(np.clip(l_coef[2] * 0.04, 0.02, 0.06))
        w_cc = float(np.clip(l_coef[3] * 0.03, 0.02, 0.05))
    else:
        w_late, w_carry, w_dur, w_cc = 0.12, 0.10, 0.03, 0.03

    return {
        "inflection_points": (t_early, t_late),
        "early_weights": {
            "laning": round(w_laning, 3),
            "early_bully": round(w_bully, 3),
            "matchup": 0.08,
            "dominance": 0.04,
        },
        "late_weights": {
            "late_scaling": round(w_late, 3),
            "hyper_carry": round(w_carry, 3),
            "durability": round(w_dur, 3),
            "cc": round(w_cc, 3),
        }
    }

if __name__ == "__main__":
    params = calibrate_parameters()
    print("Calibrated Time Curve Parameters:")
    print(params)
