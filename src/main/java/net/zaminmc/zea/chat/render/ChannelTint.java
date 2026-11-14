package net.zaminmc.zea.chat.render;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.zaminmc.zea.chat.model.MessageCategory;

import java.util.Set;

/**
 * Tints the text of received channel messages slightly yellow — the
 * dev.26 live request: on the General tab the player reads messages from
 * Global, Local, Town, Nation and Trade side by side with system and
 * event lines, and the tint marks which ones arrived in a chat channel.
 * Messages outside the writable channels keep their current colors, and
 * mention highlighting (the gold line background) is unaffected: the tint
 * changes text color only.
 *
 * <p>The tint is a <b>blend</b>, not a repaint: every run's effective
 * color is moved partway toward warm yellow, so rank colors and name
 * colors inside a channel message stay distinguishable while the whole
 * line picks up the cast. Runs without an explicit color blend from
 * white, which is what they render as.
 *
 * <p>Styles are resolved exactly as vanilla resolves them at render time
 * ({@code Component.visit}: a node's own style applies over its parent's
 * effective style, siblings inherit that) and then baked per node with
 * the color blended — so the rebuilt tree renders identically apart from
 * the colors: bold, italic, hover and click events all carry over. One
 * known limit, shared with every style-rewriting approach: components
 * hiding extra runs inside translatable arguments (never seen from
 * EarthMC chat) keep their argument styles untouched.
 *
 * <p>Pure and deterministic: same component in, same tinted copy out. The
 * caller applies it where chat lines are wrapped for display, never on
 * the stored message, so copying, mention identity and history are
 * untouched.
 */
public final class ChannelTint {
        /** The categories the tint applies to: the writable server channels. */
        public static final Set<MessageCategory> TINTED_CATEGORIES =
                        Set.of(MessageCategory.GLOBAL, MessageCategory.LOCAL, MessageCategory.TRADE,
                                        MessageCategory.NATION, MessageCategory.TOWN);
        /** The warm yellow every run's color blends toward. */
        static final int TINT_RGB = 0xFFE878;
        /** How much of the tint lands in the result (0 = none, 1 = full repaint). */
        static final float TINT_STRENGTH = 0.35F;
        /** Colorless runs render as white; that is the base they blend from. */
        private static final int DEFAULT_RGB = 0xFFFFFF;

        private ChannelTint() {
        }

        /**
         * A deep copy of the component tree with every run's effective
         * color blended toward the tint. The input component is never
         * modified.
         */
        public static MutableComponent tint(Component component) {
                return tintNode(component, Style.EMPTY);
        }

        private static MutableComponent tintNode(Component node, Style parentEffective) {
                Style effective = node.getStyle().applyTo(parentEffective);
                MutableComponent out = MutableComponent.create(node.getContents())
                                .setStyle(tintStyle(effective));
                for (Component sibling : node.getSiblings()) {
                        out.append(tintNode(sibling, effective));
                }
                return out;
        }

        /** One style with its color blended; everything else is untouched. */
        static Style tintStyle(Style style) {
                TextColor color = style.getColor();
                int blended = blend(color == null ? DEFAULT_RGB : color.getValue());
                return style.withColor(TextColor.fromRgb(blended));
        }

        /** Moves {@code rgb} partway toward the tint color, channel by channel. */
        static int blend(int rgb) {
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int tr = (TINT_RGB >> 16) & 0xFF;
                int tg = (TINT_RGB >> 8) & 0xFF;
                int tb = TINT_RGB & 0xFF;
                int nr = Math.round(r + (tr - r) * TINT_STRENGTH);
                int ng = Math.round(g + (tg - g) * TINT_STRENGTH);
                int nb = Math.round(b + (tb - b) * TINT_STRENGTH);
                return (nr << 16) | (ng << 8) | nb;
        }
}
