#!/usr/bin/env python3
"""Generates RarityTables.java — the mapped-out rarity lookup.

The rarity engine no longer derives its enchantment budget from the
live registry at runtime: EarthMC syncs custom enchantments into the
enchantment registry, and every budget computed on such a world
wobbles with whatever the server shipped that day. Instead, the whole
rarity outcome space is mapped out once, offline, from the vanilla
26.3 data extracted from the client jar
(data/minecraft/enchantment/*.json plus the exclusive_set and
enchantable item tags) and frozen as generated lookup tables:

  - per item family, the scorable (non-neutral) enchantment universe:
    path, max level, conflict edges;
  - every reachable fill numerator (the exact integer arithmetic the
    runtime uses), enumerated over all legitimate level combinations
    with conflict-pruned states;
  - the rarity ordinal for every (family, material tier, numerator)
    combination, computed with the documented reference rule
    (RarityCalculator's thresholds), checked into the source.

The runtime never divides, never compares against progress thresholds
and never asks the live registry anything: it computes the fill
numerator from the pinned universe, binary-searches the family's
reachable numerators and reads the rarity ordinal.

Re-run after a Minecraft data change:
    python3 scripts/gen_rarity_table.py
"""

import itertools
import os
from math import gcd

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "src", "main", "java", "net", "zaminmc", "zea", "rarity",
                   "RarityTables.java")

# The RarityCalculator reference constants (java doubles, kept identical).
UNCOMMON_PROGRESS = 0.30
EPIC_PROGRESS = 0.55
LEGENDARY_PROGRESS = 0.85
LEGENDARY_MIN_FILL = 3.0

MAX_TIER_CEILING_UNCOMMON = 1
MAX_TIER_CEILING_EPIC = 4

MATERIAL_TIERS = 7  # 0..6


def lcm(a, b):
    return a * b // gcd(a, b)


class Family:
    """One item family: scorable universe + conflict edges + fixed tier."""

    def __init__(self, category, enchants, edges, comment):
        self.category = category
        # enchants: list of (path, maxLevel); edges: list of frozenset pairs
        self.enchants = enchants
        self.edges = [frozenset(e) for e in edges]
        self.comment = comment
        self.denominator = 0
        self.reachable = []       # sorted distinct numerators
        self.max_numerator = 0
        self.tiers = None         # [materialTier][index] -> rarity ordinal

    def unit(self, max_level):
        return self.denominator // max_level

    def conflicts(self, state):
        for edge in self.edges:
            if all(state.get(path, 0) > 0 for path in edge):
                return True
        return False

    def enumerate_states(self):
        """Every legitimate level combination, conflict-pruned."""
        domains = [[0] + [self.unit(m) * l for l in range(1, m + 1)]
                   for (_, m) in self.enchants]
        paths = [p for (p, _) in self.enchants]
        numerators = set()
        for combo in itertools.product(*domains):
            state = dict(zip(paths, combo))
            if not self.conflicts(state):
                numerators.add(sum(combo))
        return sorted(numerators)

    def build(self):
        maxes = [m for (_, m) in self.enchants]
        d = 1
        for m in maxes:
            d = lcm(d, m)
        self.denominator = d
        self.reachable = self.enumerate_states()
        self.max_numerator = self.reachable[-1]
        # The reference rule runs on the doubles the runtime's callers
        # produce — RarityCalculator.calculate receives fill and maxFill
        # as ratios of the same denominator and divides them itself —
        # so the generator applies the rule to exactly those doubles.
        # Computing the progress from raw numerators instead would round
        # differently and the boundary cells could drift by one tier.
        max_fill_d = self.max_numerator / self.denominator
        self.tiers = [[self.reference(tier,
                                      (fill / self.denominator) / max_fill_d,
                                      fill / self.denominator)
                       for fill in self.reachable]
                      for tier in range(MATERIAL_TIERS)]
        return self

    @staticmethod
    def reference(material_tier, progress, fill):
        """RarityCalculator.calculate, in the generator's own integers."""
        material_rarity = max(0, min(2, material_tier - 4))
        enchant_rarity = 0
        if progress >= LEGENDARY_PROGRESS and fill is not None \
                and fill >= LEGENDARY_MIN_FILL:
            enchant_rarity = 3
        elif progress >= EPIC_PROGRESS:
            enchant_rarity = 2
        elif progress >= UNCOMMON_PROGRESS:
            enchant_rarity = 1
        if material_tier <= MAX_TIER_CEILING_UNCOMMON:
            ceiling = 1
        elif material_tier <= MAX_TIER_CEILING_EPIC:
            ceiling = 2
        else:
            ceiling = 3
        return max(0, min(ceiling, max(material_rarity, enchant_rarity)))

    def tiers_java(self):
        rows = []
        for tier in range(MATERIAL_TIERS):
            row = self.tiers[tier]
            rows.append("                {%s}" % ", ".join(str(v) for v in row))
        return "new int[][] {\n" + ",\n".join(rows) + "}"


