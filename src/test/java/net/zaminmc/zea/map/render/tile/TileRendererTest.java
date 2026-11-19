package net.zaminmc.zea.map.render.tile;

import net.zaminmc.zea.map.cache.TileCache;
import net.zaminmc.zea.map.model.MapCamera;
import net.zaminmc.zea.map.model.MapWorld;
import net.zaminmc.zea.map.model.TileId;
import net.zaminmc.zea.map.projection.MapProjection;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame planner: layer emission, parent fallback, the coverage
 * gate's interaction with the cache, request budgets and priority,
 * world-bounds filtering, and the pan lead — the visual policies that
 * would otherwise only be checkable by eye on the live server.
 */
class TileRendererTest {

        private final AtomicLong clock = new AtomicLong(1_000_000);
        private final TileCache<byte[]> cache = new TileCache<>(256, clock::get);
        private final TileRenderer renderer = new TileRenderer(cache);

        private static MapProjection view(MapWorld world, double x, double z, double ppb, int w, int h) {
                return MapProjection.northUp(new MapCamera(world, x, z, ppb), w, h);
        }

        /** Loads a tile to READY through the two threads' joint protocol. */
        private void ready(TileId id) {
                TileCache.LoadTicket ticket = cache.beginLoad(id);
                assertNotNull(ticket, "load must start for " + id);
                assertTrue(cache.complete(id, new byte[] { 1 }, null, null, ticket.generation()));
                TileCache.Upload<byte[]> upload;
                while ((upload = cache.claimForUpload()) != null) {
                        cache.markUploaded(upload);
                }
        }

        /** 404s a tile through the fetch thread's protocol. */
        private void absent(TileId id) {
                assertNotNull(cache.beginLoad(id), "load must start for " + id);
                cache.fail(id, 404);
        }

        @Test
        void overviewLayerDrawsReadyZoomZeroOnly() {
                // ppb 0.03125 → zoom 2 for the detailed choice; overview is zoom 0.
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 0.03125, 800, 600);
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertTrue(plan.overview().isEmpty(), "nothing ready yet");
                assertEquals(0, plan.detailed().size());
                assertFalse(plan.detailedActive());

