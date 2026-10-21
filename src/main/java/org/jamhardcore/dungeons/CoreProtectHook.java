package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Интеграция с CoreProtect для проверки наличия построек игроков перед спавном данжа.
 * Сбор блоков контрольных точек производится строго в главном потоке (sync),
 * а тяжелые обращения к БД CoreProtect (blockLookup) выполняются асинхронно.
 */
public final class CoreProtectHook {
    private final JamDungeons plugin;
    private final Configs configs;
    private CoreProtectAPI api;
    private boolean initialized;

    public CoreProtectHook(JamDungeons plugin, Configs configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public synchronized void init() {
        if (!this.configs.main().getBoolean("coreprotect.enabled", true)) {
            this.api = null;
            this.initialized = true;
            this.plugin.getLogger().info("CoreProtect отключен в config.yml");
            return;
        }
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("CoreProtect");
            if (p instanceof CoreProtect cp) {
                CoreProtectAPI cpApi = cp.getAPI();
                if (cpApi != null && cpApi.isEnabled()) {
                    this.api = cpApi;
                    this.plugin.getLogger().info("CoreProtect успешно подключен — сканирование построек игроков активно.");
                } else {
                    this.plugin.getLogger().warning("CoreProtect найден, но API недоступен или отключен.");
                }
            } else {
                this.plugin.getLogger().info("CoreProtect не обнаружен — сканирование истории блоков отключено.");
            }
        } catch (Throwable t) {
            this.plugin.getLogger().log(Level.WARNING, "Не удалось инициализировать CoreProtect API: " + t.getMessage(), t);
        }
        this.initialized = true;
    }

    public boolean isAvailable() {
        if (!this.initialized) {
            this.init();
        }
        return this.api != null && this.configs.main().getBoolean("coreprotect.enabled", true);
    }

    /**
     * Сбор блоков контрольных точек на поверхности и по периметру коробки данжа.
     * ОБЯЗАТЕЛЬНО вызывать в главном потоке сервера.
     */
    public List<Block> collectSampleBlocks(World world, Model.Box box) {
        ArrayList<Block> samples = new ArrayList<>();
        if (!this.isAvailable() || world == null) {
            return samples;
        }

        int step = this.configs.main().getInt("coreprotect.sample-step", 4);
        if (step < 1) step = 1;
        int maxChecks = this.configs.main().getInt("coreprotect.max-samples", 64);
        int checks = 0;

        int x1 = box.x1();
        int x2 = box.x2();
        int z1 = box.z1();
        int z2 = box.z2();

        // 1. Сканирование поверхности по сетке (как в RDang)
        for (int x = x1; x <= x2; x += step) {
            for (int z = z1; z <= z2; z += step) {
                if (checks >= maxChecks) break;
                int y = world.getHighestBlockYAt(x, z);
                if (y >= world.getMinHeight() && y <= world.getMaxHeight()) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        samples.add(b);
                        checks++;
                    }
                }
            }
            if (checks >= maxChecks) break;
        }

        // 2. Сканирование контрольных точек внутри коробки данжа
        if (this.configs.main().getBoolean("coreprotect.check-interior", true) && checks < maxChecks) {
            int yStep = Math.max(3, (box.y2() - box.y1()) / 3);
            for (int y = box.y1(); y <= box.y2(); y += yStep) {
                for (int x = x1; x <= x2; x += step * 2) {
                    for (int z = z1; z <= z2; z += step * 2) {
                        if (checks >= maxChecks) break;
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) {
                            samples.add(b);
                            checks++;
                        }
                    }
                    if (checks >= maxChecks) break;
                }
                if (checks >= maxChecks) break;
            }
        }

        return samples;
    }

    /**
     * Асинхронный поиск действий игроков по базе CoreProtect.
     * Возвращает true, если среди переданных блоков найдены блоки, установленные игроками.
     */
    public CompletableFuture<Boolean> hasPlayerBuildsAsync(List<Block> blocks) {
        if (!this.isAvailable() || blocks == null || blocks.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                for (Block block : blocks) {
                    List<String[]> lookup = this.api.blockLookup(block, 0);
                    if (lookup == null || lookup.isEmpty()) continue;
                    for (String[] line : lookup) {
                        CoreProtectAPI.ParseResult parse = this.api.parseResult(line);
                        // actionId 1 = блок поставлен игроком
                        if (parse.getActionId() == 1) {
                            String player = parse.getPlayer();
                            // Игнорируем природные изменения (#water, #fire, #environment и т.д.)
                            if (player != null && !player.isBlank() && !player.startsWith("#")) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                this.plugin.getLogger().log(Level.WARNING, "Ошибка при проверке истории CoreProtect: " + t.getMessage(), t);
            }
            return false;
        });
    }
}
