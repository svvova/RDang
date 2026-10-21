# Architectural & Technical Findings

## 1. CoreProtect Main Thread vs Async Threading
- **Finding:** In Bukkit/Paper, calling `world.getBlockAt(...)` or `world.getHighestBlockYAt(...)` from an asynchronous thread causes thread-safety exceptions or corrupts chunk access. However, `CoreProtectAPI.blockLookup(...)` performs SQL queries against the CoreProtect SQLite or MySQL database, which must NOT run on the main thread because database latency causes server tick stalls.
- **Solution:** Hybrid execution implemented in `CoreProtectHook`:
  1. Main thread collects sample block references via `collectSampleBlocks` (surface grid and interior checkpoints).
  2. Asynchronous thread queries CoreProtect database via `hasPlayerBuildsAsync` (`CompletableFuture`).
  3. Result callback returns to the main thread via `Bukkit.getScheduler().runTask(...)` to proceed with dungeon spawning or retry another candidate.

## 2. 3D Bounding Box Scanning & Server Lag
- **Finding:** The original JamDungeons code in `candidate(...)` iterated over every single block $(x_1 \dots x_2, y_1 \dots y_2, z_1 \dots z_2)$ in the 3D box synchronously on the server tick. For a medium-sized 30x20x30 dungeon, this performed 18,000 block reads per candidate; across 64 placement attempts, up to 1.15 million block queries could run synchronously, causing massive TPS drops.
- **Solution:** Implemented stepped scanning (`check-step: 3`, reducing block accesses by ~96%) combined with async chunk loading. Player structures (which are multi-block structures) are easily detected by stepped sampling and historical CoreProtect logs without impacting TPS.

## 3. Dimensional Terrain & Height Detection
- **Finding:** In the Nether, `world.getHighestBlockYAt(...)` returns the topmost bedrock roof block (Y=127 or 255), causing dungeons to spawn on top of the bedrock ceiling. In the End, height maps can return void boundaries.
- **Solution:** `PlacementService.getGroundHeight` scans downwards from below the Nether ceiling (Y=115 down to Y=32) for solid ground with passable air space above. For The End, it scans between Y=80 and Y=40 for End stone or obsidian surfaces.

## 4. WorldGuard 7 Version Compatibility
- **Finding:** Different builds of WorldGuard 7 have slight differences in available StateFlags and RemovalStrategy enums. Hardcoded reflection invocations caused `NoSuchFieldException` or `IllegalArgumentException` on older or newer builds.
- **Solution:** Individual flag setters in `WorldGuardRegions.set(...)` are wrapped in exception guards, and `remove(...)` checks whether the region actually exists before removing, preventing failed deletion states during dungeon rollback.