                ready(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 0, 0, 0));
                plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertEquals(1, plan.overview().size());
                TileRenderer.Quad quad = plan.overview().get(0);
                assertEquals(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 0, 0, 0), quad.texture());
                assertEquals(MapProjection.FULL_REGION, quad.region());
                // Zoom-0 tile spans 16384 blocks; at ppb 0.03125 that is 512 px.
                // Its top-left corner is the world origin, which is the camera.
                assertEquals(512.0, quad.width(), 1e-9);
                assertEquals(512.0, quad.height(), 1e-9);
                assertEquals(0.0, quad.x(), 1e-9);
                assertEquals(0.0, quad.y(), 1e-9);
        }

        @Test
        void detailedLayerWaitsForFullCoverageThenStays() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 1.0, 200, 200);
                assertEquals(5, MapProjection.chooseTileZoom(1.0));
                List<TileId> visible = projection.visibleTiles(5, 0, TileId.TileSource.SQUAREMAP);
                // The diagonal superset: a 200-block view around the origin spans
                // tile range -1..0 on both axes — 4 slots.
                assertEquals(4, visible.size());

                ready(visible.get(0));
                ready(visible.get(1));
                ready(visible.get(2));
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertFalse(plan.detailedActive(), "3 of 4 covered must not activate");
                assertEquals(0, plan.detailed().size());

                ready(visible.get(3));
                plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertTrue(plan.detailedActive());
                assertEquals(4, plan.detailed().size());
                for (TileRenderer.Quad quad : plan.detailed()) {
                        assertEquals(MapProjection.FULL_REGION, quad.region());
                }
        }

        @Test
        void ancestorBackingIsNotCoverageForActivation() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 1.0, 200, 200);
                List<TileId> visible = projection.visibleTiles(5, 0, TileId.TileSource.SQUAREMAP);
                // Three slots confirmed absent, the fourth drawable only through a
                // zoom-3 ancestor: everything is *shown*, but the gate must not
                // activate — a slot backed by an ancestor is exactly the blurry
                // mosaic the gate exists to prevent.
                for (int i = 1; i < 4; i++) {
                        absent(visible.get(i));
                }
                TileId slot = visible.get(0);
                TileId ancestor = slot;
                while (ancestor.zoom() > 3) {
                        ancestor = ancestor.parent();
                }
                ready(ancestor);
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertFalse(plan.detailedActive(), "an ancestor-backed slot is blurry, not covered");
                assertEquals(0, plan.detailed().size());
                assertEquals(3, plan.coveredSlots(), "only the absent slots count as covered");
        }

        @Test
        void missingSlotFallsBackToNearestAncestorAboveZoomZero() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 1.0, 200, 200);
                List<TileId> visible = projection.visibleTiles(5, 0, TileId.TileSource.SQUAREMAP);
                for (TileId id : visible) {
                        ready(id);
                }
                assertTrue(renderer.planFrame(projection, TileRenderer.Motion.STILL).detailedActive());

                // Pan east: two fresh slots enter the view uncovered — the gate
                // stays active (sticky) and the fresh slots draw from their nearest
                // drawable ancestor above zoom 0. The zoom-4 parents stay unloaded
                // so the walk must reach the zoom-3 grandparents.
                MapProjection panned = view(MapWorld.EARTH, 600, 0, 1.0, 200, 200);
                TileId freshSlot = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 1, 0);
                TileId grandparent = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 3, 0, 0);
                ready(grandparent);
                ready(new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 3, 0, -1));
                TileRenderer.FramePlan plan = renderer.planFrame(panned, TileRenderer.Motion.STILL);
                assertTrue(plan.detailedActive(), "sticky through the coverage dip");
                assertEquals(4, plan.detailed().size(), "two self quads plus two ancestor fallbacks");
                TileRenderer.Quad quad = plan.detailed().stream()
                                                .filter(q -> q.texture().equals(grandparent)
                                                                                && q.region().equals(MapProjection.regionWithin(freshSlot, grandparent)))
                                                .findFirst().orElseThrow();
                assertEquals(3, quad.texture().zoom(), "the fallback walked past the unloaded zoom-4 parent");
                // The fallback quad covers the missing slot's own rectangle:
                // tile (1, 0) at zoom 5 spans [512, 1024) x [0, 512) blocks.
                assertEquals((1 * 512 - 600) * 1.0, quad.x(), 1e-9);
                assertEquals((0 * 512 - 0) * 1.0, quad.y(), 1e-9);
                assertEquals(512.0, quad.width(), 1e-9);
                assertEquals(512.0, quad.height(), 1e-9);
        }

        @Test
        void coarsestCameraViewServesTheWholeWorldFromZoomZero() {
                // The camera's zoom floor is ppb 1/1024 — deep in the globe
                // phases, where the whole world fits the viewport and zoom 0 is
                // the served layer itself, not just an under-layer.
                MapProjection projection = view(MapWorld.EARTH, 0, 0, MapCamera.MIN_PIXELS_PER_BLOCK, 800, 600);
                assertEquals(0, MapProjection.chooseTileZoom(MapCamera.MIN_PIXELS_PER_BLOCK));
                Set<Integer> zooms = new HashSet<>();
                TileRenderer.FramePlan plan = null;
                int rounds = 0;
                do {
                        plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                        for (TileId id : plan.requests()) {
                                zooms.add(id.zoom());
                                ready(id);
                        }
                } while (!plan.requests().isEmpty() && ++rounds < 40);
                assertTrue(plan.requests().isEmpty(), "the coarsest view settles within " + rounds + " rounds");
                assertTrue(zooms.contains(0), "the whole world renders from zoom 0, saw " + zooms);
        }

        @Test
        void movingBudgetShrinksAndServesThePanLeadFirst() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 0.03125, 1920, 1080);
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertEquals(TileRenderer.REQUEST_BUDGET_IDLE, plan.requests().size(),
                                                "an empty cache asks for the full idle budget");

                // Eastward pan: the lead rect is displaced 0.75 diagonals east, so
                // its nearest edge starts at tile column 4 of zoom 2 — the tiny
                // moving budget must go there, never to the trailing west side.
                plan = renderer.planFrame(projection, new TileRenderer.Motion(10, 0));
                assertEquals(TileRenderer.REQUEST_BUDGET_MOVING, plan.requests().size());
                for (TileId id : plan.requests()) {
                        assertEquals(2, id.zoom());
                        assertTrue(id.x() >= 4, "an eastward pan serves the lead rect, never west of the camera: " + id);
                        assertTrue(id.x() <= 15, "lead requests stop at the world's east edge: " + id);
                }
                assertEquals(4, plan.requests().get(0).x(), "the nearest lead column is served first");
        }

        @Test
        void readyTilesAreNeverReRequested() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 0.03125, 800, 600);
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                for (TileId id : plan.requests()) {
                        ready(id);
                }
                plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                for (TileId id : plan.requests()) {
                        assertFalse(cache.stateOf(id) == TileCache.State.READY,
                                                        "a READY tile must not appear in requests: " + id);
                }
        }

        @Test
        void requestsStayInsideTheWorldExtent() {
                MapProjection projection = view(MapWorld.EARTH, -64_000, -32_000, 0.25, 1920, 1080);
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertFalse(plan.requests().isEmpty());
                for (TileId id : plan.requests()) {
                        long span = MapProjection.tileWorldSpan(id.zoom());
                        assertTrue(id.x() * span < 64_512 && (id.x() + 1) * span > -64_512,
                                                        "tile outside world X requested: " + id);
                        assertTrue(id.z() * span < 32_256 && (id.z() + 1) * span > -32_256,
                                                        "tile outside world Z requested: " + id);
                }
        }

        @Test
        void idleFramesPrefetchAdjacentZooms() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 0.25, 400, 300);
                assertEquals(5, MapProjection.chooseTileZoom(0.25));
                Set<Integer> zooms = new HashSet<>();
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                for (int round = 0; round < 8; round++) {
                        for (TileId id : plan.requests()) {
                                if (cache.stateOf(id) != TileCache.State.READY) {
                                        ready(id);
                                }
                        }
                        plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                        for (TileId id : plan.requests()) {
                                zooms.add(id.zoom());
                        }
                }
                assertTrue(zooms.contains(4), "coarser prefetch must surface once the view is served, saw " + zooms);
        }

        @Test
        void movingBudgetServesTrueLeadTilesOnceTheViewIsServed() {
                // The lead rect overlaps the current view, and READY tiles are
                // never re-requested — so only a served view lets the moving
                // budget reach tiles beyond the view's leading edge. That is the
                // whole point of the lead: it serves what is about to scroll in.
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 0.25, 400, 300);
                Set<TileId> everRequested = new HashSet<>();
                TileRenderer.FramePlan plan;
                int rounds = 0;
                do {
                        plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                        for (TileId id : plan.requests()) {
                                ready(id);
                                everRequested.add(id);
                        }
                } while (!plan.requests().isEmpty() && ++rounds < 10);
                assertTrue(plan.requests().isEmpty(), "an idle view settles within " + rounds + " rounds");

                // Southward pan: the served margin ends at tile row 2; the lead
                // beyond it starts at row 3.
                plan = renderer.planFrame(projection, new TileRenderer.Motion(0, 500));
                assertEquals(TileRenderer.REQUEST_BUDGET_MOVING, plan.requests().size());
                for (TileId id : plan.requests()) {
                        assertEquals(5, id.zoom());
                        assertTrue(id.z() >= 3, "a southward pan serves rows beyond the served margin, never north of it: " + id);
                        assertFalse(everRequested.contains(id), "the moving budget goes to fresh lead tiles: " + id);
                }
        }

        @Test
        void quadPositionsAreCentreRelativeMapFrame() {
                MapProjection projection = view(MapWorld.EARTH, 1000, -2000, 1.0, 800, 600);
                TileId tile = new TileId(MapWorld.EARTH, TileId.TileSource.SQUAREMAP, 5, 0, -4);
                ready(tile);
                renderer.coverage().update(MapWorld.EARTH, 5, 99, 99);
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                TileRenderer.Quad quad = plan.detailed().stream()
                                                .filter(q -> q.texture().equals(tile))
                                                .findFirst().orElseThrow();
                assertEquals(MapProjection.FULL_REGION, quad.region());
                // Tile (0, -4) at zoom 5 spans blocks [0,512) x [-2048,-1536);
                // its top-left corner is (0-1000, -2048+2000) blocks from the
                // camera, and at ppb 1 a block is a pixel.
                assertEquals(-1000.0, quad.x(), 1e-9);
                assertEquals(-48.0, quad.y(), 1e-9);
                assertEquals(512.0, quad.width(), 1e-9);
                assertEquals(512.0, quad.height(), 1e-9);
        }

        @Test
        void failedAbsentSlotsCountAsCovered() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 1.0, 200, 200);
                List<TileId> visible = projection.visibleTiles(5, 0, TileId.TileSource.SQUAREMAP);
                assertEquals(4, visible.size());
                ready(visible.get(0));
                ready(visible.get(1));
                absent(visible.get(2));
                absent(visible.get(3));
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertTrue(plan.detailedActive(), "a 404 slot is covered: no finer imagery exists");
                assertEquals(2, plan.detailed().size(), "only the READY slots draw");
        }

        @Test
        void degenerateSurfacePlansNothingExplosive() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 1.0, 0, 0);
                TileRenderer.FramePlan plan = renderer.planFrame(projection, TileRenderer.Motion.STILL);
                assertEquals(0, plan.totalSlots());
                assertFalse(plan.detailedActive());
                assertTrue(plan.overview().isEmpty());
                assertTrue(plan.detailed().isEmpty());
                assertTrue(plan.requests().isEmpty());
                // A pan on a degenerate surface must not explore an infinite lead.
                plan = renderer.planFrame(projection, new TileRenderer.Motion(100, 100));
                assertTrue(plan.requests().isEmpty());
        }

        @Test
        void worldChangeRearmsCoverageThroughThePlanner() {
                MapProjection projection = view(MapWorld.EARTH, 0, 0, 1.0, 200, 200);
                List<TileId> visible = projection.visibleTiles(5, 0, TileId.TileSource.SQUAREMAP);
                for (TileId id : visible) {
                        ready(id);
                }
                assertTrue(renderer.planFrame(projection, TileRenderer.Motion.STILL).detailedActive());

                MapProjection moon = view(MapWorld.MOON, 0, 0, 1.0, 200, 200);
                assertFalse(renderer.planFrame(moon, TileRenderer.Motion.STILL).detailedActive(),
                                                "the other world's view starts over");
        }
}
