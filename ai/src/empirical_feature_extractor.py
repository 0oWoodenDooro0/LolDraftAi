import json
import os
from typing import List, Dict, Any, Optional
import numpy as np

class EmpiricalFeatureExtractor:
    """
    Extracts a 21-dimensional input vector for the Hybrid Draft Model:
    - [0..4]: Blue champion IDs (1..num_champions, 0 for empty)
    - [5..9]: Red champion IDs (1..num_champions, 0 for empty)
    - [10..20]: 11 objective empirical differential features (Blue - Red)
    """
    def __init__(self, stats_path: Optional[str] = None):
        if stats_path is None:
            # Look in standard locations
            candidates = [
                "/workspace/data/champion_empirical_stats.json",
                "../data/champion_empirical_stats.json",
                "data/champion_empirical_stats.json",
                "src/main/resources/data/champion_empirical_stats.json",
            ]
            for c in candidates:
                if os.path.exists(c):
                    stats_path = c
                    break

        if stats_path and os.path.exists(stats_path):
            with open(stats_path, "r", encoding="utf-8") as f:
                self.stats = json.load(f)
        else:
            self.stats = {"champ_to_id": {}, "champions": {}, "synergy": {}, "counters": {}, "global_stats": {}}

        self.champ_to_id: Dict[str, int] = self.stats.get("champ_to_id", {})
        self.champions: Dict[str, Dict[str, Any]] = self.stats.get("champions", {})
        self.synergy: Dict[str, Dict[str, Any]] = self.stats.get("synergy", {})
        self.counters: Dict[str, Dict[str, Any]] = self.stats.get("counters", {})
        self.global_stats: Dict[str, float] = self.stats.get("global_stats", {
            "dpm": 500.0, "dtpm": 600.0, "dmpm": 600.0, "firsttower": 0.5, "firstdragon": 0.5
        })

    def get_champ_id(self, name: str) -> int:
        return self.champ_to_id.get(name, 0)

    def get_champ_stats(self, name: str) -> Dict[str, float]:
        if name in self.champions:
            c = self.champions[name]
            return {
                "win_rate": c.get("smoothed_win_rate", 0.5),
                "gd15": c.get("smoothed_gd15", 0.0),
                "csd15": c.get("smoothed_csd15", 0.0),
                "dpm": c.get("smoothed_dpm", self.global_stats.get("dpm", 500.0)),
                "dtpm": c.get("smoothed_dtpm", self.global_stats.get("dtpm", 600.0)),
                "dmpm": c.get("smoothed_dmpm", self.global_stats.get("dmpm", 600.0)),
                "first_tower": c.get("first_tower_rate", 0.5),
                "first_dragon": c.get("first_dragon_rate", 0.5),
            }
        return {
            "win_rate": 0.5,
            "gd15": 0.0,
            "csd15": 0.0,
            "dpm": self.global_stats.get("dpm", 500.0),
            "dtpm": self.global_stats.get("dtpm", 600.0),
            "dmpm": self.global_stats.get("dmpm", 600.0),
            "first_tower": 0.5,
            "first_dragon": 0.5,
        }

    def extract(self, blue_picks: List[str], red_picks: List[str]) -> np.ndarray:
        vec = np.zeros(21, dtype=np.float32)

        # 1. Champion IDs [0..4] Blue, [5..9] Red
        for i, c in enumerate(blue_picks[:5]):
            vec[i] = float(self.get_champ_id(c))
        for i, c in enumerate(red_picks[:5]):
            vec[5 + i] = float(self.get_champ_id(c))

        # 2. Team Stats Aggregation
        blue_stats_list = [self.get_champ_stats(c) for c in blue_picks[:5]]
        red_stats_list = [self.get_champ_stats(c) for c in red_picks[:5]]

        def calc_team_stat(stats_list, key, default):
            if not stats_list:
                return default
            return float(np.mean([s[key] for s in stats_list]))

        b_wr = calc_team_stat(blue_stats_list, "win_rate", 0.5)
        r_wr = calc_team_stat(red_stats_list, "win_rate", 0.5)
        vec[10] = b_wr - r_wr

        b_gd15 = calc_team_stat(blue_stats_list, "gd15", 0.0)
        r_gd15 = calc_team_stat(red_stats_list, "gd15", 0.0)
        vec[11] = b_gd15 - r_gd15

        b_csd15 = calc_team_stat(blue_stats_list, "csd15", 0.0)
        r_csd15 = calc_team_stat(red_stats_list, "csd15", 0.0)
        vec[12] = b_csd15 - r_csd15

        b_dpm = calc_team_stat(blue_stats_list, "dpm", self.global_stats.get("dpm", 500.0))
        r_dpm = calc_team_stat(red_stats_list, "dpm", self.global_stats.get("dpm", 500.0))
        vec[13] = b_dpm - r_dpm

        b_dtpm = calc_team_stat(blue_stats_list, "dtpm", self.global_stats.get("dtpm", 600.0))
        r_dtpm = calc_team_stat(red_stats_list, "dtpm", self.global_stats.get("dtpm", 600.0))
        vec[14] = b_dtpm - r_dtpm

        b_dmpm = calc_team_stat(blue_stats_list, "dmpm", self.global_stats.get("dmpm", 600.0))
        r_dmpm = calc_team_stat(red_stats_list, "dmpm", self.global_stats.get("dmpm", 600.0))
        vec[15] = b_dmpm - r_dmpm

        b_ft = calc_team_stat(blue_stats_list, "first_tower", 0.5)
        r_ft = calc_team_stat(red_stats_list, "first_tower", 0.5)
        vec[16] = b_ft - r_ft

        b_fd = calc_team_stat(blue_stats_list, "first_dragon", 0.5)
        r_fd = calc_team_stat(red_stats_list, "first_dragon", 0.5)
        vec[17] = b_fd - r_fd

        # 3. Synergy Delta
        def calc_team_synergy(picks):
            if len(picks) < 2:
                return 0.5
            syn_scores = []
            for i in range(len(picks)):
                for j in range(i + 1, len(picks)):
                    pair = sorted([picks[i], picks[j]])
                    key = f"{pair[0]}|{pair[1]}"
                    if key in self.synergy:
                        syn_scores.append(self.synergy[key].get("smoothed_win_rate", 0.5))
                    else:
                        syn_scores.append(0.5)
            return float(np.mean(syn_scores)) if syn_scores else 0.5

        b_syn = calc_team_synergy(blue_picks[:5])
        r_syn = calc_team_synergy(red_picks[:5])
        vec[18] = b_syn - r_syn

        # 4. Lane Matchup Counters
        counter_wrs = []
        counter_gds = []
        min_len = min(len(blue_picks[:5]), len(red_picks[:5]))
        for i in range(min_len):
            b_c = blue_picks[i]
            r_c = red_picks[i]
            key_b_r = f"{b_c}|{r_c}"
            if key_b_r in self.counters:
                info = self.counters[key_b_r]
                # info["smoothed_win_rate"] is Blue's smoothed win rate vs Red
                counter_wrs.append(info.get("smoothed_win_rate", 0.5) - 0.5)
                counter_gds.append(info.get("smoothed_gd15", 0.0))
            else:
                counter_wrs.append(0.0)
                counter_gds.append(0.0)

        vec[19] = float(np.mean(counter_wrs)) if counter_wrs else 0.0
        vec[20] = float(np.mean(counter_gds)) if counter_gds else 0.0

        return vec
