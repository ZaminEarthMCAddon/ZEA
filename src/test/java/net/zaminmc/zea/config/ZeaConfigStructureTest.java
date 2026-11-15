package net.zaminmc.zea.config;

import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the contract the MoulConfig processor reads via reflection. The
 * processor itself needs a Minecraft runtime, so these tests verify the
 * structure it consumes instead: categories resolvable (top level and
 * nested — a {@code @Category} field inside a category class is the
 * subcategory mechanism), every option public and non-static, every
 * option paired with an editor whose type matches the field.
 */
class ZeaConfigStructureTest {
        private static final List<Class<? extends Annotation>> EDITOR_ANNOTATIONS = List.of(
                        ConfigEditorBoolean.class, ConfigEditorSlider.class, ConfigEditorDropdown.class,
                        ConfigEditorText.class, ConfigEditorButton.class);

        @Test
        void extendsMoulConfigBase() {
                assertTrue(Config.class.isAssignableFrom(ZeaConfig.class));
        }

        @Test
        void categoryFieldsAreAnnotatedAndResolvable() {
                for (Field field : ZeaConfig.class.getDeclaredFields()) {
                        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                                continue;
                        }
                        if (field.getType().getDeclaringClass() == null) {
                                continue; // framework-owned fields
                        }
                        assertTrue(field.isAnnotationPresent(Category.class),
                                        field.getName() + " is a category holder and needs @Category");
                        assertTrue(!field.getAnnotation(Category.class).name().isBlank(),
                                        field.getName() + " needs a category name");
                }
        }

        @Test
        void chatCategoryFieldsAreOptionsOrSubcategories() {
                // The dev.26 layout: everything chat-related lives in the Chat
                // category — its own options plus the Tabs / Chat interface /
                // Notifications / Mentions / Chat history subcategories, each
                // declared as a @Category field (the MoulConfig nesting
                // mechanism, verified against the bundled 4.8.0 processor).
                for (Field field : ZeaConfig.Chat.class.getDeclaredFields()) {
                        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                                continue;
                        }
                        boolean option = field.isAnnotationPresent(ConfigOption.class);
                        boolean subcategory = field.isAnnotationPresent(Category.class);
                        assertTrue(option ^ subcategory,
                                        "Chat." + field.getName() + " needs exactly one of"
                                                        + " @ConfigOption or @Category (subcategory)");
                        if (subcategory) {
                                assertTrue(!field.getAnnotation(Category.class).name().isBlank(),
                                                "Chat." + field.getName() + " needs a subcategory name");
                        }
                }
                assertTrue(categoriesHeldBy(ZeaConfig.Chat.class).size() >= 5,
                                "the chat-related subcategories must be nested under Chat");
        }

        @Test
        void chatSubcategoriesAreNotTopLevelAnymore() {
                // The dev.26 move: Tabs, Chat interface, Notifications,
                // Mentions and Chat history render under Chat, not beside it.
                List<Class<?>> topLevel = new ArrayList<>();
                for (Field field : ZeaConfig.class.getDeclaredFields()) {
                        if (field.isAnnotationPresent(Category.class)) {
                                topLevel.add(field.getType());
                        }
                }
                assertTrue(topLevel.contains(ZeaConfig.Chat.class), "Chat stays a top-level category");
                assertTrue(topLevel.contains(ZeaConfig.InventoryButtons.class),
                                "Inventory buttons is not chat-related and stays top-level");
                assertFalse(topLevel.contains(ZeaConfig.Tabs.class)
                                || topLevel.contains(ZeaConfig.ChatInterface.class)
                                || topLevel.contains(ZeaConfig.Notifications.class)
                                || topLevel.contains(ZeaConfig.Mentions.class)
                                || topLevel.contains(ZeaConfig.History.class),
                                "chat-related categories must nest under Chat, not stand beside it");
        }

        @Test
        void rarityCategoryNestsItsVisualSubcategories() {
                // The rarity spec's layout: one top-level Rarity category
                // holding the master switch plus the Appearance and
                // Enchantment display subcategories — the same MoulConfig
                // nesting mechanism Chat uses.
                List<Class<?>> topLevel = new ArrayList<>();
                for (Field field : ZeaConfig.class.getDeclaredFields()) {
                        if (field.isAnnotationPresent(Category.class)) {
                                topLevel.add(field.getType());
                        }
                }
                assertTrue(topLevel.contains(ZeaConfig.Rarity.class), "Rarity is a top-level category");
                assertTrue(topLevel.contains(ZeaConfig.Miscellaneous.class),
                                "Miscellaneous is a top-level category");
                assertFalse(topLevel.contains(ZeaConfig.Appearance.class)
                                || topLevel.contains(ZeaConfig.EnchantmentDisplay.class),
                                "rarity visual settings nest under Rarity");
                List<Class<?>> heldByRarity = categoriesHeldBy(ZeaConfig.Rarity.class);
                assertTrue(heldByRarity.contains(ZeaConfig.Appearance.class),
                                "Appearance nests under Rarity");
                assertTrue(heldByRarity.contains(ZeaConfig.EnchantmentDisplay.class),
                                "Enchantment display nests under Rarity");
        }

        @Test
        void inventoryUtilitiesNestTheirSubcategories() {
                // The inventory utilities layout: one top-level category
                // holding the master switch, the editor launcher and the
                // Search bar, Slot binding and Slot locking subcategories.
                // (Button placement left with the region model — free
                // placement has nothing to toggle per edge.)
                List<Class<?>> held = categoriesHeldBy(ZeaConfig.InventoryButtons.class);
                assertTrue(held.contains(ZeaConfig.SearchBar.class),
                                "Search bar nests under the inventory category");
                assertTrue(held.contains(ZeaConfig.SlotBinding.class),
                                "Slot binding nests under the inventory category");
                assertTrue(held.contains(ZeaConfig.SlotLocking.class),
                                "Slot locking nests under the inventory category");
                List<Class<?>> topLevel = new ArrayList<>();
                for (Field field : ZeaConfig.class.getDeclaredFields()) {
                        if (field.isAnnotationPresent(Category.class)) {
                                topLevel.add(field.getType());
                        }
                }
                assertFalse(topLevel.contains(ZeaConfig.SearchBar.class)
                                || topLevel.contains(ZeaConfig.SlotBinding.class)
                                || topLevel.contains(ZeaConfig.SlotLocking.class),
                                "inventory utility settings nest, never stand beside the category");
        }

        @Test
        void miscellaneousHoldsScrollableTooltips() {
                boolean found = false;
                for (Field field : ZeaConfig.Miscellaneous.class.getDeclaredFields()) {
                        if (field.getName().equals("scrollableTooltips")) {
                                found = true;
                                assertTrue(field.isAnnotationPresent(ConfigOption.class));
                                assertTrue(field.isAnnotationPresent(ConfigEditorBoolean.class));
                        }
                }
                assertTrue(found, "the scrollable-tooltips option lives under Miscellaneous");
        }

        @Test
        void everyOptionIsPublicNonStaticAndPairedWithAMatchingEditor() {
                for (Class<?> category : categoryTypes()) {
                        for (Field field : category.getDeclaredFields()) {
                                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                                        continue;
                                }
                                if (field.isAnnotationPresent(Category.class)) {
                                        continue; // subcategories carry @Category, not an editor
                                }
                                assertTrue(field.isAnnotationPresent(ConfigOption.class),
                                                category.getSimpleName() + "." + field.getName() + " needs @ConfigOption");
                                assertTrue(Modifier.isPublic(field.getModifiers()),
                                                category.getSimpleName() + "." + field.getName() + " must be public for the editor");
                                List<Class<? extends Annotation>> editors = EDITOR_ANNOTATIONS.stream()
                                                .filter(a -> field.isAnnotationPresent(a)).toList();
                                assertTrue(!editors.isEmpty(),
                                                category.getSimpleName() + "." + field.getName() + " has no editor annotation");
                                Assertions.assertEquals(1, editors.size(),
                                                category.getSimpleName() + "." + field.getName()
                                                                + " has more than one editor annotation");
                                if (editors.contains(ConfigEditorSlider.class)) {
                                        assertTrue(field.getType() == int.class || field.getType() == float.class
                                                        || field.getType() == double.class || field.getType() == long.class,
                                                        category.getSimpleName() + "." + field.getName() + " is a slider but not numeric");
                                }
                                if (editors.contains(ConfigEditorBoolean.class)) {
                                        assertTrue(field.getType() == boolean.class,
                                                        category.getSimpleName() + "." + field.getName() + " is a toggle but not boolean");
                                }
                                if (editors.contains(ConfigEditorDropdown.class)) {
                                        assertTrue(field.getType() == String.class || field.getType().isEnum(),
                                                        category.getSimpleName() + "." + field.getName()
                                                                        + " is a dropdown but neither a string nor an enum");
                                }
                                if (editors.contains(ConfigEditorText.class)) {
                                        assertTrue(field.getType() == String.class,
                                                        category.getSimpleName() + "." + field.getName()
                                                                        + " is a text field but not a string");
                                }
                                if (editors.contains(ConfigEditorButton.class)) {
                                        assertTrue(field.getType() == boolean.class,
                                                        category.getSimpleName() + "." + field.getName()
                                                                        + " is a launcher button but not boolean (NEU convention)");
                                }
                        }
                }
        }

        @Test
        void sliderRangeCoversTheDocumentedBounds() {
                ConfigEditorSlider slider;
                try {
                        Field field = ZeaConfig.Chat.class.getDeclaredField("dispatchIntervalMillis");
                        slider = field.getAnnotation(ConfigEditorSlider.class);
                } catch (NoSuchFieldException e) {
                        throw new AssertionError("dispatchIntervalMillis disappeared", e);
                }
                assertTrue(slider.minValue() <= ZeaConfig.MIN_DISPATCH_INTERVAL_MILLIS
                                && slider.maxValue() >= ZeaConfig.MAX_DISPATCH_INTERVAL_MILLIS,
                                "slider bounds must match the clamped accessor range");
        }

        private static List<Class<?>> categoriesHeldBy(Class<?> holder) {
                List<Class<?>> categories = new ArrayList<>();
                for (Field field : holder.getDeclaredFields()) {
                        if (!field.isSynthetic() && field.isAnnotationPresent(Category.class)) {
                                categories.add(field.getType());
                        }
                }
                return categories;
        }

        private static List<Class<?>> categoryTypes() {
                List<Class<?>> categories = new ArrayList<>();
                collectCategories(ZeaConfig.class, categories);
                assertTrue(!categories.isEmpty(), "ZeaConfig must expose at least one category");
                return categories;
        }

        /** Categories held by {@code @Category} fields on {@code holder}, recursively. */
        private static void collectCategories(Class<?> holder, List<Class<?>> into) {
                for (Field field : holder.getDeclaredFields()) {
                        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                                continue;
                        }
                        if (!field.isAnnotationPresent(Category.class)) {
                                continue;
                        }
                        Class<?> type = field.getType();
                        if (type == holder || into.contains(type)) {
                                continue;
                        }
                        into.add(type);
                        collectCategories(type, into);
                }
        }
}
