package net.zaminmc.zea.chat.feeds;

import net.zaminmc.zea.chat.model.ClassifiedMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which visible chat tabs hold messages that arrived while the player
 * was looking somewhere else — the unread state behind the tab strip's
 * yellow labels. A tab goes unread the moment a message lands in it
 * while another tab is selected, and goes read the moment the player
 * selects it; the state lives in the subsystem, so it survives the
 * chat screen closing and reopening (that is the point: "new messages
 * since my last visit"), and clears on disconnect.
 *
 * <p>Marking is defined by what the player would have missed: a
 * message marks every <b>visible</b> tab that shows it and is not the
 * selected one. The Global aggregate shows everything except what the
 * user turned off there, so a message whose type was hidden from
 * Global does not light it — a yellow tab the player cannot find
 * anything new on is a lie. A message whose category has no visible
 * tab (the Votes feed disabled, say) lights only the tabs that would
 * have shown it; it never lights anything when the player is already
 * on such a tab. Selection is the only way to clear a tab: quietly
 * watching it turn white is fine, and nothing else claims to have
 * "visited" it.
 *
 * <p>Pure model, no Minecraft types; the subsystem feeds it, the tab
 * strip reads it.
 */
public final class UnreadTracker {
        /** Feeds currently holding unseen messages, by id. */
        private final Set<String> unreadFeedIds = new HashSet<>();

        /**
         * Records one incoming message: every visible tab that shows it and
         * is not the selected feed becomes unread. The Global aggregate's
         * per-type opt-outs are respected — a type hidden from Global does
         * not light it.
         *
         * @param message     the message that just arrived
         * @param selected    the feed the player is currently looking at
         * @param visibleTabs the tabs the strip is showing
         * @param visibility  what the Global tab currently hides
         */
        public void onMessage(ClassifiedMessage message, Feed selected, List<Feed> visibleTabs,
                                        GlobalVisibility visibility) {
                if (message == null || selected == null || visibleTabs == null || visibility == null) {
                        return;
                }
                for (Feed tab : visibleTabs) {
                        if (tab.equals(selected) || !tab.shows(message)) {
                                continue;
                        }
                        if (tab.showsEverything() && visibility.hides(message.category())) {
                                continue;
                        }
                        unreadFeedIds.add(tab.id());
                }
        }

        /** The player opened this feed; whatever it held is now seen. */
        public void markSelected(Feed feed) {
                if (feed != null) {
                        unreadFeedIds.remove(feed.id());
                }
        }

        /** Whether the feed's tab should show its unread color. */
        public boolean isUnread(Feed feed) {
                return feed != null && unreadFeedIds.contains(feed.id());
        }

        /** The number of unread tabs; the strip's worst-case label work. */
        public int unreadCount() {
                return unreadFeedIds.size();
        }

        /** Forgets every tab's unread state; the disconnect reset. */
        public void clear() {
                unreadFeedIds.clear();
        }
}
