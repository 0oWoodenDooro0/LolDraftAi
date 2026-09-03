# 英雄聯盟 AI BP 預測與勝率評估系統 (LoL Draft AI) - 漸進式 Issue 分割與技術架構規劃

本規劃採用**「近期精細（M1/M2 拆細至單一 PR 與 TDD 驗收級別）、遠期輪廓（M3~M5 保持功能模組級別）」**的漸進式細化（Progressive Elaboration）策略，並確立 **Kotlin (全非 AI 模組) + Docker 化 Python (僅限 AI/ML 訓練)** 的雙軌隔離技術架構。

---

## 壹、 技術棧與隔離原則 (Strict Architecture Policy)

依據專案規範與 [`AGENTS.md`](../AGENTS.md)：
1. **Kotlin (JVM 21 / Kotlin 2.x)**：
   - 負責**所有非 AI 模組**（領域模型、資料清洗管線、戰隊/選手分析器、版本環境矩陣、Ktor 後端服務、Compose Desktop 排位懸浮客戶端）。
   - 建置與驗證工具：Gradle Kotlin DSL (`./gradlew test`, `./gradlew build`)。
   - 序列化與資料契約：`kotlinx.serialization`、嚴格 `data class` 與 `sealed hierarchy`。
2. **Python (僅限 AI/ML 訓練) — 嚴格 Docker 隔離**：
   - 僅負責 5v5 勝率預測模型、時間勝率曲線回歸與策略網路之訓練與研究。
   - **禁止直接在本機污染環境**（不直接使用本機 `pip`、`poetry`、`uv` 或 `.venv`）。
   - **所有 Python 執行、相依套件與訓練任務均強制封裝於 Docker 容器中**。
   - 訓練產出模型導出為 **ONNX 格式 (`.onnx`)**，由 Kotlin 模組直接透過 `onnxruntime` 進行高性能力推論，達成跨語言無縫解耦。

---

## 貳、 專案與儲存庫現狀

