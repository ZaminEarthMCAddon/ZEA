package net.zaminmc.zea.market.model;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The spawn reference of a shop: the town or nation spawn command a
 * player runs to reach the shop, in the form the player typed it.
 *
 * <p>The completion screen accepts the shapes a player actually types at
 * a keyboard — {@code /n spawn france}, {@code n spawn france},
 * {@code /t spawn paris}, {@code spawn paris}, {@code france} — and
 * normalizes each to the canonical command form: {@code /n spawn france}
 * or {@code /t spawn paris}. A bare name is a town spawn; the nation
 * spelling needs its letter or word. Anything without a name, or with a
 * name too long to be one, is declined — {@code null}, never a guess.
 */
public final class SpawnRef {
        /** The longest town or nation name accepted. */
        private static final int MAX_NAME_LENGTH = 48;

        private SpawnRef() {
        }

        /** The canonical nation-spawn reference for a resolved nation name. */
        public static String ofNation(String nation) {
                return "/n spawn " + nation.trim();
        }

        /** The canonical town-spawn reference for a resolved town name. */
        public static String ofTown(String town) {
                return "/t spawn " + town.trim();
        }

        /**
         * The canonical spawn command the typed input stands for, or
         * {@code null} when the input is not a spawn reference.
         */
        public static @Nullable String normalize(String input) {
                if (input == null) {
                        return null;
                }
                String text = input.trim();
                if (text.startsWith("/")) {
                        text = text.substring(1).trim();
                }
                if (text.isEmpty()) {
                        return null;
                }
                String lowered = text.toLowerCase(Locale.ROOT);
                // "<n|nation|t|town|spawn> <name>" — the spawn word may carry
                // its own name, so the longest matching head wins.
                String[] heads = {"nation spawn ", "town spawn ", "n spawn ", "t spawn ", "spawn "};
                for (String head : heads) {
                        if (lowered.startsWith(head)) {
                                return ref(head, text.substring(head.length()));
                        }
                }
                // A bare name: the town spawn is the default way in. A bare
                // command head with no name — "/n spawn", "/spawn" — is not
                // a name, and a name that ends in one is not one either.
                for (String head : new String[] {"nation spawn", "town spawn", "n spawn", "t spawn", "spawn"}) {
                        if (lowered.equals(head) || lowered.endsWith(" " + head)) {
                                return null;
                        }
                }
                return ref("spawn ", text);
        }

        /** The canonical command for the parsed name, or {@code null} when the name is not one. */
        private static @Nullable String ref(String head, String name) {
                String trimmed = name.trim();
                if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
                        return null;
                }
                boolean nation = head.startsWith("n ") || head.startsWith("nation ");
                return (nation ? "/n spawn " : "/t spawn ") + trimmed;
        }

        /** The spawn's display name — {@code France} from {@code /n spawn France}. */
        public static String display(String reference) {
                String text = reference.trim();
                if (text.startsWith("/")) {
                        text = text.substring(1);
                }
                String lowered = text.toLowerCase(Locale.ROOT);
                for (String head : new String[] {"nation spawn ", "town spawn ", "n spawn ", "t spawn ", "spawn "}) {
                        if (lowered.startsWith(head)) {
                                return text.substring(head.length()).trim();
                        }
                }
                return text;
        }
}
