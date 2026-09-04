import pytest
import pandas as pd
import numpy as np
from src.empirical_stats import EmpiricalStatsExtractor

def create_mock_oracle_df():
    data = [
        # Game 1: Blue team (Top: Aatrox, Jng: Sejuani, Mid: Ahri, Bot: Varus, Sup: Nautilus) -> Blue win (1)
        {"gameid": "G1", "participantid": 1, "side": "Blue", "position": "top", "champion": "Aatrox", "result": 1,
         "golddiffat15": 250.0, "csdiffat15": 5.0, "dpm": 600.0, "damagetakenperminute": 700.0, "damagemitigatedperminute": 800.0,
         "firsttower": 1, "firstdragon": 1},
        {"gameid": "G1", "participantid": 2, "side": "Blue", "position": "jng", "champion": "Sejuani", "result": 1,
         "golddiffat15": 100.0, "csdiffat15": 2.0, "dpm": 350.0, "damagetakenperminute": 900.0, "damagemitigatedperminute": 1000.0,
         "firsttower": 1, "firstdragon": 1},
        {"gameid": "G1", "participantid": 3, "side": "Blue", "position": "mid", "champion": "Ahri", "result": 1,
         "golddiffat15": -50.0, "csdiffat15": -1.0, "dpm": 550.0, "damagetakenperminute": 500.0, "damagemitigatedperminute": 400.0,
         "firsttower": 1, "firstdragon": 1},
        {"gameid": "G1", "participantid": 4, "side": "Blue", "position": "bot", "champion": "Varus", "result": 1,
         "golddiffat15": 300.0, "csdiffat15": 10.0, "dpm": 750.0, "damagetakenperminute": 450.0, "damagemitigatedperminute": 300.0,
         "firsttower": 1, "firstdragon": 1},
        {"gameid": "G1", "participantid": 5, "side": "Blue", "position": "sup", "champion": "Nautilus", "result": 1,
         "golddiffat15": 100.0, "csdiffat15": 0.0, "dpm": 200.0, "damagetakenperminute": 600.0, "damagemitigatedperminute": 700.0,
         "firsttower": 1, "firstdragon": 1},
        # Game 1: Red team (Top: Renekton, Jng: Vi, Mid: Azir, Bot: Kai'Sa, Sup: Rell) -> Red loss (0)
        {"gameid": "G1", "participantid": 6, "side": "Red", "position": "top", "champion": "Renekton", "result": 0,
         "golddiffat15": -250.0, "csdiffat15": -5.0, "dpm": 500.0, "damagetakenperminute": 750.0, "damagemitigatedperminute": 700.0,
         "firsttower": 0, "firstdragon": 0},
        {"gameid": "G1", "participantid": 7, "side": "Red", "position": "jng", "champion": "Vi", "result": 0,
         "golddiffat15": -100.0, "csdiffat15": -2.0, "dpm": 300.0, "damagetakenperminute": 850.0, "damagemitigatedperminute": 800.0,
         "firsttower": 0, "firstdragon": 0},
        {"gameid": "G1", "participantid": 8, "side": "Red", "position": "mid", "champion": "Azir", "result": 0,
         "golddiffat15": 50.0, "csdiffat15": 1.0, "dpm": 500.0, "damagetakenperminute": 450.0, "damagemitigatedperminute": 350.0,
         "firsttower": 0, "firstdragon": 0},
        {"gameid": "G1", "participantid": 9, "side": "Red", "position": "bot", "champion": "Kai'Sa", "result": 0,
         "golddiffat15": -300.0, "csdiffat15": -10.0, "dpm": 600.0, "damagetakenperminute": 500.0, "damagemitigatedperminute": 350.0,
         "firsttower": 0, "firstdragon": 0},
        {"gameid": "G1", "participantid": 10, "side": "Red", "position": "sup", "champion": "Rell", "result": 0,
         "golddiffat15": -100.0, "csdiffat15": 0.0, "dpm": 180.0, "damagetakenperminute": 650.0, "damagemitigatedperminute": 600.0,
         "firsttower": 0, "firstdragon": 0},
    ]
    return pd.DataFrame(data)

def test_aggregate_individual_stats():
    df = create_mock_oracle_df()
    extractor = EmpiricalStatsExtractor(all_champions=["Aatrox", "Renekton", "Sejuani", "Vi", "Ahri", "Azir", "Varus", "Kai'Sa", "Nautilus", "Rell", "Zac"])
    stats = extractor.compute_stats(df)
    
    assert "Aatrox" in stats["champions"]
    aatrox = stats["champions"]["Aatrox"]
    assert aatrox["id"] > 0
    assert aatrox["picks"] == 1
    assert aatrox["win_rate"] == 1.0
    assert aatrox["gd15"] == 250.0
    assert aatrox["dpm"] == 600.0

    # Unplayed champion "Zac" should have smoothed prior
    assert "Zac" in stats["champions"]
    zac = stats["champions"]["Zac"]
    assert zac["picks"] == 0
    assert zac["win_rate"] == 0.5

def test_pairwise_synergy_computation():
    df = create_mock_oracle_df()
    extractor = EmpiricalStatsExtractor(all_champions=["Aatrox", "Renekton", "Sejuani", "Vi", "Ahri", "Azir", "Varus", "Kai'Sa", "Nautilus", "Rell"])
    stats = extractor.compute_stats(df)
    
    pair_key = extractor.get_synergy_key("Aatrox", "Sejuani")
    assert pair_key in stats["synergy"]
    assert stats["synergy"][pair_key]["wins"] == 1
    assert stats["synergy"][pair_key]["games"] == 1

def test_lane_counter_computation():
    df = create_mock_oracle_df()
    extractor = EmpiricalStatsExtractor(all_champions=["Aatrox", "Renekton", "Sejuani", "Vi", "Ahri", "Azir", "Varus", "Kai'Sa", "Nautilus", "Rell"])
    stats = extractor.compute_stats(df)
    
    counter_key = extractor.get_counter_key("Aatrox", "Renekton")
    assert counter_key in stats["counters"]
    assert stats["counters"][counter_key]["wins"] == 1
    assert stats["counters"][counter_key]["avg_gd15"] == 250.0
