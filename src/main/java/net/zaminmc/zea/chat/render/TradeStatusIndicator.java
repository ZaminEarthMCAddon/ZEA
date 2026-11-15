package net.zaminmc.zea.chat.render;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.zaminmc.zea.chat.feeds.TradeGate;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The trade pacing status line: a small yellow "Trade 4:12 · 2 queued"
 * tag on its own row above the chat tab strip, right-aligned to the
 * screen. It appears whenever there is something to say — the
 * five-minute cooldown running, messages waiting for the auto-send
 * drain, or both — and clicking it toggles auto-send, so the player
 * never has to open the settings to change the one thing they would
 * want to change mid-conversation.
 *
 * <p>The label is recomputed every frame (the countdown moves), so the
 * widget hugs its text: width and x are re-set per frame from the
 * measured label, keeping the right edge fixed. Rendering speaks the
 * tab strip's language — one flat translucent box, yellow text — and
 * like every ZEA chat widget it is mouse-driven and refuses keyboard
 * focus navigation, so it can never steal the caret from the chat input.
 *
 * <p>Visibility is the chat screen's call, polled per frame: the widget
 * itself has no way back into rendering once invisible, so the screen
 * flips {@code visible} from its render path (the same per-frame hook
 * that watches the search field's focus).
 */
public final class TradeStatusIndicator extends AbstractWidget {
        /** The status row's height; it sits directly above the tab strip. */
        public static final int HEIGHT = ChatTabBar.TAB_HEIGHT;
        /** Inner padding each side of the text, the tab strip's density. */
        private static final int PADDING = 4;
        /** Vertical offset of the 9px text inside the 12px row — the tabs' centering. */
        private static final int TEXT_Y_OFFSET = 2;
        private static final int BACKGROUND = 0x66000000;

        private final Font font;
        private final BooleanSupplier pacingEnabled;
        private final Supplier<TradeGate.Snapshot> state;
        private final BooleanSupplier autoSend;
        private final Runnable onToggleAutoSend;
        private final int rightEdge;

        /**
         * @param rightEdge        the screen x the tag's right edge stays glued to
         * @param y                the row's top edge (the tab strip's top minus HEIGHT)
         * @param font             the text renderer that measures and draws the label
         * @param pacingEnabled    whether trade pacing is on at all
         * @param state            the gate's snapshot, sampled per frame
         * @param autoSend         whether auto-send is currently on (the tooltip shows it)
         * @param onToggleAutoSend run when the tag is clicked
         */
        public TradeStatusIndicator(int rightEdge, int y, Font font, BooleanSupplier pacingEnabled,
                        Supplier<TradeGate.Snapshot> state, BooleanSupplier autoSend, Runnable onToggleAutoSend) {
                super(rightEdge - 60, y, 60, HEIGHT, Component.translatable("zea.chat.trade.title"));
                if (font == null || pacingEnabled == null || state == null || autoSend == null
                                        || onToggleAutoSend == null) {
                        throw new NullPointerException("callbacks must not be null");
                }
                this.font = font;
                this.pacingEnabled = pacingEnabled;
                this.state = state;
                this.autoSend = autoSend;
                this.onToggleAutoSend = onToggleAutoSend;
                this.rightEdge = rightEdge;
                setHeight(HEIGHT);
        }

        /**
         * Whether the tag has anything to show right now — pacing on and a
         * running cooldown or waiting messages. The chat screen polls this
         * per frame and flips the widget's {@code visible}; the widget cannot
         * poll itself back to life once invisible.
         */
        public boolean shouldShow() {
                return pacingEnabled.getAsBoolean() && state.get().active();
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                        float partialTick) {
                TradeGate.Snapshot snapshot = state.get();
                Component label = label(snapshot);
                int width = font.width(label) + 2 * PADDING;
                setX(rightEdge - width);
                setWidth(width);
                extractor.fill(getX(), getY(), getX() + width, getY() + HEIGHT, BACKGROUND);
                extractor.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                                .accept(TextAlignment.LEFT, getX() + PADDING, getY() + TEXT_Y_OFFSET,
                                                label.copy().withStyle(ChatFormatting.YELLOW));
                if (isMouseOver(mouseX, mouseY)) {
                        setTooltip(Tooltip.create(
                                        Component.translatable("zea.chat.trade.tooltip").withStyle(ChatFormatting.YELLOW),
                                        Component.translatable(autoSendKey())));
                } else {
                        setTooltip(null);
                }
        }

        /** "Trade 4:12", "Trade 4:12 · 2 queued", or the ready form when only a queue waits. */
        private Component label(TradeGate.Snapshot snapshot) {
                Component remaining = snapshot.coolingDown()
                                ? Component.literal(TradeGate.formatRemaining(snapshot.remainingMillis()))
                                : Component.translatable("zea.chat.trade.ready");
                return snapshot.queued() > 0
                                ? Component.translatable("zea.chat.trade.status_queued", remaining, snapshot.queued())
                                : Component.translatable("zea.chat.trade.status", remaining);
        }

        private String autoSendKey() {
                return autoSend.getAsBoolean() ? "zea.chat.trade.auto_on" : "zea.chat.trade.auto_off";
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !shouldShow()) {
                        return;
                }
                onToggleAutoSend.run();
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

        @Override
        public boolean isMouseOver(double x, double y) {
                return shouldShow() && super.isMouseOver(x, y);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
                output.add(NarratedElementType.TITLE, label(state.get()));
        }
}
