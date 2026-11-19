package net.zaminmc.zea.map.geo;

import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TownClaim;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The association registry's determinism contract and the mosaic's
 * geometry: nation-primary association, exact aliases, ambiguity dropped
 * not guessed, exclusions honored, overrides authoritative, holes and
 * islands preserved through the mips, and the flag anchor deterministic.
 */
class CountryMosaicTest {

        private static final String DATASET = """
                        {
                          "version": 1,
                          "countries": [
                            {"id": "gb", "name": "United Kingdom", "iso": "GB", "flag": ["#012169"]},
                            {"id": "fr", "name": "France", "iso": "FR", "flag": ["#002395"]},
                            {"id": "pk", "name": "Pakistan", "iso": "PK", "flag": ["#01411C"]},
                            {"id": "eg", "name": "Egypt", "iso": "EG", "flag": ["#CE1126"]},
                            {"id": "us", "name": "United States", "iso": "US", "flag": ["#B22234"]}
                          ],
                          "townAliases": {
                            "London": "gb",
                            "Paris": "fr",
                            "Hyderabad": "pk",
                            "Alexandria": ["eg", "us"]
                          },
                          "exclusions": ["Springfield"]
                        }
                        """;

        private static TownClaim town(String name, String nation, int x0, int z0, int x1, int z1) {
                // A solid square claim: one outer ring, four corners.
                int[][] ring = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
                return TownClaim.of(MapWorld.EARTH, name, nation,
                                                0xFF000000, 0xFF888888, List.<int[][]>of(ring));
        }

        // ------------------------------------------------------------------
        // Registry
        // ------------------------------------------------------------------

