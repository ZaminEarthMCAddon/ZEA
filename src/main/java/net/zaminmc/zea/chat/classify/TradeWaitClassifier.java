package net.zaminmc.zea.chat.classify;

import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.model.TradeWait;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes the server's trade-cooldown rejection line.
 *
 * <p>Observed shapes (formatting stripped, as the flattened capture
 * text always is — the raw line carries {@code §6» §r§c} codes):
 *
 * <ul>
 *   <li>{@code » You must wait another 4 seconds before sending your
 *       next message in Trade.}</li>
 *   <li>{@code » You must wait another 4 minutes and 58 seconds before
 *       sending your next message in Trade.}</li>
 * </ul>
 *
 * <p>Minutes and seconds are the only two units observed; the singular
 * forms ("1 minute", "1 second") are accepted. The wait time is part of
 * the payload ({@link TradeWait}) so the pacing model can sync from the
 * server's own numbers. The category is SYSTEM — the line is a server
 * system message — but the feed filter hides it everywhere while trade
 * pacing is on, because the client answers it itself: the rejected
 * message queues and the status line shows the countdown.
 */
public final class TradeWaitClassifier implements MessageClassifier {
	private static final Pattern WAIT = Pattern.compile(
					"^» You must wait another (?:(\\d+) minutes? and )?(\\d+) seconds?"
									+ " before sending your next message in Trade\\.$");

	@Override
	public Optional<Result> tryClassify(CapturedMessage message) {
		var match = WAIT.matcher(message.text());
		if (!match.matches()) {
			return Optional.empty();
		}
		long minutes = match.group(1) == null ? 0L : Long.parseLong(match.group(1));
		long seconds = Long.parseLong(match.group(2));
		return Optional.of(Result.of(MessageCategory.SYSTEM,
						new TradeWait((minutes * 60L + seconds) * 1000L)));
	}

	@Override
	public String toString() {
		return "trade-wait";
	}
}
