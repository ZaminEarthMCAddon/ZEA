package net.zaminmc.zea.rarity;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Judgment calls about enchantments that the raw registry cannot make:
 * which ones are preference-dependent, and which member of a mutually
 * exclusive group is the meaningful suggestion when none is present.
 *
 * <p>Identification goes through registry keys ({@code minecraft:knockback}
 * and friends), never through display text, so resource packs and
 * translations cannot change classification.
 *
 * <p><b>Neutral enchantments</b> (the spec's examples plus the same
 * principle applied consistently): Knockback and Thorns — players
 * deliberately avoid or want them; Soul Speed and Swift Sneak — playstyle
 * choices that trade durability or utility rather than mark progression;
 * Frost Walker — the alternative to Depth Strider, not a step past it;
 * both curses — never progression. A neutral enchant contributes nothing
 * to rarity, is never suggested as a missing upgrade, and is never
 * required for Legendary.
 *
 * <p><b>Group leaders</b>: when an item has no enchantment from a mutually
 * exclusive group, only one member is a meaningful next target — the
 * general-purpose one a normal player would take first (Sharpness, not
 * Smite; Protection, not Fire Protection; Mending, not Infinity). Ranks
 * decide that member; ties fall back to the alphabetical registry key.
 */
public final class EnchantPolicy {
        /** Registry-key paths of enchantments treated as preference-dependent. */
        private static final java.util.Set<String> NEUTRAL = java.util.Set.of(
                        "knockback",
                        "thorns",
                        "soul_speed",
                        "swift_sneak",
                        "frost_walker",
                        "binding_curse",
                        "vanishing_curse");

        /**
         * Preferred member of each exclusive group, ranked so the
         * general-purpose enchantment wins (higher = more preferred).
         */
        private static final Map<String, Integer> GROUP_LEADER_RANK = Map.ofEntries(
                        // Damage enchantments: Sharpness is the all-around choice.
                        Map.entry("sharpness", 400),
                        Map.entry("smite", 300),
                        Map.entry("bane_of_arthropods", 200),
                        // Protection enchantments: Protection is the all-around choice.
                        Map.entry("protection", 400),
                        Map.entry("fire_protection", 300),
                        Map.entry("blast_protection", 250),
                        Map.entry("projectile_protection", 200),
                        // Durability: Mending over Infinity (they conflict).
                        Map.entry("mending", 400),
                        Map.entry("infinity", 300),
                        // Mining: Silk Touch over Fortune (they conflict).
                        Map.entry("silk_touch", 400),
                        Map.entry("fortune", 300),
                        // Trident movement: Loyalty first, Riptide second,
                        // Channeling last (Riptide excludes the other two).
                        Map.entry("loyalty", 400),
                        Map.entry("riptide", 300),
                        Map.entry("channeling", 200),
                        // Crossbow fire mode.
                        Map.entry("multishot", 400),
                        Map.entry("piercing", 300),
                        // Boots ice vs water (Frost Walker is also neutral).
                        Map.entry("depth_strider", 400),
                        Map.entry("frost_walker", 300));

        private EnchantPolicy() {
        }

        /** Whether the enchantment is preference-dependent and thus rarity-silent. */
        public static boolean isNeutral(Holder<Enchantment> enchantment) {
                return isNeutralPath(keyPath(enchantment));
        }

        /** The neutral set membership by registry-key path — the testable core. */
        static boolean isNeutralPath(String keyPath) {
                return NEUTRAL.contains(keyPath);
        }

        /**
         * The preferred absent enchantment of a mutually exclusive group:
         * the highest-ranked candidate, ties broken alphabetically by
         * registry key. {@code candidates} is never empty.
         */
        public static Holder<Enchantment> groupLeader(java.util.Collection<Holder<Enchantment>> candidates) {
                return groupLeaderByKey(candidates.stream()
                                                .map(candidate -> Map.entry(keyPath(candidate), candidate))
                                                .toList());
        }

        /**
         * The leader by registry-key path — the testable core of the
         * ranking. Entries are (keyPath, holder); ranking by the leader
         * table, ties alphabetically, unknown paths lowest.
         */
        static Holder<Enchantment> groupLeaderByKey(List<Map.Entry<String, Holder<Enchantment>>> candidates) {
                Map.Entry<String, Holder<Enchantment>> best = null;
                for (Map.Entry<String, Holder<Enchantment>> candidate : candidates) {
                        boolean wins = best == null || beats(candidate.getKey(), best.getKey());
                        if (wins) {
                                best = candidate;
                        }
                }
                return best.getValue();
        }

        /** Whether {@code candidate} ranks above {@code incumbent} by the leader table, then key. */
        private static boolean beats(String candidate, String incumbent) {
                int candidateRank = GROUP_LEADER_RANK.getOrDefault(candidate, 0);
                int incumbentRank = GROUP_LEADER_RANK.getOrDefault(incumbent, 0);
                if (candidateRank != incumbentRank) {
                        return candidateRank > incumbentRank;
                }
                return candidate.compareTo(incumbent) < 0;
        }

        private static String keyPath(Holder<Enchantment> enchantment) {
                Optional<net.minecraft.resources.ResourceKey<Enchantment>> key = enchantment.unwrapKey();
                return key.map(resourceKey -> resourceKey.identifier().getPath()).orElse("");
        }
}
