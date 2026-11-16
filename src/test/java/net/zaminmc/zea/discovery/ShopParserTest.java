package net.zaminmc.zea.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The EarthMC shop-information parser, against the exact live block from
 * the live report. Direction semantics must never reverse; stock and
 * per-transaction maximum stay separate; the price keeps its raw form.
 */
class ShopParserTest {
        private static final List<String> LIVE_BLOCK = List.of(
                        "+---------------------------------------------------+",
                        "| Shop Information:",
                        "| Owner: Ayskow",
                        "| Item: Diamond Pickaxe   [Item Preview]",
                        "| 100% remaining.",
                        "| Stock: 33",
                        "| Price per Diamond Pickaxe - 2G",
                        "| This shop is SELLING items.",
                        "+--------------------Enchantments--------------------+",
                        "| Fortune III",
                        "| Unbreaking III",
                        "| Efficiency V",
                        "+---------------------------------------------------+",
                        "",
                        "Enter in chat, how many you wish to BUY.",
                        "You can buy 16. Enter all to buy them all.");

        @Test
        void theLiveBlockParsesCompletely() {
                ShopObservation observation = ShopParser.parse(LIVE_BLOCK, "aurora.earthmc.net|minecraft:overworld|10,64,-20",
                                1_000L);
                assertEquals("Ayskow", observation.owner);
                assertEquals("Diamond Pickaxe", observation.itemName);
                assertEquals(Integer.valueOf(100), observation.remainingPercent);
                assertEquals(Integer.valueOf(33), observation.stock);
                assertEquals("2G", observation.priceRaw);
                assertEquals(Double.valueOf(2.0), observation.priceValue);
                assertEquals("G", observation.currency);
                assertEquals(ShopObservation.Direction.BUY_FROM, observation.direction,
                                "a SELLING shop is one the customer buys FROM");
                assertEquals(Integer.valueOf(16), observation.maxTransaction);
                assertEquals(List.of("Fortune III", "Unbreaking III", "Efficiency V"), observation.enchantments);
        }

        @Test
        void stockAndTransactionQuantityStaySeparate() {
                ShopObservation observation = ShopParser.parse(LIVE_BLOCK, "id", 1L);
                assertEquals(Integer.valueOf(33), observation.stock);
                assertEquals(Integer.valueOf(16), observation.maxTransaction,
                                "the shop's stock and the per-transaction cap are different fields");
        }

        @Test
        void aBuyingShopMeansSellTo() {
                ShopObservation observation = ShopParser.parse(
                                List.of("| Shop Information:",
                                                "| Owner: Trader",
                                                "| This shop is BUYING items.",
                                                "Enter in chat, how many you wish to SELL."),
                                "id", 1L);
                assertEquals(ShopObservation.Direction.SELL_TO, observation.direction,
                                "a BUYING shop is one the customer sells TO");
        }

        @Test
        void promptsAreRecognized() {
                assertTrue(ShopParser.isQuantityPrompt("Enter in chat, how many you wish to BUY."));
                assertEquals("SELL", ShopParser.promptAction("Enter in chat, how many you wish to SELL."));
                assertEquals("BUY", ShopParser.promptAction("enter in chat, how many you wish to buy."));
            assertEquals(null, ShopParser.promptAction("| Owner: Ayskow"));
        }

        @Test
        void aPartialBlockStillProducesTheFieldsItCarried() {
                ShopObservation observation = ShopParser.parse(
                                List.of("| Shop Information:", "| Owner: Ayskow", "| Item: Diamond Pickaxe"),
                                "id", 1L);
                assertEquals("Ayskow", observation.owner);
                assertEquals("Diamond Pickaxe", observation.itemName);
                assertNull(observation.priceRaw);
                assertNull(observation.direction);
                assertTrue(observation.enchantments.isEmpty());
        }

        @Test
        void pricesWithoutANumberStayRaw() {
                ShopObservation observation = new ShopObservation();
                ShopParser.apply(observation, List.of("| Price per Diamond Pickaxe - cash"));
                assertEquals("cash", observation.priceRaw);
                assertNull(observation.priceValue);
        }

        @Test
        void identityIsThePhysicalPlaceNotTheOffer() {
                String one = ShopIdentity.shopId("aurora.earthmc.net", "minecraft:overworld",
                                new net.minecraft.core.BlockPos(10, 64, -20));
                String sameCase = ShopIdentity.shopId("AURORA.EARTHMC.NET", "minecraft:overworld",
                                new net.minecraft.core.BlockPos(10, 64, -20));
                String withPort = ShopIdentity.shopId("aurora.earthmc.net:25565", "minecraft:overworld",
                                new net.minecraft.core.BlockPos(10, 64, -20));
                assertEquals(one, sameCase, "case spellings of one server share identity");
                assertEquals(one, withPort, "the port strips away; the host is the identity");
                String otherGate = ShopIdentity.shopId("go.earthmc.net", "minecraft:overworld",
                                new net.minecraft.core.BlockPos(10, 64, -20));
                org.junit.jupiter.api.Assertions.assertNotEquals(one, otherGate,
                                "distinct subdomains are distinct identities (the ServerIdentity policy)");
                String elsewhere = ShopIdentity.shopId("aurora.earthmc.net", "minecraft:overworld",
                                new net.minecraft.core.BlockPos(12, 64, -20));
                org.junit.jupiter.api.Assertions.assertNotEquals(one, elsewhere,
                                "a different sign position is a different shop");
        }
}
