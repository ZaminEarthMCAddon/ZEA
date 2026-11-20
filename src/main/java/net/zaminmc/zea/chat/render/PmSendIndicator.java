package net.zaminmc.zea.chat.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.zaminmc.zea.chat.feeds.PrivateMessaging;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The {@code Send to <player>} tag on the chat input's row, at its right
 * end, shown while the PMs tab is selected. It answers the one question
 * the PMs tab leaves open — <em>who gets this message?</em> — before it
 * is sent, instead of after: the tab routes a plain message to
 * {@code /r}, and the reply target moves with every PM exchange in
 * either direction, so the tag names {@link PrivateMessaging#partner()}
 * live. Yellow, the selected tab's own color.
 *
 * <p>While no reply target is known — a fresh join, before any exchange —
 * the tag shows the session's recent frequent partners instead:
 * {@code Recent: a, b, c}, gray, the partners of a real back-and-forth
 * ({@value PrivateMessaging#FREQUENT_EXCHANGES}+ exchanges), most recent
 * first. A quiet memory of who has been in touch, not a send target —
 * nothing has been said yet, so nothing claims where it would go.
 * One-off pings never appear; with no contacts the tag shows nothing at
 * all and the row reads exactly like vanilla.
 *
 * <p>The tag is text, not a widget surface: it never consumes a click,
 * never takes focus, never speaks a sound — it sits beside the input the
 * way the tab strip sits above it, plain text in the vanilla palette,
 * and steps aside silently when it would not fit the screen.
 *
 * <p>Visibility is the chat screen's call, polled per frame (the same
 * per-frame hook that watches the search field's focus); the widget
 * hugs its label like the trade status line, re-measuring per frame so
 * a long name grows the tag leftward-anchored at the input's edge.
 */
public final class PmSendIndicator extends AbstractWidget {
        /** Breathing room between the input's right edge and the tag's text. */
        public static final int GAP = 5;
        /** Horizontal padding each side of the text, the tab strip's density. */
        private static final int PADDING = 4;
        /** Right margin the tag never crosses; past it the tag simply stays away. */
        private static final int RIGHT_MARGIN = 2;
        /** Vertical offset of the 9px text inside the input row, the tabs' centering. */
        private static final int TEXT_Y_OFFSET = 2;

        private final Font font;
        private final Supplier<PrivateMessaging> pms;
        private final BooleanSupplier pmsTabSelected;
        private final int screenWidth;

        /**
         * @param left           the x the tag's text starts at — the input's right edge plus a gap
         * @param y              the input row's top edge
         * @param height         the input row's height; the text centers inside it
         * @param font           the text renderer that measures and draws the label
         * @param pms            the session's private-message state, sampled per frame
         * @param pmsTabSelected whether the PMs tab is the selected feed right now
         * @param screenWidth    the screen's width; the tag refuses to cross its margin
         */
        public PmSendIndicator(int left, int y, int height, Font font, Supplier<PrivateMessaging> pms,
                        BooleanSupplier pmsTabSelected, int screenWidth) {
                super(left, y, 0, height, Component.translatable("zea.chat.pm.title"));
                if (font == null || pms == null || pmsTabSelected == null) {
                        throw new NullPointerException("callbacks must not be null");
                }
                this.font = font;
                this.pms = pms;
                this.pmsTabSelected = pmsTabSelected;
                this.screenWidth = screenWidth;
        }

        /**
         * Whether the tag has anything to say right now — the PMs tab is
         * selected and there is a partner to name or recent partners to
         * recall. The chat screen polls this per frame and flips the
         * widget's {@code visible}; the widget cannot poll itself back to
         * life once invisible.
         */
        public boolean shouldShow() {
                return pmsTabSelected.getAsBoolean() && content() != null;
        }

        /** The tag's text and color this frame, or null while there is nothing to show. */
        private Content content() {
                PrivateMessaging state = pms.get();
                if (state == null) {
                        return null;
                }
                Optional<String> partner = state.partner();
                if (partner.isPresent()) {
                        return new Content("Send to " + partner.get(), ChatFormatting.YELLOW);
                }
                List<PrivateMessaging.Contact> recent = state.recentContacts();
                if (recent.isEmpty()) {
                        return null;
                }
                String names = recent.stream()
                                .map(PrivateMessaging.Contact::name)
                                .collect(Collectors.joining(", "));
                return new Content("Recent: " + names, ChatFormatting.GRAY);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                        float partialTick) {
                Content content = content();
                if (content == null) {
                        return;
                }
                Component label = Component.literal(content.text()).withStyle(content.color());
                setWidth(font.width(label) + 2 * PADDING);
                if (getX() + getWidth() > screenWidth - RIGHT_MARGIN) {
                        // A name that does not fit the screen says nothing
                        // rather than half of itself.
                        return;
                }
                extractor.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                                .accept(TextAlignment.LEFT, getX() + PADDING, getY() + TEXT_Y_OFFSET, label);
        }

        /** Text and color, resolved per frame from the PM state. */
        private record Content(String text, ChatFormatting color) {
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
                // Quiet like the tab strip: text does not speak.
        }

        /** Text is not a click target; see the class javadoc. */
        @Override
        public boolean isMouseOver(double x, double y) {
                return false;
        }

        /** Never a keyboard-navigation target; see the class javadoc. */
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
                return null;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
                Content content = content();
                if (content != null) {
                        output.add(NarratedElementType.TITLE, Component.literal(content.text()));
                }
        }
}
