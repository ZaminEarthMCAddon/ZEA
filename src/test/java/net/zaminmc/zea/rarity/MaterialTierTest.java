package net.zaminmc.zea.rarity;

import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Material resolution against the real 26.3 registry data. The tier
 * ladders are looked up from the actual {@link ToolMaterial} records and
 * {@link EquipmentAssets} constants, through the pure component/asset
 * seams — a bare test JVM bootstraps the registries but never binds item
 * default components (that needs datapack tag data), so the stack-based
 * {@link MaterialTier#evaluate} wrapper gets live verification instead.
 */
class MaterialTierTest {
        static {
                VanillaBootstrap.ensure();
        }

        private static int toolTier(ToolMaterial material) {
                return MaterialTier.toolTierFromComponents(material.durability(), material.enchantmentValue());
        }

        @Test
        void toolMaterialFingerprintsAreUnique() {
                // The (durability, enchantability) pair must identify a
                // material exactly — otherwise two materials would classify
                // as one.
                ToolMaterial[] materials = {
                                                ToolMaterial.WOOD, ToolMaterial.GOLD, ToolMaterial.STONE,
                                                ToolMaterial.COPPER, ToolMaterial.IRON, ToolMaterial.DIAMOND,
                                                ToolMaterial.NETHERITE};
                for (int i = 0; i < materials.length; i++) {
                        for (int j = i + 1; j < materials.length; j++) {
                                assertNotEquals(toolTier(materials[i]) + "/" + toolTier(materials[j]),
                                                                fingerprint(materials[i]) + " vs "
                                                                                                + fingerprint(materials[j]));
                                assertNotEquals(fingerprint(materials[i]), fingerprint(materials[j]));
                        }
                }
        }

        private static long fingerprint(ToolMaterial material) {
                return ((long) material.durability() << 32) | (material.enchantmentValue() & 0xFFFFFFFFL);
        }

        @Test
        void toolLadderOrder() {
                assertEquals(0, toolTier(ToolMaterial.WOOD));
                assertEquals(1, toolTier(ToolMaterial.GOLD));
                assertEquals(2, toolTier(ToolMaterial.STONE));
                assertEquals(3, toolTier(ToolMaterial.COPPER));
                assertEquals(4, toolTier(ToolMaterial.IRON));
                assertEquals(5, toolTier(ToolMaterial.DIAMOND));
                assertEquals(6, toolTier(ToolMaterial.NETHERITE));
        }

        @Test
        void unknownFingerprintFallsBackToTierZero() {
                // A pair no vanilla material produces (edited components,
                // modded items) must not classify as any real material.
                assertEquals(0, MaterialTier.toolTierFromComponents(4242, 10));
                assertEquals(0, MaterialTier.toolTierFromComponents(null, 10));
                assertEquals(0, MaterialTier.toolTierFromComponents(59, null));
        }

        @Test
        void armorLadderThroughEquipmentAssets() {
                assertEquals(0, MaterialTier.armorTierFromAsset(EquipmentAssets.LEATHER));
                assertEquals(1, MaterialTier.armorTierFromAsset(EquipmentAssets.GOLD));
                assertEquals(2, MaterialTier.armorTierFromAsset(EquipmentAssets.CHAINMAIL));
                assertEquals(3, MaterialTier.armorTierFromAsset(EquipmentAssets.COPPER));
                assertEquals(4, MaterialTier.armorTierFromAsset(EquipmentAssets.IRON));
                assertEquals(5, MaterialTier.armorTierFromAsset(EquipmentAssets.DIAMOND));
                assertEquals(5, MaterialTier.armorTierFromAsset(EquipmentAssets.TURTLE_SCUTE));
                assertEquals(6, MaterialTier.armorTierFromAsset(EquipmentAssets.NETHERITE));
        }

        @Test
        void nonPlayerEquipmentAssetsFallBackToTierZero() {
                // Wolf armor and elytra exist as equipment assets but are
                // not on the player armor ladder.
                assertEquals(0, MaterialTier.armorTierFromAsset(EquipmentAssets.ARMADILLO_SCUTE));
                assertEquals(0, MaterialTier.armorTierFromAsset(EquipmentAssets.ELYTRA));
        }

        @Test
        void durabilityValuesFeedTheFingerprintExactly() {
                // The record values the fingerprint reads — pin a few so a
                // vanilla data change that breaks the pair-uniqueness
                // assumption shows up here first.
                assertEquals(59, ToolMaterial.WOOD.durability());
                assertEquals(32, ToolMaterial.GOLD.durability());
                assertEquals(2031, ToolMaterial.NETHERITE.durability());
                assertEquals(15, ToolMaterial.WOOD.enchantmentValue());
                assertEquals(22, ToolMaterial.GOLD.enchantmentValue());
                assertEquals(15, ToolMaterial.NETHERITE.enchantmentValue());
        }

        /**
         * The dedicated weapon classes evaluate to the diamond-class
         * weapon tier: with no material ladder of their own, bow,
         * crossbow, trident and mace sit where the rarity model's
         * established rules put single-instance endgame equipment —
         * Uncommon unenchanted, Legendary fully maxed, never below a
         * properly developed diamond item.
         */
        @Test
        void weaponClassesEvaluateToTheDiamondClassTier() {
                assertEquals(MaterialTier.WEAPON_TIER, 5);
                assertEquals(1, Math.clamp(MaterialTier.WEAPON_TIER - 4, 0, 2),
                                "an unenchanted bow reads Uncommon, like a diamond sword");
                assertEquals(3, RarityCalculator.ceilingFor(MaterialTier.WEAPON_TIER),
                                "a fully-maxed bow can reach Legendary");
        }
}
