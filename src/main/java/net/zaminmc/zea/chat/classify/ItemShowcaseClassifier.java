package net.zaminmc.zea.chat.classify;

import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.ItemShowcaseMessage;
import net.zaminmc.zea.chat.model.MessageCategory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes renamed-item showcase advertisements — messages that use a
 * structured item component to parade a custom-named item:
 * {@code ☄🐇 [Germany|Bremen] axieyoel is holding [Dragon]}.
 *
 * <p>Two anchors must hold together: the message carries a structured
 * item component (the object-replacement placeholder the client renders
 * the item with, or an explicit {@code [item/...]} label), and it ends
 * with the {@code is holding [<name>]} presentation. Ordinary
 * item-related messages — someone linking an item in chat — have no
 * "is holding" tail, and an "is holding" phrase without an item
 * component is prose; neither is hidden. The custom item name is kept
 * as payload metadata.
 */
public final class ItemShowcaseClassifier implements MessageClassifier {
	/** The {@code is holding [<name>]} tail, as the last thing the message says. */
	private static final Pattern HOLDING_TAIL =
			Pattern.compile(".*\\bis holding \\[([^\\[\\]]{1,64})\\]\\s*$", Pattern.CASE_INSENSITIVE);

	/** The holder name right before the tail: {@code <player> is holding [x]}. */
	private static final Pattern HOLDER =
			Pattern.compile("(\\w{1,16})\\s+is holding", Pattern.CASE_INSENSITIVE);

	@Override
	public Optional<Result> tryClassify(CapturedMessage message) {
		String text = message.text();
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		boolean hasItemComponent = text.indexOf('\uFFFC') >= 0
				|| text.contains("[item/");
		if (!hasItemComponent) {
			return Optional.empty();
		}
		Matcher tail = HOLDING_TAIL.matcher(text);
		if (!tail.matches()) {
			return Optional.empty();
		}
		Matcher holder = HOLDER.matcher(text);
		if (!holder.find()) {
			return Optional.empty();
		}
		return Optional.of(Result.of(MessageCategory.ITEM_SHOWCASE,
				new ItemShowcaseMessage(holder.group(1), tail.group(1))));
	}

	@Override
	public String toString() {
		return "item-showcase";
	}
}
