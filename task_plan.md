# BMAD Implementation Plan: JamDungeons Base with RDang CoreProtect Spawning & WorldGuard Lifecycle

## Project Overview
Refactor/port the plugin base to **JamDungeons** (from `E:\Downloads\JamDungeons-1.1.9.jar`) on a dedicated git branch `new`. Preserve 90% of JamDungeons' native mechanics (PvE/PvP dungeon sessions, waves, mob equipment, reward pools, voucher delivery, admin UI, MariaDB storage, WorldEdit schematics) while enhancing automatic spawning with **CoreProtect player build detection**, **WorldGuard claim creation & deletion**, **biome/terrain height checks** inspired by RDang, and **lag-free async candidate scanning**.

---

## Phases & Status

### Phase 1: Repository Setup & Git Branching
- [ ] Create and checkout git branch `new`.
- [ ] Preserve previous RDang branch reference on `main`.
- [ ] Document initial state in `progress.md`.

### Phase 2: Source Codebase Import & Gradle Configuration
- [ ] Import decompiled JamDungeons 1.1.9 sources into `src/main/java`.
- [ ] Import raw resources (`plugin.yml`, `config.yml`, `arenas.yml`, `messages.yml`, `rewards.yml`, `themes.yml`, `migrations/`).
- [ ] Update `build.gradle` to include:
  - Paper API 1.21.4
  - MariaDB JDBC driver (shaded)
  - WorldEdit Bukkit API (provided/compileOnly)
  - WorldGuard Bukkit API (provided/compileOnly)
  - CoreProtect API (provided/compileOnly)
  - Vault, PlaceholderAPI (optional compileOnly)
- [ ] Fix decompilation artifacts (e.g. `CallSite` glitch in `WorldGuardRegions.java`).

### Phase 3: CoreProtect Integration Module
- [ ] Implement `CoreProtectHook.java` (safe reflection/API access to `net.coreprotect.CoreProtectAPI`).
- [ ] Implement main-thread block collection with sample step & max check limits (inspired by RDang).
- [ ] Implement asynchronous lookup check `hasPlayerBuilds(...)` (checking actionId 1 / player placements).
- [ ] Integrate configuration toggles in `config.yml` (`coreprotect.enabled`, `coreprotect.sample-step`, `coreprotect.max-samples`).

### Phase 4: Enhanced Dungeon Spawning & Placement Mechanics
- [ ] Merge RDang's biome filtering and height calculation logic into `PlacementService`:
  - WorldBorder clamping.
  - Multi-environment support: Overworld surface, Nether roof/floor, The End islands, Water/Underwater, Air.
  - Biome compatibility matching (per arena/theme configuration).
  - Distance checks: min/max distance from spawn, min distance to existing active dungeons.
  - Anti-lag execution: async chunk preloading, non-blocking candidate sampling, async CoreProtect lookup.
- [ ] Ensure `EventSchedule` / `AutoSpawnManager` supports interval-based autospawn and limit enforcement.

### Phase 5: WorldGuard Region Lifecycle (Creation, Flags & Deletion)
- [ ] Verify `WorldGuardRegions` region creation:
  - Generates unique, clean region name (e.g. `jd_event_<uuid>` or `dang_<id>`).
  - Sets bounding cuboid with configured padding.
  - Applies flags: `BUILD: DENY`, `BLOCK_BREAK: DENY`, `BLOCK_PLACE: DENY`, `TNT: DENY`, `CREEPER_EXPLOSION: DENY`, `LAVA_FIRE: DENY`, `CHEST_ACCESS: ALLOW`, `USE: ALLOW`, `PVP: ALLOW/DENY`, `MOB_SPAWNING: ALLOW/DENY`.
  - Collision detection with existing claims / WorldGuard regions.
- [ ] Ensure clean WorldGuard region deletion on:
  - Dungeon completion / loot claim.
  - Dungeon timeout / failure.
  - Manual stop (`/jd stop <id>`).
  - Server shutdown / reload recovery.
  - Terrain rollback via `Templates.restore(s)`.

### Phase 6: Compilation, Verification & Quality Assurance
- [ ] Run `./gradlew clean build` and verify 0 errors, 0 warnings.
- [ ] Verify shaded JAR contains MariaDB driver and correct `plugin.yml`.
- [ ] Verify backward compatibility and runtime safety if CoreProtect or WorldGuard is missing/disabled.
- [ ] Finalize documentation and BMAD summary.

---

## Key Decisions Log
| Decision | Rationale |
|----------|-----------|
| Base codebase | `JamDungeons-1.1.9` as requested ("Основной плагин тут тот который я прикрепил jar, 90% механик с него брать"). |
| Branch `new` | Dedicated branch keeping `main` pristine. |
| Async CoreProtect | Block coordinates read on main thread, database lookup run async to avoid freezing server tick. |
| Shaded MariaDB | MariaDB JDBC driver bundled inside shadowJar so servers without MariaDB driver in /lib still load. |
