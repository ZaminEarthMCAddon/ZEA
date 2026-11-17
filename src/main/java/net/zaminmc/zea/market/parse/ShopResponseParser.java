package net.zaminmc.zea.market.parse;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStackTemplate;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.market.ItemFingerprints;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopResponse;
import net.zaminmc.zea.market.model.StockUnit;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the EarthMC shop response block into a {@link ShopResponse}.
 *
 * <p>The response arrives as several lines — one message with embedded
 * newlines, or consecutive separate messages — and in two observed
 * styles: a boxed block (lines wrapped in {@code | ... |} between
 * {@code +---+} rules) and a plain block (bare lines, an
 * {@code Enchantments:} label section). Lines are therefore normalized
 * first (decoration stripped), then anchored on the exact observed
 * shapes ({@code Owner: }, {@code Stock: }, {@code Price per 7x ... - 1G},
 * {@code This shop is SELLING items.}, {@code 100% remaining.},
 * {@code You can buy 4.}, {@code Enchantments:}, …); anything else is
 * ignored. A run without the direction anchor, or without an owner and
 * item identity, is declined rather than guessed.
 *
 * <p>Structure first: the {@code [Item Preview]} hover on the item line
 * carries the actual item template, which is what identities are built
 * from. The displayed item text is kept only as a fallback and as a
 * cross-check, and the server's enchantment section is kept verbatim as
 * cross-check evidence — never as the item identity. Nothing is ever
 * defaulted — a missing field stays {@code null} and the response
 * degrades to {@code PARTIAL}.
 */
public final class ShopResponseParser {
	private static final String HEADER = "Shop Information:";
	private static final String OWNER_PREFIX = "Owner: ";
	private static final String ITEM_PREFIX = "Item: ";
	private static final String ITEM_PREVIEW_SUFFIX = "[Item Preview]";
	private static final String STOCK_PREFIX = "Stock: ";
	private static final String SPACE_PREFIX = "Space: ";
	private static final String PRICE_PREFIX = "Price per ";
	private static final String PRICE_SEPARATOR = " - ";
	private static final String SELLING_ANCHOR = "This shop is SELLING items.";
	private static final String BUYING_ANCHOR = "This shop is BUYING items.";
	private static final String ENCHANTMENT_LABEL = "Enchantments:";
	private static final String SELL_LIMIT_PREFIX = "You can sell ";
	private static final String ALL_PREFIX = "Enter all in chat";

	/** {@code There are 7 items in each bulk and you can buy 64 bulks.} — both observed wordings. */
	private static final Pattern BULK_INFO = Pattern.compile(
			"^There are (\\d+) items (?:in each bulk|per bulk) and you can (buy|sell) (\\d+) bulks\\.$");

	/** {@code You can buy 4.} — an item limit, reported by per-item SELLING shops. */
	private static final Pattern BUY_LIMIT = Pattern.compile("^You can buy (\\d+)\\.$");

	/** {@code 100% remaining.} — the shelf-fill state the response may report. */
	private static final Pattern REMAINING = Pattern.compile("^(\\d{1,3})% remaining\\.$");

	/** The bulk size inside a per-bulk price: {@code Price per 7x Block of Diamond - 1G}. */
	private static final Pattern PRICE_BULK_ITEM = Pattern.compile("^(\\d+)x\\s+(.*)$");

	/**
	 * One enchantment line inside the server's enchantment section:
	 * {@code Fortune III}, {@code Punch II}. Levels run I–X; anything
	 * else is not an enchantment line and closes the section.
	 */
	private static final Pattern ENCHANTMENT_LINE = Pattern.compile(
			"^([A-Za-z][A-Za-z' ]*?)\\s+(I{1,3}|IV|V|VI{1,3}|IX|X)$");

	/** A pure box rule: {@code +--------+}. */
	private static final Pattern BOX_RULE = Pattern.compile("^\\+[-+]*\\+$");

