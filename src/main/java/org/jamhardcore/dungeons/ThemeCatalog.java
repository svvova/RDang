/*
 * Decompiled with CFR 0.152.
 */
package org.jamhardcore.dungeons;

import java.util.List;
import java.util.Map;

final class ThemeCatalog {
    private static final Map<String, List<String>> MOBS = Map.of("mining", List.of("zombie", "skeleton", "spider", "cave_spider", "silverfish"), "forest", List.of("drowned", "skeleton", "bogged", "spider", "cave_spider", "witch"), "flying", List.of("skeleton", "phantom", "breeze", "shulker"), "desert", List.of("husk", "husk_spear", "parched", "spider"), "water", List.of("drowned", "drowned_trident", "pillager"), "raiders", List.of("pillager", "vindicator", "witch", "evoker", "vex", "ravager"));

    ThemeCatalog() {
    }

    static List<String> allowed(String theme) {
        return MOBS.get(theme);
    }
}

