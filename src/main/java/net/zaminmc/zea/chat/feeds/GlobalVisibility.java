package net.zaminmc.zea.chat.feeds;

import net.zaminmc.zea.chat.model.MessageCategory;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the Global aggregate tab hides. Global is the "every message"
 * view — every category shows there by default — and this model carries
 * the per-type opt-outs the user configured: one switch per kind of
 * message, so the aggregate can be trimmed to exactly what the player
 * wants to read.
 *
 * <p>Two categories are deliberately not toggleable. GLOBAL itself —
 * the aggregate must always show the channel it is named after — and
 * UNCLASSIFIED: a message format ZEA does not recognize must never
 * vanish from the aggregate just because it arrived untagged (the
 * classifier's blind spot would read as messages disappearing). Unknown
 * formats stay visible on Global; the Other tab exists for inspecting
 * them, and the retention wrap keeps anything a feed hides retrievable
 * regardless.
 *
 * <p>Pure model, no Minecraft types; the config layer builds it from its
 * booleans, the feed filter and the unread tracker consume it.
 */
public final class GlobalVisibility {
	/**
	 * The categories the user may turn off on the Global tab: every kind
	 * of message except Global's own channel and the unclassified bucket.
	 */
	public static final Set<MessageCategory> TOGGLEABLE = Set.of(
					MessageCategory.LOCAL, MessageCategory.TRADE,
					MessageCategory.NATION, MessageCategory.TOWN,
					MessageCategory.PRIVATE_MESSAGE, MessageCategory.DISCORD,
					MessageCategory.VOTE, MessageCategory.CRATE_WIN,
					MessageCategory.DEATH, MessageCategory.SYSTEM);

	/** The shared instance hiding nothing; avoids an allocation per read. */
	public static final GlobalVisibility SHOW_EVERYTHING = new GlobalVisibility(Set.of());

	private final Set<MessageCategory> hidden;

	/**
	 * @param hiddenCategories the categories to keep off the Global tab;
	 *                         anything not toggleable is ignored, and null
	 *                         reads as "hide nothing"
	 */
	public GlobalVisibility(Set<MessageCategory> hiddenCategories) {
		Set<MessageCategory> toggleable = EnumSet.noneOf(MessageCategory.class);
		if (hiddenCategories != null) {
			for (MessageCategory category : hiddenCategories) {
				if (TOGGLEABLE.contains(category)) {
					toggleable.add(category);
				}
			}
		}
		this.hidden = toggleable.isEmpty() ? Set.of() : toggleable;
	}

	/** Whether the Global tab hides this category. */
	public boolean hides(MessageCategory category) {
		return hidden.contains(category);
	}

	/** Whether nothing is hidden and Global shows literally everything. */
	public boolean showsEverything() {
		return hidden.isEmpty();
	}

	/** The hidden categories, never null and never mutable from outside. */
	public Set<MessageCategory> hidden() {
		return hidden;
	}
}
