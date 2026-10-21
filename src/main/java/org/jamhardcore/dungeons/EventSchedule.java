/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.jamhardcore.dungeons;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.bukkit.plugin.java.JavaPlugin;
import org.jamhardcore.dungeons.Engine;
import org.jamhardcore.dungeons.Model;
import org.jamhardcore.dungeons.Store;

public final class EventSchedule {
    private final Engine e;
    private final JavaPlugin p;
    private long next;
    private long nextIntervalSpawn;

    public EventSchedule(JavaPlugin p, Engine e) {
        this.p = p;
        this.e = e;
    }

    public static List<Integer> dailyOffsets(int start, int end, int amount, int gap, Random random) {
        int i;
        int slack = end - start - Math.max(0, amount - 1) * gap;
        if (amount < 0 || slack < 0) {
            throw new IllegalArgumentException("\u041d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u043d\u043e\u0435 \u0440\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u0435");
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

    public void tick() {
        long now = System.currentTimeMillis();

        // 1. Интервальный авто-спавн данжей
        int intervalMinutes = this.e.c.main().getInt("scheduling.interval-minutes", 0);
        if (intervalMinutes > 0) {
            if (this.nextIntervalSpawn == 0L) {
                this.nextIntervalSpawn = now + (long)intervalMinutes * 60_000L;
            } else if (now >= this.nextIntervalSpawn) {
                this.nextIntervalSpawn = now + (long)intervalMinutes * 60_000L;
                this.triggerIntervalSpawn();
            }
        }

        // 2. Расписание по календарным окнам
        if (now < this.next || !this.e.c.main().getBoolean("scheduling.enabled")) {
            return;
        }
        this.next = now + 15000L;
        ZoneId zone = ZoneId.of(this.e.c.main().getString("timezone"));
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
            this.scheduleAttempt(choices, 0, job, "\u043d\u0435\u0442 \u0433\u043e\u0442\u043e\u0432\u044b\u0445 \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u0445 \u0430\u0440\u0435\u043d");
        }
    }

    private void triggerIntervalSpawn() {
        if (this.e.sessions.size() >= this.e.c.limit("placed-events")) {
            this.p.getLogger().info("Автоспавн: лимит активных данжей (" + this.e.c.limit("placed-events") + ") достигнут, пропуск.");
            return;
        }
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
    }

    private void scheduleAttempt(List<Model.Arena> choices, int index, Store.Job job, String last) {
        if (index >= choices.size()) {
            this.e.db.jobStatus(job.id(), "SKIPPED");
            this.p.getLogger().warning("\u0421\u043e\u0431\u044b\u0442\u0438\u0435 \u043f\u0440\u043e\u043f\u0443\u0449\u0435\u043d\u043e: " + job.id() + ": " + last);
            return;
        }
        Model.Arena arena = choices.get(index);
        ArrayList<String> sites = new ArrayList<String>(arena.sites.keySet());
        String site = arena.template && arena.placement == Model.Placement.STATIC && !sites.isEmpty() ? (String)sites.get(new Random().nextInt(sites.size())) : null;
        this.e.placement.spawn(arena.id, site, false, "SCHEDULER").whenComplete((s, error) -> {
            if (error == null) {
                this.e.db.jobStatus(job.id(), "DONE");
            } else {
                this.scheduleAttempt(choices, index + 1, job, Objects.toString(error.getMessage(), "\u043e\u0448\u0438\u0431\u043a\u0430 \u0440\u0430\u0437\u043c\u0435\u0449\u0435\u043d\u0438\u044f"));
            }
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