	/**
	 * Whether this text is a line of a shop response. The interaction
	 * collector buffers only lines this accepts, so unrelated chat never
	 * enters a shop record. Box decoration ({@code | }, {@code +---+})
	 * is accepted because it wraps observed response lines; it adds no
	 * fields of its own.
	 */
	public static boolean isShopLine(String text) {
		String line = normalize(text);
		return line != null && isShopLineNormalized(line);
	}

	private static boolean isShopLineNormalized(String line) {
		if (line.startsWith("+") || line.startsWith(ENCHANTMENT_LABEL)) {
			return true;
		}
		if (isEnchantmentLine(line)) {
			return true;
		}
		return textAnchor(line);
	}

	/** The observed field and instruction shapes, decoration already stripped. */
	private static boolean textAnchor(String text) {
		return text.equals(HEADER)
				|| text.startsWith(OWNER_PREFIX)
				|| text.startsWith(ITEM_PREFIX)
				|| text.startsWith(STOCK_PREFIX)
				|| text.startsWith(SPACE_PREFIX)
				|| text.startsWith(PRICE_PREFIX)
				|| text.equals(SELLING_ANCHOR)
				|| text.equals(BUYING_ANCHOR)
				|| text.startsWith(SELL_LIMIT_PREFIX)
				|| text.startsWith(ALL_PREFIX)
				|| BULK_INFO.matcher(text).matches()
				|| BUY_LIMIT.matcher(text).matches()
				|| REMAINING.matcher(text).matches()
				|| text.startsWith("Enter in chat, how ");
	}

	/**
	 * Strips the box decoration from a response line. A pure box rule
	 * ({@code +---+}) is returned as-is: it frames the response, so it
	 * counts as a response line (the run stays open across it) but adds
	 * no field of its own. {@code null} only for blank lines.
	 */
	public static @Nullable String normalize(String raw) {
		String line = raw.trim();
		if (line.isEmpty()) {
			return null;
		}
		while (line.startsWith("|")) {
			line = line.substring(1).trim();
		}
		while (line.endsWith("|")) {
			line = line.substring(0, line.length() - 1).trim();
		}
		return line.isEmpty() ? null : line;
	}

	/** Whether the line opens the server's enchantment section. */
	private static boolean isEnchantmentHeader(String line) {
		return line.startsWith("+")
				? line.contains("Enchantments")
				: line.equals(ENCHANTMENT_LABEL);
	}

	private static boolean isEnchantmentLine(String line) {
		return ENCHANTMENT_LINE.matcher(line).matches();
	}

	/**
	 * The shop response lines one captured message contains, in order, or
	 * {@code null} when the message holds none. A multi-line message
	 * contributes each of its shop-shaped lines; plain chat contributes
	 * nothing and never enters a shop record.
	 */
	public static @Nullable List<String> shopLines(CapturedMessage message) {
		List<String> lines = null;
		for (String raw : message.text().split("\\n")) {
			String line = normalize(raw);
			if (line != null && isShopLineNormalized(line)) {
				if (lines == null) {
					lines = new ArrayList<>();
				}
				lines.add(line);
			}
		}
		return lines;
	}

