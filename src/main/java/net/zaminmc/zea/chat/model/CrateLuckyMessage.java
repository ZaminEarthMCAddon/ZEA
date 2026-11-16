package net.zaminmc.zea.chat.model;

/**
 * A crate reward announcement, e.g. {@code (hubbayes got lucky and
 * received a bruh box /vote)}. The server formats it with per-character
 * styling that flattens away; the player and crate name are parsed,
 * never hardcoded.
 *
 * @param player    the player the announcement names
 * @param crateName the reward/crate name as written ("bruh box")
 */
public record CrateLuckyMessage(String player, String crateName) implements MessagePayload {
	public CrateLuckyMessage {
		if (player == null || crateName == null) {
			throw new NullPointerException("crate lucky fields must not be null");
		}
		if (player.isBlank() || crateName.isBlank()) {
			throw new IllegalArgumentException("crate lucky fields must not be blank");
		}
	}
}
