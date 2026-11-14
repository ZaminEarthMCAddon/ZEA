package net.zaminmc.zea.rarity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a supported item is made of, resolved from Minecraft 26.3's actual
 * registries — never from item-name substrings.
 *
 * <p>Two families of supported items exist, with separate ladders because
 * their materials differ:
 *
 * <ul>
 *   <li><b>Tools</b> (swords, pickaxes, axes, shovels, hoes — the vanilla
 *       {@code #minecraft:swords}/{@code #minecraft:axes}/... item tags):
 *       the material is recovered by matching the item's own component
 *       values ({@code max_damage}, {@code enchantable}) against the
 *       public {@link ToolMaterial} records. Every vanilla tool material
 *       has a unique (durability, enchantability) pair — verified for the
 *       26.3 values — so the pair identifies the record exactly. A
 *       modified or unknown fingerprint falls back to tier 0, never to a
 *       wrong material.</li>
 *   <li><b>Armor</b> (the four {@code #minecraft:*_armor} tags): the
 *       material is the {@link Equippable} component's equipment-asset
 *       key, matched against the {@link EquipmentAssets} constants —
 *       registry identity, not a name check. Turtle armor counts as
 *       diamond tier.</li>
 * </ul>
 *
 * <p>Tiers run 0..6 on both ladders. The user-facing meaning of a tier
 * (material rarity, ceiling) lives in {@link RarityCalculator}; this class
 * only answers "what is it made of".
 */
public final class MaterialTier {
        /** Tool progression, lowest to highest. Gold is durable-material-wise below stone. */
        private static final ToolMaterial[] TOOL_LADDER = {
                        ToolMaterial.WOOD, ToolMaterial.GOLD, ToolMaterial.STONE, ToolMaterial.COPPER,
                        ToolMaterial.IRON, ToolMaterial.DIAMOND, ToolMaterial.NETHERITE};

        /** Armor progression, lowest to highest; turtle sits with diamond. */
        private static final Map<ResourceKey<EquipmentAsset>, Integer> ARMOR_TIERS = Map.ofEntries(
                        Map.entry(EquipmentAssets.LEATHER, 0),
                        Map.entry(EquipmentAssets.GOLD, 1),
                        Map.entry(EquipmentAssets.CHAINMAIL, 2),
                        Map.entry(EquipmentAssets.COPPER, 3),
                        Map.entry(EquipmentAssets.IRON, 4),
                        Map.entry(EquipmentAssets.DIAMOND, 5),
                        Map.entry(EquipmentAssets.TURTLE_SCUTE, 5),
                        Map.entry(EquipmentAssets.NETHERITE, 6));

        /**
         * (durability, enchantability) → ToolMaterial, built from the
         * actual record values so the fingerprint can never drift from the
         * data vanilla ships.
         */
        private static final Map<Long, ToolMaterial> TOOL_FINGERPRINTS = buildFingerprints();

        /** What kind of item this is, deciding which ladder applies. */
        public enum Kind {
                TOOL,
                ARMOR,
                /** Not a supported tool or armor piece: rarity never applies. */
                UNSUPPORTED
        }

        /** The evaluation result: kind and, for supported items, the material tier 0..6. */
        public record Evaluation(Kind kind, int tier) {
                public boolean supported() {
                        return kind != Kind.UNSUPPORTED;
                }
        }

        private MaterialTier() {
        }

        /**
         * Resolves the material of {@code stack}. Unsupported items get
         * {@link Kind#UNSUPPORTED}; supported items whose material cannot
         * be identified (modded fingerprints, edited components) evaluate
         * to tier 0 rather than to a wrong material.
         */
        public static Evaluation evaluate(ItemStack stack) {
                if (stack.isEmpty() || stack.getItem() == null) {
                        return new Evaluation(Kind.UNSUPPORTED, 0);
                }
                Item item = stack.getItem();
                var holder = item.builtInRegistryHolder();
                if (holder.is(ItemTags.SWORDS) || holder.is(ItemTags.PICKAXES) || holder.is(ItemTags.AXES)
                                || holder.is(ItemTags.SHOVELS) || holder.is(ItemTags.HOES)) {
                        return new Evaluation(Kind.TOOL, toolTier(stack));
                }
                if (holder.is(ItemTags.HEAD_ARMOR) || holder.is(ItemTags.CHEST_ARMOR)
                                || holder.is(ItemTags.LEG_ARMOR) || holder.is(ItemTags.FOOT_ARMOR)) {
                        return new Evaluation(Kind.ARMOR, armorTier(stack));
                }
                return new Evaluation(Kind.UNSUPPORTED, 0);
        }

        /**
         * The tool ladder position of the item's material, or 0 when
         * unknown. Delegates to the pure fingerprint lookup; the stack
         * wrapper exists so rendering asks one question.
         */
        static int toolTier(ItemStack stack) {
                // MAX_DAMAGE and ENCHANTABLE are exactly what the material
                // records write into tool properties, so an unmodified
                // vanilla tool always matches its own material record.
                Integer damage = stack.get(DataComponents.MAX_DAMAGE);
                Integer enchantable = Optional.ofNullable(stack.get(DataComponents.ENCHANTABLE))
                                .map(enchantable1 -> enchantable1.value())
                                .orElse(null);
                return toolTierFromComponents(damage, enchantable);
        }

        /**
         * The tool tier for a (durability, enchantability) pair — the
         * pure lookup, so the tests can pin the fingerprint against the
         * real material records without constructing stacks.
         */
        static int toolTierFromComponents(Integer damage, Integer enchantable) {
                if (damage == null || enchantable == null) {
                        return 0;
                }
                ToolMaterial material = TOOL_FINGERPRINTS.get(fingerprint(damage, enchantable));
                if (material == null) {
                        return 0;
                }
                for (int tier = 0; tier < TOOL_LADDER.length; tier++) {
                        if (TOOL_LADDER[tier] == material) {
                                return tier;
                        }
                }
                return 0;
        }

        /** The armor ladder position of the item's equipment asset, or 0 when unknown. */
        static int armorTier(ItemStack stack) {
                Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable == null || equippable.assetId().isEmpty()) {
                        return 0;
                }
                return armorTierFromAsset(equippable.assetId().get());
        }

        /**
         * The armor tier for an equipment-asset key — the pure lookup,
         * testable against the real {@link EquipmentAssets} constants.
         */
        static int armorTierFromAsset(ResourceKey<EquipmentAsset> assetId) {
                return ARMOR_TIERS.getOrDefault(assetId, 0);
        }

        private static Long fingerprint(int durability, int enchantability) {
                return ((long) durability << 32) | (enchantability & 0xFFFFFFFFL);
        }

        private static Map<Long, ToolMaterial> buildFingerprints() {
                Map<Long, ToolMaterial> fingerprints = new HashMap<>();
                for (ToolMaterial material : TOOL_LADDER) {
                        fingerprints.put(fingerprint(material.durability(), material.enchantmentValue()), material);
                }
                return Map.copyOf(fingerprints);
        }
}
