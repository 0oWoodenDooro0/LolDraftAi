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
  - `M1: 數據底座與標準化 (Data Asset & Engine)` (#1) [✅ 100% DONE]
  - `M2: 陣容評估大腦構建 (AI Decision Brain)` (#2) [✅ 100% DONE]
  - `M3: 即時 BP 推演成型 (Pro Platform & Simulation)` (#3) [✅ 100% DONE]
  - `M4: 賽後複盤與歸因 (Post-match Debrief)` (#4) [✅ 100% DONE]
  - `M5: 排位賽輔助拓展 (SoloQ Client - Phase 2)` (#5) [⏸️ 暫緩 / 延後]
  - `M6: 職業賽即時預測整合與 Web 儀表板 (Pro Real-time BP Platform)` [🚀 進行中]

---

## 參、 漸進式架構依賴圖 (15 項 Issues)

```mermaid
graph TD
    subgraph M1 [M1: Kotlin 數據工程底座 (已完成)]
        I1["#1 核心對局模型與 20 輪 BP 日誌 Schema [✅ DONE]"]
        I2["#2 職業賽事數據接入與清洗管線 [✅ DONE]"]
        I3["#3 戰隊風格標籤與紅藍選邊分析器 [✅ DONE]"]
        I4["#4 選手生涯池與天梯練角預警器 [✅ DONE]"]
        I5["#5 英雄五維屬性標籤庫與版本環境 [✅ DONE]"]
    end

    subgraph M2 [M2: AI 決策大腦 (已完成)]
        I6["#6 5v5 陣容特徵工程與勝率評估模型 [✅ DONE]"]
        I7["#7 陣容形勢 Eval Bar 與前後期曲線 [✅ DONE]"]
        I8["#8 陣容失衡預警規則引擎 [✅ DONE]"]
        I9["#9 下一手意圖預測、反制推薦與搖擺位 [✅ DONE]"]
    end

    subgraph M3_M4 [M3~M4: 核心平台應用 (已完成)]
        I10["#10 賽前沙盤推演與劇本模擬 [✅ DONE]"]
        I11["#11 實時觀賽助手與動態 Eval Bar [✅ DONE]"]
        I12["#12 賽後 BP 複盤與歸因系統 [✅ DONE]"]
    end

    subgraph M6 [M6: 職業賽即時預測整合平台 (當前目標)]
        I15["#15 (Issue #27) 職業賽即時 BP 預測整合伺服器與 Web 控制台 [🚀 ACTIVE]"]
    end

    subgraph M5_Deferred [Phase 2: 排位客戶端 (暫緩)]
        I13["#13 排位賽選角自動感知與輕量懸浮視窗 [⏸️ DEFERRED]"]
        I14["#14 排位專屬個人熟練度與線路剋制引擎 [⏸️ DEFERRED]"]
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
    I10 --> I15
    I11 --> I15
    I12 --> I15
    I2 --> I15
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

#### 13. `feat(client): 排位賽選角自動感知與輕量懸浮視窗 (Ranked Client Companion & Overlay)` [⏸️ 暫緩 / 延後]
- **GitHub Issue**: [#13](https://github.com/0oWoodenDooro0/LolDraftAi/issues/13) | **AiKanban Task**: #2
- **Scope**: `client` | **Priority**: `LOW` | **Milestone**: M5 (Deferred)
- **Branch**: `feature/client-ranked-overlay`
- **技術棧**: Kotlin, Compose Multiplatform (Desktop), Ktor Client (LCU API)
- **說明**: LCU API 對接與桌面輕量懸浮 UI，即時感知識別當前對局選角狀態。（因 Linux 平台缺乏 Vanguard 官方客戶端環境，優先權調降並暫緩）。

#### 14. `feat(client): 排位專屬個人熟練度與線路剋制推薦引擎 (SoloQ Counter & Mastery Recommendation)` [⏸️ 暫緩 / 延後]
- **GitHub Issue**: [#14](https://github.com/0oWoodenDooro0/LolDraftAi/issues/14) | **AiKanban Task**: #1
- **Scope**: `client` | **Priority**: `LOW` | **Milestone**: M5 (Deferred)
- **Branch**: `feature/client-soloq-engine`
- **技術棧**: Kotlin
- **說明**: 玩家常用英雄池加權、單線對線剋制優先推薦與智慧補位提示。（同上，隨客戶端功能一併暫緩）。

---

### Milestone 6: 職業賽即時預測整合與 Web 儀表板 (Pro Real-time BP Platform)

#### 15. `feat(platform): 職業賽即時 BP 預測整合伺服器與 Web 控制台 (Pro Match Real-time BP Prediction Server & Web Dashboard)` [🚀 當前焦點]
- **GitHub Issue**: [#27](https://github.com/0oWoodenDooro0/LolDraftAi/issues/27) | **AiKanban Task**: #15
- **Scope**: `platform` | **Priority**: `HIGH` | **Milestone**: M6
- **Branch**: `feature/platform-pro-bp-server-dashboard`
- **技術棧**: Kotlin, Ktor Server (Netty, WebSockets, CORS, Static Content), HTML5/CSS/ES6 WebSocket Dashboard
- **說明**:
  - 將 Phase 1 已開發完成的賽前推演、實時觀賽、賽後復盤三大引擎整合為單一可執行 Ktor 伺服器 (`Application.kt`)，支援 Gradle `./gradlew run`。
  - 內建 `ProMatchRepository`，啟動時自動解析載入 2026 職業賽事真實對局 CSV (`2026_LoL_esports_match_data_from_OraclesElixir.csv`)，提供 LCK/LPL/LEC/LCS 等賽區戰隊檔案與選手常用英雄 API。
  - 提供現代化響應式電競觀賽副屏 Web 控制台（直接由 Ktor 託管於 `http://localhost:8080`），支援下拉選擇對陣戰隊、20 輪標準 BP 棋盤即時點選、實時動態勝率 Eval Bar、敵方下一手意圖預測 Top 3、己方反制推薦、陣容失衡預警及一鍵賽後復盤報告導出。
- **交付物**:
  - `src/main/kotlin/com/loldraft/server/Application.kt` (伺服器統一入口)
  - `src/main/kotlin/com/loldraft/server/ProMatchRepository.kt` (職業賽事與戰隊情報儲存庫)
  - `src/main/kotlin/com/loldraft/platform/pro/api/ProApiRouting.kt` (職業聯賽、戰隊情報 REST API)
  - `src/main/resources/static/index.html` (電競 BP 控制台 Web UI)
  - `src/main/resources/static/css/style.css` (暗黑電競科技風樣式)
  - `src/main/resources/static/js/app.js` (WebSocket 實時連線與 BP 互動引擎)
  - `src/test/kotlin/com/loldraft/server/ProMatchRepositoryTest.kt` (資料載入單元測試)
  - `src/test/kotlin/com/loldraft/server/ServerApplicationTest.kt` (伺服器端到端整合測試)

