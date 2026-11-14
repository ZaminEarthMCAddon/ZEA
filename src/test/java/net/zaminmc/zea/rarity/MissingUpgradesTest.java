package net.zaminmc.zea.rarity;

import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The missing-upgrade selection and rendering: next-level suggestions,
 * max-level suggestions for absent enchantments, conflict-aware filtering,
 * group leaders, the neutral exclusion (upstream of this class, via the
 * budget) and the comma-aware wrapping that keeps the tooltip honest.
 */
class MissingUpgradesTest {
        private final EnchantmentFixtures fixtures = new EnchantmentFixtures();

        private static final ToIntFunction<String> WIDTH = String::length;

        private static ItemEnchantments enchantments(EnchantmentFixtures fixtures, Object... pairs) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                for (int i = 0; i < pairs.length; i += 2) {
                        mutable.set(fixtures.get((String) pairs[i]), (Integer) pairs[i + 1]);
                }
                return mutable.toImmutable();
        }

        private static List<String> displays(List<MissingUpgrades.Upgrade> upgrades) {
                return upgrades.stream().map(MissingUpgrades.Upgrade::display).toList();
        }

        @Test
        void sharpnessFourSuggestsSharpnessFiveOnly() {
                fixtures.make("looting", 3);
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("sharpness", "looting"),
                                                enchantments(fixtures, "sharpness", 4));
                assertEquals(List.of("Looting III", "Sharpness V"), displays(upgrades));
        }

        @Test
        void absentEnchantmentsSuggestTheirMaximum() {
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("efficiency"),
                                                ItemEnchantments.EMPTY);
                assertEquals(List.of("Efficiency V"), displays(upgrades));
        }

        @Test
        void maxedEnchantmentsAreNeverSuggested() {
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("sharpness", "smite"),
                                                enchantments(fixtures, "sharpness", 5, "smite", 5));
                assertTrue(upgrades.isEmpty());
        }

        @Test
        void presentBelowMaxSuggestsTheNextLevelNotIntermediateOnes() {
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("sharpness"),
                                                enchantments(fixtures, "sharpness", 2));
                assertEquals(List.of("Sharpness III"), displays(upgrades));
        }

        @Test
        void conflictingPresentEnchantmentBlocksItsGroup() {
                // A sword with Smite III cannot take Sharpness — the
                // suggestion list must not offer it.
                fixtures.make("looting", 3);
                var sharpness = fixtures.makeExclusive("sharpness", 5);
                var smite = fixtures.makeExclusive("smite", 5, sharpness);
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("sharpness", "smite", "looting"),
                                                enchantments(fixtures, "smite", 3));
                assertEquals(List.of("Looting III", "Smite IV"), displays(upgrades));
        }

        @Test
        void maxedGroupMemberBlocksTheWholeGroup() {
                var sharpness = fixtures.makeExclusive("sharpness", 5);
                fixtures.makeExclusive("smite", 5, sharpness);
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("sharpness", "smite"),
                                                enchantments(fixtures, "smite", 5));
                assertTrue(upgrades.isEmpty());
        }

        @Test
        void absentGroupSuggestsOnlyItsLeader() {
                var sharpness = fixtures.makeExclusive("sharpness", 5);
                fixtures.makeExclusive("smite", 5, sharpness);
                fixtures.makeExclusive("bane_of_arthropods", 5, sharpness);
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("sharpness", "smite",
                                                                                "bane_of_arthropods"),
                                                ItemEnchantments.EMPTY);
                assertEquals(List.of("Sharpness V"), displays(upgrades));
        }

        @Test
        void heldNeutralEnchantmentStillBlocksItsConflictGroup() {
                // Boots with Frost Walker (neutral, held): Depth Strider is
                // impossible on them, so it must not be suggested.
                var depthStrider = fixtures.makeExclusive("depth_strider", 3);
                var frostWalker = fixtures.makeExclusive("frost_walker", 2, depthStrider);
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("depth_strider", "frost_walker"),
                                                enchantments(fixtures, "frost_walker", 2));
                assertTrue(upgrades.isEmpty());
        }

        @Test
        void neutralEnchantmentsAreFilteredUpstreamOfSuggestions() {
                // The budget only contains non-neutral enchantments, so a
                // neutral one (held here at max) never appears in the list.
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(
                                                fixtures.compatibleAll("sharpness"),
                                                enchantments(fixtures, "knockback", 2));
                assertEquals(List.of("Sharpness V"), displays(upgrades));
        }

        @Test
        void renderingJoinsWithCommasIntoOneLineWhenShort() {
                List<net.minecraft.network.chat.Component> lines = MissingUpgrades.render(
                                                List.of(
                                                                                new MissingUpgrades.Upgrade("Sharpness V"),
                                                                                new MissingUpgrades.Upgrade("Looting III")),
                                                200,
                                                WIDTH);
                assertEquals(2, lines.size());
                assertEquals("Missing enchantments:", lines.get(0).getString());
                assertEquals("Sharpness V, Looting III", lines.get(1).getString());
        }

        @Test
        void renderingWrapsLongListsAtCommaBoundaries() {
                List<MissingUpgrades.Upgrade> upgrades = List.of(
                                                new MissingUpgrades.Upgrade("Alpha Enchantment VII"),
                                                new MissingUpgrades.Upgrade("Beta Enchantment VII"),
                                                new MissingUpgrades.Upgrade("Gamma Enchantment VII"));
                // Width 30 forces a wrap between entries — never inside an
                // entry — and every entry survives.
                List<net.minecraft.network.chat.Component> lines = MissingUpgrades.render(
                                                                upgrades, 30, WIDTH);
                assertTrue(lines.size() > 2);
                String joined = String.join(" ", lines.subList(1, lines.size()).stream()
                                                .map(net.minecraft.network.chat.Component::getString).toList());
                for (String name : List.of("Alpha Enchantment VII", "Beta Enchantment VII",
                                                "Gamma Enchantment VII")) {
                        assertTrue(joined.contains(name), name + " missing from: " + joined);
                }
        }

        @Test
        void renderingNeverBreaksInsideASingleLongEntry() {
                String longName = "Extraordinarily Long Enchantment Name";
                List<net.minecraft.network.chat.Component> lines = MissingUpgrades.render(
                                                List.of(new MissingUpgrades.Upgrade(longName)),
                                                10,
                                                WIDTH);
                // The entry does not fit the budget, but it is still shown,
                // alone, unbroken.
                assertEquals(2, lines.size());
                assertEquals(longName, lines.get(1).getString());
        }

        @Test
        void renderingAnEmptyListShowsNothing() {
                List<net.minecraft.network.chat.Component> lines = MissingUpgrades.render(
                                                List.of(), 200, WIDTH);
                assertTrue(lines.isEmpty());
        }

        @Test
        void manyUpgradesWrapWithoutOverflow() {
                var upgrades = new java.util.ArrayList<MissingUpgrades.Upgrade>();
                for (int i = 0; i < 30; i++) {
                        upgrades.add(new MissingUpgrades.Upgrade("Enchant " + i + " V"));
                }
                List<net.minecraft.network.chat.Component> lines = MissingUpgrades.render(
                                                upgrades, 120, WIDTH);
                for (int i = 1; i < lines.size(); i++) {
                        assertTrue(lines.get(i).getString().length() <= 120,
                                                        "line exceeds width: " + lines.get(i).getString());
                }
                assertTrue(lines.size() > 3);
        }
}
