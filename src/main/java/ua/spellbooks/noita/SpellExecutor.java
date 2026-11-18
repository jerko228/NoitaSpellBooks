package ua.spellbooks.noita;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpellExecutor {
    private final Plugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SpellExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean cast(Player player, SpellStats stats) {
        long now = System.currentTimeMillis();
        long cdMillis = stats.cooldownTicks * 50L;
        long readyAt = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now < readyAt) {
            double left = (readyAt - now) / 1000.0;
           player.spigot().sendMessage(
    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
    net.md_5.bungee.api.chat.TextComponent.fromLegacyText("Перезарядка")
);
        }

        cooldowns.put(player.getUniqueId(), now + cdMillis);
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.2f);

        if (stats.structures.contains(SpellToken.AURA)) {
            applyImpact(player, player.getLocation(), stats);
            spawnRingParticles(player.getLocation().add(0, 1, 0), stats.radius, stats);
            return true;
        }

        if (stats.structures.contains(SpellToken.TOUCH)) {
            Location target = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3.0));
            applyImpact(player, target, stats);
            return true;
        }

        if (stats.structures.contains(SpellToken.MINE)) {
            spawnMine(player, player.getLocation(), stats);
            return true;
        }

        int projectiles = stats.count;
        boolean isBurst = stats.structures.contains(SpellToken.BURST);

        for (int i = 0; i < projectiles; i++) {
            final int index = i;
            long delay = isBurst ? i * 4L : 0L;

            new BukkitRunnable() {
                @Override
                public void run() {
                    launchProjectile(player, stats, index, projectiles);
                }
            }.runTaskLater(plugin, delay);
        }

        return true;
    }

    private void launchProjectile(Player caster, SpellStats stats, int index, int total) {
        Location start = caster.getEyeLocation();
        Vector dir = start.getDirection().normalize();

        // Spread calculation
        double spreadAmount = stats.spread;
        if (stats.structures.contains(SpellToken.TRIPLE) && total > 1) {
            double angleOffset = (index - (total - 1) / 2.0) * (0.18 + spreadAmount);
            dir.rotateAroundY(angleOffset);
        } else if (spreadAmount > 0) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            dir.add(new Vector(
                    rnd.nextDouble(-spreadAmount, spreadAmount),
                    rnd.nextDouble(-spreadAmount, spreadAmount),
                    rnd.nextDouble(-spreadAmount, spreadAmount)
            )).normalize();
        }

        boolean isArrow = stats.structures.contains(SpellToken.ARROW);
        boolean isOrb = stats.structures.contains(SpellToken.ORB);
        boolean isBoomerang = stats.structures.contains(SpellToken.BOOMERANG);
        boolean isBounce = stats.structures.contains(SpellToken.BOUNCE);

        new BukkitRunnable() {
            Location current = start.clone();
            Vector velocity = dir.clone().multiply(stats.speed * 0.5);
            double traveled = 0;
            int bounces = isBounce ? 3 : 0;
            boolean returning = false;
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (traveled >= stats.range || ticks > 200 || !caster.isOnline()) {
                    applyImpact(caster, current, stats);
                    cancel();
                    return;
                }

                if (isArrow) {
                    velocity.setY(velocity.getY() - 0.03); // Gravity arc
                }

                if (isBoomerang && !returning && traveled >= stats.range * 0.5) {
                    returning = true;
                }

                if (returning) {
                    Vector toCaster = caster.getEyeLocation().toVector().subtract(current.toVector());
                    if (toCaster.length() < 1.5) {
                        cancel();
                        return;
                    }
                    velocity = toCaster.normalize().multiply(stats.speed * 0.6);
                }

                Location next = current.clone().add(velocity);
                traveled += velocity.length();

                // Particle trail
                spawnTrailParticle(current, stats);

                // Orb pulses damage while flying
                if (isOrb && ticks % 10 == 0) {
                    applyImpact(caster, current, stats);
                }

                // Digging through blocks
                if (stats.effects.contains(SpellToken.DIG)) {
                    Block b = next.getBlock();
                    if (!b.getType().isAir() && b.getType().getHardness() >= 0 && b.getType().getHardness() < 50) {
                        b.breakNaturally();
                    }
                }

                // Block collision
                if (!next.getBlock().isPassable() && !stats.effects.contains(SpellToken.DIG)) {
                    if (bounces > 0) {
                        bounces--;
                        velocity.multiply(-0.8);
                    } else {
                        applyImpact(caster, current, stats);
                        cancel();
                        return;
                    }
                }

                // Entity collision
                for (Entity e : current.getWorld().getNearbyEntities(current, 0.8, 0.8, 0.8)) {
                    if (e instanceof LivingEntity target && !e.equals(caster)) {
                        applyImpact(caster, current, stats);
                        if (!isOrb) {
                            cancel();
                            return;
                        }
                    }
                }

                current = next;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnMine(Player caster, Location loc, SpellStats stats) {
        loc.getWorld().playSound(loc, Sound.BLOCK_STONE_PRESSURE_PLATE_CLICK_ON, 1f, 0.8f);
        new BukkitRunnable() {
            int life = 0;

            @Override
            public void run() {
                life += 5;
                if (life > 600) { // 30 seconds max
                    cancel();
                    return;
                }
                loc.getWorld().spawnParticle(Particle.CRIT_MAGIC, loc.clone().add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0);
                for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                    if (e instanceof LivingEntity && !e.equals(caster)) {
                        applyImpact(caster, loc, stats);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 5L);
    }

    private void applyImpact(Player caster, Location loc, SpellStats stats) {
        World world = loc.getWorld();
        if (world == null) return;

        boolean isWet = stats.tokens.contains(SpellToken.WET);
        double finalDamage = stats.damage;

        if (isWet && (stats.effects.contains(SpellToken.FROST) || stats.effects.contains(SpellToken.LIGHTNING))) {
            finalDamage *= 1.35; // Synergy bonus
        }

        if (stats.effects.contains(SpellToken.BOOM)) {
            world.createExplosion(loc, (float) Math.min(4.0, stats.radius * 1.2), stats.effects.contains(SpellToken.FIRE), true, caster);
        } else if (stats.tokens.contains(SpellToken.BLAST)) {
            world.createExplosion(loc, (float) Math.min(2.5, stats.radius), false, false, caster);
        }

        if (stats.effects.contains(SpellToken.LIGHTNING)) {
            world.strikeLightningEffect(loc);
        }

        if (stats.effects.contains(SpellToken.LIGHT)) {
            Block b = loc.getBlock();
            if (b.getType().isAir()) {
                b.setType(Material.LIGHT);
            }
        }

        if (isWet) {
            world.spawnParticle(Particle.WATER_SPLASH, loc, 25, stats.radius, 0.5, stats.radius);
            if (loc.getBlock().getType() == Material.FIRE) {
                loc.getBlock().setType(Material.AIR);
            }
        }

        for (Entity entity : world.getNearbyEntities(loc, stats.radius, stats.radius, stats.radius)) {
            if (!(entity instanceof LivingEntity target)) continue;

            // Heal affects allies/self too
            if (stats.effects.contains(SpellToken.HEAL)) {
                double maxHp = target.getMaxHealth();
                target.setHealth(Math.min(maxHp, target.getHealth() + finalDamage));
                world.spawnParticle(Particle.HEART, target.getLocation().add(0, 1.5, 0), 5);
                continue;
            }

            if (target.equals(caster) && !stats.structures.contains(SpellToken.BOOMERANG)) continue;

            if (finalDamage > 0 && !stats.effects.contains(SpellToken.PUSH)) {
                target.damage(finalDamage, caster);
                if (stats.effects.contains(SpellToken.VAMPIRE)) {
                    double healAmt = finalDamage * 0.3;
                    caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + healAmt));
                }
            }

            if (stats.effects.contains(SpellToken.FIRE) && !isWet) {
                target.setFireTicks(100);
            }
            if (stats.effects.contains(SpellToken.FROST)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, isWet ? 140 : 80, 2));
                target.setFreezeTicks(140);
            }
            if (stats.effects.contains(SpellToken.POISON)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            }
            if (stats.effects.contains(SpellToken.PUSH)) {
                Vector pushDir = target.getLocation().toVector().subtract(loc.toVector());
                if (pushDir.lengthSquared() == 0) pushDir = caster.getLocation().getDirection();
                target.setVelocity(pushDir.normalize().multiply(1.5 + (stats.radius * 0.3)).setY(0.5));
            }
        }
    }

    private void spawnTrailParticle(Location loc, SpellStats stats) {
        Particle p = Particle.ENCHANTMENT_TABLE;
        if (stats.effects.contains(SpellToken.FIRE)) p = Particle.FLAME;
        else if (stats.effects.contains(SpellToken.FROST)) p = Particle.SNOWFLAKE;
        else if (stats.effects.contains(SpellToken.POISON)) p = Particle.VILLAGER_HAPPY;
        else if (stats.effects.contains(SpellToken.HEAL)) p = Particle.HEART;
        else if (stats.effects.contains(SpellToken.LIGHTNING)) p = Particle.ELECTRIC_SPARK;

        loc.getWorld().spawnParticle(p, loc, 2, 0.05, 0.05, 0.05, 0.01);
    }

    private void spawnRingParticles(Location center, double radius, SpellStats stats) {
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            spawnTrailParticle(center.clone().add(x, 0, z), stats);
        }
    }
}
