package net.zaminmc.zea.market;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.zaminmc.zea.market.model.ItemFingerprint;
import org.jspecify.annotations.Nullable;
import com.mojang.serialization.JsonOps;

import java.util.Locale;

/**
 * Builds the market's item identity tokens from the evidence the server
 * provides.
 *
 * <p>An exact token covers the item id plus every component the
 * {@code [Item Preview]} hover carried, encoded through the component
 * patch codec into a canonical JSON form — the same stack always yields
 * the same token, on every client and after every restart. The stack
 * count is normalized away: a pile of twelve pickaxes is the same
 * product as one pickaxe.
 *
 * <p>A display-only token is a normalized material slug derived from
 * display text. It can never exact-match (the source text can be
 * truncated or cosmetic) and exists so sign-only observations can still
 * be listed and grouped by name.
 */
public final class ItemFingerprints {
	/** Registry view used to resolve component codecs; cheap to build. */
	private static final RegistryAccess FROZEN_ACCESS =
			RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

	private ItemFingerprints() {
	}

	/** The exact identity of the item the server previewed. */
	public static ItemFingerprint ofTemplate(ItemStackTemplate template) {
		// Resolving the holder through the registry would instantiate the
		// item's default components; the key alone is the canonical id and
		// never needs a bound client. An unregistered holder (should not
		// happen for a live server's hover) degrades to an unknown
		// fingerprint instead of a token that would never match anything.
		return template.item().unwrapKey()
				.map(key -> new ItemFingerprint(key.identifier().toString() + "|"
						+ encodePatch(template.components()), ItemFingerprint.Fidelity.EXACT))
				.orElseGet(ItemFingerprint::unknown);
	}

	/** The exact identity of a stack; the count is normalized away. */
	public static ItemFingerprint ofStack(ItemStack stack) {
		return ofTemplate(ItemStackTemplate.fromStack(stack.copyWithCount(1)));
	}

	/**
	 * The best display-text identity available. An empty or blank name
	 * yields an unknown fingerprint — the market treats the item as
	 * unmatchable instead of guessing.
	 */
	public static ItemFingerprint fromDisplay(@Nullable String display) {
		if (display == null || display.isBlank()) {
			return ItemFingerprint.unknown();
		}
		return new ItemFingerprint(slugify(display), ItemFingerprint.Fidelity.DISPLAY_ONLY);
	}

	private static String encodePatch(DataComponentPatch patch) {
		return DataComponentPatch.CODEC.encodeStart(FROZEN_ACCESS.createSerializationContext(JsonOps.INSTANCE), patch)
				.result()
				.map(Object::toString)
				.orElse("unencodable");
	}

	private static String slugify(String display) {
		return display.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}
}
