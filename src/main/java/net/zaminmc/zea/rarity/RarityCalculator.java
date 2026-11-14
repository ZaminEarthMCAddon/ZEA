package net.zaminmc.zea.rarity;

/**
 * The deterministic rarity model: how material tier and enchantment
 * progression combine into one of the four {@link ItemRarity} levels.
 * Pure math — no registries, no stacks, no rendering — so every threshold
 * is unit-testable and the whole model is readable in one place.
 *
 * <h2>The model</h2>
 *
 * <p>Two factors are measured, then combined:
 *
 * <ol>
 *   <li><b>Material tier</b> {@code T} — 0..6 along the ladder of
 *       {@link MaterialTier}: tools wood, gold, stone, copper, iron,
 *       diamond, netherite; armor leather, gold, chainmail, copper, iron,
 *       diamond/turtle, netherite.</li>
 *   <li><b>Enchantment fill</b> {@code F} — for every compatible,
 *       non-neutral enchantment on the item, {@code level / maxLevel},
 *       summed. Neutral enchantments (Knockback, Thorns, Soul Speed, Swift
 *       Sneak, Frost Walker, curses) never count, in either direction.
 *       The <b>maximum possible fill</b> {@code M} is the fill of the best
 *       legitimate fully maxed setup for the item's class, with every
 *       exclusive group counted once — the number a hypermaxed item
 *       reaches.</li>
 * </ol>
 *
 * <p>The enchantment factor is the <b>progress</b> {@code P = F / M} — how
 * close the item is to being hypermaxed <em>for its own class</em>. That
 * normalization is what keeps a maxed pickaxe (fill 4.0) and a maxed
 * sword (fill 6.0) equivalent, and keeps an item from being penalized for
 * missing an enchantment it cannot legitimately have: an exclusive group
 * counts once toward M, so a bow with Infinity has Mending already
 * "consumed" and is not incomplete.
 *
 * <h2>Combining</h2>
 *
 * <pre>
 * material rarity   r_mat = clamp(T - 4, 0, 2)      iron and below Common, diamond Uncommon,
 *                                                   netherite Epic — never Legendary alone
 * enchant rarity    r_enc = 0                        P < 0.30
 *                         1                          P >= 0.30  (Uncommon — "some useful enchantments")
 *                         2                          P >= 0.55  (Epic — "substantial progression")
 *                         3                          P >= 0.85 and F >= 3.0 (Legendary — hypermaxed;
 *                                                   the absolute floor keeps a genuinely maximized
 *                                                   item from riding normalization alone)
 * rarity ceiling    C     = 1                        T <= 1 (wood/gold tools, leather/gold armor)
 *                         2                          T <= 4 (stone/copper/iron)
 *                         3                          T >= 5 (diamond/turtle/netherite)
 * result            R     = clamp(max(r_mat, r_enc), 0, C)
 * </pre>
 *
 * <h2>Why each guard exists</h2>
 *
 * <ul>
 *   <li><b>Neither factor alone reaches Legendary.</b> Unenchanted
 *       netherite is Epic (r_mat caps at 2); a maxed wooden sword is
 *       Uncommon (ceiling 1). Legendary needs diamond-or-better material
 *       <em>and</em> near-complete enchantment progress — a genuinely
 *       high-tier combination.</li>
 *   <li><b>The ceiling keeps materials ordered.</b> A highly enchanted
 *       low-tier item beats its unenchanted self but never overtakes a
 *       properly developed high-tier item: maxed wood caps at Uncommon,
 *       maxed stone/copper/iron at Epic.</li>
 *   <li><b>Per-class normalization with an absolute floor.</b> A maxed
 *       chestplate (F = 3.0) and a maxed sword (F = 6.0) are both
 *       hypermaxed (P = 1.0), but an item with little absolute fill can
 *       still not call itself Legendary.</li>
 *   <li><b>Determinism.</b> Same material, same enchantments → same
 *       rarity, always.</li>
 * </ul>
 *
 * <h2>Worked examples (26.3 values)</h2>
 *
 * <pre>
 * wooden sword, unenchanted              T=0 F=0    P=0      → Common
 * golden pickaxe, unenchanted            T=1 F=0    P=0      → Common
 * stone axe, unenchanted                 T=2 F=0    P=0      → Common
 * iron sword, unenchanted                T=4 F=0    P=0      → Common
 * diamond sword, unenchanted             T=5 F=0    P=0      → Uncommon
 * netherite sword, unenchanted           T=6 F=0    P=0      → Epic
 * wooden sword, fully maxed (F=6, M=6)   T=0 P=1.0  r_enc=3, ceiling 1 → Uncommon
 * iron sword, fully maxed (F=6, M=6)     T=4 P=1.0  ceiling 2          → Epic
 * diamond sword, fully maxed             T=5 P=1.0  ceiling 3          → Legendary
 * netherite sword, fully maxed           T=6 P=1.0  ceiling 3          → Legendary
 * stone pickaxe, Efficiency V + Fortune III
 *                                        T=2 F=2.0 M=4.0 P=0.5 → Uncommon
 * netherite sword, Sharpness V only      T=6 F=1.0 M=6.0 P=0.17 → Epic (material carries)
 * diamond chestplate, Prot IV + Unbreaking III
 *                                        T=5 F=2.0 M=3.0 P=0.67 → Epic
 * diamond chestplate, fully maxed (F=3, M=3)
 *                                        T=5 P=1.0 F=3.0 ≥ 3.0     → Legendary
 * bow with Infinity (M excludes Mending) P measured against the right maximum → never penalized
 * </pre>
 */
