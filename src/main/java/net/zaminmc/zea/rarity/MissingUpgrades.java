package net.zaminmc.zea.rarity;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The meaningful remaining upgrades of a supported item — what the
 * shift-hover line shows. An upgrade is meaningful when:
 *
 * <ul>
 *   <li>the enchantment is compatible with the item's class (the budget's
 *       set already guarantees this),</li>
 *   <li>it is not neutral (preference-dependent enchants are never
 *       suggested),</li>
 *   <li>it is not already at its maximum level,</li>
 *   <li>it does not conflict with any enchantment the item already has —
 *       impossible combinations are never suggested, and</li>
 *   <li>when the item has no enchantment from a mutually exclusive group,
 *       only the group's leader is suggested — never all members.</li>
 * </ul>
 *
 * <p>The suggested level is the next one for an enchantment already
 * present (Sharpness IV → "Sharpness V") and the maximum level for one
 * that is absent entirely ("Efficiency V" on an unenchanted pickaxe) —
 * the meaningful target, never a lower level. The result is one
 * comma-separated block that the renderer wraps to the tooltip's usable
 * width; an empty result renders nothing at all.
 */
public final class MissingUpgrades {
        /** The header line above the gray list. */
        private static final String HEADER = "Missing enchantments:";

        private MissingUpgrades() {
        }

        /**
         * One suggested upgrade, ready to display.
         */
        public record Upgrade(String display) {
        }

        /**
         * The ordered missing upgrades for {@code present} levels against
         * the budget's compatible set. Output is sorted by display name;
         * duplicates are impossible by construction.
         */
        public static List<Upgrade> of(List<EnchantBudget.Compatible> compatible, ItemEnchantments present) {
                List<Upgrade> upgrades = new ArrayList<>();
                // Enchantments the item already carries, at any level, in
                // any compatibility — a present Frost Walker still blocks
                // suggesting Depth Strider.
                List<Holder<Enchantment>> held = List.copyOf(present.keySet());
                Map<Holder<Enchantment>, Integer> levels = new HashMap<>();
                present.entrySet().forEach(entry -> levels.put(entry.getKey(), entry.getIntValue()));

                // Absent candidates grouped by mutual exclusion, so groups
                // contribute exactly one suggestion: their leader.
                List<EnchantBudget.Compatible> absent = new ArrayList<>();
                for (EnchantBudget.Compatible candidate : compatible) {
                        int level = levels.getOrDefault(candidate.enchantment(), 0);
                        if (level >= candidate.maxLevel()) {
                                continue;
                        }
                        if (level > 0) {
                                upgrades.add(new Upgrade(candidate.displayAt(level + 1)));
                                continue;
                        }
                        if (conflictsWithHeld(candidate.enchantment(), held)) {
                                continue;
                        }
                        absent.add(candidate);
                }
                // Only absent candidates remain; group them by conflict and
                // keep the leader of each group.
                List<EnchantBudget.Compatible> leaders = pickGroupLeaders(absent);
                for (EnchantBudget.Compatible candidate : leaders) {
                        upgrades.add(new Upgrade(candidate.displayAt(candidate.maxLevel())));
                }
                // Present-below-max suggestions were collected while
                // scanning; sort everything so the list reads stably
                // regardless of the compatible set's order.
                upgrades.sort(java.util.Comparator.comparing(Upgrade::display));
                return upgrades;
        }

        /**
         * Renders the upgrades as tooltip lines: a header, then the list
         * joined with ", " and wrapped to {@code maxWidth} glyph units
         * using {@code width} as the width oracle. Comma boundaries are
         * preferred break points; an entry that cannot fit on any line is
         * placed alone rather than dropped. Returns an empty list when
         * there are no upgrades.
         */
        public static List<Component> render(List<Upgrade> upgrades, int maxWidth, ToIntFunction<String> width) {
                if (upgrades.isEmpty()) {
                        return List.of();
                }
                List<String> names = upgrades.stream().map(Upgrade::display).toList();
                List<Component> lines = new ArrayList<>();
                MutableComponent header = Component.literal(HEADER)
                                .withStyle(ChatFormatting.DARK_GRAY);
                lines.add(header);
                for (String line : wrap(names, maxWidth, width)) {
                        lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                }
                return lines;
        }

        /**
         * Greedy comma-aware wrapping: entries accumulate on the current
         * line while the joined text fits; the line breaks before the
         * entry that would overflow. A single entry wider than the whole
         * budget gets its own line (the tooltip can grow to that width —
         * it is chosen well below the screen edge).
         */
        static List<String> wrap(List<String> names, int maxWidth, ToIntFunction<String> width) {
                List<String> lines = new ArrayList<>();
                StringBuilder current = new StringBuilder();
                for (String name : names) {
                        if (current.length() == 0) {
                                current.append(name);
                                continue;
                        }
                        int joinedWidth = width.applyAsInt(current + ", " + name);
                        if (joinedWidth <= maxWidth) {
                                current.append(", ").append(name);
                                continue;
                        }
                        lines.add(current.toString());
                        current.setLength(0);
                        current.append(name);
                }
                if (current.length() > 0) {
                        lines.add(current.toString());
                }
                return lines;
        }

        private static boolean conflictsWithHeld(Holder<Enchantment> candidate,
                        List<Holder<Enchantment>> held) {
                return held.stream().anyMatch(
                                present -> !Enchantment.areCompatible(candidate, present));
        }

        /**
         * Keeps, for every cluster of mutually exclusive absent candidates,
         * only the leader. Clusters are found transitively: vanilla
         * exclusive groups are cliques, but the transitive walk keeps the
         * choice correct even if a future registry shapes conflicts
         * differently. Candidates without conflicts pass through.
         */
        private static List<EnchantBudget.Compatible> pickGroupLeaders(List<EnchantBudget.Compatible> absent) {
                List<EnchantBudget.Compatible> result = new ArrayList<>();
                List<EnchantBudget.Compatible> remaining = new ArrayList<>(absent);
                while (!remaining.isEmpty()) {
                        EnchantBudget.Compatible seed = remaining.remove(0);
                        List<Holder<Enchantment>> group = new ArrayList<>();
                        group.add(seed.enchantment());
                        List<EnchantBudget.Compatible> rest = new ArrayList<>();
                        for (EnchantBudget.Compatible candidate : remaining) {
                                if (!Enchantment.areCompatible(seed.enchantment(), candidate.enchantment())) {
                                        group.add(candidate.enchantment());
                                } else {
                                        rest.add(candidate);
                                }
                        }
                        remaining = rest;
                        Holder<Enchantment> leader = group.size() == 1
                                        ? seed.enchantment()
                                        : EnchantPolicy.groupLeader(group);
                        if (leader == seed.enchantment()) {
                                result.add(seed);
                        } else {
                                // The leader is a different member of the
                                // group; find its compatible record (its
                                // display name and max level are per-member).
                                result.add(recordFor(leader, absent));
                        }
                }
                return result;
        }

        private static EnchantBudget.Compatible recordFor(Holder<Enchantment> enchantment,
                        List<EnchantBudget.Compatible> absent) {
                return absent.stream()
                                .filter(candidate -> candidate.enchantment() == enchantment)
                                .findFirst()
                                .orElse(new EnchantBudget.Compatible(
                                                enchantment, enchantment.value().getMaxLevel()));
        }
}
