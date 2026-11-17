package net.zaminmc.zea.itempresentation.presentation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;

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
 *   <li>{@code APPLIED} — the enchantment is on the item; its symbol
 *       shows in the active color.</li>
 *   <li>{@code MISSING} — the item supports it, nothing conflicts, and
 *       the enchantment is absent; its symbol shows in neutral gray —
 *       a potential upgrade, never a defect.</li>
 *   <li>{@code NOT_APPLICABLE} — the item does not support it, or a
 *       present enchantment conflicts with it (Infinity against
 *       Mending, Silk Touch against Fortune, Riptide against its
 *       siblings); its symbol does not show at all.</li>
 * </ul>
 *
 * <p>Applicability and conflicts come from the game's own enchantment
 * definitions — {@link Enchantment#isSupportedItem} and {@link
 * Enchantment#areCompatible}, the same mechanisms the rarity budget
 * uses — so the exclusive-set tags vanilla ships decide the conflicts,
 * not a hand-written list. The candidate profiles only decide which
 * enchantments are worth a symbol per item class; a candidate the item
 * class cannot carry falls out as not applicable and is hidden.
 *
 * <p>The row omits its heading: the symbols are compact enough to
 * explain themselves through their hover text, which carries the
 * enchantment's name, its level when applied, and a one-line effect
 * summary. When no candidate ends up applied or missing, the entire
 * section — and its spacing — is omitted.
 */
public final class SpecialEnchantments {
        /** The active color for an applied indicator (§b, the aqua family). */
        static final int APPLIED_COLOR = 0x55FFFF;
        /** The neutral gray for a missing indicator — the potential-upgrade color. */
        static final int MISSING_COLOR = 0x555555;

        /**
         * The indicator registry: registry-key path to its symbol and its
         * one-line effect summary. Riptide and Multishot intentionally
         * share the trident-glyph symbol; their registry paths — and with
         * them their identities, states and conflicts — stay distinct.
         */
        private static final Map<String, Entry> REGISTRY = buildRegistry();

        private static Map<String, Entry> buildRegistry() {
                Map<String, Entry> registry = new LinkedHashMap<>();
                register(registry, "infinity", "∞", "Arrows are not consumed");
                register(registry, "mending", "♻", "Repairs with gathered XP");
                register(registry, "silk_touch", "🔮", "Blocks drop themselves");
                register(registry, "unbreaking", "🛡️", "Durability loss often avoided");
                register(registry, "fire_aspect", "🔥", "Sets targets on fire");
                register(registry, "punch", "🏹", "Arrows knock targets back");
                register(registry, "knockback", "☄", "Melee hits knock targets back");
                register(registry, "flame", "💥", "Arrows set targets on fire");
                register(registry, "channeling", "⚡", "Summons lightning in storms");
                register(registry, "riptide", "🔱", "Launches you with the trident");
                register(registry, "multishot", "🔱", "Fires three arrows at once");
                register(registry, "piercing", "🎯", "Arrows pierce through entities");
                register(registry, "wind_burst", "💨", "Launches you on smash hit");
                register(registry, "aqua_affinity", "⚓", "Full mining speed underwater");
                register(registry, "respiration", "🫧", "Longer underwater breathing");
                register(registry, "soul_speed", "🏃", "Speeds you up on soul blocks");
                register(registry, "swift_sneak", "👣", "Faster sneaking");
                register(registry, "frost_walker", "❄", "Freezes water underfoot");
                return Map.copyOf(registry);
        }

        private static void register(Map<String, Entry> registry, String path, String symbol, String effect) {
                registry.put(path, new Entry(path, symbol, effect));
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

        private record Entry(String path, String symbol, String effect) {
        }

        /** One indicator's computed state and presentation data. */
        public record Indicator(String path, String symbol, String display, int level,
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
                        String display = Enchantment.getFullname(holder.get(), Math.max(1, level)).getString();
                        indicators.add(new Indicator(path, entry.symbol(), display, level, state, entry.effect()));
                }
                return List.copyOf(indicators);
        }

        /**
         * The indicator row for {@code indicators}: one compact line of
         * symbols, applied in aqua and missing in gray, each carrying its
         * hover text. Empty when nothing shows.
         */
        public static List<Component> row(List<Indicator> indicators) {
                if (indicators.isEmpty()) {
                        return List.of();
                }
                MutableComponent line = Component.empty();
                boolean first = true;
                for (Indicator indicator : indicators) {
                        if (!first) {
                                line.append(Component.literal("  "));
                        }
                        int color = indicator.state() == State.APPLIED ? APPLIED_COLOR : MISSING_COLOR;
                        MutableComponent glyph = Component.literal(indicator.symbol()).withColor(color);
                        glyph.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal(hoverText(indicator)))));
                        line.append(glyph);
                        first = false;
                }
                return List.of(line);
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
