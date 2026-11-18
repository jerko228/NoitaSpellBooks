package ua.spellbooks.noita;

import java.util.*;

public class SpellStats {
    public double damage = 2.0;       // Base reduced damage (1 heart)
    public double speed = 1.5;        // Blocks per tick
    public double range = 30.0;       // Max distance
    public double radius = 1.0;       // AoE radius
    public int count = 1;             // Projectile count
    public int cooldownTicks = 20;    // 1 second base
    public double spread = 0.15;      // Base inaccuracy
    public int xpCost = 0;

    public final List<SpellToken> tokens = new ArrayList<>();
    public final Set<SpellToken> structures = new LinkedHashSet<>();
    public final Set<SpellToken> effects = new LinkedHashSet<>();

    public static SpellStats compile(String fullText) {
        SpellStats stats = new SpellStats();
        Map<SpellToken, Integer> counts = new EnumMap<>(SpellToken.class);

        String[] words = fullText.split("\\s+");
        for (String w : words) {
            SpellToken.fromString(w).ifPresent(token -> {
                stats.tokens.add(token);
                int occurrence = counts.getOrDefault(token, 0) + 1;
                counts.put(token, occurrence);

                // Progressive XP cost for duplicates: 1st=2, 2nd=+2, 3rd=+5, 4th=+10, nth=2+(n-1)^2
                stats.xpCost += calculateTokenCost(occurrence);
                applyTokenStat(stats, token, occurrence);
            });
        }

        // Apply CHEAP discount at the end
        int cheapCount = counts.getOrDefault(SpellToken.CHEAP, 0);
        if (cheapCount > 0 && stats.xpCost > 0) {
            double multiplier = Math.pow(0.85, cheapCount);
            stats.xpCost = Math.max(1, (int) Math.round(stats.xpCost * multiplier));
        }

        // Default structure if none specified
        if (stats.structures.isEmpty() && !stats.tokens.isEmpty()) {
            stats.structures.add(SpellToken.BOLT);
        }

        // Clamp minimum sensible values
        stats.speed = Math.max(0.3, Math.min(4.5, stats.speed));
        stats.radius = Math.max(0.5, Math.min(8.0, stats.radius));
        stats.cooldownTicks = Math.max(5, stats.cooldownTicks);
        stats.spread = Math.max(0.0, stats.spread);

        return stats;
    }

    private static int calculateTokenCost(int occurrence) {
        if (occurrence == 1) return 2;
        if (occurrence == 2) return 2;
        if (occurrence == 3) return 5;
        if (occurrence == 4) return 10;
        return 2 + (int) Math.pow(occurrence - 1, 2);
    }

    private static void applyTokenStat(SpellStats s, SpellToken token, int occurrence) {
        // Diminishing returns factor for stacking modifiers
        double stackEfficiency = 1.0 / Math.sqrt(occurrence);

        switch (token.getCategory()) {
            case STRUCTURE -> {
                s.structures.add(token);
                if (token == SpellToken.TRIPLE || token == SpellToken.BURST) {
                    s.count += 2;
                    s.damage *= 0.85; // Slight per-projectile balance penalty
                    s.cooldownTicks += 6;
                } else if (token == SpellToken.ORB) {
                    s.speed *= 0.4;
                    s.radius += 1.5;
                    s.cooldownTicks += 10;
                } else if (token == SpellToken.AURA) {
                    s.radius += 2.0;
                    s.cooldownTicks += 15;
                }
            }
            case EFFECT -> {
                s.effects.add(token);
                if (token == SpellToken.BOOM) {
                    s.radius += 1.5;
                    s.cooldownTicks += 12;
                } else if (token == SpellToken.LIGHTNING) {
                    s.damage += 2.0;
                    s.cooldownTicks += 10;
                }
            }
            case MODIFIER -> {
                switch (token) {
                    case FAST -> {
                        s.speed += 0.65 * stackEfficiency;
                        s.radius *= 0.95; // Trade-off: tighter area
                    }
                    case SLOW -> {
                        s.speed *= 0.65;
                        s.range += 12.0 * stackEfficiency;
                    }
                    case POWER -> {
                        s.damage += 3.0 * stackEfficiency;
                        s.cooldownTicks += 4; // Trade-off: heavier cast
                    }
                    case GIANT -> {
                        s.radius += 1.2 * stackEfficiency;
                        s.speed *= 0.90; // Trade-off: heavier projectile
                        s.cooldownTicks += 5;
                    }
                    case TINY -> {
                        s.radius *= 0.75;
                        s.cooldownTicks -= 4;
                        s.speed += 0.2;
                    }
                    case FAR -> {
                        s.range += 20.0 * stackEfficiency;
                    }
                    case AIM -> {
                        s.spread *= 0.2;
                    }
                    case BLAST -> {
                        s.radius += 0.75;
                        s.damage += 1.0;
                    }
                    case WET -> {
                        s.damage += 0.5;
                    }
                    default -> {}
                }
            }
        }
    }
}