# The 26.3 vanilla universe, transcribed from the client jar's
# data/minecraft/enchantment/*.json and the exclusive_set tags.
# Neutral enchantments (Knockback, Thorns, Soul Speed, Swift Sneak,
# Frost Walker, both curses — EnchantPolicy's set) are absent by
# construction: they are rarity-silent in either engine.
FAMILIES = [
    Family("SWORD", [
        ("sharpness", 5), ("smite", 5), ("bane_of_arthropods", 5),
        ("looting", 3), ("sweeping_edge", 3), ("fire_aspect", 2),
        ("mending", 1), ("unbreaking", 3)],
        [("sharpness", "smite"), ("sharpness", "bane_of_arthropods"),
         ("smite", "bane_of_arthropods")],
        "swords: the damage clique, Looting, Sweeping Edge, Fire Aspect, Mending, Unbreaking"),
    Family("AXE", [
        ("sharpness", 5), ("smite", 5), ("bane_of_arthropods", 5),
        ("efficiency", 5), ("fortune", 3), ("silk_touch", 1),
        ("looting", 3), ("mending", 1), ("unbreaking", 3)],
        [("sharpness", "smite"), ("sharpness", "bane_of_arthropods"),
         ("smite", "bane_of_arthropods"), ("fortune", "silk_touch")],
        "axes: the damage clique, Efficiency, the mining-loot pair, Looting, Mending, Unbreaking"),
    Family("PICKAXE", [
        ("efficiency", 5), ("fortune", 3), ("silk_touch", 1),
        ("mending", 1), ("unbreaking", 3)],
        [("fortune", "silk_touch")],
        "pickaxes: Efficiency, the mining-loot pair, Mending, Unbreaking"),
    Family("SHOVEL", [
        ("efficiency", 5), ("fortune", 3), ("silk_touch", 1),
        ("mending", 1), ("unbreaking", 3)],
        [("fortune", "silk_touch")],
        "shovels: Efficiency, the mining-loot pair, Mending, Unbreaking"),
    Family("HOE", [
        ("efficiency", 5), ("fortune", 3), ("silk_touch", 1),
        ("mending", 1), ("unbreaking", 3)],
        [("fortune", "silk_touch")],
        "hoes: Efficiency, the mining-loot pair, Mending, Unbreaking"),
    Family("SPEAR", [
        ("lunge", 3), ("looting", 3), ("fire_aspect", 2),
        ("mending", 1), ("unbreaking", 3)],
        [],
        "spears: Lunge, Looting, Fire Aspect, Mending, Unbreaking"),
    Family("MACE", [
        ("density", 5), ("breach", 4), ("wind_burst", 3),
        ("fire_aspect", 2), ("mending", 1), ("unbreaking", 3)],
        [("density", "breach")],
        "maces: the damage clique (Density, Breach), Wind Burst, Fire Aspect, Mending, Unbreaking"),
    Family("BOW", [
        ("power", 5), ("punch", 2), ("flame", 1),
        ("mending", 1), ("infinity", 1)],
        [("mending", "infinity")],
        "bows: Power, Punch, Flame, the Mending/Infinity pair"),
    Family("CROSSBOW", [
        ("multishot", 1), ("piercing", 4), ("quick_charge", 3),
        ("mending", 1), ("unbreaking", 3)],
        [("multishot", "piercing")],
        "crossbows: the fire-mode pair, Quick Charge, Mending, Unbreaking"),
    Family("TRIDENT", [
        ("impaling", 5), ("loyalty", 3), ("riptide", 3), ("channeling", 1),
        ("mending", 1), ("unbreaking", 3)],
        [("loyalty", "riptide"), ("channeling", "riptide")],
        "tridents: Impaling, Loyalty/Channeling, Riptide (excludes both), Mending, Unbreaking"),
    Family("HELMET", [
        ("protection", 4), ("fire_protection", 4), ("blast_protection", 4),
        ("projectile_protection", 4), ("respiration", 3), ("aqua_affinity", 1),
        ("mending", 1), ("unbreaking", 3)],
        [("protection", "fire_protection"), ("protection", "blast_protection"),
         ("protection", "projectile_protection"), ("fire_protection", "blast_protection"),
         ("fire_protection", "projectile_protection"), ("blast_protection", "projectile_protection")],
        "helmets: the protection clique, Respiration, Aqua Affinity, Mending, Unbreaking"),
    Family("CHESTPLATE", [
        ("protection", 4), ("fire_protection", 4), ("blast_protection", 4),
        ("projectile_protection", 4), ("mending", 1), ("unbreaking", 3)],
        [("protection", "fire_protection"), ("protection", "blast_protection"),
         ("protection", "projectile_protection"), ("fire_protection", "blast_protection"),
         ("fire_protection", "projectile_protection"), ("blast_protection", "projectile_protection")],
        "chestplates: the protection clique, Mending, Unbreaking"),
    Family("LEGGINGS", [
        ("protection", 4), ("fire_protection", 4), ("blast_protection", 4),
        ("projectile_protection", 4), ("mending", 1), ("unbreaking", 3)],
        [("protection", "fire_protection"), ("protection", "blast_protection"),
         ("protection", "projectile_protection"), ("fire_protection", "blast_protection"),
         ("fire_protection", "projectile_protection"), ("blast_protection", "projectile_protection")],
        "leggings: the protection clique, Mending, Unbreaking"),
    Family("BOOTS", [
        ("protection", 4), ("fire_protection", 4), ("blast_protection", 4),
        ("projectile_protection", 4), ("feather_falling", 4), ("depth_strider", 3),
        ("mending", 1), ("unbreaking", 3)],
        [("protection", "fire_protection"), ("protection", "blast_protection"),
         ("protection", "projectile_protection"), ("fire_protection", "blast_protection"),
         ("fire_protection", "projectile_protection"), ("blast_protection", "projectile_protection")],
        "boots: the protection clique, Feather Falling, Depth Strider (Frost Walker and Soul Speed "
        "are rarity-silent), Mending, Unbreaking"),
    Family("SHEARS", [
        ("efficiency", 5), ("mending", 1), ("unbreaking", 3)],
        [],
        "shears: Efficiency, Mending, Unbreaking (the mining-loot pair does not reach shears in 26.3)"),
    Family("FLINT_AND_STEEL", [
        ("mending", 1), ("unbreaking", 3)],
        [],
        "flint and steel: Mending, Unbreaking"),
    Family("SHIELD", [
        ("mending", 1), ("unbreaking", 3)],
        [],
        "shields: Mending, Unbreaking"),
]