        @Test
        void nationAssociationWins() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                var match = registry.associate("SomeTown", "Pakistan", "sometown");
                assertEquals("pk", match.countryId());
                assertEquals(CountryRegistry.Source.NATION, match.source());
        }

        @Test
        void specExamplesAssociateExactly() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                assertEquals("gb", registry.associate("London", null, "london").countryId());
                assertEquals("fr", registry.associate("Paris", null, "paris").countryId());
                assertEquals("pk", registry.associate("Hyderabad", null, "hyderabad").countryId());
        }

        @Test
        void ambiguousTownAliasDroppedNotGuessed() {
                // "Alexandria" names two countries in the dataset: the alias
                // must not resolve at all.
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                assertEquals(CountryRegistry.Source.NONE,
                                                registry.associate("Alexandria", null, "alexandria").source());
        }

        @Test
        void townNamedExactlyAfterACountryAssociates() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                var match = registry.associate("France", null, "france");
                assertEquals("fr", match.countryId());
                assertEquals(CountryRegistry.Source.NAME, match.source());
        }

        @Test
        void exclusionBeatsEverythingButOverrides() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                assertEquals(CountryRegistry.Source.NONE,
                                                registry.associate("Springfield", "United States", "springfield").source());
                registry.setOverride("springfield", "us");
                var match = registry.associate("Springfield", "United States", "springfield");
                assertEquals("us", match.countryId());
                assertEquals(CountryRegistry.Source.OVERRIDE, match.source());
        }

        @Test
        void neverSubstrings() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                // "Parisian Heights" contains "Paris" but is not Paris — no
                // association without an exact match or its own alias.
                assertEquals(CountryRegistry.Source.NONE,
                                                registry.associate("Parisian Heights", null, "parisianheights").source());
        }

        @Test
        void unknownNationNeverGuesses() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                assertNull(registry.associate("Weird Town", "Not A Country", "weirdtown").countryId());
        }

        // ------------------------------------------------------------------
        // Mosaic
        // ------------------------------------------------------------------

        @Test
        void rasterizesSolidSquareClaims() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                List<CountryMosaic.Aggregate> aggregates = CountryMosaic.build(
                                                List.of(town("London", null, 0, 0, 255, 255)), registry);
                assertEquals(1, aggregates.size());
                assertEquals("gb", aggregates.get(0).countryId());
                // The claim spans 256 blocks: the cell centers of cells (0,0)
                // and (1,1) sit inside; (0,1)/(1,0)'s centers too (the claim
                // covers 0..255, cell centers at 64/192) — all four cells.
                assertEquals(4, aggregates.get(0).cellCount());
        }

        @Test
        void holesStayHoles() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                // A town whose outer ring covers 4×4 cells with a 2×2 hole
                // in the middle: 16 − 4 = 12 cells — and the hole sits in
                // every possible 2×2 block, so level 1 merges nothing.
                int[][] outer = {{0, 0}, {512, 0}, {512, 512}, {0, 512}};
                int[][] hole = {{128, 128}, {384, 128}, {384, 384}, {128, 384}};
                TownClaim hollow = TownClaim.of(MapWorld.EARTH, "London", null,
                                                0xFF000000, 0xFF888888, List.<int[][]>of(outer, hole));
                List<CountryMosaic.Aggregate> aggregates = CountryMosaic.build(List.of(hollow), registry);
                CountryMosaic.Aggregate aggregate = aggregates.get(0);
                assertEquals(12, aggregate.cellCount());
                assertEquals(0, aggregate.cells(1).size(), "the hole blocks every complete quad");

                // Wider territory, same hole: away from the hole the quads
                // complete, and level 2 fills where level 1 completed.
                int[][] wide = {{0, 0}, {1024, 0}, {1024, 1024}, {0, 1024}};
                TownClaim wideTown = TownClaim.of(MapWorld.EARTH, "London", null,
                                                0xFF000000, 0xFF888888, List.<int[][]>of(wide, hole));
                CountryMosaic.Aggregate wideAggregate = CountryMosaic
                                                .build(List.of(wideTown), registry).get(0);
                assertEquals(64 - 4, wideAggregate.cellCount());
                assertEquals(12, wideAggregate.cells(1).size(), "16 blocks minus the four the hole touches");
                assertTrue(wideAggregate.cells(2).size() > 0, "level 2 fills deeper in");
        }

        @Test
        void islandsStayDisconnected() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                // Two distant towns of one country: the mosaic holds two
                // disconnected clusters — never one rectangle bridging them.
                TownClaim a = town("London", null, 0, 0, 127, 127);
                TownClaim b = town("York", "United Kingdom", 20_000, 20_000, 20_127, 20_127);
                List<CountryMosaic.Aggregate> aggregates = CountryMosaic.build(List.of(a, b), registry);
                assertEquals(1, aggregates.size());
                assertEquals(2, aggregates.get(0).cellCount());
                assertEquals(0, aggregates.get(0).cells(1).size(), "no bridge, no merge");
        }

        @Test
        void flagAnchorIsDeterministicAndInsideTheTerritory() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                TownClaim a = town("London", null, 0, 0, 255, 255);
                TownClaim b = town("York", "United Kingdom", 20_000, 20_000, 20_255, 20_255);
                List<CountryMosaic.Aggregate> first = CountryMosaic.build(List.of(a, b), registry);
                List<CountryMosaic.Aggregate> second = CountryMosaic.build(List.of(b, a), registry);
                assertEquals(first.get(0).flagCell(), second.get(0).flagCell(),
                                                "input order must not move the flag");
                Set<Long> cells = first.get(0).cells(0);
                assertTrue(cells.contains(first.get(0).flagCell()),
                                                "the anchor sits on filled ground");
        }

        @Test
        void mipSelectsTheScreenReadableLevel() {
                // 3 screen pixels per cell: at ppb 0.01 a level-0 cell is
                // 1.28 px — too fine; the walk starts deep and settles on
                // the coarsest readable level, whose cells are ≥ 3 px wide.
                int mip = CountryMosaic.mipFor(0.01, 3.0);
                assertTrue((double) (CountryMosaic.CELL << mip) * 0.01 >= 3.0,
                                                "the chosen mip's cells are at least 3 px wide");
                // A huge ppb settles on level 0.
                assertEquals(0, CountryMosaic.mipFor(1.0, 3.0));
        }

        @Test
        void unassociatedTownsBuildNothing() {
                CountryRegistry registry = CountryRegistry.parse(DATASET);
                List<CountryMosaic.Aggregate> aggregates = CountryMosaic.build(
                                                List.of(town("Weird Town", "Not A Country", 0, 0, 255, 255)), registry);
                assertTrue(aggregates.isEmpty(), "the mosaic never guesses");
        }

        @Test
        void packsAndUnpacksNegativeCells() {
                for (int x : new int[] {-507, -1, 0, 1, 507}) {
                        for (int z : new int[] {-507, -1, 0, 1, 507}) {
                                assertEquals(x, CountryMosaic.unpackX(CountryMosaic.pack(x, z)));
                                assertEquals(z, CountryMosaic.unpackZ(CountryMosaic.pack(x, z)));
                        }
                }
        }
}
