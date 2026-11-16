package net.zaminmc.zea.chat.feeds;

import net.zaminmc.zea.chat.model.MessageCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Global tab's per-type opt-outs: every kind of message can be
 * hidden from the aggregate individually (the dev.36 live request), with
 * exactly two exceptions pinned here — Global's own channel and the
 * unclassified bucket, which the constructor refuses to hide no matter
 * what the config layer hands it.
 */
class GlobalVisibilityTest {
        @Test
        void nothingHiddenShowsEverything() {
                GlobalVisibility visibility = new GlobalVisibility(Set.of());

                assertTrue(visibility.showsEverything());
                for (MessageCategory category : MessageCategory.values()) {
                        assertFalse(visibility.hides(category), category.name());
                }
        }

        @Test
        void nullReadsAsHideNothing() {
                GlobalVisibility visibility = new GlobalVisibility(null);

                assertTrue(visibility.showsEverything());
        }

        @Test
        void theToggleableKindsAreEveryMessageExceptGlobalAndUnclassified() {
                assertEquals(Set.of(
                                MessageCategory.LOCAL, MessageCategory.TRADE,
                                MessageCategory.NATION, MessageCategory.TOWN,
                                MessageCategory.PRIVATE_MESSAGE, MessageCategory.DISCORD,
                                MessageCategory.VOTE, MessageCategory.CRATE_WIN,
                                MessageCategory.DEATH, MessageCategory.SYSTEM), GlobalVisibility.TOGGLEABLE);
        }

        @ParameterizedTest
        @EnumSource(value = MessageCategory.class, names = {"GLOBAL", "UNCLASSIFIED"})
        void globalAndUnclassifiedAreNeverHidden(MessageCategory category) {
                GlobalVisibility visibility = new GlobalVisibility(EnumSet.allOf(MessageCategory.class));

                assertFalse(visibility.hides(category), category + " must never be hidden on Global");
        }

        @ParameterizedTest
        @EnumSource(value = MessageCategory.class, names = {
                                        "GLOBAL", "UNCLASSIFIED",
                                        // The advertisement kinds have their own dedicated filter
                                        // toggles and are pinned separately below.
                                        "DISCORD_RECRUITMENT", "CRATE_LUCKY", "PROMO_BANNER", "ITEM_SHOWCASE"},
                                        mode = EnumSource.Mode.EXCLUDE)
        void everyOtherKindHidesWhenAskedTo(MessageCategory category) {
                GlobalVisibility visibility = new GlobalVisibility(Set.of(category));

                assertFalse(visibility.showsEverything());
                assertTrue(visibility.hides(category));
                assertEquals(Set.of(category), visibility.hidden());
        }

        @ParameterizedTest
        @EnumSource(value = MessageCategory.class, names = {
                                        "DISCORD_RECRUITMENT", "CRATE_LUCKY", "PROMO_BANNER", "ITEM_SHOWCASE"})
        void advertisementKindsAreGovernedByTheirOwnFilterToggles(MessageCategory category) {
                // The four advertisement-shaped categories are hidden from the
                // Global aggregate through their dedicated filter toggles
                // (ZeaConfig.hiddenChatCategories), not through the per-type
                // GlobalVisibility switches — one control per kind, never two.
                assertFalse(new GlobalVisibility(Set.of(category)).hides(category));
                assertFalse(new GlobalVisibility(EnumSet.allOf(MessageCategory.class)).hides(category));
        }
}
