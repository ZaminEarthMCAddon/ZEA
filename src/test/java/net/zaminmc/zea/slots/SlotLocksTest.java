package net.zaminmc.zea.slots;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotLocksTest {
        @TempDir
        Path tempDir;

        private SlotLocks locks() throws IOException {
            Path file = tempDir.resolve("locks.json");
            Files.writeString(file, "{}", UTF_8);
            return new SlotLocks(new SlotLockStore(file));
        }

        @Test
        void toggleLockFlipsAndClearsBindings() throws IOException {
            SlotLocks locks = locks();
            locks.bind(11, 2);

            assertTrue(locks.toggleLock(11));
            assertTrue(locks.isLocked(11));
            assertEquals(-1, locks.bindTarget(11), "a locked slot is never also bound");

            assertFalse(locks.toggleLock(11));
            assertFalse(locks.isLocked(11));
            assertEquals(-1, locks.bindTarget(11), "unlocking does not resurrect the old binding");
        }

        @Test
        void everyPlayerInventorySlotIsLockable() throws IOException {
            SlotLocks locks = locks();
            for (int slot = 0; slot < SlotLockStore.SLOT_COUNT; slot++) {
                locks.toggleLock(slot);
                assertTrue(locks.isLocked(slot), "slot " + slot + " should lock");
                locks.toggleLock(slot);
                assertFalse(locks.isLocked(slot));
            }
            // Out-of-range slots are simply not player slots.
            assertFalse(locks.isLocked(-1));
            assertFalse(locks.isLocked(41));
        }

        @Test
        void bindingIsExclusiveWithLocksAndMonogamous() throws IOException {
            SlotLocks locks = locks();
            locks.bind(11, 2);
            locks.bind(12, 2);

            assertEquals(-1, locks.bindTarget(11), "a hotbar slot has at most one partner");
            assertEquals(2, locks.bindTarget(12));
            assertFalse(locks.isLocked(12));

            // Binding a locked slot un-locks it (NEU's exclusivity rule).
            locks.toggleLock(13);
            locks.bind(13, 4);
            assertFalse(locks.isLocked(13));
            assertEquals(4, locks.bindTarget(13));
        }

        @Test
        void clearBindRemovesOnlyTheBinding() throws IOException {
            SlotLocks locks = locks();
            locks.bind(11, 2);
            locks.clearBind(11);

            assertEquals(-1, locks.bindTarget(11));
            assertFalse(locks.isLocked(11));
        }

        @Test
        void hotbarSlotsAreNotBindSources() {
            assertFalse(SlotLocks.bindable(0));
            assertFalse(SlotLocks.bindable(8));
            assertTrue(SlotLocks.bindable(9));
            assertTrue(SlotLocks.bindable(35));
            assertTrue(SlotLocks.bindable(36));
            assertTrue(SlotLocks.bindable(40));
            assertFalse(SlotLocks.bindable(41));
        }

        @Test
        void invalidBindsAreIgnored() throws IOException {
            SlotLocks locks = locks();
            locks.bind(0, 2);
            locks.bind(11, 9);
            locks.bind(11, -1);

            assertEquals(-1, locks.bindTarget(0));
            assertEquals(-1, locks.bindTarget(11));
        }

        @Test
        void contextsSeparateTheirState() throws IOException {
            SlotLocks locks = locks();
            locks.toggleLock(11);
            locks.bind(12, 3);

            locks.context("earthmc.net");
            assertFalse(locks.isLocked(11), "the generic context's lock does not leak");

            locks.toggleLock(20);
            locks.context("aurora.earthmc.net");
            assertFalse(locks.isLocked(20), "one server's lock never applies on another");
            locks.context("earthmc.net");
            assertTrue(locks.isLocked(20));

            assertEquals("earthmc.net", locks.context());
            locks.context(null);
            assertEquals(SlotLocks.GENERIC_CONTEXT, locks.context());
            locks.context("  ");
            assertEquals(SlotLocks.GENERIC_CONTEXT, locks.context());
        }

        @Test
        void statePersistsAcrossInstances() throws IOException {
            Path file = tempDir.resolve("locks.json");
            Files.writeString(file, "{}", UTF_8);
            SlotLocks first = new SlotLocks(new SlotLockStore(file));
            first.context("earthmc.net");
            first.toggleLock(8);
            first.bind(30, 6);

            SlotLocks second = new SlotLocks(new SlotLockStore(file));
            second.context("earthmc.net");

            assertTrue(second.isLocked(8), "the ninth hotbar slot's lock survives a restart");
            assertEquals(6, second.bindTarget(30));
            second.context(SlotLocks.GENERIC_CONTEXT);
            assertFalse(second.isLocked(8));
        }

        @Test
        void thePairingFlowBeginsAndEnds() throws IOException {
            SlotLocks locks = locks();
            assertFalse(locks.pairingActive());

            locks.beginPairing(11);
            assertTrue(locks.pairingActive());
            assertEquals(11, locks.pairingSlot());

            locks.endPairing();
            assertFalse(locks.pairingActive());
            assertEquals(-1, locks.pairingSlot());
        }

        @Test
        void switchingContextsEndsAnyPairing() throws IOException {
            SlotLocks locks = locks();
            locks.beginPairing(11);

            locks.context("earthmc.net");

            assertFalse(locks.pairingActive());
        }
}
