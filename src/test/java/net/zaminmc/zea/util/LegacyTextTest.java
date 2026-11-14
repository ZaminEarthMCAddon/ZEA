package net.zaminmc.zea.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTextTest {
        @Test
        void plainFlattensSiblingText() {
                MutableComponent message = Component.literal("☠ ").withStyle(ChatFormatting.RED)
                                .append(Component.literal("xfw_x was killed by dex247").withStyle(ChatFormatting.GRAY));
                assertEquals("☠ xfw_x was killed by dex247", LegacyText.plain(message));
        }

        @Test
        void unstyledTextHasNoCodes() {
                assertEquals("hello", LegacyText.format(Component.literal("hello")));
        }

        @Test
        void styleChangesResetFirstLikeTheServerDoes() {
                MutableComponent message = Component.literal("red").withStyle(ChatFormatting.RED)
                                .append(Component.literal("gray").withStyle(ChatFormatting.GRAY));
                assertEquals("§cred§r§7gray", LegacyText.format(message));
        }

        @Test
        void flagsEmitAfterColorInStableOrder() {
                MutableComponent message = Component.literal("loud")
                                .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.UNDERLINE)
                                .withStyle(ChatFormatting.STRIKETHROUGH).withStyle(ChatFormatting.OBFUSCATED);
                assertEquals("§e§l§o§n§m§kloud", LegacyText.format(message));
        }

        @Test
        void styledRunFollowedByUnstyledSiblingResets() {
                MutableComponent message = Component.empty();
                message.append(Component.literal("gray").withStyle(ChatFormatting.GRAY));
                message.append(Component.literal("default"));
                assertEquals("§7gray§rdefault", LegacyText.format(message));
        }

        @Test
        void childrenInheritParentStylesWithoutExtraCodes() {
                // A child without its own style renders in the parent's style.
                MutableComponent message = Component.literal("gray").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal("inherited"));
                assertEquals("§7grayinherited", LegacyText.format(message));
        }

        @Test
        void unstyledRunBeforeStyledRunNeedsNoLeadingReset() {
                MutableComponent message = Component.literal("default")
                                .append(Component.literal("gray").withStyle(ChatFormatting.GRAY));
                assertEquals("default§7gray", LegacyText.format(message));
        }

        @Test
        void exoticColorsAreSkippedButTextKept() {
                MutableComponent message = Component.literal("fancy")
                                .withStyle(s -> s.withColor(0x123456));
                assertEquals("fancy", LegacyText.format(message));
        }

        @Test
        void deathMessageMatchesTheObservedSegmentStyle() {
                // Observed on EarthMC: §c☠ §r§7xfw_x was killed by dex247§r
                // (the trailing §r is a terminator; runs need no trailing reset)
                MutableComponent message = Component.literal("☠ ").withStyle(ChatFormatting.RED)
                                .append(Component.literal("xfw_x was killed by dex247").withStyle(ChatFormatting.GRAY));
                assertEquals("§c☠ §r§7xfw_x was killed by dex247", LegacyText.format(message));
        }

        @Test
        void repeatedCallsAreIndependent() {
                MutableComponent message = Component.empty();
                message.append(Component.literal("bold").withStyle(ChatFormatting.BOLD));
                message.append(Component.literal("plain"));
                assertEquals("§lbold§rplain", LegacyText.format(message));
                assertEquals("§lbold§rplain", LegacyText.format(message));
        }

        @Test
        void parseSplitsRunsByColorCodes() {
                MutableComponent parsed = LegacyText.parse("&bZamin's EarthMC Addon&7 - Created by &eAbasing");

                assertEquals("Zamin's EarthMC Addon - Created by Abasing", parsed.getString());
                assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), firstRun(parsed).getStyle().getColor());
                assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY),
                                parsed.getSiblings().get(1).getStyle().getColor());
                assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW),
                                parsed.getSiblings().get(2).getStyle().getColor());
        }

        @Test
        void parseHonorsSectionMarkerToo() {
                MutableComponent parsed = LegacyText.parse("§ahi§bw");
                assertEquals("hiw", parsed.getString());
                assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), firstRun(parsed).getStyle().getColor());
                assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA),
                                parsed.getSiblings().get(1).getStyle().getColor());
        }

        @Test
        void parseAppliesFormatFlags() {
                MutableComponent parsed = LegacyText.parse("&lbold&rother");
                assertEquals("boldother", parsed.getString());
                assertTrue(firstRun(parsed).getStyle().isBold(), "&l must bold the run after it");
                // The reset clears the flag for the run after it.
                assertFalse(parsed.getSiblings().get(1).getStyle().isBold() == true);
                assertNull(parsed.getSiblings().get(1).getStyle().getColor());
        }

        @Test
        void unknownMarkerStaysLiteral() {
                // "&x" is not a code: the marker survives as text.
                assertEquals("a&xb", LegacyText.parse("a&xb").getString());
                assertEquals("a&", LegacyText.parse("a&").getString());
        }

        @Test
        void emptyAndNullParseToEmpty() {
                assertEquals("", LegacyText.parse("").getString());
                assertEquals("", LegacyText.parse(null).getString());
        }

        @Test
        void uppercaseCodesParseLikeLowercase() {
                assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA),
                                firstRun(LegacyText.parse("&Bhi")).getStyle().getColor());
        }

        private static MutableComponent firstRun(MutableComponent component) {
                return (MutableComponent) component.getSiblings().get(0);
        }
}
