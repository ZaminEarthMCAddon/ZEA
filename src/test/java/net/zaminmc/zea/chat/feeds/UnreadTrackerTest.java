package net.zaminmc.zea.chat.feeds;

import net.minecraft.network.chat.Component;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.ClassifiedMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.model.MessagePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnreadTrackerTest {
        private final UnreadTracker tracker = new UnreadTracker();

        private ClassifiedMessage classified(MessageCategory category) {
                CapturedMessage captured = CapturedMessage.of(Component.literal("sample"), Instant.EPOCH);
                return new ClassifiedMessage(captured, category, MessagePayload.None.INSTANCE);
        }

        @Test
        void aMessageMarksEveryOtherTabThatShowsIt() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);

                assertTrue(tracker.isUnread(Feeds.GLOBAL), "the Global tab shows global messages");
                assertFalse(tracker.isUnread(Feeds.LOCAL), "the selected tab cannot go unread");
                assertFalse(tracker.isUnread(Feeds.TRADE), "the Trade tab does not show global messages");
        }

        @Test
        void selectingATabClearsOnlyThatTab() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);
                tracker.onMessage(classified(MessageCategory.PRIVATE_MESSAGE), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);

                tracker.markSelected(Feeds.GLOBAL);

                assertFalse(tracker.isUnread(Feeds.GLOBAL));
                assertTrue(tracker.isUnread(Feeds.PRIVATE_MESSAGES), "the PMs tab still holds its own unseen messages");
        }

        @Test
        void selectionIsTheOnlyWayToClear() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);

                // Watching the same tab go unread again must not clear it; only
                // Global shows global messages.
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);

                assertTrue(tracker.isUnread(Feeds.GLOBAL));
                assertEquals(1, tracker.unreadCount());
        }

        @Test
        void aHiddenFeedStaysUnmarked() {
                // The Votes tab is not among the visible tabs.
                List<Feed> visible = Feeds.defaultVisibleTabs();

                tracker.onMessage(classified(MessageCategory.VOTE), Feeds.LOCAL, visible,
                                GlobalVisibility.SHOW_EVERYTHING);

                assertFalse(tracker.isUnread(Feeds.VOTES), "the tab is not shown, so nothing glows there");
                assertTrue(tracker.isUnread(Feeds.GLOBAL), "Global still aggregates the vote");
        }

        @Test
        void aTypeHiddenFromGlobalDoesNotLightIt() {
                // Votes are turned off on the Global tab.
                GlobalVisibility noVotes = new GlobalVisibility(Set.of(MessageCategory.VOTE));

                tracker.onMessage(classified(MessageCategory.VOTE), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), noVotes);

                assertFalse(tracker.isUnread(Feeds.GLOBAL),
                                "a hidden type lighting Global would be a glow with nothing new behind it");
        }

        @Test
        void aTypeHiddenFromGlobalStillLightsItsOwnTabWhenVisible() {
                GlobalVisibility noVotes = new GlobalVisibility(Set.of(MessageCategory.VOTE));
                List<Feed> visible = Feeds.visibleTabs(Set.of(Feeds.VOTES.id()));

                tracker.onMessage(classified(MessageCategory.VOTE), Feeds.LOCAL, visible, noVotes);

                assertTrue(tracker.isUnread(Feeds.VOTES), "the Votes tab still shows votes");
                assertFalse(tracker.isUnread(Feeds.GLOBAL));
        }

        @Test
        void messagesOnTheSelectedTabMarkNothingNew() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.GLOBAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);

                assertEquals(0, tracker.unreadCount());
        }

        @Test
        void nullArgumentsAreIgnored() {
                tracker.onMessage(null, Feeds.GLOBAL, Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);
                tracker.onMessage(classified(MessageCategory.GLOBAL), null, Feeds.defaultVisibleTabs(),
                                GlobalVisibility.SHOW_EVERYTHING);
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.GLOBAL, null,
                                GlobalVisibility.SHOW_EVERYTHING);
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.GLOBAL,
                                Feeds.defaultVisibleTabs(), null);
                tracker.markSelected(null);

                assertEquals(0, tracker.unreadCount());
                assertFalse(tracker.isUnread(null));
        }

        @Test
        void clearForgetsEverything() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);

                tracker.clear();

                assertEquals(0, tracker.unreadCount());
                assertFalse(tracker.isUnread(Feeds.GLOBAL));
        }

        @Test
        void aTabEnabledLaterStartsRead() {
                // A vote arrives while the Votes tab is hidden.
                tracker.onMessage(classified(MessageCategory.VOTE), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs(), GlobalVisibility.SHOW_EVERYTHING);
                // The player enables the Votes tab afterwards; it was not visible
                // when the message arrived, so it starts in its read state — no
                // stale glow for a tab that was not there to miss anything.
                assertFalse(tracker.isUnread(Feeds.VOTES));
        }
}
