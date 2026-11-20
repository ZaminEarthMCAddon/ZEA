package net.zaminmc.zea.market;

import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-memory market index: every known physical shop plus the
 * observation history each shop has accumulated.
 *
 * <p>Ownership follows the chat history discipline: the client thread
 * owns all mutations (inspections arrive on it) and all reads (search,
 * screens, tooltips read it on it); persistence snapshots serialize
 * onto the market executor. No locks, because no other thread touches
 * the index.
 *
 * <p>Recording rules:
 *
 * <ul>
 *   <li>A revisit updates the shop record (owner, nation, chest, stamp)
 *       and appends an observation — never replaces the history.</li>
 *   <li>A repeat observation that says exactly what a very recent one
 *       said is the same measurement, not new evidence, and is
 *       dropped.</li>
 *   <li>History per shop is bounded; the oldest observations fall off a
 *       bounded list instead of growing without end.</li>
 * </ul>
 */
public final class MarketData {
        /** Observations kept per shop; older ones compact away. */
        public static final int HISTORY_PER_SHOP = 20;

        /** Two identical observations inside this window are one measurement. */
        public static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(60);

        private final Map<String, PhysicalShop> shops = new HashMap<>();
        private final Map<String, List<ShopObservation>> history = new HashMap<>();
        /** Bumped on every change so derived views know when to recompute. */
        private long revision;

        public int shopCount() {
                return shops.size();
        }

        public long revision() {
                return revision;
        }

        /** The shop record, or {@code null} when never inspected. */
        public PhysicalShop shop(String shopId) {
                return shops.get(shopId);
        }

        /** All known shops; unsorted, caller decides the order. */
        public List<PhysicalShop> shops() {
                return List.copyOf(shops.values());
        }

        /** The observation history of one shop, oldest first. */
        public List<ShopObservation> observations(String shopId) {
                return List.copyOf(history.getOrDefault(shopId, List.of()));
        }

        /** Every observation in the market, oldest first per shop. */
        public List<ShopObservation> allObservations() {
                List<ShopObservation> all = new ArrayList<>();
                for (List<ShopObservation> observations : history.values()) {
                        all.addAll(observations);
                }
                return all;
        }

        /**
         * Records one inspection: the shop it happened at and what it
         * advertised. Returns {@code true} when the market changed.
         */
        public boolean record(PhysicalShop shop, ShopObservation observation) {
                boolean changed = false;
                PhysicalShop existing = shops.get(shop.shopId());
                if (existing == null) {
                        shops.put(shop.shopId(), shop);
                        changed = true;
                } else {
                        PhysicalShop revisited = existing.revisited(shop.owner(), shop.nation(), observation.observedAt());
                        // A chest resolved on this visit fills the record in but must
                        // never un-resolve what an earlier visit saw.
                        if (revisited.chestPos() == null && shop.chestPos() != null) {
                                revisited = new PhysicalShop(revisited.shopId(), revisited.serverId(), revisited.world(),
                                                revisited.signPos(), shop.chestPos(), revisited.owner(), revisited.nation(),
                                                revisited.spawn(), revisited.createdAt(), revisited.lastObservedAt());
                        }
                        if (!revisited.equals(existing)) {
                                shops.put(shop.shopId(), revisited);
                                changed = true;
                        }
                }
                List<ShopObservation> observations = history.computeIfAbsent(shop.shopId(), key -> new ArrayList<>());
                if (isDuplicate(observations, observation)) {
                        return changed;
                }
                observations.add(observation);
                if (observations.size() > HISTORY_PER_SHOP) {
                        observations.subList(0, observations.size() - HISTORY_PER_SHOP).clear();
                }
                revision++;
                return true;
        }

        /**
         * Restores one shop straight from disk (load path): no revisit
         * semantics, the record lands as stored.
         */
        public void putShop(PhysicalShop shop) {
                shops.put(shop.shopId(), shop);
        }

        /**
         * Saves the spawn reference a player gave for a shop — the town or
         * nation spawn that leads there. {@code true} when the record changed.
         */
        public boolean annotate(String shopId, String spawn) {
                if (spawn == null || spawn.isBlank()) {
                        return false;
                }
                PhysicalShop shop = shops.get(shopId);
                if (shop == null || spawn.equals(shop.spawn())) {
                        return false;
                }
                shops.put(shopId, shop.annotated(spawn));
                revision++;
                return true;
        }

        /** Restores one observation straight from disk (load path). */
        public void putObservation(ShopObservation observation) {
                List<ShopObservation> observations = history.computeIfAbsent(observation.shopId(), key -> new ArrayList<>());
                observations.add(observation);
                if (observations.size() > HISTORY_PER_SHOP) {
                        observations.subList(0, observations.size() - HISTORY_PER_SHOP).clear();
                }
        }

        private boolean isDuplicate(List<ShopObservation> observations, ShopObservation candidate) {
                if (observations.isEmpty()) {
                        return false;
                }
                ShopObservation latest = observations.get(observations.size() - 1);
                if (!sameMeasurement(latest, candidate)) {
                        return false;
                }
                Duration gap = Duration.between(latest.observedAt(), candidate.observedAt());
                return !gap.isNegative() && gap.compareTo(DUPLICATE_WINDOW) < 0;
        }

        /** Two observations say the same thing when every recorded field matches. */
        private static boolean sameMeasurement(ShopObservation a, ShopObservation b) {
                return a.direction() == b.direction()
                                && a.fingerprint().equals(b.fingerprint())
                                && priceEquals(a.price(), b.price())
                                && a.priceScope() == b.priceScope()
                                && nullableEquals(a.bulkSize(), b.bulkSize())
                                && nullableEquals(a.stockRaw(), b.stockRaw())
                                && a.stockUnit() == b.stockUnit()
                                && nullableEquals(a.remainingPercent(), b.remainingPercent())
                                && nullableEquals(a.maxBuyBulks(), b.maxBuyBulks())
                                && nullableEquals(a.maxBuyItems(), b.maxBuyItems())
                                && nullableEquals(a.maxSellBulks(), b.maxSellBulks())
                                && nullableEquals(a.maxSellItems(), b.maxSellItems())
                                && a.supportsAll() == b.supportsAll()
                                && a.enchantments().equals(b.enchantments())
                                && a.sourceType() == b.sourceType();
        }

        private static boolean priceEquals(@Nullable BigDecimal a, @Nullable BigDecimal b) {
                // compareTo, not equals: "1" and "1.0" are the same gold price.
                return a == null ? b == null : b != null && a.compareTo(b) == 0;
        }

        private static boolean nullableEquals(@Nullable Object a, @Nullable Object b) {
                return a == null ? b == null : a.equals(b);
        }
}
