package net.zaminmc.zea.discovery;

import net.zaminmc.zea.earthmc.ServerIdentity;
import net.zaminmc.zea.lifecycle.ClientLifecycle;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * The stable physical identity of a shop. A shop is its place on the
 * map: the server it lives on, the dimension, and the sign's block
 * position — with the chest position kept as data but never as identity
 * (a chest may be replaced while the shop's offer and location stay the
 * same, and two chests cannot share one sign).
 *
 * <p>The identity deliberately excludes owner, item, price and stock:
 * those describe the shop's offer and change over time, and merging two
 * records because their offers match is the exact bug this class exists
 * to prevent — two shops by one owner selling one item are two shops.
 */
public final class ShopIdentity {
        private ShopIdentity() {
        }

        /**
         * The shop id: {@code host|dimension|sign x,y,z}. The host is the
         * normalized connect identity ({@link ServerIdentity}) so the same
         * physical shop seen via different address spellings lands on one
         * record.
         */
        public static String shopId(String serverAddress, String dimension, BlockPos signPos) {
                String host = ServerIdentity.of(serverAddress)
                                .map(ServerIdentity::host)
                                .orElse(serverAddress == null ? "unknown" : serverAddress.toLowerCase(Locale.ROOT));
                return host + "|" + dimension + "|" + formatPos(signPos);
        }

        /** A position as {@code x,y,z} — the compact form stored in records. */
        public static String formatPos(BlockPos pos) {
                return pos == null ? null : pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }

        /** The local player's server identity, or a safe fallback. */
        public static String currentServer(ClientLifecycle lifecycle) {
                return lifecycle == null ? null
                                : lifecycle.earthMcServer().map(ServerIdentity::host).orElse(null);
        }
}
