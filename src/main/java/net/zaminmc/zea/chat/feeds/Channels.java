package net.zaminmc.zea.chat.feeds;

import java.util.List;
import java.util.Optional;

/**
 * The writable EarthMC server channels (verified table; mirrors ZEA-REPO
 * {@code datasets/channels.json}, which becomes the runtime source once
 * the repository client exists).
 *
 * <p>Each channel's {@code messageCommand} is the per-message form used
 * when the matching tab is selected ({@code /global <msg>}, {@code /nc
 * <msg>}, ...). Global accepts both {@code g} and {@code global} on
 * EarthMC; the full word is used so the send matches the command the
 * player would type themselves.
 */
public final class Channels {
        public static final WriteChannel GLOBAL = new WriteChannel("global", "Global", "global");
        public static final WriteChannel LOCAL = new WriteChannel("local", "Local", "local");
        public static final WriteChannel TRADE = new WriteChannel("trade", "Trade", "tradechat");
        public static final WriteChannel NATION = new WriteChannel("nation", "Nation", "nc");
        public static final WriteChannel TOWN = new WriteChannel("town", "Town", "tc");

        public static List<WriteChannel> all() {
                return List.of(GLOBAL, LOCAL, TRADE, NATION, TOWN);
        }

        public static Optional<WriteChannel> byId(String id) {
                return all().stream().filter(channel -> channel.id().equals(id)).findFirst();
        }

        private Channels() {
        }
}
