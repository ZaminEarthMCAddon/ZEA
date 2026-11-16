package net.zaminmc.zea.chat.links;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Click-to-visit navigation for spawn commands in chat: players
 * advertise locations as {@code /n spawn Anjou} or {@code /t spawn
 * Wonsan}, and typing those by hand is exactly the friction this
 * removes. When the feature is on, the actual command substring of an
 * incoming message becomes clickable — clicking dispatches the command
 * through Minecraft's normal command pathway (a {@link
 * ClickEvent.RunCommand}, never prefixed by a channel command) and the
 * hover explains what a click does. The visible text and formatting are
 * preserved verbatim: nothing about the message's appearance changes,
 * so a link is discovered by hovering, never by a re-colored line.
 *
 * <p>Recognition is case-insensitive and anchored to the command shape —
 * {@code /n spawn <nation>} and {@code /t spawn <town>} — so trailing
 * punctuation, surrounding prose, URLs and other commands never become
 * links. The clicked command keeps the target exactly as the player
 * wrote it ({@code /N SPAWN VAYGACH} dispatches {@code /n spawn
 * VAYGACH}) and normalizes only the command word, which is what the
 * server's command parser is case-insensitive about.
 *
 * <p>Rebuild discipline: the message is flattened into styled leaf runs
 * ({@link Component#visit} yields each run with its accumulated style)
 * and reassembled run by run, every run keeping its style verbatim —
 * colors, bold, italics, and any hover, click or insertion another part
 * of the message already carries. Only the runs inside a spawn command,
 * which by construction carried no interaction of their own, gain the
 * click and hover; a command embedded in an interactive run (an item
 * preview's hover, another mod's link) is left unlinked rather than
 * overwriting someone else's event. This also handles commands split
 * across several per-character formatting components — EarthMC writes
 * exactly that way.
 */
public final class SpawnCommandLinks {
        /**
         * The navigation commands recognized: nation ({@code /n}) and town
         * ({@code /t}) spawns, case-insensitive, with the target run made
         * of the characters EarthMC town and nation names use. The target
         * stops at punctuation, so {@code /t spawn Wonsan.} links the
         * command without the period.
         */
        private static final Pattern SPAWN_COMMAND = Pattern.compile(
                        "(?<!\\S)/([nt])[ \\t]+spawn[ \\t]+([A-Za-z0-9_\\-]{1,40})",
                        Pattern.CASE_INSENSITIVE);

        private SpawnCommandLinks() {
        }

        /** One recognized spawn command inside the flattened message text. */
        record Match(int start, int end, String typeLetter, String target) {
        }

        /**
         * The dispatchable command for a match: the command word normalized
         * to lowercase, the target exactly as the message wrote it.
         */
        public static String dispatchCommand(String typeLetter, String target) {
                return "/" + typeLetter.toLowerCase(Locale.ROOT) + " spawn " + target;
        }

        /** Whether the flattened message text contains a spawn command worth linking. */
        public static boolean containsSpawnCommand(String flatText) {
                return flatText != null && SPAWN_COMMAND.matcher(flatText).find();
        }

        /**
         * The message with its spawn commands made clickable. Returns the
         * original instance unchanged when the message has no spawn
         * command — and never mutates the instance it was given.
         */
        public static Component link(Component message) {
                if (message == null) {
                        return null;
                }
                List<Run> runs = flatten(message);
                if (runs.isEmpty()) {
                        return message;
                }
                String flat = flatText(runs);
                List<Match> matches = findMatches(flat);
                if (matches.isEmpty()) {
                        return message;
                }
                MutableComponent rebuilt = Component.empty();
                int position = 0;
                boolean linkedAny = false;
                for (Match match : matches) {
                        if (rangeHasInteraction(runs, match.start(), match.end())) {
                                // Never overwrite an interaction somebody else
                                // put there; this command stays plain text.
                                continue;
                        }
                        appendRange(rebuilt, runs, position, match.start(), null);
                        appendRange(rebuilt, runs, match.start(), match.end(),
                                        dispatchCommand(match.typeLetter(), match.target()));
                        position = match.end();
                        linkedAny = true;
                }
                if (!linkedAny) {
                        // Every match was protected by an existing event: the
                        // message must come out untouched.
                        return message;
                }
                appendRange(rebuilt, runs, position, flat.length(), null);
                return rebuilt;
        }

        /** One styled run of the flattened message. */
        private record Run(Style style, String text) {
        }

        /** The message flattened into leaf runs with accumulated styles. */
        private static List<Run> flatten(Component message) {
                List<Run> runs = new ArrayList<>();
                message.visit((style, text) -> {
                        if (!text.isEmpty()) {
                                runs.add(new Run(style, text));
                        }
                        return Optional.empty();
                }, Style.EMPTY);
                return runs;
        }

        private static String flatText(List<Run> runs) {
                StringBuilder out = new StringBuilder();
                for (Run run : runs) {
                        out.append(run.text());
                }
                return out.toString();
        }

        /** Every spawn command in the flattened text, in order. */
        private static List<Match> findMatches(String flat) {
                List<Match> matches = new ArrayList<>();
                Matcher matcher = SPAWN_COMMAND.matcher(flat);
                while (matcher.find()) {
                        matches.add(new Match(matcher.start(), matcher.end(),
                                        matcher.group(1), matcher.group(2)));
                }
                return matches;
        }

        /** Whether any run overlapping [from, to) carries a click, hover or insertion. */
        private static boolean rangeHasInteraction(List<Run> runs, int from, int to) {
                int cursor = 0;
                for (Run run : runs) {
                        int runStart = cursor;
                        int runEnd = cursor + run.text().length();
                        cursor = runEnd;
                        if (runEnd <= from || runStart >= to) {
                                continue;
                        }
                        Style style = run.style();
                        if (style.getClickEvent() != null || style.getHoverEvent() != null
                                        || style.getInsertion() != null) {
                                return true;
                        }
                }
                return false;
        }

        /**
         * Appends the runs covering [from, to) to the rebuilt message,
         * each piece with its run's own style — plus the click and hover
         * when {@code command} is set. Splitting follows run boundaries,
         * so EarthMC's per-character formatting keeps its exact looks.
         */
        private static void appendRange(MutableComponent root, List<Run> runs, int from, int to, String command) {
                if (to <= from) {
                        return;
                }
                int cursor = 0;
                for (Run run : runs) {
                        int runStart = cursor;
                        int runEnd = cursor + run.text().length();
                        cursor = runEnd;
                        int start = Math.max(runStart, from);
                        int end = Math.min(runEnd, to);
                        if (end <= start) {
                                continue;
                        }
                        String piece = run.text().substring(start - runStart, end - runStart);
                        if (command == null) {
                                root.append(Component.literal(piece).withStyle(run.style()));
                        } else {
                                root.append(Component.literal(piece).withStyle(linkedStyle(run.style(), command)));
                        }
                }
        }

        /** The original style plus the click and hover; nothing visual changes. */
        private static Style linkedStyle(Style base, String command) {
                return base
                                .withClickEvent(new ClickEvent.RunCommand(command))
                                .withHoverEvent(new HoverEvent.ShowText(
                                                Component.translatable("zea.chat.click_to_visit", command)));
        }
}
