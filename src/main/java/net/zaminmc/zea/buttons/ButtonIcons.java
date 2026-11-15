package net.zaminmc.zea.buttons;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves button icons to renderable things: either a registered item
 * stack or one of ZEA's own 16×16 custom textures — the set of generic
 * glyphs (settings cog, search glass, check marks, ...) the reference
 * port ships, adapted as our own PNGs under the same names. Unknown or
 * malformed ids fall back to the barrier item — the vanilla convention
 * for missing content — instead of hiding the button, so a typo is
 * visible (and flagged in the tooltip) rather than silent.
 *
 * <p>Stacks are cached per id: item icons are needed every frame while a
 * container screen is open, and button sets are small. Registry lookups
 * happen on the client thread during rendering, matching vanilla's own
 * GUI item access.
 */
public final class ButtonIcons {
	/** Rendered when the configured icon id does not resolve to an item. */
	private static final String FALLBACK_ICON = "minecraft:barrier";

	/** The editor's quick-pick icons: common, recognizable, all vanilla. */
	public static final String[] QUICK_PICK = {
			"minecraft:compass",
			"minecraft:chest",
			"minecraft:ender_chest",
			"minecraft:book",
			"minecraft:emerald",
			"minecraft:clock",
			"minecraft:map",
			"minecraft:paper",
			"minecraft:gold_ingot",
			"minecraft:nether_star",
	};

	/**
	 * ZEA's custom icon textures, keyed by the id a button stores. The
	 * names follow the reference port's vocabulary so layouts shared
	 * between the two resolve; the pixels are our own.
	 */
	public static final Map<String, Identifier> CUSTOM_TEXTURES = customTextures();

	private static Map<String, Identifier> customTextures() {
		Map<String, Identifier> textures = new LinkedHashMap<>();
		textures.put("custom:cross", id("cross"));
		textures.put("custom:green_check", id("green_check"));
		textures.put("custom:white_check", id("white_check"));
		textures.put("custom:question", id("question"));
		textures.put("custom:settings", id("settings"));
		textures.put("custom:search", id("search"));
		textures.put("custom:armor", id("armor"));
		textures.put("custom:pet", id("pet"));
		textures.put("custom:recipe", id("recipe"));
		textures.put("custom:baubles", id("baubles"));
		return java.util.Collections.unmodifiableMap(textures);
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath("zea", "textures/icons/" + name + ".png");
	}

	/** Cache cap; button sets are tiny, this only guards pathological input. */
	private static final int MAX_CACHE_SIZE = 64;

	private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

	private ButtonIcons() {
	}

	/** The custom texture for an icon id, or {@code null} when it is not one. */
	public static Identifier customTextureFor(String iconId) {
		return iconId == null ? null : CUSTOM_TEXTURES.get(iconId);
	}

	/** Whether an icon id names one of the custom textures. */
	public static boolean isCustomTexture(String iconId) {
		return customTextureFor(iconId) != null;
	}

	/** The stack to draw for an icon id, falling back when it cannot resolve. */
	public static ItemStack iconFor(String iconId) {
		String id = iconId == null || isCustomTexture(iconId) ? FALLBACK_ICON : iconId;
		ItemStack cached = CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		ItemStack stack = resolve(id);
		if (CACHE.size() >= MAX_CACHE_SIZE) {
			CACHE.clear();
		}
		CACHE.put(id, stack);
		return stack;
	}

	/** Whether an icon id resolves to a registered item or a custom texture. */
	public static boolean isKnown(String iconId) {
		return isCustomTexture(iconId) || identifierResolves(iconId);
	}

	private static boolean identifierResolves(String iconId) {
		Identifier id = Identifier.tryParse(iconId);
		return id != null && BuiltInRegistries.ITEM.containsKey(id);
	}

	private static ItemStack resolve(String id) {
		Identifier parsed = Identifier.tryParse(id);
		if (parsed != null) {
			return BuiltInRegistries.ITEM.getOptional(parsed)
					.map(ItemStack::new)
					.orElseGet(() -> new ItemStack(Items.BARRIER));
		}
		return new ItemStack(Items.BARRIER);
	}
}
