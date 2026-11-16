package net.zaminmc.zea.chat.model;

/**
 * A renamed-item showcase advertisement, e.g. {@code ☄🐇
 * [Germany|Bremen] axieyoel is holding [Dragon]}. The message carries a
 * structured item component (the object-replacement placeholder or an
 * {@code [item/...]} label) and presents a renamed item as an ad; player
 * names, nations, towns and item names all vary.
 *
 * @param holder  the player holding the showcased item
 * @param itemName the custom (renamed) item name as written
 */
public record ItemShowcaseMessage(String holder, String itemName) implements MessagePayload {
	public ItemShowcaseMessage {
		if (holder == null || itemName == null) {
			throw new NullPointerException("item showcase fields must not be null");
		}
		if (holder.isBlank() || itemName.isBlank()) {
			throw new IllegalArgumentException("item showcase fields must not be blank");
		}
	}
}