public final class RarityCalculator {
        /**
         * Progress at which an item counts as having "some useful
         * enchantments" — the Uncommon enchantment factor.
         */
        public static final double UNCOMMON_PROGRESS = 0.30;

        /**
         * Progress marking substantial, near-complete progression — the
         * Epic enchantment factor.
         */
        public static final double EPIC_PROGRESS = 0.55;

        /**
         * Progress at which an item counts as hypermaxed — the Legendary
         * enchantment factor.
         */
        public static final double LEGENDARY_PROGRESS = 0.85;

        /**
         * Absolute enchantment fill required for Legendary in addition to
         * the progress gate, so a class with a small maximum cannot reach
         * Legendary on progress alone.
         */
        public static final double LEGENDARY_MIN_FILL = 3.0;

        /** Highest material tier that still tops out at Uncommon. */
        public static final int MAX_TIER_CEILING_UNCOMMON = 1;

        /** Highest material tier that still tops out at Epic. */
        public static final int MAX_TIER_CEILING_EPIC = 4;

        private RarityCalculator() {
        }

        /**
         * The rarity of a supported item with the given material tier and
         * enchantment fills. Unsupported items never reach this method —
         * the engine filters them out before calling.
         *
         * @param materialTier the {@link MaterialTier} ladder position, 0..6
         * @param fill the summed {@code level/maxLevel} of the item's
         *        compatible, non-neutral enchantments
         * @param maxFill the fill of the best legitimate maxed setup for
         *        the item's class; 0 when the class has no scorable
         *        enchantments
         */
        public static ItemRarity calculate(int materialTier, double fill, double maxFill) {
                int materialRarity = Math.clamp(materialTier - 4, 0, 2);
                double progress = maxFill > 0 ? fill / maxFill : 0.0;
                int enchantRarity;
                if (progress >= LEGENDARY_PROGRESS && fill >= LEGENDARY_MIN_FILL) {
                        enchantRarity = 3;
                } else if (progress >= EPIC_PROGRESS) {
                        enchantRarity = 2;
                } else if (progress >= UNCOMMON_PROGRESS) {
                        enchantRarity = 1;
                } else {
                        enchantRarity = 0;
                }
                int ceiling = ceilingFor(materialTier);
                int level = Math.clamp(Math.max(materialRarity, enchantRarity), 0, ceiling);
                return ItemRarity.values()[level];
        }

        /**
         * The rarity a material alone allows: the ceiling an item's
         * enchantment progress can never push past.
         */
        public static int ceilingFor(int materialTier) {
                if (materialTier <= MAX_TIER_CEILING_UNCOMMON) {
                        return 1;
                }
                return materialTier <= MAX_TIER_CEILING_EPIC ? 2 : 3;
        }
}