HEADER = """package net.zaminmc.zea.rarity;

import net.zaminmc.zea.itempresentation.recognition.ItemCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The mapped-out rarity tables: every rarity outcome the equipment
 * families can reach, enumerated offline from the vanilla 26.3 data and
 * frozen as lookup arrays. <b>Generated file</b> — regenerated by
 * {@code scripts/gen_rarity_table.py}; hand edits are lost on the next
 * run.
 *
 * <p>Why tables and not a formula: the previous engine derived its
 * enchantment budget from the live registry at runtime, and EarthMC
 * syncs custom enchantments into that registry — every budget computed
 * on such a world wobbled with whatever the server shipped, and the
 * floating-point progress thresholds turned the wobble into unexpected
 * tiers. The tables answer every question the formula asked, once, at
 * generation time:
 *
 * <ul>
 *   <li>each family's scorable (non-neutral) enchantment universe, its
 *       maximum levels and its conflict edges — transcribed from the
 *       client jar's {@code data/minecraft/enchantment/*.json} and the
 *       {@code exclusive_set} tags;</li>
 *   <li>every reachable fill numerator, enumerated over all legitimate
 *       level combinations (conflict-pruned) in exact integer
 *       arithmetic;</li>
 *   <li>the rarity ordinal for every (family, material tier, numerator)
 *       combination, computed with {@link RarityCalculator}'s
 *       documented rule — the rule stays as the readable reference, and
 *       the unit tests re-derive every table cell through it, so the
 *       tables can never drift from the rule silently.</li>
 * </ul>
 *
 * <p>The runtime does one thing: sum the pinned universe's
 * contributions for the item's present enchantments (unknown paths —
 * server custom enchantments — contribute nothing, which is precisely
 * the stability the formula could not give), binary-search the family's
 * reachable numerators, and read the rarity. An enchanted state that
 * could only exist command-forced (two members of an exclusive clique
 * on one item) maps to the nearest reachable state below its sum, so
 * the outcome stays inside the mapped space.
 */
public final class RarityTables {
        /** The material-ladder width every table row spans: tiers 0..6. */
        public static final int MATERIAL_TIERS = %d;
""" % MATERIAL_TIERS

