package net.zaminmc.zea.itempresentation.calculation;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Resolves a named enchantment's level from a snapshot by its registry
 * key path, matching the path-based identification {@link
 * net.zaminmc.zea.rarity.EnchantPolicy} uses — immune to resource-pack
 * display rewrites and translation.
 *
 * <p>A single shared seam so every calculator that needs "the level of
 * enchantment X on this item" asks one question and the path-to-name
 * mapping lives in one place.
 */
final class EnchantmentLevel {
        private EnchantmentLevel() {
        }

        /** The level of the Efficiency enchantment on the snapshot, or 0. */
        static int efficiency(ItemSnapshot snapshot) {
                return byPath(snapshot, "efficiency");
        }

        /** The level of the Unbreaking enchantment on the snapshot, or 0. */
        static int unbreaking(ItemSnapshot snapshot) {
                return byPath(snapshot, "unbreaking");
        }

        /** The level of the Power enchantment on the snapshot, or 0. */
        static int power(ItemSnapshot snapshot) {
                return byPath(snapshot, "power");
        }

        /** The level of the Density enchantment on the snapshot, or 0. */
        static int density(ItemSnapshot snapshot) {
                return byPath(snapshot, "density");
        }

        /** The level of the Lunge enchantment on the snapshot, or 0. */
        static int lunge(ItemSnapshot snapshot) {
                return byPath(snapshot, "lunge");
        }

        /**
         * The level of the enchantment whose registry-key path ends with
         * {@code path}, or 0. Public so calculators can resolve
         * enchantments not covered by a dedicated accessor (Multishot,
         * Piercing) without duplicating the path lookup.
         */
        static int byPath(ItemSnapshot snapshot, String path) {
                for (ItemSnapshot.EnchantmentEntry entry : snapshot.enchantmentEntries()) {
                        Holder<Enchantment> holder = entry.enchantment();
                        String entryPath = holder.unwrapKey()
                                        .map(key -> key.identifier().getPath())
                                        .orElse("");
                        if (entryPath.equals(path)) {
                                return entry.level();
                        }
                }
                return 0;
        }
}
