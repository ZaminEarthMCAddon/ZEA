package net.zaminmc.zea.chat.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The trade queue panel: what the pacing held and what will happen to
 * it, opened by clicking the status line above the chat tabs. One row
 * per queued trade message with a delete button beside it — a held
 * message is the player's own words, and being able to take them back
 * is the difference between a queue and a trap — plus the auto-send
 * toggle (the status line's old click action, moved somewhere it has
 * room to explain itself) and a close button in the header.
 *
 * <p>The panel reads the queue fresh every frame, so deletions and the
 * auto-send drain reflow it live; it hugs its content (width measured
 * from the rows, clamped) with the right edge and bottom fixed. Long
 * messages are cut to the row and carry the full text as a tooltip —
 * "labels are never shortened" is a tab-strip rule; here the message
 * is the point, so it truncates visibly and tells all on hover.
 *
 * <p>Like every ZEA chat widget it is mouse-driven, refuses keyboard
 * focus navigation, and never takes focus from the chat input on click
 * — typing continues while the panel is open.
 */
public final class TradeQueuePanel extends AbstractWidget {
        /** One row's height; the tab strip's density. */
        public static final int ROW_HEIGHT = 12;
        /** The panel's widest extent, however long the messages are. */
        public static final int MAX_WIDTH = 240;
        /** The narrowest the panel gets — the header and auto rows' width. */
        public static final int MIN_WIDTH = 130;

        private static final int PADDING = 5;
        private static final int BUTTON_SIZE = 9;
        private static final int TEXT_Y_OFFSET = 2;
        private static final int BACKGROUND = 0xB0000000;
        private static final int ROW_HOVER = 0x22FFFFFF;
        private static final int ICON = 0xFFAAAAAA;
        private static final int ICON_ACTIVE = 0xFFFFFFFF;

        /** The command every queued line starts with, stripped for display. */
        private static final String TRADE_COMMAND = "tradechat ";

        private final Font font;
        private final Supplier<List<String>> queued;
        private final BooleanSupplier autoSend;
        private final Runnable onToggleAutoSend;
        private final IntConsumer onRemoveQueued;
        private final Runnable onClose;
        private final int rightEdge;
        private final int bottomY;

        /** The queued rows the current layout was built from; delete indexes into this. */
        private List<String> rows = List.of();

        public TradeQueuePanel(Font font, int rightEdge, int bottomY, Supplier<List<String>> queued,
                                        BooleanSupplier autoSend, Runnable onToggleAutoSend, IntConsumer onRemoveQueued,
                                        Runnable onClose) {
                super(rightEdge - MIN_WIDTH, bottomY - 3 * ROW_HEIGHT, MIN_WIDTH, 3 * ROW_HEIGHT,
                                                Component.translatable("zea.chat.trade.queue.title"));
                if (font == null || queued == null || autoSend == null || onToggleAutoSend == null
                                                        || onRemoveQueued == null || onClose == null) {
                        throw new NullPointerException("callbacks must not be null");
                }
                this.font = font;
                this.queued = queued;
                this.autoSend = autoSend;
                this.onToggleAutoSend = onToggleAutoSend;
                this.onRemoveQueued = onRemoveQueued;
                this.onClose = onClose;
                this.rightEdge = rightEdge;
                this.bottomY = bottomY;
                setHeight(3 * ROW_HEIGHT);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                        float partialTick) {
                rows = queued.get();
                int rowCount = rowCount();
                int height = rowCount * ROW_HEIGHT;

                int width = MIN_WIDTH;
                width = Math.max(width, font.width(headerLabel()));
                width = Math.max(width, font.width(autoSendLabel()));
                for (int i = 0; i < rows.size(); i++) {
                        width = Math.max(width, font.width(fullRowText(i)) + deleteColumn());
                }
                width = Math.min(width, MAX_WIDTH);
                setX(rightEdge - width);
                setY(bottomY - height);
                setWidth(width);
                setHeight(height);

                extractor.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND);

                int hoveredRow = rowAt(mouseX, mouseY);
                for (int row = 0; row < rowCount; row++) {
                        int y = getY() + row * ROW_HEIGHT;
                        if (row == hoveredRow && row >= 2) {
                                extractor.fill(getX(), y, getX() + width, y + ROW_HEIGHT, ROW_HOVER);
                        }
                        Component label = rowLabel(row, width);
                        ChatFormatting color = row == 0 ? ChatFormatting.YELLOW
                                                        : row == 1 ? (autoSend.getAsBoolean() ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                                                        : ChatFormatting.WHITE;
                        extractor.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                                                        .accept(TextAlignment.LEFT, getX() + PADDING, y + TEXT_Y_OFFSET,
                                                                                        label.copy().withStyle(color));
                        if (row == 0) {
                                drawCloseButton(extractor, mouseX, mouseY);
                        } else if (row >= 2 && !rows.isEmpty()) {
                                drawDeleteButton(extractor, row - 2, mouseX, mouseY);
                        }
                }

                if (hoveredRow >= 2) {
                        int index = hoveredRow - 2;
                        if (index < rows.size() && font.width(fullRowText(index)) + deleteColumn() > width) {
                                setTooltip(Tooltip.create(Component.literal(displayText(index))));
                        } else {
                                setTooltip(deleteTooltipIfHovered(hoveredRow, mouseX, mouseY));
                        }
                } else {
                        setTooltip(deleteTooltipIfHovered(hoveredRow, mouseX, mouseY));
                }
        }

