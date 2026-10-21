# Technical Findings & Analysis

## JamDungeons-1.1.9 Jar Decompilation Analysis
- Jar location: `E:\Downloads\JamDungeons-1.1.9.jar` (~1,011,150 bytes).
- Contains:
  - 28 Java source classes under package `org.jamhardcore.dungeons` (and `org.jamhardcore.dungeons.api`).
  - Embedded MariaDB JDBC driver 3.5.3 (`org.mariadb.jdbc`).
  - Resources: `plugin.yml`, `config.yml`, `arenas.yml`, `messages.yml`, `rewards.yml`, `themes.yml`, and `migrations/` directory.
  - Dependencies originally targeted Paper API, WorldEdit/FAWE, WorldGuard, Vault, PlaceholderAPI, PhoenixCrates.

## Decompilation Nuances
- CFR decompiler succeeded on all 28 classes.
- One minor CFR decompilation artifact found in `WorldGuardRegions.java`:
  - `ArrayList<CallSite> failures = new ArrayList<CallSite>();`
  - Needs to be standard `List<String> failures = new ArrayList<>();`.
- Class `Store.java` uses `MariaDbPoolDataSource` and handles MariaDB connection pooling and table schemas (`sessions`, `rewards`, `outcomes`, `audit`, `jobs`, `vouchers`, `pending_returns`).

## RDang Spawning & CoreProtect Mechanics Analysis
- `RDang` checked player builds using `CoreProtectAPI`:
  - `collectSurfaceBlocks(Location center, int radiusX, int radiusZ)` on the main thread (uses `getHighestBlockYAt` and `getBlockAt`).
  - Step of 5 blocks, maximum 64 sample blocks.
  - `hasPlayerBuilds(List<Block> blocks)` runs asynchronously: calls `api.blockLookup(block, 0)`, checks `parse.getActionId() == 1` (placed block).
  - If player placed blocks are found in candidate area, spawning is aborted and another location is chosen.
- Height detection in RDang (`SpawnManager.java`):
  - Nether: scans upward from `minY` for solid floor with air above.
  - The End: scans downward for end stone / obsidian with air above.
  - Surface: scans downward for solid ground with air above.
- WorldBorder clamping:
  - `getBorderClampedRange(World world)` calculates min/max bounds based on `world.getWorldBorder()`.

## Target Architecture Synthesis
- Maintain the JamDungeons structure as the primary framework.
- Integrate `CoreProtectHook` into `PlacementService`.
- Enrich `PlacementService.candidate(...)` with multi-environment height selection (Overworld, Nether, End, Water, Air), biome verification, and non-blocking asynchronous CoreProtect lookups.
- Maintain WorldGuard region lifecycle: creating on spawn with comprehensive flags and safely removing on despawn/finish/stop.
