package net.zaminmc.zea.itempresentation.recognition;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * The single authoritative classifier deciding whether an
 * {@link ItemStack} receives ZEA's RPG equipment presentation and, if
 * so, which {@link ItemCategory} it belongs to. Every tooltip entry
 * point routes through here; nothing else decides support.
 *
 * <p>The supported set is the spec's equipment allowlist and nothing
 * more — the four tool families, the six weapon families and the four
 * armor slots. Broad conditions (damageable, enchantable, components,
 * attribute modifiers, custom names, enchantments) are deliberately
 * NOT used: they would pull food, shields, elytra, enchanted books and
 * every renamed item into the presentation. Membership is resolved
 * from Minecraft 26.3's actual item definitions:
 *
 * <ol>
 *   <li><b>Dedicated item classes</b> — the few items vanilla still
 *       models with a real subclass rather than a data-driven
 *       definition: {@link BowItem}, {@link CrossbowItem}, {@link
 *       TridentItem}, {@link MaceItem}. These carry mechanics no tag
 *       captures (draw curves, smash attacks, throwing), so the class
 *       is the truthful signal.</li>
 *   <li><b>Item tags</b> — {@code #minecraft:swords}, {@code
 *       #minecraft:axes}, {@code #minecraft:pickaxes}, {@code
 *       #minecraft:shovels}, {@code #minecraft:hoes}, {@code
 *       #minecraft:spears} and the four {@code #minecraft:*_armor}
 *       tags. Datapacks and mods extend these tags, so any registered
 *       member is recognised without a hardcoded list.</li>
 * </ol>
 *
 * <p>The order is load-bearing. A mace is a melee weapon but is not in
 * {@code #minecraft:swords}; resolving the class first keeps it out of
 * the generic melee bucket. An axe is in both {@code #minecraft:axes}
 * and (as a tool) the mining path, and the tag resolves it correctly
 * without a special case. Armor is checked by slot so a chestplate is
 * never mistaken for a helmet.
 *
 * <p>Anything that matches none of the above is {@link
 * ItemCategory#OTHER} — blocks, ores, ingots, raw materials, food,
 * potions, enchanted books, shields, elytra, horse armor, music discs,
 * spawn eggs, utility items — and keeps its vanilla tooltip
 * completely untouched: no stat block, no rarity footer, no name
 * recoloring, no enchantment re-styling, no suppressed native lines.
 *
 * <p>The elytra sits in {@code #minecraft:chest_armor} but is not
 * armor — it contributes no armor points — so the chest tag alone
 * would misfile it as a chestplate. The armor-attribute test keeps it
 * (and any other chest-slot equippable without armor, from a mod) out
 * of the supported set; both resolve to {@link ItemCategory#OTHER}.
 */
public final class ItemClassifier {
        private ItemClassifier() {
        }

        /**
         * The category of {@code stack}, or {@link ItemCategory#OTHER}
         * for empty stacks and every item outside the equipment
         * allowlist.
         *
         * <p>The chest/elytra split is resolved here rather than in the
         * pure seam: it depends on the stack's {@link Equippable} and
         * attribute-modifier components, which the seam does not see.
         * A chest-slot item with no armor attribute is not armor.
         */
        public static ItemCategory classify(ItemStack stack) {
                if (stack == null || stack.isEmpty()) {
                        return ItemCategory.OTHER;
                }
                Item item = stack.getItem();
                ItemClass itemClass = itemClassOf(item);
                boolean chestArmor = item.builtInRegistryHolder().is(ItemTags.CHEST_ARMOR);
                // The elytra is a chest-slot equippable without armor
                // points — the only chest-tagged vanilla item that does
                // not contribute armor. Resolved here, not in the seam,
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
                                item.builtInRegistryHolder().is(ItemTags.FOOT_ARMOR));
        }

        /**
         * The pure classification seam: the decision from the boolean
         * tag-membership signals, the item's class, and the
         * chest-without-armor flag. Exposed so a bare test JVM — which
         * bootstraps the registries but never binds datapack tags — can
         * pin the precedence and the elytra rejection without
         * constructing real stacks.
         */
        static ItemCategory classifyFromSignals(ItemClass itemClass, boolean elytra,
                        boolean sword, boolean axe, boolean spear,
                        boolean pickaxe, boolean shovel, boolean hoe,
                        boolean headArmor, boolean chestArmor, boolean legArmor, boolean footArmor) {
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
                        // A chest-tagged item without armor points (the
                        // elytra, or a modded glider) is not supported
                        // equipment: no RPG presentation, vanilla
                        // tooltip untouched.
                        return elytra ? ItemCategory.OTHER : ItemCategory.CHESTPLATE;
                }
                if (legArmor) {
                        return ItemCategory.LEGGINGS;
                }
                if (footArmor) {
                        return ItemCategory.BOOTS;
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
                return ItemClass.PLAIN;
        }

        /** The dedicated-item-subclass signal used by the pure seam. */
        enum ItemClass {
                PLAIN, BOW, CROSSBOW, TRIDENT, MACE
        }
}
