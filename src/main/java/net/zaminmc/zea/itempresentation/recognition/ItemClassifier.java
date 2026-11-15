package net.zaminmc.zea.itempresentation.recognition;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Resolves the {@link ItemCategory} of an item from Minecraft 26.3's
 * actual item definitions.
 *
 * <p>Three signals are used, in this order of precedence:
 *
 * <ol>
 *   <li><b>Dedicated item classes</b> — the few items vanilla still
 *       models with a real subclass rather than a data-driven definition:
 *       {@link BowItem}, {@link CrossbowItem}, {@link TridentItem},
 *       {@link MaceItem}, {@link ShieldItem}, {@link ShearsItem}. These
 *       carry mechanics that no tag captures (draw curves, smash
 *       attacks, charge sounds, blocking), so the class is the truthful
 *       signal.</li>
 *   <li><b>Item tags</b> — {@code #minecraft:swords}, {@code
 *       #minecraft:axes}, {@code #minecraft:pickaxes}, {@code
 *       #minecraft:shovels}, {@code #minecraft:hoes}, {@code
 *       #minecraft:spears} and the four {@code #minecraft:*_armor} tags.
 *       Datapacks and mods extend these tags, so any registered member
 *       is recognised without a hardcoded list.</li>
 *   <li><b>Component presence</b> — food is whatever carries a
 *       {@link FoodProperties} component; an elytra is whatever is
 *       {@link Equippable} in the chest slot without armor points.
 *       Both are data-driven, so the test follows the definition rather
 *       than a name.</li>
 * </ol>
 *
 * <p>The order is load-bearing. A mace is a melee weapon but is not in
 * {@code #minecraft:swords}; resolving the class first keeps it out of
 * the generic melee bucket. An axe is in both {@code #minecraft:axes}
 * and (as a tool) the mining path, and the tag resolves it correctly
 * without a special case. Armor is checked by slot so a chestplate is
 * never mistaken for a helmet.
 *
 * <p>Anything that matches none of the above is {@link ItemCategory#OTHER}
 * and keeps its vanilla tooltip.
 */
public final class ItemClassifier {
        private ItemClassifier() {
        }

        /**
         * The category of {@code stack}, or {@link ItemCategory#OTHER}
         * for empty stacks and items ZEA has no specific presentation
         * for.
         *
         * <p>The chest/elytra split is resolved here rather than in the
         * pure seam: it depends on the stack's {@link Equippable} and
         * attribute-modifier components, which the seam does not see.
         * A chest-slot item with no armor attribute is an elytra.
         */
        public static ItemCategory classify(ItemStack stack) {
                if (stack == null || stack.isEmpty()) {
                        return ItemCategory.OTHER;
                }
                Item item = stack.getItem();
                ItemClass itemClass = itemClassOf(item);
                boolean chestArmor = item.builtInRegistryHolder().is(ItemTags.CHEST_ARMOR);
                // The elytra is a chest-slot equippable without armor
                // points — the only chest-tagged item that does not
                // contribute armor. Resolved here, not in the seam,
                // because it reads the stack's attribute-modifier
                // component.
                boolean elytra = chestArmor && !hasArmorAttribute(stack);
                return classifyFromSignals(
                                itemClass,
                                elytra,
                                item.builtInRegistryHolder().is(ItemTags.SWORDS),
                                item.builtInRegistryHolder().is(ItemTags.AXES),
                                item.builtInRegistryHolder().is(ItemTags.SPEARS),
                                item.builtInRegistryHolder().is(ItemTags.PICKAXES),
                                item.builtInRegistryHolder().is(ItemTags.SHOVELS),
                                item.builtInRegistryHolder().is(ItemTags.HOES),
                                item.builtInRegistryHolder().is(ItemTags.HEAD_ARMOR),
                                chestArmor,
                                item.builtInRegistryHolder().is(ItemTags.LEG_ARMOR),
                                item.builtInRegistryHolder().is(ItemTags.FOOT_ARMOR),
                                stack.has(DataComponents.FOOD));
        }

        /**
         * The pure classification seam: the decision from the boolean
         * tag-membership signals, the item's class, and the food
         * component flag. Exposed so a bare test JVM — which bootstraps
         * the registries but never binds datapack tags — can pin the
         * precedence and the chest/elytra split without constructing
         * real stacks.
         */
        static ItemCategory classifyFromSignals(ItemClass itemClass, boolean elytra,
                        boolean sword, boolean axe, boolean spear,
                        boolean pickaxe, boolean shovel, boolean hoe,
                        boolean headArmor, boolean chestArmor, boolean legArmor, boolean footArmor,
                        boolean food) {
                if (itemClass == ItemClass.BOW) {
                        return ItemCategory.BOW;
                }
                if (itemClass == ItemClass.CROSSBOW) {
                        return ItemCategory.CROSSBOW;
                }
                if (itemClass == ItemClass.TRIDENT) {
                        return ItemCategory.TRIDENT;
                }
                if (itemClass == ItemClass.MACE) {
                        return ItemCategory.MACE;
                }
                if (itemClass == ItemClass.SHIELD) {
                        return ItemCategory.SHIELD;
                }
                if (itemClass == ItemClass.SHEARS) {
                        return ItemCategory.SHEARS;
                }
                if (sword) {
                        return ItemCategory.SWORD;
                }
                if (axe) {
                        return ItemCategory.AXE;
                }
                if (spear) {
                        return ItemCategory.SPEAR;
                }
                if (pickaxe) {
                        return ItemCategory.PICKAXE;
                }
                if (shovel) {
                        return ItemCategory.SHOVEL;
                }
                if (hoe) {
                        return ItemCategory.HOE;
                }
                if (headArmor) {
                        return ItemCategory.HELMET;
                }
                if (chestArmor) {
                        return elytra ? ItemCategory.ELYTRA : ItemCategory.CHESTPLATE;
                }
                if (legArmor) {
                        return ItemCategory.LEGGINGS;
                }
                if (footArmor) {
                        return ItemCategory.BOOTS;
                }
                if (food) {
                        return ItemCategory.FOOD;
                }
                return ItemCategory.OTHER;
        }

        /**
         * Whether the stack carries an armor attribute modifier — the
         * line that separates a chestplate from an elytra. Both sit in
         * {@link EquipmentSlot#CHEST}; only a chestplate contributes
         * armor points.
         */
        private static boolean hasArmorAttribute(ItemStack stack) {
                var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (modifiers == null) {
                        return false;
                }
                boolean[] found = {false};
                modifiers.forEach(EquipmentSlot.CHEST, (attribute, modifier) -> {
                        if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) {
                                found[0] = true;
                        }
                });
                return found[0];
        }

        /**
         * Resolves the dedicated-item-subclass signal. The elytra is not
         * a dedicated class — it is a chest-slot equippable without armor
         * points, so {@link #classify} separates it from a chestplate by
         * the armor-attribute test and surfaces the result through the
         * {@code elytra} boolean.
         */
        private static ItemClass itemClassOf(Item item) {
                if (item instanceof BowItem) {
                        return ItemClass.BOW;
                }
                if (item instanceof CrossbowItem) {
                        return ItemClass.CROSSBOW;
                }
                if (item instanceof TridentItem) {
                        return ItemClass.TRIDENT;
                }
                if (item instanceof MaceItem) {
                        return ItemClass.MACE;
                }
                if (item instanceof ShieldItem) {
                        return ItemClass.SHIELD;
                }
                if (item instanceof ShearsItem) {
                        return ItemClass.SHEARS;
                }
                return ItemClass.PLAIN;
        }

        /** The dedicated-item-subclass signal used by the pure seam. */
        enum ItemClass {
                PLAIN, BOW, CROSSBOW, TRIDENT, MACE, SHIELD, SHEARS
        }
}
