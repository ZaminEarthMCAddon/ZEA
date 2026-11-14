package net.zaminmc.zea.chat.render;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;
import net.zaminmc.zea.VanillaBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slight-yellow channel tint: every run's effective color blends
 * toward warm yellow, uncolored runs blend from white, the input is never
 * modified, and the rest of the style survives.
 */
class ChannelTintTest {
        private static final int TINT = ChannelTint.TINT_RGB;

        @Test
        void colorlessRunsBlendFromWhite() {
                VanillaBootstrap.ensure();
                MutableComponent tinted = ChannelTint.tint(Component.literal("hello"));

                assertEquals(blend(0xFFFFFF), rgbOf(tinted.getStyle().getColor()));
                assertEquals("hello", tinted.getString());
        }

        @Test
        void coloredRunsBlendTowardTheTint() {
                VanillaBootstrap.ensure();
                MutableComponent tinted = ChannelTint.tint(
                                Component.literal("name").withStyle(ChatFormatting.GRAY));

                assertEquals(blend(colorOf(ChatFormatting.GRAY)), rgbOf(tinted.getStyle().getColor()));
        }

        @Test
        void blendIsAShiftNotARepaint() {
                // Distant colors stay distinguishable: gray blends to warm
                // gray, yellow stays yellow-ish — never one flat color.
                int fromGray = blend(colorOf(ChatFormatting.GRAY));
                int fromYellow = blend(colorOf(ChatFormatting.YELLOW));
                assertNotEquals(fromGray, fromYellow);
                // And the blend is partial: the tint never fully wins.
                assertTrue(fromGray != TINT && fromYellow != TINT);
        }

        @Test
        void inputComponentIsNeverModified() {
                VanillaBootstrap.ensure();
                MutableComponent original = Component.literal("player")
                                .withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(" hello there"));

                ChannelTint.tint(original);

                assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), original.getStyle().getColor(),
                                "tinting must not touch the stored message");
        }

        @Test
        void siblingsKeepTheirOwnBlendedColors() {
                VanillaBootstrap.ensure();
                MutableComponent message = Component.literal("player").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(" hi").withStyle(ChatFormatting.AQUA));

                MutableComponent tinted = ChannelTint.tint(message);

                assertEquals(blend(colorOf(ChatFormatting.GRAY)), rgbOf(tinted.getStyle().getColor()));
                assertEquals(blend(colorOf(ChatFormatting.AQUA)),
                                rgbOf(tinted.getSiblings().get(0).getStyle().getColor()));
                assertEquals("player hi", tinted.getString());
        }

        @Test
        void inheritedColorsResolveBeforeBlending() {
                // A child without its own color renders in the parent's color;
                // the tint must hit that effective color, not white.
                VanillaBootstrap.ensure();
                MutableComponent parent = Component.literal("gray").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("inherited"));

                MutableComponent tinted = ChannelTint.tint(parent);

                assertEquals(blend(colorOf(ChatFormatting.GRAY)), rgbOf(tinted.getSiblings().get(0).getStyle().getColor()),
                                "the colorless child inherits the parent's blended color");
        }

        @Test
        void nonColorStyleSurvivesTheTint() {
                VanillaBootstrap.ensure();
                MutableComponent tinted = ChannelTint.tint(
                                Component.literal("loud").withStyle(ChatFormatting.BOLD));

                assertTrue(tinted.getStyle().isBold());
        }

        @Test
        void tintIsDeterministic() {
                VanillaBootstrap.ensure();
                MutableComponent message = Component.literal("same").withStyle(ChatFormatting.GRAY);

                assertEquals(ChannelTint.tint(message).getStyle().getColor(),
                                ChannelTint.tint(message).getStyle().getColor(),
                                "same input, same tint — the wrap memo relies on it");
        }

        private static int colorOf(ChatFormatting formatting) {
                return TextColor.fromLegacyFormat(formatting).getValue();
        }

        private static int rgbOf(TextColor color) {
                return color.getValue();
        }

        private static int blend(int rgb) {
                return ChannelTint.blend(rgb);
        }
}
