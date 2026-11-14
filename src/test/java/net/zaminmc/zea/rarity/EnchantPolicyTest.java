package net.zaminmc.zea.rarity;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnchantPolicy}'s judgment calls: the neutral set and the
 * group-leader ranking that decides which member of a mutually exclusive
 * group is the meaningful suggestion. Registry-key logic runs through the
 * path-based seams with the real vanilla key paths; direct-holder
 * fixtures exercise {@link Enchantment#areCompatible} for the exclusivity
 * contract.
 */
class EnchantPolicyTest {
        private final EnchantmentFixtures fixtures = new EnchantmentFixtures();

        @Test
        void specExamplesAreNeutral() {
                assertTrue(EnchantPolicy.isNeutralPath("knockback"));
                assertTrue(EnchantPolicy.isNeutralPath("thorns"));
        }

        @Test
        void theOtherPreferenceEnchantmentsAreNeutral() {
                assertTrue(EnchantPolicy.isNeutralPath("soul_speed"));
                assertTrue(EnchantPolicy.isNeutralPath("swift_sneak"));
                assertTrue(EnchantPolicy.isNeutralPath("frost_walker"));
                assertTrue(EnchantPolicy.isNeutralPath("binding_curse"));
                assertTrue(EnchantPolicy.isNeutralPath("vanishing_curse"));
        }

        @Test
        void progressionEnchantmentsAreNotNeutral() {
                assertFalse(EnchantPolicy.isNeutralPath("sharpness"));
                assertFalse(EnchantPolicy.isNeutralPath("protection"));
                assertFalse(EnchantPolicy.isNeutralPath("efficiency"));
                assertFalse(EnchantPolicy.isNeutralPath("unbreaking"));
                assertFalse(EnchantPolicy.isNeutralPath("mending"));
                assertFalse(EnchantPolicy.isNeutralPath("looting"));
        }

        @Test
        void keylessHoldersAreNeverNeutral() {
                // A holder without a registry key (a direct holder, or
                // something exotic from another source) cannot match the
                // neutral set by accident.
                assertFalse(EnchantPolicy.isNeutral(fixtures.make("knockback", 2)));
        }

        @Test
        void damageGroupLeaderIsSharpness() {
                List<Map.Entry<String, Holder<Enchantment>>> group = List.of(
                                                Map.entry("bane_of_arthropods", fixtures.make("bane_of_arthropods", 5)),
                                                Map.entry("smite", fixtures.make("smite", 5)),
                                                Map.entry("sharpness", fixtures.make("sharpness", 5)));
                assertEquals(fixtures.get("sharpness"), EnchantPolicy.groupLeaderByKey(group));
        }

        @Test
        void protectionGroupLeaderIsProtection() {
                List<Map.Entry<String, Holder<Enchantment>>> group = List.of(
                                                Map.entry("fire_protection", fixtures.make("fire_protection", 4)),
                                                Map.entry("projectile_protection", fixtures.make("projectile_protection", 4)),
                                                Map.entry("protection", fixtures.make("protection", 4)),
                                                Map.entry("blast_protection", fixtures.make("blast_protection", 4)));
                assertEquals(fixtures.get("protection"), EnchantPolicy.groupLeaderByKey(group));
        }

        @Test
        void durabilityGroupLeaderIsMendingOverInfinity() {
                List<Map.Entry<String, Holder<Enchantment>>> group = List.of(
                                                Map.entry("infinity", fixtures.make("infinity", 1)),
                                                Map.entry("mending", fixtures.make("mending", 1)));
                assertEquals(fixtures.get("mending"), EnchantPolicy.groupLeaderByKey(group));
        }

        @Test
        void tridentGroupLeaderIsLoyalty() {
                List<Map.Entry<String, Holder<Enchantment>>> group = List.of(
                                                Map.entry("riptide", fixtures.make("riptide", 3)),
                                                Map.entry("channeling", fixtures.make("channeling", 1)),
                                                Map.entry("loyalty", fixtures.make("loyalty", 3)));
                assertEquals(fixtures.get("loyalty"), EnchantPolicy.groupLeaderByKey(group));
        }

        @Test
        void unrankedGroupFallsBackToAlphabeticalKey() {
                List<Map.Entry<String, Holder<Enchantment>>> group = List.of(
                                                Map.entry("zeta_enchant", fixtures.make("zeta_enchant", 1)),
                                                Map.entry("alpha_enchant", fixtures.make("alpha_enchant", 1)));
                assertEquals(fixtures.get("alpha_enchant"), EnchantPolicy.groupLeaderByKey(group));
        }

        @Test
        void rankedBeatsUnrankedRegardlessOfAlphabet() {
                List<Map.Entry<String, Holder<Enchantment>>> group = List.of(
                                                Map.entry("aaa_unranked", fixtures.make("aaa_unranked", 1)),
                                                Map.entry("sharpness", fixtures.make("sharpness", 5)));
                assertEquals(fixtures.get("sharpness"), EnchantPolicy.groupLeaderByKey(group));
        }

        @Test
        void singletonGroupReturnsItsOnlyMember() {
                Holder<Enchantment> only = fixtures.make("only_enchant", 1);
                assertEquals(only,
                                                EnchantPolicy.groupLeaderByKey(List.of(Map.entry("only_enchant", only))));
        }

        @Test
        void mutuallyExclusiveFixturesAreIncompatible() {
                Holder<Enchantment> sharpness = fixtures.makeExclusive("sharpness", 5);
                Holder<Enchantment> smite = fixtures.makeExclusive("smite", 5, sharpness);
                assertFalse(Enchantment.areCompatible(sharpness, smite));
                assertTrue(Enchantment.areCompatible(sharpness, fixtures.make("looting", 3)));
        }

        @Test
        void anEnchantmentIsIncompatibleWithItself() {
                Holder<Enchantment> sharpness = fixtures.make("sharpness", 5);
                assertFalse(Enchantment.areCompatible(sharpness, sharpness));
        }

        @Test
        void exclusiveGroupMembersAreFoundTransitively() {
                // The group walk in MissingUpgrades clusters by direct
                // conflicts; a clique (each member conflicting with the
                // seed) must collapse to one leader.
                Holder<Enchantment> sharpness = fixtures.makeExclusive("sharpness", 5);
                Holder<Enchantment> smite = fixtures.makeExclusive("smite", 5, sharpness);
                Holder<Enchantment> bane = fixtures.makeExclusive("bane_of_arthropods", 5, sharpness);
                assertFalse(Enchantment.areCompatible(sharpness, smite));
                assertFalse(Enchantment.areCompatible(sharpness, bane));
                // Smite and Bane are not exclusive with each other in
                // vanilla either way — the seed's conflicts define the
                // group.
                assertTrue(Enchantment.areCompatible(smite, bane));
        }
}
