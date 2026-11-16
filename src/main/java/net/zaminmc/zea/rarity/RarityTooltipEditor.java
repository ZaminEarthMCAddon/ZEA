package net.zaminmc.zea.rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.itempresentation.calculation.ItemStatistics;
import net.zaminmc.zea.itempresentation.calculation.StatisticsEngine;
import net.zaminmc.zea.itempresentation.presentation.EnchantmentSection;
import net.zaminmc.zea.itempresentation.presentation.ItemPresentationBuilder;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import net.zaminmc.zea.itempresentation.recognition.ItemClassifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Applies ZEA's RPG equipment presentation to one item tooltip, in one
 * pass:
 *
 * <ol>
 *   <li><b>Scope gate</b> — the one authoritative {@link ItemClassifier}
 *       decision is made here, first. An item outside the equipment
 *       allowlist (blocks, materials, food, potions, books, shields,
 *       elytra, spawn eggs, ...) returns untouched: no stats, no
 *       enchantment re-styling, no name recoloring, no footer, no
 *       suppressed native lines. The restriction is enforced centrally
 *       so no individual rendering path can forget it.</li>
 *   <li><b>RPG block</b> — for supported equipment with the RPG-stats
 *       option on, the vanilla attribute listing is replaced by the
 *       compact stat block, and the vanilla per-line enchantment list
 *       is replaced by the compact grouped section (blue names, grey
 *       separators, comma-boundary wrapping). Only the replaced lines
 *       are removed — lore, item descriptions and native advanced
 *       diagnostics survive.</li>
 *   <li><b>Name coloring</b> — the title line takes the rarity's
 *       color, on a rendering copy: the text, its translations, its
 *       non-color styling and the underlying stack are all preserved.
 *       Custom names are recolored the same way — the exact displayed
 *       text survives; only the presentation color follows the
 *       rarity.</li>
 *   <li><b>Enchantment tier styling</b> — when the compact section is
 *       disabled, vanilla's own enchantment lines are recolored by
 *       level tier instead: grey below-max, the animated chroma or
 *       static gold at max, blue above-max.</li>
 *   <li><b>Missing upgrades</b> — while Left Shift is held, the gray
 *       comma-separated list from {@link MissingUpgrades} is inserted
 *       before the footer.</li>
 *   <li><b>Rarity footer</b> — the bold "RARITY TYPE" line ("UNCOMMON
 *       SWORD"), positioned after the stat, enchantment and
 *       descriptive sections and before Minecraft's native advanced
 *       tooltip information.</li>
 * </ol>
 *
 * <p>The editor only ever works on the list vanilla returns, so
 * Minecraft's own tooltip pipeline — including the F3+H advanced item
 * identifier, component details and source footer — keeps running and
 * keeps its ordering; nothing is fabricated or replaced on that side.
 * The underlying item, its components and its stored lore are
 * untouched.
 */
public final class RarityTooltipEditor {
        /**
         * The widest the wrapped enchantment and missing-upgrade
         * sections may be, in glyphs. A tooltip never strays near the
         * screen edge: at the widest vanilla GUI scale the screen fits
         * this twice over.
         */
        private static final int MAX_SECTION_WIDTH = 240;

        /** Narrowest wrap budget, for very small windows. */
        private static final int MIN_SECTION_WIDTH = 120;

        /** Margin kept between any tooltip and the window edge. */
        private static final int SCREEN_MARGIN = 20;

        /** The blue (§9) an ordinary enchantment name wears in the compact section. */
        private static final int ENCHANT_BLUE = 0x5555FF;

        /** The red a curse wears — vanilla's own curse presentation color. */
        private static final int CURSE_RED = 0xFF5555;

        /** The gray a foreign (incompatible, command-placed) enchantment wears. */
        private static final int FOREIGN_GRAY = 0xAAAAAA;

        /** The dark gray vanilla styles its advanced item-identifier line with. */
        private static final int ADVANCED_ID_GRAY = 0x3F3F3F;

        private RarityTooltipEditor() {
        }

        /**
         * Edits a freshly built tooltip list for {@code stack}. Returns
         * {@code list} unchanged unless an effect actually applies —
         * and byte-for-byte unchanged for every item outside the
         * equipment allowlist.
         *
         * @param list the tooltip lines vanilla just built for this frame
         * @param stack the hovered item
         */
        public static List<Component> edit(List<Component> list, ItemStack stack) {
                ZeaConfig cfg = ZeaClient.config();
                if (cfg == null || list.isEmpty()) {
                        return list;
                }
                // The central scope gate: one classifier decision that
                // every RPG path below respects. Unsupported items keep
                // their vanilla tooltip completely untouched.
                ItemCategory category = ItemClassifier.classify(stack);
                if (category == ItemCategory.OTHER) {
                        return list;
                }
                List<Component> edited = new ArrayList<>(list);
                ItemRarity rarity = RarityEngine.rarityOf(stack);
                if (cfg.rarityRpgStatsEnabled()) {
                        insertRpgBlock(edited, stack, category);
                }
                if (rarity == null) {
                        return edited;
                }
                if (cfg.rarityNameColoringEnabled()) {
                        recolorName(edited, rarity);
                }
                if (!cfg.rarityRpgStatsEnabled()) {
                        styleEnchantments(edited, stack);
                }
                if (cfg.rarityMissingEnchantsEnabled() && Minecraft.getInstance().hasShiftDown()) {
                        insertMissingUpgrades(edited, stack);
                }
                if (cfg.rarityLoreRarityEnabled()) {
                        insertRarityFooter(edited, rarity, category, stack);
                }
                return edited;
        }

        /**
         * Inserts the RPG block: the compact stat section after the
         * item name, and the compact grouped enchantment section after
         * it, removing only the vanilla lines they replace — the
         * {@code item.modifiers.*} attribute listing and the per-line
         * enchantment entries. When the item carries no statistics and
         * no enchantments, nothing is inserted and nothing is removed.
         */
        private static void insertRpgBlock(List<Component> lines, ItemStack stack, ItemCategory category) {
                ItemStatistics statistics = StatisticsEngine.statisticsOf(stack, levelLookup());
                List<Component> statLines = ItemPresentationBuilder.build(category, statistics);
                ItemEnchantments enchantments = stack.getOrDefault(
                                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (statLines.isEmpty() && enchantments.isEmpty()) {
                        return;
                }
                if (!statLines.isEmpty()) {
                        removeVanillaAttributeSection(lines);
                }
                removeVanillaEnchantmentLines(lines, enchantments);
                // The stat block carries its own leading and trailing
                // blank separators; insert after the name (line 0).
                lines.addAll(1, statLines);
                insertCompactEnchantments(lines, stack, enchantments, 1 + statLines.size());
        }

        /**
         * Inserts the compact grouped enchantment section at {@code
         * insertAt}: blue names (red for curses, the maxed tier color
         * for enchantments at their registered cap, gray for foreign
         * command-placed ones), grey separators, wrapped to the usable
         * tooltip width at comma boundaries. The section ends with one
         * blank line so whatever follows — lore, the footer — starts
         * clearly separated. Nothing is inserted when the item carries
         * no enchantments.
         */
        private static void insertCompactEnchantments(List<Component> lines, ItemStack stack,
                        ItemEnchantments enchantments, int insertAt) {
                if (enchantments.isEmpty()) {
                        return;
                }
                ZeaConfig cfg = ZeaClient.config();
                boolean animated = cfg != null && cfg.rarityChromaEnabled();
                int maxedColor = animated
                                ? RarityEngine.chromaColor(System.currentTimeMillis())
                                : RarityEngine.STATIC_MAXED_COLOR;
                EnchantBudget budget = budgetFor(stack);
                List<EnchantmentSection.Entry> entries = new ArrayList<>();
                for (var entry : enchantments.entrySet()) {
                        var holder = entry.getKey();
                        int level = entry.getIntValue();
                        entries.add(new EnchantmentSection.Entry(
                                        Enchantment.getFullname(holder, level).getString(),
                                        compactEnchantmentColor(holder, level, maxedColor, budget)));
                }
                List<Component> section = EnchantmentSection.render(entries, sectionWidth(), widthOracle());
                if (section.isEmpty()) {
                        return;
                }
                section.add(Component.empty());
                lines.addAll(Math.min(insertAt, lines.size()), section);
        }

        /**
         * The color one enchantment name wears in the compact section:
         * red for a curse (vanilla's own presentation), the maxed tier
         * color for a compatible enchantment at or past its registered
         * maximum, gray for a foreign enchantment the item's class
         * cannot legitimately hold (so it reads as foreign), and the
         * spec's blue for everything else.
         */
        private static int compactEnchantmentColor(Holder<Enchantment> holder, int level,
                        int maxedColor, EnchantBudget budget) {
                if (holder.is(EnchantmentTags.CURSE)) {
                        return CURSE_RED;
                }
                boolean compatible = budget.compatible().stream()
                                .anyMatch(candidate -> candidate.enchantment() == holder);
                if (!compatible) {
                        return FOREIGN_GRAY;
                }
                return styleForLevels(level, holder.value().getMaxLevel()) == EnchantmentStyle.MAXED
                                ? maxedColor
                                : ENCHANT_BLUE;
        }

        /**
         * Removes the vanilla per-line enchantment entries — the lines
         * whose text exactly equals one of the held enchantments' own
         * display strings — so the compact section can present them
         * without duplication. The name line (index 0) is never
         * touched, and lines that do not match a held enchantment
         * (lore, descriptions) are left alone.
         */
        private static void removeVanillaEnchantmentLines(List<Component> lines, ItemEnchantments enchantments) {
                if (enchantments.isEmpty()) {
                        return;
                }
                Set<String> displays = new HashSet<>();
                for (var entry : enchantments.entrySet()) {
                        displays.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
                }
                for (int i = lines.size() - 1; i >= 1; i--) {
                        if (displays.contains(lines.get(i).getString())) {
                                lines.remove(i);
                        }
                }
        }

        /**
         * Recolors the title line to the rarity color, on a rendering
         * copy. The exact displayed text, its translations, its
         * non-color styling and any child formatting survive — only
         * the root color follows the rarity — and the underlying
         * ItemStack and its name component are never modified.
         */
        private static void recolorName(List<Component> lines, ItemRarity rarity) {
                if (lines.isEmpty()) {
                        return;
                }
                Component name = lines.get(0);
                MutableComponent recolored = name.copy()
                                .withStyle(style -> style.withColor(RarityEngine.textColor(rarity)));
                lines.set(0, recolored);
        }

        /**
         * Styles the item's vanilla enchantment lines by their level
         * relative to the registered maximum — the fallback path when
         * the compact RPG section is disabled and vanilla's own
         * per-line list is on screen:
         *
         * <ul>
         *   <li><b>Below maximum</b> — grey ({@code &7}), the basic
         *       enchantment look. The default for an ordinary
         *       partially-levelled enchantment.</li>
         *   <li><b>At maximum</b> — the animated chroma color when
         *       the animation is enabled, otherwise the static gold
         *       that keeps the maxed tier readable without motion.</li>
         *   <li><b>Above maximum</b> — blue ({@code &9}), the
         *       "enhanced" look for an enchantment pushed past its
         *       registered maximum.</li>
         * </ul>
         *
         * <p>Maximum levels are read from {@link Enchantment#getMaxLevel}
         * — never hardcoded. Lines are matched against the exact vanilla
         * display text ({@code Enchantment.getFullname}) of the item's
         * own enchantments, so custom lore that merely contains the same
         * words is out of reach unless it really is that enchantment
         * line.
         */
        private static void styleEnchantments(List<Component> lines, ItemStack stack) {
                ItemEnchantments enchantments = stack.getOrDefault(
                                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (enchantments.isEmpty()) {
                        return;
                }
                ZeaConfig cfg = ZeaClient.config();
                boolean animated = cfg != null && cfg.rarityChromaEnabled();
                long now = System.currentTimeMillis();
                int chroma = animated
                                ? RarityEngine.chromaColor(now)
                                : RarityEngine.STATIC_MAXED_COLOR;
                for (int i = 0; i < lines.size(); i++) {
                        Component line = lines.get(i);
                        String text = line.getString();
                        EnchantmentStyle style = enchantmentStyle(text, enchantments, budgetFor(stack));
                        if (style == null) {
                                continue;
                        }
                        int color = style == EnchantmentStyle.MAXED
                                        ? (chroma & 0xFFFFFF)
                                        : style.rgb;
                        lines.set(i, line.copy().withStyle(
                                        s -> s.withColor(TextColor.fromRgb(color))));
                }
        }

        /**
         * The styling decision for one enchantment line, or {@code null}
         * when the line is not one of the item's own enchantment
         * displays. Resolved by matching the line text against each
         * held enchantment's vanilla fullname at its held level.
         */
        private static EnchantmentStyle enchantmentStyle(String text, ItemEnchantments enchantments,
                        EnchantBudget budget) {
                for (var entry : enchantments.entrySet()) {
                        var holder = entry.getKey();
                        int level = entry.getIntValue();
                        int maxLevel = holder.value().getMaxLevel();
                        // Only enchantments the item's class actually
                        // accepts are styled — a command-placed
                        // enchantment the item cannot legitimately hold
                        // is left at its vanilla color so it reads as
                        // foreign rather than as a celebrated max.
                        boolean compatible = budget.compatible().stream()
                                        .anyMatch(candidate -> candidate.enchantment() == holder);
                        if (!compatible) {
                                continue;
                        }
                        String fullname = Enchantment.getFullname(holder, level).getString();
                        if (!fullname.equals(text)) {
                                continue;
                        }
                        return styleForLevels(level, maxLevel);
                }
                return null;
        }

        /**
         * The styling tier for a (level, maxLevel) pair — the pure
         * seam, testable without a registry. Above-max is enhanced
         * (blue), at-max is the chroma tier, below-max is basic (grey).
         */
        static EnchantmentStyle styleForLevels(int level, int maxLevel) {
                if (level > maxLevel) {
                        return EnchantmentStyle.ENHANCED;
                }
                if (level >= maxLevel) {
                        return EnchantmentStyle.MAXED;
                }
                return EnchantmentStyle.BASIC;
        }

        /** The three enchantment styling tiers and their fixed colors. */
        enum EnchantmentStyle {
                /** Below maximum — grey ({@code &7}). */
                BASIC(0xAAAAAA),
                /** At maximum — the animated chroma color, applied by the caller. */
                MAXED(-1),
                /** Above maximum — blue ({@code &9}). */
                ENHANCED(0x5555FF);

                final int rgb;

                EnchantmentStyle(int rgb) {
                        this.rgb = rgb;
                }
        }

        /**
         * Inserts the missing-upgrade block — header plus the gray,
         * comma-separated list wrapped to the usable tooltip width —
         * before the rarity footer position, so it never lands after
         * Minecraft's native advanced lines.
         */
        private static void insertMissingUpgrades(List<Component> lines, ItemStack stack) {
                EnchantBudget budget = budgetFor(stack);
                ItemEnchantments enchantments = stack.getOrDefault(
                                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                List<MissingUpgrades.Upgrade> upgrades =
                                MissingUpgrades.of(budget.compatible(), enchantments);
                if (upgrades.isEmpty()) {
                        return;
                }
                List<Component> block = MissingUpgrades.render(upgrades, sectionWidth(), widthOracle());
                lines.addAll(nativeTrailerPosition(lines, stack), block);
        }

        /**
         * Inserts the bold rarity footer — "RARITY TYPE", uppercase,
         * in the rarity's color — after the stat, enchantment and
         * descriptive sections and before Minecraft's native advanced
         * tooltip information, wherever it is present. The footer is
         * never duplicated.
         */
        private static void insertRarityFooter(List<Component> lines, ItemRarity rarity, ItemCategory category,
                        ItemStack stack) {
                String label = footerLabel(rarity, category);
                for (Component line : lines) {
                        if (line.getString().equals(label)) {
                                return;
                        }
                }
                int at = nativeTrailerPosition(lines, stack);
                // Exactly one blank line before the footer: collapse
                // any run of trailing blanks (the removed vanilla
                // sections can leave one behind) to a single separator.
                while (at > 1 && lines.get(at - 1).getString().isEmpty()
                                && lines.get(at - 2).getString().isEmpty()) {
                        lines.remove(at - 1);
                        at--;
                }
                if (at > 0 && !lines.get(at - 1).getString().isEmpty()) {
                        lines.add(at, Component.empty());
                        at++;
                }
                lines.add(at, Component.literal(label)
                                .withStyle(ChatFormatting.BOLD)
                                .withStyle(style -> style.withColor(RarityEngine.textColor(rarity))));
        }

        /**
         * Where ZEA's trailer content (missing upgrades, the rarity
         * footer) may start: at Minecraft's native advanced section
         * when one is present — the advanced durability line, the
         * dark-gray registry-identifier literal, the component-count
         * line — and at the end of the list otherwise. The native
         * lines are located, never fabricated: the identifier is
         * matched by recomputing the exact string and color vanilla
         * itself produced, so the footer always lands before them and
         * their order is preserved.
         */
        private static int nativeTrailerPosition(List<Component> lines, ItemStack stack) {
                for (int i = 1; i < lines.size(); i++) {
                        if (isNativeAdvancedLine(lines.get(i), stack)) {
                                return i;
                        }
                }
                return lines.size();
        }

        /**
         * Whether the line belongs to Minecraft's native advanced
         * tooltip section: the translatable {@code item.durability}
         * and {@code item.components} entries, or the registry
         * identifier literal — this item's own key as plain text,
         * dark gray, exactly as vanilla's advanced branch builds it.
         */
        private static boolean isNativeAdvancedLine(Component line, ItemStack stack) {
                if (line.getContents() instanceof TranslatableContents contents) {
                        return contents.getKey().equals("item.durability")
                                        || contents.getKey().equals("item.components");
                }
                if (line.getContents() instanceof PlainTextContents contents) {
                        String expected = stack.getItem().builtInRegistryHolder().key().identifier().toString();
                        return contents.text().equals(expected)
                                        && line.getStyle().getColor() != null
                                        && line.getStyle().getColor().getValue() == ADVANCED_ID_GRAY;
                }
                return false;
        }

        /**
         * The footer label — RARITY plus equipment type, uppercase by
         * construction from the enum names: "UNCOMMON SWORD", "EPIC
         * PICKAXE", "LEGENDARY BOW", "RARE"-style bare words never
         * appear. Package-private as the pure seam the formatting
         * tests pin.
         */
        static String footerLabel(ItemRarity rarity, ItemCategory category) {
                return rarity.label() + " " + category.name();
        }

        /**
         * Removes the vanilla attribute section — the lines between an
         * {@code item.modifiers.*} header ("When in Main Hand:",
         * "When on Chest:", …) and the next blank line. The stat block
         * reproduces these numbers in a cleaner form, so the redundant
         * vanilla listing is dropped. The header is recognized by its
         * translation key, not its display text: 26.3 renamed the chest
         * header ("When on Body:" became "When on Chest:") and added
         * group headers ("When worn:", "When equipped:", "When held:"),
         * and matching the key keeps every current and future slot
         * group covered without a hardcoded string list drifting behind
         * the game. Enchantment lines and lore sit outside this section
         * and are untouched — plain-text lore never carries the
         * translatable contents a real header has. If no attribute
         * section is present (a server tooltip that omits it), nothing
         * is removed.
         */
        private static void removeVanillaAttributeSection(List<Component> lines) {
                int headerIndex = -1;
                for (int i = 0; i < lines.size(); i++) {
                        if (isAttributeSectionHeader(lines.get(i))) {
                                headerIndex = i;
                                break;
                        }
                }
                if (headerIndex < 0) {
                        return;
                }
                // The section runs from the header through the next blank
                // line (inclusive). A missing closing blank is treated as
                // the end of the tooltip.
                int end = headerIndex + 1;
                while (end < lines.size() && !lines.get(end).getString().isEmpty()) {
                        end++;
                }
                if (end < lines.size() && lines.get(end).getString().isEmpty()) {
                        end++;
                }
                for (int i = end - 1; i >= headerIndex; i--) {
                        lines.remove(i);
                }
        }

        /**
         * Whether the line is a vanilla attribute-section header: a
         * translatable component keyed under {@code item.modifiers.}
         * (mainhand, offhand, chest, armor, saddle, …). Vanilla builds
         * exactly these components for its "When in Main Hand:" style
         * headings, so the key test matches them all without pinning
         * any translated wording — and a plain-text lore line that
         * merely spells the same words is not fooled for a header.
         * Package-private as the pure seam the styling tests pin.
         */
        static boolean isAttributeSectionHeader(Component line) {
                return line.getContents() instanceof TranslatableContents contents
                                && contents.getKey().startsWith("item.modifiers.");
        }

        /** The wrap budget for the wrapped tooltip sections, from the window's usable width. */
        private static int sectionWidth() {
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                return Math.max(
                                MIN_SECTION_WIDTH,
                                Math.min(MAX_SECTION_WIDTH, screenWidth - SCREEN_MARGIN * 2));
        }

        /** The font-backed width oracle for the wrapped sections. */
        private static ToIntFunction<String> widthOracle() {
                return text -> Minecraft.getInstance().font.width(text);
        }

        /**
         * The item class's enchantment budget, shared by the styling and
         * missing-upgrade paths so one hover asks for it once per use.
         */
        private static EnchantBudget budgetFor(ItemStack stack) {
                return EnchantBudget.of(stack.getItem(), levelLookup());
        }

        /**
         * The level's registry access; {@code null} outside a level,
         * which is fine here — rarity was already resolved to non-null by
         * the time budget lookups run, and those paths are unreachable
         * without a level.
         */
        private static net.minecraft.core.HolderLookup.Provider levelLookup() {
                Minecraft minecraft = Minecraft.getInstance();
                return minecraft == null || minecraft.level == null
                                ? null
                                : minecraft.level.registryAccess();
        }
}
