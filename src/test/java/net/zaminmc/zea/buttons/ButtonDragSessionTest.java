package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonDragSessionTest {
        private static final int BUTTON_X = 100;
        private static final int BUTTON_Y = 40;

        @Test
        void beginGrabsAtTheCursorAndTracksThePreview() {
                ButtonDragSession drag = new ButtonDragSession();
                InventoryButton button = InventoryButton.newButton(1);
                drag.begin(button, BUTTON_X, BUTTON_Y, 105, 47);

                assertTrue(drag.isActive());
                assertEquals(button.id(), drag.button().id());
                assertEquals(BUTTON_X, drag.previewX());
                assertEquals(BUTTON_Y, drag.previewY());

                drag.moveTo(120, 60);
                assertEquals(115, drag.previewX());
                assertEquals(53, drag.previewY());
                assertTrue(drag.moved());
        }

        @Test
        void jitterWithinThreePixelsIsNotMovement() {
                ButtonDragSession drag = new ButtonDragSession();
                drag.begin(InventoryButton.newButton(1), BUTTON_X, BUTTON_Y, 100, 40);
                drag.moveTo(102, 42);
                assertFalse(drag.moved());
                drag.moveTo(104, 40);
                assertTrue(drag.moved());
        }

        @Test
        void endReturnsTheButtonOnlyForARealDrag() {
                ButtonDragSession drag = new ButtonDragSession();
                drag.begin(InventoryButton.newButton(1), BUTTON_X, BUTTON_Y, 100, 40);
                drag.moveTo(101, 41);
                assertNull(drag.end());
                assertFalse(drag.isActive());

                InventoryButton second = InventoryButton.newButton(2).withOrder(1);
                drag.begin(second, BUTTON_X, BUTTON_Y, 100, 40);
                drag.moveTo(140, 60);
                InventoryButton dragged = drag.end();
                assertEquals(second.id(), dragged.id());
                assertEquals(1, dragged.order());
                assertFalse(drag.isActive());
        }

        @Test
        void cancelAbortsAndMoveIsIgnoredWhenIdle() {
                ButtonDragSession drag = new ButtonDragSession();
                drag.begin(InventoryButton.newButton(1), BUTTON_X, BUTTON_Y, 100, 40);
                drag.cancel();
                assertFalse(drag.isActive());
                drag.moveTo(200, 200);
                assertEquals(BUTTON_X, drag.previewX(), "an idle session ignores moves");
        }

        @Test
        void beginRejectsNull() {
                ButtonDragSession drag = new ButtonDragSession();
                assertThrows(NullPointerException.class, () -> drag.begin(null, 0, 0, 0, 0));
        }
}
