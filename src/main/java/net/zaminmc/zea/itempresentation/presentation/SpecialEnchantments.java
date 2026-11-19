package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import net.zaminmc.zea.rarity.ItemRarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The special-enchantment indicators: the compact symbol row under the
 * stat block, one glyph per unique-enchantment property, in the spirit
 * of Hypixel SkyBlock's historical gemstone indicators.
 *
 * <p>Special enchantments are not statistics. Infinity, Mending, Silk
 * Touch, Unbreaking, Fire Aspect and friends describe what the item
 * <em>is</em> when it carries them — never a number — so they present
 * as their symbol, not as a stat row, and never as raw enchantment
 * levels. Efficiency and Fortune are deliberately outside this
 * registry: they are represented through the statistics they derive
 * (Mining Speed, Mining Fortune) and the enchantment section.
 *
 * <p>Every candidate carries one of three states:
 *
 * <ul>
 *   <li>{@code APPLIED} — the enchantment is on the item; its glyph
 *       wears its own meaningful color and its brackets the rarity
 *       color its applied level maps to.</li>
 *   <li>{@code MISSING} — the item supports it, nothing conflicts, and
 *       the enchantment is absent; its symbol shows in neutral gray —
 *       a potential upgrade, never a defect.</li>
 *   <li>{@code NOT_APPLICABLE} — the item does not support it, or a
 *       present enchantment conflicts with it (Infinity against
 *       Mending, Silk Touch against Fortune, Riptide against its
 *       siblings); its symbol does not show at all.</li>
 * </ul>
 *
 * <p><b>Colors.</b> Every part carries a deliberate color. The
 * <em>brackets</em> are the progression signal: an applied indicator's
 * brackets reuse {@link ItemRarity}'s existing hierarchy — the same
 * tiers and RGB values the rarity footer and the tier ladder already
 * show — through one general rule: the maximum legitimate level always
 * maps to Legendary gold, the lowest level to Common, and the levels
 * between spread proportionally across the tiers in between (nearest
 * tier, halves up). A single-level enchantment's only level is its
 * maximum, so its brackets show gold. The spec's own example —
 * Unbreaking, maximum III — lands on Common, Epic, Legendary, exactly
 * what the rule produces. Missing indicators take the legacy neutral
 * gray.
 *
 * <p>The <em>glyph</em> never borrows the rarity color: every registry
 * entry carries its own meaningful color, chosen for what the
 * enchantment does — Unbreaking's shield wears shield blue, Mending's
 * recycle loops XP green, Fire Aspect burns flame red, Riptide dives
 * tide aqua, Frost Walker freezes ice pale. The colors are fixed
 * constants, not a generated palette, and each item class's profile
 * row keeps its members visually distinct (the bow row's violet, green,
 * blue, ember and amber never collide). A missing glyph stays gray so
 * the applied/missing distinction survives — the meaningful color is
 * what the enchantment <em>is</em>, the gray is the upgrade it could
 * still become. An indicator whose registry path is unknown to this
 * build falls back to the legacy single-color rendering.
 *
 * <p>Applicability and conflicts come from the game's own enchantment
 * definitions — {@link Enchantment#isSupportedItem} and {@link
 * Enchantment#areCompatible}, the same mechanisms the rarity budget
 * uses — so the exclusive-set tags vanilla ships decide the conflicts,
 * not a hand-written list. The candidate profiles only decide which
 * enchantments are worth a symbol per item class; a candidate the item
 * class cannot carry falls out as not applicable and is hidden.
 *
 * <p><b>Fonts.</b> Minecraft 26.3 dropped unifont, so the default font
 * defines only a curated codepoint set. Every registry symbol was
 * verified against the shipped font assets: ten of them render from
 * vanilla ({@code ∞ 🛡 🔥 🏹 ☄ ⚡ 🔱 ⚓ ❄}), the other eight
 * ({@code ♻ 🔮 💥 🎯 💨 🫧 🏃 👣}) have no vanilla glyph and render
 * from ZEA's own bitmap font, {@code zea:enchant_indicators} — the
 * 8×8 doodle-style texture {@code gen_enchant_font.py} generates. The
 * split is recorded per registry entry, so a codepoint is never routed
 * to a font that lacks it and the missing-glyph box cannot appear.
 *
 * <p>The row omits its heading: the symbols are compact enough to
 * explain themselves through their hover text, which carries the
 * enchantment's name, its level when applied, and a one-line effect
 * summary. When no candidate ends up applied or missing, the entire
 * section — and its spacing — is omitted.
 */
public final class SpecialEnchantments {
        /** ZEA's bitmap font for the glyphs vanilla's default font does not define. */
        static final FontDescription FONT = new FontDescription.Resource(
                        Identifier.fromNamespaceAndPath("zea", "enchant_indicators"));

        /**
         * The indicator registry: registry-key path to its symbol, the
         * font that renders the symbol, and its one-line effect summary.
         * Riptide and Multishot intentionally share the trident-glyph
         * symbol; their registry paths — and with them their identities,
         * states and conflicts — stay distinct. Symbols carry no
         * variation selector: the glyphs live on the base codepoints,
         * and a bare {@code U+FE0F} has no glyph of its own.
         */
        private static final Map<String, Entry> REGISTRY = buildRegistry();

        /** Where a symbol's glyph comes from — the verified 26.3 split. */
        enum FontSource {
                /** The vanilla default font defines the codepoint. */
                VANILLA,
                /** ZEA's bitmap font defines the codepoint. */
                ZEA
        }

        private static Map<String, Entry> buildRegistry() {
                Map<String, Entry> registry = new LinkedHashMap<>();
                register(registry, "infinity", "∞", FontSource.VANILLA, "Arrows are not consumed",
                                0xAA55FF, "violet — the unending quiver");
                register(registry, "mending", "♻", FontSource.ZEA, "Repairs with gathered XP",
                                0x55FF55, "XP green");
                register(registry, "silk_touch", "🔮", FontSource.ZEA, "Blocks drop themselves",
                                0xFF55FF, "arcane magenta");
                register(registry, "unbreaking", "🛡", FontSource.VANILLA, "Durability loss often avoided",
                                0x5555FF, "shield blue");
                register(registry, "fire_aspect", "🔥", FontSource.VANILLA, "Sets targets on fire",
                                0xFF5555, "flame red");
                register(registry, "punch", "🏹", FontSource.VANILLA, "Arrows knock targets back",
                                0xFFAA55, "bowstring amber");
                register(registry, "knockback", "☄", FontSource.VANILLA, "Melee hits knock targets back",
                                0xFFFF55, "impact yellow");
                register(registry, "flame", "💥", FontSource.ZEA, "Arrows set targets on fire",
                                0xFF6633, "ember orange");
                register(registry, "channeling", "⚡", FontSource.VANILLA, "Summons lightning in storms",
                                0xCCDDFF, "storm pale");
                register(registry, "riptide", "🔱", FontSource.VANILLA, "Launches you with the trident",
                                0x55FFFF, "tide aqua");
                register(registry, "multishot", "🔱", FontSource.VANILLA, "Fires three arrows at once",
                                0x66BFFF, "spread sky blue");
                register(registry, "piercing", "🎯", FontSource.ZEA, "Arrows pierce through entities",
                                0xCC5555, "bolt crimson");
                register(registry, "wind_burst", "💨", FontSource.ZEA, "Launches you on smash hit",
                                0xEEEEEE, "gust white");
                register(registry, "aqua_affinity", "⚓", FontSource.VANILLA, "Full mining speed underwater",
                                0x5599CC, "harbor steel blue");
                register(registry, "respiration", "🫧", FontSource.ZEA, "Longer underwater breathing",
                                0x7FDBDB, "bubble cyan");
                register(registry, "soul_speed", "🏃", FontSource.ZEA, "Speeds you up on soul blocks",
                                0x40D0C0, "soul teal");
                register(registry, "swift_sneak", "👣", FontSource.ZEA, "Faster sneaking",
                                0x8877AA, "shadow violet");
                register(registry, "frost_walker", "❄", FontSource.VANILLA, "Freezes water underfoot",
                                0x99DDFF, "ice pale blue");
                return Map.copyOf(registry);
        }

        private static void register(Map<String, Entry> registry, String path, String symbol,
                        FontSource font, String effect, int glyphColor, String colorName) {
                registry.put(path, new Entry(path, symbol, font, effect, glyphColor, colorName));
        }

        /**
         * The symbol registered for {@code path}, or null when the path is
         * outside the registry — the test seam for the duplicate-symbol and
         * identity rules.
         */
        static String symbolOf(String path) {
                Entry entry = REGISTRY.get(path);
                return entry == null ? null : entry.symbol();
        }

        /**
         * The font that renders {@code path}'s symbol — the test seam for
         * the font-routing rules, so no codepoint is ever sent to a font
         * that lacks its glyph.
         */
        static FontSource fontOf(String path) {
                Entry entry = REGISTRY.get(path);
                return entry == null ? null : entry.font();
        }

        /** The registry's entry: symbol, font, effect summary and the glyph's own color. */
        private record Entry(String path, String symbol, FontSource font, String effect,
                        int glyphColor, String colorName) {
        }

        /** One indicator's computed state and presentation data. */
        public record Indicator(String path, String symbol, String display, int level, int maxLevel,
                        State state, String effect) {
        }

        /** The three states an indicator can carry. */
        public enum State {
                APPLIED,
                MISSING,
                NOT_APPLICABLE
        }

        private SpecialEnchantments() {
        }

        /**
         * The rarity tier an applied level maps to — the one general
         * rule every registry enchantment shares. The maximum level is
         * always {@link ItemRarity#LEGENDARY}, the minimum always
         * {@link ItemRarity#COMMON}, and the levels between distribute
         * proportionally (nearest tier; halves round up). A
         * single-level enchantment has no distance to travel: its only
         * level is its maximum, so it is Legendary.
         */
        static ItemRarity tierFor(int level, int maxLevel) {
                ItemRarity[] tiers = ItemRarity.values();
                int top = tiers.length - 1;
                if (maxLevel <= 1) {
                        return tiers[top];
                }
                int clamped = Math.clamp(level, 1, maxLevel);
                long scaled = Math.round((clamped - 1) * (double) top / (maxLevel - 1));
                return tiers[(int) scaled];
        }

        /**
         * The candidate paths for {@code category}, in display order — the
         * item-class profiles. These decide which enchantments are worth a
         * symbol; whether the item can actually carry one is the game
         * definitions' decision, taken later in the state classifier.
         */
        public static List<String> profile(ItemCategory category) {
                return switch (category) {
                        case BOW -> List.of("infinity", "mending", "unbreaking", "flame", "punch");
                        case CROSSBOW -> List.of("multishot", "piercing", "mending", "unbreaking");
                        case SWORD, SPEAR -> List.of("fire_aspect", "knockback", "mending", "unbreaking");
                        case PICKAXE, SHOVEL, HOE, AXE -> List.of("silk_touch", "mending", "unbreaking");
                        case TRIDENT -> List.of("channeling", "riptide", "mending", "unbreaking");
                        case MACE -> List.of("wind_burst", "mending", "unbreaking");
                        case HELMET -> List.of("aqua_affinity", "respiration", "mending", "unbreaking");
                        case CHESTPLATE -> List.of("mending", "unbreaking");
                        case LEGGINGS -> List.of("swift_sneak", "mending", "unbreaking");
                        case BOOTS -> List.of("soul_speed", "frost_walker", "mending", "unbreaking");
                        case OTHER -> List.of();
                };
        }

        /**
         * The state for one candidate: applied when the item carries it,
         * not applicable when the item cannot support it or a present
         * enchantment conflicts with it, missing otherwise. The vanilla
         * mechanisms decide both gates — the same ones the rarity budget
         * builds on, so the indicators can never disagree with the
         * compatibility evaluator.
         */
        public static State state(ItemStack stack, Map<Holder<Enchantment>, Integer> present,
                        Holder<Enchantment> candidate) {
                return state(candidate.value().isSupportedItem(stack), present, candidate);
        }

        /**
         * The state classifier's component-free seam: {@code supported}
         * carries the {@code isSupportedItem} answer, and the conflict gate
         * runs through the holders' own exclusive sets — the seam the unit
         * tests drive, since a bare test JVM cannot construct an item
         * stack (its default components bind only during the data-load
         * flow).
         */
        static State state(boolean supported, Map<Holder<Enchantment>, Integer> present,
                        Holder<Enchantment> candidate) {
                if (present.containsKey(candidate)) {
                        return State.APPLIED;
                }
                if (!supported) {
                        return State.NOT_APPLICABLE;
                }
                for (Holder<Enchantment> held : present.keySet()) {
                        if (!Enchantment.areCompatible(candidate, held)) {
                                return State.NOT_APPLICABLE;
                        }
                }
                return State.MISSING;
        }

        /**
         * The indicators for {@code stack}: the profile's candidates in
         * order, classified, with the not-applicable ones and every
         * registry-path unknown to this client dropped. Empty when nothing
         * applies — the caller then omits the section entirely. The
         * registry access may be {@code null} outside a level, which
         * yields an empty row.
         */
        public static List<Indicator> indicators(ItemStack stack, HolderLookup.Provider registries,
                        ItemCategory category) {
                if (stack.isEmpty() || registries == null) {
                        return List.of();
                }
                Map<Holder<Enchantment>, Integer> present = present(stack);
                List<Indicator> indicators = new ArrayList<>();
                for (String path : profile(category)) {
                        Optional<Holder.Reference<Enchantment>> holder = holder(path, registries);
                        if (holder.isEmpty()) {
                                continue;
                        }
                        State state = state(stack, present, holder.get());
                        if (state == State.NOT_APPLICABLE) {
                                continue;
                        }
                        Entry entry = REGISTRY.get(path);
                        if (entry == null) {
                                continue;
                        }
                        int level = present.getOrDefault(holder.get(), 0);
                        int maxLevel = holder.get().value().getMaxLevel();
                        String display = Enchantment.getFullname(holder.get(), Math.max(1, level)).getString();
                        indicators.add(new Indicator(path, entry.symbol(), display, level, maxLevel,
                                        state, entry.effect()));
                }
                return List.copyOf(indicators);
        }

        /**
         * The indicator row for {@code indicators}: one compact line of
         * space-separated bracketed symbols — {@code [symbol]}, the
         * brackets from the vanilla font around the glyph — each part
         * fully colored and carrying its hover text. An applied
         * indicator takes its level's rarity tier color; a missing one
         * the legacy neutral gray. Empty when nothing shows.
         */
        public static List<Component> row(List<Indicator> indicators) {
                if (indicators.isEmpty()) {
                        return List.of();
                }
                MutableComponent line = Component.empty();
                boolean first = true;
                for (Indicator indicator : indicators) {
                        if (!first) {
                                line.append(Component.literal(" "));
                        }
                        line.append(bracketed(indicator));
                        first = false;
                }
                return List.of(line);
        }

        /**
         * The glyph color registered for {@code path}, or {@code null}
         * when the path is outside the registry — the test seam for the
         * meaningful-color rule.
         */
        static Integer glyphColorOf(String path) {
                Entry entry = REGISTRY.get(path);
                return entry == null ? null : entry.glyphColor();
        }

        /**
         * One indicator as {@code [symbol]}: the brackets carry the
         * progression color — the level's rarity tier for an applied
         * indicator, the legacy neutral gray for a missing one — while
         * the glyph wears its own meaningful color, the one its registry
         * entry pins (shield blue for Unbreaking, XP green for Mending).
         * A missing glyph stays gray so the applied/missing distinction
         * reads in color, not only in hover text; a path unknown to this
         * build falls back to the legacy single-color rendering. The
         * symbol renders in the font that has its glyph, and the hover
         * text sits on the whole part so it works on the brackets too.
         */
        private static MutableComponent bracketed(Indicator indicator) {
                boolean applied = indicator.state() == State.APPLIED;
                TextColor bracketColor = applied
                                ? TextColor.fromRgb(tierFor(indicator.level(), indicator.maxLevel()).rgb())
                                : TextColor.fromLegacyFormat(ChatFormatting.GRAY);
                Integer glyph = glyphColorOf(indicator.path());
                TextColor glyphColor = applied
                                ? (glyph == null ? bracketColor : TextColor.fromRgb(glyph))
                                : TextColor.fromLegacyFormat(ChatFormatting.GRAY);
                MutableComponent part = Component.empty();
                part.append(Component.literal("[").withColor(bracketColor));
                MutableComponent glyphPiece = Component.literal(indicator.symbol()).withColor(glyphColor);
                if (fontOf(indicator.path()) == FontSource.ZEA) {
                        glyphPiece.withStyle(style -> style.withFont(FONT));
                }
                part.append(glyphPiece);
                part.append(Component.literal("]").withColor(bracketColor));
                part.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                                Component.literal(hoverText(indicator)))));
                return part;
        }

        /** The hover text: the name, the applied level, and the effect summary. */
        private static String hoverText(Indicator indicator) {
                if (indicator.state() == State.APPLIED) {
                        return indicator.display() + "\n" + indicator.effect();
                }
                // A missing indicator's display text carries no level yet
                // (getFullname at level one); strip the roman numeral the name
                // formatter can append at higher levels and show the bare name.
                String name = indicator.display().replaceFirst(" [IVXL]+$", "");
                return name + "\n" + indicator.effect();
        }

        /** The item's present enchantments and their levels, as the stack carries them. */
        private static Map<Holder<Enchantment>, Integer> present(ItemStack stack) {
                ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS,
                                ItemEnchantments.EMPTY);
                Map<Holder<Enchantment>, Integer> levels = new HashMap<>();
                for (Holder<Enchantment> holder : enchantments.keySet()) {
                        levels.put(holder, enchantments.getLevel(holder));
                }
                return levels;
        }

        /** The registry holder for {@code path}, or empty when unknown here. */
        private static Optional<Holder.Reference<Enchantment>> holder(String path,
                        HolderLookup.Provider registries) {
                return registries.lookupOrThrow(Registries.ENCHANTMENT)
                                .get(ResourceKey.create(Registries.ENCHANTMENT,
                                                Identifier.fromNamespaceAndPath("minecraft", path)));
        }
}
