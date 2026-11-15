package net.zaminmc.zea.market.tx;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTransactionTest {
	@Test
	void reviewRequiresExplicitSteps() {
		ShopTransaction transaction = new ShopTransaction();
		assertEquals(ShopTransaction.Phase.ENTERING, transaction.phase());

		assertTrue(transaction.beginReview());
		assertEquals(ShopTransaction.Phase.REVIEWING, transaction.phase());
		assertTrue(transaction.cancelReview());
		assertEquals(ShopTransaction.Phase.ENTERING, transaction.phase());
	}

	@Test
	void aSecondConfirmCanNeverSendADuplicate() {
		ShopTransaction transaction = new ShopTransaction();
		transaction.beginReview();
		Optional<String> first = transaction.markSubmitted("2");
		Optional<String> second = transaction.markSubmitted("2");

		assertEquals("2", first.orElseThrow());
		assertTrue(second.isEmpty());
		assertEquals(1, transaction.submitCount());
		assertFalse(transaction.canConfirm());
	}

	@Test
	void submissionDirectlyFromTheEditorIsRefused() {
		ShopTransaction transaction = new ShopTransaction();
		assertTrue(transaction.markSubmitted("2").isEmpty());
		assertEquals(0, transaction.submitCount());
	}

	@Test
	void aTimeoutIsNotAnOutcomeAndDoesNotRetry() {
		ShopTransaction transaction = new ShopTransaction();
		transaction.beginReview();
		Instant sent = Instant.now();
		transaction.markSubmitted("3");

		// Inside the window: still waiting, still submitted.
		assertFalse(transaction.markTimedOut(sent.plusSeconds(5)));
		assertEquals(ShopTransaction.Phase.SUBMITTED, transaction.phase());

		// Past the window: outcome unknown, never a retry, never a claim.
		assertTrue(transaction.markTimedOut(sent.plusSeconds(16)));
		assertEquals(ShopTransaction.Phase.TIMED_OUT, transaction.phase());
		assertEquals(1, transaction.submitCount());
		assertFalse(transaction.canConfirm());
	}

	@Test
	void onlyAFreshTransactionCanSendAgain() {
		ShopTransaction transaction = new ShopTransaction();
		transaction.beginReview();
		transaction.markSubmitted("3");
		transaction.markTimedOut(Instant.now().plusSeconds(16));

		assertTrue(transaction.markSubmitted("3").isEmpty());
		transaction.restart();
		assertEquals(ShopTransaction.Phase.ENTERING, transaction.phase());
		transaction.beginReview();
		assertEquals("3", transaction.markSubmitted("3").orElseThrow());
	}
}
