package net.zaminmc.zea.map.config;

import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import net.zaminmc.zea.config.ZeaConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structure guarantees for the Map category, mirroring the house
 * structure tests: every public field is an option or a subcategory,
 * options pair with editors, the expected subcategories exist, and the
 * defaults encode the map policy (on, EarthMC-gated, north-up).
 */
class MapConfigStructureTest {

	@Test
	void everyPublicFieldIsAnOptionOrSubcategory() {
		for (Class<?> clazz : new Class<?>[] {MapConfig.class, MapConfig.Minimap.class,
				MapConfig.WorldMap.class, MapConfig.SharedDisplay.class}) {
			for (Field field : clazz.getDeclaredFields()) {
				int mods = field.getModifiers();
				if (Modifier.isStatic(mods) || !Modifier.isPublic(mods)) {
					continue;
				}
				assertTrue(field.isAnnotationPresent(ConfigOption.class)
								|| field.isAnnotationPresent(Category.class),
						clazz.getSimpleName() + "." + field.getName()
								+ " needs @ConfigOption or @Category (subcategory)");
			}
		}
	}

	@Test
	void everyOptionHasAMatchingEditor() {
		for (Class<?> clazz : new Class<?>[] {MapConfig.class, MapConfig.Minimap.class,
				MapConfig.WorldMap.class, MapConfig.SharedDisplay.class}) {
			for (Field field : clazz.getDeclaredFields()) {
				if (!field.isAnnotationPresent(ConfigOption.class)) {
					continue;
				}
				boolean editor = field.isAnnotationPresent(ConfigEditorBoolean.class)
						|| field.isAnnotationPresent(ConfigEditorDropdown.class)
						|| field.isAnnotationPresent(ConfigEditorSlider.class)
						|| field.isAnnotationPresent(ConfigEditorText.class);
				assertTrue(editor, clazz.getSimpleName() + "." + field.getName() + " has no editor");
			}
		}
	}

	@Test
	void mapCategoryNestsItsThreeSubcategories() {
		List<String> subcategories = new ArrayList<>();
		for (Field field : MapConfig.class.getDeclaredFields()) {
			if (field.isAnnotationPresent(Category.class)) {
				subcategories.add(field.getName());
			}
		}
		assertEquals(List.of("minimap", "worldMap", "sharedDisplay"), subcategories,
				"the Map category holds exactly the three surface subcategories");
	}

	@Test
	void defaultsEncodeTheMapPolicy() {
		ZeaConfig config = new ZeaConfig();
		MapConfig map = config.map;
		assertTrue(map.enabled, "map on by default");
		assertTrue(map.worldMapEnabled);
		assertTrue(map.minimapEnabled);
		assertTrue(map.earthMcOnly, "EarthMC-only is the safe default");
		assertEquals(MapConfig.WorldMode.AUTO, map.defaultWorld, "auto follows the player's world");
		assertEquals("", map.apiKey, "no key until the user provides one");
		assertTrue(map.minimap.rotateWithPlayer == false, "north-up is the geographic default");
		assertTrue(map.minimap.centerPlayer);
		assertEquals(MapConfig.Minimap.DEFAULT_SIZE, map.minimap.size);
		assertTrue(map.sharedDisplay.imageryEnabled);
	}

	@Test
	void sliderBoundsContainTheDefaults() {
		MapConfig.Minimap minimap = new MapConfig.Minimap();
		assertTrue(minimap.size >= MapConfig.Minimap.MIN_SIZE
				&& minimap.size <= MapConfig.Minimap.MAX_SIZE);
		MapConfig.WorldMap worldMap = new MapConfig.WorldMap();
		assertTrue(worldMap.defaultBlocksAcross >= MapConfig.WorldMap.MIN_ZOOM
				&& worldMap.defaultBlocksAcross <= MapConfig.WorldMap.MAX_ZOOM);
	}

	@Test
	void dropdownEnumsRenderReadableLabels() {
		assertFalse(MapConfig.WorldMode.AUTO.toString().isBlank());
		assertFalse(MapConfig.MinimapShape.SQUARE.toString().isBlank());
		assertFalse(MapConfig.PlayerIconStyle.HEAD_BORDER.toString().isBlank());
		assertEquals("Circle", MapConfig.MinimapShape.CIRCLE.toString());
	}
}
