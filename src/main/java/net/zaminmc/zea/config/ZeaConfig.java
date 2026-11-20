package net.zaminmc.zea.config;

import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOrder;
import io.github.notenoughupdates.moulconfig.common.text.StructuredStyle;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import net.zaminmc.zea.buttons.ButtonEditorScreen;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.market.config.MarketConfig;
import net.zaminmc.zea.chat.feeds.Feeds;
import net.zaminmc.zea.chat.feeds.GlobalVisibility;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.notify.Notification;
import net.zaminmc.zea.search.SearchOverlay;
import net.zaminmc.zea.ZeaClient;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ZEA's configuration, edited through MoulConfig and persisted with the
 * safe-write protocol. Fields are written by the MoulConfig editor via
 * reflection; accessors — not raw fields — apply range checks so a
 * hand-edited file can never produce an unsafe runtime value.
 *
 * <p>Layout (the dev.26 live request: chat-related functions nested under
 * Chat instead of standing side by side): one top-level Chat category
 * with everything chat — its own sending options plus the Tabs, Chat
 * interface, Notifications, Mentions and Chat history subcategories —
 * and Inventory buttons beside it. MoulConfig renders a
 * {@code @Category} field inside a category class as a subcategory of the
 * enclosing one (verified against the bundled 4.8.0 processor driver, one
 * nesting level only).
 *
 * <p>The menu title is the mod's name and author with legacy color codes
 * ({@link #MENU_TITLE_CODED}, rendered through {@link #getTitle()} —
 * MoulConfig's default title is "Config GUI").
 *
 * <p>Policy for schema changes: {@code configVersion} gates structured
 * migrations. Loading an older file applies the migration steps for its
 * version; fields the old file omits keep their defaults; unknown fields
 * are dropped on the next save. Version 2 hoists the five chat-related
 * category objects under {@code chat} — the values themselves carry over
 * unchanged. Version 3 replaces the General tab's single
 * "hide server broadcasts" switch with the Global tab's per-type
 * toggles (the General tab itself was removed; Global is the aggregate
 * now).
 */
public final class ZeaConfig extends Config {
        /** Bump when fields change shape; add the matching migration in the store. */
        public static final int CURRENT_VERSION = 3;

        /** Slider bounds, mirrored here so tests can pin the accessible range. */
        public static final int MIN_DISPATCH_INTERVAL_MILLIS = 500;
        public static final int MAX_DISPATCH_INTERVAL_MILLIS = 5000;

        /** Smooth-chat slider bounds. */
        public static final int MIN_SMOOTH_CHAT_MILLIS = 50;
        public static final int MAX_SMOOTH_CHAT_MILLIS = 500;

        /** Auto-response cooldown slider bounds, in seconds. */
        public static final int MIN_AUTO_RESPONSE_COOLDOWN_SECONDS = 10;
        public static final int MAX_AUTO_RESPONSE_COOLDOWN_SECONDS = 300;

        /** History size slider bounds, in entries per server. */
        public static final int MIN_HISTORY_ENTRIES = 500;
        public static final int MAX_HISTORY_ENTRIES = 10_000;

        /** Slot-tint intensity slider bounds, in percent. */
        public static final int MIN_TINT_OPACITY = 10;
        public static final int MAX_TINT_OPACITY = 60;

        /** Search-dim slider bounds and default, in percent of opacity. */
        public static final int MIN_SEARCH_DIM = SearchOverlay.MIN_DIM_INTENSITY;
        public static final int MAX_SEARCH_DIM = SearchOverlay.MAX_DIM_INTENSITY;
        public static final int DEFAULT_SEARCH_DIM = SearchOverlay.DEFAULT_DIM_INTENSITY;

        /** The MoulConfig runnable id that opens the button editor. */
        public static final int RUNNABLE_OPEN_BUTTON_EDITOR = 7;

        /** The default history size, deliberately far below the maximum. */
        public static final int DEFAULT_HISTORY_ENTRIES = 2_000;

        /** The config menu title, written with legacy {@code &} codes. */
        public static final String MENU_TITLE_CODED =
                        "&bZamin's EarthMC Addon&7 - Created by &eAbasing";

        /** Chat options. */
        public static final class Chat {
                @ConfigOption(name = "Multi-channel sending", desc = "Allow sending one message to several "
                                + "channels at once with shift-click. Messages are still sent one at a time, "
                                + "spaced by the dispatch interval below.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean multiSendEnabled = false;

                @ConfigOption(name = "Dispatch interval", desc = "Milliseconds between messages when "
                                + "sending to multiple channels. Conservative spacing respects server spam limits.")
                @ConfigEditorSlider(minValue = MIN_DISPATCH_INTERVAL_MILLIS, maxValue = MAX_DISPATCH_INTERVAL_MILLIS,
                                minStep = 100)
                @ConfigOrder(1)
                public int dispatchIntervalMillis = 1000;

                @ConfigOption(name = "Channel tint", desc = "Tint messages received in a chat channel "
                                + "(Global, Local, Trade, Nation, Town) slightly yellow, so you can tell "
                                + "where a message arrived. Other messages keep their colors.")
                @ConfigEditorBoolean
                @ConfigOrder(2)
                public boolean channelTint = true;

                @Category(name = "Global tab", desc = "What the Global tab shows. Global is the "
                                + "every-message view: every kind of message shows there by default — "
                                + "channels, PMs, votes, deaths, wins, server lines. Turn off the kinds "
                                + "you do not want in it; each kind has its own switch, and everything "
                                + "you hide stays available on its own tab. Messages ZEA cannot classify "
                                + "always show on Global, so a new server format can never vanish.")
                @ConfigOrder(3)
                public GlobalTab globalTab = new GlobalTab();

                @Category(name = "Tabs", desc = "Which additional chat tabs are shown. The default tabs are always visible.")
                @ConfigOrder(4)
                public Tabs tabs = new Tabs();

                @Category(name = "Chat interface", desc = "How the chat looks and moves.")
                @ConfigOrder(5)
                public ChatInterface chatInterface = new ChatInterface();

                @Category(name = "Notifications", desc = "Private message and mention notifications.")
                @ConfigOrder(6)
                public Notifications notifications = new Notifications();

                @Category(name = "Mentions", desc = "How mentions of your name are handled in chat.")
                @ConfigOrder(7)
                public Mentions mentions = new Mentions();

                @Category(name = "Chat history", desc = "Persistent, searchable EarthMC chat history.")
                @ConfigOrder(8)
                public History history = new History();

                @Category(name = "Trade chat", desc = "Trade channel pacing: the server allows one trade "
                                + "message every five minutes.")
                @ConfigOrder(9)
                public TradeChat tradeChat = new TradeChat();

                @Category(name = "Message filters", desc = "Hide the server's advertisement-shaped noise — "
                                + "each category is classified by structure, so ordinary messages that merely "
                                + "mention the same words are never hidden. Hidden messages stay retrievable: "
                                + "they leave the General tab but are never deleted.")
                @ConfigOrder(10)
                public ChatFilters filters = new ChatFilters();
        }

        /** The optional advertisement-shaped message filters, each independently toggleable. */
        public static final class ChatFilters {
                @ConfigOption(name = "Crate reward announcements", desc = "Hide the parenthesized \"got lucky "
                                + "and received … /vote\" announcements in General. Real crate wins keep their "
                                + "own category and tab; normal talk about crates is never hidden.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean hideCrateRewards = true;

                @ConfigOption(name = "Discord recruitment ads", desc = "Hide Discord-forwarded recruitment "
                                + "advertisements (\"NEW PLAYERS! … type /t join X\") in General. Messages that "
                                + "are merely forwarded from Discord stay.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean hideDiscordRecruitment = true;

                @ConfigOption(name = "Promotional banners", desc = "Hide the centered promotional command "
                                + "banners (a lone \"/premium\" line between blank rows). Ordinary messages "
                                + "that mention a command are never hidden.")
                @ConfigEditorBoolean
                @ConfigOrder(2)
                public boolean hidePromoBanners = true;

                @ConfigOption(name = "Item showcase ads", desc = "Hide advertisements disguised as item "
                                + "showcases (\"… is holding [Dragon]\" with an item component). Linked items "
                                + "without that shape stay.")
                @ConfigEditorBoolean
                @ConfigOrder(3)
                public boolean hideItemShowcaseAds = true;

                @ConfigOption(name = "Click-to-visit spawn commands", desc = "Make /n spawn and /t spawn "
                                + "commands in chat clickable: clicking one sends that exact command to the "
                                + "server (never through your selected channel). Nothing happens without a "
                                + "deliberate click.")
                @ConfigEditorBoolean
                @ConfigOrder(4)
                public boolean clickToVisitSpawnCommands = false;
        }

        /**
         * Inventory utilities: buttons beside container GUIs, the search
         * bar beneath them, slot binding and slot locking. The {@code
         * enabled} key is the buttons' master switch (it predates the
         * others and keeps its stored name).
         */
        public static final class InventoryButtons {
                @ConfigOption(name = "Inventory utilities", desc = "Show your custom buttons beside "
                                + "inventory and chest screens. Buttons run the command you "
                                + "configured when clicked; the search bar, slot binding and slot "
                                + "locking have their own switches below.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean enabled = true;

                @ConfigOption(name = "Open button editor", desc = "Open the button editor (also "
                                + "available as the /zeabuttons command).")
                @SuppressWarnings("deprecation") // the annotation member is deprecated-but-live; NEU uses it
                @ConfigEditorButton(runnableId = RUNNABLE_OPEN_BUTTON_EDITOR, buttonText = "Open")
                @ConfigOrder(1)
                public boolean openEditor = true;

                @Category(name = "Search bar", desc = "The search bar under the inventory: calculator and item matching.")
                @ConfigOrder(2)
                public SearchBar searchBar = new SearchBar();

                @Category(name = "Shop index", desc = "The item browser beside the inventory: every item "
                                + "variant observed in EarthMC shops, with the physical shops behind it.")
                @ConfigOrder(3)
                public ShopBrowser shopBrowser = new ShopBrowser();

                @Category(name = "Slot binding", desc = "Bind inventory slots to hotbar slots.")
                @ConfigOrder(4)
                public SlotBinding slotBinding = new SlotBinding();

                @Category(name = "Slot locking", desc = "Protect inventory slots from accidental actions.")
                @ConfigOrder(5)
                public SlotLocking slotLocking = new SlotLocking();
        }

        /** The inventory shop-index browser. */
        public static final class ShopBrowser {
                @ConfigOption(name = "Shop index browser", desc = "Show the item browser on the "
                                + "inventory's right side: observed item variants, their physical shops, "
                                + "and navigation to them.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean enabled = true;
        }

        /** The inventory search bar. */
        public static final class SearchBar {
                @ConfigOption(name = "Search bar", desc = "Show the search bar under inventory and "
                                + "container screens.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean enabled = true;

                @ConfigOption(name = "Calculator", desc = "Type an arithmetic expression (like "
                                + "39 + 4) and the result appears in the bar.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean calculator = true;

                @ConfigOption(name = "Double-click matching", desc = "Double-click the bar to enter "
                                + "matching mode: items whose name or lore match your search are "
                                + "highlighted and the rest are dimmed.")
                @ConfigEditorBoolean
                @ConfigOrder(2)
                public boolean doubleClickMatching = true;

                @ConfigOption(name = "Matching highlight color", desc = "The color of the highlight "
                                + "on matching item slots.")
                @ConfigEditorDropdown
                @ConfigOrder(3)
                public SearchOverlay.HighlightColor highlight = SearchOverlay.HighlightColor.GREEN;

                @ConfigOption(name = "Non-matching dim", desc = "How strongly non-matching items are "
                                + "darkened, in percent.")
                @ConfigEditorSlider(minValue = MIN_SEARCH_DIM, maxValue = MAX_SEARCH_DIM, minStep = 1)
                @ConfigOrder(4)
                public int dimIntensity = DEFAULT_SEARCH_DIM;
        }

        /** Slot binding (inventory slot ↔ hotbar slot). */
        public static final class SlotBinding {
                @ConfigOption(name = "Slot binding", desc = "Hold the lock key over an inventory "
                                + "slot and click a hotbar slot to bind them; right-click the bound "
                                + "slot to swap it with its hotbar partner.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean enabled = true;

                @ConfigOption(name = "Binding indicators", desc = "Show the bound hotbar slot's digit "
                                + "on bound slots.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean indicators = true;
        }

        /** Slot locking. */
        public static final class SlotLocking {
                @ConfigOption(name = "Slot locking", desc = "Press the lock key over a player "
                                + "inventory slot to lock it: clicking, shift-clicking, swapping, "
                                + "dragging and dropping it are blocked. Other keybinds keep working.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean enabled = true;

                @ConfigOption(name = "Lock indicators", desc = "Show a small padlock on locked slots.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean indicators = true;
        }

        /** Persistent chat history. */
        public static final class History {
                /** Retention choices for persistent chat history. */
                public enum Retention {
                        ONE_DAY("1 day", 1),
                        THREE_DAYS("3 days", 3),
                        SEVEN_DAYS("1 week", 7),
                        FOURTEEN_DAYS("2 weeks", 14),
                        THIRTY_DAYS("30 days", 30),
                        NINETY_DAYS("90 days", 90);

                        private final String label;
                        private final int days;

                        Retention(String label, int days) {
                                this.label = label;
                                this.days = days;
                        }

                        /** What the MoulConfig dropdown shows for this choice. */
                        @Override
                        public String toString() {
                                return label;
                        }

                        /** The retention length in whole days. */
                        public int days() {
                                return days;
                        }
                }

                @ConfigOption(name = "Persistent history", desc = "Remember EarthMC chat between "
                                + "sessions. History is stored per server, only on this computer, "
                                + "and is never uploaded.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean enabled = true;

                @ConfigOption(name = "History retention", desc = "How long messages survive. Older "
                                + "messages are removed by the next cleanup — history never grows "
                                + "without bound.")
                @ConfigEditorDropdown
                @ConfigOrder(1)
                public Retention retention = Retention.SEVEN_DAYS;

                @ConfigOption(name = "History size", desc = "How many messages are kept per "
                                + "server. Searching covers exactly these.")
                @ConfigEditorSlider(minValue = MIN_HISTORY_ENTRIES, maxValue = MAX_HISTORY_ENTRIES,
                                minStep = 500)
                @ConfigOrder(2)
                public int maxEntries = DEFAULT_HISTORY_ENTRIES;
        }

        /** Trade channel pacing. */
        public static final class TradeChat {
                @ConfigOption(name = "Trade pacing", desc = "Handle the trade channel's "
                                + "five-minute limit the way the server reports it: a rejected "
                                + "message is held (with auto-send below) and the red wait line is "
                                + "hidden — the client shows the countdown instead, synced to the "
                                + "server's own numbers. Nothing is ever blocked speculatively: "
                                + "while no cooldown is known, trade messages go straight out.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean pacing = true;

                @ConfigOption(name = "Auto-send queued messages", desc = "Send held trade messages "
                                + "automatically when the server's cooldown expires. Queued messages "
                                + "wait, never lost, until sent, deleted from the queue (click the "
                                + "status line above the chat tabs) or you disconnect.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean autoSend = true;
        }

        /** Chat appearance and motion. */
        public static final class ChatInterface {
                @ConfigOption(name = "Smooth chat", desc = "Animate new messages: they glide into "
                                + "place and fade in instead of jumping, the way Polyfrost's Chatting "
                                + "animates arrivals. Right-click copying and search are unaffected.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean smoothChat = true;

                @ConfigOption(name = "Smooth chat duration", desc = "How long one arrival animation "
                                + "takes, in milliseconds. Chatting's default is 500.")
                @ConfigEditorSlider(minValue = MIN_SMOOTH_CHAT_MILLIS, maxValue = MAX_SMOOTH_CHAT_MILLIS,
                                minStep = 10)
                @ConfigOrder(1)
                public int smoothChatDurationMillis = 500;

                @ConfigOption(name = "Smooth scrolling", desc = "Animate the chat scroll position: "
                                + "wheel and key scrolling glide to their target instead of jumping "
                                + "line by line, the way Chatting scrolls.")
                @ConfigEditorBoolean
                @ConfigOrder(2)
                public boolean smoothScrolling = true;

                @ConfigOption(name = "Smooth scrolling duration", desc = "How long one scroll glide "
                                + "takes, in milliseconds. Chatting's default is 150.")
                @ConfigEditorSlider(minValue = MIN_SMOOTH_CHAT_MILLIS, maxValue = MAX_SMOOTH_CHAT_MILLIS,
                                minStep = 10)
                @ConfigOrder(3)
                public int smoothScrollingDurationMillis = 150;

                @ConfigOption(name = "Search button", desc = "Show a search button at the right end "
                                + "of the chat tab row. It opens and closes the chat search — the "
                                + "same thing Ctrl+F does — so the search never depends on knowing "
                                + "the hotkey.")
                @ConfigEditorBoolean
                @ConfigOrder(4)
                public boolean searchButton = true;

                @ConfigOption(name = "Clear history button", desc = "Show a clear button at the right "
                                + "end of the chat tab row. One click empties the visible chat and "
                                + "this server's saved history.")
                @ConfigEditorBoolean
                @ConfigOrder(5)
                public boolean clearHistoryButton = true;
        }

        /** What the Global aggregate tab shows; see {@link Chat.GlobalTab}. */
        public static final class GlobalTab {
                @ConfigOption(name = "Local messages", desc = "Show messages sent in the Local "
                                + "channel on the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean local = true;

                @ConfigOption(name = "Trade messages", desc = "Show messages sent in the Trade "
                                + "channel on the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean trade = true;

                @ConfigOption(name = "Nation messages", desc = "Show messages sent in the Nation "
                                + "channel on the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(2)
                public boolean nation = true;

                @ConfigOption(name = "Town messages", desc = "Show messages sent in the Town "
                                + "channel on the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(3)
                public boolean town = true;

                @ConfigOption(name = "Private messages", desc = "Show private messages on the "
                                + "Global tab. They always show on the PMs tab too.")
                @ConfigEditorBoolean
                @ConfigOrder(4)
                public boolean privateMessages = true;

                @ConfigOption(name = "Discord messages", desc = "Show messages relayed from "
                                + "Discord on the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(5)
                public boolean discord = true;

                @ConfigOption(name = "Votes", desc = "Show vote notifications on the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(6)
                public boolean votes = true;

                @ConfigOption(name = "Crate wins", desc = "Show crate win announcements on the "
                                + "Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(7)
                public boolean crateWins = true;

                @ConfigOption(name = "Deaths", desc = "Show death messages on the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(8)
                public boolean deaths = true;

                @ConfigOption(name = "System messages", desc = "Show server system messages on "
                                + "the Global tab.")
                @ConfigEditorBoolean
                @ConfigOrder(9)
                public boolean system = true;
        }

        /** Chat tab visibility. The default tabs are always present. */
        public static final class Tabs {
                @ConfigOption(name = "Discord tab", desc = "Show incoming Discord messages as a chat tab.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean discord = false;

                @ConfigOption(name = "Votes tab", desc = "Show vote notifications as a chat tab.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean votes = false;

                @ConfigOption(name = "Crate wins tab", desc = "Show crate win announcements as a chat tab.")
                @ConfigEditorBoolean
                @ConfigOrder(2)
                public boolean crates = false;

                @ConfigOption(name = "Deaths tab", desc = "Show death messages as a chat tab.")
                @ConfigEditorBoolean
                @ConfigOrder(3)
                public boolean deaths = false;

                @ConfigOption(name = "System tab", desc = "Show server system messages as a chat tab.")
                @ConfigEditorBoolean
                @ConfigOrder(4)
                public boolean system = false;

                @ConfigOption(name = "Other tab", desc = "Show messages ZEA could not classify as a chat tab.")
                @ConfigEditorBoolean
                @ConfigOrder(5)
                public boolean unclassified = false;
        }

        /** Notifications about private messages and mentions. */
        public static final class Notifications {
                @ConfigOption(name = "PM notifications", desc = "Show a notification when a private "
                                + "message arrives. It stays until you reply to that player.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean pmNotifications = true;

                @ConfigOption(name = "PM notification sound", desc = "Play a soft cue when a private "
                                + "message arrives.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean pmSound = true;

                @ConfigOption(name = "PM notification placement", desc = "Where private message "
                                + "notifications appear. The action bar is the calmer choice; titles "
                                + "re-show while the message waits for a reply.")
                @ConfigEditorDropdown
                @ConfigOrder(2)
                public Notification.Destination pmDestination = Notification.Destination.ACTION_BAR;

                @ConfigOption(name = "Mention notifications", desc = "Show a short notification when "
                                + "someone mentions your name in chat.")
                @ConfigEditorBoolean
                @ConfigOrder(3)
                public boolean mentionNotifications = true;

                @ConfigOption(name = "Mention notification sound", desc = "Play a soft cue when "
                                + "someone mentions your name in chat.")
                @ConfigEditorBoolean
                @ConfigOrder(4)
                public boolean mentionSound = true;

                @ConfigOption(name = "Mention notification placement", desc = "Where mention "
                                + "notifications appear.")
                @ConfigEditorDropdown
                @ConfigOrder(5)
                public Notification.Destination mentionDestination = Notification.Destination.ACTION_BAR;
        }

        /** Mentions: chat highlighting and the opt-in auto-response. */
        public static final class Mentions {
                @ConfigOption(name = "Highlight mentioned messages", desc = "Tint the background of "
                                + "chat lines that mention your name, so they stand out while "
                                + "scrolling.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean highlight = true;

                @ConfigOption(name = "Auto-respond to mentions", desc = "Automatically send the "
                                + "response below when someone mentions you. Off by default; "
                                + "cooldown-limited and capped per session.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean autoRespond = false;

                @ConfigOption(name = "Auto-response message", desc = "What to send when the "
                                + "auto-response triggers. Keep it short, and do not include "
                                + "other players' names — the response is public chat.")
                @ConfigEditorText(forbidden = "")
                @ConfigOrder(2)
                public String autoResponseText = "";

                @ConfigOption(name = "Auto-response cooldown", desc = "Minimum seconds between "
                                + "two automatic responses.")
                @ConfigEditorSlider(minValue = MIN_AUTO_RESPONSE_COOLDOWN_SECONDS,
                                maxValue = MAX_AUTO_RESPONSE_COOLDOWN_SECONDS, minStep = 5)
                @ConfigOrder(3)
                public int autoResponseCooldownSeconds = 30;
        }

        /**
         * Item rarity for tools and armor: Common, Uncommon, Epic,
         * Legendary, calculated from the item's material and its
         * enchantment progression.
         */
        public static final class Rarity {
                @ConfigOption(name = "Item rarity", desc = "Rate supported equipment — the tool, "
                                + "weapon, utility (shears, flint and steel, shield) and armor "
                                + "families — as Common, Uncommon, Epic or Legendary. Every "
                                + "possible outcome is mapped out from the vanilla data and "
                                + "applied as a lookup, so a server's custom enchantments can "
                                + "never shift a rating. Turning this off disables every rarity "
                                + "visual.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean enabled = true;

                @Category(name = "Appearance", desc = "How rarity shows up on items.")
                @ConfigOrder(1)
                public Appearance appearance = new Appearance();

                @Category(name = "Enchantment display", desc = "What the tooltip says about "
                                + "enchantments.")
                @ConfigOrder(2)
                public EnchantmentDisplay enchantmentDisplay = new EnchantmentDisplay();
        }

        /** Rarity appearance: slot tint, name coloring, tooltip label. */
        public static final class Appearance {
                @ConfigOption(name = "Slot tint", desc = "Give the slot of a rated item a subtle "
                                + "background wash in its rarity color inside inventory and "
                                + "container screens. Items, counts, durability bars and enchantment "
                                + "glints draw on top of it untouched.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean slotTint = true;

                @ConfigOption(name = "Hotbar tint", desc = "Show the same rarity wash under the "
                                + "items in your hotbar (and the off-hand slot) in the world, not "
                                + "only inside inventory screens.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean hotbarTint = true;

                @ConfigOption(name = "Tint intensity", desc = "How strong the rarity wash is, in "
                                + "percent. Lower is subtler.")
                @ConfigEditorSlider(minValue = MIN_TINT_OPACITY, maxValue = MAX_TINT_OPACITY,
                                minStep = 5)
                @ConfigOrder(2)
                public int tintOpacity = 25;

                @ConfigOption(name = "Rarity-colored names", desc = "Color a supported item's tooltip name in "
                                + "its rarity color. The exact name text, its translations and its non-color "
                                + "formatting survive untouched — only the presented color follows the "
                                + "rarity, and the item itself is never modified.")
                @ConfigEditorBoolean
                @ConfigOrder(3)
                public boolean nameColoring = true;

                @ConfigOption(name = "Rarity in tooltip", desc = "End supported items' tooltips with "
                                + "the bold rarity and equipment type (COMMON, UNCOMMON, EPIC, "
                                + "LEGENDARY — SWORD, PICKAXE, CHESTPLATE, ...) in the rarity color, "
                                + "before Minecraft's own advanced tooltip lines.")
                @ConfigEditorBoolean
                @ConfigOrder(4)
                public boolean loreRarity = true;

                @ConfigOption(name = "RPG-style stats", desc = "Replace the vanilla 'When in "
                                + "Main Hand' attribute clutter and the per-line enchantment list with "
                                + "the clean RPG presentation — compact stat lines (Damage: +12 [5], "
                                + "Defense, Mining Speed, Durability), grouped enchantments and the "
                                + "category-specific numbers (bow draw, mace smash, trident throw). "
                                + "Statistics are calculated from the item's actual attributes, "
                                + "enchantments and components, never invented. Server-provided lore "
                                + "and Minecraft's advanced F3+H tooltip are preserved untouched.")
                @ConfigEditorBoolean
                @ConfigOrder(4)
                public boolean rpgStats = true;
        }

        /** Enchantment display: missing upgrades on Shift and max-level chroma. */
        public static final class EnchantmentDisplay {
                @ConfigOption(name = "Missing enchantments", desc = "Hold Shift while hovering a "
                                + "supported tool or armor piece to see its meaningful remaining "
                                + "upgrades as a gray, comma-separated list. Impossible combinations "
                                + "and preference enchantments (Knockback, Thorns, ...) are never "
                                + "listed.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean missingEnchants = true;

                @ConfigOption(name = "Max-level chroma", desc = "Enchantments that have reached "
                                + "their maximum legitimate level cycle slowly through the colors in "
                                + "the tooltip. Turning this off keeps the level tiers visible but "
                                + "gives maxed enchantments a static gold instead of the animation.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean chroma = false;
        }

        /** Small quality-of-life features that fit nowhere else. */
        public static final class Miscellaneous {
                @ConfigOption(name = "EarthMC Only", desc = "Stay idle on every server the "
                                + "address does not recognise as EarthMC (earthmc.net and its "
                                + "subdomains): the EarthMC-specific features — chat channels, "
                                + "the market — stay off there and ZEA logs one notice per "
                                + "connection. Off keeps them available everywhere for testing.")
                @ConfigEditorBoolean
                @ConfigOrder(0)
                public boolean earthMcOnly = false;

                @ConfigOption(name = "Scrollable tooltips", desc = "When an item tooltip is taller "
                                + "or wider than the screen, scroll it with the mouse wheel (Shift "
                                + "scrolls sideways). It stays pinned inside the screen and starts at "
                                + "the top when you move onto another item.")
                @ConfigEditorBoolean
                @ConfigOrder(1)
                public boolean scrollableTooltips = true;

                @ConfigOption(name = "Hide server item meta", desc = "Hide the server's Historical "
                                + "information block on forged items — the Forged by, Origin and Date "
                                + "lines. Every line of the record is recognized by its exact text or "
                                + "prefix and removed; a tooltip without the block is untouched line "
                                + "for line.")
                @ConfigEditorBoolean
                @ConfigOrder(2)
                public boolean hideServerItemMeta = true;
        }

        private int configVersion = CURRENT_VERSION;

        @Category(name = "Chat", desc = "EarthMC chat: sending, tabs, appearance, notifications, history.")
        @ConfigOrder(0)
        public Chat chat = new Chat();

        @Category(name = "Inventory utilities", desc = "Buttons, search bar, slot binding and slot locking.")
        @ConfigOrder(1)
        public InventoryButtons inventoryButtons = new InventoryButtons();

        @Category(name = "Rarity", desc = "Item rarity for tools and armor: calculation and visuals.")
        @ConfigOrder(2)
        public Rarity rarity = new Rarity();

        @Category(name = "Miscellaneous", desc = "Small utilities: scrollable tooltips and friends.")
        @ConfigOrder(3)
        public Miscellaneous miscellaneous = new Miscellaneous();

        @Category(name = "Market", desc = "EarthMC physical shops: inspect, compare, navigate.")
        @ConfigOrder(5)
        public MarketConfig market = new MarketConfig();

        /** Set by the store so the MoulConfig editor's saves go through safe-write. */
        private transient Runnable saveAction;

        /**
         * The menu title MoulConfig draws above the editor — the mod's name
         * and author, colors from the legacy {@code &} codes. The default
         * this replaces is MoulConfig's own "Config GUI".
         */
        @Override
        public StructuredText getTitle() {
                StructuredText.Mutable out = StructuredText.empty();
                StructuredStyle style = freshStyle();
                StringBuilder run = new StringBuilder();
                for (int i = 0; i < MENU_TITLE_CODED.length(); i++) {
                        char c = MENU_TITLE_CODED.charAt(i);
                        ChatFormatting code = (c == '§' || c == '&') && i + 1 < MENU_TITLE_CODED.length()
                                        ? ChatFormatting.getByCode(Character.toLowerCase(MENU_TITLE_CODED.charAt(i + 1)))
                                        : null;
                        if (code != null) {
                                if (run.length() > 0) {
                                        out.append(StructuredText.of(run.toString()).withStyle(style));
                                        run.setLength(0);
                                }
                                style = applyCode(style, code);
                                i++;
                                continue;
                        }
                        run.append(c);
                }
                if (run.length() > 0) {
                        out.append(StructuredText.of(run.toString()).withStyle(style));
                }
                return out;
        }

        /**
         * The button-editor launcher for the config's "Open button editor"
         * button. Deferred through {@link Minecraft#execute} like the
         * settings screen itself: a command that runs mid-keystroke would
         * have its screen wiped by the same key's follow-up handling.
         */
        @Override
        @SuppressWarnings("deprecation") // MoulConfig's runnable hook, deprecated-but-live; NEU uses it too
        public void executeRunnable(int runnableId) {
                if (runnableId == RUNNABLE_OPEN_BUTTON_EDITOR) {
                        Minecraft minecraft = Minecraft.getInstance();
                        net.zaminmc.zea.buttons.InventoryButtons buttons = ZeaClient.inventoryButtons();
                        if (buttons != null) {
                                minecraft.execute(() -> minecraft.gui.setScreen(new ButtonEditorScreen(null, buttons)));
                        }
                }
        }

        @Override
        public boolean isValidRunnable(int runnableId) {
                return runnableId == RUNNABLE_OPEN_BUTTON_EDITOR;
        }

        /** An empty MoulConfig style, the base every code applies onto. */
        private static StructuredStyle freshStyle() {
                return StructuredText.empty().getStyle();
        }

        /**
         * One legacy {@link ChatFormatting} applied onto MoulConfig's
         * {@link StructuredStyle}: colors via {@link TextColor}, the five
         * format flags, reset back to empty.
         */
        private static StructuredStyle applyCode(StructuredStyle style, ChatFormatting code) {
                TextColor color = TextColor.fromLegacyFormat(code);
                if (color != null) {
                        return style.withColour(color.getValue());
                }
                return switch (code) {
                        case RESET -> freshStyle();
                        case OBFUSCATED -> style.withObfuscated(true);
                        case BOLD -> style.withBold(true);
                        case STRIKETHROUGH -> style.withStrikethrough(true);
                        case UNDERLINE -> style.withUnderline(true);
                        case ITALIC -> style.withItalic(true);
                        default -> style;
                };
        }

        /** Interval between multi-channel dispatches, clamped to the slider range. */
        public long dispatchIntervalMillis() {
                return Math.clamp(chat.dispatchIntervalMillis,
                                MIN_DISPATCH_INTERVAL_MILLIS, MAX_DISPATCH_INTERVAL_MILLIS);
        }

        /** Whether multi-target sending is enabled; disabled by default per the Phase 1 spec. */
        public boolean multiSendEnabled() {
                return chat.multiSendEnabled;
        }

        /** Whether received channel messages are tinted slightly yellow; on by default. */
        public boolean channelTintEnabled() {
                return chat.channelTint;
        }

        /**
         * What the Global aggregate tab hides, built live from the Global-tab
         * switches. Never null, and never hides Global's own channel or
         * unclassified messages — those two are not user-toggleable.
         */
        public GlobalVisibility globalVisibility() {
                GlobalTab global = chat.globalTab;
                if (global == null) {
                        return GlobalVisibility.SHOW_EVERYTHING;
                }
                Set<MessageCategory> hidden = EnumSet.noneOf(MessageCategory.class);
                if (!global.local) {
                        hidden.add(MessageCategory.LOCAL);
                }
                if (!global.trade) {
                        hidden.add(MessageCategory.TRADE);
                }
                if (!global.nation) {
                        hidden.add(MessageCategory.NATION);
                }
                if (!global.town) {
                        hidden.add(MessageCategory.TOWN);
                }
                if (!global.privateMessages) {
                        hidden.add(MessageCategory.PRIVATE_MESSAGE);
                }
                if (!global.discord) {
                        hidden.add(MessageCategory.DISCORD);
                }
                if (!global.votes) {
                        hidden.add(MessageCategory.VOTE);
                }
                if (!global.crateWins) {
                        hidden.add(MessageCategory.CRATE_WIN);
                }
                if (!global.deaths) {
                        hidden.add(MessageCategory.DEATH);
                }
                if (!global.system) {
                        hidden.add(MessageCategory.SYSTEM);
                }
                if (hidden.isEmpty()) {
                        return GlobalVisibility.SHOW_EVERYTHING;
                }
                return new GlobalVisibility(hidden);
        }

        /**
         * The advertisement-shaped categories the message filters hide on
         * the General feed, per the four independent toggles. Built per
         * call — the set is tiny and the toggles live-edit.
         */
        public Set<MessageCategory> hiddenChatCategories() {
                Set<MessageCategory> hidden = EnumSet.noneOf(MessageCategory.class);
                if (chat.filters.hideCrateRewards) {
                        hidden.add(MessageCategory.CRATE_LUCKY);
                }
                if (chat.filters.hideDiscordRecruitment) {
                        hidden.add(MessageCategory.DISCORD_RECRUITMENT);
                }
                if (chat.filters.hidePromoBanners) {
                        hidden.add(MessageCategory.PROMO_BANNER);
                }
                if (chat.filters.hideItemShowcaseAds) {
                        hidden.add(MessageCategory.ITEM_SHOWCASE);
                }
                return hidden;
        }

        /** Whether spawn commands in chat are click-to-visit; off by default. */
        public boolean clickToVisitSpawnCommandsEnabled() {
                return chat.filters.clickToVisitSpawnCommands;
        }

        /** Whether the trade channel's sends are paced against the server's cooldown; on by default. */
        public boolean tradePacingEnabled() {
                return chat.tradeChat.pacing;
        }

        /** Whether queued trade messages send automatically when the cooldown expires; on by default. */
        public boolean tradeAutoSend() {
                return chat.tradeChat.autoSend;
        }

        /** Whether smooth chat is enabled; on by default. */
        public boolean smoothChatEnabled() {
                return chat.chatInterface.smoothChat;
        }

        /** The smooth-chat animation length, clamped to the slider range. */
        public int smoothChatDurationMillis() {
                return (int) Math.clamp(chat.chatInterface.smoothChatDurationMillis,
                                MIN_SMOOTH_CHAT_MILLIS, MAX_SMOOTH_CHAT_MILLIS);
        }

        /** Whether the scroll position animates between anchor jumps; on by default. */
        public boolean smoothScrollingEnabled() {
                return chat.chatInterface.smoothScrolling;
        }

        /** The smooth-scroll animation length, clamped to the slider range. */
        public int smoothScrollingDurationMillis() {
                return (int) Math.clamp(chat.chatInterface.smoothScrollingDurationMillis,
                                MIN_SMOOTH_CHAT_MILLIS, MAX_SMOOTH_CHAT_MILLIS);
        }

        /** Whether the search button shows in the chat action row; on by default. */
        public boolean searchButtonEnabled() {
                return chat.chatInterface.searchButton;
        }

        /** Whether the clear-history button shows in the chat action row; on by default. */
        public boolean clearHistoryButtonEnabled() {
                return chat.chatInterface.clearHistoryButton;
        }

        /**
         * The extra feed ids whose tabs the user enabled. Ids come from
         * {@link Feeds} so the mapping cannot drift from the registry.
         */
        public Set<String> enabledExtraFeedIds() {
                Set<String> ids = new LinkedHashSet<>();
                if (chat.tabs.discord) {
                        ids.add(Feeds.DISCORD.id());
                }
                if (chat.tabs.votes) {
                        ids.add(Feeds.VOTES.id());
                }
                if (chat.tabs.crates) {
                        ids.add(Feeds.CRATE_WINS.id());
                }
                if (chat.tabs.deaths) {
                        ids.add(Feeds.DEATHS.id());
                }
                if (chat.tabs.system) {
                        ids.add(Feeds.SYSTEM.id());
                }
                if (chat.tabs.unclassified) {
                        ids.add(Feeds.UNCLASSIFIED.id());
                }
                return ids;
        }

        /** Whether private message notifications are enabled; on by default. */
        public boolean pmNotificationsEnabled() {
                return chat.notifications.pmNotifications;
        }

        /** Whether the private message cue sounds; on by default. */
        public boolean pmSoundEnabled() {
                return chat.notifications.pmSound;
        }

        /** Where private message notifications appear; defaults if unset. */
        public Notification.Destination pmDestination() {
                return chat.notifications.pmDestination == null
                                ? Notification.Destination.ACTION_BAR
                                : chat.notifications.pmDestination;
        }

        /** Whether mention notifications are enabled; on by default. */
        public boolean mentionNotificationsEnabled() {
                return chat.notifications.mentionNotifications;
        }

        /** Whether the mention cue sounds; on by default. */
        public boolean mentionSoundEnabled() {
                return chat.notifications.mentionSound;
        }

        /** Where mention notifications appear; defaults if unset. */
        public Notification.Destination mentionDestination() {
                return chat.notifications.mentionDestination == null
                                ? Notification.Destination.ACTION_BAR
                                : chat.notifications.mentionDestination;
        }

        /** Whether mentioned chat lines are tinted; on by default. */
        public boolean mentionHighlightEnabled() {
                return chat.mentions.highlight;
        }

        /** Whether the mention auto-response is enabled; off by default. */
        public boolean autoRespondEnabled() {
                return chat.mentions.autoRespond;
        }

        /** The configured auto-response text, trimmed. */
        public String autoResponseText() {
                return chat.mentions.autoResponseText == null ? "" : chat.mentions.autoResponseText.strip();
        }

        /** The auto-response cooldown, clamped to the slider range. */
        public long autoResponseCooldownMillis() {
                return Math.clamp(chat.mentions.autoResponseCooldownSeconds,
                                MIN_AUTO_RESPONSE_COOLDOWN_SECONDS, MAX_AUTO_RESPONSE_COOLDOWN_SECONDS) * 1000L;
        }

        /** Whether inventory buttons render; on by default (they do nothing until configured). */
        public boolean inventoryButtonsEnabled() {
                return inventoryButtons != null && inventoryButtons.enabled;
        }

        /** Whether the inventory search bar is shown. */
        public boolean inventorySearchEnabled() {
                return inventoryButtons != null && inventoryButtons.searchBar != null
                                && inventoryButtons.searchBar.enabled;
        }

        /** Whether the inventory shop-index browser is shown. */
        public boolean inventoryShopBrowserEnabled() {
                return inventoryButtons != null && inventoryButtons.shopBrowser != null
                                && inventoryButtons.shopBrowser.enabled;
        }

        /** Whether the search bar evaluates arithmetic. */
        public boolean inventorySearchCalculatorEnabled() {
                return inventorySearchEnabled() && inventoryButtons.searchBar.calculator;
        }

        /** Whether double-click matching mode may be entered. */
        public boolean inventorySearchMatchingEnabled() {
                return inventorySearchEnabled() && inventoryButtons.searchBar.doubleClickMatching;
        }

        /** The matching highlight color, defaulting to green when unset. */
        public SearchOverlay.HighlightColor inventorySearchHighlight() {
                if (inventoryButtons == null || inventoryButtons.searchBar == null
                                || inventoryButtons.searchBar.highlight == null) {
                        return SearchOverlay.HighlightColor.GREEN;
                }
                return inventoryButtons.searchBar.highlight;
        }

        /** The non-matching dim intensity, clamped to the slider range. */
        public int inventorySearchDimIntensity() {
                if (inventoryButtons == null || inventoryButtons.searchBar == null) {
                        return DEFAULT_SEARCH_DIM;
                }
                return (int) Math.clamp(inventoryButtons.searchBar.dimIntensity, MIN_SEARCH_DIM, MAX_SEARCH_DIM);
        }

        /** Whether slot binding is enabled. */
        public boolean slotBindingEnabled() {
                return inventoryButtons != null && inventoryButtons.slotBinding != null
                                && inventoryButtons.slotBinding.enabled;
        }

        /** Whether binding indicators are drawn. */
        public boolean slotBindingIndicatorsEnabled() {
                return slotBindingEnabled() && inventoryButtons.slotBinding.indicators;
        }

        /** Whether slot locking is enabled. */
        public boolean slotLockingEnabled() {
                return inventoryButtons != null && inventoryButtons.slotLocking != null
                                && inventoryButtons.slotLocking.enabled;
        }

        /** Whether lock indicators are drawn. */
        public boolean slotLockIndicatorsEnabled() {
                return slotLockingEnabled() && inventoryButtons.slotLocking.indicators;
        }

        /** Whether the item rarity system runs; the master switch for every rarity visual. */
        public boolean rarityEnabled() {
                return rarity != null && rarity.enabled;
        }

        /** Whether supported items' slots receive the rarity background tint. */
        public boolean raritySlotTintEnabled() {
                return rarityEnabled() && rarity.appearance.slotTint;
        }

        /** Whether the hotbar shows the rarity wash under its items; on by default. */
        public boolean rarityHotbarTintEnabled() {
                return rarityEnabled() && rarity.appearance.hotbarTint;
        }

        /** The slot-tint intensity, clamped to the slider range. */
        public int rarityTintOpacity() {
                if (rarity == null) {
                        return 25;
                }
                return (int) Math.clamp(rarity.appearance.tintOpacity, MIN_TINT_OPACITY, MAX_TINT_OPACITY);
        }

        /** Whether vanilla item names are recolored to the rarity color; custom names always win. */
        public boolean rarityNameColoringEnabled() {
                return rarityEnabled() && rarity.appearance.nameColoring;
        }

        /** Whether supported items' tooltips end with the rarity label. */
        public boolean rarityLoreRarityEnabled() {
                return rarityEnabled() && rarity.appearance.loreRarity;
        }

        /** Whether the RPG-style stat block replaces the vanilla attribute tooltip lines. */
        public boolean rarityRpgStatsEnabled() {
                return rarityEnabled() && rarity.appearance.rpgStats;
        }

        /** Whether holding Shift reveals the missing enchantment upgrades. */
        public boolean rarityMissingEnchantsEnabled() {
                return rarityEnabled() && rarity.enchantmentDisplay.missingEnchants;
        }

        /** Whether maximum-level enchantments carry the chroma animation. */
        public boolean rarityChromaEnabled() {
                return rarityEnabled() && rarity.enchantmentDisplay.chroma;
        }

        /** Whether forged items' Historical information block is hidden. */
        public boolean hideServerItemMetaEnabled() {
                return miscellaneous != null && miscellaneous.hideServerItemMeta;
        }

        /** Whether overflowing tooltips scroll with the mouse wheel. */
        public boolean scrollableTooltipsEnabled() {
                return miscellaneous != null && miscellaneous.scrollableTooltips;
        }

        /**
         * Whether ZEA stays idle on servers the address does not recognise
         * as EarthMC: the EarthMC-specific features (chat channels, the
         * market) stay off there and one warning is logged per connection.
         * Off by default — the pre-existing behavior that permits
         * supported functionality on other servers for testing.
         */
        public boolean earthMcOnly() {
                return miscellaneous != null && miscellaneous.earthMcOnly;
        }

        /** Whether chat history persists between sessions; on by default, strictly retention-bound. */
        public boolean historyEnabled() {
                return chat.history.enabled;
        }

        /** The selected retention, in days; a hand-cleared field falls back to one week. */
        public int historyRetentionDays() {
                History.Retention retention = chat.history.retention;
                return (retention == null ? History.Retention.SEVEN_DAYS : retention).days();
        }

        /** The selected retention, in milliseconds. */
        public long historyRetentionMillis() {
                return historyRetentionDays() * 86_400_000L;
        }

        /** The history size limit, clamped to the slider range. */
        public int historyMaxEntries() {
                return (int) Math.clamp(chat.history.maxEntries, MIN_HISTORY_ENTRIES, MAX_HISTORY_ENTRIES);
        }

        /** The schema version this instance was loaded from (or wrote). */
        public int configVersion() {
                return configVersion;
        }

        /** Marks the instance as migrated to {@link #CURRENT_VERSION}. */
        public void markMigrated() {
                configVersion = CURRENT_VERSION;
        }

        public void setSaveAction(Runnable saveAction) {
                this.saveAction = saveAction;
        }

        @Override
        public void saveNow() {
                if (saveAction != null) {
                        saveAction.run();
                }
        }
}
