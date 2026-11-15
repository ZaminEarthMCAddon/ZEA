package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.zaminmc.zea.itempresentation.calculation.ItemStatistics;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the RPG-style stat lines for one item — the structured block
 * that sits between the item name and the enchantment list in the
 * rebuilt tooltip.
 *
 * <p>The layout follows the spec's structural example: a damage section
 * for weapons, a mining section for tools, an armor section for worn
 * pieces, a durability line whenever the item is damageable, and the
 * smash/projectile/spear/food sections for the categories that carry
 * them. Only sections with a populated statistic appear — a diamond
 * shovel never shows attack damage, a bow never shows melee attack
 * speed. Numbers that hold only under a condition state the condition
 * in their own line, never as a bare universal claim.
 *
 * <p>The builder is a pure transform: it takes the already-computed
 * {@link ItemStatistics} and the category, and emits {@link Component}
 * lines. No calculation, no registry access, no rendering — those are
 * the engine's and the renderer's jobs. This keeps the layout
 * unit-testable against constructed statistics, exactly the way the
 * rarity calculator is.
 */
public final class ItemPresentationBuilder {
        /** The grey label color for stat-line keys ("Damage:", "Armor:"). */
        private static final int LABEL_GRAY = 0xAAAAAA;
        /** The white value color for stat-line numbers. */
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
                statistics.miningSpeed().ifPresent(mining -> lines.add(miningLine(mining)));
                statistics.armor().ifPresent(armor -> lines.addAll(armorLines(armor)));
                statistics.projectile().ifPresent(projectile -> lines.addAll(projectileLines(category, projectile)));
                statistics.smashDamage().ifPresent(smash -> lines.addAll(smashLines(smash)));
                statistics.food().ifPresent(food -> lines.addAll(foodLines(food)));
                statistics.durability().ifPresent(durability -> lines.add(durabilityLine(durability)));
                if (!lines.isEmpty()) {
                        lines.add(0, Component.empty());
                        lines.add(Component.empty());
                }
                return lines;
        }

        /**
         * The melee damage section: damage (with the base/enchantment
         * split stated when an enchantment contributes), attack speed,
         * sweeping, conditional bonuses. A spear's left-click is the
         * jab, so its damage line says so.
         */
        private static List<Component> meleeLines(ItemCategory category, ItemStatistics.MeleeDamage melee) {
                List<Component> lines = new ArrayList<>();
                String damageKey = category == ItemCategory.SPEAR ? "Jab Damage" : "Damage";
                if (melee.enchantmentBonus() > 0.0) {
                        String split = format(melee.baseDamage()) + " base + "
                                        + format(melee.enchantmentBonus())
                                        + (melee.bonusLabel().isEmpty() ? "" : " " + melee.bonusLabel());
                        lines.add(statLine(damageKey, format(melee.total()) + " (" + split + ")"));
                } else {
                        lines.add(statLine(damageKey, format(melee.baseDamage())));
                }
                lines.add(statLine("Attack Speed", format(melee.attackSpeed())));
                if (melee.sweepingRatio() > 0.0) {
                        lines.add(statLine("Sweeping Damage Ratio", format(melee.sweepingRatio())));
                }
                for (ItemStatistics.ConditionalBonus bonus : melee.conditional()) {
                        lines.add(statLine(bonus.label(), "+" + format(bonus.value())));
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
                                        format(spear.minReach()) + " to " + format(spear.maxReach()) + " blocks"));
                }
                if (spear.chargeAttackMultiplier() > 0.0) {
                        lines.add(statLine("Charged Attack", "x" + format(spear.chargeAttackMultiplier())));
                }
                if (spear.lungeLevel() > 0) {
                        lines.add(statLine("Lunge", "dash " + format(spear.lungeImpulse())));
                }
                return lines;
        }

        /** The mining-speed section, marked correct-for-drops when Efficiency is present. */
        private static Component miningLine(ItemStatistics.MiningSpeed mining) {
                String label = mining.appliesOnlyWhenCorrect() && mining.efficiencyBonus() > 0.0
                                ? "Mining Speed (correct for drops)"
                                : "Mining Speed";
                return statLine(label, format(mining.correctForDrops()));
        }

        /**
         * The armor section: armor, toughness, knockback resistance, and
         * the protection enchantments as conditional reductions — each
         * states the damage type it applies to and the reduction this
         * piece alone grants against it.
         */
        private static List<Component> armorLines(ItemStatistics.ArmorStats armor) {
                List<Component> lines = new ArrayList<>();
                lines.add(statLine("Armor", format(armor.armor())));
                if (armor.toughness() > 0.0) {
                        lines.add(statLine("Armor Toughness", format(armor.toughness())));
                }
                if (armor.knockbackResistance() > 0.0) {
                        lines.add(statLine("Knockback Resistance", formatPercent(armor.knockbackResistance())));
                }
                for (ItemStatistics.ConditionalBonus protection : armor.protectionEnchantments()) {
                        lines.add(statLine(protection.label(),
                                        "-" + formatPercent(Math.min(20.0, protection.value()) / 25.0)));
                }
                return lines;
        }

        /**
         * The projectile section: fully-drawn damage for bows (with the
         * draw condition stated), the loaded projectile's damage for
         * crossbows, draw/reload time, projectile count under
         * Multishot, and pierce depth.
         */
        private static List<Component> projectileLines(ItemCategory category, ItemStatistics.ProjectileStats projectile) {
                List<Component> lines = new ArrayList<>();
                if (projectile.firework()) {
                        lines.add(statLine("Projectile", "Firework Rocket"));
                        lines.add(statLine("Firework Damage",
                                        format(projectile.fireworkDamage())
                                        + " (" + projectile.fireworkStars() + " stars)"));
                } else if (category == ItemCategory.BOW) {
                        lines.add(statLine("Projectile Damage (fully drawn)", format(projectile.fullyCharged())));
                } else {
                        lines.add(statLine("Projectile Damage (arrow)", format(projectile.fullyCharged())));
                }
                if (category == ItemCategory.BOW) {
                        lines.add(statLine("Draw Time", ticksLine(projectile.chargeTicks())));
                } else {
                        lines.add(statLine("Reload Time", ticksLine(projectile.chargeTicks())));
                }
                if (projectile.projectileCount() > 1) {
                        lines.add(statLine("Projectiles", String.valueOf(projectile.projectileCount())));
                }
                if (projectile.piercing() > 0) {
                        lines.add(statLine("Piercing", String.valueOf(projectile.piercing())));
                }
                return lines;
        }

        /**
         * The smash section: the fall threshold, the piecewise fall
         * bonus in words, Density's per-block rate, Breach's armor
         * reduction, and the Wind Burst flag. The mace's normal-hit
         * damage is the melee section's business.
         */
        private static List<Component> smashLines(ItemStatistics.SmashDamage smash) {
                List<Component> lines = new ArrayList<>();
                StringBuilder bands = new StringBuilder();
                for (int i = 0; i < smash.bands().size(); i++) {
                        ItemStatistics.SmashDamage.SmashBand band = smash.bands().get(i);
                        if (i > 0) {
                                bands.append(", ");
                        }
                        bands.append("+").append(format(band.perBlock())).append("/block ");
                        if (Double.isInfinite(band.upTo())) {
                                bands.append("after");
                        } else if (i == smash.bands().size() - 1) {
                                bands.append("to ").append(format(band.upTo())).append(", then onward");
                        } else {
                                bands.append("to ").append(format(band.upTo()));
                        }
                }
                lines.add(statLine("Smash Attack",
                                "+" + format(smash.fallBonus(smash.fallThreshold()))
                                + " past " + format(smash.fallThreshold()) + " blocks fallen (" + bands + ")"));
                if (smash.densityPerFallenBlock() > 0.0) {
                        lines.add(statLine("Density Bonus",
                                        "+" + format(smash.densityPerFallenBlock()) + "/block fallen"));
                }
                if (smash.breach() != 0.0) {
                        lines.add(statLine("Breach",
                                        "-" + formatPercent(-smash.breach()) + " armor effectiveness"));
                }
                if (smash.windBurst()) {
                        lines.add(statLine("Wind Burst", "launches on smash hit"));
                }
                return lines;
        }

        /** The food section: nutrition, saturation, and any consume effects. */
        private static List<Component> foodLines(ItemStatistics.FoodStats food) {
                List<Component> lines = new ArrayList<>();
                lines.add(statLine("Nutrition", String.valueOf(food.nutrition())));
                lines.add(statLine("Saturation", format(food.saturation())));
                if (food.canAlwaysEat()) {
                        lines.add(statLine("Can Always Eat", "yes"));
                }
                for (String effect : food.effects()) {
                        lines.add(statLine("Effect", effect));
                }
                return lines;
        }

        /** The durability line: current / maximum, with effective maximum when Unbreaking applies. */
        private static Component durabilityLine(ItemStatistics.Durability durability) {
                String value = durability.current() + " / " + durability.maximum();
                if (durability.unbreakingFactor() > 1.0) {
                        value += "  (effective ~" + durability.effectiveMaximum() + ")";
                }
                return statLine("Durability", value);
        }

        /** A tick count as both ticks and seconds, the way players read draw times. */
        private static String ticksLine(int ticks) {
                return ticks + " ticks (" + format(ticks / 20.0) + "s)";
        }

        /**
         * One "Label: value" line — the label in grey, the value in white,
         * the same visual rhythm the spec example uses.
         */
        private static Component statLine(String label, String value) {
                MutableComponent line = Component.literal(label + ": ")
                                .withColor(LABEL_GRAY);
                line.append(Component.literal(value).withColor(VALUE_WHITE));
                return line;
        }

        /**
         * Formats a double with up to three decimals, trailing zeros
         * stripped — one decimal covers damage and speed, but the
         * stats with fractional data rates (Sweeping Edge's 0.75, the
         * spear's 1.075 charge multiplier) must not be rounded away.
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
