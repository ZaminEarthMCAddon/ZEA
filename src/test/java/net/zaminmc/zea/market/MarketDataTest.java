package net.zaminmc.zea.market;

import net.minecraft.core.BlockPos;
import net.zaminmc.zea.market.model.PhysicalShop;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataTest {
        private static final Instant T = Instant.ofEpochMilli(1_000_000L);

        private final MarketData data = new MarketData();

        private static PhysicalShop shop(String id) {
                return new PhysicalShop(id, "earthmc.net", "minecraft:overworld",
                                new BlockPos(10, 64, -20), null, "Abasing", null, null, T, T);
        }

        @Test
        void annotateSavesTheSpawnOnTheShop() {
                data.putShop(shop("s1"));

                assertTrue(data.annotate("s1", "/n spawn france"));

                assertEquals("/n spawn france", data.shop("s1").spawn());
                assertTrue(data.revision() > 0);
        }

        @Test
        void annotateKeepsTheRestOfTheRecord() {
                data.putShop(shop("s1"));

                data.annotate("s1", "/t spawn paris");

                PhysicalShop annotated = data.shop("s1");
                assertEquals("Abasing", annotated.owner());
                assertEquals(new BlockPos(10, 64, -20), annotated.signPos());
                assertEquals(T, annotated.createdAt());
        }

        @Test
        void theSameSpawnIsNotAChange() {
                data.putShop(shop("s1"));
                data.annotate("s1", "/n spawn france");
                long revision = data.revision();

                assertFalse(data.annotate("s1", "/n spawn france"));
                assertEquals(revision, data.revision());
        }

        @Test
        void aBlankSpawnIsDeclined() {
                data.putShop(shop("s1"));

                assertFalse(data.annotate("s1", "   "));
                assertFalse(data.annotate("s1", null));
                assertNull(data.shop("s1").spawn());
        }

        @Test
        void anUnknownShopIsDeclined() {
                assertFalse(data.annotate("missing", "/n spawn france"));
        }
}
