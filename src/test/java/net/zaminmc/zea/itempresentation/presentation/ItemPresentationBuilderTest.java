package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.network.chat.Component;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.itempresentation.calculation.ItemStatistics;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RPG-style stat-line layout, pinned against constructed
 * statistics. The builder is a pure transform from
 * {@link ItemStatistics} to {@link Component} lines, so the whole layout
 * — what sections appear, in what order, with what formatting — is
 * testable without a loaded world.
 */
class ItemPresentationBuilderTest {
        static {
                VanillaBootstrap.ensure();
        }

        /** A sword with Sharpness shows the damage split, attack speed and sweeping. */
        @Test
        void swordShowsDamageSplitAttackSpeedAndSweeping() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                8.0, 3.0, "Sharpness V", 1.6, 0.75, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals(5, lines.size(), "blank + damage + speed + sweeping + blank");
                assertEquals("", lines.get(0).getString(), "leading blank separator");
                assertEquals("Damage: 11 (8 base + 3 Sharpness V)", lines.get(1).getString());
                assertEquals("Attack Speed: 1.6", lines.get(2).getString());
                assertEquals("Sweeping Damage Ratio: 0.75", lines.get(3).getString());
        }

        /** An unenchanted sword shows the plain damage without a split. */
        @Test
        void unenchantedSwordShowsPlainDamage() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                8.0, 0.0, "", 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Damage: 8", lines.get(1).getString());
        }

        /** A conditional damage enchantment states its condition on its own line. */
        @Test
        void conditionalDamageStatesItsCondition() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                8.0, 0.0, "", 1.6, 0.0,
                                                List.of(new ItemStatistics.ConditionalBonus("Smite II (vs undead)", 5.0)))),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Smite II (vs undead): +5", lines.get(3).getString());
        }

        /** A spear labels the damage line as the jab and adds reach, charge and Lunge. */
        @Test
        void spearShowsJabReachChargeAndLunge() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                5.0, 0.0, "", 1.05, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.SpearStats(2.0f, 4.5f, 1.075, 3, 1.374)),
                                Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SPEAR, stats);
                assertTrue(containsLineStarting(lines, "Jab Damage:"),
                                "a spear's left-click damage is the jab");
                assertEquals("Reach: 2 to 4.5 blocks", lineStarting(lines, "Reach:").getString());
                assertEquals("Charged Attack: x1.075", lineStarting(lines, "Charged Attack:").getString());
                assertEquals("Lunge: dash 1.374", lineStarting(lines, "Lunge:").getString());
        }

        /** A shovel shows mining speed but never attack damage. */
        @Test
        void shovelShowsMiningSpeedNotDamage() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(),
                                Optional.of(new ItemStatistics.MiningSpeed(2.0, 0.0, true)),
                                Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SHOVEL, stats);
                assertEquals(3, lines.size(), "blank + mining speed + blank");
                assertTrue(lines.get(1).getString().startsWith("Mining Speed:"));
                assertFalse(lines.get(1).getString().contains("correct for drops"),
                                "no Efficiency means no correct-for-drops qualifier");
        }

        /** A pickaxe with Efficiency marks the mining speed as correct-for-drops. */
        @Test
        void pickaxeWithEfficiencyMarksCorrectForDrops() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(),
                                Optional.of(new ItemStatistics.MiningSpeed(8.0, 26.0, true)),
                                Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.PICKAXE, stats);
                assertEquals("Mining Speed (correct for drops): 34", lines.get(1).getString());
        }

        /** A chestplate shows armor, toughness, knockback resistance and the EPF lines. */
        @Test
        void chestplateShowsArmorToughnessKnockbackAndProtection() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ArmorStats(8.0, 2.0, 0.05, List.of(
                                                new ItemStatistics.ConditionalBonus("Protection IV (most damage)", 4.0),
                                                new ItemStatistics.ConditionalBonus("Fire Protection IV (fire damage)", 8.0)))),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CHESTPLATE, stats);
                assertTrue(containsLineStarting(lines, "Armor:"));
                assertTrue(containsLineStarting(lines, "Armor Toughness:"));
                assertEquals("Knockback Resistance: 5%", lineStarting(lines, "Knockback Resistance:").getString());
                assertEquals("Protection IV (most damage): -16%", lineStarting(lines, "Protection IV").getString());
                assertEquals("Fire Protection IV (fire damage): -32%",
                                lineStarting(lines, "Fire Protection IV").getString());
        }

        /** A bow shows the fully-drawn projectile damage and its draw time, never melee attack speed. */
        @Test
        void bowShowsFullyDrawnDamageAndDrawTime() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(3.0, 2.0, 3.0, 20, 1, 0, false, 0)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.BOW, stats);
                assertEquals("Projectile Damage (fully drawn): 15",
                                lineStarting(lines, "Projectile Damage").getString());
                assertEquals("Draw Time: 20 ticks (1s)", lineStarting(lines, "Draw Time:").getString());
                assertFalse(containsLineStarting(lines, "Attack Speed:"),
                                "a bow must never show melee attack speed");
        }

        /** A multishot crossbow shows reload time, three projectiles and pierce depth. */
        @Test
        void crossbowShowsReloadProjectilesAndPiercing() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(3.15, 2.0, 0.0, 10, 3, 4, false, 0)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CROSSBOW, stats);
                assertEquals("Projectile Damage (arrow): 7", lineStarting(lines, "Projectile Damage").getString());
                assertEquals("Reload Time: 10 ticks (0.5s)", lineStarting(lines, "Reload Time:").getString());
                assertEquals("Projectiles: 3", lineStarting(lines, "Projectiles:").getString());
                assertEquals("Piercing: 4", lineStarting(lines, "Piercing:").getString());
        }

        /** A firework-loaded crossbow presents the rocket, not an arrow damage number. */
        @Test
        void fireworkCrossbowShowsTheRocket() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(1.6, 2.0, 0.0, 25, 1, 0, true, 3)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CROSSBOW, stats);
                assertEquals("Projectile: Firework Rocket", lineStarting(lines, "Projectile:").getString());
                assertEquals("Firework Damage: 11 (3 stars)", lineStarting(lines, "Firework Damage:").getString());
        }

        /** A mace shows the smash threshold, the fall-bonus bands and the Density rate. */
        @Test
        void maceShowsSmashCurveDensityAndBreach() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(6.0, 0.0, "", 0.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(),
                                Optional.of(new ItemStatistics.SmashDamage(
                                                1.5,
                                                List.of(new ItemStatistics.SmashDamage.SmashBand(4.0, 3.0),
                                                                new ItemStatistics.SmashDamage.SmashBand(2.0, 8.0),
                                                                new ItemStatistics.SmashDamage.SmashBand(1.0,
                                                                                Double.POSITIVE_INFINITY)),
                                                2.5, -0.6, true)),
                                Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.MACE, stats);
                assertEquals("Smash Attack: +6 past 1.5 blocks fallen "
                                + "(+4/block to 3, +2/block to 8, +1/block after)",
                                lineStarting(lines, "Smash Attack:").getString());
                assertEquals("Density Bonus: +2.5/block fallen", lineStarting(lines, "Density Bonus:").getString());
                assertEquals("Breach: -60% armor effectiveness", lineStarting(lines, "Breach:").getString());
                assertTrue(containsLineStarting(lines, "Wind Burst:"));
        }

        /** A food item shows nutrition, saturation and formatted effects, never weapon statistics. */
        @Test
        void foodShowsNutritionSaturationAndEffects() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.FoodStats(6, 1.2f, true,
                                                List.of("Regeneration II (0:05)", "Absorption I (2:00) — 100%"))));
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.FOOD, stats);
                assertTrue(containsLineStarting(lines, "Nutrition:"));
                assertTrue(containsLineStarting(lines, "Saturation:"));
                assertTrue(containsLineStarting(lines, "Can Always Eat:"));
                assertEquals("Effect: Regeneration II (0:05)", lineStarting(lines, "Effect: Regeneration").getString());
                assertFalse(containsLineStarting(lines, "Damage:"),
                                "food must not show weapon-style damage statistics");
        }

        /** Durability with Unbreaking shows the effective maximum alongside the raw. */
        @Test
        void durabilityWithUnbreakingShowsEffectiveMaximum() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.Durability(1561, 1561, 4.0)),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.PICKAXE, stats);
                Component durabilityLine = lineStarting(lines, "Durability:");
                assertTrue(durabilityLine.getString().contains("effective"),
                                "Unbreaking must surface the effective maximum");
                assertEquals("Durability: 1561 / 1561  (effective ~6244)", durabilityLine.getString());
        }

        /** An item with no populated statistics shows nothing. */
        @Test
        void emptyStatisticsShowsNothing() {
                List<Component> lines = ItemPresentationBuilder.build(
                                ItemCategory.OTHER, ItemStatistics.empty());
                assertTrue(lines.isEmpty(), "no statistics means no stat lines");
        }

        private static boolean containsLineStarting(List<Component> lines, String prefix) {
                return lineStarting(lines, prefix) != null;
        }

        private static Component lineStarting(List<Component> lines, String prefix) {
                for (Component line : lines) {
                        if (line.getString().startsWith(prefix)) {
                                return line;
                        }
                }
                return null;
        }
}
