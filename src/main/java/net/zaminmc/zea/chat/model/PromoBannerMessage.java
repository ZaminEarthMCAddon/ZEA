package net.zaminmc.zea.chat.model;

/**
 * A centered promotional banner, e.g. a fully-formatted {@code /premium}
 * line surrounded by blank rows. The banner's content is the command it
 * advertises; nothing else about the message is structured.
 *
 * @param command the advertised command, with its leading slash
 */
public record PromoBannerMessage(String command) implements MessagePayload {
	public PromoBannerMessage {
		if (command == null) {
			throw new NullPointerException("promo banner command must not be null");
		}
		if (command.isBlank()) {
			throw new IllegalArgumentException("promo banner command must not be blank");
		}
	}
}
