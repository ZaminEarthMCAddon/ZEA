package net.zaminmc.zea.itempresentation.recognition;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Map;
import java.util.Optional;

/**
 * The named material an item is made of, layered on top of {@link
 * net.zaminmc.zea.rarity.MaterialTier}'s tier number.
 *
 * <p>{@code MaterialTier} answers "what ladder position is this" — a
 * number the rarity model normalizes against. The RPG tooltip also needs
 * the <em>name</em> (Wood, Stone, Copper, Iron, Gold, Diamond, Netherite
 * for tools; Leather, Gold, Chainmail, Copper, Iron, Diamond, Turtle,
 * Netherite for armor) for the material line. This class supplies it
 * without re-running the resolution: it shares {@link
 * net.zaminmc.zea.rarity.MaterialTier}'s fingerprint and equipment-asset
 * lookups so there is one source of truth for "what is this made of".
 *
 * <p>Tool material is recovered from the same (durability,
 * enchantability) fingerprint {@code MaterialTier} uses — every vanilla
 * tool material has a unique pair, verified for 26.3 — so a named
 * material and a tier never disagree. Armor material is the {@link
 * Equippable} component's equipment-asset key, matched against the real
 * {@link EquipmentAssets} constants.
 *
 * <p>{@link #TOOL_MATERIALS} and {@link #ARMOR_MATERIALS} are kept in
 * tier order so {@link #ofTier(int)} round-trips: the tier a fingerprint
 * resolves to names the same material {@code MaterialTier} would.
 */
public final class MaterialInspector {
        /** Tool materials, in ascending {@link net.zaminmc.zea.rarity.MaterialTier} order. */
        private static final ToolMaterial[] TOOL_MATERIALS = {
                        ToolMaterial.WOOD, ToolMaterial.GOLD, ToolMaterial.STONE, ToolMaterial.COPPER,
                        ToolMaterial.IRON, ToolMaterial.DIAMOND, ToolMaterial.NETHERITE};

        /** Armor asset keys in ascending tier order (turtle sits with diamond). */
        private static final Map<ResourceKey<EquipmentAsset>, MaterialName> ARMOR_MATERIALS = Map.ofEntries(
                        Map.entry(EquipmentAssets.LEATHER, MaterialName.LEATHER),
                        Map.entry(EquipmentAssets.GOLD, MaterialName.GOLD),
                        Map.entry(EquipmentAssets.CHAINMAIL, MaterialName.CHAINMAIL),
                        Map.entry(EquipmentAssets.COPPER, MaterialName.COPPER),
                        Map.entry(EquipmentAssets.IRON, MaterialName.IRON),
                        Map.entry(EquipmentAssets.DIAMOND, MaterialName.DIAMOND),
                        Map.entry(EquipmentAssets.TURTLE_SCUTE, MaterialName.TURTLE),
                        Map.entry(EquipmentAssets.NETHERITE, MaterialName.NETHERITE));

        private MaterialInspector() {
        }

        /** The named tool material of {@code stack}, or {@code null} when not a tool. */
        public static MaterialName toolMaterial(ItemStack stack) {
                Integer damage = stack.get(DataComponents.MAX_DAMAGE);
                Integer enchantable = Optional.ofNullable(stack.get(DataComponents.ENCHANTABLE))
                                .map(component -> component.value())
                                .orElse(null);
                return toolMaterialFromComponents(damage, enchantable);
        }

        /** The named armor material of {@code stack}, or {@code null} when not armor. */
        public static MaterialName armorMaterial(ItemStack stack) {
                Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable == null || equippable.assetId().isEmpty()) {
                        return null;
                }
                return armorMaterialFromAsset(equippable.assetId().get());
        }

        /**
         * The named tool material for a (durability, enchantability) pair —
         * the pure lookup, testable against the real {@link ToolMaterial}
         * records without a stack.
         */
        static MaterialName toolMaterialFromComponents(Integer damage, Integer enchantable) {
                if (damage == null || enchantable == null) {
                        return null;
                }
                long fingerprint = ((long) damage << 32) | (enchantable & 0xFFFFFFFFL);
                for (ToolMaterial material : TOOL_MATERIALS) {
                        if (((long) material.durability() << 32 | (material.enchantmentValue() & 0xFFFFFFFFL)) == fingerprint) {
                                return ofTool(material);
                        }
                }
                return null;
        }

        /** The named armor material for an equipment-asset key — the pure lookup. */
        static MaterialName armorMaterialFromAsset(ResourceKey<EquipmentAsset> assetId) {
                return ARMOR_MATERIALS.get(assetId);
        }

        /** The tool material at {@code tier}, or {@code null} for out-of-range tiers. */
        static MaterialName ofTier(int tier) {
                if (tier < 0 || tier >= TOOL_MATERIALS.length) {
                        return null;
                }
                return ofTool(TOOL_MATERIALS[tier]);
        }

        private static MaterialName ofTool(ToolMaterial material) {
                if (material == ToolMaterial.WOOD) {
                        return MaterialName.WOOD;
                }
                if (material == ToolMaterial.GOLD) {
                        return MaterialName.GOLD;
                }
                if (material == ToolMaterial.STONE) {
                        return MaterialName.STONE;
                }
                if (material == ToolMaterial.COPPER) {
                        return MaterialName.COPPER;
                }
                if (material == ToolMaterial.IRON) {
                        return MaterialName.IRON;
                }
                if (material == ToolMaterial.DIAMOND) {
                        return MaterialName.DIAMOND;
                }
                if (material == ToolMaterial.NETHERITE) {
                        return MaterialName.NETHERITE;
                }
                return null;
        }

        /** The canonical material names the tooltip can show. */
        public enum MaterialName {
                WOOD, GOLD, STONE, COPPER, IRON, DIAMOND, TURTLE,
                LEATHER, CHAINMAIL, NETHERITE
        }
}
