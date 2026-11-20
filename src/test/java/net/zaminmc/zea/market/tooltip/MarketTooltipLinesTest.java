package net.zaminmc.zea.market.tooltip;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.zaminmc.zea.VanillaBootstrap;
import net.zaminmc.zea.market.MarketData;
import net.zaminmc.zea.market.index.ItemVariantIndex;
import net.zaminmc.zea.market.index.ItemVariantIndex.Variant;
import net.zaminmc.zea.market.model.ItemFingerprint;
import net.zaminmc.zea.market.model.PhysicalShop;
import net.zaminmc.zea.market.model.PriceScope;
import net.zaminmc.zea.market.model.ShopDirection;
import net.zaminmc.zea.market.model.ShopObservation;
import net.zaminmc.zea.market.model.SourceType;
import net.zaminmc.zea.market.model.StockUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketTooltipLinesTest {
        private static final Instant T = Instant.ofEpochMilli(1_000_000L);
        private static final Instant NOW = T.plusSeconds(7_200);
        private static final String TOKEN = "minecraft:diamond_pickaxe|{}";
        /** ZEA's language file, read once so translatable keys resolve here. */
        private static final Map<String, String> LANG = new HashMap<>();

        @BeforeAll
        static void boot() {
                VanillaBootstrap.ensure();
                try (java.io.InputStream in = MarketTooltipLinesTest.class
                                .getResourceAsStream("/assets/zea/lang/en_us.json")) {
                        if (in != null) {
                                net.minecraft.locale.Language.loadFromJson(in, LANG::put);
                        }
                } catch (java.io.IOException e) {
                        throw new IllegalStateException("ZEA language file did not load", e);
                }
        }

        private final MarketData data = new MarketData();

        private static PhysicalShop shop(String id, String owner) {
                int hash = Math.abs(id.hashCode());
                return new PhysicalShop(id, "earthmc.net", "minecraft:overworld",
                                new BlockPos(hash % 1000, 64, hash % 997), null, owner, null, T, T);
        }

        private static ShopObservation selling(String shopId, String price, String stock,
                        StockUnit unit, String owner, Instant at) {
                return new ShopObservation(shopId, ShopDirection.SELLING,
                                new ItemFingerprint(TOKEN, ItemFingerprint.Fidelity.EXACT),
                                "Diamond Pickaxe",
                                price == null ? null : new BigDecimal(price),
                                price == null ? null : PriceScope.PER_ITEM,
                                null, stock, unit, null, null, null, null, null,
                                false, List.of(), SourceType.SERVER_RESPONSE, at);
        }

        private void record(String shopId, String owner, ShopObservation observation) {
                data.record(shop(shopId, owner), observation);
        }

        private Variant variant() {
                List<Variant> variants = ItemVariantIndex.variants(data);
                assertEquals(1, variants.size());
                return variants.get(0);
        }

        /**
         * The line's plain text with ZEA's own language file applied — a
         * local resolution, deliberately not a global {@code Language.inject},
         * so no other test's translatable behavior changes.
         */
        private static String resolve(Component component) {
                StringBuilder out = new StringBuilder();
                if (component.getContents() instanceof TranslatableContents translatable) {
                        out.append(resolve(translatable));
                } else {
                        out.append(component.getString());
                }
                for (Component sibling : component.getSiblings()) {
                        out.append(resolve(sibling));
                }
                return out.toString();
        }

        private static String resolve(TranslatableContents translatable) {
                String template = LANG.getOrDefault(translatable.getKey(), translatable.getKey());
                Object[] args = translatable.getArgs();
                String formatted = template;
                for (Object arg : args) {
                        formatted = formatted.replaceFirst("%s", String.valueOf(arg));
                }
                return formatted;
        }

        private static List<String> text(List<Component> lines) {
                return lines.stream().map(MarketTooltipLinesTest::resolve).toList();
        }

        @Test
        void theSectionShowsSellersPriceRangeAndCombinedStock() {
                record("s1", "Player A", selling("s1", "1", "78", StockUnit.ITEMS, "Player A", T));
                record("s2", "Player B", selling("s2", "2.5", "100", StockUnit.ITEMS, "Player B", T));
                record("s3", "Player C", selling("s3", "2", "46", StockUnit.ITEMS, "Player C", T));

                List<String> lines = text(MarketTooltipLines.build(variant(), NOW));

                assertEquals("", lines.get(0));
                assertEquals("MARKET INFORMATION", lines.get(1));
                assertTrue(lines.get(2).contains("Sellers: 3"), lines.get(2));
                assertTrue(lines.get(3).contains("Price: 1G-2.5G"), lines.get(3));
                assertTrue(lines.get(4).contains("Stock: 224 items"), lines.get(4));
                assertEquals(6, lines.size());
        }

        @Test
        void oneOwnerWithSeveralShopsIsOneSeller() {
                record("s1", "Player A", selling("s1", "1", "78", StockUnit.ITEMS, "Player A", T));
                record("s2", "Player A", selling("s2", "2", "10", StockUnit.ITEMS, "Player A", T));

                List<String> lines = text(MarketTooltipLines.build(variant(), NOW));
                assertTrue(lines.get(2).contains("Sellers: 1"), lines.get(2));
                assertTrue(lines.get(3).contains("Price: 1G-2G"), lines.get(3));
        }

        @Test
        void aSinglePriceShowsWithoutARange() {
                record("s1", "Player A", selling("s1", "3", "78", StockUnit.ITEMS, "Player A", T));

                List<String> lines = text(MarketTooltipLines.build(variant(), NOW));
                assertTrue(lines.get(3).contains("Price: 3G"), lines.get(3));
        }

        @Test
        void stockNeverCombinesNonItemUnits() {
                // One shop reports stacks, one reports space: no honest item
                // total exists, so the line stays out.
                record("s1", "A", selling("s1", "1", "30", StockUnit.ITEMS, "A", T));
                record("s2", "B", selling("s2", "2", "5", StockUnit.STACKS, "B", T));
                List<String> mixed = text(MarketTooltipLines.build(variant(), NOW));
                assertEquals(5, mixed.size());
                assertTrue(mixed.stream().noneMatch(line -> line.contains("Stock")));

                data.record(shop("s3", "C"), selling("s3", "3", "12", StockUnit.SPACE, "C", T));
                List<String> withSpace = text(MarketTooltipLines.build(variant(), NOW));
                assertTrue(withSpace.stream().noneMatch(line -> line.contains("Stock")));
        }

        @Test
        void unpricedSellersStillCountButNeverShapeTheRange() {
                record("s1", "Player A", selling("s1", "1", "78", StockUnit.ITEMS, "Player A", T));
                record("s2", "Player B", selling("s2", null, null, StockUnit.UNKNOWN, "Player B", T));

                List<String> lines = text(MarketTooltipLines.build(variant(), NOW));
                assertTrue(lines.get(2).contains("Sellers: 2"), lines.get(2));
                assertTrue(lines.get(3).contains("Price: 1G"), lines.get(3));
        }

        @Test
        void theBuyingSideMirrorsTheShapeAsPayout() {
                record("s1", "Player A", selling("s1", "1", "78", StockUnit.ITEMS, "Player A", T));
                data.record(shop("s2", "Player B"), new ShopObservation("s2", ShopDirection.BUYING,
                                new ItemFingerprint(TOKEN, ItemFingerprint.Fidelity.EXACT),
                                "Diamond Pickaxe", new BigDecimal("2"), PriceScope.PER_ITEM, null,
                                "30", StockUnit.SPACE, null, null, null, null, null,
                                false, List.of(), SourceType.SERVER_RESPONSE, T));

                List<String> lines = text(MarketTooltipLines.build(variant(), NOW));
                assertTrue(lines.stream().anyMatch(line -> line.contains("Sellers: 1")));
                assertTrue(lines.stream().anyMatch(line -> line.contains("Buyers: 1")));
                assertTrue(lines.stream().anyMatch(line -> line.contains("Payout: 2G")));
                // The buying side's stock figure is space, never summed into items.
                assertEquals(1, lines.stream().filter(line -> line.contains("Stock")).count());
        }

        @Test
        void theAgeLineSpeaksOfObservations() {
                record("s1", "Player A", selling("s1", "1", "78", StockUnit.ITEMS, "Player A", T));

                List<String> lines = text(MarketTooltipLines.build(variant(), NOW));
                assertTrue(lines.get(lines.size() - 1).contains("Based on observations - 2 hours old"),
                                lines.get(lines.size() - 1));
        }

        @Test
        void colorsFollowTheBulletShape() {
                record("s1", "Player A", selling("s1", "1", "78", StockUnit.ITEMS, "Player A", T));

                Component line = MarketTooltipLines.build(variant(), NOW).get(2);
                String plain = resolve(line);
                assertTrue(plain.startsWith("\u25aa "), plain);
                // The bullet is black, the label white, the value gold: the
                // bullet is the line's own style, the label and value are
                // its siblings.
                assertEquals(0x555555, line.getStyle().getColor().getValue());
                assertEquals(0xFFFFFF, line.getSiblings().get(0).getStyle().getColor().getValue());
                String valueText = line.getSiblings().get(2).getString();
                assertTrue(valueText.contains("1"), valueText);
                assertEquals(0xFFAA00, line.getSiblings().get(2).getStyle().getColor().getValue());
        }
}
