package net.zaminmc.zea.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySearchTest {
        @Test
        void startsInCalculatorModeWithAnEmptyQuery() {
                InventorySearch search = new InventorySearch();

                assertEquals(InventorySearch.Mode.CALCULATOR, search.mode());
                assertEquals("", search.query());
                assertFalse(search.matchingActive());
        }

        @Test
        void doubleClickTogglesMatchingMode() {
                InventorySearch search = new InventorySearch();

                assertFalse(search.barClicked(1_000), "the first click only focuses");
                assertFalse(search.matchingActive());
                assertTrue(search.barClicked(1_200), "the second click within the window toggles");
                assertTrue(search.matchingActive());

                // A slow second click is a fresh single click.
                assertFalse(search.barClicked(2_000));
                assertTrue(search.matchingActive());
                assertTrue(search.barClicked(2_200));
                assertFalse(search.matchingActive());
        }

        @Test
        void leaveMatchingModeIsTheClearWayOut() {
                InventorySearch search = new InventorySearch();
                search.barClicked(1_000);
                search.barClicked(1_100);

                search.leaveMatchingMode();

                assertEquals(InventorySearch.Mode.CALCULATOR, search.mode());
        }

        @Test
        void theQueryTracksTheBar() {
                InventorySearch search = new InventorySearch();

                search.query("diamond");

                assertEquals("diamond", search.query());
                search.query(null);
                assertEquals("", search.query());
        }
}
