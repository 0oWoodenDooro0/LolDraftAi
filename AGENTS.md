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