UNIVERSE_BLOCK = """
        /** One family's mapped space: universe, reachable numerators, rarity table. */
        private record Universe(String family, int denominator, List<Scored> scorable,
                        int[] reachable, int[][] tiersByMaterial) {

                /**
                 * The fill numerator of a level map — the pinned
                 * universe's contributions only. Levels cap at the
                 * pinned maximum; paths outside the universe (server
                 * custom enchantments) contribute nothing; the
                 * enchantments a family cannot carry were never in it.
                 */
                int numeratorOf(Map<String, Integer> levels) {
                        int numerator = 0;
                        for (Scored scored : scorable) {
                                int level = levels.getOrDefault(scored.path(), 0);
                                if (level > 0) {
                                        numerator += Math.min(level, scored.maxLevel()) * scored.unit();
                                }
                        }
                        return numerator;
                }

                /** The index of the reachable numerator a sum maps to: exact, else nearest below. */
                int indexOf(int numerator) {
                        int at = Arrays.binarySearch(reachable, numerator);
                        if (at >= 0) {
                                return at;
                        }
                        int insertion = -(at + 1);
                        return Math.max(0, insertion - 1);
                }
        }

        /**
         * One scorable enchantment of one family: its registry path, its
         * pinned maximum level, and the numerator one level contributes.
         */
        private record Scored(String path, int maxLevel, int unit) {
        }
"""

FOOTER = """
        private RarityTables() {
        }

        /**
         * The mapped rarity at one reachable fill numerator — the direct
         * table read the level-map path above resolves to. {@code
         * numerator} need not be reachable: an unreachable sum (a
         * command-forced exclusive pair) maps to the nearest reachable
         * state below it, so every outcome stays inside the mapped
         * space. The seam the cell-level tests drive.
         */
        public static ItemRarity rarityAt(ItemCategory category, int materialTier, int numerator) {
                Universe universe = UNIVERSES.get(category);
                if (universe == null) {
                        return null;
                }
                int tier = Math.clamp(materialTier, 0, MATERIAL_TIERS - 1);
                return ItemRarity.values()[universe.tiersByMaterial()[tier][universe.indexOf(numerator)]];
        }

        /**
         * The mapped rarity for one supported item: the family's table,
         * read at the item's material tier and its enchantments' fill
         * numerator. {@code levels} carries registry-path to level for
         * the stack's present enchantments; anything outside the
         * family's pinned universe is ignored, so a server's custom
         * enchantments can never move the outcome. Returns
         * {@code null} only for a category without a universe — the
         * unsupported {@code OTHER} scope the classifier never sends.
         */
        public static ItemRarity rarityFor(ItemCategory category, int materialTier,
                        Map<String, Integer> levels) {
                Universe universe = UNIVERSES.get(category);
                if (universe == null) {
                        return null;
                }
                return rarityAt(category, materialTier, universe.numeratorOf(levels));
        }

        /** The scorable enchantment paths pinned for one category — the test seam. */
        public static List<String> scorablePaths(ItemCategory category) {
                Universe universe = UNIVERSES.get(category);
                return universe == null
                                ? List.of()
                                : universe.scorable().stream().map(Scored::path).toList();
        }

        /** The pinned maximum level of one scorable path, or 0 when unpinned — the test seam. */
        public static int pinnedMaxLevel(ItemCategory category, String path) {
                Universe universe = UNIVERSES.get(category);
                if (universe == null) {
                        return 0;
                }
                return universe.scorable().stream()
                                .filter(scored -> scored.path().equals(path))
                                .mapToInt(Scored::maxLevel)
                                .findFirst()
                                .orElse(0);
        }

        /** Every reachable fill numerator of one category — the test seam. */
        public static int[] reachableNumerators(ItemCategory category) {
                Universe universe = UNIVERSES.get(category);
                return universe == null ? new int[0] : universe.reachable();
        }
}
"""


def emit():
    blocks = [HEADER, UNIVERSE_BLOCK]
    blocks.append("""
        /** The family count, for tests and diagnostics. */
        public static final int FAMILY_COUNT = %d;
""" % len(FAMILIES))
    for family in FAMILIES:
        family.build()
        max_fill = family.max_numerator / family.denominator
        scored = ",\n                ".join(
                'new Scored("%s", %d, %d)' % (path, max_level, family.unit(max_level))
                for (path, max_level) in family.enchants)
        blocks.append("""
        /** %s — denominator %d, %d reachable states, maxed fill %.1f. */
        private static final Universe %s = new Universe("%s", %d, List.of(
                %s),
                new int[] {%s},
                %s);
""" % (family.comment, family.denominator, len(family.reachable), max_fill,
            family.category, family.category, family.denominator,
            scored,
            ", ".join(str(n) for n in family.reachable),
            family.tiers_java()))
    blocks.append("""
        private static final Map<ItemCategory, Universe> UNIVERSES = Map.ofEntries(
%s);
""" % ",\n".join('                Map.entry(ItemCategory.%s, %s)' % (f.category, f.category)
                 for f in FAMILIES))
    blocks.append(FOOTER)
    with open(OUT, "w") as out:
        out.write("".join(blocks))
    print("wrote", OUT)
    for family in FAMILIES:
        print("  %-16s D=%-3d states=%-5d maxFill=%.1f" % (
            family.category, family.denominator, len(family.reachable),
            family.max_numerator / family.denominator))


if __name__ == "__main__":
    emit()
