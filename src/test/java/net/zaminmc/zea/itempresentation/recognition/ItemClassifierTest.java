package net.zaminmc.zea.itempresentation.recognition;

import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import static net.zaminmc.zea.itempresentation.recognition.ItemClassifier.ItemClass;
import static net.zaminmc.zea.itempresentation.recognition.ItemClassifier.classifyFromSignals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The classification precedence and the equipment allowlist, pinned
 * through the pure seam. A bare test JVM bootstraps the registries but
 * never binds the datapack item tags, so tag membership cannot be read
 * from a real stack; the seam takes the boolean signals the live
 * classifier reads, so every precedence rule is testable without a
 * loaded world.
 */
class ItemClassifierTest {
        static {
                VanillaBootstrap.ensure();
        }

        /** No dedicated class and no tags is the catch-all. */
        @Test
        void unknownItemIsOther() {
                assertEquals(ItemCategory.OTHER,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, false, false,
                                                false, false, false, false));
        }

        /** A dedicated class wins over every tag — maces are not swords. */
        @Test
        void dedicatedClassBeatsTags() {
                // A mace is conceptually a melee weapon; if it were also
                // tagged #minecraft:swords the class must still win.
                assertEquals(ItemCategory.MACE,
                                classifyFromSignals(ItemClass.MACE, false,
                                                false, true, false, false, false, false,
                                                false, false, false, false));
                assertEquals(ItemCategory.BOW,
                                classifyFromSignals(ItemClass.BOW, false,
                                                true, false, false, false, false, false,
                                                false, false, false, false));
                assertEquals(ItemCategory.TRIDENT,
                                classifyFromSignals(ItemClass.TRIDENT, false,
                                                false, false, false, false, false, false,
                                                false, false, false, true));
        }

        /** Swords, axes, spears resolve from their tags in that order. */
        @Test
        void meleeTagPrecedenceIsSwordAxeSpear() {
                assertEquals(ItemCategory.SWORD,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                true, true, true, false, false, false,
                                                false, false, false, false));
                assertEquals(ItemCategory.AXE,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, true, true, false, false, false,
                                                false, false, false, false));
                assertEquals(ItemCategory.SPEAR,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, true, false, false, false,
                                                false, false, false, false));
        }

        /** Pickaxe, shovel, hoe resolve from their tags in that order. */
        @Test
        void miningTagPrecedenceIsPickaxeShovelHoe() {
                assertEquals(ItemCategory.PICKAXE,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, true, true, true,
                                                false, false, false, false));
                assertEquals(ItemCategory.SHOVEL,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, true, true,
                                                false, false, false, false));
                assertEquals(ItemCategory.HOE,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, false, true,
                                                false, false, false, false));
        }

        /** Armor resolves by slot — a chestplate is never a helmet. */
        @Test
        void armorResolvesBySlot() {
                assertEquals(ItemCategory.HELMET,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, false, false,
                                                true, false, false, false));
                assertEquals(ItemCategory.LEGGINGS,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, false, false,
                                                false, false, true, false));
                assertEquals(ItemCategory.BOOTS,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, false, false,
                                                false, false, false, true));
        }

        /**
         * A chest-slot item with no armor attribute (the elytra, or a
         * modded glider) is NOT supported equipment: it resolves to
         * OTHER and keeps its vanilla tooltip untouched.
         */
        @Test
        void chestSlotWithoutArmorIsUnsupported() {
                assertEquals(ItemCategory.OTHER,
                                classifyFromSignals(ItemClass.PLAIN, true,
                                                false, false, false, false, false, false,
                                                false, true, false, false));
                assertEquals(ItemCategory.CHESTPLATE,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, false, false,
                                                false, true, false, false));
        }

        /**
         * The spec's exclusion list, expressed through the seam: items
         * that carry no supported family signal fall to OTHER no matter
         * what combination of unsupported properties they have. Food,
         * shields, shears and elytra are not RPG-supported.
         */
        @Test
        void exclusionsFallToOther() {
                // Everything absent: blocks, ingots, potions, books,
                // spawn eggs, horse armor, music discs, utility items.
                assertEquals(ItemCategory.OTHER,
                                classifyFromSignals(ItemClass.PLAIN, false,
                                                false, false, false, false, false, false,
                                                false, false, false, false));
                // A chest-slot item without armor points — the elytra.
                assertEquals(ItemCategory.OTHER,
                                classifyFromSignals(ItemClass.PLAIN, true,
                                                false, false, false, false, false, false,
                                                false, true, false, false));
        }

        /** The category grouping helpers. */
        @Test
        void categoryGroupsAreCorrect() {
                assertEquals(true, ItemCategory.SWORD.isMeleeWeapon());
                assertEquals(true, ItemCategory.AXE.isMeleeWeapon());
                assertEquals(true, ItemCategory.SPEAR.isMeleeWeapon());
                assertEquals(true, ItemCategory.MACE.isMeleeWeapon());
                assertEquals(true, ItemCategory.TRIDENT.isMeleeWeapon());
                assertEquals(false, ItemCategory.BOW.isMeleeWeapon());

                assertEquals(true, ItemCategory.BOW.isRangedWeapon());
                assertEquals(true, ItemCategory.CROSSBOW.isRangedWeapon());
                assertEquals(true, ItemCategory.TRIDENT.isRangedWeapon());
                assertEquals(false, ItemCategory.SWORD.isRangedWeapon());

                assertEquals(true, ItemCategory.PICKAXE.isMiningTool());
                assertEquals(true, ItemCategory.SHOVEL.isMiningTool());
                assertEquals(true, ItemCategory.HOE.isMiningTool());
                assertEquals(true, ItemCategory.AXE.isMiningTool());
                assertEquals(false, ItemCategory.SWORD.isMiningTool());

                assertEquals(true, ItemCategory.HELMET.isArmor());
                assertEquals(true, ItemCategory.CHESTPLATE.isArmor());
                assertEquals(true, ItemCategory.LEGGINGS.isArmor());
                assertEquals(true, ItemCategory.BOOTS.isArmor());
                assertEquals(false, ItemCategory.OTHER.isArmor());
        }
}
