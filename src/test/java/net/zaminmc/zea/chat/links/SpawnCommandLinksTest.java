package net.zaminmc.zea.chat.links;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Click-to-visit spawn commands, against the exact live examples. The
 * visible text must never change; only the command substring gains the
 * click event; existing interactions are never overwritten.
 */
class SpawnCommandLinksTest {
        private static String clickedCommand(Component message) {
                String[] found = {null};
                message.visit((style, text) -> {
                        if (style.getClickEvent() instanceof ClickEvent.RunCommand run) {
                                found[0] = run.command();
                        }
                        return java.util.Optional.empty();
                }, Style.EMPTY);
                return found[0];
        }

        /** The first position whose run carries a click event, or -1. */
        private static int firstClickPosition(Component message) {
                int[] cursor = {0};
                int[] found = {-1};
                message.visit((style, text) -> {
                        if (found[0] < 0 && style.getClickEvent() != null && !text.isEmpty()) {
                                found[0] = cursor[0];
                        }
                        cursor[0] += text.length();
                        return java.util.Optional.empty();
                }, Style.EMPTY);
                return found[0];
        }

        @Test
        void nationSpawnExampleBecomesClickable() {
                Component linked = SpawnCommandLinks.link(Component.literal(
                                "🌎 [France|Anjou] Karakan: Nansel54 do /n spawn Anjou"));
                assertEquals("🌎 [France|Anjou] Karakan: Nansel54 do /n spawn Anjou", linked.getString(),
                                "the visible text never changes");
                assertEquals("/n spawn Anjou", clickedCommand(linked));
        }

        @Test
        void townSpawnExampleBecomesClickable() {
                Component linked = SpawnCommandLinks.link(Component.literal(
                                "[Trade] Unbidleaf: Public Trial Chamber at /t spawn Wonsan"));
                assertEquals("[Trade] Unbidleaf: Public Trial Chamber at /t spawn Wonsan", linked.getString());
                assertEquals("/t spawn Wonsan", clickedCommand(linked));
        }

        @Test
        void uppercaseCommandDispatchesEquivalentCommand() {
                Component linked = SpawnCommandLinks.link(Component.literal(
                                "[Trade] Unknown: 3G MENDING AT /N SPAWN VAYGACH"));
                assertEquals("[Trade] Unknown: 3G MENDING AT /N SPAWN VAYGACH", linked.getString());
                assertEquals("/n spawn VAYGACH", clickedCommand(linked),
                                "the target stays exactly as written; the command word normalizes");
        }

        @Test
        void trailingPunctuationIsNotPartOfTheCommand() {
                Component linked = SpawnCommandLinks.link(Component.literal(
                                "do /t spawn Wonsan. then come back"));
                assertEquals("/t spawn Wonsan", clickedCommand(linked));
        }

        @Test
        void onlyTheCommandSubstringIsClickable() {
                String text = "[Trade] Unbidleaf: Public Trial Chamber at /t spawn Wonsan";
                Component linked = SpawnCommandLinks.link(Component.literal(text));
                int commandAt = text.indexOf("/t spawn");
                assertEquals(commandAt, firstClickPosition(linked),
                                "the click starts exactly at the command, not the prose");
                assertEquals(commandAt + "/t spawn Wonsan".length(), lastClickEndOffset(linked),
                                "the click ends exactly where the command ends");
        }

        /** The offset one past the last clicked character. */
        private static int lastClickEndOffset(Component message) {
                int[] cursor = {0};
                int[] found = {-1};
                message.visit((style, text) -> {
                        if (style.getClickEvent() != null && !text.isEmpty()) {
                                found[0] = cursor[0] + text.length();
                        }
                        cursor[0] += text.length();
                        return java.util.Optional.empty();
                }, Style.EMPTY);
                return found[0];
        }

        @Test
        void formattingIsPreservedAcrossPerCharacterRuns() {
                // EarthMC formats per character: each letter its own styled run.
                Component original = Component.empty()
                                .append(Component.literal("come to ").withStyle(net.minecraft.ChatFormatting.YELLOW))
                                .append(Component.literal("/").withStyle(net.minecraft.ChatFormatting.RED))
                                .append(Component.literal("n").withStyle(net.minecraft.ChatFormatting.AQUA))
                                .append(Component.literal(" ").withStyle(net.minecraft.ChatFormatting.GRAY))
                                .append(Component.literal("spawn").withStyle(net.minecraft.ChatFormatting.GREEN))
                                .append(Component.literal(" Anjou").withStyle(net.minecraft.ChatFormatting.GOLD));
                Component linked = SpawnCommandLinks.link(original);
                assertEquals(original.getString(), linked.getString(), "text identical");
                assertEquals("/n spawn Anjou", clickedCommand(linked),
                                "a command split across runs still links");
                assertEquals(6, linked.getSiblings().size(),
                                "the styled runs are preserved one for one");
        }

        @Test
        void existingClickEventsAreNeverOverwritten() {
                Style withLink = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/existing"));
                Component original = Component.empty()
                                .append(Component.literal("check "))
                                .append(Component.literal("/n spawn Anjou").withStyle(withLink));
                assertSame(original, SpawnCommandLinks.link(original),
                                "an interactive message is returned untouched");
        }

        @Test
        void interactionsOutsideTheCommandSurvive() {
                Style withHover = Style.EMPTY.withHoverEvent(
                                new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("preview")));
                Component original = Component.empty()
                                .append(Component.literal("look ").withStyle(withHover))
                                .append(Component.literal("then /n spawn Anjou"));
                Component linked = SpawnCommandLinks.link(original);
                assertEquals("look then /n spawn Anjou", linked.getString());
                boolean hoverSurvived = false;
                boolean clickAdded = false;
                for (Component sibling : linked.getSiblings()) {
                        if (sibling.getStyle().getHoverEvent() != null) {
                                hoverSurvived = true;
                        }
                        if (sibling.getStyle().getClickEvent() != null) {
                                clickAdded = true;
                        }
                }
                assertTrue(hoverSurvived, "the unrelated hover event keeps working");
                assertTrue(clickAdded, "the spawn command still links");
        }

        @Test
        void urlsAndOtherCommandsAreNotLinked() {
                Component url = Component.literal("see https://example.com/t spawn fake");
                assertSame(url, SpawnCommandLinks.link(url),
                                "a URL is prose, not a spawn command");
                Component res = Component.literal("run /res spawn Somewhere please");
                assertSame(res, SpawnCommandLinks.link(res),
                                "only /n and /t spawn link");
        }

        @Test
        void messagesWithoutSpawnCommandsAreUntouched() {
                Component message = Component.literal("hello world");
                assertSame(message, SpawnCommandLinks.link(message));
        }

        @Test
        void dispatchCommandNormalizesOnlyTheCommandWord() {
                assertEquals("/n spawn Anjou", SpawnCommandLinks.dispatchCommand("N", "Anjou"));
                assertEquals("/t spawn VAYGACH", SpawnCommandLinks.dispatchCommand("T", "VAYGACH"));
        }

        @Test
        void containsSpawnCommandDetectsTheShape() {
                assertTrue(SpawnCommandLinks.containsSpawnCommand("do /n spawn Anjou"));
                assertTrue(SpawnCommandLinks.containsSpawnCommand("/T SPAWN WONSAN"));
                assertFalse(SpawnCommandLinks.containsSpawnCommand("no commands here"));
                assertFalse(SpawnCommandLinks.containsSpawnCommand(""));
                assertFalse(SpawnCommandLinks.containsSpawnCommand(null));
        }
}
