package net.zaminmc.zea.rarity;

import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mapped rarity tables, pinned against their own reference rule.
 * Every cell of every family's table is re-derived through
 * {@link RarityCalculator#calculate} — the readable rule the generator
 * implements — so the generated data can never drift from the rule
 * silently. The universes are pinned against the vanilla 26.3 data the
 * generator transcribed, the worked examples from the rule's own
 * documentation hold at the tables, and the stability promise — server
 * custom enchantments contribute nothing — is asserted directly.
 */
class RarityTablesTest {
        private static final List<ItemCategory> FAMILIES = List.of(
                        ItemCategory.SWORD, ItemCategory.AXE, ItemCategory.PICKAXE,
                        ItemCategory.SHOVEL, ItemCategory.HOE, ItemCategory.SPEAR,
                        ItemCategory.MACE, ItemCategory.BOW, ItemCategory.CROSSBOW,
                        ItemCategory.TRIDENT, ItemCategory.HELMET, ItemCategory.CHESTPLATE,
                        ItemCategory.LEGGINGS, ItemCategory.BOOTS, ItemCategory.SHEARS,
                        ItemCategory.FLINT_AND_STEEL, ItemCategory.SHIELD);

        /** Every family the rarity system rates has a mapped universe. */
        @Test
        void everyRatedFamilyHasAUniverse() {
                assertEquals(17, RarityTables.FAMILY_COUNT);
                for (ItemCategory family : FAMILIES) {
                        assertTrue(RarityTables.reachableNumerators(family).length > 0,
                                        family + " maps at least one state");
                }
                assertNull(RarityTables.rarityFor(ItemCategory.OTHER, 0, Map.of()),
                                "OTHER has no universe");
                assertNull(RarityTables.rarityAt(ItemCategory.OTHER, 0, 0),
                                "OTHER has no universe at the numerator seam either");
        }

        /**
         * Every cell of every table equals the reference rule's answer
         * for the same (material tier, fill) pair — the whole point of
         * mapping the space out.
         */
        @Test
        void everyCellEqualsTheReferenceRule() {
                for (ItemCategory family : FAMILIES) {
                        int[] reachable = RarityTables.reachableNumerators(family);
                        int denominator = denominatorOf(family);
                        int maxNumerator = reachable[reachable.length - 1];
                        for (int tier = 0; tier <= 6; tier++) {
                                for (int numerator : reachable) {
                                        ItemRarity mapped = RarityTables.rarityAt(
                                                        family, tier, numerator);
                                        ItemRarity reference = RarityCalculator.calculate(
                                                        tier,
                                                        (double) numerator / denominator,
                                                        (double) maxNumerator / denominator);
                                        assertEquals(reference, mapped,
                                                        family + " tier " + tier + " numerator " + numerator);
                                }
                        }
                }
        }

        /**
         * An unreachable numerator — a command-forced exclusive pair's
         * sum — maps to the nearest reachable state below it, so the
         * outcome stays inside the mapped space; below the first
         * reachable state it maps to that state.
         */
        @Test
        void unreachableNumeratorsMapNearestBelow() {
                // The sword's reachable set starts at Sharpness I's fill
                // (6 of 30); anything below maps onto it.
                int[] reachable = RarityTables.reachableNumerators(ItemCategory.SWORD);
                assertEquals(RarityTables.rarityAt(ItemCategory.SWORD, 5, reachable[0]),
                                RarityTables.rarityAt(ItemCategory.SWORD, 5, 1));
                // A forced Sharpness V + Smite V sum (60 of 180) exceeds
                // the maxed single-member fill (30) by exactly one unit;
                // it reads as the state one step below the maxed sum.
                int forcedSum = 2 * RarityTables.pinnedMaxLevel(ItemCategory.SWORD, "sharpness")
                                * (denominatorOf(ItemCategory.SWORD) / 5);
                int below = reachable[reachable.length - 2];
                assertEquals(RarityTables.rarityAt(ItemCategory.SWORD, 5, below),
                                RarityTables.rarityAt(ItemCategory.SWORD, 5, forcedSum));
        }

        /**
         * The pinned universes are the vanilla 26.3 data: every scorable
         * enchantment at its extracted maximum, nothing else.
         */
        @Test
        void theUniversesAreTheVanillaData() {
                assertEquals(List.of("sharpness", "smite", "bane_of_arthropods", "looting",
                                "sweeping_edge", "fire_aspect", "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.SWORD));
                assertEquals(5, RarityTables.pinnedMaxLevel(ItemCategory.SWORD, "sharpness"));
                assertEquals(3, RarityTables.pinnedMaxLevel(ItemCategory.SWORD, "sweeping_edge"));
                assertEquals(2, RarityTables.pinnedMaxLevel(ItemCategory.SWORD, "fire_aspect"));
                assertEquals(List.of("density", "breach", "wind_burst", "fire_aspect",
                                "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.MACE));
                assertEquals(5, RarityTables.pinnedMaxLevel(ItemCategory.MACE, "density"));
                assertEquals(4, RarityTables.pinnedMaxLevel(ItemCategory.MACE, "breach"));
                assertEquals(List.of("lunge", "looting", "fire_aspect", "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.SPEAR));
                assertEquals(3, RarityTables.pinnedMaxLevel(ItemCategory.SPEAR, "lunge"));
                assertEquals(List.of("power", "punch", "flame", "mending", "infinity"),
                                RarityTables.scorablePaths(ItemCategory.BOW));
                assertEquals(List.of("multishot", "piercing", "quick_charge", "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.CROSSBOW));
                assertEquals(List.of("impaling", "loyalty", "riptide", "channeling",
                                "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.TRIDENT));
                assertEquals(5, RarityTables.pinnedMaxLevel(ItemCategory.TRIDENT, "impaling"));
                assertEquals(List.of("protection", "fire_protection", "blast_protection",
                                "projectile_protection", "respiration", "aqua_affinity",
                                "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.HELMET));
                assertEquals(4, RarityTables.pinnedMaxLevel(ItemCategory.HELMET, "protection"));
                assertEquals(3, RarityTables.pinnedMaxLevel(ItemCategory.HELMET, "respiration"));
                assertEquals(List.of("protection", "fire_protection", "blast_protection",
                                "projectile_protection", "feather_falling", "depth_strider",
                                "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.BOOTS));
                assertEquals(4, RarityTables.pinnedMaxLevel(ItemCategory.BOOTS, "feather_falling"));
                assertEquals(List.of("efficiency", "mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.SHEARS),
                                "the mining-loot pair does not reach shears in 26.3");
                assertEquals(5, RarityTables.pinnedMaxLevel(ItemCategory.SHEARS, "efficiency"));
                assertEquals(List.of("mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.FLINT_AND_STEEL));
                assertEquals(List.of("mending", "unbreaking"),
                                RarityTables.scorablePaths(ItemCategory.SHIELD));
                // Fire Aspect does not reach axes in 26.3 (its support tag
                // covers melee weapons and the mace, not the axe).
                assertFalse(RarityTables.scorablePaths(ItemCategory.AXE).contains("fire_aspect"));
        }

        /** The rule documentation's worked examples hold at the tables. */
        @Test
        void theWorkedExamplesHold() {
                Map<String, Integer> empty = Map.of();
                // An unenchanted wooden sword (tier 0): Common.
                assertEquals(ItemRarity.COMMON, RarityTables.rarityFor(ItemCategory.SWORD, 0, empty));
                // An unenchanted diamond sword (tier 5): Uncommon.
                assertEquals(ItemRarity.UNCOMMON, RarityTables.rarityFor(ItemCategory.SWORD, 5, empty));
                // An unenchanted netherite sword (tier 6): Epic.
                assertEquals(ItemRarity.EPIC, RarityTables.rarityFor(ItemCategory.SWORD, 6, empty));
                // A fully maxed wooden sword: Uncommon (ceiling 1).
                assertEquals(ItemRarity.UNCOMMON, RarityTables.rarityFor(ItemCategory.SWORD, 0,
                                maxed(ItemCategory.SWORD)));
                // A fully maxed iron sword (tier 4): Epic (ceiling 2).
                assertEquals(ItemRarity.EPIC, RarityTables.rarityFor(ItemCategory.SWORD, 4,
                                maxed(ItemCategory.SWORD)));
                // A fully maxed diamond sword: Legendary.
                assertEquals(ItemRarity.LEGENDARY, RarityTables.rarityFor(ItemCategory.SWORD, 5,
                                maxed(ItemCategory.SWORD)));
                // A fully maxed diamond chestplate: Legendary (F = 3.0 reaches the floor).
                assertEquals(ItemRarity.LEGENDARY, RarityTables.rarityFor(ItemCategory.CHESTPLATE, 5,
                                maxed(ItemCategory.CHESTPLATE)));
                // A diamond chestplate with Protection IV + Unbreaking III: Epic.
                assertEquals(ItemRarity.EPIC, RarityTables.rarityFor(ItemCategory.CHESTPLATE, 5,
                                Map.of("protection", 4, "unbreaking", 3)));
                // A netherite sword with Sharpness V only: Epic (material carries).
                assertEquals(ItemRarity.EPIC, RarityTables.rarityFor(ItemCategory.SWORD, 6,
                                Map.of("sharpness", 5)));
                // A stone pickaxe with Efficiency V + Fortune III (P = 0.5): Uncommon.
                assertEquals(ItemRarity.UNCOMMON, RarityTables.rarityFor(ItemCategory.PICKAXE, 2,
                                Map.of("efficiency", 5, "fortune", 3)));
        }

        /**
         * The stability promise: a server custom enchantment on the
         * stack — any path outside the pinned universe — changes nothing.
         * A spear carrying the same vanilla setup plus fictional server
         * enchantments reads exactly the same rarity at every tier.
         */
        @Test
        void serverCustomEnchantmentsContributeNothing() {
                Map<String, Integer> honest = Map.of("lunge", 3, "unbreaking", 3);
                Map<String, Integer> poisoned = new HashMap<>(honest);
                poisoned.put("earthmc_lifesteal", 5);
                poisoned.put("custom_overcharge", 3);
                for (int tier = 0; tier <= 6; tier++) {
                        assertEquals(RarityTables.rarityFor(ItemCategory.SPEAR, tier, honest),
                                        RarityTables.rarityFor(ItemCategory.SPEAR, tier, poisoned),
                                        "tier " + tier + " is unmoved by custom enchantments");
                }
        }

        /**
         * Command-forced exclusive pairs stay inside the map: both
         * members of the damage clique on one sword read as the nearest
         * reachable state below the impossible sum — the same rarity the
         * single best member produces.
         */
        @Test
        void commandForcedConflictPairsStayInsideTheMap() {
                Map<String, Integer> forced = Map.of("sharpness", 5, "smite", 5, "unbreaking", 3);
                Map<String, Integer> honest = Map.of("sharpness", 5, "unbreaking", 3);
                for (int tier = 0; tier <= 6; tier++) {
                        assertEquals(RarityTables.rarityFor(ItemCategory.SWORD, tier, honest),
                                        RarityTables.rarityFor(ItemCategory.SWORD, tier, forced),
                                        "tier " + tier + " maps the forced pair onto the honest one");
                }
        }

        /**
         * The utility families rate: shears sit on the iron tier, the
         * flint and steel beside them, the shield on the single-instance
         * tier beside the bow. A maxed flint and steel tops out at Epic
         * — its maxFill of 2.0 cannot reach the Legendary fill floor —
         * while a maxed shield at the diamond-class tier reaches
         * Legendary, exactly like the other tier-5 pieces.
         */
        @Test
        void theUtilityFamiliesRate() {
                assertEquals(ItemRarity.COMMON, RarityTables.rarityFor(ItemCategory.SHEARS, 4, Map.of()));
                assertEquals(ItemRarity.EPIC, RarityTables.rarityFor(ItemCategory.SHEARS, 4,
                                maxed(ItemCategory.SHEARS)));
                assertEquals(ItemRarity.COMMON, RarityTables.rarityFor(
                                ItemCategory.FLINT_AND_STEEL, 4, Map.of()));
                assertEquals(ItemRarity.EPIC, RarityTables.rarityFor(
                                ItemCategory.FLINT_AND_STEEL, 4, maxed(ItemCategory.FLINT_AND_STEEL)));
                assertEquals(ItemRarity.UNCOMMON, RarityTables.rarityFor(ItemCategory.SHIELD, 5, Map.of()),
                                "an unenchanted shield reads like an unenchanted bow");
                assertEquals(ItemRarity.LEGENDARY, RarityTables.rarityFor(ItemCategory.SHIELD, 5,
                                maxed(ItemCategory.SHIELD)));
        }

        /** A fully maxed setup for one family, level for level. */
        private static Map<String, Integer> maxed(ItemCategory family) {
                Map<String, Integer> levels = new HashMap<>();
                for (String path : RarityTables.scorablePaths(family)) {
                        levels.put(path, RarityTables.pinnedMaxLevel(family, path));
                }
                return levels;
        }

        /** The family's fill denominator, recovered from the pinned maxima. */
        private static int denominatorOf(ItemCategory family) {
                int d = 1;
                for (String path : RarityTables.scorablePaths(family)) {
                        d = lcm(d, RarityTables.pinnedMaxLevel(family, path));
                }
                return d;
        }

        private static int lcm(int a, int b) {
                return a * b / java.math.BigInteger.valueOf(a)
                                .gcd(java.math.BigInteger.valueOf(b)).intValue();
        }
}
