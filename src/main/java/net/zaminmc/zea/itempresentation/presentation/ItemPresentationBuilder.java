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
 * smash/projectile/food sections for the categories that carry them.
 * Only sections with a populated statistic appear — a diamond shovel
 * never shows attack damage, a bow never shows melee attack speed.
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
                statistics.miningSpeed().ifPresent(mining -> lines.add(miningLine(mining)));
                statistics.armor().ifPresent(armor -> lines.addAll(armorLines(armor)));
                statistics.projectile().ifPresent(projectile -> lines.addAll(projectileLines(projectile)));
                statistics.smashDamage().ifPresent(smash -> lines.addAll(smashLines(smash)));
                statistics.food().ifPresent(food -> lines.addAll(foodLines(food)));
                statistics.durability().ifPresent(durability -> lines.add(durabilityLine(durability)));
                if (!lines.isEmpty()) {
                        lines.add(0, Component.empty());
                        lines.add(Component.empty());
                }
                return lines;
        }

        /** The melee damage section: damage, attack speed, sweeping, conditional bonuses. */
        private static List<Component> meleeLines(ItemCategory category, ItemStatistics.MeleeDamage melee) {
                List<Component> lines = new ArrayList<>();
                lines.add(statLine("Damage", format(melee.total())));
                lines.add(statLine("Attack Speed", format(melee.attackSpeed())));
                if (melee.sweepingRatio() > 0.0) {
                        lines.add(statLine("Sweeping Damage Ratio", format(melee.sweepingRatio())));
                }
                for (ItemStatistics.ConditionalBonus bonus : melee.conditional()) {
                        lines.add(statLine(bonus.label(), format(bonus.value())));
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

        /** The armor section: armor, toughness, knockback resistance, protection enchantments. */
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
                        lines.add(statLine(protection.label(), format(protection.value())));
                }
                return lines;
        }

        /**
         * The projectile section: fully-charged damage with draw ticks for
         * bows, projectile damage and pierce for crossbows.
         */
        private static List<Component> projectileLines(ItemStatistics.ProjectileStats projectile) {
                List<Component> lines = new ArrayList<>();
                lines.add(statLine("Projectile Damage", format(projectile.fullyCharged())));
                if (projectile.drawTicks() > 0) {
                        lines.add(statLine("Draw Time", projectile.drawTicks() + " ticks"));
                }
                if (projectile.multishot()) {
                        lines.add(statLine("Multishot", "yes"));
                }
                if (projectile.piercing() > 0) {
                        lines.add(statLine("Piercing", String.valueOf(projectile.piercing())));
                }
                return lines;
        }

        /**
         * The smash section: the per-fallen-block Density rate and the
         * Breach/Wind Burst flags. Stated as conditional because smash
         * damage depends on fall distance.
         */
        private static List<Component> smashLines(ItemStatistics.SmashDamage smash) {
                List<Component> lines = new ArrayList<>();
                if (smash.perFallenBlock() > 0.0) {
                        lines.add(statLine("Smash Damage per fallen block", format(smash.perFallenBlock())));
                }
                if (smash.breach()) {
                        lines.add(statLine("Breach", "active"));
                }
                if (smash.windBurst()) {
                        lines.add(statLine("Wind Burst", "active"));
                }
                if (lines.isEmpty()) {
                        lines.add(statLine("Smash Attack", "active (no fall bonus)"));
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
                for (ItemStatistics.ConditionalBonus effect : food.effects()) {
                        lines.add(statLine("Effect", effect.label()));
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

        /** Formats a double as a one-decimal value, dropping the ".0" on whole numbers. */
        private static String format(double value) {
                if (value == Math.rint(value)) {
                        return String.valueOf((long) value);
                }
                return String.format("%.1f", value);
        }

        /** Formats a 0..1 fraction as a percentage, the way knockback resistance is shown. */
        private static String formatPercent(double value) {
                return String.format("%.0f%%", value * 100.0);
        }
}
