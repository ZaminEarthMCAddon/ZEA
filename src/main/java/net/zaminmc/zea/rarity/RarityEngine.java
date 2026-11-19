package net.zaminmc.zea.rarity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import net.zaminmc.zea.itempresentation.recognition.ItemClassifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The rarity engine — the single facade rendering and tooltips ask. The
 * rarity itself is a table lookup: {@link RarityTables} maps out every
 * reachable (family, material tier, enchantment state) outcome offline
 * from the vanilla 26.3 data, and this class wires the live stack to
 * those tables — the family from {@link net.zaminmc.zea.itempresentation.recognition.ItemClassifier},
 * the material tier from {@link MaterialTier}, the enchantment levels
 * from the stack's own component — and owns the cache. Pure calculation
 * support lives beside it: {@link RarityCalculator} (the readable
 * reference rule the tables were generated from), {@link MaterialTier}
 * (what an item is made of) and {@link MissingUpgrades} (meaningful
 * remaining upgrades).
 *
 * <p><b>Why a lookup and not a formula.</b> The previous engine derived
 * its enchantment budget from the live registry, and EarthMC syncs
 * custom enchantments into that registry — every budget computed on
 * such a world wobbled with whatever the server shipped that day, and
 * the floating-point progress thresholds turned the wobble into
 * unexpected tiers. The mapped engine reads only the pinned vanilla
 * universe: a server's custom enchantments contribute nothing to the
 * fill numerator, an enchantment the family cannot carry was never in
 * the table, and the outcome for every legitimate state was enumerated
 * and frozen — nothing is calculated at hover time that can surprise.
 *
 * <p><b>Caching.</b> Rarity is cached per (item, enchantments) component
 * pair — the component instance is stable while a stack is unchanged, so
 * hovering or re-rendering the same slot is a map hit, and editing the
 * item on an anvil lands a new component and a fresh computation. The
 * cache is bounded; a flush costs one re-computation per visible item.
 * Unlike the formula it replaced, the lookup needs no level and no
 * registry — it answers identically in and out of a world.
 *
 * <p>Everything here runs on the client thread — rendering and tooltip
 * extraction do — so plain maps suffice.
 */
public final class RarityEngine {
        /** The rarity cache bound, after which the whole map is dropped. */
        private static final int RARITY_CACHE_BOUND = 1024;

        /**
         * Chroma cycle length in milliseconds — one full hue rotation.
         *
         * <p>Eight seconds is deliberately slow: a fast cycle makes the
         * text strobe and is hard to read, while a near-static one reads
         * as a flat color. NEU's chroma sits in this band, and the value
         * here is the slow end of it so a fully-maxed enchantment reads
         * as a gentle drift rather than a flicker.
         */
        public static final long CHROMA_PERIOD_MILLIS = 8_000L;

        /**
         * The static color a maxed enchantment wears when the chroma
         * animation is disabled — the gold ({@code &6}) the tier has
         * always implied, so turning the animation off quiets the
         * tooltip without hiding which enchantments are at their cap.
         */
        public static final int STATIC_MAXED_COLOR = 0xFFAA00;

        /** The per-(item, enchantments) rarity cache. */
        private static final Map<RarityKey, ItemRarity> RARITY_CACHE = new HashMap<>();

        /** Cache key: the item class plus the exact enchantment component. */
        private record RarityKey(net.minecraft.world.item.Item item, ItemEnchantments enchantments) {
        }

        private RarityEngine() {
        }

        /**
         * The rarity of {@code stack}, or {@code null} when the system is
         * off or the item is outside the rated families. The outcome is a
         * table lookup — the mapped {@link RarityTables} universe, read at
         * the stack's material tier and its enchantments' fill numerator —
         * and needs no level and no registry: a server's custom
         * enchantments never enter the tables, so the outcome cannot
         * wobble with them.
         */
        public static ItemRarity rarityOf(ItemStack stack) {
                if (!isSystemEnabled()) {
                        return null;
                }
                ItemCategory category = ItemClassifier.classify(stack);
                if (category == ItemCategory.OTHER) {
                        return null;
                }
                MaterialTier.Evaluation material = MaterialTier.evaluate(stack);
                if (!material.supported()) {
                        return null;
                }
                ItemEnchantments enchantments = stack.getOrDefault(
                                                net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                                                ItemEnchantments.EMPTY);
                RarityKey key = new RarityKey(stack.getItem(), enchantments);
                ItemRarity cached = RARITY_CACHE.get(key);
                if (cached != null) {
                        return cached;
                }
                ItemRarity rarity = RarityTables.rarityFor(category, material.tier(),
                                levelsOf(enchantments));
                if (RARITY_CACHE.size() >= RARITY_CACHE_BOUND) {
                        RARITY_CACHE.clear();
                }
                RARITY_CACHE.put(key, rarity);
                return rarity;
        }

