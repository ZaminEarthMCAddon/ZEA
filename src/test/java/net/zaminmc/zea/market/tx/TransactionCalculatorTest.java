package net.zaminmc.zea.market.tx;

import net.zaminmc.zea.market.model.ShopDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionCalculatorTest {
	@Test
	void oneBulkOfSevenCostsOneG() {
		Optional<TransactionCalculator.BulkPlan> plan = TransactionCalculator.buy(
				ShopDirection.SELLING, new BigDecimal("1"), 7, 64, 1);

		TransactionCalculator.BulkPlan bulk = plan.orElseThrow();
		assertEquals(7, bulk.itemCount());
		assertEquals(new BigDecimal("1"), bulk.totalCost());
		assertEquals("1", bulk.inputToSend());
	}

	@Test
	void twoBulksOfSixtyFourCostTwoG() {
		Optional<TransactionCalculator.BulkPlan> plan = TransactionCalculator.buy(
				ShopDirection.SELLING, new BigDecimal("1"), 64, 64, 2);

		assertEquals(128, plan.orElseThrow().itemCount());
		assertEquals(new BigDecimal("2"), plan.orElseThrow().totalCost());
	}

	@Test
	void theServerReportedMaximumIsEnforced() {
		assertTrue(TransactionCalculator.buy(ShopDirection.SELLING, new BigDecimal("1"), 7, 64, 65).isEmpty());
		assertTrue(TransactionCalculator.buy(ShopDirection.SELLING, new BigDecimal("1"), 7, 64, 64).isPresent());
	}

	@Test
	void buyingWithoutTheServerBulkSizeIsImpossible() {
		// The bulk size is the server's number; without it the market
		// never substitutes an assumption.
		assertTrue(TransactionCalculator.buy(ShopDirection.SELLING, new BigDecimal("1"), null, null, 1).isEmpty());
	}

	@Test
	void theDirectionsNeverReverse() {
		// A bulk count means nothing at a BUYING shop, an item count
		// nothing at a SELLING shop.
		assertTrue(TransactionCalculator.buy(ShopDirection.BUYING, new BigDecimal("17"), 7, 64, 1).isEmpty());
		assertTrue(TransactionCalculator.sell(ShopDirection.SELLING, new BigDecimal("17"), 10, 1).isEmpty());
	}

	@Test
	void oneItemAtSeventeenGPaysSeventeen() {
		Optional<TransactionCalculator.ItemPlan> plan = TransactionCalculator.sell(
				ShopDirection.BUYING, new BigDecimal("17"), 38, 1);

		assertEquals(new BigDecimal("17"), plan.orElseThrow().totalPayout());
		assertEquals("1", plan.orElseThrow().inputToSend());
	}

	@Test
	void twoItemsAtSeventeenGPaysThirtyFour() {
		Optional<TransactionCalculator.ItemPlan> plan = TransactionCalculator.sell(
				ShopDirection.BUYING, new BigDecimal("17"), 38, 2);

		assertEquals(new BigDecimal("34"), plan.orElseThrow().totalPayout());
	}

	@Test
	void aZeroSellableQuantityRefusesEverySubmission() {
		// "You can sell 0." — the shop can buy nothing right now.
		assertTrue(TransactionCalculator.sell(ShopDirection.BUYING, new BigDecimal("17"), 0, 1).isEmpty());
	}

	@Test
	void theReportedSellableQuantityCapsTheSale() {
		assertTrue(TransactionCalculator.sell(ShopDirection.BUYING, new BigDecimal("17"), 38, 39).isEmpty());
		assertTrue(TransactionCalculator.sell(ShopDirection.BUYING, new BigDecimal("17"), 38, 38).isPresent());
	}

	@Test
	void allIsOnlyOfferedWhenTheServerSaysSo() {
		assertTrue(TransactionCalculator.all(false, ShopDirection.SELLING).isEmpty());
		TransactionCalculator.AllPlan plan = TransactionCalculator.all(true, ShopDirection.SELLING).orElseThrow();
		assertEquals("all", plan.inputToSend());
	}
}
