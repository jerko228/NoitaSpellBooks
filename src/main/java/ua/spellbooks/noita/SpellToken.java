package ua.spellbooks.noita;

import java.util.Arrays;
import java.util.Optional;

public enum SpellToken {
    // 1. STRUCTURE (10)
    BOLT(Category.STRUCTURE, "Single straight projectile"),
    ARROW(Category.STRUCTURE, "Ballistic arrow projectile"),
    TRIPLE(Category.STRUCTURE, "Splits shot into 3 projectiles"),
    BURST(Category.STRUCTURE, "Fires 3 projectiles sequentially"),
    ORB(Category.STRUCTURE, "Slow hovering sphere pulsing damage"),
    BOOMERANG(Category.STRUCTURE, "Projectile returns to caster"),
    TOUCH(Category.STRUCTURE, "Instant melee-range cast"),
    AURA(Category.STRUCTURE, "Area cast centered on caster"),
    BOUNCE(Category.STRUCTURE, "Projectiles bounce off blocks"),
    MINE(Category.STRUCTURE, "Places a proximity trap"),

    // 2. EFFECT (10)
    FIRE(Category.EFFECT, "Ignites targets and terrain"),
    FROST(Category.EFFECT, "Slows and freezes targets"),
    LIGHTNING(Category.EFFECT, "Strikes lightning at impact"),
    BOOM(Category.EFFECT, "Creates terrain-damaging explosion"),
    POISON(Category.EFFECT, "Applies lingering poison"),
    HEAL(Category.EFFECT, "Restores health to target/self"),
    DIG(Category.EFFECT, "Breaks blocks along path"),
    LIGHT(Category.EFFECT, "Places light source at impact"),
    VAMPIRE(Category.EFFECT, "Lifesteals portion of damage"),
    PUSH(Category.EFFECT, "Strong knockback impulse"),

    // 3. MODIFIER (10)
    FAST(Category.MODIFIER, "+Speed, -5% Radius"),
    SLOW(Category.MODIFIER, "-Speed, +Range"),
    POWER(Category.MODIFIER, "+3.0 Damage, +Cooldown"),
    GIANT(Category.MODIFIER, "+Radius, -10% Speed"),
    TINY(Category.MODIFIER, "-Radius, -Cooldown"),
    FAR(Category.MODIFIER, "+Range"),
    AIM(Category.MODIFIER, "Removes spread"),
    BLAST(Category.MODIFIER, "Adds non-destructive shockwave"),
    CHEAP(Category.MODIFIER, "-15% XP creation cost"),
    WET(Category.MODIFIER, "Extinguishes fire, boosts Frost/Lightning");

    public enum Category {
        STRUCTURE, EFFECT, MODIFIER
    }

    private final Category category;
    private final String description;

    SpellToken(Category category, String description) {
        this.category = category;
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public static Optional<SpellToken> fromString(String raw) {
        String clean = raw.replaceAll("[^a-zA-Z]", "").toUpperCase();
        return Arrays.stream(values())
                .filter(t -> t.name().equals(clean))
                .findFirst();
    }
}
