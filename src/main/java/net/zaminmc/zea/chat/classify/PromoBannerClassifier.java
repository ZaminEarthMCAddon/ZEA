package net.zaminmc.zea.chat.classify;

import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.model.PromoBannerMessage;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes the server's centered promotional banners — a message whose
 * entire content is one advertised command (the observed example is
 * {@code /premium}, spelled out per-character with heavy formatting and
 * surrounded by blank rows).
 *
 * <p>Only the bare-command shape matches: ordinary player messages
 * always carry a sender prefix or prose, so a conversation that mentions
 * "/premium" is never hidden, and multi-line system responses (shop
 * prompts, channel reports) never reduce to a lone command.
 */
public final class PromoBannerClassifier implements MessageClassifier {
        /** The whole message is one slash command, padded by centered spacing. */
        private static final Pattern BANNER = Pattern.compile("^/[a-z0-9_]{1,24}$", Pattern.CASE_INSENSITIVE);

        @Override
        public Optional<Result> tryClassify(CapturedMessage message) {
                String text = message.text();
                if (text == null || text.isBlank()) {
                        return Optional.empty();
                }
                // The banner arrives with its centering: blank rows around the one
                // command line. Every non-blank line must reduce to the banner.
                String banner = null;
                for (String line : text.split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) {
                                continue;
                        }
                        if (banner != null || !BANNER.matcher(trimmed).matches()) {
                                return Optional.empty();
                        }
                        banner = trimmed;
                }
                if (banner == null) {
                        return Optional.empty();
                }
                return Optional.of(Result.of(MessageCategory.PROMO_BANNER, new PromoBannerMessage(banner)));
        }

        @Override
        public String toString() {
                return "promo-banner";
        }
}