        /**
         * The stack's present enchantment levels as registry paths — the
         * shape the mapped tables read. The paths are the enchantments'
         * own registry keys, so resource packs and translations cannot
         * change classification.
         */
        private static Map<String, Integer> levelsOf(ItemEnchantments enchantments) {
                Map<String, Integer> levels = new HashMap<>();
                for (var holder : enchantments.keySet()) {
                        holder.unwrapKey().ifPresent(key ->
                                        levels.put(key.identifier().getPath(), enchantments.getLevel(holder)));
                }
                return levels;
        }

        /**
         * The translucent slot-tint color for {@code stack} in the
         * rarity's color, or 0 when nothing should be drawn. The alpha
         * comes from the tint-intensity setting; the tint is a background
         * fill that the item texture, count, durability bar and glint all
         * render on top of.
         */
        public static int slotTintArgb(ItemStack stack) {
                return tintArgb(stack, cfg -> cfg.raritySlotTintEnabled());
        }

        /**
         * The same wash for the HUD hotbar (and off-hand) slots — the
         * dev.36 live request: rarity color in the hotbar, not only
         * inside container screens. Gated by the hotbar's own switch.
         */
        public static int hotbarTintArgb(ItemStack stack) {
                return tintArgb(stack, cfg -> cfg.rarityHotbarTintEnabled());
        }

        private static int tintArgb(ItemStack stack, java.util.function.Predicate<ZeaConfig> enabled) {
                ZeaConfig cfg = ZeaClient.config();
                if (cfg == null || !enabled.test(cfg)) {
                        return 0;
                }
                ItemRarity rarity = rarityOf(stack);
                if (rarity == null) {
                        return 0;
                }
                int alpha = Math.round(255 * cfg.rarityTintOpacity() / 100.0f);
                return (alpha << 24) | rarity.rgb();
        }

        /** Drops the rarity cache; budgets clear themselves with the level. */
        public static void invalidateCaches() {
                RARITY_CACHE.clear();
        }

        /**
         * Whether the rarity system (master switch) is on. The engine's
         * callers — sort orders among them — check this before paying for
         * stack resolution: when the system is off nothing is ever rated,
         * so resolving a stack to ask would be pure waste.
         */
        public static boolean isSystemEnabled() {
                ZeaConfig cfg = ZeaClient.config();
                return cfg != null && cfg.rarityEnabled();
        }

        /**
         * The chroma color for {@code millis} — an animated hue rotation
         * in the spirit of NEU's chroma text, applied per line rather
         * than per glyph to keep tooltip rendering cheap.
         *
         * <p>The hue is a pure function of {@code millis} modulo the
         * period, so the same wall-clock instant always yields the same
         * color. That makes the animation stable across tooltip reopens —
         * closing and reopening a tooltip never resets the hue, and two
         * chroma lines on the same item stay in phase. High saturation
         * and full brightness keep the text readable on any background.
         *
         * <p>The HSV→RGB conversion is inlined here rather than borrowed
         * from {@code java.awt.Color}: that pulls the whole AWT stack
         * into a client-only tooltip path for one three-line formula.
         */
        public static int chromaColor(long millis) {
                float hue = (millis % CHROMA_PERIOD_MILLIS) / (float) CHROMA_PERIOD_MILLIS;
                return 0xFF000000 | hsvToRgb(hue, 0.85f, 1.0f);
        }

        /**
         * The sRGB triple of an HSV color, as a packed 0xRRGGBB int.
         * Matches {@code java.awt.Color.HSBtoRGB} bit-for-bit on the same
         * inputs without paying for the AWT dependency.
         */
        private static int hsvToRgb(float hue, float saturation, float value) {
                float h = (hue - (float) Math.floor(hue)) * 6.0f;
                int i = (int) h;
                float f = h - i;
                float p = value * (1.0f - saturation);
                float q = value * (1.0f - saturation * f);
                float t = value * (1.0f - saturation * (1.0f - f));
                float r;
                float g;
                float b;
                switch (i) {
                        case 0 -> { r = value; g = t; b = p; }
                        case 1 -> { r = q; g = value; b = p; }
                        case 2 -> { r = p; g = value; b = t; }
                        case 3 -> { r = p; g = q; b = value; }
                        case 4 -> { r = t; g = p; b = value; }
                        default -> { r = value; g = p; b = q; }
                }
                return ((int) (r * 255.0f + 0.5f) << 16)
                                | ((int) (g * 255.0f + 0.5f) << 8)
                                | (int) (b * 255.0f + 0.5f);
        }

        /** The color of {@code rarity} as a tooltip {@code TextColor}, cached per rarity. */
        public static net.minecraft.network.chat.TextColor textColor(ItemRarity rarity) {
                return net.minecraft.network.chat.TextColor.fromRgb(rarity.rgb());
        }
}