- **GitHub Repository**: [`0oWoodenDooro0/LolDraftAi`](https://github.com/0oWoodenDooro0/LolDraftAi)
- **Default Base Branch**: `main`
- **AiKanban 配置**: [`.aikanban.json`](../.aikanban.json)（已設定 `./gradlew test` 驗證門禁）
- **專案規格文件**: [`README.md`](../README.md)
- **Agent 指南規範**: [`AGENTS.md`](../AGENTS.md)
- **GitHub Milestones**:
  - `M1: 數據底座與標準化 (Data Asset & Engine)` (#1)
  - `M2: 陣容評估大腦構建 (AI Decision Brain)` (#2)
  - `M3: 即時 BP 推演成型 (Pro Platform & Simulation)` (#3)
  - `M4: 賽後複盤與歸因 (Post-match Debrief)` (#4)
  - `M5: 排位賽輔助拓展 (SoloQ Client - Phase 2)` (#5)

---

## 參、 漸進式架構依賴圖 (14 項 Issues)

```mermaid
graph TD
    subgraph M1 [M1: Kotlin 數據工程底座]
        I1["#1 feat(data): 核心對局資料模型與 20 輪 BP 日誌 Schema [Kotlin]"]
        I2["#2 feat(data): 職業賽事歷史數據接入與清洗管線 [Kotlin]"]
        I3["#3 feat(data): 戰隊風格標籤與紅藍選邊傾向分析器 [Kotlin]"]
        I4["#4 feat(data): 職業選手生涯池與天梯練角突增預警器 [Kotlin]"]
        I5["#5 feat(data): 英雄五維屬性標籤庫與版本環境矩陣 [Kotlin]"]
    end

    subgraph M2 [M2: AI 決策大腦 (Docker Python 訓練 + ONNX / Kotlin 推論)]
        I6["#6 feat(model): 5v5 陣容特徵工程與勝率期望值評估模型 [Python Docker + ONNX]"]
        I7["#7 feat(model): 陣容形勢評估值 (Eval Bar) 與前後期勝率曲線演算法 [Kotlin]"]
        I8["#8 feat(model): 陣容失衡預警規則引擎 [Kotlin]"]
        I9["#9 feat(model): 下一手 BP 意圖預測、反制推薦與搖擺位識別 [Kotlin + ONNX]"]
    end

    subgraph M3_M5 [M3~M5: Kotlin 平台應用與排位客戶端]
        I10["#10 feat(platform): 賽前沙盤推演與劇本模擬 [Ktor / Kotlin]"]
        I11["#11 feat(platform): 實時觀賽助手與動態 Eval Bar 視覺化 [Ktor / Web]"]
        I12["#12 feat(platform): 賽後 BP 複盤與得失分歸因系統 [Kotlin]"]
        I13["#13 feat(client): 排位賽選角自動感知與輕量懸浮視窗 [Compose Desktop / LCU]"]
        I14["#14 feat(client): 排位專屬個人熟練度與線路剋制推薦引擎 [Kotlin]"]
    end

    I1 --> I2
    I1 --> I3
    I2 --> I6
    I5 --> I6
    I6 --> I7
    I6 --> I8
    I4 --> I9
    I6 --> I9
    I7 --> I10
    I9 --> I10
    I7 --> I11
    I9 --> I11
    I6 --> I12
    I7 --> I14
    I13 --> I14
```

---

## 肆、 具體 Issue 詳細規格

### Milestone 1: 數據底座與標準化 (Data Asset & Engine) — 全 Kotlin

#### 1. `feat(data): 核心對局資料模型與 20 輪 BP 日誌 Schema`
- **GitHub Issue**: [#1](https://github.com/0oWoodenDooro0/LolDraftAi/issues/1) | **AiKanban Task**: #14
- **Scope**: `data` | **Priority**: `HIGH` | **Milestone**: M1
- **Branch**: `feature/data-core-schemas`
- **技術棧**: Kotlin (JVM 21), `kotlinx.serialization`, JUnit 5
- **說明**: 定義 LoL 賽事與 BP 核心領域模型（`Match`, `Game`, `Team`, `Player`, `Role`, `PickBanAction`, `DraftState`, `Turn 1~20`），提供型別嚴謹的 Kotlin Data Class / Sealed Interface 與單元測試。
- **交付物**:
  - `src/main/kotlin/com/loldraft/data/models/` (資料模型與列舉)
  - `src/main/kotlin/com/loldraft/data/validation/` (20 輪次嚴格驗證器)
  - `src/test/kotlin/com/loldraft/data/models/DraftModelTest.kt` (單元測試)

#### 2. `feat(data): 職業賽事歷史數據接入與清洗管線 (Leaguepedia / Oracle's Elixir)`
- **GitHub Issue**: [#2](https://github.com/0oWoodenDooro0/LolDraftAi/issues/2) | **AiKanban Task**: #13
- **Scope**: `data` | **Priority**: `HIGH` | **Milestone**: M1
- **Branch**: `feature/data-pro-matches-pipeline`
- **技術棧**: Kotlin, Ktor HTTP Client, `kotlinx.coroutines`
- **說明**: 串接全球四大賽區（LCK, LPL, LEC, LCS 等）歷史對局數據，完成資料清洗、版本補丁 (Patch) 綁定，轉換為標準 20 輪 BP 結構。
- **交付物**:
  - `src/main/kotlin/com/loldraft/data/sources/` (外部數據拉取適配器)
  - `src/main/kotlin/com/loldraft/data/pipeline/` (批次匯入與清洗作業)
  - `src/test/kotlin/com/loldraft/data/pipeline/PipelineTest.kt`

#### 3. `feat(data): 戰隊風格標籤與紅藍選邊傾向分析器`
- **GitHub Issue**: [#3](https://github.com/0oWoodenDooro0/LolDraftAi/issues/3) | **AiKanban Task**: #12
- **Scope**: `data` | **Priority**: `MEDIUM` | **Milestone**: M1
- **Branch**: `feature/data-team-style-analyzer`
- **技術棧**: Kotlin
- **說明**: 聚合歷史對局，計算各戰隊特徵：紅藍方勝率差、選邊傾向、前期一血/首龍率、血腥度（Kills/Min）、平均時長與優先選角習慣。
- **交付物**:
  - `src/main/kotlin/com/loldraft/data/analysis/TeamStyleAnalyzer.kt`
  - `src/test/kotlin/com/loldraft/data/analysis/TeamStyleAnalyzerTest.kt`

#### 4. `feat(data): 職業選手生涯池與天梯 (SoloQ) 練角突增預警器`
- **GitHub Issue**: [#4](https://github.com/0oWoodenDooro0/LolDraftAi/issues/4) | **AiKanban Task**: #11
- **Scope**: `data` | **Priority**: `HIGH` | **Milestone**: M1
- **Branch**: `feature/data-player-soloq-tracker`
- **技術棧**: Kotlin
- **說明**: 建立職業選手生涯英雄池與招牌角資料庫，追蹤選手高分 SoloQ（韓服/超級服）動態，實現近 3~7 天練角頻率突增預警（黑科技/練角預測演算法）。
- **交付物**:
  - `src/main/kotlin/com/loldraft/data/tracker/PlayerSoloQTracker.kt`
  - 練角異常預警突增偵測演算法與單元測試

#### 5. `feat(data): 英雄五維屬性標籤庫與版本環境 (Patch Meta) 矩陣`
- **GitHub Issue**: [#5](https://github.com/0oWoodenDooro0/LolDraftAi/issues/5) | **AiKanban Task**: #10
- **Scope**: `data` | **Priority**: `HIGH` | **Milestone**: M1
- **Branch**: `feature/data-patch-meta-matrix`
- **技術棧**: Kotlin
- **說明**: 構建英雄核心特徵庫（傷害屬性、開團/反手、對線強度、前後期發力曲線），並計算當前版本 P/B Rate 矩陣、英雄組合 Combo 勝率與線路剋制指標。
- **交付物**:
  - `src/main/kotlin/com/loldraft/data/meta/ChampionTagMatrix.kt`
  - `src/main/kotlin/com/loldraft/data/meta/PatchMetaMatrix.kt`

---

### Milestone 2: 陣容評估大腦構建 (AI Decision Brain) — 雙軌協同

#### 6. `feat(model): 5v5 陣容特徵工程與勝率期望值評估模型 (Draft Value Model)`
- **GitHub Issue**: [#6](https://github.com/0oWoodenDooro0/LolDraftAi/issues/6) | **AiKanban Task**: #9
- **Scope**: `model` | **Priority**: `HIGH` | **Milestone**: M2
- **Branch**: `feature/model-draft-evaluator`
- **技術棧**: Python (PyTorch/LightGBM 封裝在 Docker 中訓練) -> 導出為 ONNX -> Kotlin ONNX Runtime 載入
- **說明**: 陣容特徵向量化，在 Docker 容器內訓練終局期望勝率預測模型，導出 ONNX 模型供 Kotlin 端即時推論。
- **交付物**:
  - `ai/Dockerfile`, `ai/docker-compose.yml`
  - `ai/src/train_draft_value.py`, `ai/src/export_onnx.py`
  - `src/main/kotlin/com/loldraft/models/DraftValueEvaluator.kt` (Kotlin ONNX 載入器)

#### 7. `feat(model): 陣容形勢評估值 (Eval Bar) 與前後期勝率曲線演算法`
- **GitHub Issue**: [#7](https://github.com/0oWoodenDooro0/LolDraftAi/issues/7) | **AiKanban Task**: #8
- **Scope**: `model` | **Priority**: `HIGH` | **Milestone**: M2
- **Branch**: `feature/model-eval-bar-curve`
- **技術棧**: Kotlin
- **說明**: 將模型勝率對數映射為形勢評估值（+1.5 藍優 / -1.2 紅優），計算前 15 分鐘線路節奏勝率與 30 分鐘後團戰勝率，以及五維屬性雷達分。
- **交付物**:
  - `src/main/kotlin/com/loldraft/models/eval/EvalBarCalculator.kt`
  - `src/main/kotlin/com/loldraft/models/eval/TimeHorizonCurve.kt`

#### 8. `feat(model): 陣容失衡預警規則引擎 (結構性缺陷檢查)`
- **GitHub Issue**: [#8](https://github.com/0oWoodenDooro0/LolDraftAi/issues/8) | **AiKanban Task**: #7
- **Scope**: `model` | **Priority**: `MEDIUM` | **Milestone**: M2
- **Branch**: `feature/model-composition-flaws`
- **技術棧**: Kotlin
- **說明**: 檢查陣容結構性問題：缺乏清線、全物理菜刀隊、缺乏硬控、缺乏前排坦度等，輸出結構化警報清單。
- **交付物**:
  - `src/main/kotlin/com/loldraft/models/flaws/CompositionFlawDetector.kt`
  - 完整失衡檢測規則單元測試

#### 9. `feat(model): 下一手 BP 意圖預測、反制推薦與搖擺位識別`
- **GitHub Issue**: [#9](https://github.com/0oWoodenDooro0/LolDraftAi/issues/9) | **AiKanban Task**: #6
- **Scope**: `model` | **Priority**: `HIGH` | **Milestone**: M2
- **Branch**: `feature/model-bp-recommendation`
- **技術棧**: Kotlin (搜尋推薦與搖擺位) + Python Docker (Policy Net)
- **說明**: 模擬對手教練思維輸出 Top 3 預測；以 Max-WinRate Gain 演算法推薦最佳己方選角；識別多路搖擺位並輸出分路機率。
- **交付物**:
  - `src/main/kotlin/com/loldraft/models/recommend/PickRecommender.kt`
  - `src/main/kotlin/com/loldraft/models/recommend/FlexPickAnalyzer.kt`

---

### Milestone 3 ~ 5: 平台展示與業務延伸 (Kotlin 生態系)

#### 10. `feat(platform): 賽前沙盤推演與劇本模擬 (Pre-match Sandbox)`
- **GitHub Issue**: [#10](https://github.com/0oWoodenDooro0/LolDraftAi/issues/10) | **AiKanban Task**: #5
- **Scope**: `platform` | **Priority**: `MEDIUM` | **Milestone**: M3
- **Branch**: `feature/platform-pre-match-sandbox`
- **技術棧**: Kotlin, Ktor Server
- **說明**: 輸入對陣戰隊自動推演 3 套高機率交鋒劇本，提供樹狀分叉模擬與 What-if 分析。

#### 11. `feat(platform): 實時觀賽助手與動態 Eval Bar 視覺化 (Live Match Companion)`
- **GitHub Issue**: [#11](https://github.com/0oWoodenDooro0/LolDraftAi/issues/11) | **AiKanban Task**: #4
- **Scope**: `platform` | **Priority**: `MEDIUM` | **Milestone**: M3
- **Branch**: `feature/platform-live-companion`
- **技術棧**: Kotlin (Ktor WebSocket) + Web 前端
- **說明**: 賽事同步介面、動態 Eval Bar、五維雷達與即時反制建議。

#### 12. `feat(platform): 賽後 BP 複盤與得失分歸因系統 (Post-match Debrief & Attribution)`
- **GitHub Issue**: [#12](https://github.com/0oWoodenDooro0/LolDraftAi/issues/12) | **AiKanban Task**: #3
- **Scope**: `platform` | **Priority**: `MEDIUM` | **Milestone**: M4
- **Branch**: `feature/platform-post-match-debrief`
- **技術棧**: Kotlin
- **說明**: 量化兩隊教練 BP 得失分，區隔陣容優劣 vs 選手發揮歸因，導出複盤報告。

#### 13. `feat(client): 排位賽選角自動感知與輕量懸浮視窗 (Ranked Client Companion & Overlay)`
- **GitHub Issue**: [#13](https://github.com/0oWoodenDooro0/LolDraftAi/issues/13) | **AiKanban Task**: #2
- **Scope**: `client` | **Priority**: `LOW` | **Milestone**: M5
- **Branch**: `feature/client-ranked-overlay`
- **技術棧**: Kotlin, Compose Multiplatform (Desktop), Ktor Client (LCU API)
- **說明**: LCU API 對接與桌面輕量懸浮 UI，即時感知識別當前對局選角狀態。

#### 14. `feat(client): 排位專屬個人熟練度與線路剋制推薦引擎 (SoloQ Counter & Mastery Recommendation)`
- **GitHub Issue**: [#14](https://github.com/0oWoodenDooro0/LolDraftAi/issues/14) | **AiKanban Task**: #1
- **Scope**: `client` | **Priority**: `LOW` | **Milestone**: M5
- **Branch**: `feature/client-soloq-engine`
- **技術棧**: Kotlin
- **說明**: 玩家常用英雄池加權、單線對線剋制優先推薦與智慧補位提示。
