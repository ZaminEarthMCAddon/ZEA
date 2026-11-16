package net.zaminmc.zea.chat.classify;

import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.DiscordRecruitmentMessage;
import net.zaminmc.zea.chat.model.MessageCategory;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes Discord recruitment advertisements: messages forwarded by
 * the server's Discord integration whose content recruits new players —
 * {@code Premium [Discord] nf_miracle.: NEW PLAYERS! need a town, and
 * want free tp, diamonds and housing? Type /t join Berlin}.
 *
 * <p>Both anchors must hold: the message is Discord-forwarded (a
 * {@code [Discord]} marker after an optional staff rank, before the
 * sender name), and the text behind it carries recruitment language —
 * a "new players" phrase plus a town or nation join reference or a
 * free-stuff promise. Different ranks, usernames, towns and wording all
 * pass; a normal conversation that merely mentions Discord does not,
 * and neither does any other Discord forward.
 */
public final class DiscordRecruitmentClassifier implements MessageClassifier {
	/** {@code [rank ]<Discord> sender.: advertisement}, the rank optional. */
	private static final Pattern FORWARDED = Pattern.compile(
			"^(?:\\S+ )?\\[Discord\\] (\\S+?): (.+)$", Pattern.CASE_INSENSITIVE);

	private static final Pattern NEW_PLAYERS = Pattern.compile("new player", Pattern.CASE_INSENSITIVE);

	private static final Pattern JOIN_REFERENCE =
			Pattern.compile("/[tn] join |need a town|want a town|want free|free tp|free diamonds|free housing",
					Pattern.CASE_INSENSITIVE);

	@Override
	public Optional<Result> tryClassify(CapturedMessage message) {
		var matcher = FORWARDED.matcher(message.text());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String advertisement = matcher.group(2);
		String lowered = advertisement.toLowerCase(Locale.ROOT);
		if (!NEW_PLAYERS.matcher(lowered).find() || !JOIN_REFERENCE.matcher(lowered).find()) {
			return Optional.empty();
		}
		return Optional.of(Result.of(MessageCategory.DISCORD_RECRUITMENT,
				new DiscordRecruitmentMessage(matcher.group(1), advertisement)));
	}

	@Override
	public String toString() {
		return "discord-recruitment";
	}
}
