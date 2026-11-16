package net.zaminmc.zea.chat.classify;

import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.CrateLuckyMessage;
import net.zaminmc.zea.chat.model.MessageCategory;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes crate reward announcements of the "got lucky" shape —
 * {@code (hubbayes got lucky and received a bruh box /vote)} — distinct
 * from the {@link CrateWinClassifier}'s "won N currency from a crate"
 * shape. The server wraps the announcement in parentheses with heavy
 * per-character formatting that flattens away in the captured text; the
 * player and crate name are parsed, never hardcoded. The parentheses are
 * required: a player saying "I got lucky and received a hug" in normal
 * chat must never classify as an announcement.
 */
public final class CrateLuckyClassifier implements MessageClassifier {
	private static final Pattern LUCKY = Pattern.compile(
			"^\\((\\w{1,16}) got lucky and received (?:an? )?(.+?)(?: /vote)?\\)$");

	@Override
	public Optional<Result> tryClassify(CapturedMessage message) {
		var matcher = LUCKY.matcher(message.text());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(Result.of(MessageCategory.CRATE_LUCKY,
				new CrateLuckyMessage(matcher.group(1), matcher.group(2))));
	}

	@Override
	public String toString() {
		return "crate-lucky";
	}
}
