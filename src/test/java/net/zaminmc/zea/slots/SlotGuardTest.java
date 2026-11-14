package net.zaminmc.zea.slots;

import net.minecraft.world.inventory.ContainerInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotGuardTest {
        // Slot numbering: 0-8 hotbar, 9-35 main, 36-39 armor, 40 off-hand.
        private static final int HOTBAR_3 = 2;
        private static final int MAIN_12 = 11;
        private static final int ARMOR_CHEST = 37;
        private static final int OFFHAND = 40;

        @TempDir
        Path tempDir;

        private SlotLocks locks() throws IOException {
                Path file = tempDir.resolve("locks.json");
                Files.writeString(file, "{}", UTF_8);
                return new SlotLocks(new SlotLockStore(file));
        }

        @Test
        void lockedSlotsBlockEveryInput() throws IOException {
                SlotLocks locks = locks();
                locks.toggleLock(MAIN_12);
                locks.toggleLock(HOTBAR_3);
                locks.toggleLock(ARMOR_CHEST);
                locks.toggleLock(OFFHAND);

                for (ContainerInput input : ContainerInput.values()) {
                        assertEquals(SlotGuard.Action.BLOCK, SlotGuard.onSlotClick(MAIN_12, input, 0, locks),
                                        "locked main slot vs " + input);
                        assertEquals(SlotGuard.Action.BLOCK, SlotGuard.onSlotClick(HOTBAR_3, input, 0, locks),
                                        "locked hotbar slot (including the ninth slot's neighbors) vs " + input);
                        assertEquals(SlotGuard.Action.BLOCK, SlotGuard.onSlotClick(ARMOR_CHEST, input, 0, locks),
                                        "locked armor slot vs " + input);
                        assertEquals(SlotGuard.Action.BLOCK, SlotGuard.onSlotClick(OFFHAND, input, 0, locks),
                                        "locked off-hand slot vs " + input);
                }
        }

        @Test
        void theNinthHotbarSlotLocksLikeAnyOther() throws IOException {
                SlotLocks locks = locks();
                locks.toggleLock(8);

                assertTrue(locks.isLocked(8));
                assertEquals(SlotGuard.Action.BLOCK, SlotGuard.onSlotClick(8, ContainerInput.PICKUP, 0, locks));
        }

        @Test
        void numberKeySwapsIntoALockedHotbarSlotAreBlocked() throws IOException {
                SlotLocks locks = locks();
                locks.toggleLock(HOTBAR_3);

                // Hovering any free slot, pressing "3": SWAP with button 2.
                assertEquals(SlotGuard.Action.BLOCK, SlotGuard.onSlotClick(MAIN_12, ContainerInput.SWAP, HOTBAR_3, locks));
                // Swapping into a free hotbar slot passes.
                assertEquals(SlotGuard.Action.PASS, SlotGuard.onSlotClick(MAIN_12, ContainerInput.SWAP, 0, locks));
        }

        @Test
        void unlockedSlotsPassEverything() throws IOException {
                SlotLocks locks = locks();

                for (ContainerInput input : ContainerInput.values()) {
                        assertEquals(SlotGuard.Action.PASS, SlotGuard.onSlotClick(MAIN_12, input, 0, locks));
                }
        }

        @Test
        void nonPlayerSlotsPass() throws IOException {
                SlotLocks locks = locks();

                assertEquals(SlotGuard.Action.PASS, SlotGuard.onSlotClick(-1, ContainerInput.PICKUP, 0, locks));
                assertEquals(SlotGuard.Action.PASS, SlotGuard.onSlotClick(-1, ContainerInput.QUICK_MOVE, 0, locks));
        }

        @Test
        void rightClickOnABoundSlotBecomesTheSwapAction() throws IOException {
                SlotLocks locks = locks();
                locks.bind(MAIN_12, HOTBAR_3);

                assertEquals(SlotGuard.Action.SWAP,
                                SlotGuard.onSlotClick(MAIN_12, ContainerInput.PICKUP, 1, locks));
                // Left-click and other inputs stay vanilla.
                assertEquals(SlotGuard.Action.PASS,
                                SlotGuard.onSlotClick(MAIN_12, ContainerInput.PICKUP, 0, locks));
                assertEquals(SlotGuard.Action.PASS,
                                SlotGuard.onSlotClick(MAIN_12, ContainerInput.QUICK_MOVE, 1, locks));
                // Unbound slots never swap.
                assertEquals(SlotGuard.Action.PASS,
                                SlotGuard.onSlotClick(ARMOR_CHEST, ContainerInput.PICKUP, 1, locks));
        }

        @Test
        void lockedSlotsNeverJoinQuickCraft() throws IOException {
                SlotLocks locks = locks();
                locks.toggleLock(MAIN_12);

                assertFalse(SlotGuard.mayJoinQuickCraft(MAIN_12, locks));
                assertTrue(SlotGuard.mayJoinQuickCraft(MAIN_12 + 1, locks));
                assertTrue(SlotGuard.mayJoinQuickCraft(-1, locks), "container slots are not ZEA's to guard");
        }

        @Test
        void theDropKeyIsBlockedForALockedSelectedSlot() throws IOException {
                SlotLocks locks = locks();
                locks.toggleLock(HOTBAR_3);

                assertTrue(SlotGuard.blocksDropKey(HOTBAR_3, locks));
                assertFalse(SlotGuard.blocksDropKey(0, locks));
                assertFalse(SlotGuard.blocksDropKey(-1, locks));
        }
}
