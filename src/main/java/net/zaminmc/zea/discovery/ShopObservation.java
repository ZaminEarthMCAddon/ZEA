package net.zaminmc.zea.discovery;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * One physical EarthMC shop observation. The identity of a shop is its
 * {@link #shopId} — server, dimension and sign position — so two shops
 * run by the same owner selling the same item are two records, never a
 * merged one. Re-observing the same physical shop updates its record;
 * the observation timestamps ({@code firstObservedAt}/{@code
 * lastObservedAt}) are what the repository merge reasons over.
 *
 * <p>Every field is nullable except the identity: an observation with a
 * missing price or unknown chest position is still a valid observation —
 * one optional piece must never discard the rest.
 */
public final class ShopObservation {
        public static final int CURRENT_VERSION = 1;

        /** What the customer does at this shop (never reversed; see ShopParser). */
        public enum Direction {
                /** The shop is selling items — the customer buys from it. */
                BUY_FROM,
                /** The shop is buying items — the customer sells to it. */
                SELL_TO
        }

        @SerializedName("schemaVersion")
        public int schemaVersion = CURRENT_VERSION;

        /** Stable physical identity: {@code server|dimension|sign x,y,z}. */
        @SerializedName("shopId")
        public String shopId;

        /** The connect host the observation happened on. */
        @SerializedName("server")
        public String server;

        /** Dimension id, e.g. {@code minecraft:overworld}. */
        @SerializedName("dimension")
        public String dimension;

        /** The sign block position: {@code x,y,z}. */
        @SerializedName("signPos")
        public String signPos;

        /** The shop chest position, when reliably identified; null otherwise. */
        @SerializedName("chestPos")
        public String chestPos;

        /** Shop owner as the server wrote it. */
        @SerializedName("owner")
        public String owner;

        /** Item id from the structured component, when available. */
        @SerializedName("itemType")
        public String itemType;

        /** The item's display name as the server wrote it. */
        @SerializedName("itemName")
        public String itemName;

        /** Enchantment names from the enchantment section, in order. */
        @SerializedName("enchantments")
        public List<String> enchantments = new ArrayList<>();

        /** Customer-side direction: BUY_FROM or SELL_TO. */
        @SerializedName("direction")
        public Direction direction;

        /** The price exactly as written ("2G"). */
        @SerializedName("priceRaw")
        public String priceRaw;

        /** The numeric price value, when it parsed; null otherwise. */
        @SerializedName("priceValue")
        public Double priceValue;

        /** The currency as written ("G"), when it parsed. */
        @SerializedName("currency")
        public String currency;

        /** The shop's stock count, when stated. */
        @SerializedName("stock")
        public Integer stock;

        /** The per-transaction maximum ("You can buy 16"), when stated. */
        @SerializedName("maxTransaction")
        public Integer maxTransaction;

        /** The remaining percentage, when stated. */
        @SerializedName("remainingPercent")
        public Integer remainingPercent;

        /** The town the sign stood in, when known from the map data. */
        @SerializedName("town")
        public String town;

        /** The town's nation, when known. */
        @SerializedName("nation")
        public String nation;

        /** First time this physical shop was observed (epoch ms). */
        @SerializedName("firstObservedAt")
        public long firstObservedAt;

        /** Last time this physical shop was observed (epoch ms). */
        @SerializedName("lastObservedAt")
        public long lastObservedAt;

        /** Where the observation came from: "local" or "repo". */
        @SerializedName("source")
        public String source;

        /** Explicitly stale/removed shops keep the record with this flag. */
        @SerializedName("invalidated")
        public boolean invalidated;

        /** A short note on why the shop was invalidated, when it was. */
        @SerializedName("invalidatedReason")
        public String invalidatedReason;
}
