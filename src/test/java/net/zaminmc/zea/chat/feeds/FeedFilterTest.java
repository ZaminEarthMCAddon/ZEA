package net.zaminmc.zea.chat.feeds;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.zaminmc.zea.chat.classify.ClassificationCache;
import net.zaminmc.zea.chat.classify.ClassificationPipeline;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.ClassifiedMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.model.MessagePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedFilterTest {
        private ClassifiedMessage classified(MessageCategory category) {
                CapturedMessage captured = CapturedMessage.of(Component.literal("sample"), Instant.EPOCH);
                return new ClassifiedMessage(captured, category, MessagePayload.None.INSTANCE);
        }

        @Test
        void globalAggregatesEveryCategory() {
                for (MessageCategory category : MessageCategory.values()) {
                        assertTrue(Feeds.GLOBAL.shows(classified(category)), category.name());
                }
        }

        @ParameterizedTest
        @EnumSource(value = MessageCategory.class, names = {"LOCAL", "TRADE", "NATION", "TOWN"})
        void channelFeedsShowOnlyTheirCategory(MessageCategory category) {
                Feed feed = Feeds.allFeeds().stream()
                                .filter(f -> f.id().equals(category.name().toLowerCase()))
                                .findFirst().orElseThrow();

                assertTrue(feed.shows(classified(category)));
                for (MessageCategory other : MessageCategory.values()) {
                        if (other != category) {
                                assertFalse(feed.shows(classified(other)), category + " feed shows " + other);
                        }
                }
        }

        @Test
        void unclassifiedFeedKeepsUnknownMessages() {
                assertTrue(Feeds.UNCLASSIFIED.shows(classified(MessageCategory.UNCLASSIFIED)));
                assertTrue(Feeds.GLOBAL.shows(classified(MessageCategory.UNCLASSIFIED)),
                                "the aggregate never swallows an unknown format");
        }

        @Test
        void defaultTabsAreTheImportantOnes() {
                List<String> ids = Feeds.defaultVisibleTabs().stream().map(Feed::id).toList();

                assertEquals(List.of("global", "local", "trade", "nation", "town", "pms"), ids);
        }

        @Test
        void extraFeedsRemainAccessible() {
                List<String> ids = Feeds.extraFeeds().stream().map(Feed::id).toList();

                assertEquals(List.of("discord", "votes", "crates", "deaths", "system", "unclassified"), ids);
        }

        @Test
        void everyCategoryHasAHome() {
                for (MessageCategory category : MessageCategory.values()) {
                        boolean visibleSomewhere = Feeds.allFeeds().stream()
                                        .anyMatch(feed -> feed.shows(classified(category)));
                        assertTrue(visibleSomewhere, category + " has no feed");
                }
        }

        @Test
        void visibleTabsDefaultToTheImportantOnes() {
                List<String> ids = Feeds.visibleTabs(Set.of()).stream().map(Feed::id).toList();

                assertEquals(List.of("global", "local", "trade", "nation", "town", "pms"), ids);
        }

        @Test
        void enabledExtrasAppendAfterTheDefaultTabs() {
                List<String> ids = Feeds.visibleTabs(Set.of("deaths", "discord")).stream()
                                .map(Feed::id).toList();

                assertEquals(List.of("global", "local", "trade", "nation", "town", "pms",
                                "discord", "deaths"), ids);
        }

        @Test
        void unknownEnabledIdsAreIgnored() {
                List<String> ids = Feeds.visibleTabs(Set.of("nonsense", "votes")).stream()
                                .map(Feed::id).toList();

                assertEquals(List.of("global", "local", "trade", "nation", "town", "pms", "votes"), ids);
        }

        private final ClassificationCache cache = new ClassificationCache(new ClassificationPipeline(), 64);
        private final FeedSelection selection = new FeedSelection(Feeds.GLOBAL);
        private GlobalVisibility visibility = GlobalVisibility.SHOW_EVERYTHING;
        private boolean hideTradeWaitLines;
        private final FeedFilter filter = new FeedFilter(cache, selection,
                        () -> visibility, () -> hideTradeWaitLines);

        private GuiMessage message(String text) {
                return new GuiMessage(0, Component.literal(text), new MessageSignature(new byte[256]),
                                GuiMessageSource.SYSTEM_SERVER, GuiMessageTag.system());
        }

        @Test
        void globalTabUsesNoFilterSoVanillaShowsEverything() {
                assertNull(filter.currentFilter());
        }

        /**
         * A filter without its classification service must never exist: the
         * dev.23 live crash was a FeedFilter constructed with a null cache
         * (ChatSubsystem's field initializer ran before the constructor body
         * assigned the cache), and its predicate then dereferenced null inside
         * vanilla's packet path — a disconnect. The constructor now fails
         * fast, so a wiring mistake dies at startup where it is debuggable.
         */
        @Test
        void feedFilterRequiresItsCollaborators() {
                assertThrows(NullPointerException.class,
                                () -> new FeedFilter(null, selection, () -> visibility, () -> false));
                assertThrows(NullPointerException.class,
                                () -> new FeedFilter(cache, null, () -> visibility, () -> false));
                assertThrows(NullPointerException.class, () -> new FeedFilter(cache, selection, null, () -> false));
                assertThrows(NullPointerException.class, () -> new FeedFilter(cache, selection, () -> visibility, null));
        }

        /**
         * Every state installs an {@link FeedFilter.Installed} predicate, so
         * the retention wrap in ChatComponentMixin can tell ZEA's filters
         * from vanilla's and other mods' and retain only what ZEA hides.
         */
        @Test
        void installedFilterIsAlwaysZeaOwned() {
                assertTrue(filter.installableFilter() instanceof FeedFilter.Installed);

                filter.setSearchQuery("");
                assertTrue(filter.installableFilter() instanceof FeedFilter.Installed);

                selection.select(Feeds.VOTES);
                assertTrue(filter.installableFilter() instanceof FeedFilter.Installed);

                filter.setSearchQuery("gold");
                assertTrue(filter.installableFilter() instanceof FeedFilter.Installed);
        }

        @Test
        void installedWrapperDelegatesToTheComposedFilter() {
                selection.select(Feeds.VOTES);
                filter.setSearchQuery("gold");

                Predicate<GuiMessage> installed = filter.installableFilter();

                assertTrue(installed.test(message("AdamGladio voted and received a gold crate /vote")));
                assertFalse(installed.test(message("[Germany|Berlin] KoenigLuis: selling gold cheap")));
                assertFalse(installed.test(message("AdamGladio voted and received a silver crate /vote")));
        }

        /**
         * Vanilla's chat requires a non-null visibleMessageFilter — the field
         * starts always-true and addMessage tests it without a null check, so a
         * null install throws on the next incoming message and drops the
         * connection. The Global tab (also: the disconnect reset) must therefore
         * install "show everything", not null.
         */
        @Test
        void globalTabInstallsNonNullAlwaysTrueFilter() {
                Predicate<GuiMessage> installed = filter.installableFilter();

                assertNotNull(installed);
                assertTrue(installed.test(message("[Germany|Berlin] KoenigLuis: hello")));
                assertTrue(installed.test(message("completely unknown structure")));
        }

        @Test
        void blankSearchOnGlobalInstallsNonNullAlwaysTrueFilter() {
                filter.setSearchQuery("");

                Predicate<GuiMessage> installed = filter.installableFilter();

                assertNotNull(installed);
                assertTrue(installed.test(message("[Germany|Berlin] KoenigLuis: hello")));
        }

        @Test
        void installableFilterTracksSearchAndFeedWhenTheyFilter() {
                filter.setSearchQuery("gravel");
                selection.select(Feeds.VOTES);

                Predicate<GuiMessage> installed = filter.installableFilter();

                assertNotNull(installed);
                assertFalse(installed.test(message("[Germany|Berlin] KoenigLuis: selling dirt cheap")));
                assertTrue(installed.test(message("AdamGladio voted and received a gravel crate /vote")));
        }

        @Test
        void selectedFeedShowsOnlyItsMessages() {
                selection.select(Feeds.VOTES);
                Predicate<GuiMessage> predicate = filter.currentFilter();
                assertNotNull(predicate);

                assertTrue(predicate.test(message("AdamGladio voted and received a gold crate /vote")));
                assertFalse(predicate.test(message("[Yugoslavia|Pest] GavriloPrincip: hello")));
                assertFalse(predicate.test(message("completely unknown structure")));
        }

        @Test
        void unclassifiedMessagesStayVisibleInTheAggregateOnly() {
                selection.select(Feeds.UNCLASSIFIED);
                Predicate<GuiMessage> other = filter.currentFilter();

                assertTrue(other.test(message("completely unknown structure")));
                assertFalse(other.test(message("AdamGladio voted and received a gold crate /vote")));

                selection.select(Feeds.VOTES);
                assertFalse(filter.currentFilter().test(message("completely unknown structure")));
        }

        /**
         * The Global tab's per-type opt-outs: a hidden kind hides there,
         * conversational categories stay, and — the safety rules — Global's
         * own channel and anything unclassified are never hidden, however the
         * set is built.
         */
        @Test
        void globalHidesExactlyTheConfiguredTypes() {
                visibility = new GlobalVisibility(Set.of(
                                MessageCategory.VOTE, MessageCategory.DEATH, MessageCategory.SYSTEM,
                                MessageCategory.UNCLASSIFIED, MessageCategory.GLOBAL));
                selection.select(Feeds.GLOBAL);
                Predicate<GuiMessage> predicate = filter.currentFilter();

                assertNotNull(predicate);
                assertFalse(predicate.test(message("AdamGladio voted and received a gold crate /vote")));
                assertFalse(predicate.test(message("☠ Alex was killed by Bob")));
                assertTrue(predicate.test(message("[Germany|Berlin] KoenigLuis: hello")),
                                "channel chat is conversational and not hidden");
                assertTrue(predicate.test(message("completely unknown structure")),
                                "an unknown format must never be swallowed, even when asked to hide it");
                assertTrue(predicate.test(message("[Global] someone said something")),
                                "the aggregate always keeps its own channel");
        }

        @Test
        void globalShowsEverythingWhenNothingIsHidden() {
                visibility = GlobalVisibility.SHOW_EVERYTHING;
                selection.select(Feeds.GLOBAL);

                assertNull(filter.currentFilter());
        }

        @Test
        void theGlobalOptOutsNeverTouchModuleTabs() {
                visibility = new GlobalVisibility(Set.of(MessageCategory.VOTE));

                for (Feed feed : Feeds.allFeeds()) {
                        if (feed.showsEverything()) {
                                continue;
                        }
                        selection.select(feed);
                        Predicate<GuiMessage> predicate = filter.currentFilter();
                        assertNotNull(predicate);
                        assertTrue(predicate.test(message("AdamGladio voted and received a gold crate /vote")) == feed.shows(
                                        classified(MessageCategory.VOTE)),
                                        feed.label() + " must keep its own category filter");
                }
        }

        /**
         * The server's trade-cooldown rejection line is hidden everywhere
         * while pacing is on — the client answers it itself (queued message,
         * countdown). It stays retrievable like anything a feed hides, and it
         * shows again when pacing is off.
         */
        @Test
        void tradeWaitLinesHideOnEveryFeedWhilePacingIsOn() {
                hideTradeWaitLines = true;
                String waitLine = "» You must wait another 4 seconds before sending your next message in Trade.";

                for (Feed feed : Feeds.allFeeds()) {
                        selection.select(feed);
                        Predicate<GuiMessage> predicate = filter.currentFilter();
                        assertNotNull(predicate, feed.label() + " hides the wait line");
                        assertFalse(predicate.test(message(waitLine)), feed.label() + " hides the wait line");
                }
        }

        @Test
        void tradeWaitLinesShowWhenPacingIsOff() {
                hideTradeWaitLines = false;
                selection.select(Feeds.GLOBAL);

                assertNull(filter.currentFilter());
        }

        @Test
        void filterRecognizesMessagesSeenByTheCacheFirst() {
                Component death = Component.literal("☠ xfw_x was killed by dex247");
                cache.classificationOf(death);

                selection.select(Feeds.DEATHS);
                assertTrue(filter.currentFilter().test(new GuiMessage(0, death, null,
                                GuiMessageSource.SYSTEM_SERVER, null)));
        }

        @Test
        void openSearchWithBlankQueryFiltersNothing() {
                filter.setSearchQuery("");

                assertNull(filter.currentFilter());
        }

        @Test
        void searchOnGlobalNarrowsByText() {
                filter.setSearchQuery("gravel");

                Predicate<GuiMessage> predicate = filter.currentFilter();
                assertNotNull(predicate);
                assertTrue(predicate.test(message("[Germany|Berlin] KoenigLuis: selling gravel cheap")));
                assertFalse(predicate.test(message("[Germany|Berlin] KoenigLuis: selling dirt cheap")));
        }

        @Test
        void searchMatchesCaseInsensitively() {
                filter.setSearchQuery("koENig");

                assertTrue(filter.currentFilter().test(message("[Germany|Berlin] KoenigLuis: hello")));
        }

        @Test
        void searchComposesWithTheSelectedFeed() {
                filter.setSearchQuery("gravel");
                selection.select(Feeds.VOTES);

                Predicate<GuiMessage> predicate = filter.currentFilter();
                // Matches the text but not the feed: stays hidden.
                assertFalse(predicate.test(message("[Germany|Berlin] KoenigLuis: selling gravel cheap")));
                // Matches the feed but not the text: stays hidden.
                assertFalse(predicate.test(message("AdamGladio voted and received a dirt crate /vote")));
                // Matches both: shown.
                assertTrue(predicate.test(message("AdamGladio voted and received a gravel crate /vote")));
        }

        @Test
        void searchMatchesFlattenedStyledText() {
                net.minecraft.network.chat.MutableComponent name = Component.literal("Aurora")
                                .withStyle(net.minecraft.ChatFormatting.AQUA);
                net.minecraft.network.chat.MutableComponent message = Component.literal(": anyone selling gravel?")
                                .withStyle(net.minecraft.ChatFormatting.WHITE);
                filter.setSearchQuery("selling gravel");

                assertTrue(filter.currentFilter().test(new GuiMessage(0, name.append(message), null,
                                GuiMessageSource.SYSTEM_SERVER, null)));
        }

        @Test
        void closingTheSearchRestoresTheFeedFilterOnly() {
                filter.setSearchQuery("gravel");
                selection.select(Feeds.VOTES);
                filter.setSearchQuery(null);

                Predicate<GuiMessage> predicate = filter.currentFilter();
                assertNotNull(predicate);
                assertTrue(predicate.test(message("AdamGladio voted and received a gold crate /vote")));
                assertFalse(predicate.test(message("[Germany|Berlin] KoenigLuis: selling gravel cheap")));
        }
}
