import os
import json
import logging
import pandas as pd
from empirical_stats import EmpiricalStatsExtractor

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

def build_empirical_stats(
    csv_path: str = "/workspace/data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv",
    out_paths: list[str] = [
        "/workspace/data/champion_empirical_stats.json",
        "/workspace/src/main/resources/data/champion_empirical_stats.json",
    ]
):
    logger.info(f"Loading raw Oracle's Elixir match data from: {csv_path}")
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"Oracle CSV not found at {csv_path}")

    # Read relevant columns only for speed and memory efficiency
    cols = [
        "gameid", "participantid", "side", "position", "champion", "result",
        "golddiffat15", "csdiffat15", "dpm", "damagetakenperminute",
        "damagemitigatedperminute", "firsttower", "firstdragon"
    ]
    df = pd.read_csv(csv_path, usecols=cols, low_memory=False)
    logger.info(f"Loaded {len(df)} rows.")

    extractor = EmpiricalStatsExtractor()
    stats = extractor.compute_stats(df)

    logger.info(f"Computed stats for {stats['num_champions']} champions.")
    logger.info(f"Found {len(stats['synergy'])} pairwise synergies.")
    logger.info(f"Found {len(stats['counters'])} lane counter matchups.")

    for path in out_paths:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(stats, f, indent=2, ensure_ascii=False)
        logger.info(f"Saved empirical stats to: {path}")

    return stats

if __name__ == "__main__":
    build_empirical_stats()
