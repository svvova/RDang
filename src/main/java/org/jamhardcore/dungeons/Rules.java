/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.util.Vector
 */
package org.jamhardcore.dungeons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;
import org.bukkit.util.Vector;
import org.jamhardcore.dungeons.Model;

public final class Rules {
    public static final int PVE_SECONDS = 900;
    public static final int WAVE_SECONDS = 180;

    private Rules() {
    }

    public static int wavesDue(long now, long start) {
        return now < start ? 0 : Math.min(5, (int)((now - start) / 180000L) + 1);
    }

    public static long nextStage(long now, long start, int wave) {
        return Rules.remaining(now, start + (long)(Math.min(5, Math.max(1, wave)) * 180) * 1000L);
    }

    public static double distanceSquared(Model.Box box, double x, double z) {
        double dx = Math.max(Math.max((double)box.x1() - x, 0.0), x - (double)(box.x2() + 1));
        double dz = Math.max(Math.max((double)box.z1() - z, 0.0), z - (double)(box.z2() + 1));
        return dx * dx + dz * dz;
    }

    public static Vector pushDirection(Model.Box box, double x, double z, double strength) {
        double dz;
        double dx = x - Math.clamp(x, (double)box.x1(), (double)box.x2() + 1.0);
        if (dx * dx + (dz = z - Math.clamp(z, (double)box.z1(), (double)box.z2() + 1.0)) * dz < 1.0E-6) {
            double[] distances = new double[]{x - (double)box.x1(), (double)(box.x2() + 1) - x, z - (double)box.z1(), (double)(box.z2() + 1) - z};
            int side = 0;
            for (int i = 1; i < 4; ++i) {
                if (!(distances[i] < distances[side])) continue;
                side = i;
            }
            double d = side == 0 ? -1.0 : (dx = side == 1 ? 1.0 : 0.0);
            dz = side == 2 ? -1.0 : (side == 3 ? 1.0 : 0.0);
        }
        return new Vector(dx, 0.0, dz).normalize().multiply(strength).setY(0.12);
    }

    public static int dueWave(long now, long start, int interval, int duration) {
        if (now < start || now >= start + (long)duration * 1000L) {
            return 0;
        }
        return Math.min(5, (int)((now - start) / ((long)interval * 1000L)) + 1);
    }

    public static long remaining(long now, long end) {
        return Math.max(0L, (end - now + 999L) / 1000L);
    }

    public static String time(long seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public static boolean validId(String s) {
        return s != null && s.matches("[a-z0-9_-]{1,40}");
    }

    public static <T> List<T> pick(List<T> values, int count, ToDoubleFunction<T> weight, RandomGenerator rng) {
        if (count < 0 || count > values.size()) {
            throw new IllegalArgumentException("\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0439 \u043d\u0430\u0433\u0440\u0430\u0434");
        }
        ArrayList<T> bag = new ArrayList<T>(values);
        ArrayList<T> result = new ArrayList<T>();
        for (int i = 0; i < count; ++i) {
            double sum = bag.stream().mapToDouble(weight).sum();
            if (!Double.isFinite(sum) || sum <= 0.0) {
                throw new IllegalArgumentException("\u041d\u0435\u043a\u043e\u0440\u0440\u0435\u043a\u0442\u043d\u044b\u0435 \u0432\u0435\u0441\u0430");
            }
            double r = rng.nextDouble(sum);
            T chosen = bag.getLast();
            for (T value : bag) {
                if (!((r -= weight.applyAsDouble(value)) < 0.0)) continue;
                chosen = value;
                break;
            }
            result.add(chosen);
            bag.remove(chosen);
        }
        return result;
    }

    public static void positiveWeight(double n) {
        if (!Double.isFinite(n) || n <= 0.0) {
            throw new IllegalArgumentException("\u0412\u0435\u0441 \u0434\u043e\u043b\u0436\u0435\u043d \u0431\u044b\u0442\u044c \u043f\u043e\u043b\u043e\u0436\u0438\u0442\u0435\u043b\u044c\u043d\u044b\u043c");
        }
    }
}