	/**
	 * Parses a run of shop-shaped lines, in arrival order. The preview
	 * template is looked up in the component that carried the item line.
	 */
	public Optional<ShopResponse> parse(List<CapturedMessage> messages, Instant receivedAt) {
		String owner = null;
		String itemDisplay = null;
		ItemStackTemplate preview = null;
		BigDecimal price = null;
		PriceScope priceScope = null;
		Integer bulkSize = null;
		String stockRaw = null;
		StockUnit stockUnit = StockUnit.UNKNOWN;
		Integer remainingPercent = null;
		Integer maxBuyBulks = null;
		Integer maxBuyItems = null;
		Integer maxSellBulks = null;
		Integer maxSellItems = null;
		boolean supportsAll = false;
		ShopDirection direction = null;
		boolean inEnchantments = false;
		List<String> enchantments = new ArrayList<>();
		List<String> rawLines = new ArrayList<>();

		for (CapturedMessage message : messages) {
			ItemStackTemplate messagePreview = findPreview(message.original());
			for (String raw : message.text().split("\\n")) {
				String text = normalize(raw);
				if (text == null) {
					continue;
				}
				// A pure box rule frames the response and carries nothing.
				if (BOX_RULE.matcher(text).matches()) {
					continue;
				}
				// The enchantment section runs from its header until a
				// shop field resumes or an unrelated line interrupts it;
				// its lines are cross-check evidence, never identity.
				if (inEnchantments) {
					if (isEnchantmentLine(text)) {
						enchantments.add(text);
						rawLines.add(text);
						continue;
					}
					if (isEnchantmentHeader(text)) {
						continue;
					}
					inEnchantments = false;
				}
				if (text.equals(HEADER)) {
					rawLines.add(text);
				} else if (text.startsWith(OWNER_PREFIX)) {
					owner = text.substring(OWNER_PREFIX.length()).trim();
					rawLines.add(text);
				} else if (text.startsWith(ITEM_PREFIX)) {
					itemDisplay = displayOf(text);
					rawLines.add(text);
					if (messagePreview != null) {
						preview = messagePreview;
					}
				} else if (text.startsWith(STOCK_PREFIX)) {
					stockRaw = text.substring(STOCK_PREFIX.length()).trim();
					// Stackable items may report stacks, unstackable
					// equipment individual items: keep the raw value and
					// admit the unit is not reliably known.
					stockUnit = StockUnit.UNKNOWN;
					rawLines.add(text);
				} else if (text.startsWith(SPACE_PREFIX)) {
					stockRaw = text.substring(SPACE_PREFIX.length()).trim();
					stockUnit = StockUnit.SPACE;
					rawLines.add(text);
				} else if (text.startsWith(PRICE_PREFIX)) {
					PriceLine parsed = parsePrice(text);
					if (parsed != null) {
						price = parsed.amount();
						priceScope = parsed.scope();
						if (parsed.bulkSize() != null) {
							bulkSize = parsed.bulkSize();
						}
						if (parsed.itemName() != null && itemDisplay == null) {
							itemDisplay = parsed.itemName();
						}
					}
					rawLines.add(text);
				} else if (text.equals(SELLING_ANCHOR)) {
					direction = ShopDirection.SELLING;
					rawLines.add(text);
				} else if (text.equals(BUYING_ANCHOR)) {
					direction = ShopDirection.BUYING;
					rawLines.add(text);
				} else if (text.startsWith(SELL_LIMIT_PREFIX)) {
					maxSellItems = sellLimitOf(text);
					rawLines.add(text);
				} else if (text.startsWith(ALL_PREFIX)) {
					supportsAll = true;
					rawLines.add(text);
				} else if (isEnchantmentHeader(text)) {
					inEnchantments = true;
					rawLines.add(text);
				} else {
					Matcher bulk = BULK_INFO.matcher(text);
					if (bulk.matches()) {
						bulkSize = Integer.valueOf(bulk.group(1));
						Integer bulks = Integer.valueOf(bulk.group(3));
						if (bulk.group(2).equals("buy")) {
							maxBuyBulks = bulks;
						} else {
							maxSellBulks = bulks;
						}
						rawLines.add(text);
						continue;
					}
					Matcher buyLimit = BUY_LIMIT.matcher(text);
					if (buyLimit.matches()) {
						maxBuyItems = Integer.valueOf(buyLimit.group(1));
						rawLines.add(text);
						continue;
					}
					Matcher remaining = REMAINING.matcher(text);
					if (remaining.matches()) {
						int percent = Integer.parseInt(remaining.group(1));
						if (percent <= 100) {
							remainingPercent = percent;
							rawLines.add(text);
						}
					}
				}
			}
		}

		if (direction == null || (owner == null && itemDisplay == null)) {
			return Optional.empty();
		}

		boolean complete = owner != null && itemDisplay != null
				&& price != null && priceScope != null
				&& (priceScope == PriceScope.PER_ITEM || bulkSize != null);
		ItemFingerprint fingerprint = preview != null
				? ItemFingerprints.ofTemplate(preview)
				: ItemFingerprints.fromDisplay(itemDisplay);
		return Optional.of(new ShopResponse(owner, direction, preview, itemDisplay, price, priceScope,
				bulkSize, stockRaw, stockUnit, remainingPercent, maxBuyBulks, maxBuyItems,
				maxSellBulks, maxSellItems, supportsAll, enchantments,
				complete ? ShopResponse.Completeness.COMPLETE : ShopResponse.Completeness.PARTIAL,
				rawLines, receivedAt));
	}

