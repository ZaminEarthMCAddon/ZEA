package net.zaminmc.zea.browser;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.zaminmc.zea.market.ItemFingerprints;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rebuilds display stacks from exact fingerprint tokens.
 *
 * <p>The token is the identity, but a tile needs an icon: the token
 * carries everything needed to rebuild one — the item id before the
 * first {@code |}, and the canonically encoded component patch after
 * it, which is exactly what {@link ItemFingerprints} wrote. Parsing the
 * encoded patch back through the codec with the level's registry view
 * reconstructs the actual observed variant: the enchanted pickaxe
 * renders with its glint, the damaged tool with its durability bar.
 * This is the real data round-tripping through its own serialization —
 * never a guess from a display name.
 *
 * <p>A token whose patch cannot decode (an item the client does not
 * know, a registry the level has not loaded) yields a stack without
 * components rather than a fabricated variant — and the renderer marks
 * such tiles, so an approximate icon is never presented as the exact
 * one. Resolved stacks are cached; the cache is bounded and thread-safe
 * (render state extraction may run on the render thread while detail
 * views build on the client thread).
 */
public final class VariantStacks {
	/** The resolved-stack cache's bound; variants exceed this only in absurd markets. */
	private static final int MAX_CACHE = 512;

	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

	private VariantStacks() {
	}

	/** One resolved stack and whether the reconstruction was exact. */
	public record Resolved(ItemStack stack, boolean exact) {
	}

	/**
	 * The display stack for a fingerprint token, resolved from its
	 * encoded form. The result is a shared cached stack — callers must
	 * not mutate it; the renderer only draws it.
	 */
	public static Resolved of(String token) {
		Cached cached = CACHE.get(token);
		if (cached == null) {
			cached = new Cached(resolve(token));
			if (CACHE.size() >= MAX_CACHE) {
				CACHE.clear();
			}
			CACHE.put(token, cached);
		}
		return cached.value;
	}

	private static Resolved resolve(String token) {
		int split = token.indexOf('|');
		if (split <= 0) {
			return new Resolved(ItemStack.EMPTY, false);
		}
		Item item = itemOf(token.substring(0, split));
		if (item == null) {
			return new Resolved(ItemStack.EMPTY, false);
		}
		if (split + 1 >= token.length() || token.regionMatches(split + 1, "{}", 0, 2)) {
			// No components: the plain item, exactly.
			return new Resolved(new ItemStack(item), true);
		}
		DataComponentPatch patch = patchOf(token.substring(split + 1));
		if (patch == null) {
			// The patch could not be decoded against the loaded
			// registries: show the bare item and say so.
			return new Resolved(new ItemStack(item), false);
		}
		return new Resolved(new ItemStackTemplate(item, patch).create(), true);
	}

	private static @Nullable Item itemOf(String id) {
		try {
			return BuiltInRegistries.ITEM.get(Identifier.parse(id))
					.map(net.minecraft.core.Holder::value)
					.orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Decodes the encoded component patch with the level's registry
	 * view — the same view the encoding used, so the round trip is
	 * faithful. {@code null} when the form or the registries fail.
	 */
	private static @Nullable DataComponentPatch patchOf(String encoded) {
		RegistryAccess access = ItemFingerprints.levelAccess().orElse(null);
		if (access == null) {
			return null;
		}
		try {
			var json = JsonParser.parseString(encoded);
			return DataComponentPatch.CODEC
					.parse(access.createSerializationContext(JsonOps.INSTANCE), json)
					.result()
					.orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	private record Cached(Resolved value) {
	}
}
