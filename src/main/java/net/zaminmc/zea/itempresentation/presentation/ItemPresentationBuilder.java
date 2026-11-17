package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.zaminmc.zea.itempresentation.calculation.ItemStatistics;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the RPG-style stat lines for one item — the compact block
 * that sits between the item name and the enchantment section in the
 * rebuilt tooltip.
 *
 * <p>The presentation is deliberately terse: the final readable value,
 * never the arithmetic behind it. A damage line reads "Damage: +12
 * [5]" — total first, the contributing enchantment's level in the
 * bracket — and never "10 (7 base + 3 Sharpness V)". Positive
 * additive bonuses carry a leading {@code +}; ratios that are
 * percentages read as percentages ("Sweep Damage: +75%", never
 * "Sweeping Damage Ratio: 0.75"); durations read in seconds. No line
 * shows an internal attribute identifier or a raw ratio.
 *
 * <p>Colors follow the spec palette: grey labels, red damage values,
 * green defensive and utility bonuses, white for neutral cadence
 * numbers. Only sections with a populated statistic appear — a shovel
 * never shows attack damage, a bow never shows melee attack speed,
 * and a number that holds only under a condition states the condition
 * in its own line rather than masquerading as universal.
 *
 * <p>The builder is a pure transform: it takes the already-computed
 * {@link ItemStatistics} and the category, and emits {@link Component}
 * lines. No calculation, no registry access, no rendering — those are
 * the engine's and the renderer's jobs. This keeps the layout
 * unit-testable against constructed statistics, exactly the way the
 * rarity calculator is.
 */
public final class ItemPresentationBuilder {
        /** The grey label color for stat-line keys ("Damage:", "Defense:"). */
        private static final int LABEL_GRAY = 0xAAAAAA;
        /** The red value color for damage numbers (§c). */
        private static final int DAMAGE_RED = 0xFF5555;
        /** The green value color for positive defensive and utility bonuses (§a). */
        private static final int BONUS_GREEN = 0x55FF55;
        /** The white value color for neutral cadence and capacity numbers. */
        private static final int VALUE_WHITE = 0xFFFFFF;

        private ItemPresentationBuilder() {
        }

        /**
         * The RPG-style stat lines for {@code statistics} under
         * {@code category}. Returns an empty list when the item has no
         * populated statistics to show.
         */
        public static List<Component> build(ItemCategory category, ItemStatistics statistics) {
                List<Component> lines = new ArrayList<>();
                statistics.meleeDamage().ifPresent(melee -> lines.addAll(meleeLines(category, melee)));
                statistics.spear().ifPresent(spear -> lines.addAll(spearLines(spear)));
                statistics.miningSpeed().ifPresent(mining -> lines.addAll(miningLines(mining)));
                statistics.armor().ifPresent(armor -> lines.addAll(armorLines(armor)));
                statistics.projectile().ifPresent(projectile -> lines.addAll(projectileLines(category, projectile)));
                statistics.smashDamage().ifPresent(smash -> lines.addAll(smashLines(smash)));
                statistics.durability().ifPresent(durability -> lines.add(durabilityLine(durability)));
                if (!lines.isEmpty()) {
                        lines.add(0, Component.empty());
                        lines.add(Component.empty());
                }
                return lines;
        }

        /**
         * The melee damage section: damage (with the contributing
         * enchantment's level in the bracket when one contributes),
         * attack speed, sweeping, conditional bonuses. A spear's
         * left-click is the jab, so its damage line says so; a
         * trident's is the melee thrust.
         */
        private static List<Component> meleeLines(ItemCategory category, ItemStatistics.MeleeDamage melee) {
                List<Component> lines = new ArrayList<>();
                String damageKey = category == ItemCategory.SPEAR ? "Jab Damage"
                                : category == ItemCategory.TRIDENT ? "Melee Damage"
                                : "Damage";
                if (melee.enchantmentBonus() > 0.0 && melee.bonusLevel() > 0) {
                        lines.add(bonusStatLine(damageKey, melee.total(), melee.bonusLevel(), DAMAGE_RED));
                } else {
                        lines.add(statLine(damageKey, "+" + format(melee.baseDamage()), DAMAGE_RED));
                }
                lines.add(statLine("Attack Speed", format(melee.attackSpeed()), VALUE_WHITE));
                if (melee.sweepingRatio() > 0.0) {
                        lines.add(bonusStatLine("Sweep Damage",
                                        melee.sweepingRatio() * 100.0, melee.sweepingLevel(), DAMAGE_RED, "%"));
                }
                for (ItemStatistics.ConditionalBonus bonus : melee.conditional()) {
                        lines.add(statLine(bonus.label(), "+" + format(bonus.value()), DAMAGE_RED));
                }
                return lines;
        }

