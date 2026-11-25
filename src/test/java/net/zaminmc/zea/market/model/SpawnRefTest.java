package net.zaminmc.zea.market.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpawnRefTest {
        @Test
        void theSlashCommandFormNormalizes() {
                assertEquals("/n spawn france", SpawnRef.normalize("/n spawn france"));
                assertEquals("/n spawn france", SpawnRef.normalize("/n spawn  france"));
                assertEquals("/t spawn paris", SpawnRef.normalize("/t spawn paris"));
        }

        @Test
        void theCommandFormsNormalizeWithoutTheSlash() {
                assertEquals("/n spawn france", SpawnRef.normalize("n spawn france"));
                assertEquals("/n spawn france", SpawnRef.normalize("nation spawn france"));
                assertEquals("/t spawn paris", SpawnRef.normalize("t spawn paris"));
                assertEquals("/t spawn paris", SpawnRef.normalize("town spawn paris"));
                assertEquals("/t spawn paris", SpawnRef.normalize("spawn paris"));
        }

        @Test
        void aBareNameIsATownSpawn() {
                assertEquals("/t spawn france", SpawnRef.normalize("france"));
                assertEquals("/t spawn New York", SpawnRef.normalize("  New York "));
        }

        @Test
        void namesWithSpacesSurvive() {
                assertEquals("/n spawn new york", SpawnRef.normalize("/n spawn new york"));
        }

        @Test
        void garbageIsDeclined() {
                assertNull(SpawnRef.normalize(null));
                assertNull(SpawnRef.normalize("   "));
                assertNull(SpawnRef.normalize("/n spawn"));
                assertNull(SpawnRef.normalize("spawn   "));
        }

        @Test
        void anOverlongNameIsDeclined() {
                assertNull(SpawnRef.normalize("/n spawn "
                                + "x".repeat(49)));
                assertEquals("/n spawn " + "x".repeat(48), SpawnRef.normalize("/n spawn "
                                + "x".repeat(48)));
        }

        @Test
        void displayShowsTheName() {
                assertEquals("france", SpawnRef.display("/n spawn france"));
                assertEquals("paris", SpawnRef.display("/t spawn paris"));
                assertEquals("paris", SpawnRef.display("t spawn paris"));
                assertEquals("france", SpawnRef.display("france"));
        }
}
