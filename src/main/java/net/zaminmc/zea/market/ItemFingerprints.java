package net.zaminmc.zea.market;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.zaminmc.zea.market.model.ItemFingerprint;
import org.jspecify.annotations.Nullable;
import com.mojang.serialization.JsonOps;

import java.util.Locale;
import java.util.Optional;

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
 * <p>The codec runs against the client level's registry view whenever
 * one is open, because the components that change an item variant —
 * enchantments above all — live in data-driven registries that the
 * built-in registry set alone cannot serialize. Without a level (or if
 * the patch still fails to encode) the token degrades to
 * {@linkplain ItemFingerprint#unknown() unknown} rather than guessing:
 * a token that silently dropped an enchantment would match a different
 * product, which is the one failure the fingerprint must never have.
 *
 * <p>A display-only token is a normalized material slug derived from
 * display text. It can never exact-match (the source text can be
 * truncated or cosmetic) and exists so sign-only observations can still
 * be listed and grouped by name.
 */
public final class ItemFingerprints {
	/** Registry view for the built-in registries; the no-level fallback. */
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
				.flatMap(key -> encodePatch(template.components())
						.map(patch -> new ItemFingerprint(key.identifier().toString() + "|" + patch,
								ItemFingerprint.Fidelity.EXACT)))
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

	/**
	 * The canonical JSON form of a component patch, or {@code empty}
	 * when the patch cannot be serialized against the available
	 * registries — an enchantment whose registry is not loaded, say.
	 * Identity callers treat that as no exact identity, never as a
	 * reduced one.
	 */
	private static Optional<String> encodePatch(DataComponentPatch patch) {
		RegistryAccess access = levelAccess().orElse(FROZEN_ACCESS);
		return DataComponentPatch.CODEC
				.encodeStart(access.createSerializationContext(JsonOps.INSTANCE), patch)
				.result()
				.map(Object::toString);
	}

	/**
	 * The level's registry view when the client is in a world — the only
	 * view that carries the data-driven registries (enchantments) item
	 * components reference. Read on the client thread, where every
	 * fingerprint is computed. No client (headless test JVM) is the same
	 * as no level: both fall back to the built-in view.
	 */
	private static Optional<RegistryAccess> levelAccess() {
		try {
			Minecraft client = Minecraft.getInstance();
			ClientLevel level = client == null ? null : client.level;
			return level == null ? Optional.empty() : Optional.of(level.registryAccess());
		} catch (Throwable notOnAClientThread) {
			// Headless test JVM: loading the client class cannot give a
			// level, and the built-in fallback stays honest.
			return Optional.empty();
		}
	}

	private static String slugify(String display) {
		return display.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}
}
