package net.zaminmc.zea.chat.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The chat action buttons: a search toggle and a clear-history button
 * as one quiet flat row at the right end of the chat tab row — the
 * placement Polyfrost's Chatting established (it puts the same buttons
 * beside its search field on the input's row) and the live player asked
 * ZEA to match. Clicking the search button does exactly what Ctrl+F
 * does; clicking the clear button empties the visible chat and this
 * server's saved history in one step, the way Chatting's delete-history
 * button clears the live chat — extended over ZEA's persistent store so
 * "cleared" cannot mean "back after rejoining".
 *
 * <p>Rendering matches the tab strip's restrained language: 9×9 flat
 * translucent squares (Chatting's button grid), icons drawn as chunky
 * 1-pixel fills — no textures, no borders, no animation. The search
 * button is the only one that shows a state: while a search is open it
 * renders "active" (solid background, white icon) like a selected tab.
 *
 * <p>Like every other ZEA chat widget the row is mouse-driven, refuses
 * keyboard focus navigation, and never takes focus from the chat input
 * on click (the dev.24 and dev.36 live reports), so the caret survives
 * every button press. Only the left button activates actions —
 * right-click stays free for message copying.
 */
public final class ChatButtonBar extends AbstractWidget {
        /** One button's edge in screen pixels — Chatting's 9×9 grid. */
        public static final int BUTTON_SIZE = 9;
        /** Gap between adjacent buttons. */
        public static final int BUTTON_GAP = 2;
        /** Vertical offset of the 9px buttons inside the 12px tab row — Chatting's centering, (12 - 9 + 1) / 2. */
        private static final int BUTTON_Y_OFFSET = 2;

        private static final int BACKGROUND = 0x66000000;
        private static final int BACKGROUND_ACTIVE = 0xAA000000;
        private static final int ICON = 0xFFAAAAAA;
        private static final int ICON_ACTIVE = 0xFFFFFFFF;

        /** Button slots, left to right: search first, clear last (Chatting's order). */
        private static final int SEARCH = 0;
        private static final int CLEAR = 1;

        private final boolean searchButton;
        private final boolean clearButton;
        private final BooleanSupplier searchOpen;
        private final Runnable onToggleSearch;
        private final Runnable onClearHistory;

        /**
         * @param x              the row's left edge (the screen places it at the
         *                       right end of the tab row)
         * @param y              the row's top edge (the tab row's top)
         * @param searchButton   whether the search button shows
         * @param clearButton    whether the clear-history button shows
         * @param searchOpen     whether a search is currently open (drives the
         *                       search button's active look)
         * @param onToggleSearch run when the search button is clicked
         * @param onClearHistory run when the clear button is clicked
         */
        public ChatButtonBar(int x, int y, boolean searchButton, boolean clearButton,
                        BooleanSupplier searchOpen, Runnable onToggleSearch, Runnable onClearHistory) {
                super(x, y, rowWidth(searchButton, clearButton), ChatTabBar.TAB_HEIGHT,
                                Component.literal("Chat actions"));
                if (searchOpen == null || onToggleSearch == null || onClearHistory == null) {
                        throw new NullPointerException("callbacks must not be null");
                }
                this.searchButton = searchButton;
                this.clearButton = clearButton;
                this.searchOpen = searchOpen;
                this.onToggleSearch = onToggleSearch;
                this.onClearHistory = onClearHistory;
        }

        /**
         * The width the row occupies for the given buttons — 0 when none
         * show, so callers can skip the widget and reserve nothing.
         */
        public static int rowWidth(boolean searchButton, boolean clearButton) {
                int count = (searchButton ? 1 : 0) + (clearButton ? 1 : 0);
                return count == 0 ? 0 : count * BUTTON_SIZE + (count - 1) * BUTTON_GAP;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                        float partialTick) {
                int y = getY() + BUTTON_Y_OFFSET;
                // Full-box hover, or the tooltip would linger when the mouse
                // leaves vertically while staying inside the button's x-range.
                int hovered = mouseY >= y && mouseY < y + BUTTON_SIZE ? buttonAt(mouseX) : -1;
                for (int button = 0; button < 2; button++) {
                        if (!shows(button)) {
                                continue;
                        }
                        int x = slotX(button);
                        boolean isHovered = button == hovered;
                        boolean active = button == SEARCH && searchOpen.getAsBoolean();
                        extractor.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE,
                                        active || isHovered ? BACKGROUND_ACTIVE : BACKGROUND);
                        int color = active || isHovered ? ICON_ACTIVE : ICON;
                        if (button == SEARCH) {
                                drawMagnifier(extractor, x, y, color);
                        } else {
                                drawTrashCan(extractor, x, y, color);
                        }
                }
                if (hovered >= 0) {
                        // Chatting's tooltips: one yellow line naming the action.
                        setTooltip(Tooltip.create(Component.translatable(hovered == SEARCH
                                        ? "zea.chat.button.search" : "zea.chat.button.clear")
                                        .withStyle(ChatFormatting.YELLOW)));
                } else {
                        setTooltip(null);
                }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                        return;
                }
                int button = buttonAt(event.x());
                if (button == SEARCH) {
                        onToggleSearch.run();
                } else if (button == CLEAR) {
                        onClearHistory.run();
                }
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
                // Quiet like the tab strip — Chatting's buttons are silent too.
        }

        /** Never a keyboard-navigation target; see the class javadoc. */
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
                return null;
        }

        /** Never takes the caret from the chat input on click; see the class javadoc. */
        @Override
        public boolean shouldTakeFocusAfterInteraction() {
                return false;
        }

        @Override
        public boolean isMouseOver(double x, double y) {
                return super.isMouseOver(x, y) && buttonAt(x) >= 0;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
                output.add(NarratedElementType.TITLE, createNarrationMessage());
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
                List<Component> actions = new ArrayList<>();
                if (searchButton) {
                        actions.add(Component.translatable("zea.chat.button.search"));
                }
                if (clearButton) {
                        actions.add(Component.translatable("zea.chat.button.clear"));
                }
                return Component.literal(actions.stream()
                                .map(Component::getString)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse(""));
        }

        /** Whether the slot's button is present in this row. */
        private boolean shows(int button) {
                return button == SEARCH ? searchButton : clearButton;
        }

        /** The slot's left edge — search first, clear after it. */
        private int slotX(int button) {
                int x = getX();
                if (button == CLEAR) {
                        // The clear button follows every button before it; with the
                        // search button off it simply starts at the row's left edge.
                        x += (searchButton ? 1 : 0) * (BUTTON_SIZE + BUTTON_GAP);
                }
                return x;
        }

        /** The button under {@code mouseX}, or -1 between buttons / past the ends. */
        private int buttonAt(double mouseX) {
                for (int button = 0; button < 2; button++) {
                        if (!shows(button)) {
                                continue;
                        }
                        int x = slotX(button);
                        if (mouseX >= x && mouseX < x + BUTTON_SIZE) {
                                return button;
                        }
                }
                return -1;
        }

        /**
         * A magnifier: a square 1-pixel ring with a two-pixel diagonal
         * handle — chunky fills, the pixel-art idiom the vanilla font
         * already speaks.
         */
        private static void drawMagnifier(GuiGraphicsExtractor extractor, int x, int y, int color) {
                // Ring: outer 5×5 at +1, hole 3×3 in the middle.
                extractor.fill(x + 1, y + 1, x + 6, y + 2, color);
                extractor.fill(x + 1, y + 5, x + 6, y + 6, color);
                extractor.fill(x + 1, y + 2, x + 2, y + 5, color);
                extractor.fill(x + 5, y + 2, x + 6, y + 5, color);
                // Handle, down-right from the ring's corner.
                extractor.fill(x + 6, y + 6, x + 7, y + 7, color);
                extractor.fill(x + 7, y + 7, x + 8, y + 8, color);
        }

        /**
         * A trash can, Chatting's delete icon in spirit — lid, body and
         * slits — drawn as chunky 1-pixel fills, the pixel-art idiom the
         * vanilla font already speaks.
         */
        private static void drawTrashCan(GuiGraphicsExtractor extractor, int x, int y, int color) {
                // Lid knob and the lid bar under it.
                extractor.fill(x + 4, y + 1, x + 5, y + 2, color);
                extractor.fill(x + 1, y + 2, x + 8, y + 3, color);
                // Body walls and floor.
                extractor.fill(x + 2, y + 4, x + 3, y + 8, color);
                extractor.fill(x + 6, y + 4, x + 7, y + 8, color);
                extractor.fill(x + 3, y + 7, x + 6, y + 8, color);
                // Two slits inside the body.
                extractor.fill(x + 3, y + 4, x + 4, y + 6, color);
                extractor.fill(x + 5, y + 4, x + 6, y + 6, color);
        }
}
