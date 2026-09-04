import json
import logging
from typing import Dict, List, Optional, Tuple, Any
import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

# Complete list of League champions as of patch 15.x / 16.x (170 champions)
# (Will also dynamically expand if new champions are encountered)
DEFAULT_CHAMPION_LIST = [
    "Aatrox", "Ahri", "Akali", "Akshan", "Alistar", "Ambessa", "Amumu", "Anivia", "Annie", "Aphelios",
    "Ashe", "Aurelion Sol", "Aurora", "Azir", "Bard", "Bel'Veth", "Blitzcrank", "Brand", "Braum", "Briar",
    "Caitlyn", "Camille", "Cassiopeia", "Cho'Gath", "Corki", "Darius", "Diana", "Dr. Mundo", "Draven", "Ekko",
    "Elise", "Evelynn", "Ezreal", "Fiddlesticks", "Fiora", "Fizz", "Galio", "Gangplank", "Garen", "Gnar",
    "Gragas", "Graves", "Gwen", "Hecarim", "Heimerdinger", "Hwei", "Illaoi", "Irelia", "Ivern", "Janna",
    "Jarvan IV", "Jax", "Jayce", "Jhin", "Jinx", "K'Sante", "Kai'Sa", "Kalista", "Karma", "Karthus",
    "Kassadin", "Katarina", "Kayle", "Kayn", "Kennen", "Kha'Zix", "Kindred", "Kled", "Kog'Maw", "LeBlanc",
    "Lee Sin", "Leona", "Lillia", "Lissandra", "Lucian", "Lulu", "Lux", "Malphite", "Malzahar", "Maokai",
    "Master Yi", "Milio", "Miss Fortune", "Mordekaiser", "Morgana", "Naafiri", "Nami", "Nasus", "Nautilus", "Neeko",
    "Nidalee", "Nilah", "Nocturne", "Nunu & Willump", "Olaf", "Orianna", "Ornn", "Pantheon", "Poppy", "Pyke",
    "Qiyana", "Quinn", "Rakan", "Rammus", "Rek'Sai", "Rell", "Renata Glasc", "Renekton", "Rengar", "Riven",
    "Rumble", "Ryze", "Samira", "Sejuani", "Senna", "Seraphine", "Sett", "Shaco", "Shen", "Shyvana",
    "Singed", "Sion", "Sivir", "Skarner", "Smolder", "Sona", "Soraka", "Swain", "Sylas", "Syndra",
    "Tahm Kench", "Taliyah", "Talon", "Taric", "Teemo", "Thresh", "Tristana", "Trundle", "Tryndamere", "Twisted Fate",
    "Twitch", "Udyr", "Urgot", "Varus", "Vayne", "Veigar", "Vel'Koz", "Vex", "Vi", "Viego",
    "Viktor", "Vladimir", "Volibear", "Warwick", "Wukong", "Xayah", "Xerath", "Xin Zhao", "Yasuo", "Yone",
    "Yorick", "Yuumi", "Zac", "Zed", "Zeri", "Ziggs", "Zilean", "Zoe", "Zyra"
]

