package net.zaminmc.zea.chat.model;

/**
 * A Discord recruitment advertisement, e.g. {@code Premium [Discord]
 * nf_miracle.: NEW PLAYERS! need a town, and want free tp, diamonds and
 * housing? Type /t join Berlin}. Ranks, usernames, towns and the
 * advertisement text all vary; only the Discord-forwarded header and
 * the recruitment shape are recognized.
 *
 * @param sender        the advertised sender, as written ("nf_miracle.")
 * @param advertisement the advertisement text after the sender
 */
public record DiscordRecruitmentMessage(String sender, String advertisement)
		implements MessagePayload {
	public DiscordRecruitmentMessage {
		if (sender == null || advertisement == null) {
			throw new NullPointerException("discord recruitment fields must not be null");
		}
		if (sender.isBlank() || advertisement.isBlank()) {
			throw new IllegalArgumentException("discord recruitment fields must not be blank");
		}
	}
}
