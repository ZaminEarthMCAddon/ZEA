package net.zaminmc.zea.config;

import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code /zea} menu's preprocessing — the dev.24 live defect was that
 * the menu showed nothing: the editor was built over a processor that had
 * no editor factories registered and had never been driven, so it held
 * zero categories and zero options. These tests pin both halves of the
 * recipe ({@code withDefaults} and the driver) at the processor level;
 * the editor and screen themselves need a Minecraft runtime to render.
 */
class ConfigScreenTest {
        @Test
        void processingFinalizesTheProcessor() {
                MoulConfigProcessor<ZeaConfig> processor = ConfigScreen.process(new ZeaConfig());

                assertTrue(processor.isFinalized(),
                                "the driver never ran — the editor would render an empty config");
        }

        @Test
        void processingExposesEveryConfigCategory() {
                MoulConfigProcessor<ZeaConfig> processor = ConfigScreen.process(new ZeaConfig());

                Map<String, ? extends ProcessedCategory> categories = processor.getAllCategories();

                assertFalse(categories.isEmpty(),
                                "no categories survived processing — the /zea screen would be blank");
                assertTrue(categories.size() >= 7,
                                "expected at least the seven config categories, found " + categories.keySet());
        }

        @Test
        void everyCategoryCarriesItsOptions() {
                MoulConfigProcessor<ZeaConfig> processor = ConfigScreen.process(new ZeaConfig());

                for (ProcessedCategory category : processor.getAllCategories().values()) {
                        assertFalse(category.getOptions().isEmpty(),
                                        category.getIdentifier() + " lost all its options —"
                                                        + " no editor factory matched its fields");
                }
        }

        @Test
        void processingNestsTheChatSubcategoriesUnderChat() {
                // The dev.26 layout: chat-related settings are subcategories of
                // Chat, not categories of their own. The processor must record
                // that parent link — the editor renders subcategories indented
                // under their parent (and folds them into search).
                Map<String, ? extends ProcessedCategory> categories =
                                ConfigScreen.process(new ZeaConfig()).getAllCategories();
                ProcessedCategory chat = byDisplayName(categories, "Chat");
                for (String name : List.of("Tabs", "Chat interface", "Notifications", "Mentions", "Chat history")) {
                        ProcessedCategory sub = byDisplayName(categories, name);
                        assertEquals(chat.getIdentifier(), sub.getParentCategoryId(),
                                        name + " must nest under Chat");
                }
        }

        @Test
        void inventoryButtonsStaysTopLevel() {
                Map<String, ? extends ProcessedCategory> categories =
                                ConfigScreen.process(new ZeaConfig()).getAllCategories();
                assertNull(byDisplayName(categories, "Inventory utilities").getParentCategoryId(),
                                "Inventory utilities is not chat-related and must not nest under Chat");
        }

        @Test
        void processingNestsTheRaritySubcategoriesUnderRarity() {
                // The rarity spec's layout: Appearance and Enchantment
                // display render under Rarity, and Miscellaneous stays
                // top-level for the small utilities.
                Map<String, ? extends ProcessedCategory> categories =
                                ConfigScreen.process(new ZeaConfig()).getAllCategories();
                ProcessedCategory rarity = byDisplayName(categories, "Rarity");
                for (String name : List.of("Appearance", "Enchantment display")) {
                        ProcessedCategory sub = byDisplayName(categories, name);
                        assertEquals(rarity.getIdentifier(), sub.getParentCategoryId(),
                                        name + " must nest under Rarity");
                }
                assertNull(byDisplayName(categories, "Miscellaneous").getParentCategoryId(),
                                "Miscellaneous is top-level");
        }

        private static ProcessedCategory byDisplayName(Map<String, ? extends ProcessedCategory> categories,
                        String name) {
                return categories.values().stream()
                                .filter(category -> category.getDisplayName().getText().equals(name))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("category missing after processing: " + name));
        }
}
