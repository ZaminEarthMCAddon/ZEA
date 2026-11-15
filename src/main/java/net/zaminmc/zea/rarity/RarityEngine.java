package net.zaminmc.zea.rarity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * The rarity engine — the single facade rendering and tooltips ask. Pure
 * calculation lives in {@link RarityCalculator} (material + enchantment
 * model), {@link MaterialTier} (what an item is made of), {@link
 * EnchantBudget} (what progression is possible for its class) and
 * {@link MissingUpgrades} (meaningful remaining upgrades); this class
 * wires them to the live config and the level's registry, and owns the
 * caches.
 *
 * <p><b>Caching.</b> Rarity is cached per (item, enchantments) component
 * pair — the component instance is stable while a stack is unchanged, so
 * hovering or re-rendering the same slot is a map hit, and editing the
 * item on an anvil lands a new component and a fresh computation. The
 * cache is bounded; a flush costs one re-computation per visible item.
 * Enchantment budgets live in {@link EnchantBudget} and are cleared when
 * a level closes, since the enchantment registry is data-driven per
 * world.
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

        /** The per-(item, enchantments) rarity cache. */
        private static final Map<RarityKey, ItemRarity> RARITY_CACHE = new HashMap<>();

        /** Cache key: the item class plus the exact enchantment component. */
        private record RarityKey(net.minecraft.world.item.Item item, ItemEnchantments enchantments) {
        }

        private RarityEngine() {
        }

        /**
         * The rarity of {@code stack}, or {@code null} when the system is
         * off, the item is not a supported tool or armor piece, or the
         * level (and with it the enchantment registry) is not available.
         */
        public static ItemRarity rarityOf(ItemStack stack) {
                if (!enabled()) {
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
                double fill = fillOf(enchantments, stack);
                EnchantBudget budget = EnchantBudget.of(stack.getItem(), registryLookup());
                double maxFill = budget.maxFill();
                ItemRarity rarity = RarityCalculator.calculate(material.tier(), fill, maxFill);
                if (RARITY_CACHE.size() >= RARITY_CACHE_BOUND) {
                        RARITY_CACHE.clear();
                }
                RARITY_CACHE.put(key, rarity);
                return rarity;
        }

        /**
         * The translucent slot-tint color for {@code stack} in the
         * rarity's color, or 0 when nothing should be drawn. The alpha
         * comes from the tint-intensity setting; the tint is a background
         * fill that the item texture, count, durability bar and glint all
         * render on top of.
         */
        public static int slotTintArgb(ItemStack stack) {
                ZeaConfig cfg = ZeaClient.config();
                if (cfg == null || !cfg.rarityEnabled() || !cfg.raritySlotTintEnabled()) {
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

        /** Whether the rarity system (master switch) is on. */
        private static boolean enabled() {
                ZeaConfig cfg = ZeaClient.config();
                return cfg != null && cfg.rarityEnabled();
        }

        /**
         * The summed {@code level/maxLevel} fill of the stack's
         * compatible, non-neutral enchantments. Unsupported or neutral
         * entries contribute nothing — a stack can carry an enchantment
         * its class does not accept (commands), and that must not score.
         *
         * <p><b>Client independence.</b> The sum iterates the budget's
         * canonically ordered list (registry-key order, see {@link
         * EnchantBudget}) and reads each level through {@code getLevel}
         * — never the component's own entry set. {@code ItemEnchantments}
         * is backed by an identity-hashed open-addressing map, so its
         * iteration order differs between JVMs; summing over it could
         * round the floating-point total differently on different
         * clients. Summing over the sorted budget list makes the fill —
         * and with it the rarity — bit-for-bit identical for every
         * player seeing the same item in the same world.
         */
        private static double fillOf(ItemEnchantments enchantments, ItemStack stack) {
                if (enchantments.isEmpty()) {
                        return 0.0;
                }
                EnchantBudget budget = EnchantBudget.of(stack.getItem(), registryLookup());
                double fill = 0.0;
                for (EnchantBudget.Compatible compatible : budget.compatible()) {
                        int level = enchantments.getLevel(compatible.enchantment());
                        if (level > 0 && !EnchantPolicy.isNeutral(compatible.enchantment())) {
                                fill += compatible.fillAt(level);
                        }
                }
                return fill;
        }

        /**
         * The level's registry access, or {@code null} outside a level —
         * the enchantment registry is data-driven, so no level means no
         * enchantment data and no rarity.
         */
        private static HolderLookup.Provider registryLookup() {
                Minecraft minecraft = Minecraft.getInstance();
                return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
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
