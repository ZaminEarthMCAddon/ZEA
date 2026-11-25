package net.zaminmc.zea.market.index;

import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.pricing.PriceMath;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The item-variant index: every exact item variant the market has
 * observed, with the physical shops that offer it — the grouping the
 * inventory browser presents.
 *
 * <p>Two levels, never collapsed into each other:
 *
 * <ul>
 *   <li>A <b>variant</b> is one exact item fingerprint — one tile in the
 *       browser, however many shops sell it. A display name is never a
 *       variant identity; the fingerprint token decides, so an enchanted
 *       pickaxe and a plain one are different tiles, and a thousand
 *       owners of one variant still show one tile.</li>
 *   <li>An <b>offer</b> is one physical shop's <em>latest</em> observation.
 *       One shop trades one thing: a revisit whose response says something
 *       different replaces what stood before, so a sign that changed its
 *       item stops being a seller of the old one — the old listing leaves
 *       the index with the shop instead of haunting it as a phantom
 *       seller. Offers are never merged away across shops: identical
 *       items at identical prices stay distinct shops, each independently
 *       navigable.</li>
 * </ul>
 *
 * <p>Only server-verified and player-confirmed observations with exact
 * fingerprints group into variants. Sign-only adverts and unmatchable
 * identities stay out of the index — they can aid discovery but can
 * never claim to be one exact product. An offer's unit price is the
 * observation's price
 * normalized per individual item ({@link PriceMath}); an offer whose
 * price cannot normalize is still listed, it just never enters the
 * statistics — see {@link VariantPricing}.
 *
 * <p>The index reads only the in-memory market model and is rebuilt per
 * query on the client thread (screens cache by the market's revision);
 * it performs no parsing, no disk access and no world access.
 */
public final class ItemVariantIndex {
        private ItemVariantIndex() {
        }

        /** One exact variant and every physical shop observed offering it. */
        public record Variant(String token, String display, List<Offer> offers) {
                public Variant {
                        if (token == null || display == null || offers == null) {
                                throw new NullPointerException("variant fields must not be null");
                        }
                        offers = List.copyOf(offers);
                }

                /** The physical shops currently offering this variant, unsorted. */
                public List<PhysicalShop> shops() {
                        return offers.stream().map(Offer::shop).toList();
                }

                /** The distinct owners across the variant's offers. */
                public List<String> owners() {
                        return offers.stream()
                                        .map(Offer::shop)
                                        .map(PhysicalShop::owner)
                                        .filter(owner -> owner != null && !owner.isBlank())
                                        .distinct()
                                        .sorted(String.CASE_INSENSITIVE_ORDER)
                                        .toList();
                }

                /** The most recent observation time among the variant's offers. */
                public java.time.Instant lastObservedAt() {
                        return offers.stream()
                                        .map(offer -> offer.observation().observedAt())
                                        .max(Comparator.naturalOrder())
                                        .orElseThrow();
                }

                /**
                 * The variant's enchantment lines as the servers reported them,
                 * most-reported first (first-seen on a tie). Presentation and
                 * search text only — the fingerprint, never this list, is the
                 * identity.
                 */
                public List<String> enchantments() {
                        Map<String, Integer> votes = new LinkedHashMap<>();
                        for (Offer offer : offers) {
                                for (String enchantment : offer.observation().enchantments()) {
                                        votes.merge(enchantment, 1, Integer::sum);
                                }
                        }
                        return votes.entrySet().stream()
                                        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                                                        .thenComparing(Map.Entry::getKey))
                                        .map(Map.Entry::getKey)
                                        .toList();
                }

                /**
                 * The text this variant matches by search: item name, registry id
                 * (inside the fingerprint token), the server's enchantment lines
                 * and the owners. Search never reconstructs the identity — this
                 * is presentation text only.
                 */
                public String searchText() {
                        StringBuilder text = new StringBuilder(display.toLowerCase(Locale.ROOT))
                                        .append(' ').append(token.toLowerCase(Locale.ROOT));
                        for (Offer offer : offers) {
                                for (String enchantment : offer.observation().enchantments()) {
                                        text.append(' ').append(enchantment.toLowerCase(Locale.ROOT));
                                }
                                String owner = offer.shop().owner();
                                if (owner != null) {
                                        text.append(' ').append(owner.toLowerCase(Locale.ROOT));
                                }
                        }
                        return text.toString();
                }
        }

        /** One physical shop's offer of a variant. */
        public record Offer(PhysicalShop shop, ShopObservation observation,
                        @Nullable BigDecimal unitPrice) {
                public Offer {
                        if (shop == null || observation == null) {
                                throw new NullPointerException("offer fields must not be null");
                        }
                }
        }

        /**
         * Builds the variant index over the market's current data. One shop
         * contributes at most one offer — its latest observation, whatever
         * it says. An older observation is history, never an active offer,
         * even when it named a different item: the shop changed, and the
         * listing at those coordinates is gone.
         */
        public static List<Variant> variants(MarketData data) {
                // The shop's latest observation decides what it trades — any
                // observation competes for latest, a sign-only advert too: the
                // newest interaction is the shop's present state, even when it
                // speaks only in display text.
                Map<String, Offer> latestPerShop = new LinkedHashMap<>();
                for (ShopObservation observation : data.allObservations()) {
                        PhysicalShop shop = data.shop(observation.shopId());
                        if (shop == null) {
                                continue;
                        }
                        Offer candidate = new Offer(shop, observation,
                                        PriceMath.normalize(observation.price(), observation.priceScope(),
                                                        observation.bulkSize()).orElse(null));
                        latestPerShop.merge(observation.shopId(), candidate, ItemVariantIndex::latest);
                }
                // Group the shops' current offers by exact identity. A display-only
                // latest observation (a sign-only advert) vouches for the shop's
                // present state only by item text: the same text an exact record
                // carries keeps the shop's exact offer alive, a different text
                // retires it — the shop now trades something the index cannot name.
                Map<String, Map<String, Offer>> byToken = new LinkedHashMap<>();
                for (Offer offer : latestPerShop.values()) {
                        Offer offered = offer;
                        if (!offer.observation().fingerprint().isExact()) {
                                offered = exactRecordMatching(data, offer);
                                if (offered == null) {
                                        continue;
                                }
                        }
                        byToken.computeIfAbsent(offered.observation().fingerprint().token(),
                                                        key -> new LinkedHashMap<>())
                                        .merge(offered.shop().shopId(), offered, ItemVariantIndex::latest);
                }
                // One tile per token; the display name is the shops' consensus.
                List<Variant> variants = new ArrayList<>();
                for (Map.Entry<String, Map<String, Offer>> entry : byToken.entrySet()) {
                        List<Offer> offers = List.copyOf(entry.getValue().values());
                        String display = consensusDisplay(offers);
                        variants.add(new Variant(entry.getKey(), display, offers));
                }
                return variants;
        }

        /**
         * The exact offer a display-only latest observation stands for, or
         * {@code null} when the shop leaves the index. The shop's newest exact
         * record decides: the same item text the sign advertises confirms the
         * shop still trades it; any other text — or no exact record at all —
         * means the shop's exact listing is gone.
         */
        private static @Nullable Offer exactRecordMatching(MarketData data, Offer displayOnly) {
                String display = displayOnly.observation().itemDisplay();
                if (display == null || display.isBlank()) {
                        return null;
                }
                List<ShopObservation> history = data.observations(displayOnly.shop().shopId());
                for (int i = history.size() - 1; i >= 0; i--) {
                        ShopObservation observation = history.get(i);
                        if (!trustedSource(observation.sourceType())
                                        || !observation.fingerprint().isExact()) {
                                continue;
                        }
                        String recorded = observation.itemDisplay();
                        if (recorded == null || !recorded.trim().equalsIgnoreCase(display.trim())) {
                                return null;
                        }
                        return new Offer(displayOnly.shop(), observation,
                                        PriceMath.normalize(observation.price(), observation.priceScope(),
                                                        observation.bulkSize()).orElse(null));
                }
                return null;
        }

        /** The offer whose observation is newer; ties keep the incumbent. */
        private static Offer latest(Offer current, Offer candidate) {
                return candidate.observation().observedAt().isAfter(current.observation().observedAt())
                                ? candidate : current;
        }

        /**
         * The sources the index trusts with an exact identity: the server's
         * own response, another client's capture of the same, and the player's
         * own entered-or-confirmed listing. Sign-only adverts never qualify —
         * their identity is display text, not an exact product.
         */
        private static boolean trustedSource(SourceType sourceType) {
                return sourceType == SourceType.SERVER_RESPONSE
                                || sourceType == SourceType.REMOTE_SHARED
                                || sourceType == SourceType.MANUAL;
        }

        /**
         * The variant's display name: the most frequently reported item text
         * across its offers, lexicographic on a tie so the choice is
         * deterministic. The display is presentation — the token is the
         * identity.
         */
        private static String consensusDisplay(List<Offer> offers) {
                Map<String, Integer> votes = new LinkedHashMap<>();
                for (Offer offer : offers) {
                        String display = offer.observation().itemDisplay();
                        if (display != null && !display.isBlank()) {
                                votes.merge(display, 1, Integer::sum);
                        }
                }
                return votes.entrySet().stream()
                                .max(Map.Entry.<String, Integer>comparingByValue()
                                                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                                .map(Map.Entry::getKey)
                                .orElse("");
        }
}
