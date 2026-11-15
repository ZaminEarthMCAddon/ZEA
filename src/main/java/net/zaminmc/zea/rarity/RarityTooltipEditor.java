package net.zaminmc.zea.rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.ZeaClient;
import net.zaminmc.zea.config.ZeaConfig;
import net.zaminmc.zea.itempresentation.calculation.ItemStatistics;
import net.zaminmc.zea.itempresentation.calculation.StatisticsEngine;
import net.zaminmc.zea.itempresentation.presentation.ItemPresentationBuilder;
import net.zaminmc.zea.itempresentation.recognition.ItemCategory;
import net.zaminmc.zea.itempresentation.recognition.ItemClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Applies ZEA's rarity text effects to one item tooltip, in one pass:
 *
 * <ol>
 *   <li><b>Name coloring</b> — the first line takes the rarity's color.
 *       Custom names win: an item with a {@code custom_name} component
 *       keeps the name the player or server gave it, including its own
 *       formatting; only vanilla-generated names are recolored. The rest
 *       of the line's formatting (italic and friends) is preserved.</li>
 *   <li><b>Enchantment styling</b> — enchantment lines are recolored
 *       by their level relative to the registered maximum: grey for
 *       below-max (basic), the animated chroma color for at-max, and
 *       blue for above-max (enhanced). Maximum levels are read from
 *       {@link Enchantment#getMaxLevel}, never hardcoded. Lines are
 *       matched against the exact vanilla display text ({@code
 *       Enchantment.getFullname}) of the item's own enchantments, so
 *       custom lore that merely contains the same words is out of reach
 *       unless it really is that enchantment line. Colors are recomputed
 *       per frame from the clock — tooltips rebuild every frame while
 *       hovered, so the animation costs nothing extra. The tier styling
 *       itself is not optional: when the chroma <em>animation</em> is
 *       disabled, a maxed enchantment keeps its tier but wears the
 *       static gold instead of the animated color — the option gates
 *       the animation, not the information.</li>
 *   <li><b>Missing upgrades</b> — while Left Shift is held, the gray
 *       comma-separated list from {@link MissingUpgrades} is appended,
 *       wrapped to the tooltip's usable width so it can neither stretch to
 *       the screen edge nor fall apart into one-enchantment-per-line.</li>
 *   <li><b>Lore rarity</b> — the bold rarity label is appended at the very
 *       bottom, unless a line with that exact label already exists (never
 *       two entries). Nothing is written back to the stack: the edit lives
 *       in the freshly built tooltip list vanilla hands us every frame.</li>
 * </ol>
 *
 * <p>The editor only ever works on the list vanilla returns; the
 * underlying item, its components and its stored lore are untouched.
 * Unsupported items pass through byte-for-byte.
 */
public final class RarityTooltipEditor {
        /**
         * The widest the missing-upgrades block may be, in glyphs. A
         * tooltip never strays near the screen edge: at the widest
         * vanilla GUI scale the screen fits this twice over.
         */
        private static final int MAX_MISSING_WIDTH = 240;

        /** Narrowest wrap budget, for very small windows. */
        private static final int MIN_MISSING_WIDTH = 120;

        /** Margin kept between any tooltip and the window edge. */
        private static final int SCREEN_MARGIN = 20;

        private RarityTooltipEditor() {
        }

        /**
         * Edits a freshly built tooltip list for {@code stack}. Returns
         * {@code list} unchanged unless an effect actually applies.
         *
         * @param list the tooltip lines vanilla just built for this frame
         * @param stack the hovered item, for rarity, enchantments and the
         *        custom-name check
         */
        public static List<Component> edit(List<Component> list, ItemStack stack) {
                ZeaConfig cfg = ZeaClient.config();
                if (cfg == null || list.isEmpty()) {
                        return list;
                }
                List<Component> edited = new ArrayList<>(list);
                // The RPG stat block applies to any recognised category —
                // bows and maces are not rarity-rated (MaterialTier only
                // covers tools and armor), but they still carry the
                // statistics this block presents. Resolved before the
                // rarity null-check so those categories are not skipped.
                if (cfg.rarityRpgStatsEnabled()) {
                        insertRpgStatsBlock(edited, stack);
                }
                ItemRarity rarity = RarityEngine.rarityOf(stack);
                if (rarity == null) {
                        return edited;
                }
                if (cfg.rarityNameColoringEnabled()) {
                        recolorName(edited, stack, rarity);
                }
                styleEnchantments(edited, stack);
                if (cfg.rarityMissingEnchantsEnabled() && Minecraft.getInstance().hasShiftDown()) {
                        appendMissingUpgrades(edited, stack);
                }
                if (cfg.rarityLoreRarityEnabled()) {
                        appendRarityLabel(edited, rarity);
                }
                if (cfg.rarityAdvancedInfoEnabled() && Minecraft.getInstance().hasControlDown()) {
                        appendAdvancedInfo(edited, stack);
                }
                return edited;
        }

        /**
         * Recolors the name line to the rarity color. Vanilla-generated
         * names only: a custom name is the player's or server's statement
         * about the item and outranks rarity coloring. The transform
         * replaces the color, nothing else.
         */
        private static void recolorName(List<Component> lines, ItemStack stack, ItemRarity rarity) {
                if (lines.isEmpty() || stack.has(DataComponents.CUSTOM_NAME)) {
                        return;
                }
                Component name = lines.get(0);
                MutableComponent recolored = name.copy()
                                .withStyle(style -> style.withColor(RarityEngine.textColor(rarity)));
                lines.set(0, recolored);
        }

        /**
         * Styles the item's enchantment lines by their level relative to
         * the registered maximum:
         *
         * <ul>
         *   <li><b>Below maximum</b> — grey ({@code &7}), the basic
         *       enchantment look. The default for an ordinary
         *       partially-levelled enchantment.</li>
         *   <li><b>At maximum</b> — the animated chroma color when
         *       the animation is enabled, otherwise the static gold
         *       that keeps the maxed tier readable without motion. One
         *       enchantment at its cap is the achievement the chroma
         *       celebrates, regardless of enchantment type.</li>
         *   <li><b>Above maximum</b> — blue ({@code &9}), the
         *       "enhanced" look. An enchantment pushed past its
         *       registered maximum (commands, anvil combining) keeps its
         *       real displayed level but takes the max-level visual
         *       treatment, so a Sharpness VI reads as enhanced rather
         *       than as a glitch.</li>
         * </ul>
         *
         * <p>Maximum levels are read from {@link Enchantment#getMaxLevel}
         * — never hardcoded. Lines are matched against the exact vanilla
         * display text ({@code Enchantment.getFullname}) of the item's
         * own enchantments, so custom lore that merely contains the same
         * words is out of reach unless it really is that enchantment
         * line. Neutral enchantments (Knockback, Thorns, …) are styled
         * by level like any other — neutral is a rarity-scoring concern,
         * not a presentation one.
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
                                        s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(color))));
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
         * Appends the missing-upgrade block: header plus the gray,
         * comma-separated list wrapped to the usable tooltip width.
         */
        private static void appendMissingUpgrades(List<Component> lines, ItemStack stack) {
                EnchantBudget budget = budgetFor(stack);
                ItemEnchantments enchantments = stack.getOrDefault(
                                                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                List<MissingUpgrades.Upgrade> upgrades =
                                                MissingUpgrades.of(budget.compatible(), enchantments);
                if (upgrades.isEmpty()) {
                        return;
                }
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int budgetWidth = Math.max(
                                                MIN_MISSING_WIDTH,
                                                Math.min(MAX_MISSING_WIDTH, screenWidth - SCREEN_MARGIN * 2));
                ToIntFunction<String> width = text -> Minecraft.getInstance().font.width(text);
                lines.addAll(MissingUpgrades.render(upgrades, budgetWidth, width));
        }

        /**
         * Appends the bold rarity label at the bottom, unless the tooltip
         * already carries an entry with that exact label — a duplicate
         * rarity line is never added.
         */
        private static void appendRarityLabel(List<Component> lines, ItemRarity rarity) {
                for (Component line : lines) {
                        if (line.getString().equals(rarity.label())) {
                                return;
                        }
                }
                lines.add(Component.literal(rarity.label())
                                                .withStyle(ChatFormatting.BOLD)
                                                .withStyle(style -> style.withColor(RarityEngine.textColor(rarity))));
        }

        /**
         * Appends the hidden technical detail the clean tooltip
         * suppresses: the item's registry identifier, its recognised
         * category, the enchantment levels against their registered
         * maximums, and the count of data components the stack carries.
         *
         * <p>Shown only when the advanced-info option is on <em>and</em>
         * Left Ctrl is held — a deliberate two-gate so the default view
         * stays clean and the detail is opt-in per hover. The
         * information is appended, never replacing: server-provided lore
         * and the rarity label sit above it. Unknown items get the
         * identifier and component count only, since they have no
         * category or enchantments to enumerate.
         */
        private static void appendAdvancedInfo(List<Component> lines, ItemStack stack) {
                lines.add(Component.empty());
                lines.add(Component.literal("§7Identifier: §f"
                                + stack.getItem().builtInRegistryHolder().key().identifier()));
                ItemCategory category = ItemClassifier.classify(stack);
                if (category != ItemCategory.OTHER) {
                        lines.add(Component.literal("§7Category: §f" + category.name().toLowerCase()));
                }
                ItemEnchantments enchantments = stack.getOrDefault(
                                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (!enchantments.isEmpty()) {
                        lines.add(Component.literal("§7Enchantments:"));
                        for (var entry : enchantments.entrySet()) {
                                int level = entry.getIntValue();
                                int max = entry.getKey().value().getMaxLevel();
                                String path = entry.getKey().unwrapKey()
                                                .map(key -> key.identifier().toString())
                                                .orElse("? ");
                                lines.add(Component.literal("§8  " + path + " " + level + "/" + max));
                        }
                }
                int components = stack.getComponents().size();
                lines.add(Component.literal("§7Components: §f" + components));
        }

        /**
         * Inserts the RPG-style stat block after the item name and drops
         * the redundant vanilla attribute section it replaces. The
         * stat block is built from the item's actual computed statistics;
         * when the item carries no statistics (an unrecognised item, or
         * a recognised one whose calculators all returned empty) nothing
         * is inserted and the vanilla tooltip is left intact. Server
         * enchantments and lore are never removed — only the
         * "When in Main Hand" / "When on Body" attribute listing that the
         * stat block now carries in a cleaner form.
         */
        private static void insertRpgStatsBlock(List<Component> lines, ItemStack stack) {
                ItemCategory category = ItemClassifier.classify(stack);
                if (category == ItemCategory.OTHER) {
                        return;
                }
                ItemStatistics statistics = StatisticsEngine.statisticsOf(stack, levelLookup());
                List<Component> statLines = ItemPresentationBuilder.build(category, statistics);
                if (statLines.isEmpty()) {
                        return;
                }
                removeVanillaAttributeSection(lines);
                // Insert after the name (line 0); the stat block already
                // carries its own leading and trailing blank separators.
                int insertAt = Math.min(1, lines.size());
                lines.addAll(insertAt, statLines);
        }

        /**
         * Removes the vanilla "When in Main Hand" / "When on Body"
         * attribute section — the lines between the header and the next
         * blank line. The stat block reproduces these numbers in a
         * cleaner form, so the redundant vanilla listing is dropped.
         * Enchantment lines and lore sit outside this section and are
         * untouched. If no attribute section is present (a server
         * tooltip that omits it), nothing is removed.
         */
        private static void removeVanillaAttributeSection(List<Component> lines) {
                int headerIndex = -1;
                for (int i = 0; i < lines.size(); i++) {
                        String text = lines.get(i).getString();
                        if (text.equals("When in Main Hand:")
                                        || text.equals("When in Off Hand:")
                                        || text.equals("When on Body:")
                                        || text.equals("When on Head:")
                                        || text.equals("When on Legs:")
                                        || text.equals("When on Feet:")) {
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