	/** The displayed item text, without the {@code [Item Preview]} marker. */
	private static String displayOf(String line) {
		String display = line.substring(ITEM_PREFIX.length()).trim();
		int marker = display.lastIndexOf(ITEM_PREVIEW_SUFFIX);
		if (marker >= 0) {
			display = (display.substring(0, marker) + display.substring(marker + ITEM_PREVIEW_SUFFIX.length())).trim();
		}
		return display;
	}

	/** {@code You can sell 0.} — the reported sellable quantity. */
	private static @Nullable Integer sellLimitOf(String line) {
		String value = line.substring(SELL_LIMIT_PREFIX.length()).trim();
		if (value.endsWith(".")) {
			value = value.substring(0, value.length() - 1);
		}
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * {@code Price per 7x Block of Diamond - 1G} or
	 * {@code Price per Netherite Ingot - 17G}: the scope, the bulk size
	 * when the line carries one, and the amount. {@code null} when the
	 * line does not decompose — an unparseable price is never guessed.
	 */
	private static @Nullable PriceLine parsePrice(String line) {
		String body = line.substring(PRICE_PREFIX.length());
		int separator = body.lastIndexOf(PRICE_SEPARATOR);
		if (separator < 0) {
			return null;
		}
		String itemPart = body.substring(0, separator).trim();
		BigDecimal amount = parseAmount(body.substring(separator + PRICE_SEPARATOR.length()).trim());
		if (amount == null || itemPart.isEmpty()) {
			return null;
		}
		Matcher bulk = PRICE_BULK_ITEM.matcher(itemPart);
		if (bulk.matches()) {
			try {
				return new PriceLine(amount, PriceScope.PER_BULK, Integer.valueOf(bulk.group(1)), bulk.group(2).trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return new PriceLine(amount, PriceScope.PER_ITEM, null, itemPart);
	}

	/** A plain gold amount ({@code 1G}, {@code 1.5G}, {@code 2,500G}). */
	private static @Nullable BigDecimal parseAmount(String text) {
		String value = text;
		if (value.endsWith("G")) {
			value = value.substring(0, value.length() - 1);
		}
		value = value.replace(",", "");
		try {
			return new BigDecimal(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Walks a message's component tree looking for the shop's
	 * {@code [Item Preview]} hover — a {@code show_item} event, which in
	 * 26.3 carries the full item template. The template is returned as
	 * wire data: identity needs no resolved stack, and resolving one
	 * would require the client's bound components. A response without a
	 * usable hover simply yields no preview.
	 */
	private static @Nullable ItemStackTemplate findPreview(Component component) {
		HoverEvent hover = component.getStyle().getHoverEvent();
		if (hover instanceof HoverEvent.ShowItem showItem) {
			return showItem.item();
		}
		for (Component sibling : component.getSiblings()) {
			ItemStackTemplate found = findPreview(sibling);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** One parsed price line: amount, scope, and the bulk size when present. */
	private record PriceLine(BigDecimal amount, PriceScope scope,
			@Nullable Integer bulkSize, @Nullable String itemName) {
	}
}
