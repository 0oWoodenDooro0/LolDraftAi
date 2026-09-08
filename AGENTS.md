# Agent Guidelines for LoL Draft AI

This document defines the architecture, technology stack boundaries, environment isolation rules, and development standards for AI coding agents working in the `LolDraftAi` repository.

---

## 1. Project Overview & Vision

**LoL Draft AI** is a professional-grade League of Legends real-time Ban/Pick (BP) decision, intent prediction, and draft win-rate evaluation system (featuring an Eval Bar, time-horizon win-rate curves, five-dimension composition radars, and counter recommendations).

The project evolves in two phases:
1. **Phase 1 (Core)**: Professional esports league data + high-tier SoloQ intelligence -> Sandbox simulation, live broadcast companion, and post-match debrief.
2. **Phase 2 (Extension)**: Lightweight desktop client for ranked SoloQ players with LCU auto-detection and player mastery weighting.

---

## 2. Technology Stack & Language Boundaries (Strict Rule)

### 2.1 Kotlin (JVM 21 / Kotlin 2.x) — All Non-AI Components
**ALL non-AI modules MUST be implemented in Kotlin using the Gradle Kotlin DSL (`.kts`).**

This encompasses:
- **Core Domain & Data Engineering**: Domain models, 20-round BP schema, turn validation, data ingestion pipelines (Leaguepedia, Oracle's Elixir), team tactical styles, player SoloQ trackers, patch meta matrices.
- **Backend & Simulation Platform**: Ktor HTTP / WebSocket services, simulation sandbox engine, debrief attribution services.
- **Desktop Client & Ranked Overlay**: Compose Multiplatform (Desktop) connecting to the League Client Update (LCU) API.
- **Inference Runtime**: High-performance local model inference via **ONNX Runtime (Java/Kotlin)** or gRPC/REST clients.
- **Serialization & Validation**: `kotlinx.serialization`, strict data classes, and sealed class hierarchies.
- **Testing**: JUnit 5 / Kotest.

### 2.2 Python (AI / ML Only) — Strictly Encapsulated in Docker
Python is strictly reserved for machine learning research, deep learning architectures, feature engineering experimentation, and model training (PyTorch, LightGBM, Pandas, Scikit-learn).

> [!CAUTION]
> ### 🛑 Host Environment Isolation Policy (No Pollution)
> - **NEVER** install Python packages (`pip install`, `poetry install`, `uv add`, `conda`) directly on the host machine.
> - **NEVER** create local Python virtual environments (`.venv`, `venv`, `env`) in the workspace root or host folders.
> - **NEVER** run Python scripts directly on the host.
> - **ALL** Python development, preprocessing, training routines, and dependency management **MUST run strictly inside Docker containers** (via `Dockerfile` and `docker-compose.yml` located in `ai/`).
> - Trained models must be exported into **ONNX format (`.onnx`)** or served as containerized microservices so Kotlin services consume them directly without requiring a host Python environment.

---

## 3. Project Architecture & Modular Layout

```
LolDraftAi/
├── core/                    # [Kotlin] Domain models, 20-turn BP schemas, validation, data pipelines
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/loldraft/core/
│       └── test/kotlin/com/loldraft/core/
├── server/                  # [Kotlin] Ktor backend API, simulation sandbox, WebSocket engine
│   ├── build.gradle.kts
│   └── src/
├── client/                  # [Kotlin] Compose Multiplatform desktop app, LCU integration, overlay
│   ├── build.gradle.kts
│   └── src/
├── ai/                      # [Python / Docker Only] ML training, models, dataset preparation
│   ├── Dockerfile           # Isolated Docker container for Python / PyTorch / LightGBM
│   ├── docker-compose.yml   # Multi-service & GPU training orchestration
│   ├── pyproject.toml       # Python dependencies managed inside Docker
│   ├── README.md            # AI module & Docker usage instructions
│   └── src/                 # ML model architectures, training loops, ONNX exporters
├── docs/                    # Technical specifications and implementation plans
├── .aikanban.json           # AiKanban & VCS configuration
├── AGENTS.md                # Agent rules & constraints (this file)
├── build.gradle.kts         # Root Gradle build script
└── settings.gradle.kts      # Multi-project Gradle settings
```

---

## 4. Build, Test & Execution Commands

Always use the Gradle wrapper (`./gradlew`) for Kotlin, and Docker commands for Python.

### Kotlin (Host / Gradle)
- **Run Unit Tests**: `./gradlew test`
- **Verify Compilation**: `./gradlew classes` (or `./gradlew check`)
- **Full Project Build**: `./gradlew build`
- **Clean Build Artifacts**: `./gradlew clean`

### Python / AI (Docker Only)
- **Build AI Container Image**:
  ```bash
  docker compose -f ai/docker-compose.yml build
  ```
- **Run Training Inside Container**:
  ```bash
  docker compose -f ai/docker-compose.yml run --rm trainer python src/train.py
  ```
- **Export Trained Model to ONNX**:
  ```bash
  docker compose -f ai/docker-compose.yml run --rm exporter python src/export_onnx.py --output ../core/src/main/resources/models/
  ```

---

## 5. Coding Standards & Conventions

### 5.1 Kotlin Idioms
- **Immutability First**: Default to `val` and immutable collections (`List`, `Set`, `Map`). Avoid `var` and mutable collections in public API contracts.
- **Sealed Hierarchies for BP State**: Use `sealed interface` or `sealed class` to represent turns, draft actions (`PickAction`, `BanAction`), and state transitions.
- **Null Safety**: Strict adherence to non-null types. Avoid `!!` operator.
- **Data Classes**: Use `data class` with `@Serializable` for all DTOs and domain records (`Match`, `Game`, `DraftTurn`, `Team`).
- **Exhaustive `when`**: When handling actions or turn phases, use expression `when` without `else` where possible to guarantee compiler-enforced exhaustiveness.

### 5.2 AiKanban & Git Workflow
- **Issue-Driven Development**: All development stems from AiKanban tasks linked to GitHub Issues.
- **Branch Naming**: `feature/<scope>-<short-description>` (e.g. `feature/data-core-schemas`).
- **Commit Messages**: Strictly follow Conventional Commits:
  - `feat(scope): description`
  - `fix(scope): description`
  - `docs(scope): description`
  - `test(scope): description`
  - `refactor(scope): description`
- **TDD Requirement**: Write unit tests first before production implementation. Ensure `./gradlew test` passes with 100% success before submitting PRs.

---

## 6. UI & Data Synchronization Guidelines (Compose Desktop & Analytics)

To maintain a clean, professional, and consistent user interface and respect third-party API rate limits, all AI agents and contributors MUST strictly adhere to the following rules:

### 6.1 Avoid Table Cell Wrapping & Redundant Text (避免表格欄位換行與冗餘贅字)
- In all tabular interfaces (such as `ExcelDataGrid`, `TeamRosterPoolView`, `SoloQIntelligenceView`, `PlayersGridView`, `TeamsGridView`), table headers and data cells **MUST NEVER WRAP onto multiple lines**.
- Always specify `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis` on all header and cell `Text` composables.
- **Strictly use exact numbers without redundant words (表格只要精確數字，無冗餘贅字)**:
  - Do NOT clutter table cells with redundant characters or verbose descriptions (e.g. avoid `"15場 · 10勝5敗"`, `"0 場 🚨 未登場"`, `"MIDDLE 本職"`).
  - Instead, display clean, exact numbers or standard codes directly:
    - Games: `15`
    - W-L: `10-5`
    - Win Rate: `67%`
    - KDA: `3.8`
    - CS: `185`
    - Pro Games: `0` or `12` (clean colored number, with `★` if special pick)
    - Role: standard enum name `MID`, `TOP`, `JUNGLE`, `BOT`, `SUPPORT` without `"本職/副路"` suffix.
- Provide adequate column widths (`width(...)` or min-width) and enable horizontal scrolling (`horizontalScroll`) whenever total column width exceeds panel width.

### 6.2 No Example Placeholders in TextFields (移除文字框範例 Placeholder)
- Text fields and input boxes (`BasicTextField`, `TextField`, `DialogInputField`) **MUST NEVER contain example hint placeholders** (such as `"例如 Hide on bush"`, `"KR1"`, `"例如 T1, GEN"`, `"RGAPI-xxxxxxxx-..."`).
- Keep input fields clean and empty when blank.
- Labels above or beside input fields must cleanly describe the field without embedded examples (e.g. `賽區`, `隊伍`, `選手名稱`, `召喚師名稱`, `標籤`).

### 6.3 No Mixed Language Formatting (去除「中文(英文)」混合格式)
- All UI strings, tab titles, dialog messages, table headers, and badges **MUST NEVER use mixed `中文(英文)` or `英文(中文)` formats** (e.g., avoid `"戰隊總表 (Teams Grid)"`, `"單雙排 (Ranked SoloQ)"`, `"KR (南韓伺服器)"`, `"WIN 勝利"`, `"(60%勝率)"`).
- Choose either consistent Chinese or consistent English. In Chinese UI contexts:
  - Tab titles: `戰隊英雄池看板`, `選手數據總表`, `戰隊數據總表`, `選手天梯情報`.
  - Servers: `南韓伺服器`, `台港澳伺服器`, `北美伺服器`, `西歐伺服器`, `北東歐伺服器`, `日本伺服器`.
  - Match results: `勝利`, `敗北`.
  - Statistical delimiters: Use clean Chinese dots or spaces (e.g., `15場 · 10勝5敗`, `勝率 60%`).

### 6.4 SoloQ Data Local Caching & Sync Principles (天梯資料本地快取與同步原則)
- **Persistent Local Disk Cache (保留到本地快取不刪除)**:
  - Once SoloQ match records are fetched via Riot API, they **MUST be persisted to local disk cache** (`config/soloq_matches_cache.json`) so they survive application restarts and are never deleted unintentionally.
  - When registering or updating a player account, existing cached matches must be preserved.
  - Local cache files and API keys (`config/soloq_matches_cache.json`, `config/riot_api_key.txt`, `config/soloq_accounts.json`) are strictly local and must remain in `.gitignore`.
- **On-Demand Single-Player Sync Only (禁止自動批次同步所有選手)**:
  - Background processes or tab navigation **MUST NEVER automatically fire Riot API requests** or sync all player accounts in bulk.
  - Riot API requests must **ONLY be triggered when**:
    1. The user explicitly clicks the "同步天梯數據" / "立即同步" button for the currently selected player.
    2. The user registers a brand new player account ("新增選手帳號").
  - Under all other circumstances (viewing, switching players, changing filters), read solely from local cache (`cached ?: emptyList()`).
