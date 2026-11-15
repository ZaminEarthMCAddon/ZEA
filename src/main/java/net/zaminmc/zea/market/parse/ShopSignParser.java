package net.zaminmc.zea.market.parse;

import net.zaminmc.zea.util.LegacyText;
import net.zaminmc.zea.market.model.ShopDirection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads a shop sign's four text lines — the preliminary information a
 * player sees before any server response.
 *
 * <p>A sign line is never more than advertising: the item name can be
 * truncated, the price can be stale, and line 2 carries only the
 * direction and a quantity. Everything parsed here is recorded as
 * {@code SIGN_ONLY} evidence and never ranks against server-verified
 * observations; the parse exists so an inspection whose server response
 * never arrived still identifies the shop for discovery.
 *
 * <p>Anchored shapes (observed):
 * <pre>
 *     &amp;aAbasing        ← owner, legacy color codes
 *     Selling 36       ← direction + sign quantity
 *     Diamond Pickaxe  ← displayed item name
 *     2G each          ← advertised price
 * </pre>
 */
public final class ShopSignParser {
	private static final String SELLING_PREFIX = "Selling ";
	private static final String BUYING_PREFIX = "Buying ";

	private ShopSignParser() {
	}

	/**
	 * Whether these lines look like a shop sign at all — a sign whose
	 * second line opens with {@code Selling } or {@code Buying }.
	 */
	public static boolean isShopSign(List<String> lines) {
		if (lines.size() < 2) {
			return false;
		}
		String direction = stripCodes(lines.get(1)).trim();
		return direction.startsWith(SELLING_PREFIX) || direction.startsWith(BUYING_PREFIX);
	}

	/**
	 * The advertised direction, sign quantity, item display name and raw
	 * price text, or {@code empty} when the lines are not a shop sign.
	 * Nothing is inferred beyond what the lines say: the quantity is
	 * kept verbatim (it is a sign quantity, not server stock) and the
	 * price is kept as raw text (its unit is ambiguous before a server
	 * response).
	 */
	public static Optional<SignAdvert> parse(List<String> lines) {
		if (!isShopSign(lines)) {
			return Optional.empty();
		}
		String directionLine = stripCodes(lines.get(1)).trim();
		ShopDirection direction;
		String quantity;
		if (directionLine.startsWith(SELLING_PREFIX)) {
			direction = ShopDirection.SELLING;
			quantity = directionLine.substring(SELLING_PREFIX.length()).trim();
		} else {
			direction = ShopDirection.BUYING;
			quantity = directionLine.substring(BUYING_PREFIX.length()).trim();
		}
		String owner = lines.size() > 0 ? stripCodes(lines.get(0)).trim() : null;
		String itemDisplay = lines.size() > 2 ? stripCodes(lines.get(2)).trim() : null;
		String priceRaw = lines.size() > 3 ? stripCodes(lines.get(3)).trim() : null;
		return Optional.of(new SignAdvert(direction, quantity.isEmpty() ? null : quantity,
				itemDisplay == null || itemDisplay.isEmpty() ? null : itemDisplay,
				priceRaw == null || priceRaw.isEmpty() ? null : priceRaw,
				owner == null || owner.isEmpty() ? null : owner));
	}

	/** Flattens a legacy-coded line ({@code &a}/{@code §a}) to plain text. */
	private static String stripCodes(String line) {
		return LegacyText.parse(line).getString();
	}

	/**
	 * What one shop sign advertises. {@code quantity} is the sign's own
	 * figure ({@code Selling 36}) — never interpreted as server stock;
	 * {@code priceRaw} stays exactly as written ({@code 2G each}).
	 */
	public record SignAdvert(ShopDirection direction, @Nullable String quantity,
			@Nullable String itemDisplay, @Nullable String priceRaw, @Nullable String owner) {

		public SignAdvert {
			if (direction == null) {
				throw new NullPointerException("direction must not be null");
			}
		}

		/** The sign quantity, or 0 when absent — never a stock claim. */
		public int quantityOrZero() {
			if (quantity == null) {
				return 0;
			}
			try {
				return Integer.parseInt(quantity.toLowerCase(Locale.ROOT));
			} catch (NumberFormatException e) {
				return 0;
			}
		}
	}
}
