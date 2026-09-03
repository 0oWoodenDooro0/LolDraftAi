# LoL Draft AI — AI & ML Training Module

This module contains the machine learning pipelines for feature extraction, model training, performance evaluation, and ONNX model export for the 5v5 Draft Value Model.

Strictly adheres to the **Host Environment Isolation Policy** in [`AGENTS.md`](../AGENTS.md):
- Python, PyTorch, Scikit-learn, and ONNX dependencies run exclusively inside Docker containers.
- Trained models are exported as standard `.onnx` files into `src/main/resources/models/` for native consumption by the Kotlin backend.

---

## 1. Quick Start with Docker Compose

### Build the AI Container Image
```bash
docker compose -f ai/docker-compose.yml build
```

### Run Model Training & ONNX Export
```bash
docker compose -f ai/docker-compose.yml run --rm trainer
```

### Run Evaluation & Metrics Verification
```bash
docker compose -f ai/docker-compose.yml run --rm evaluator
```

---

## 2. 52-Dimensional Feature Schema
The model uses a 52-dimensional standardized feature vector aligned with Kotlin's `DraftFeatures`:
1. `blue_laning`, `blue_engage`, `blue_disengage`, `blue_waveclear`, `blue_late_game` (0..4)
2. `red_laning`, `red_engage`, `red_disengage`, `red_waveclear`, `red_late_game` (5..9)
3. `delta_laning`, `delta_engage`, `delta_disengage`, `delta_waveclear`, `delta_late_game` (10..14)
4. `blue_dmg_phys`, `blue_dmg_magic`, `blue_dmg_true` (15..17)
5. `red_dmg_phys`, `red_dmg_magic`, `red_dmg_true` (18..20)
6. `blue_durability`, `red_durability`, `delta_durability` (21..23)
7. `blue_cc_score`, `red_cc_score`, `delta_cc_score` (24..26)
8. `blue_meta_tier`, `red_meta_tier`, `delta_meta_tier` (27..29)
9. `blue_meta_winrate`, `red_meta_winrate`, `delta_meta_winrate` (30..32)
10. `blue_synergy`, `red_synergy`, `delta_synergy` (33..35)
11. `delta_matchup_counter` (36)
12. `delta_team_rating`, `delta_early_dominance` (37..38)
13. `side_advantage_bias`, `blue_side_preference`, `red_side_preference` (39..41)
14. `blue_count_tank`, `blue_count_marksman`, `blue_count_mage`, `blue_count_assassin`, `blue_count_enchanter` (42..46)
15. `red_count_tank`, `red_count_marksman`, `red_count_mage`, `red_count_assassin`, `red_count_enchanter` (47..51)