class EmpiricalStatsExtractor:
    """
    Computes 100% objective, empirical champion and synergy/counter statistics
    from professional Oracle's Elixir match records.
    Applies empirical Bayesian shrinkage toward neutral priors for low-sample/unseen picks.
    """
    def __init__(self, all_champions: Optional[List[str]] = None, prior_games: float = 10.0):
        champs = sorted(list(set(all_champions or DEFAULT_CHAMPION_LIST)))
        self.champ_to_id: Dict[str, int] = {c: idx + 1 for idx, c in enumerate(champs)}
        self.id_to_champ: Dict[int, str] = {idx + 1: c for idx, c in enumerate(champs)}
        self.prior_games = prior_games

    @staticmethod
    def get_synergy_key(c1: str, c2: str) -> str:
        pair = sorted([c1, c2])
        return f"{pair[0]}|{pair[1]}"

    @staticmethod
    def get_counter_key(c1: str, c2: str) -> str:
        return f"{c1}|{c2}"

    def compute_stats(self, df: pd.DataFrame) -> Dict[str, Any]:
        # Filter to player rows (participantid 1..10)
        p_df = df[df["participantid"].isin(range(1, 11))].copy()
        
        # Ensure champion names are strings
        p_df = p_df[p_df["champion"].notna()]
        p_df["champion"] = p_df["champion"].astype(str)

        # Update champ_to_id for any encountered champion not in initial list
        encountered = sorted(p_df["champion"].unique())
        for c in encountered:
            if c not in self.champ_to_id:
                new_id = len(self.champ_to_id) + 1
                self.champ_to_id[c] = new_id
                self.id_to_champ[new_id] = c

        # Numeric conversions with safe defaults
        numeric_cols = [
            "golddiffat15", "csdiffat15", "dpm", "damagetakenperminute",
            "damagemitigatedperminute", "firsttower", "firstdragon", "result"
        ]
        for col in numeric_cols:
            if col in p_df.columns:
                p_df[col] = pd.to_numeric(p_df[col], errors="coerce").fillna(0.0)
            else:
                p_df[col] = 0.0

        # Global empirical averages for shrinkage
        global_dpm = float(p_df["dpm"].mean()) if len(p_df) > 0 else 500.0
        global_dtpm = float(p_df["damagetakenperminute"].mean()) if len(p_df) > 0 else 600.0
        global_dmpm = float(p_df["damagemitigatedperminute"].mean()) if len(p_df) > 0 else 600.0
        global_ft = float(p_df["firsttower"].mean()) if len(p_df) > 0 else 0.5
        global_fd = float(p_df["firstdragon"].mean()) if len(p_df) > 0 else 0.5

        # Individual champion stats
        champions_stats: Dict[str, Dict[str, Any]] = {}
        grouped = p_df.groupby("champion")

        # First populate all known champions with priors
        for c, cid in self.champ_to_id.items():
            champions_stats[c] = {
                "id": cid,
                "picks": 0,
                "wins": 0,
                "win_rate": 0.5,
                "gd15": 0.0,
                "csd15": 0.0,
                "dpm": global_dpm,
                "dtpm": global_dtpm,
                "dmpm": global_dmpm,
                "first_tower_rate": global_ft,
                "first_dragon_rate": global_fd,
            }

        for c, group in grouped:
            cid = self.champ_to_id[c]
            picks = int(len(group))
            wins = int(group["result"].sum())
            sum_gd15 = float(group["golddiffat15"].sum())
            sum_csd15 = float(group["csdiffat15"].sum())
            sum_dpm = float(group["dpm"].sum())
            sum_dtpm = float(group["damagetakenperminute"].sum())
            sum_dmpm = float(group["damagemitigatedperminute"].sum())
            sum_ft = float(group["firsttower"].sum())
            sum_fd = float(group["firstdragon"].sum())

            # Bayesian shrinkage toward mean
            p = self.prior_games
            smoothed_wr = (wins + p * 0.5) / (picks + p)
            smoothed_gd15 = sum_gd15 / (picks + p)
            smoothed_csd15 = sum_csd15 / (picks + p)
            smoothed_dpm = (sum_dpm + p * global_dpm) / (picks + p)
            smoothed_dtpm = (sum_dtpm + p * global_dtpm) / (picks + p)
            smoothed_dmpm = (sum_dmpm + p * global_dmpm) / (picks + p)
            smoothed_ft = (sum_ft + p * global_ft) / (picks + p)
            smoothed_fd = (sum_fd + p * global_fd) / (picks + p)

            champions_stats[c] = {
                "id": cid,
                "picks": picks,
                "wins": wins,
                "win_rate": float(wins / picks) if picks > 0 else 0.5,
                "smoothed_win_rate": float(smoothed_wr),
                "gd15": float(group["golddiffat15"].mean()),
                "smoothed_gd15": float(smoothed_gd15),
                "csd15": float(group["csdiffat15"].mean()),
                "smoothed_csd15": float(smoothed_csd15),
                "dpm": float(group["dpm"].mean()),
                "smoothed_dpm": float(smoothed_dpm),
                "dtpm": float(group["damagetakenperminute"].mean()),
                "smoothed_dtpm": float(smoothed_dtpm),
                "dmpm": float(group["damagemitigatedperminute"].mean()),
                "smoothed_dmpm": float(smoothed_dmpm),
                "first_tower_rate": float(smoothed_ft),
                "first_dragon_rate": float(smoothed_fd),
            }

        # Pairwise Synergy (Teammates in same match & side)
        synergy_records: Dict[str, Dict[str, Any]] = {}
        counter_records: Dict[str, Dict[str, Any]] = {}

        # Group by gameid and side
        game_groups = p_df.groupby(["gameid", "side"])
        match_teams: Dict[Tuple[str, str], Dict[str, Any]] = {}

        for (gid, side), team_group in game_groups:
            champs_in_team = team_group["champion"].tolist()
            result = int(team_group["result"].iloc[0])
            match_teams[(gid, side)] = {
                "result": result,
                "champions": team_group.set_index("position")["champion"].to_dict(),
                "gd15": team_group.set_index("position")["golddiffat15"].to_dict(),
            }

            # Pairwise combinations on same team
            for i in range(len(champs_in_team)):
                for j in range(i + 1, len(champs_in_team)):
                    key = self.get_synergy_key(champs_in_team[i], champs_in_team[j])
                    if key not in synergy_records:
                        synergy_records[key] = {"games": 0, "wins": 0}
                    synergy_records[key]["games"] += 1
                    synergy_records[key]["wins"] += result

        # Compute smoothed synergy win rate
        synergy_stats: Dict[str, Dict[str, Any]] = {}
        for key, rec in synergy_records.items():
            g = rec["games"]
            w = rec["wins"]
            smoothed_syn = (w + 5.0 * 0.5) / (g + 5.0)
            synergy_stats[key] = {
                "games": g,
                "wins": w,
                "win_rate": float(w / g) if g > 0 else 0.5,
                "smoothed_win_rate": float(smoothed_syn),
            }

        # Lane Counter Matchups (Facing each other at same position across sides)
        # Find games where both Blue and Red exist
        all_game_ids = p_df["gameid"].unique()
        for gid in all_game_ids:
            blue_info = match_teams.get((gid, "Blue"))
            red_info = match_teams.get((gid, "Red"))
            if not blue_info or not red_info:
                continue

            for pos in ["top", "jng", "mid", "bot", "sup"]:
                b_champ = blue_info["champions"].get(pos)
                r_champ = red_info["champions"].get(pos)
                if b_champ and r_champ:
                    # Record Blue champ vs Red champ
                    b_win = blue_info["result"]
                    b_gd = blue_info["gd15"].get(pos, 0.0)

                    k_b_vs_r = self.get_counter_key(b_champ, r_champ)
                    if k_b_vs_r not in counter_records:
                        counter_records[k_b_vs_r] = {"games": 0, "wins": 0, "total_gd15": 0.0}
                    counter_records[k_b_vs_r]["games"] += 1
                    counter_records[k_b_vs_r]["wins"] += b_win
                    counter_records[k_b_vs_r]["total_gd15"] += b_gd

                    # Mirror for Red champ vs Blue champ
                    r_win = 1 - b_win
                    r_gd = -b_gd
                    k_r_vs_b = self.get_counter_key(r_champ, b_champ)
                    if k_r_vs_b not in counter_records:
                        counter_records[k_r_vs_b] = {"games": 0, "wins": 0, "total_gd15": 0.0}
                    counter_records[k_r_vs_b]["games"] += 1
                    counter_records[k_r_vs_b]["wins"] += r_win
                    counter_records[k_r_vs_b]["total_gd15"] += r_gd

        # Compute smoothed counter stats
        counter_stats: Dict[str, Dict[str, Any]] = {}
        for key, rec in counter_records.items():
            g = rec["games"]
            w = rec["wins"]
            tot_gd = rec["total_gd15"]
            counter_stats[key] = {
                "games": g,
                "wins": w,
                "win_rate": float(w / g) if g > 0 else 0.5,
                "smoothed_win_rate": float((w + 5.0 * 0.5) / (g + 5.0)),
                "avg_gd15": float(tot_gd / g) if g > 0 else 0.0,
                "smoothed_gd15": float(tot_gd / (g + 5.0)),
            }

        return {
            "num_champions": len(self.champ_to_id),
            "champ_to_id": self.champ_to_id,
            "champions": champions_stats,
            "synergy": synergy_stats,
            "counters": counter_stats,
            "global_stats": {
                "dpm": global_dpm,
                "dtpm": global_dtpm,
                "dmpm": global_dmpm,
                "firsttower": global_ft,
                "firstdragon": global_fd,
            }
        }
