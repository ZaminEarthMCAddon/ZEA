package net.zaminmc.zea.itempresentation.recognition;

import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.zaminmc.zea.VanillaBootstrap;
import org.junit.jupiter.api.Test;

import static net.zaminmc.zea.itempresentation.recognition.MaterialInspector.MaterialName;
import static net.zaminmc.zea.itempresentation.recognition.MaterialInspector.armorMaterialFromAsset;
import static net.zaminmc.zea.itempresentation.recognition.MaterialInspector.ofTier;
import static net.zaminmc.zea.itempresentation.recognition.MaterialInspector.toolMaterialFromComponents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The material-name resolution against the real 26.3 registry data.
 * The pure seams take the (durability, enchantability) fingerprint and
 * the equipment-asset key the live inspector reads, so every mapping is
 * testable without binding item default components.
 */
class MaterialInspectorTest {
        static {
                VanillaBootstrap.ensure();
        }

        /** Each tool material's real record values resolve to its name. */
        @Test
        void toolMaterialFingerprintsResolveToNames() {
                assertEquals(MaterialName.WOOD,
                                toolMaterialFromComponents(ToolMaterial.WOOD.durability(),
                                                ToolMaterial.WOOD.enchantmentValue()));
                assertEquals(MaterialName.GOLD,
                                toolMaterialFromComponents(ToolMaterial.GOLD.durability(),
                                                ToolMaterial.GOLD.enchantmentValue()));
                assertEquals(MaterialName.STONE,
                                toolMaterialFromComponents(ToolMaterial.STONE.durability(),
                                                ToolMaterial.STONE.enchantmentValue()));
                assertEquals(MaterialName.COPPER,
                                toolMaterialFromComponents(ToolMaterial.COPPER.durability(),
                                                ToolMaterial.COPPER.enchantmentValue()));
                assertEquals(MaterialName.IRON,
                                toolMaterialFromComponents(ToolMaterial.IRON.durability(),
                                                ToolMaterial.IRON.enchantmentValue()));
                assertEquals(MaterialName.DIAMOND,
                                toolMaterialFromComponents(ToolMaterial.DIAMOND.durability(),
                                                ToolMaterial.DIAMOND.enchantmentValue()));
                assertEquals(MaterialName.NETHERITE,
                                toolMaterialFromComponents(ToolMaterial.NETHERITE.durability(),
                                                ToolMaterial.NETHERITE.enchantmentValue()));
        }

        /** A fingerprint that matches no material resolves to null. */
        @Test
        void unknownFingerprintResolvesToNull() {
                assertNull(toolMaterialFromComponents(12345, 99));
                assertNull(toolMaterialFromComponents(null, 14));
                assertNull(toolMaterialFromComponents(250, null));
        }

        /** The tier round-trips to the same material name in ladder order. */
        @Test
        void ofTierMatchesLadderOrder() {
                assertEquals(MaterialName.WOOD, ofTier(0));
                assertEquals(MaterialName.GOLD, ofTier(1));
                assertEquals(MaterialName.STONE, ofTier(2));
                assertEquals(MaterialName.COPPER, ofTier(3));
                assertEquals(MaterialName.IRON, ofTier(4));
                assertEquals(MaterialName.DIAMOND, ofTier(5));
                assertEquals(MaterialName.NETHERITE, ofTier(6));
                assertNull(ofTier(-1));
                assertNull(ofTier(7));
        }

        /** Each armor equipment-asset constant resolves to its name. */
        @Test
        void armorAssetsResolveToNames() {
                assertEquals(MaterialName.LEATHER, armorMaterialFromAsset(EquipmentAssets.LEATHER));
                assertEquals(MaterialName.GOLD, armorMaterialFromAsset(EquipmentAssets.GOLD));
                assertEquals(MaterialName.CHAINMAIL, armorMaterialFromAsset(EquipmentAssets.CHAINMAIL));
                assertEquals(MaterialName.COPPER, armorMaterialFromAsset(EquipmentAssets.COPPER));
                assertEquals(MaterialName.IRON, armorMaterialFromAsset(EquipmentAssets.IRON));
                assertEquals(MaterialName.DIAMOND, armorMaterialFromAsset(EquipmentAssets.DIAMOND));
                assertEquals(MaterialName.TURTLE, armorMaterialFromAsset(EquipmentAssets.TURTLE_SCUTE));
                assertEquals(MaterialName.NETHERITE, armorMaterialFromAsset(EquipmentAssets.NETHERITE));
        }

        /** An asset key not in the ladder resolves to null. */
        @Test
        void unknownArmorAssetResolvesToNull() {
                ResourceKey<EquipmentAsset> unknown = ResourceKey.create(
                                EquipmentAssets.ROOT_ID,
                                net.minecraft.resources.Identifier.parse("zea:does_not_exist"));
                assertNull(armorMaterialFromAsset(unknown));
        }
}
