package net.zaminmc.zea.discovery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The freshness-aware merge of shop observations — the rule set that
 * makes remote repository data safe against local observations.
 *
 * <p>The invariant the player asked for, in one sentence: <b>older
 * remote data never overwrites newer local observations</b>. The rules,
 * each pinned by tests:
 *
 * <ul>
 * <li><b>Local newer</b> (local.lastObservedAt &gt; remote.lastObservedAt):
 *     the local record stands untouched.</li>
 * <li><b>Remote newer</b>: the valid remote observation may update the
 *     local record — keeping the local {@code firstObservedAt} so the
 *     discovery history stays continuous.</li>
 * <li><b>Different shops</b> (different {@code shopId}): both records
 *     exist.</li>
 * <li><b>Local-only</b>: a local record the repository does not know is
 *     never removed.</li>
 * <li><b>Missing remote timestamps</b>: a remote record without a
 *     trustworthy {@code lastObservedAt} never overwrites a local
 *     observation (repository publication time is not observation
 *     time).</li>
 * <li><b>Invalidation</b>: a locally invalidated shop is not resurrected
 *     by a stale remote record; a remote record explicitly marked
 *     invalid may invalidate the local one only when it is at least as
 *     fresh.</li>
 * <li><b>Malformed remote entries</b> (no shop id, no identity fields)
 *     are rejected, never merged, never crash the pass.</li>
 * </ul>
 *
 * <p>The merge is deterministic: same inputs, same output map, in
 * insertion order.
 */
public final class ObservationMerge {
        private ObservationMerge() {
        }

        /** Whether a remote record carries the minimum identity to merge at all. */
        public static boolean isMergeable(ShopObservation remote) {
                return remote != null
                                && remote.shopId != null && !remote.shopId.isBlank()
                                && remote.signPos != null && !remote.signPos.isBlank()
                                && remote.lastObservedAt > 0;
        }

        /**
         * Merges the remote observations into the local map, in place,
         * returning the same map for chaining. {@code nowMs} stamps newly
         * added records' fetch time; the clock is injected for tests.
         */
        public static Map<String, ShopObservation> merge(Map<String, ShopObservation> local,
                        Map<String, ShopObservation> remote, LongSupplier clock) {
                if (local == null || remote == null) {
                        return local;
                }
                Map<String, ShopObservation> result = new LinkedHashMap<>(local);
                for (ShopObservation candidate : remote.values()) {
                        if (!isMergeable(candidate)) {
                                continue;
                        }
                        ShopObservation existing = result.get(candidate.shopId);
                        if (existing == null) {
                                // Case 3/4: a different physical shop — join the set.
                                ShopObservation added = candidate;
                                if (added.firstObservedAt <= 0) {
                                        added.firstObservedAt = candidate.lastObservedAt;
                                }
                                result.put(candidate.shopId, added);
                                continue;
                        }
                        result.put(candidate.shopId, mergeOne(existing, candidate, clock));
                }
                return result;
        }

        /** The fresher-truth resolution for one physical shop. */
        public static ShopObservation mergeOne(ShopObservation local, ShopObservation remote,
                        LongSupplier clock) {
                if (remote.lastObservedAt <= 0) {
                        // Case 5: no trustworthy observation time — the local
                        // record can never be overwritten by it.
                        return local;
                }
                if (local.invalidated && !remote.invalidated
                                && remote.lastObservedAt <= local.lastObservedAt) {
                        // A stale remote record must not resurrect a shop the
                        // local player found gone.
                        return local;
                }
                if (remote.lastObservedAt > local.lastObservedAt) {
                        // Case 2: the newer valid observation wins. The first
                        // sighting stays local history; the source reflects
                        // where the newest data came from.
                        ShopObservation merged = remote;
                        merged.firstObservedAt = local.firstObservedAt > 0
                                        ? local.firstObservedAt
                                        : remote.firstObservedAt;
                        return merged;
                }
                // Case 1: local is at least as fresh — it stands.
                return local;
        }
}
