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
 * The compact RPG stat-line layout, pinned against constructed
 * statistics. The builder is a pure transform from
 * {@link ItemStatistics} to {@link Component} lines, so the whole
 * layout — what sections appear, in what order, with what compact
 * formatting — is testable without a loaded world. The formatting
 * rules under test are the spec's: a final value first, then — for
 * damage — the color-coded contributor annotations, a leading + on
 * positive additive bonuses, the contributing enchantment's level in
 * the blue bracket, percentages for ratios, seconds for durations,
 * and the maximum alone for a fully repaired item.
 */
class ItemPresentationBuilderTest {
        static {
                VanillaBootstrap.ensure();
        }

        /**
         * A held sword with Sharpness shows the annotated damage line —
         * total, the item's contribution in red, the enchantment's
         * level in the blue bracket, the wielder's base in dark — and
         * the effective attack speed in yellow. The block sits directly
         * under the name: no leading blank separator.
         */
        @Test
        void heldSwordShowsAnnotatedDamageAndYellowSpeed() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 3.0, "Sharpness V", 5, 1.0, 0.0, "", 1.6, 0.75, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals(4, lines.size(), "damage + speed + sweep + trailing blank");
                assertFalse(lines.get(0).getString().isEmpty(),
                                "the first line is the damage line, not a separator");
                assertEquals("Damage: +10 (6) [5] (1)", lines.get(0).getString());
                assertEquals("Attack Speed: +1.6", lines.get(1).getString());
                assertEquals(0xFFFF55, lines.get(1).getSiblings().get(0).getStyle().getColor().getValue(),
                                "the attack speed value is yellow");
                assertEquals("Sweep Damage: +75%", lines.get(2).getString(),
                                "the sweeping ratio reads as a percentage, no level bracket");
                Component damage = lines.get(0);
                assertEquals(0xFF5555, damage.getSiblings().get(0).getStyle().getColor().getValue(),
                                "the total is red");
                assertEquals(0xFF5555, damage.getSiblings().get(1).getStyle().getColor().getValue(),
                                "the item contribution is red");
                assertEquals(0x5555FF, damage.getSiblings().get(2).getStyle().getColor().getValue(),
                                "the enchantment level bracket is blue");
                assertEquals(0x000000, damage.getSiblings().get(3).getStyle().getColor().getValue(),
                                "the wielder base is dark");
        }

        /**
         * A spare sword — the same stats without the wielder — loses
         * the dark base annotation and reads the item plus enchantment
         * total only.
         */
        @Test
        void spareSwordShowsNoWielderAnnotation() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 3.0, "Sharpness V", 5, 0.0, 0.0, "", 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Damage: +9 (6) [5]", lines.get(0).getString());
        }

        /** An unenchanted spare sword reads just its own +6 — one contributor, no annotations. */
        @Test
        void unenchantedSpareSwordReadsItsOwnDamage() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 0.0, "", 0, 0.0, 0.0, "", 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Damage: +6", lines.get(0).getString(),
                                "a single contributor needs no breakdown of itself");
        }

        /**
         * An unenchanted held sword splits item from fist: +7 (6) (1).
         */
        @Test
        void unenchantedHeldSwordSplitsItemFromFist() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 0.0, "", 0, 1.0, 0.0, "", 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Damage: +7 (6) (1)", lines.get(0).getString());
        }

        /** An active Strength effect annotates its own contribution in green. */
        @Test
        void activeStrengthAnnotatesItsContribution() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 3.0, "Sharpness V", 5, 1.0, 3.0, "Strength", 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Damage: +13 (6) [5] (1) (+3 Strength)", lines.get(0).getString());
                int siblings = lines.get(0).getSiblings().size();
                assertEquals(0x55FF55, lines.get(0).getSiblings().get(siblings - 1).getStyle().getColor().getValue(),
                                "the Strength annotation is green");
        }

        /** A fractional bonus (Sharpness II, 8.5 held) keeps its half point. */
        @Test
        void halfPointBonusesKeepTheirFraction() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 1.5, "Sharpness II", 2, 1.0, 0.0, "", 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Damage: +8.5 (6) [2] (1)", lines.get(0).getString());
        }

        /** A conditional damage enchantment states its condition on its own line. */
        @Test
        void conditionalDamageStatesItsCondition() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 0.0, "", 0, 0.0, 0.0, "", 1.6, 0.0,
                                                List.of(new ItemStatistics.ConditionalBonus("Smite II (vs undead)", 5.0)))),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Smite II (vs undead): +5", lines.get(2).getString());
        }

        /** A trident labels the melee line and adds the thrown section. */
        @Test
        void tridentShowsMeleeAndThrownSections() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                8.0, 0.0, "", 0, 0.0, 0.0, "", 1.1, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(
                                                2.5, 8.0, 0.0, 0, 10, 1, 0, false, 0, false)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.TRIDENT, stats);
                assertEquals("Melee Damage: +8", lineStarting(lines, "Melee Damage:").getString());
                assertEquals("Thrown Damage: +8", lineStarting(lines, "Thrown Damage:").getString());
                assertEquals("Throw Velocity: 50 blocks/s", lineStarting(lines, "Throw Velocity:").getString());
                assertEquals("Throw Time: 0.5s", lineStarting(lines, "Throw Time:").getString());
        }

        /** A spear labels the damage line as the jab and adds reach, charge and Lunge. */
        @Test
        void spearShowsJabReachChargeAndLunge() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                5.0, 0.0, "", 0, 0.0, 0.0, "", 1.05, 0.0, List.of())),
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
                                Optional.of(new ItemStatistics.MiningSpeed(2.0, 0.0, 0, true)),
                                Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SHOVEL, stats);
                assertEquals(2, lines.size(), "mining speed + trailing blank");
                assertEquals("Mining Speed: 2", lines.get(0).getString());
        }

        /**
         * A pickaxe with Efficiency shows the base speed and the
         * efficiency bonus as separate compact lines, the bonus with
         * the enchantment's level in the blue bracket.
         */
        @Test
        void pickaxeWithEfficiencyShowsBaseAndBonus() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(),
                                Optional.of(new ItemStatistics.MiningSpeed(8.0, 26.0, 5, true)),
                                Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.PICKAXE, stats);
                assertEquals("Mining Speed: 8", lines.get(0).getString());
                assertEquals("Mining Efficiency: +26 [5]", lines.get(1).getString());
                assertEquals(0x5555FF, lines.get(1).getSiblings().get(1).getStyle().getColor().getValue(),
                                "the enchantment bracket is blue");
        }

        /**
         * A chestplate shows the RPG defense number — armor points and
         * generic Protection, both on the 10× scale, the Protection
         * level in the blue bracket — and the vanilla toughness under
         * its renamed label. A specialized protection (Fire
         * Protection's EPF rides as its value) names the damage type it
         * guards and never appears as generic Protection.
         */
        @Test
        void chestplateShowsRpgDefenseRenamedToughnessAndSpecializedProtections() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ArmorStats(130.0, 2.0, 0.05, 5, List.of(
                                                new ItemStatistics.ConditionalBonus("Fire Resistance", 8.0)))),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CHESTPLATE, stats);
                assertEquals("Defense: +130 [5]", lineStarting(lines, "Defense:").getString());
                assertEquals("Toughness: +2", lineStarting(lines, "Toughness:").getString(),
                                "the vanilla label 'Armor Toughness' is renamed");
                assertFalse(containsLineStarting(lines, "Armor Toughness"));
                assertEquals("Knockback Resistance: +5%", lineStarting(lines, "Knockback Resistance:").getString());
                assertEquals("Fire Resistance: -32%", lineStarting(lines, "Fire Resistance:").getString(),
                                "Fire Protection IV reads 8 EPF, 32% for the piece");
                assertFalse(containsLineStarting(lines, "Protection"),
                                "generic Protection lives in the defense number, not as its own line");
                Component defense = lineStarting(lines, "Defense:");
                assertEquals(0x5555FF, defense.getSiblings().get(1).getStyle().getColor().getValue(),
                                "the Protection bracket is blue");
        }

        /** A chestplate without Protection shows the bare scaled defense, no bracket. */
        @Test
        void chestplateWithoutProtectionShowsBareDefense() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ArmorStats(80.0, 2.0, 0.0, 0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CHESTPLATE, stats);
                assertEquals("Defense: +80", lineStarting(lines, "Defense:").getString());
                assertFalse(lineStarting(lines, "Defense:").getSiblings().size() > 1,
                                "no bracket without Protection");
        }

        /**
         * A bow shows the fully-drawn damage, the gold velocity with
         * the + sign and Blocks/s unit, and the green draw time —
         * never melee attack speed, never a Projectile qualifier.
         */
        @Test
        void bowShowsFullyDrawnDamageGoldVelocityAndDrawTime() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(3.0, 2.0, 3.0, 5, 20, 1, 0, false, 0, true)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.BOW, stats);
                assertEquals("Damage: +15 [5]",
                                lineStarting(lines, "Damage:").getString());
                assertEquals("Velocity: +60 Blocks/s",
                                lineStarting(lines, "Velocity:").getString());
                assertEquals(0xFFAA00, lineStarting(lines, "Velocity:").getSiblings().get(0).getStyle().getColor().getValue(),
                                "the bow's launch velocity is gold");
                assertEquals("Draw Time: 1s", lineStarting(lines, "Draw Time:").getString());
                assertFalse(containsLineStarting(lines, "Projectile"),
                                "a bow never qualifies its damage or velocity with 'Projectile'");
                assertFalse(containsLineStarting(lines, "Attack Speed:"),
                                "a bow must never show melee attack speed");
                Component drawTime = lineStarting(lines, "Draw Time:");
                assertEquals(0x55FF55, drawTime.getSiblings().get(0).getStyle().getColor().getValue(),
                                "the draw time value is green");
        }

        /** A multishot crossbow shows reload time, three projectiles and pierce depth. */
        @Test
        void crossbowShowsReloadProjectilesAndPiercing() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(3.15, 2.0, 0.0, 0, 10, 3, 4, false, 0, true)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CROSSBOW, stats);
                assertEquals("Projectile Damage: +7", lineStarting(lines, "Projectile Damage").getString());
                assertEquals("Reload Time: 0.5s", lineStarting(lines, "Reload Time:").getString());
                assertEquals("Projectiles: 3", lineStarting(lines, "Projectiles:").getString());
                assertEquals("Piercing: 4", lineStarting(lines, "Piercing:").getString());
        }

        /** A firework-loaded crossbow presents the rocket, not an arrow damage number. */
        @Test
        void fireworkCrossbowShowsTheRocket() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.ProjectileStats(1.6, 2.0, 0.0, 0, 25, 1, 0, true, 3, true)),
                                Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.CROSSBOW, stats);
                assertEquals("Projectile: Firework Rocket", lineStarting(lines, "Projectile:").getString());
                assertEquals("Firework Damage: +11 (3 stars)", lineStarting(lines, "Firework Damage:").getString());
        }

        /**
         * A mace shows the compact smash section: the fall-gated bonus
         * at the minimum smash fall, Density's per-block rate, Breach's
         * armor reduction and the Wind Burst flag — no band breakdown,
         * no calculation chain.
         */
        @Test
        void maceShowsCompactSmashSection() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(6.0, 0.0, "", 0, 0.0, 0.0, "", 0.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(),
                                Optional.of(new ItemStatistics.SmashDamage(
                                                1.5,
                                                List.of(new ItemStatistics.SmashDamage.SmashBand(4.0, 3.0),
                                                                new ItemStatistics.SmashDamage.SmashBand(2.0, 8.0),
                                                                new ItemStatistics.SmashDamage.SmashBand(1.0,
                                                                                Double.POSITIVE_INFINITY)),
                                                2.5, 5, -0.6, 4, true)),
                                Optional.empty(), Optional.empty());
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.MACE, stats);
                assertEquals("Smash Damage: +6 past 1.5 blocks fallen",
                                lineStarting(lines, "Smash Damage:").getString());
                assertEquals("Density: +2.5/block fallen", lineStarting(lines, "Density:").getString());
                assertEquals("Breach: -60% armor effectiveness", lineStarting(lines, "Breach:").getString());
                assertEquals("Wind Burst: launches on smash hit", lineStarting(lines, "Wind Burst:").getString());
                assertFalse(containsLineStarting(lines, "+4/block"),
                                "the piecewise band breakdown is calculation-chain prose, not a stat");
        }

        /**
         * Looting on the hovered item shows Magic Find in aqua, one
         * point per level, between the other stats and durability.
         */
        @Test
        void lootingShowsMagicFindInAqua() {
                ItemStatistics stats = new ItemStatistics(
                                Optional.of(new ItemStatistics.MeleeDamage(
                                                6.0, 3.0, "Sharpness V", 5, 1.0, 0.0, "", 1.6, 0.0, List.of())),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(3));
                List<Component> lines = ItemPresentationBuilder.build(ItemCategory.SWORD, stats);
                assertEquals("Magic Find: +3", lineStarting(lines, "Magic Find:").getString());
                assertEquals(0x55FFFF, lineStarting(lines, "Magic Find:").getSiblings().get(0).getStyle().getColor().getValue(),
                                "the Magic Find value is aqua");
                int magicFind = lines.indexOf(lineStarting(lines, "Magic Find:"));
                assertTrue(lines.indexOf(lineStarting(lines, "Damage:")) < magicFind,
                                "Magic Find follows the combat stats");
        }

        /**
         * A fully repaired item shows the maximum alone; a damaged one
         * shows current / maximum. Neither is ever Unbreaking-inflated.
         */
        @Test
        void durabilityShowsMaxAloneWhenFull() {
                ItemStatistics full = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.Durability(1561, 1561)),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> fullLines = ItemPresentationBuilder.build(ItemCategory.PICKAXE, full);
                assertEquals("Durability: 1561", lineStarting(fullLines, "Durability:").getString(),
                                "a full item does not echo the maximum twice");
                ItemStatistics damaged = new ItemStatistics(
                                Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.of(new ItemStatistics.Durability(1358, 1561)),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                List<Component> damagedLines = ItemPresentationBuilder.build(ItemCategory.PICKAXE, damaged);
                assertEquals("Durability: 1358 / 1561", lineStarting(damagedLines, "Durability:").getString());
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
