package net.zaminmc.zea.slots;

import net.zaminmc.zea.persistence.JsonStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and saves slot locks and bindings through the safe-write
 * protocol. The file holds one entry per server context; each context
 * carries two parallel arrays indexed by the player-inventory container
 * slot (0-8 hotbar, 9-35 main, 36-39 armor, 40 off-hand): whether the
 * slot is locked, and which hotbar slot it is bound to ({@code -1} for
 * none).
 *
 * <p>Per-context separation is the modern reading of NEU's per-profile
 * and Rift/Overworld lock separation, adapted to ZEA: EarthMC runs
 * several servers whose inventories are unrelated, so a lock belongs to
 * the server it was made on. {@code generic} covers menus and
 * singleplayer. A damaged context yields empty arrays for that context
 * only; one bad entry never takes the file down.
 */
public final class SlotLockStore {
        private static final Logger LOGGER = LoggerFactory.getLogger(SlotLockStore.class);

        /** Bump when the file shape changes; add the matching migration below. */
        public static final int CURRENT_VERSION = 1;

        /** How many player-inventory slots the model covers (0-40 inclusive). */
        public static final int SLOT_COUNT = 41;

        private final Path file;
        private final JsonStore json = new JsonStore();

        public SlotLockStore(Path file) {
                this.file = file;
        }

        /** The lock and bind state of one server context. */
        public record ContextState(boolean[] locked, int[] boundTo) {
                public ContextState {
                        locked = locked == null ? new boolean[SLOT_COUNT] : fit(locked);
                        boundTo = boundTo == null ? filled(SLOT_COUNT) : fit(boundTo);
                }

                private static boolean[] fit(boolean[] values) {
                        boolean[] out = new boolean[SLOT_COUNT];
                        System.arraycopy(values, 0, out, 0, Math.min(values.length, SLOT_COUNT));
                        return out;
                }

                private static int[] fit(int[] values) {
                        int[] out = filled(SLOT_COUNT);
                        System.arraycopy(values, 0, out, 0, Math.min(values.length, SLOT_COUNT));
                        return out;
                }

                private static int[] filled(int count) {
                        int[] out = new int[count];
                        java.util.Arrays.fill(out, -1);
                        return out;
                }
        }

        /** The file shape on disk. */
        private record LockFile(int version, Map<String, ContextState> contexts) {
                LockFile {
                        contexts = contexts == null ? new LinkedHashMap<>() : contexts;
                }
        }

        /** Loads every context; a missing or corrupt file yields an empty map. */
        public Map<String, ContextState> load() {
                LockFile data = json.load(file, LockFile.class, () -> new LockFile(CURRENT_VERSION, new LinkedHashMap<>()));
                if (data.version() < 1) {
                        LOGGER.warn("Slot lock file carries no version; treating it as version 1");
                }
                return data.contexts();
        }

        /** Saves every context. */
        public void save(Map<String, ContextState> contexts) {
                try {
                        json.save(file, new LockFile(CURRENT_VERSION, new LinkedHashMap<>(contexts)));
                } catch (IOException e) {
                        LOGGER.warn("Could not save slot locks {}", file, e);
                }
        }

        /** The empty context (the default for a context that has never been edited). */
        public static ContextState emptyState() {
                return new ContextState(null, null);
        }

        /** Convenience: contexts as a mutable map with an empty state for a key. */
        public static Map<String, ContextState> mutableCopy(Map<String, ContextState> loaded) {
                Map<String, ContextState> out = new LinkedHashMap<>();
                List<String> broken = new ArrayList<>();
                loaded.forEach((key, state) -> {
                        if (key == null || key.isBlank()) {
                                broken.add(key);
                        } else {
                                out.put(key, state == null ? emptyState() : state);
                        }
                });
                if (!broken.isEmpty()) {
                        LOGGER.warn("Dropping {} damaged slot lock context entries", broken.size());
                }
                return out;
        }
}
