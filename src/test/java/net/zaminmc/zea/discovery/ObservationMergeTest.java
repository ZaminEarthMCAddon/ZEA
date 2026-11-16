package net.zaminmc.zea.discovery;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The freshness-aware repository merge, against the task's exact cases:
 * newer local wins, newer valid remote may update, different shops coexist,
 * local-only records stay, timestamp-less remotes never overwrite,
 * invalidations do not resurrect, and malformed entries are rejected.
 */
class ObservationMergeTest {
        private static final long CLOCK = 5_000L;

        private static ShopObservation shop(String id, long lastObservedAt, String price) {
                ShopObservation observation = new ShopObservation();
                observation.shopId = id;
                observation.signPos = "10,64,-20";
                observation.server = "aurora.earthmc.net";
                observation.dimension = "minecraft:overworld";
                observation.priceRaw = price;
                observation.lastObservedAt = lastObservedAt;
                observation.firstObservedAt = lastObservedAt;
                observation.source = "local";
                return observation;
        }

        @Test
        void case1NewerLocalWins() {
                Map<String, ShopObservation> local = Map.of("shopA", shop("shopA", toMs("2026-11-17T00:30"), "2G"));
                Map<String, ShopObservation> remote = Map.of("shopA", shop("shopA", toMs("2026-11-15T12:00"), "5G"));
                var merged = ObservationMerge.merge(new LinkedHashMap<>(local), remote, () -> CLOCK);
                assertEquals("2G", merged.get("shopA").priceRaw,
                                "the older GitHub record must not overwrite the local observation");
        }

        @Test
        void case2NewerRemoteMayUpdate() {
                Map<String, ShopObservation> local = Map.of("shopA", shop("shopA", toMs("2026-11-15T12:00"), "5G"));
                ShopObservation remoteShop = shop("shopA", toMs("2026-11-17T00:30"), "2G");
                remoteShop.source = "repo";
                var merged = ObservationMerge.merge(new LinkedHashMap<>(local), Map.of("shopA", remoteShop),
                                () -> CLOCK);
                assertEquals("2G", merged.get("shopA").priceRaw);
                assertEquals(toMs("2026-11-15T12:00"), merged.get("shopA").firstObservedAt,
                                "the local first sighting stays as history");
        }

        @Test
        void case3DifferentShopsCoexist() {
                Map<String, ShopObservation> local = Map.of("shopA", shop("shopA", 1_000L, "2G"));
                Map<String, ShopObservation> remote = Map.of("shopB", shop("shopB", 2_000L, "3G"));
                var merged = ObservationMerge.merge(new LinkedHashMap<>(local), remote, () -> CLOCK);
                assertEquals("2G", merged.get("shopA").priceRaw);
                assertEquals("3G", merged.get("shopB").priceRaw);
        }

        @Test
        void case4LocalOnlyRecordsStay() {
                Map<String, ShopObservation> local = Map.of(
                                "shopA", shop("shopA", 1_000L, "2G"),
                                "shopC", shop("shopC", 2_000L, "7G"));
                Map<String, ShopObservation> remote = Map.of("shopA", shop("shopA", 1_000L, "2G"));
                var merged = ObservationMerge.merge(new LinkedHashMap<>(local), remote, () -> CLOCK);
                assertTrue(merged.containsKey("shopC"),
                                "a locally discovered shop the repository does not have must remain");
        }

        @Test
        void case5MissingTimestampsNeverOverwrite() {
                Map<String, ShopObservation> local = Map.of("shopA", shop("shopA", 1_000L, "2G"));
                ShopObservation timestampless = shop("shopA", 0L, "99G");
                var merged = ObservationMerge.merge(new LinkedHashMap<>(local), Map.of("shopA", timestampless),
                                () -> CLOCK);
                assertEquals("2G", merged.get("shopA").priceRaw,
                                "a remote record without a trustworthy observation time must not overwrite");
        }

        @Test
        void malformedRemoteEntriesAreRejected() {
                ShopObservation noId = shop(null, 9_000L, "1G");
                ShopObservation noPos = shop("shopA", 9_000L, "1G");
                noPos.signPos = null;
                assertFalse(ObservationMerge.isMergeable(noId));
                assertFalse(ObservationMerge.isMergeable(noPos));
                ShopObservation noTime = shop("shopA", 0L, "1G");
                assertFalse(ObservationMerge.isMergeable(noTime));
        }

        @Test
        void invalidatedShopsAreNotResurrected() {
                ShopObservation local = shop("shopA", 3_000L, "2G");
                local.invalidated = true;
                local.invalidatedReason = "sign gone";
                ShopObservation staleRemote = shop("shopA", 2_000L, "5G");
                var merged = ObservationMerge.merge(new LinkedHashMap<>(Map.of("shopA", local)),
                                Map.of("shopA", staleRemote), () -> CLOCK);
                assertTrue(merged.get("shopA").invalidated,
                                "stale remote data must not resurrect an explicitly invalidated shop");
        }

        @Test
        void identicalRecordsStayIdentical() {
                ShopObservation local = shop("shopA", 1_000L, "2G");
                ShopObservation remote = shop("shopA", 1_000L, "2G");
                var merged = ObservationMerge.merge(new LinkedHashMap<>(Map.of("shopA", local)),
                                Map.of("shopA", remote), () -> CLOCK);
                assertSame(local, merged.get("shopA"),
                                "an equal-timestamp remote is not newer; the local record stands");
        }

        @Test
        void mergeIsDeterministic() {
                Map<String, ShopObservation> local = new LinkedHashMap<>();
                local.put("shopA", shop("shopA", 1_000L, "2G"));
                Map<String, ShopObservation> remote = Map.of(
                                "shopB", shop("shopB", 2_000L, "3G"),
                                "shopC", shop("shopC", 4_000L, "4G"));
                var first = ObservationMerge.merge(new LinkedHashMap<>(local), remote, () -> CLOCK);
                var second = ObservationMerge.merge(new LinkedHashMap<>(local), remote, () -> CLOCK);
                assertEquals(first.keySet().iterator().next(), second.keySet().iterator().next());
                assertEquals(first.size(), second.size());
        }

        /** ISO-ish helper: the tests' times are relative orderings, not real epochs. */
        private static long toMs(String iso) {
                return java.time.Instant.parse(iso + ":00" + (iso.endsWith("Z") ? "" : "Z")).toEpochMilli();
        }
}
