# BMAD Implementation Plan: JamDungeons Base with RDang CoreProtect Spawning & WorldGuard Lifecycle

## Project Overview
Port the plugin foundation to **JamDungeons** (from `E:\Downloads\JamDungeons-1.1.9.jar`) on git branch `new`. Preserve 90% of JamDungeons' native mechanics (PvE/PvP dungeon sessions, waves, mob equipment, reward pools, voucher delivery, admin UI, MariaDB storage, WorldEdit schematics) while upgrading automatic spawning with **CoreProtect player build detection**, **WorldGuard claim creation & deletion**, **biome/terrain height checks** inspired by RDang, and **lag-free async candidate scanning**.

---

## Phases & Status

### Phase 1: Repository Setup & Git Branching
- [x] Create and checkout git branch `new`.
- [x] Preserve previous RDang branch reference on `main`.
- [x] Document initial state in `progress.md`.

### Phase 2: Source Codebase Import & Gradle Configuration
- [x] Import decompiled JamDungeons 1.1.9 sources into `src/main/java`.
- [x] Import raw resources (`plugin.yml`, `config.yml`, `arenas.yml`, `messages.yml`, `rewards.yml`, `themes.yml`, `migrations/`).
- [x] Update `build.gradle` to include:
  - Paper API 1.21.4
  - MariaDB JDBC driver (shaded)
  - WorldEdit Bukkit API (compileOnly)
  - WorldGuard Bukkit API (compileOnly)
  - CoreProtect API (compileOnly)
- [x] Fix 33+ decompilation errors across decompiled source files.

### Phase 3: CoreProtect Integration Module
- [x] Implement `CoreProtectHook.java` (safe API access to `net.coreprotect.CoreProtectAPI`).
- [x] Implement main-thread block collection with sample step & max check limits (inspired by RDang).
- [x] Implement asynchronous lookup check `hasPlayerBuildsAsync(...)` (checking actionId 1 / player placements, ignoring natural events).
- [x] Integrate configuration toggles in `config.yml` (`coreprotect.enabled`, `coreprotect.sample-step`, `coreprotect.max-samples`, `coreprotect.check-interior`).

### Phase 4: Enhanced Dungeon Spawning & Placement Mechanics
- [x] Merge RDang's biome filtering and height calculation logic into `PlacementService`:
  - WorldBorder clamping.
  - Multi-environment support: Overworld surface, Nether roof/floor cave scanning, The End islands, Water/Underwater, Air.
  - Biome compatibility matching (per arena and per theme fallback).
  - Distance checks: min/max distance from spawn, min distance between active dungeons (`min-distance-between-dungeons`).
  - Anti-lag execution: async chunk preloading, stepped candidate sampling (step 3-4 blocks instead of 1 block), async CoreProtect lookup.
- [x] Enhance `EventSchedule` to support interval-based autospawn (`scheduling.interval-minutes`) alongside calendar jobs.

### Phase 5: WorldGuard Region Lifecycle (Creation, Flags & Deletion)
- [x] Verify `WorldGuardRegions` region creation:
  - Generates unique, clean region name (`jd_event_<uuid>` or `dang_<id>`).
  - Sets bounding cuboid with configured padding.
  - Applies flags: `BUILD: DENY`, `BLOCK_BREAK: DENY`, `BLOCK_PLACE: DENY`, `TNT: DENY`, `CREEPER_EXPLOSION: DENY`, `LAVA_FIRE: DENY`, `DAMAGE_ANIMALS: DENY`, `LIGHTER: DENY`, `RESPAWN_ANCHORS: DENY`, `GHAST_FIREBALL: DENY`, `CHEST_ACCESS: ALLOW`, `USE: ALLOW`, `PVP: ALLOW/DENY`, `MOB_SPAWNING: ALLOW/DENY`.
  - Collision detection with existing claims / WorldGuard regions.
- [x] Ensure clean WorldGuard region deletion on:
  - Dungeon completion / loot claim.
  - Dungeon timeout / failure.
  - Manual stop (`/jd stop <id>`).
  - Server shutdown / reload recovery.
  - Terrain rollback via `Templates.restore(s)`.

### Phase 6: Compilation, Verification & Quality Assurance
- [x] Run `./gradlew clean build` and verify 0 errors, 0 warnings.
- [x] Verify shaded JAR contains MariaDB driver and correct `plugin.yml`.
- [x] Verify backward compatibility and runtime safety if CoreProtect or WorldGuard is missing/disabled.
- [x] Finalize documentation and BMAD summary.