        /**
         * The spear section: the reach window from the item's own
         * attack-range component, the charged attack's damage
         * multiplier, and the Lunge dash. The jab's damage and speed
         * live in the melee section.
         */
        private static List<Component> spearLines(ItemStatistics.SpearStats spear) {
                List<Component> lines = new ArrayList<>();
                if (spear.maxReach() > 0.0f) {
                        lines.add(statLine("Reach",
                                        format(spear.minReach()) + " to " + format(spear.maxReach()) + " blocks",
                                        VALUE_WHITE));
                }
                if (spear.chargeAttackMultiplier() > 0.0) {
                        lines.add(statLine("Charged Attack", "x" + format(spear.chargeAttackMultiplier()), VALUE_WHITE));
                }
                if (spear.lungeLevel() > 0) {
                        lines.add(statLine("Lunge", "dash " + format(spear.lungeImpulse()), BONUS_GREEN));
                }
                return lines;
        }

        /**
         * The mining section: the tool's own speed, and Efficiency's
         * live contribution as its own line with the enchantment's
         * level in the bracket. Both numbers describe blocks the tool
         * is correct for — the representative case — and Efficiency
         * still reads as the enchantment it is in the enchantment
         * section.
         */
        private static List<Component> miningLines(ItemStatistics.MiningSpeed mining) {
                List<Component> lines = new ArrayList<>();
                lines.add(statLine("Mining Speed", format(mining.baseSpeed()), VALUE_WHITE));
                if (mining.efficiencyBonus() > 0.0) {
                        lines.add(bonusStatLine("Mining Efficiency",
                                        mining.efficiencyBonus(), mining.efficiencyLevel(), BONUS_GREEN));
                }
                return lines;
        }

        /**
         * The armor section: defense, toughness, knockback resistance,
         * and the protection enchantments as conditional reductions —
         * each states the damage type it applies to and the reduction
         * this piece alone grants against it.
         */
        private static List<Component> armorLines(ItemStatistics.ArmorStats armor) {
                List<Component> lines = new ArrayList<>();
                lines.add(statLine("Defense", "+" + format(armor.armor()), BONUS_GREEN));
                if (armor.toughness() > 0.0) {
                        lines.add(statLine("Armor Toughness", "+" + format(armor.toughness()), BONUS_GREEN));
                }
                if (armor.knockbackResistance() > 0.0) {
                        lines.add(statLine("Knockback Resistance",
                                        "+" + formatPercent(armor.knockbackResistance()), BONUS_GREEN));
                }
                for (ItemStatistics.ConditionalBonus protection : armor.protectionEnchantments()) {
                        lines.add(statLine(protection.label(),
                                        "-" + formatPercent(Math.min(20.0, protection.value()) / 25.0),
                                        BONUS_GREEN));
                }
                return lines;
        }

        /**
         * The projectile section: fully-drawn damage for bows (with
         * Power's level in the bracket), thrown damage for tridents,
         * the loaded projectile's damage for crossbows, launch
         * velocity, draw/reload/throw time, projectile count under
         * Multishot, and pierce depth. A bow's qualifiers are dropped —
         * on a bow there is nothing else the damage or the velocity
         * could belong to, so the lines read just "Damage" and
         * "Velocity"; tridents and crossbows keep their qualifiers
         * because they distinguish the thrown/loaded numbers from the
         * melee ones. The draw time is green: it is the one bow number
         * a faster-is-better eye tracks, and the utility-green marks
         * it apart from the white cadence numbers.
         */
        private static List<Component> projectileLines(ItemCategory category, ItemStatistics.ProjectileStats projectile) {
                List<Component> lines = new ArrayList<>();
                boolean trident = category == ItemCategory.TRIDENT;
                boolean bow = category == ItemCategory.BOW;
                String damageLabel = trident ? "Thrown Damage" : bow ? "Damage" : "Projectile Damage";
                double damage = projectile.velocityScaled()
                                ? projectile.fullyCharged() : projectile.flat();
                if (projectile.firework()) {
                        lines.add(statLine("Projectile", "Firework Rocket", VALUE_WHITE));
                        lines.add(statLine("Firework Damage",
                                        "+" + format(projectile.fireworkDamage())
                                                        + " (" + projectile.fireworkStars() + " stars)",
                                        DAMAGE_RED));
                } else if (projectile.powerBonus() > 0.0 && projectile.powerLevel() > 0) {
                        lines.add(bonusStatLine(damageLabel, damage, projectile.powerLevel(), DAMAGE_RED));
                } else {
                        lines.add(statLine(damageLabel, "+" + format(damage), DAMAGE_RED));
                }
                lines.add(statLine(trident ? "Throw Velocity" : bow ? "Velocity" : "Projectile Velocity",
                                format(projectile.speed() * 20.0) + " blocks/s", VALUE_WHITE));
                if (trident) {
                        lines.add(statLine("Throw Time", seconds(projectile.chargeTicks()), VALUE_WHITE));
                } else if (bow) {
                        lines.add(statLine("Draw Time", seconds(projectile.chargeTicks()), BONUS_GREEN));
                } else {
                        lines.add(statLine("Reload Time", seconds(projectile.chargeTicks()), VALUE_WHITE));
                }
                if (projectile.projectileCount() > 1) {
                        lines.add(statLine("Projectiles", String.valueOf(projectile.projectileCount()), VALUE_WHITE));
                }
                if (projectile.piercing() > 0) {
                        lines.add(statLine("Piercing", String.valueOf(projectile.piercing()), VALUE_WHITE));
                }
                return lines;
        }

