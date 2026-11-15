package net.zaminmc.zea.market.tx;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * One transaction's state as the shop screen drives it.
 *
 * <p>The phases encode the safety rules the protocol requires:
 *
 * <ul>
 *   <li>{@code ENTERING} — the player is choosing a quantity. Nothing
 *       has been reviewed.</li>
 *   <li>{@code REVIEWING} — the exact input, quantities and totals are
 *       on screen awaiting the explicit confirm.</li>
 *   <li>{@code SUBMITTED} — the input went out through the normal chat
 *       pathway. The button is dead in this state: a second confirm
 *       can never send a duplicate.</li>
 *   <li>{@code TIMED_OUT} — the wait window passed without a recognized
 *       server outcome. A timeout does <b>not</b> prove rejection, so
 *       nothing retries and nothing is claimed: the outcome stays
 *       unknown, and the only way forward is a fresh, deliberate
 *       transaction.</li>
 * </ul>
 *
 * <p>There is deliberately no {@code SUCCESSFUL} or {@code FAILED}
 * phase yet: EarthMC's success and failure message shapes have not been
 * captured, and recognizing them by guesswork would claim outcomes the
 * client cannot verify. The state machine grows those transitions the
 * moment real captures exist (see WORKLOG); until then every send ends
 * in {@code TIMED_OUT} — honest, safe, and never a false success.
 */
public final class ShopTransaction {
	/** How long a submitted input waits for a recognizable outcome. */
	public static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(15);

	private @Nullable Phase phase;
	private @Nullable String inputSent;
	private @Nullable Instant submittedAt;
	private int submitCount;

	public ShopTransaction() {
		this.phase = Phase.ENTERING;
	}

	/** The transaction phases. */
	public enum Phase {
		ENTERING,
		REVIEWING,
		SUBMITTED,
		TIMED_OUT
	}

	/**
	 * Marks that the player moved from the quantity editor to the review
	 * view. Refused unless the transaction is still entering — a review
	 * after submission would invite a duplicate.
	 */
	public boolean beginReview() {
		if (phase != Phase.ENTERING) {
			return false;
		}
		phase = Phase.REVIEWING;
		return true;
	}

	/** Back to the editor from the review view; never from a submission. */
	public boolean cancelReview() {
		if (phase != Phase.REVIEWING) {
			return false;
		}
		phase = Phase.ENTERING;
		return true;
	}

	/**
	 * Marks the input as sent through EarthMC's normal chat pathway.
	 * Returns {@code empty} when the transaction is not in review —
	 * which is exactly the duplicate-confirmation protection: a second
	 * confirm on the same transaction can never produce a second send.
	 */
	public Optional<String> markSubmitted(String input) {
		if (phase != Phase.REVIEWING || input == null || input.isBlank()) {
			return Optional.empty();
		}
		phase = Phase.SUBMITTED;
		inputSent = input;
		submittedAt = Instant.now();
		submitCount++;
		return Optional.of(input);
	}

	/**
	 * The wait window has elapsed with no recognizable outcome. The
	 * transaction stops there: no automatic retry, no invented result.
	 */
	public boolean markTimedOut(Instant now) {
		if (phase != Phase.SUBMITTED || submittedAt == null
				|| now.isBefore(submittedAt.plus(RESPONSE_TIMEOUT))) {
			return false;
		}
		phase = Phase.TIMED_OUT;
		return true;
	}

	/** Forgets the outcome and allows a fresh, deliberate transaction. */
	public void restart() {
		phase = Phase.ENTERING;
		inputSent = null;
		submittedAt = null;
	}

	public Phase phase() {
		return phase == null ? Phase.ENTERING : phase;
	}

	/** The exact input that was sent, once one has been. */
	public @Nullable String inputSent() {
		return inputSent;
	}

	/** When it was sent, once it has been. */
	public @Nullable Instant submittedAt() {
		return submittedAt;
	}

	/** How many sends have left this transaction; healthy ones have 1. */
	public int submitCount() {
		return submitCount;
	}

	/** Whether the confirm control may currently fire. */
	public boolean canConfirm() {
		return phase == Phase.REVIEWING;
	}
}
