package net.zaminmc.zea.rarity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.misc.TooltipScroller;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The field test the worklog promised: the rarity visuals judged against
 * the real 26.3 data, not stand-ins.
 *
 * <p>Everything the user sees in game is driven by three inputs this test
 * replaces with the genuine article:
 *
 * <ul>
 *   <li><b>Real font metrics</b> — {@code fieldtest/mc_font_widths.json}
 *       is generated from the vanilla {@code ascii.png} with the exact
 *       advance algorithm of {@code BitmapProvider$Definition} (ink width
 *       + 1; the space provider pins 0x20 at 4), so the wrap oracle here
 *       and the game's {@code Font.width} agree pixel for pixel.</li>
 *   <li><b>Real enchantment sets</b> — names, maximum levels and
 *       exclusive groups transcribed from the 26.3 vanilla datapack
 *       ({@code data/minecraft/enchantment/*.json} and the
 *       {@code enchantable/*}, {@code exclusive_set/*} tags) for every
 *       supported class: sword, pickaxe, chestplate, helmet, boots.</li>
 *   <li><b>Real code</b> — the assertions run against the shipped
 *       {@link MissingUpgrades}, {@link RarityCalculator},
 *       {@link RarityEngine#chromaColor} and
 *       {@link TooltipScroller#computeAdjustment}.</li>
 * </ul>
 *
 * <p>When {@code -Dzea.fieldtest.dump=<path>} is set, each scenario also
 * writes its wrapped lines, colors and scroll sequences to a JSON file the
 * offline renderer turns into the field-test images (inventory tint,
 * shift-hover list, chroma animation, tooltip scrolling); without the
 * property the test is a pure, hermetic regression pass.
 */
class RarityFieldTest {
        static {
                VanillaBootstrap.ensure();
        }

        // ---------------------------------------------------------- fixtures

        /**
         * One real enchantment: registry-key path, display name and
         * maximum level exactly as the vanilla datapack ships them.
         */
        private record Real(String keyPath, String display, int maxLevel) {
        }

        /** The real non-neutral, non-curse enchantment sets per item class. */
        private static final Map<String, List<Real>> CLASS_SETS = Map.of(
                        "sword", List.of(
                                        new Real("sharpness", "Sharpness", 5),
                                        new Real("smite", "Smite", 5),
                                        new Real("bane_of_arthropods", "Bane of Arthropods", 5),
                                        new Real("fire_aspect", "Fire Aspect", 2),
                                        new Real("looting", "Looting", 3),
                                        new Real("mending", "Mending", 1),
                                        new Real("sweeping_edge", "Sweeping Edge", 3),
                                        new Real("unbreaking", "Unbreaking", 3)),
                        "pickaxe", List.of(
                                        new Real("efficiency", "Efficiency", 5),
                                        new Real("fortune", "Fortune", 3),
                                        new Real("mending", "Mending", 1),
                                        new Real("silk_touch", "Silk Touch", 1),
                                        new Real("unbreaking", "Unbreaking", 3)),
                        "chestplate", List.of(
                                        new Real("blast_protection", "Blast Protection", 4),
                                        new Real("fire_protection", "Fire Protection", 4),
                                        new Real("mending", "Mending", 1),
                                        new Real("projectile_protection", "Projectile Protection", 4),
                                        new Real("protection", "Protection", 4),
                                        new Real("unbreaking", "Unbreaking", 3)),
                        "helmet", List.of(
                                        new Real("aqua_affinity", "Aqua Affinity", 1),
                                        new Real("blast_protection", "Blast Protection", 4),
                                        new Real("fire_protection", "Fire Protection", 4),
                                        new Real("mending", "Mending", 1),
                                        new Real("projectile_protection", "Projectile Protection", 4),
                                        new Real("protection", "Protection", 4),
                                        new Real("respiration", "Respiration", 3),
                                        new Real("unbreaking", "Unbreaking", 3)),
                        "boots", List.of(
                                        new Real("blast_protection", "Blast Protection", 4),
                                        new Real("depth_strider", "Depth Strider", 3),
                                        new Real("feather_falling", "Feather Falling", 4),
                                        new Real("fire_protection", "Fire Protection", 4),
                                        new Real("mending", "Mending", 1),
                                        new Real("projectile_protection", "Projectile Protection", 4),
                                        new Real("protection", "Protection", 4),
                                        new Real("unbreaking", "Unbreaking", 3)));

        /**
         * The real exclusive groups that survive the neutral filter, by
         * display name: the damage, armor and mining sets of the vanilla
         * {@code exclusive_set} tags.
         */
        private static final List<List<String>> GROUPS = List.of(
                        List.of("Sharpness", "Smite", "Bane of Arthropods"),
                        List.of("Protection", "Blast Protection", "Fire Protection", "Projectile Protection"),
                        List.of("Fortune", "Silk Touch"));

        private final Map<String, Keyed> holders = new HashMap<>();

        /** A throwaway owner for the standalone references; never queried. */
        private static final HolderOwner<Enchantment> OWNER = new HolderOwner<>() {
        };

        /**
         * A registry-keyed reference, like the holders the live game
         * uses — group-leader ranking and neutral detection both read the
         * key. Created unbound, then bound to its value so mutually
         * exclusive members can reference each other. The tag set is
         * primed empty (the registry binding that would do this in game
         * never runs in a test JVM) because {@code getFullname} reads the
         * curse tag for every display string.
         */
        private static final class Keyed extends Holder.Reference<Enchantment> {
                private Keyed(ResourceKey<Enchantment> key) {
                        super(Holder.Reference.Type.STAND_ALONE, OWNER, key, null);
                        try {
                                var field = Holder.Reference.class.getDeclaredField("tags");
                                field.setAccessible(true);
                                if (field.get(this) == null) {
                                        field.set(this, java.util.Set.of());
                                }
                        } catch (ReflectiveOperationException e) {
                                throw new IllegalStateException(e);
                        }
                }

                private void bind(Enchantment value) {
                        bindValue(value);
                }
        }

        private Holder<Enchantment> holder(String display) {
                Keyed existing = holders.get(display);
                if (existing != null && existing.isBound()) {
                        return existing;
                }
                // Create every needed reference first (unbound), so group
                // members can carry each other in their exclusive sets,
                // then build and bind the values.
                List<String> needed = new ArrayList<>(List.of(display));
                for (List<String> group : GROUPS) {
                        if (group.contains(display)) {
                                needed = group;
                        }
                }
                for (String member : needed) {
                        holders.computeIfAbsent(member, key -> new Keyed(ResourceKey.create(
                                        Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("minecraft", keyPathOf(key)))));
                }
                for (String member : needed) {
                        HolderSet<Enchantment> exclusive = HolderSet.empty();
                        List<String> group = groupOf(member);
                        if (group != null && group.size() > 1) {
                                List<Holder<Enchantment>> others = new ArrayList<>();
                                for (String other : group) {
                                        if (!other.equals(member)) {
                                                others.add(holders.get(other));
                                        }
                                }
                                exclusive = HolderSet.direct(others);
                        }
                        holders.get(member).bind(new Enchantment(
                                        Component.literal(member),
                                        Enchantment.definition(
                                                        HolderSet.empty(),
                                                        10,
                                                        maxLevelOf(member),
                                                        Enchantment.constantCost(1),
                                                        Enchantment.constantCost(1),
                                                        0,
                                                        EquipmentSlotGroup.ANY),
                                        exclusive,
                                        DataComponentMap.EMPTY));
                }
                return holders.get(display);
        }

        private String keyPathOf(String display) {
                for (List<Real> set : CLASS_SETS.values()) {
                        for (Real real : set) {
                                if (real.display().equals(display)) {
                                        return real.keyPath();
                                }
                        }
                }
                throw new IllegalArgumentException(display);
        }

        private List<String> groupOf(String display) {
                for (List<String> group : GROUPS) {
                        if (group.contains(display)) {
                                return group;
                        }
                }
                return null;
        }

        private int maxLevelOf(String display) {
                return CLASS_SETS.values().stream()
                                .flatMap(List::stream)
                                .filter(real -> real.display().equals(display))
                                .mapToInt(Real::maxLevel)
                                .max().orElse(5);
        }

        private List<EnchantBudget.Compatible> compatible(String itemClass) {
                List<EnchantBudget.Compatible> list = new ArrayList<>();
                for (Real real : CLASS_SETS.get(itemClass)) {
                        list.add(new EnchantBudget.Compatible(holder(real.display()), real.maxLevel()));
                }
                return list;
        }

        private static ItemEnchantments enchantments(RarityFieldTest test, String... displayLevelPairs) {
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                for (int i = 0; i < displayLevelPairs.length; i += 2) {
                        mutable.set(test.holder(displayLevelPairs[i]), Integer.parseInt(displayLevelPairs[i + 1]));
                }
                return mutable.toImmutable();
        }

        // ---------------------------------------------------- width oracle

        /** The real per-character advances of the vanilla default font. */
        private static final int[] FONT_WIDTHS = loadFontWidths();

        private static final ToIntFunction<String> REAL_WIDTH = text -> {
                int sum = 0;
                for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        sum += c < 128 ? FONT_WIDTHS[c] : 6;
                }
                return sum;
        };

        private static int[] loadFontWidths() {
                int[] table = new int[128];
                try (InputStream in = RarityFieldTest.class.getResourceAsStream("/fieldtest/mc_font_widths.json")) {
                        if (in == null) {
                                throw new IllegalStateException("mc_font_widths.json missing from test resources");
                        }
                        Type type = new TypeToken<Map<String, Integer>>() {
                        }.getType();
                        Map<String, Integer> raw = new Gson().fromJson(
                                        new InputStreamReader(in, StandardCharsets.UTF_8), type);
                        raw.forEach((codepoint, advance) -> {
                                table[Integer.parseInt(codepoint)] = advance;
                        });
                } catch (Exception e) {
                        throw new IllegalStateException("font width table unreadable", e);
                }
                return table;
        }

        /** The wrap budget {@code RarityTooltipEditor} derives for a GUI width. */
        private static int budgetFor(int guiWidth) {
                return Math.max(120, Math.min(240, guiWidth - 40));
        }

        // ------------------------------------------------------- rendering

        /** The missing-upgrade block for one scenario, with real widths. */
        private List<Component> missingBlock(String itemClass, ItemEnchantments held, int guiWidth) {
                List<MissingUpgrades.Upgrade> upgrades = MissingUpgrades.of(compatible(itemClass), held);
                return MissingUpgrades.render(upgrades, budgetFor(guiWidth), REAL_WIDTH);
        }

        // =========================================================== tests

        @Test
        void realNameLengthsWrapInsideTheTooltipBudget() {
                int budget = budgetFor(480); // GUI scale 4 on 1920x1080
                assertEquals(240, budget);

                Map<String, List<String>> expected = Map.of(
                                "sword", List.of("Fire Aspect II", "Looting III", "Mending",
                                                "Sharpness V", "Sweeping Edge III", "Unbreaking III"),
                                "pickaxe", List.of("Efficiency V", "Mending", "Silk Touch", "Unbreaking III"),
                                "chestplate", List.of("Mending", "Protection IV", "Unbreaking III"),
                                "helmet", List.of("Aqua Affinity", "Mending", "Protection IV",
                                                "Respiration III", "Unbreaking III"),
                                "boots", List.of("Depth Strider III", "Feather Falling IV", "Mending",
                                                "Protection IV", "Unbreaking III"));

                Map<String, Integer> joinedWidths = new LinkedHashMap<>();
                for (var entry : expected.entrySet()) {
                        List<Component> block = missingBlock(entry.getKey(), ItemEnchantments.EMPTY, 480);
                        assertEquals("Missing enchantments:", block.get(0).getString());
                        List<String> lines = block.stream().skip(1).map(Component::getString).toList();
                        // Every real name made it through, comma-joined.
                        String joined = String.join(", ", lines);
                        for (String upgrade : entry.getValue()) {
                                assertTrue(joined.contains(upgrade),
                                                entry.getKey() + " lost " + upgrade + " in [" + joined + "]");
                        }
                        // No line may exceed the budget, and the block must
                        // not degenerate into one entry per line.
                        for (String line : lines) {
                                assertTrue(REAL_WIDTH.applyAsInt(line) <= budget,
                                                entry.getKey() + " line overflows: " + line);
                        }
                        assertTrue(lines.size() < entry.getValue().size(),
                                        entry.getKey() + " wrapped one-per-line");
                        // The joined text really is too wide for one line,
                        // i.e. wrapping was necessary, not cosmetic.
                        joinedWidths.put(entry.getKey(), REAL_WIDTH.applyAsInt(joined));
                }
                assertTrue(joinedWidths.get("sword") > budget, "sword list should need wrapping");
                dump();
        }

        @Test
        void longestRealEntryCanNeverOverflowAlone() {
                // Every display string any class can ever produce — absent
                // maxima and next-level suggestions alike — fits the budget
                // on a single line, so the wrap's orphan branch (a single
                // over-long entry alone on its line) cannot trigger with
                // real data.
                int budget = budgetFor(480);
                for (List<Real> set : CLASS_SETS.values()) {
                        for (Real real : set) {
                                for (int level = 1; level <= real.maxLevel(); level++) {
                                        // Vanilla appends the numeral unless the
                                        // enchantment tops out at 1 (getFullname).
                                        String display = level == 1 && real.maxLevel() == 1
                                                        ? real.display()
                                                        : real.display() + " " + roman(level);
                                        int width = REAL_WIDTH.applyAsInt(display);
                                        assertTrue(width <= budget, display + " is " + width + " wide");
                                }
                        }
                }
        }

        @Test
        void sharpnessFourSwordSuggestsFiveNotTheWholeDamageGroup() {
                // Sharpness IV is held: the suggestion is the next level
                // (Sharpness V), and the group members Smite and Bane of
                // Arthropods are impossible and absent from the list.
                List<Component> block = missingBlock("sword",
                                enchantments(this, "Sharpness", "4"), 480);
                List<String> lines = block.stream().skip(1).map(Component::getString).toList();
                String joined = String.join(", ", lines);
                assertTrue(joined.contains("Sharpness V"));
                assertTrue(!joined.contains("Smite"));
                assertTrue(!joined.contains("Bane of Arthropods"));
        }

        @Test
        void nearlyMaxedSwordShowsOneEntry() {
                ItemEnchantments held = enchantments(this,
                                "Sharpness", "5", "Fire Aspect", "1", "Looting", "3",
                                "Mending", "1", "Sweeping Edge", "3", "Unbreaking", "3");
                List<Component> block = missingBlock("sword", held, 480);
                assertEquals(2, block.size()); // header + one line
                assertEquals("Fire Aspect II", block.get(1).getString());
        }

        @Test
        void fullyMaxedSwordRendersNothing() {
                ItemEnchantments held = enchantments(this,
                                "Sharpness", "5", "Fire Aspect", "2", "Looting", "3",
                                "Mending", "1", "Sweeping Edge", "3", "Unbreaking", "3");
                List<Component> block = missingBlock("sword", held, 480);
                assertTrue(block.isEmpty());
        }

        @Test
        void narrowWindowsShrinkTheBudgetNeverTheScreenEdge() {
                // The budget formula: min(240, guiWidth - 40), floored at
                // 120. At every width the tooltip box (budget + 8 padding
                // + 6 border) plus the scroller margin stays inside the
                // GUI — the shift line can never touch the screen edge.
                for (int guiWidth : List.of(480, 427, 320, 240, 160)) {
                        int budget = budgetFor(guiWidth);
                        int tooltipWidth = budget + 8 + 6;
                        assertTrue(tooltipWidth + 2 * 6 <= guiWidth,
                                        "budget " + budget + " leaks at gui width " + guiWidth);
                }
                assertEquals(240, budgetFor(480));
                assertEquals(200, budgetFor(240));
                assertEquals(120, budgetFor(160));
        }

        @Test
        void realBudgetsAndRaritiesMatchTheDocumentedModel() {
                // The maximum fill of each class, from the real sets with
                // exclusive groups counted once — the M of the calculator.
                Map<String, Double> expectedMaxFill = Map.of(
                                "sword", 6.0, "pickaxe", 4.0, "chestplate", 3.0,
                                "helmet", 5.0, "boots", 5.0);
                for (var entry : expectedMaxFill.entrySet()) {
                        double maxFill = 0.0;
                        List<EnchantBudget.Compatible> selected = new ArrayList<>();
                        for (EnchantBudget.Compatible candidate : compatible(entry.getKey())) {
                                boolean blocked = selected.stream().anyMatch(
                                                chosen -> !Enchantment.areCompatible(
                                                                candidate.enchantment(), chosen.enchantment()));
                                if (!blocked) {
                                        selected.add(candidate);
                                        maxFill += candidate.fillAt(candidate.maxLevel());
                                }
                        }
                        assertEquals(entry.getValue(), maxFill, 1e-9, entry.getKey() + " max fill");
                }

                // Material-only rarities (unenchanted), via the real
                // calculator and the tier ladder of MaterialTier.
                assertEquals(ItemRarity.COMMON, RarityCalculator.calculate(0, 0.0, 6.0)); // wooden sword
                assertEquals(ItemRarity.COMMON, RarityCalculator.calculate(4, 0.0, 6.0)); // iron sword
                assertEquals(ItemRarity.UNCOMMON, RarityCalculator.calculate(5, 0.0, 6.0)); // diamond sword
                assertEquals(ItemRarity.EPIC, RarityCalculator.calculate(6, 0.0, 6.0)); // netherite sword
                assertEquals(ItemRarity.UNCOMMON, RarityCalculator.calculate(5, 0.0, 3.0)); // diamond chestplate

                // The hypermaxed netherite sword: every compatible
                // non-neutral leader at its maximum — F = M = 6.0, P = 1.0.
                ItemEnchantments maxed = enchantments(this,
                                "Sharpness", "5", "Fire Aspect", "2", "Looting", "3",
                                "Mending", "1", "Sweeping Edge", "3", "Unbreaking", "3");
                double fill = 0.0;
                for (var e : maxed.entrySet()) {
                        for (EnchantBudget.Compatible c : compatible("sword")) {
                                if (c.enchantment() == e.getKey()) {
                                        fill += c.fillAt(e.getIntValue());
                                }
                        }
                }
                assertEquals(6.0, fill, 1e-9);
                assertEquals(ItemRarity.LEGENDARY, RarityCalculator.calculate(6, fill, 6.0));

                // Half-built: Sharpness V + Unbreaking III on a diamond
                // sword is P = 2/6 < 0.30 — enchant rarity 0, material 1.
                ItemEnchantments half = enchantments(this, "Sharpness", "5", "Unbreaking", "3");
                double halfFill = 0.0;
                for (var e : half.entrySet()) {
                        for (EnchantBudget.Compatible c : compatible("sword")) {
                                if (c.enchantment() == e.getKey()) {
                                        halfFill += c.fillAt(e.getIntValue());
                                }
                        }
                }
                assertEquals(2.0, halfFill, 1e-9);
                assertEquals(ItemRarity.UNCOMMON, RarityCalculator.calculate(5, halfFill, 6.0));

                // Maxed diamond boots: P = 1.0, F = 5.0 ≥ 3.0 → Legendary.
                ItemEnchantments maxedBoots = enchantments(this,
                                "Protection", "4", "Depth Strider", "3", "Feather Falling", "4",
                                "Mending", "1", "Unbreaking", "3");
                double bootsFill = 0.0;
                for (var e : maxedBoots.entrySet()) {
                        for (EnchantBudget.Compatible c : compatible("boots")) {
                                if (c.enchantment() == e.getKey()) {
                                        bootsFill += c.fillAt(e.getIntValue());
                                }
                        }
                }
                assertEquals(5.0, bootsFill, 1e-9);
                assertEquals(ItemRarity.LEGENDARY, RarityCalculator.calculate(5, bootsFill, 5.0));
        }

        @Test
        void sameEnchantmentsScoreIdenticallyOnEveryClient() {
                // Two players see the same sword through different holder
                // instances — each client's registry interns its own — so
                // the identity-keyed component maps hold different objects
                // and can walk their entries in different orders. Each
                // client sums the fill over its own budget list in the
                // canonical registry-key order (the iteration
                // RarityEngine.fillOf performs, reading levels through
                // getLevel); the key order is identical on both clients,
                // so the two fills must agree bitwise — never just within
                // an epsilon.
                RarityFieldTest otherClient = new RarityFieldTest();
                ItemEnchantments local = enchantments(this,
                                "Sharpness", "4", "Fire Aspect", "1", "Looting", "2",
                                "Mending", "1", "Unbreaking", "3");
                ItemEnchantments remote = enchantments(otherClient,
                                "Unbreaking", "3", "Mending", "1", "Looting", "2",
                                "Fire Aspect", "1", "Sharpness", "4");

                double localFill = canonicalFill(compatible("sword"), local);
                double remoteFill = canonicalFill(otherClient.compatible("sword"), remote);

                // Sharpness IV 4/5 + Fire Aspect I 1/2 + Looting II 2/3
                // + Mending 1 + Unbreaking 1 ≈ 3.97 — Epic progress on a
                // netherite sword either way.
                assertEquals(localFill, remoteFill);
                assertEquals(4.0 / 5 + 1.0 / 2 + 2.0 / 3 + 1.0 + 1.0, localFill, 1e-9);
                assertEquals(ItemRarity.EPIC, RarityCalculator.calculate(6, localFill, 6.0));
                assertEquals(ItemRarity.EPIC, RarityCalculator.calculate(6, remoteFill, 6.0));
        }

        /**
         * The fill as RarityEngine.fillOf computes it: the budget's list
         * in canonical registry-key order, each level read through
         * getLevel from the client's own component.
         */
        private static double canonicalFill(List<EnchantBudget.Compatible> budget,
                        ItemEnchantments held) {
                List<EnchantBudget.Compatible> canonical = new ArrayList<>(budget);
                canonical.sort(Comparator.comparing(candidate -> candidate.enchantment()
                                .unwrapKey().orElseThrow().identifier().toString()));
                double fill = 0.0;
                for (EnchantBudget.Compatible candidate : canonical) {
                        int level = held.getLevel(candidate.enchantment());
                        if (level > 0) {
                                fill += candidate.fillAt(level);
                        }
                }
                return fill;
        }

        @Test
        void chromaCycleIsExactlyOneRotationPerPeriod() {
                assertEquals(1800L, RarityEngine.CHROMA_PERIOD_MILLIS);
                int start = RarityEngine.chromaColor(0L);
                int half = RarityEngine.chromaColor(900L);
                int full = RarityEngine.chromaColor(1800L);
                assertEquals(start, full, "one period must return to the same color");
                assertTrue(start != half, "half a period must move the hue");
                // The saturation and brightness the readability check
                // relies on (HSV .85/1.0): every sample fully opaque.
                for (long t = 0; t < 1800; t += 50) {
                        int rgb = RarityEngine.chromaColor(t);
                        assertEquals(0xFF000000, rgb & 0xFF000000);
                }
        }

        @Test
        void scrollerKeepsRealTooltipsInsideRealScreens() {
                // A 30-line container tooltip (300 px) on the scale-4 GUI
                // (480x270): vanilla pins it with the top cut off; the
                // scroller starts it at the top margin and walks it down
                // notch by notch, never leaving the 6-px margins.
                // (The margin constant itself is package-private to
                // misc; TooltipScrollerTest in that package locks it.)
                final int margin = 6;
                int guiWidth = 480, guiHeight = 270, width = 140, height = 300;
                int vanillaX = 252, vanillaY = guiHeight - height - 3; // DefaultTooltipPositioner
                assertEquals(-33, vanillaY);

                // Reset: top edge to the margin.
                double scrollY = margin - vanillaY;
                TooltipScroller.Adjustment top = TooltipScroller.computeAdjustment(
                                vanillaX, vanillaY, width, height, guiWidth, guiHeight, 0, scrollY);
                assertEquals(39.0, top.scrollY(), 1e-9);
                assertEquals(margin, vanillaY + (int) top.scrollY());

                // Five notches down (50 px): the bottom reaches the margin
                // exactly — clamped, not overshot.
                double desired = scrollY - 50;
                TooltipScroller.Adjustment bottom = TooltipScroller.computeAdjustment(
                                vanillaX, vanillaY, width, height, guiWidth, guiHeight, 0, desired);
                assertEquals(guiHeight - margin - height - vanillaY, bottom.scrollY(), 1e-9);

                // Eight notches up from the bottom: clamped back at the top.
                TooltipScroller.Adjustment back = TooltipScroller.computeAdjustment(
                                vanillaX, vanillaY, width, height, guiWidth, guiHeight, 0,
                                bottom.scrollY() + 80);
                assertEquals(top.scrollY(), back.scrollY(), 1e-9);

                // The widest tooltip ZEA itself can build — the shift list
                // at budget 240 plus box padding — never needs horizontal
                // scrolling on any real GUI width.
                int widest = budgetFor(480) + 8 + 6;
                assertTrue(widest + 2 * margin <= 480);
        }

        // ------------------------------------------------------------ dump

        /**
         * Writes the field-test JSON for the offline renderer: the five
         * class scenarios with their wrapped lines, a maxed-sword tooltip
         * with chroma samples at animation-frame spacing, the inventory
         * demo items with their rarities, and the scroller sequences. No-op
         * without {@code -Dzea.fieldtest.dump}.
         */
        private void dump() {
                String path = System.getProperty("zea.fieldtest.dump", "");
                if (path == null || path.isBlank()) {
                        return;
                }
                Gson gson = new Gson();
                Map<String, Object> root = new LinkedHashMap<>();

                Map<String, Object> scenarios = new LinkedHashMap<>();
                for (String itemClass : List.of("sword", "pickaxe", "chestplate", "helmet", "boots")) {
                        List<Component> block = missingBlock(itemClass, ItemEnchantments.EMPTY, 480);
                        Map<String, Object> scenario = new LinkedHashMap<>();
                        scenario.put("lines", block.stream().map(Component::getString).toList());
                        scenario.put("widths", block.stream()
                                        .map(line -> REAL_WIDTH.applyAsInt(line.getString())).toList());
                        scenarios.put(itemClass, scenario);
                }
                // The nearly-maxed sword: exactly one entry left.
                List<Component> oneLeft = missingBlock("sword", enchantments(this,
                                "Sharpness", "5", "Fire Aspect", "1", "Looting", "3",
                                "Mending", "1", "Sweeping Edge", "3", "Unbreaking", "3"), 480);
                Map<String, Object> oneLeftScenario = new LinkedHashMap<>();
                oneLeftScenario.put("lines", oneLeft.stream().map(Component::getString).toList());
                oneLeftScenario.put("widths", oneLeft.stream()
                                .map(line -> REAL_WIDTH.applyAsInt(line.getString())).toList());
                scenarios.put("sword_one_left", oneLeftScenario);
                root.put("scenarios", scenarios);

                // Chroma at 60 fps spacing for one full cycle.
                Map<String, Object> chroma = new LinkedHashMap<>();
                chroma.put("periodMillis", RarityEngine.CHROMA_PERIOD_MILLIS);
                List<List<Integer>> samples = new ArrayList<>();
                int frames = 108; // 1.8 s at 60 fps
                for (int k = 0; k <= frames; k++) {
                        long t = Math.round(k * (double) RarityEngine.CHROMA_PERIOD_MILLIS / frames);
                        samples.add(List.of((int) t, RarityEngine.chromaColor(t) & 0xFFFFFF));
                }
                chroma.put("samples", samples);
                root.put("chroma", chroma);

                Map<String, Object> swordMaxed = new LinkedHashMap<>();
                swordMaxed.put("lines", List.of("Netherite Sword",
                                "Sharpness V", "Sweeping Edge III", "Fire Aspect II",
                                "Looting III", "Unbreaking III", "Mending", "LEGENDARY"));
                root.put("maxedSwordTooltip", swordMaxed);
                root.put("rarityColors", Map.of(
                                "COMMON", ItemRarity.COMMON.rgb(),
                                "UNCOMMON", ItemRarity.UNCOMMON.rgb(),
                                "EPIC", ItemRarity.EPIC.rgb(),
                                "LEGENDARY", ItemRarity.LEGENDARY.rgb()));

                // The inventory demo items: material tier, fill and rarity
                // through the real calculator, plus the slot-tint color the
                // renderer blends at 25%.
                List<Map<String, Object>> inventory = new ArrayList<>();
                inventory.add(inventoryItem("wooden_sword", "Wooden Sword", 0, 0.0, 6.0));
                inventory.add(inventoryItem("golden_sword", "Golden Sword", 1, 0.0, 6.0));
                inventory.add(inventoryItem("stone_sword", "Stone Sword", 2, 0.0, 6.0));
                inventory.add(inventoryItem("iron_sword", "Iron Sword", 4, 0.0, 6.0));
                inventory.add(inventoryItem("diamond_sword", "Diamond Sword", 5, 0.0, 6.0));
                inventory.add(inventoryItem("netherite_sword", "Netherite Sword", 6, 0.0, 6.0));
                // Sharpness V + Unbreaking III on diamond: P = 1/3, Uncommon.
                inventory.add(inventoryItem("diamond_sword_half", "Diamond Sword", 5, 2.0, 6.0));
                // The hypermaxed netherite sword and diamond boots.
                inventory.add(inventoryItem("netherite_sword_maxed", "Netherite Sword", 6, 6.0, 6.0));
                inventory.add(inventoryItem("diamond_chestplate", "Diamond Chestplate", 5, 0.0, 3.0));
                inventory.add(inventoryItem("diamond_boots_maxed", "Diamond Boots", 5, 5.0, 5.0));
                inventory.add(inventoryItem("diamond_pickaxe", "Diamond Pickaxe", 5, 0.0, 4.0));
                root.put("inventory", inventory);

                // Scroller sequences on the scale-4 GUI (1920x1080, gui
                // 480x270), through the real computeAdjustment math.
                Map<String, Object> scroller = new LinkedHashMap<>();
                scroller.put("guiWidth", 480);
                scroller.put("guiHeight", 270);
                scroller.put("margin", 6);
                scroller.put("step", 10);

                // A 30-line container tooltip (300 px tall): vanilla pins
                // it with the top cut off; the scroller walks it down.
                int vx = 252, vy = 270 - 300 - 3;
                Map<String, Object> tall = new LinkedHashMap<>();
                tall.put("vanillaX", vx);
                tall.put("vanillaY", vy);
                tall.put("width", 140);
                tall.put("height", 300);
                List<List<Integer>> positions = new ArrayList<>();
                double scrollY = 6 - vy; // reset: top edge at the margin
                TooltipScroller.Adjustment adjusted = TooltipScroller.computeAdjustment(
                                vx, vy, 140, 300, 480, 270, 0, scrollY);
                scrollY = adjusted.scrollY();
                positions.add(List.of(vx + (int) scrollY, vy + (int) scrollY));
                for (int notch = 0; notch < 8; notch++) {
                        scrollY -= 10; // one wheel notch down
                        adjusted = TooltipScroller.computeAdjustment(vx, vy, 140, 300, 480, 270, 0, scrollY);
                        scrollY = adjusted.scrollY();
                        positions.add(List.of(vx + (int) scrollY, vy + (int) scrollY));
                }
                for (int notch = 0; notch < 12; notch++) {
                        scrollY += 10; // wheel back up
                        adjusted = TooltipScroller.computeAdjustment(vx, vy, 140, 300, 480, 270, 0, scrollY);
                        scrollY = adjusted.scrollY();
                        positions.add(List.of(vx + (int) scrollY, vy + (int) scrollY));
                }
                tall.put("positions", positions);
                scroller.put("containerTall", tall);

                // The same tooltip on a MoulConfig screen: the scroller
                // never engages there, so the position stays vanilla —
                // the render shows the cut-off top as vanilla leaves it.
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("vanillaX", vx);
                config.put("vanillaY", vy);
                config.put("width", 140);
                config.put("height", 300);
                config.put("engaged", false);
                scroller.put("moulconfigTall", config);

                // A wide tooltip (a shift list that reached 240 px plus
                // padding) never scrolls horizontally on a real GUI width.
                Map<String, Object> wide = new LinkedHashMap<>();
                wide.put("tooltipWidth", budgetFor(480) + 8 + 6);
                wide.put("guiWidth", 480);
                scroller.put("widestShiftList", wide);
                root.put("scroller", scroller);

                try {
                    Files.createDirectories(Path.of(path).getParent());
                    Files.writeString(Path.of(path), gson.toJson(root));
                } catch (Exception e) {
                    throw new IllegalStateException("field test dump failed", e);
                }
        }

        private Map<String, Object> inventoryItem(String id, String name, int tier, double fill, double maxFill) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id);
                item.put("name", name);
                item.put("tier", tier);
                item.put("rarity", RarityCalculator.calculate(tier, fill, maxFill).name());
                return item;
        }

        private static String roman(int level) {
                String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
                return numerals[Math.min(level, 10) - 1];
        }
}
