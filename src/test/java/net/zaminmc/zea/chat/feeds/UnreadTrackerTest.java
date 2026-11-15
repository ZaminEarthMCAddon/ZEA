package net.zaminmc.zea.chat.feeds;

import net.minecraft.network.chat.Component;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.ClassifiedMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.model.MessagePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
                                Feeds.defaultVisibleTabs());

                assertTrue(tracker.isUnread(Feeds.GLOBAL), "the Global tab shows global messages");
                assertTrue(tracker.isUnread(Feeds.ALL), "General shows everything");
                assertFalse(tracker.isUnread(Feeds.LOCAL), "the selected tab cannot go unread");
                assertFalse(tracker.isUnread(Feeds.TRADE), "the Trade tab does not show global messages");
        }

        @Test
        void selectingATabClearsOnlyThatTab() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs());

                tracker.markSelected(Feeds.GLOBAL);

                assertFalse(tracker.isUnread(Feeds.GLOBAL));
                assertTrue(tracker.isUnread(Feeds.ALL), "General still holds its own unseen messages");
        }

        @Test
        void selectionIsTheOnlyWayToClear() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs());

                // Watching the same tab go unread again must not clear it; only
                // Global and General show global messages.
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs());

                assertTrue(tracker.isUnread(Feeds.GLOBAL));
                assertEquals(2, tracker.unreadCount());
        }

        @Test
        void aHiddenFeedStaysUnmarked() {
                // The Votes tab is not among the visible tabs.
                List<Feed> visible = Feeds.defaultVisibleTabs();

                tracker.onMessage(classified(MessageCategory.VOTE), Feeds.GLOBAL, visible);

                assertFalse(tracker.isUnread(Feeds.VOTES), "the tab is not shown, so nothing glows there");
                assertTrue(tracker.isUnread(Feeds.ALL), "General still aggregates the vote");
        }

        @Test
        void messagesOnTheSelectedTabMarkNothingNew() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.GLOBAL,
                                Feeds.defaultVisibleTabs());

                // Only General (which also shows global messages) lights up; the
                // channel the message belongs to is the selected one.
                assertTrue(tracker.isUnread(Feeds.ALL));
                assertFalse(tracker.isUnread(Feeds.GLOBAL));
        }

        @Test
        void nullArgumentsAreIgnored() {
                tracker.onMessage(null, Feeds.GLOBAL, Feeds.defaultVisibleTabs());
                tracker.onMessage(classified(MessageCategory.GLOBAL), null, Feeds.defaultVisibleTabs());
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.GLOBAL, null);
                tracker.markSelected(null);

                assertEquals(0, tracker.unreadCount());
                assertFalse(tracker.isUnread(null));
        }

        @Test
        void clearForgetsEverything() {
                tracker.onMessage(classified(MessageCategory.GLOBAL), Feeds.LOCAL,
                                Feeds.defaultVisibleTabs());

                tracker.clear();

                assertEquals(0, tracker.unreadCount());
                assertFalse(tracker.isUnread(Feeds.ALL));
        }

        @Test
        void aTabEnabledLaterStartsRead() {
                // A vote arrives while the Votes tab is hidden.
                tracker.onMessage(classified(MessageCategory.VOTE), Feeds.GLOBAL,
                                Feeds.defaultVisibleTabs());
                // The player enables the Votes tab afterwards; it was not visible
                // when the message arrived, so it starts in its read state — no
                // stale glow for a tab that was not there to miss anything.
                assertFalse(tracker.isUnread(Feeds.VOTES));
        }
}
