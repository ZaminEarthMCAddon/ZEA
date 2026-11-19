package net.zaminmc.zea.map.geo;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The country-aggregated territory: each country's associated towns
 * rasterize into a mip pyramid of fill cells — the union cell set, which
 * preserves islands, disconnected regions and holes exactly, with none
 * of the triangulation hazards a polygon union carries.
 *
 * <p>Level 0 cells are {@value #CELL}-block squares; each level up merges
 * a complete 2×2 of the level below (a cell exists at level {@code k+1}
 * only when all four of its children exist), so a hole never heals into
 * territory and an island never grows. The draw picks the level whose
 * cells are a few screen pixels wide and fills them — at the global zoom
 * a country reads as its territory shape, never as one bounding
 * rectangle around towns that never touch.
 *
 * <p>Pure type, built off the render thread (the subsystem schedules a
 * rebuild when claims publish), immutable once built, pinned by tests.
 */
public final class CountryMosaic {

        /** The base cell's edge, in blocks: two chunks. */
        public static final int CELL = 128;

        /** The deepest mip: 2^7 · 128 = 16,384-block cells — one zoom-0 tile. */
        public static final int MAX_MIP = 7;

        /**
         * One country's territory: the fill cells at level 0 plus their
         * mip pyramids, and the flag's anchor cell (the centroid of the
         * territory's cells, snapped into the set).
         */
        public static final class Aggregate {
                private final String countryId;
                private final int argb;
                private final List<Set<Long>> mips;
                private final long flagCell;

                Aggregate(String countryId, int argb, List<Set<Long>> mips, long flagCell) {
                        this.countryId = countryId;
                        this.argb = argb;
                        this.mips = mips;
                        this.flagCell = flagCell;
                }

                public String countryId() {
                        return countryId;
                }

                /** The territory's fill color: the flag's leading color. */
                public int argb() {
                        return argb;
                }

                /** The fill cells at a mip level (packed {@link #pack}). */
                public Set<Long> cells(int mip) {
                        return mips.get(Math.min(mip, mips.size() - 1));
                }

                /** The flag's anchor cell (packed, at level 0). */
                public long flagCell() {
                        return flagCell;
                }

                /** The town-cell count the aggregate covers, level 0. */
                public int cellCount() {
                        return mips.get(0).size();
                }
        }

        private CountryMosaic() {
        }

        /**
         * Aggregates one world's claims by associated country. Towns that
         * associate with nothing are skipped — the registry decided that
         * deliberately, and the mosaic never guesses.
         */
        public static List<Aggregate> build(List<TownClaim> claims, CountryRegistry registry) {
                Map<String, Set<Long>> cellsByCountry = new HashMap<>();
                Map<String, int[]> colorByCountry = new HashMap<>();
                for (TownClaim claim : claims) {
                        CountryRegistry.Match match = registry.associate(
                                                        claim.name(), claim.nation(), claim.key());
                        if (match.countryId() == null) {
                                continue;
                        }
                        CountryRegistry.Country country = registry.country(match.countryId());
                        if (country == null) {
                                continue;
                        }
                        Set<Long> cells = cellsByCountry.computeIfAbsent(match.countryId(),
                                                        ignored -> new HashSet<>());
                        rasterize(claim, cells);
                        colorByCountry.putIfAbsent(match.countryId(), new int[] {claim.fillRgb()});
                }
                List<Aggregate> aggregates = new ArrayList<>();
                for (Map.Entry<String, Set<Long>> entry : cellsByCountry.entrySet()) {
                        if (entry.getValue().isEmpty()) {
                                continue;
                        }
                        CountryRegistry.Country country = registry.country(entry.getKey());
                        int flagColor = country != null && !country.flagColors().isEmpty()
                                                        ? country.flagColors().get(0)
                                                        : colorByCountry.getOrDefault(entry.getKey(),
                                                                                        new int[] {0xFF888888})[0];
                        List<Set<Long>> mips = buildMips(entry.getValue());
                        long flagCell = chooseFlagCell(entry.getValue());
                        aggregates.add(new Aggregate(entry.getKey(), flagColor, mips, flagCell));
                }
                // Deterministic order: by id.
                aggregates.sort(java.util.Comparator.comparing(Aggregate::countryId));
                return aggregates;
        }

        /**
         * Rasterizes one town's rings into level-0 cells: a cell belongs
         * to the town when its center sits inside the rings under even-odd
         * semantics (ring 0 the outer boundary, the rest holes).
         */
        static void rasterize(TownClaim claim, Set<Long> cells) {
                int cellMinX = Math.floorDiv(claim.minX(), CELL);
                int cellMaxX = Math.floorDiv(claim.maxX() - 1, CELL);
                int cellMinZ = Math.floorDiv(claim.minZ(), CELL);
                int cellMaxZ = Math.floorDiv(claim.maxZ() - 1, CELL);
                for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
                                double px = (double) cx * CELL + CELL / 2.0;
                                double pz = (double) cz * CELL + CELL / 2.0;
                                if (insideRings(px, pz, claim.rings())) {
                                        cells.add(pack(cx, cz));
                                }
                        }
                }
        }

        /** Even-odd point-in-rings: crossing any ring flips membership. */
        static boolean insideRings(double px, double pz, List<int[][]> rings) {
                boolean inside = false;
                for (int[][] ring : rings) {
                        if (insideRing(px, pz, ring)) {
                                inside = !inside;
                        }
                    }
                return inside;
        }

        private static boolean insideRing(double px, double pz, int[][] ring) {
                int n = ring.length;
                if (n < 3) {
                        return false;
                }
                boolean inside = false;
                // Even-odd ray cast toward +x, points as [x, z].
                int j = n - 1;
                for (int i = 0; i < n; j = i++) {
                        double xi = ring[i][0];
                        double zi = ring[i][1];
                        double xj = ring[j][0];
                        double zj = ring[j][1];
                        if ((zi > pz) != (zj > pz)
                                                        && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) {
                                inside = !inside;
                        }
                }
                return inside;
        }

        /** Merges the level-0 set upward: a cell exists only under a complete 2×2. */
        static List<Set<Long>> buildMips(Set<Long> level0) {
                List<Set<Long>> mips = new ArrayList<>(MAX_MIP + 1);
                mips.add(level0);
                for (int level = 1; level <= MAX_MIP; level++) {
                        Set<Long> previous = mips.get(level - 1);
                        Set<Long> merged = new HashSet<>();
                        for (long packed : previous) {
                                int cx = unpackX(packed);
                                int cz = unpackZ(packed);
                                // The cell's position in its 2×2 sibling block.
                                if ((cx & 1) == 0 && (cz & 1) == 0
                                                                && previous.contains(pack(cx + 1, cz))
                                                                && previous.contains(pack(cx, cz + 1))
                                                                && previous.contains(pack(cx + 1, cz + 1))) {
                                        merged.add(pack(Math.floorDiv(cx, 2), Math.floorDiv(cz, 2)));
                                }
                        }
                        if (merged.isEmpty()) {
                                // Keep filling the pyramid with empty sets so
                                // lookups never fall off the list.
                                for (int rest = level; rest <= MAX_MIP; rest++) {
                                        mips.add(new HashSet<>());
                                }
                                break;
                        }
                        mips.add(merged);
                }
                return mips;
        }

        /**
         * The flag anchor: the territory's centroid snapped to the
         * containing cell — and if the centroid's cell holds no fill (a
         * U-shaped territory), the nearest filled cell by plane distance,
         * ties broken by packed order. Deterministic.
         */
        static long chooseFlagCell(Set<Long> cells) {
                double sumX = 0.0;
                double sumZ = 0.0;
                for (long packed : cells) {
                        sumX += unpackX(packed);
                        sumZ += unpackZ(packed);
                }
                double centroidX = sumX / cells.size();
                double centroidZ = sumZ / cells.size();
                long best = Long.MAX_VALUE;
                double bestDistance = Double.MAX_VALUE;
                for (long packed : cells) {
                        double dx = unpackX(packed) + 0.5 - centroidX;
                        double dz = unpackZ(packed) + 0.5 - centroidZ;
                        double distance = dx * dx + dz * dz;
                        if (distance < bestDistance || (distance == bestDistance && packed < best)) {
                                bestDistance = distance;
                                best = packed;
                        }
                }
                return best;
        }

        /** Packs a cell coordinate into a stable long: 16 bits each, offset 2^15. */
        public static long pack(int cellX, int cellZ) {
                return ((long) (cellX + 32_768) << 16) | (cellZ + 32_768);
        }

        public static int unpackX(long packed) {
                return ((int) (packed >> 16)) - 32_768;
        }

        public static int unpackZ(long packed) {
                return ((int) (packed & 0xFFFF)) - 32_768;
        }

        /**
         * The mip level for a camera: the finest level whose cells stay at
         * least {@code minPixels} wide on screen — shape detail down to
         * the readability floor, never sub-pixel noise. Past the floor the
         * mosaic thins to country shapes, never to a fog of cells.
         */
        public static int mipFor(double pixelsPerBlock, double minPixels) {
                for (int level = 0; level <= MAX_MIP; level++) {
                        double cellBlocks = (double) CELL * (1 << level);
                        if (cellBlocks * pixelsPerBlock >= minPixels) {
                                return level;
                        }
                }
                return MAX_MIP;
        }

        /** The flag anchor's world center, in blocks. */
        public static double[] flagWorldPos(Aggregate aggregate) {
                int cx = unpackX(aggregate.flagCell());
                int cz = unpackZ(aggregate.flagCell());
                return new double[] {(double) cx * CELL + CELL / 2.0, (double) cz * CELL + CELL / 2.0};
        }

        /** Debug/report helper: countries by cell count, largest first. */
        public static Map<String, Integer> countsByCountry(@Nullable List<Aggregate> aggregates) {
                Map<String, Integer> counts = new TreeMap<>();
                if (aggregates != null) {
                        for (Aggregate aggregate : aggregates) {
                                counts.put(aggregate.countryId(), aggregate.cellCount());
                        }
                }
                return counts;
        }
}
