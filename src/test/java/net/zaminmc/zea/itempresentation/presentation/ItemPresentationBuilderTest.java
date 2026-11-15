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

        /** A sword with melee damage shows damage, attack speed and sweeping. */
        @Test
        void swordShowsDamageAttackSpeedAndSweeping() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(7.0, 3.0, 1.6, 0.75, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals(5, lines.size(), "blank + damage + speed + sweeping + blank");
                assertEquals("", lines.get(0).getString(), "leading blank separator");
                assertTrue(lines.get(1).getString().startsWith("Damage:"));
                assertTrue(lines.get(2).getString().startsWith("Attack Speed:"));
                assertTrue(lines.get(3).getString().startsWith("Sweeping Damage Ratio:"));
        }

        /** A shovel shows mining speed but never attack damage. */
        @Test
        void shovelShowsMiningSpeedNotDamage() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(),
                                Optional.of(new ItemStatistics.MiningSpeed(2.0, 0.0, true)),
                                Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty());
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
                                Optional.of(new ItemStatistics.MiningSpeed(8.0, 5.0, true)),
                                Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.PICKAXE, stats);
                assertTrue(lines.get(1).getString().startsWith("Mining Speed (correct for drops):"));
        }

        /** A chestplate shows armor, toughness and knockback resistance. */
        @Test
        void chestplateShowsArmorToughnessKnockback() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ArmorStats(8.0, 2.0, 0.05, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CHESTPLATE, stats);
                assertTrue(containsLineStarting(lines, "Armor:"));
                assertTrue(containsLineStarting(lines, "Armor Toughness:"));
                assertTrue(containsLineStarting(lines, "Knockback Resistance:"));
        }

        /** A bow shows projectile damage and draw time, never melee attack speed. */
        @Test
        void bowShowsProjectileDamageAndDrawTime() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(6.0, 3.0, false, 0, 20)),
                                Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.BOW, stats);
                assertTrue(containsLineStarting(lines, "Projectile Damage:"));
                assertTrue(containsLineStarting(lines, "Draw Time:"));
                assertFalse(containsLineStarting(lines, "Attack Speed:"),
                                "a bow must never show melee attack speed");
        }

        /** A mace shows the smash section with the per-fallen-block Density rate. */
        @Test
        void maceShowsSmashDamagePerFallenBlock() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(6.0, 0.0, 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(),
                                Optional.of(new ItemStatistics.SmashDamage(0.0, 1.5, true, false)),
                                Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.MACE, stats);
                assertTrue(containsLineStarting(lines, "Smash Damage per fallen block:"));
                assertTrue(containsLineStarting(lines, "Breach:"));
                assertFalse(containsLineStarting(lines, "Wind Burst:"),
                                "no Wind Burst enchantment means no Wind Burst line");
        }

        /** A food item shows nutrition and saturation, never weapon statistics. */
        @Test
        void foodShowsNutritionAndSaturation() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.FoodStats(6, 1.2f, false, List.of())));
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.FOOD, stats);
                assertTrue(containsLineStarting(lines, "Nutrition:"));
                assertTrue(containsLineStarting(lines, "Saturation:"));
                assertFalse(containsLineStarting(lines, "Damage:"),
                                "food must not show weapon-style damage statistics");
        }

        /** Durability with Unbreaking shows the effective maximum alongside the raw. */
        @Test
        void durabilityWithUnbreakingShowsEffectiveMaximum() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.Durability(1561, 1561, 4.0 / 3.0)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.PICKAXE, stats);
                assertTrue(containsLineStarting(lines, "Durability:"));
                Component durabilityLine = lineStarting(lines, "Durability:");
                assertTrue(durabilityLine.getString().contains("effective"),
                                "Unbreaking must surface the effective maximum");
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
