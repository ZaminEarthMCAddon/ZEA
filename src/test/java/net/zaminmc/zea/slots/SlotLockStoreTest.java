package net.zaminmc.zea.slots;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotLockStoreTest {
        @TempDir
        Path tempDir;

        private SlotLockStore newStore() {
                return new SlotLockStore(tempDir.resolve("slotlocks.json"));
        }

        @Test
        void missingFileYieldsNoContexts() {
                assertTrue(newStore().load().isEmpty());
        }

        @Test
        void roundTripsContextState() {
                SlotLockStore store = newStore();
                SlotLockStore.ContextState state = new SlotLockStore.ContextState(null, null);
                state.locked()[3] = true;
                state.boundTo()[11] = 2;
                java.util.Map<String, SlotLockStore.ContextState> contexts = new java.util.HashMap<>();
                contexts.put("earthmc.net", state);

                store.save(contexts);
                SlotLockStore.ContextState loaded = newStore().load().get("earthmc.net");

                assertEquals(1, newStore().load().size());
                assertArrayEquals(state.locked(), loaded.locked());
                assertArrayEquals(state.boundTo(), loaded.boundTo());
        }

        @Test
        void arraysArePaddedToTheModelSize() {
                // A hand-edited file with short arrays must not crash reads.
                SlotLockStore.ContextState shortState = new SlotLockStore.ContextState(
                                new boolean[]{true, true}, new int[]{4});
                assertEquals(SlotLockStore.SLOT_COUNT, shortState.locked().length);
                assertEquals(SlotLockStore.SLOT_COUNT, shortState.boundTo().length);
                assertTrue(shortState.locked()[0]);
                assertFalse(shortState.locked()[5]);
                assertEquals(4, shortState.boundTo()[0]);
                assertEquals(-1, shortState.boundTo()[1]);
        }

        @Test
        void nullStateIsTheEmptyState() {
                SlotLockStore.ContextState empty = new SlotLockStore.ContextState(null, null);
                assertEquals(SlotLockStore.SLOT_COUNT, empty.locked().length);
                assertEquals(-1, empty.boundTo()[0]);
        }

        @Test
        void damagedContextsAreDroppedOnTheMutableCopy() {
                java.util.Map<String, SlotLockStore.ContextState> loaded = new java.util.HashMap<>();
                loaded.put("good", SlotLockStore.emptyState());
                loaded.put(null, SlotLockStore.emptyState());
                loaded.put("  ", SlotLockStore.emptyState());

                java.util.Map<String, SlotLockStore.ContextState> copy = SlotLockStore.mutableCopy(loaded);

                assertEquals(1, copy.size());
                assertTrue(copy.containsKey("good"));
        }

        @Test
        void corruptFileYieldsNoContextsAndIsPreserved() throws IOException {
                Path file = tempDir.resolve("slotlocks.json");
                Files.writeString(file, "{ not json", UTF_8);

                assertTrue(newStore().load().isEmpty());
                assertEquals("{ not json", Files.readString(tempDir.resolve("slotlocks.json.corrupted"), UTF_8));
        }
}
