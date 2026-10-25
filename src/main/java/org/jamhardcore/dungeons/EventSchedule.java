package org.jamhardcore.dungeons;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventSchedule {
    public static final int MAX_DEFERRED_SPAWNS = 10;

    private final Engine e;
    private final JavaPlugin p;
    private long next;
    private long nextIntervalSpawn;
    private final List<DeferredSpawn> deferredSpawns = new ArrayList<>();

    public static final class DeferredSpawn {
        private String time;
        private String arena;
        private boolean enabled;
        private transient long nextRun;
        private transient long lastFiredEpoch;
        private transient boolean recurring;

        public DeferredSpawn(String time, String arena, boolean enabled) {
            this.time = time;
            this.arena = arena != null && !arena.isBlank() ? arena : "any";
            this.enabled = enabled;
            this.recurring = true;
        }

        public String getTime() {
            return this.time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getArena() {
            return this.arena;
        }

        public void setArena(String arena) {
            this.arena = arena;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getNextRun() {
            return this.nextRun;
        }

        public void setNextRun(long nextRun) {
            this.nextRun = nextRun;
        }

        public long getLastFiredEpoch() {
            return this.lastFiredEpoch;
        }

        public void setLastFiredEpoch(long lastFiredEpoch) {
            this.lastFiredEpoch = lastFiredEpoch;
        }

        public boolean isRecurring() {
            return this.recurring;
        }

        public void setRecurring(boolean recurring) {
            this.recurring = recurring;
        }
    }

    public EventSchedule(JavaPlugin p, Engine e) {
        this.p = p;
        this.e = e;
        this.loadDeferredSpawns();
    }

    public static List<Integer> dailyOffsets(int start, int end, int amount, int gap, Random random) {
        int i;
        int slack = end - start - Math.max(0, amount - 1) * gap;
        if (amount < 0 || slack < 0) {
            throw new IllegalArgumentException("Невозможное расписание");
        }
        ArrayList<Integer> offsets = new ArrayList<Integer>();
        for (i = 0; i < amount; ++i) {
            offsets.add(random.nextInt(slack + 1));
        }
        Collections.sort(offsets);
        for (i = 0; i < amount; ++i) {
            offsets.set(i, start + i * gap + (Integer)offsets.get(i));
        }
        return offsets;
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "сейчас";
        long totalSec = millis / 1000L;
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        long seconds = totalSec % 60L;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" ч. ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append(" мин. ");
        }
        sb.append(seconds).append(" сек.");
        return sb.toString().trim();
    }

    public ZoneId getZoneId() {
        String tz = this.e.c.main().getString("scheduling.timezone");
        if (tz == null || tz.isBlank()) {
            tz = this.e.c.main().getString("timezone", "Europe/Moscow");
        }
        try {
            return ZoneId.of(tz);
        } catch (Exception ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    public synchronized void reload() {
        this.loadDeferredSpawns();
    }

    public synchronized List<DeferredSpawn> getDeferredSpawns() {
        return Collections.unmodifiableList(this.deferredSpawns);
    }

    public synchronized void loadDeferredSpawns() {
        this.deferredSpawns.clear();
        List<?> rawList = this.e.c.main().getList("scheduling.deferred-spawns");
        if (rawList == null || rawList.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        ZoneId zone = this.getZoneId();
        for (Object obj : rawList) {
            if (this.deferredSpawns.size() >= MAX_DEFERRED_SPAWNS) {
                break;
            }
            if (obj instanceof Map<?, ?> map) {
                String time = Objects.toString(map.get("time"), null);
                String arena = Objects.toString(map.get("arena"), "any");
                boolean enabled = map.get("enabled") == null || Boolean.parseBoolean(map.get("enabled").toString());
                if (time != null && !time.isBlank()) {
                    DeferredSpawn ds = new DeferredSpawn(time.trim(), arena.trim(), enabled);
                    try {
                        this.computeNextRun(ds, now, zone);
                        this.deferredSpawns.add(ds);
                    } catch (Exception ex) {
                        this.p.getLogger().warning("Ошибка загрузки отложенного спавна '" + time + "': " + ex.getMessage());
                    }
                }
            } else if (obj instanceof String str) {
                str = str.trim();
                if (str.isEmpty()) continue;
                String time = str;
                String arena = "any";
                if (str.contains(" ")) {
                    String[] parts = str.split("\\s+", 2);
                    time = parts[0];
                    arena = parts[1];
                } else if (str.indexOf(':') != str.lastIndexOf(':')) {
                    int lastColon = str.lastIndexOf(':');
                    time = str.substring(0, lastColon);
                    arena = str.substring(lastColon + 1);
                }
                DeferredSpawn ds = new DeferredSpawn(time, arena, true);
                try {
                    this.computeNextRun(ds, now, zone);
                    this.deferredSpawns.add(ds);
                } catch (Exception ex) {
                    this.p.getLogger().warning("Ошибка загрузки отложенного спавна '" + str + "': " + ex.getMessage());
                }
            }
        }
    }

    public synchronized void saveDeferredSpawns() {
        this.e.c.edit("config", y -> {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (DeferredSpawn ds : this.deferredSpawns) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("time", ds.getTime());
                map.put("arena", ds.getArena());
                map.put("enabled", ds.isEnabled());
                serialized.add(map);
            }
            y.set("scheduling.deferred-spawns", serialized);
        });
    }

    public void computeNextRun(DeferredSpawn spawn, long now, ZoneId zone) {
        String raw = spawn.getTime().trim();
        boolean once = false;
        if (raw.toLowerCase(Locale.ROOT).startsWith("once ")) {
            once = true;
            raw = raw.substring(5).trim();
        }

        // 1. Формат времени в сутках: HH:mm (например: 14:30 или 9:00)
        if (raw.matches("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")) {
            String[] parts = raw.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            ZonedDateTime nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone);
            ZonedDateTime target = nowZoned.with(LocalTime.of(hour, minute)).withSecond(0).withNano(0);
            if (!target.isAfter(nowZoned)) {
                target = target.plusDays(1);
            }
            spawn.setNextRun(target.toInstant().toEpochMilli());
            spawn.setRecurring(!once);
            return;
        }

        // 2. Формат отложенного времени / таймера: +30m, +45, +2h
        String timerStr = raw.startsWith("+") ? raw.substring(1).trim() : raw;
        if (timerStr.matches("^\\d+[mh]?$")) {
            int minutes;
            if (timerStr.endsWith("h")) {
                minutes = Integer.parseInt(timerStr.substring(0, timerStr.length() - 1).trim()) * 60;
            } else if (timerStr.endsWith("m")) {
                minutes = Integer.parseInt(timerStr.substring(0, timerStr.length() - 1).trim());
            } else {
                minutes = Integer.parseInt(timerStr);
            }
            if (minutes < 1) minutes = 1;
            spawn.setNextRun(now + (long)minutes * 60_000L);
            spawn.setRecurring(!once);
            return;
        }

        throw new IllegalArgumentException("Неверный формат времени '" + raw + "'. Примеры: 14:30 (каждый день в 14:30) или +30m (через 30 минут)");
    }

    public void tick() {
        long now = System.currentTimeMillis();
        boolean schedulingEnabled = this.e.c.main().getBoolean("scheduling.enabled", false);

        // 1. Интервальный авто-спавн данжей
        int intervalMinutes = this.e.c.main().getInt("scheduling.interval-minutes", 0);
        if (schedulingEnabled && intervalMinutes > 0) {
            if (this.nextIntervalSpawn == 0L) {
                this.nextIntervalSpawn = now + (long)intervalMinutes * 60_000L;
            } else if (now >= this.nextIntervalSpawn) {
                this.nextIntervalSpawn = now + (long)intervalMinutes * 60_000L;
                this.triggerIntervalSpawn();
            }
        }

        // 2. Отложенные и фиксированные спавны (до 10 слотов)
        if (schedulingEnabled && !this.deferredSpawns.isEmpty()) {
            ZoneId zone = this.getZoneId();
            for (int i = 0; i < this.deferredSpawns.size(); i++) {
                DeferredSpawn spawn = this.deferredSpawns.get(i);
                if (!spawn.isEnabled()) continue;
                if (spawn.getNextRun() == 0L) {
                    try {
                        this.computeNextRun(spawn, now, zone);
                    } catch (Exception ex) {
                        spawn.setEnabled(false);
                        continue;
                    }
                }
                if (now >= spawn.getNextRun() && now - spawn.getLastFiredEpoch() >= 60_000L) {
                    spawn.setLastFiredEpoch(now);
                    this.triggerDeferredSpawn(spawn, i + 1);
                    if (!spawn.isRecurring()) {
                        spawn.setEnabled(false);
                        this.saveDeferredSpawns();
                    } else {
                        try {
                            this.computeNextRun(spawn, now + 1000L, zone);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 3. Расписание по календарным окнам
        if (now < this.next || !schedulingEnabled) {
            return;
        }
        this.next = now + 15000L;
        ZoneId zone = this.getZoneId();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int day = 0; day < 2; ++day) {
            this.generate(today.plusDays(day), zone);
        }
        for (Store.Job job : this.e.db.jobs(now)) {
            this.e.db.jobStatus(job.id(), "STARTING");
            if (now - job.due() > 1000L * (long)this.e.c.main().getInt("scheduling.max-lateness-seconds", 60)) {
                this.e.db.jobStatus(job.id(), "MISSED");
                continue;
            }
            Model.Kind kind = Model.Kind.valueOf(job.kind());
            ArrayList<Model.Arena> choices = new ArrayList<Model.Arena>(this.e.c.arenas.values().stream().filter(a -> a.enabled && a.kind == kind && !this.e.sessions.containsKey(a.id)).toList());
            Collections.shuffle(choices);
            this.scheduleAttempt(choices, 0, job, "нет готовых свободных арен");
        }
    }

    public void setEnabled(boolean enabled) {
        this.e.c.edit("config", y -> y.set("scheduling.enabled", enabled));
        if (enabled) {
            int intervalMinutes = this.e.c.main().getInt("scheduling.interval-minutes", 60);
            this.nextIntervalSpawn = System.currentTimeMillis() + (long)intervalMinutes * 60_000L;
        } else {
            this.nextIntervalSpawn = 0L;
        }
    }

    public void setIntervalMinutes(int minutes) {
        this.e.c.edit("config", y -> {
            y.set("scheduling.interval-minutes", minutes);
            if (minutes > 0 && !y.getBoolean("scheduling.enabled")) {
                y.set("scheduling.enabled", true);
            }
        });
        this.nextIntervalSpawn = System.currentTimeMillis() + (long)minutes * 60_000L;
    }

    public void sendStatus(CommandSender sender) {
        boolean enabled = this.e.c.main().getBoolean("scheduling.enabled", false);
        int interval = this.e.c.main().getInt("scheduling.interval-minutes", 0);
        long now = System.currentTimeMillis();
        long diff = this.nextIntervalSpawn - now;
        String timeRemaining;
        if (!enabled || interval <= 0) {
            timeRemaining = "отключен";
        } else if (diff <= 0) {
            timeRemaining = "сейчас (ожидание тика)";
        } else {
            timeRemaining = formatDuration(diff);
        }
        int active = this.e.sessions.size();
        int limit = this.e.c.limit("placed-events");
        List<String> enabledArenas = this.e.c.arenas.values().stream()
                .filter(a -> a.enabled)
                .map(a -> a.id)
                .toList();

        sender.sendMessage(Messages.color("&6=== &eRDang Автоспавн &6==="));
        sender.sendMessage(Messages.color("&7Статус автоспавна: " + (enabled ? "&aВКЛЮЧЕН" : "&cВЫКЛЮЧЕН")));
        sender.sendMessage(Messages.color("&7Часовой пояс: &e" + this.getZoneId().getId()));
        sender.sendMessage(Messages.color("&7Интервал: &e" + (interval > 0 ? interval + " мин." : "не задан")));
        sender.sendMessage(Messages.color("&7До интервального спавна: &b" + timeRemaining));
        sender.sendMessage(Messages.color("&7Активных данжей: &f" + active + "&7/&f" + limit));
        sender.sendMessage(Messages.color("&7Доступные арены: &a" + (enabledArenas.isEmpty() ? "&c(нет включенных)" : String.join(", ", enabledArenas))));

        // Отображение отложенных спавнов
        if (!this.deferredSpawns.isEmpty()) {
            sender.sendMessage(Messages.color("&6--- Отложенные спавны (" + this.deferredSpawns.size() + "/" + MAX_DEFERRED_SPAWNS + ") ---"));
            DeferredSpawn nextSoon = null;
            for (DeferredSpawn ds : this.deferredSpawns) {
                if (ds.isEnabled() && ds.getNextRun() > now) {
                    if (nextSoon == null || ds.getNextRun() < nextSoon.getNextRun()) {
                        nextSoon = ds;
                    }
                }
            }
            if (nextSoon != null) {
                sender.sendMessage(Messages.color("&7Ближайший отложенный: &e" + nextSoon.getTime() + " &7(арена: &f" + nextSoon.getArena() + "&7) через &b" + formatDuration(nextSoon.getNextRun() - now)));
            }
            sender.sendMessage(Messages.color("&7Список всех слотов: &f/jd schedule list"));
        } else {
            sender.sendMessage(Messages.color("&7Отложенных спавнов: &8нет (добавить: /jd schedule add <время> [арена])"));
        }
    }

    public synchronized void sendDeferredList(CommandSender sender) {
        boolean enabled = this.e.c.main().getBoolean("scheduling.enabled", false);
        ZoneId zone = this.getZoneId();
        long now = System.currentTimeMillis();
        sender.sendMessage(Messages.color("&6=== &eОтложенные спавны данжей &7(" + this.deferredSpawns.size() + "/" + MAX_DEFERRED_SPAWNS + ") &6==="));
        sender.sendMessage(Messages.color("&7Часовой пояс: &e" + zone.getId() + " &7| Автоспавн: " + (enabled ? "&aВКЛЮЧЕН" : "&cВЫКЛЮЧЕН")));
        if (this.deferredSpawns.isEmpty()) {
            sender.sendMessage(Messages.color("&7Нет настроенных отложенных спавнов."));
            sender.sendMessage(Messages.color("&7Добавить: &f/jd schedule add 14:30 [arena] &7или &f/jd schedule add +30m [arena]"));
            return;
        }
        for (int i = 0; i < this.deferredSpawns.size(); i++) {
            DeferredSpawn ds = this.deferredSpawns.get(i);
            String status = ds.isEnabled() ? "&a[ВКЛ]" : "&c[ВЫКЛ]";
            String arenaStr = ds.getArena().equalsIgnoreCase("any") ? "&eслучайная" : "&b" + ds.getArena();
            String until = ds.isEnabled() ? "&fчерез &a" + formatDuration(ds.getNextRun() - now) : "&8(отключен)";
            sender.sendMessage(Messages.color("&e[" + (i + 1) + "] " + status + " &fВремя: &e" + ds.getTime() + " &7| Арена: " + arenaStr + " &7| " + until));
        }
        sender.sendMessage(Messages.color("&7Команды: &f/jd schedule add <время> [арена] &7| &fremove <номер> &7| &ftoggle <номер> &7| &fclear"));
    }

    public synchronized void addDeferredSpawn(String time, String arena, CommandSender sender) {
        if (this.deferredSpawns.size() >= MAX_DEFERRED_SPAWNS) {
            throw new IllegalArgumentException("Достигнут лимит отложенных спавнов (максимум " + MAX_DEFERRED_SPAWNS + "). Удалите ненужные: /jd schedule remove <номер>");
        }
        String targetArena = (arena == null || arena.isBlank() || arena.equalsIgnoreCase("any") || arena.equalsIgnoreCase("all")) ? "any" : arena.trim();
        if (!targetArena.equals("any") && !this.e.c.arenas.containsKey(targetArena)) {
            throw new IllegalArgumentException("Арена '" + targetArena + "' не найдена. Доступные арены: " + String.join(", ", this.e.c.arenas.keySet()));
        }
        DeferredSpawn spawn = new DeferredSpawn(time.trim(), targetArena, true);
        long now = System.currentTimeMillis();
        ZoneId zone = this.getZoneId();
        this.computeNextRun(spawn, now, zone);
        this.deferredSpawns.add(spawn);
        this.saveDeferredSpawns();

        if (!this.e.c.main().getBoolean("scheduling.enabled", false)) {
            this.setEnabled(true);
            if (sender != null) {
                sender.sendMessage(Messages.color("&e[RDang] Автоспавн включен автоматически."));
            }
        }
        if (sender != null) {
            sender.sendMessage(Messages.color("&aОтложенный спавн #" + this.deferredSpawns.size() + " добавлен: &e" + spawn.getTime() + " &7(арена: &f" + spawn.getArena() + "&7). До запуска: &b" + formatDuration(spawn.getNextRun() - now)));
        }
    }

    public synchronized void removeDeferredSpawn(int index, CommandSender sender) {
        if (index < 1 || index > this.deferredSpawns.size()) {
            throw new IllegalArgumentException("Неверный номер. Укажите от 1 до " + this.deferredSpawns.size() + ". Список: /jd schedule list");
        }
        DeferredSpawn removed = this.deferredSpawns.remove(index - 1);
        this.saveDeferredSpawns();
        if (sender != null) {
            sender.sendMessage(Messages.color("&aОтложенный спавн #" + index + " (&e" + removed.getTime() + "&a) удален."));
        }
    }

    public synchronized void toggleDeferredSpawn(int index, CommandSender sender) {
        if (index < 1 || index > this.deferredSpawns.size()) {
            throw new IllegalArgumentException("Неверный номер. Укажите от 1 до " + this.deferredSpawns.size() + ". Список: /jd schedule list");
        }
        DeferredSpawn spawn = this.deferredSpawns.get(index - 1);
        spawn.setEnabled(!spawn.isEnabled());
        if (spawn.isEnabled()) {
            this.computeNextRun(spawn, System.currentTimeMillis(), this.getZoneId());
        }
        this.saveDeferredSpawns();
        if (sender != null) {
            sender.sendMessage(Messages.color("&aОтложенный спавн #" + index + " (&e" + spawn.getTime() + "&a) " + (spawn.isEnabled() ? "&aВКЛЮЧЕН" : "&cВЫКЛЮЧЕН") + "."));
        }
    }

    public synchronized void clearDeferredSpawns(CommandSender sender) {
        int count = this.deferredSpawns.size();
        this.deferredSpawns.clear();
        this.saveDeferredSpawns();
        if (sender != null) {
            sender.sendMessage(Messages.color("&aОчищено отложенных спавнов: " + count));
        }
    }

    public void handleCommand(CommandSender sender, String[] a) {
        int offset = 0;
        if (a.length > 0 && a[0].equalsIgnoreCase("autospawn") && a.length > 1 && a[1].equalsIgnoreCase("schedule")) {
            offset = 1;
        }
        int effectiveLength = a.length - offset;
        if (effectiveLength < 2 || a[offset + 1].equalsIgnoreCase("list")) {
            this.sendDeferredList(sender);
            return;
        }
        String sub = a[offset + 1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add": {
                if (effectiveLength < 3) {
                    throw new IllegalArgumentException("Использование: /jd schedule add <время> [арена]\nПримеры: 14:30 (каждый день в 14:30), +30m (через 30 минут)");
                }
                String time = a[offset + 2];
                String arena = effectiveLength > 3 ? a[offset + 3] : "any";
                this.addDeferredSpawn(time, arena, sender);
                break;
            }
            case "remove":
            case "del":
            case "delete": {
                if (effectiveLength < 3) {
                    throw new IllegalArgumentException("Использование: /jd schedule remove <номер 1-" + this.deferredSpawns.size() + ">");
                }
                int index = Integer.parseInt(a[offset + 2]);
                this.removeDeferredSpawn(index, sender);
                break;
            }
            case "toggle": {
                if (effectiveLength < 3) {
                    throw new IllegalArgumentException("Использование: /jd schedule toggle <номер 1-" + this.deferredSpawns.size() + ">");
                }
                int index = Integer.parseInt(a[offset + 2]);
                this.toggleDeferredSpawn(index, sender);
                break;
            }
            case "clear": {
                this.clearDeferredSpawns(sender);
                break;
            }
            default: {
                throw new IllegalArgumentException("Использование: /jd schedule list|add <время> [арена]|remove <номер>|toggle <номер>|clear");
            }
        }
    }

    private CompletableFuture<Void> clearActiveDungeons(String reason) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Model.Session old : new ArrayList<>(this.e.sessions.values())) {
            this.p.getLogger().info("Ротация данжей: убираем старый данж " + old.arenaId + " перед новым спавном.");
            try {
                futures.add(this.e.stop(old.arenaId, reason));
            } catch (Exception ex) {
                this.p.getLogger().warning("Ошибка при остановке старого данжа " + old.arenaId + ": " + ex.getMessage());
            }
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public void triggerNow(CommandSender sender) {
        sender.sendMessage(Messages.color("&eОчистка старых данжей и подготовка нового спавна..."));
        this.clearActiveDungeons("MANUAL_TRIGGER").whenComplete((v, stopErr) -> {
            Bukkit.getScheduler().runTask(this.p, () -> {
                List<Model.Arena> choices = new ArrayList<>(this.e.c.arenas.values().stream()
                        .filter(a -> a.enabled && !this.e.sessions.containsKey(a.id))
                        .toList());
                if (choices.isEmpty()) {
                    sender.sendMessage(Messages.color("&cНет свободных включенных арен для спавна. Включите арены через /jd enable <id> on"));
                    return;
                }
                Collections.shuffle(choices);
                Model.Arena target = choices.get(0);
                sender.sendMessage(Messages.color("&eЗапуск внепланового автоспавна для арены &a" + target.id + "&e..."));
                this.e.placement.spawn(target.id, null, false, sender.getName()).whenComplete((session, err) -> {
                    if (err != null) {
                        sender.sendMessage(Messages.color("&cОшибка автоспавна " + target.id + ": " + err.getMessage()));
                    } else {
                        sender.sendMessage(Messages.color("&aУспешно заспавнен данж: &e" + target.id + " &7(ID: " + session.id + ")"));
                    }
                });
            });
        });
    }

    private void triggerIntervalSpawn() {
        this.clearActiveDungeons("INTERVAL_ROTATION").whenComplete((v, stopErr) -> {
            Bukkit.getScheduler().runTask(this.p, () -> {
                List<Model.Arena> choices = new ArrayList<>(this.e.c.arenas.values().stream()
                        .filter(a -> a.enabled && !this.e.sessions.containsKey(a.id))
                        .toList());
                if (choices.isEmpty()) {
                    this.p.getLogger().warning("Автоспавн: нет свободных включенных арен.");
                    return;
                }
                Collections.shuffle(choices);
                Model.Arena target = choices.get(0);
                this.p.getLogger().info("Автоспавн: запуск поиска места для данжа " + target.id + "...");
                this.e.placement.spawn(target.id, null, false, "AUTO_SPAWNER").whenComplete((session, err) -> {
                    if (err != null) {
                        this.p.getLogger().warning("Автоспавн: ошибка размещения " + target.id + ": " + err.getMessage());
                    } else {
                        this.p.getLogger().info("Автоспавн: успешно заспавнен данж " + target.id + " (" + session.id + ")");
                    }
                });
            });
        });
    }

    private void triggerDeferredSpawn(DeferredSpawn spawn, int index) {
        this.clearActiveDungeons("DEFERRED_ROTATION_" + index).whenComplete((v, stopErr) -> {
            Bukkit.getScheduler().runTask(this.p, () -> {
                Model.Arena target = null;
                if (spawn.arena != null && !spawn.arena.equalsIgnoreCase("any") && !spawn.arena.equalsIgnoreCase("all")) {
                    target = this.e.c.arenas.get(spawn.arena);
                    if (target == null || !target.enabled) {
                        this.p.getLogger().warning("Отложенный спавн #" + index + " [" + spawn.time + "]: арена '" + spawn.arena + "' не найдена или выключена (/jd enable <id> on).");
                        return;
                    }
                    if (this.e.sessions.containsKey(target.id)) {
                        this.p.getLogger().info("Отложенный спавн #" + index + " [" + spawn.time + "]: арена '" + target.id + "' уже активна, пропуск.");
                        return;
                    }
                } else {
                    List<Model.Arena> choices = new ArrayList<>(this.e.c.arenas.values().stream()
                            .filter(a -> a.enabled && !this.e.sessions.containsKey(a.id))
                            .toList());
                    if (choices.isEmpty()) {
                        this.p.getLogger().warning("Отложенный спавн #" + index + " [" + spawn.time + "]: нет свободных включенных арен.");
                        return;
                    }
                    Collections.shuffle(choices);
                    target = choices.get(0);
                }

                final Model.Arena selected = target;
                this.p.getLogger().info("Отложенный спавн #" + index + " [" + spawn.time + "]: запуск поиска места для данжа " + selected.id + "...");
                this.e.placement.spawn(selected.id, null, false, "DEFERRED_SPAWNER_" + index).whenComplete((session, err) -> {
                    if (err != null) {
                        this.p.getLogger().warning("Отложенный спавн #" + index + " [" + spawn.time + "]: ошибка размещения " + selected.id + ": " + err.getMessage());
                    } else {
                        this.p.getLogger().info("Отложенный спавн #" + index + " [" + spawn.time + "]: успешно заспавнен данж " + selected.id + " (" + session.id + ")");
                    }
                });
            });
        });
    }

    private void scheduleAttempt(List<Model.Arena> choices, int index, Store.Job job, String last) {
        if (index >= choices.size()) {
            this.e.db.jobStatus(job.id(), "SKIPPED");
            this.p.getLogger().warning("Событие пропущено: " + job.id() + ": " + last);
            return;
        }
        Model.Arena arena = choices.get(index);
        ArrayList<String> sites = new ArrayList<String>(arena.sites.keySet());
        String site = arena.template && arena.placement == Model.Placement.STATIC && !sites.isEmpty() ? (String)sites.get(new Random().nextInt(sites.size())) : null;
        this.clearActiveDungeons("SCHEDULE_CRON").whenComplete((v, stopErr) -> {
            Bukkit.getScheduler().runTask(this.p, () -> {
                this.e.placement.spawn(arena.id, site, false, "SCHEDULER").whenComplete((s, error) -> {
                    if (error == null) {
                        this.e.db.jobStatus(job.id(), "DONE");
                    } else {
                        this.scheduleAttempt(choices, index + 1, job, Objects.toString(error.getMessage(), "ошибка размещения"));
                    }
                });
            });
        });
    }

    private void generate(LocalDate day, ZoneId zone) {
        String marker = String.valueOf(day) + "-PVE-";
        if (!this.e.db.jobExists(marker + "marker")) {
            int n = this.e.c.main().getInt("scheduling.pve-per-day");
            int start = LocalTime.parse(this.e.c.main().getString("scheduling.pve-window-start")).toSecondOfDay() / 60;
            int end = LocalTime.parse(this.e.c.main().getString("scheduling.pve-window-end")).toSecondOfDay() / 60;
            int gap = this.e.c.main().getInt("scheduling.pve-gap-minutes");
            List<Integer> offsets = EventSchedule.dailyOffsets(start, end, n, gap, new Random(day.toEpochDay() ^ 0x4A616D48617264L));
            for (int i = 0; i < offsets.size(); ++i) {
                this.e.db.job(marker + i, day.atStartOfDay(zone).plusMinutes(offsets.get(i).intValue()).toInstant().toEpochMilli(), "PVE");
            }
            this.e.db.job(marker + "marker", 0L, "PVE");
            this.e.db.jobStatus(marker + "marker", "GENERATED");
        }
        if (this.e.c.main().getStringList("scheduling.pvp-days").contains(day.getDayOfWeek().name())) {
            this.e.db.job(String.valueOf(day) + "-PVP", day.atTime(LocalTime.parse(this.e.c.main().getString("scheduling.pvp-time"))).atZone(zone).toInstant().toEpochMilli(), "PVP");
        }
    }
}
