# BMAD Progress Tracking

## Active Status
- Branch: `new`
- Base: JamDungeons 1.1.9 (90% native mechanics preserved)
- Build Status: SUCCESS (`./gradlew clean build` completes with 0 errors)
- Output Artifact: `build/libs/Rdang-1.1.9.jar` (1.01 MB shaded JAR)

---

## Log of Completed Work

### 1. Branching & Setup
- Created branch `new` from original `main`.
- Cleaned up build artifacts from git tracking and added `.gitignore`.

### 2. Codebase Import & Compilation
- Extracted and decompiled `JamDungeons-1.1.9.jar`.
- Resolved decompilation errors across `AdminUI.java`, `Configs.java`, `Rewards.java`, `Spawner.java`, `WorldGuardRegions.java`, `SchematicCodec.java`, `Guards.java`, `LegacySchematics.java`, `Migrations.java`, `Rules.java`.
- Configured Gradle toolchain for Java 21, Paper 1.21.4, WorldEdit 7.3.10, WorldGuard 7.0.12, CoreProtect 22.4, and shaded MariaDB JDBC 3.5.3.

### 3. CoreProtect Integration
- Created `CoreProtectHook.java`:
  - Main-thread block sample collection (`collectSampleBlocks`).
  - Asynchronous database lookup (`hasPlayerBuildsAsync`).
  - Filters out natural actions/events (`#water`, `#fire`, etc.).
  - Configurable step and sample limits in `config.yml`.

### 4. Advanced Placement & Anti-Lag Spawning
- Overhauled `PlacementService.java`:
  - Nether & The End height scanning (`getGroundHeight`), preventing dungeons from spawning on Nether bedrock roofs or floating in void.
  - Stepped cuboid verification (step = 3 blocks) replacing synchronous full 3D scans (reduced block reads by ~96%).
  - Minimum distance between active dungeons (`min-distance-between-dungeons: 300`).
  - Biome compatibility matching (`theme-biomes` fallback if arena has no biomes set).
  - Pre-spawn asynchronous CoreProtect validation.

### 5. WorldGuard Lifecycle & Flags
- Upgraded `WorldGuardRegions.java`:
  - Extended protective flags: `DAMAGE_ANIMALS: DENY`, `LIGHTER: DENY`, `RESPAWN_ANCHORS: DENY`, `GHAST_FIREBALL: DENY`.
  - Defensive reflection logic preventing crashes on differing WorldGuard 7 versions.
  - Safe region removal (`delete`) upon completion, expiration, `/jd stop`, rollback, or shutdown.

### 6. Scheduling & Configuration
- Added interval autospawn (`scheduling.interval-minutes`) in `EventSchedule.java`.
- Updated `config.yml` with `coreprotect`, `theme-biomes`, and `random-placement` properties.
- Updated `plugin.yml` with `CoreProtect` softdepend, `api-version: '1.21'`, and aliases `[jd, dungeons, rdang, rd, dang]`.