        /**
         * The smash section: the fall-gated bonus at the minimum smash
         * fall, Density's per-block rate, Breach's armor-effectiveness
         * reduction, and the Wind Burst flag. Every line states its own
         * condition — fall-dependent damage is never presented as
         * guaranteed on a grounded attack. The normal-hit damage is the
         * melee section's business.
         */
        private static List<Component> smashLines(ItemStatistics.SmashDamage smash) {
                List<Component> lines = new ArrayList<>();
                lines.add(statLine("Smash Damage",
                                "+" + format(smash.fallBonus(smash.fallThreshold()))
                                                + " past " + format(smash.fallThreshold()) + " blocks fallen",
                                DAMAGE_RED));
                if (smash.densityPerFallenBlock() > 0.0) {
                        lines.add(statLine("Density",
                                        "+" + format(smash.densityPerFallenBlock()) + "/block fallen",
                                        DAMAGE_RED));
                }
                if (smash.breach() != 0.0) {
                        lines.add(statLine("Breach",
                                        "-" + formatPercent(-smash.breach()) + " armor effectiveness",
                                        DAMAGE_RED));
                }
                if (smash.windBurst()) {
                        lines.add(statLine("Wind Burst", "launches on smash hit", VALUE_WHITE));
                }
                return lines;
        }

        /** The durability line: current / maximum, and nothing else. */
        private static Component durabilityLine(ItemStatistics.Durability durability) {
                return statLine("Durability", durability.current() + " / " + durability.maximum(), VALUE_WHITE);
        }

        /** A tick count as seconds, trailing zeros stripped. */
        private static String seconds(int ticks) {
                return format(ticks / 20.0) + "s";
        }

        /**
         * One "Label: value" line — the label in grey, the value in the
         * given color, the same visual rhythm the spec example uses.
         */
        private static Component statLine(String label, String value, int valueColor) {
                MutableComponent line = Component.literal(label + ": ")
                                .withColor(LABEL_GRAY);
                line.append(Component.literal(value).withColor(valueColor));
                return line;
        }

        /**
         * One "Label: +value [level]" line — the compact form for a
         * value an enchantment contributed to, the bracket carrying the
         * contributing enchantment's level (Sharpness V → {@code [5]}).
         * The percent variant appends a {@code %} to the number.
         */
        private static Component bonusStatLine(String label, double value, int level, int valueColor) {
                return bonusStatLine(label, value, level, valueColor, "");
        }

        private static Component bonusStatLine(String label, double value, int level, int valueColor, String suffix) {
                MutableComponent line = Component.literal(label + ": ")
                                .withColor(LABEL_GRAY);
                line.append(Component.literal("+" + format(value) + suffix).withColor(valueColor));
                if (level > 0) {
                        line.append(Component.literal(" [" + level + "]").withColor(LABEL_GRAY));
                }
                return line;
        }

        /**
         * Formats a double with up to three decimals, trailing zeros
         * stripped — one decimal covers damage and speed, but the
         * stats with fractional data rates (the spear's 1.075 charge
         * multiplier) must not be rounded away.
         */
        private static String format(double value) {
                if (value == Math.rint(value)) {
                        return String.valueOf((long) value);
                }
                String formatted = String.format("%.3f", value);
                while (formatted.endsWith("0")) {
                        formatted = formatted.substring(0, formatted.length() - 1);
                }
                if (formatted.endsWith(".")) {
                        formatted = formatted.substring(0, formatted.length() - 1);
                }
                return formatted;
        }

        /** Formats a 0..1 fraction as a percentage, the way knockback resistance is shown. */
        private static String formatPercent(double value) {
                return String.format("%.0f%%", value * 100.0);
        }
}