        /** The queued rows' labels: header, auto-send, then the messages (or the empty note). */
        private int rowCount() {
                return rows.isEmpty() ? 3 : 2 + rows.size();
        }

        private Component headerLabel() {
                return Component.translatable("zea.chat.trade.queue.title");
        }

        private Component autoSendLabel() {
                return Component.translatable(autoSend.getAsBoolean()
                                                ? "zea.chat.trade.queue.auto_on" : "zea.chat.trade.queue.auto_off");
        }

        /** One row's label fitted to its space: the header or auto text as is, a message as "N. text…". */
        private Component rowLabel(int row, int panelWidth) {
                if (row == 0) {
                        return headerLabel();
                }
                if (row == 1) {
                        return autoSendLabel();
                }
                if (rows.isEmpty()) {
                        return Component.translatable("zea.chat.trade.queue.empty").withStyle(ChatFormatting.GRAY);
                }
                int available = panelWidth - PADDING - deleteColumn();
                return Component.literal(fit(fullRowText(row - 2), available));
        }

        /** The message row's untruncated text, for measuring and the tooltip. */
        private String fullRowText(int index) {
                return (index + 1) + ". " + displayText(index);
        }

        /** Cuts {@code text} to {@code maxWidth} pixels, marking the cut with an ellipsis. */
        private String fit(String text, int maxWidth) {
                if (font.width(text) <= maxWidth) {
                        return text;
                }
                String ellipsis = "…";
                return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }

        private String displayText(int index) {
                String line = index < rows.size() ? rows.get(index) : "";
                return line.regionMatches(true, 0, TRADE_COMMAND, 0, Math.min(TRADE_COMMAND.length(), line.length()))
                                                ? line.substring(TRADE_COMMAND.length())
                                                : line;
        }

        /** Room the delete button column owns at a row's right end. */
        private int deleteColumn() {
                return PADDING + BUTTON_SIZE + PADDING;
        }

        private int closeButtonX() {
                return getX() + getWidth() - PADDING - BUTTON_SIZE;
        }

        private int buttonY(int row) {
                return getY() + row * ROW_HEIGHT + (ROW_HEIGHT - BUTTON_SIZE) / 2;
        }

        private void drawCloseButton(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
                int x = closeButtonX();
                int y = buttonY(0);
                boolean active = mouseInSquare(mouseX, mouseY, x, y);
                drawCross(extractor, x, y, active ? ICON_ACTIVE : ICON);
        }

        private void drawDeleteButton(GuiGraphicsExtractor extractor, int row, int mouseX, int mouseY) {
                int x = closeButtonX();
                int y = buttonY(row + 2);
                boolean active = mouseInSquare(mouseX, mouseY, x, y);
                drawCross(extractor, x, y, active ? ICON_ACTIVE : ICON);
        }

        /** A cross of two 2-pixel diagonals, the strip's chunky 1-pixel idiom. */
        private static void drawCross(GuiGraphicsExtractor extractor, int x, int y, int color) {
                for (int i = 0; i < 5; i++) {
                        extractor.fill(x + 2 + i, y + 2 + i, x + 4 + i, y + 3 + i, color);
                        extractor.fill(x + 6 - i, y + 2 + i, x + 8 - i, y + 3 + i, color);
                }
        }

        private Tooltip deleteTooltipIfHovered(int hoveredRow, int mouseX, int mouseY) {
                if (hoveredRow == 0 && mouseInSquare(mouseX, mouseY, closeButtonX(), buttonY(0))) {
                        return Tooltip.create(Component.translatable("zea.chat.trade.queue.close"));
                }
                if (hoveredRow >= 2 && !rows.isEmpty()
                                        && mouseInSquare(mouseX, mouseY, closeButtonX(), buttonY(hoveredRow))) {
                        return Tooltip.create(Component.translatable("zea.chat.trade.queue.delete"));
                }
                return null;
        }

        private boolean mouseInSquare(double mouseX, double mouseY, int x, int y) {
                return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
        }

        /** The row under a point, or -1: 0 header, 1 auto-send, 2+ messages. */
        private int rowAt(double mouseX, double mouseY) {
                if (!super.isMouseOver(mouseX, mouseY)) {
                        return -1;
                }
                int row = (int) ((mouseY - getY()) / ROW_HEIGHT);
                return row >= 0 && row < rowCount() ? row : -1;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                        return;
                }
                int row = rowAt(event.x(), event.y());
                if (row == 0) {
                        if (mouseInSquare(event.x(), event.y(), closeButtonX(), buttonY(0))) {
                                onClose.run();
                        }
                        return;
                }
                if (row == 1) {
                        onToggleAutoSend.run();
                        return;
                }
                if (row >= 2) {
                        int index = row - 2;
                        if (mouseInSquare(event.x(), event.y(), closeButtonX(), buttonY(row))) {
                                onRemoveQueued.accept(index);
                        }
                }
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
                // Quiet like the tab strip and the action buttons.
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
        protected void updateWidgetNarration(NarrationElementOutput output) {
                output.add(NarratedElementType.TITLE, createNarrationMessage());
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
                return Component.translatable("zea.chat.trade.queue.title");
        }
}
