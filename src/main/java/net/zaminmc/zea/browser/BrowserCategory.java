package net.zaminmc.zea.browser;

import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import net.zaminmc.zea.itempresentation.recognition.ItemClassifier;

import java.util.Locale;

/**
 * The browser's shop-category buckets — the four tile groups the sort
 * bar's right-side filters show: weapons and tools, armor, general
 * goods, and the server's own items.
 *
 * <p>Everything here is live market data — the exact item variants the
 * shops actually offer — so the bucket resolves from the variant's
 * fingerprint, never from a curated item database:
 *
 * <ul>
 *   <li><b>EARTH</b> — the server's custom items (EarthMC's moon rocks,
 *       oxygen tanks, rocket fuel and the rest). The server marks them
 *       inside the item's own custom data, which travels in the
 *       fingerprint's component patch; a token whose patch carries the
 *       {@code earthmc} namespace is a server item. The check runs on
 *       the raw token, so the bucket survives even a patch the client
 *       cannot decode.</li>
 *   <li><b>WEAPONS / ARMOR</b> — resolved through ZEA's existing item
 *       recognition ({@link ItemClassifier}, the same engine the RPG
 *       tooltip uses): the ten equipment families are weapons-or-tools,
 *       the four worn slots are armor.</li>
 *   <li><b>GENERAL</b> — everything else: ores, ingots, food, golden
 *       apples, blocks, potions, books — anything that fits neither of
 *       the equipment buckets nor the server's own.</li>
 * </ul>
 *
 * <p>The classification runs when the browser's results rebuild (a
 * market revision, a query, an order or a filter change) — never per
 * frame — and it shares {@link VariantStacks}' resolved-stack cache,
 * so a tile's icon and its bucket come from one resolution.
 */
public enum BrowserCategory {
        /** Swords, axes, pickaxes, shovels, hoes, tridents, bows, crossbows, maces, spears. */
        WEAPONS,
        /** Helmets, chestplates, leggings, boots — the four worn slots. */
        ARMOR,
        /** Ores, food, blocks, materials, utility — the everything-else bucket. */
        GENERAL,
        /** The server's own items — EarthMC's moon, oxygen and rocket goods. */
        EARTH;

        /** The marker the server embeds in a custom item's own data. */
        private static final String SERVER_MARKER = "earthmc";

        /**
         * Whether the token itself carries the server's marker — an
         * {@code earthmc} item id or an {@code earthmc} namespace inside
         * the component patch. The check is a raw string scan, so it
         * never resolves a stack and never needs bound components; a
         * patch the client cannot decode still marks its item.
         */
        public static boolean isServerItem(String token) {
                return token != null && token.toLowerCase(Locale.ROOT).contains(SERVER_MARKER);
        }

        /** The bucket an item category belongs to. */
        public static BrowserCategory fromCategory(ItemCategory category) {
                return switch (category) {
                        case SWORD, AXE, SPEAR, MACE, BOW, CROSSBOW, TRIDENT,
                                        PICKAXE, SHOVEL, HOE -> WEAPONS;
                        case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> ARMOR;
                        default -> GENERAL;
                };
        }

        /** The bucket a fingerprint token belongs to. */
        public static BrowserCategory of(String token) {
                if (isServerItem(token)) {
                        return EARTH;
                }
                VariantStacks.Resolved resolved = VariantStacks.of(token);
                ItemCategory category = resolved.stack().isEmpty()
                                ? ItemCategory.OTHER
                                : ItemClassifier.classify(resolved.stack());
                return fromCategory(category);
        }
}
