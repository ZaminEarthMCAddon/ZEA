package net.zaminmc.zea.chat.classify;

import net.minecraft.network.chat.Component;
import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.zaminmc.zea.chat.model.TradeWait;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server's trade-cooldown rejection line, in both observed shapes
 * (seconds only; minutes and seconds) and the singular forms. The wait
 * time is the payload the pacing model syncs from — the client's only
 * authoritative clock for the trade window.
 */
class TradeWaitClassifierTest {
	private final TradeWaitClassifier classifier = new TradeWaitClassifier();

	private Optional<MessageClassifier.Result> classify(String text) {
		return classifier.tryClassify(CapturedMessage.of(Component.literal(text), Instant.EPOCH));
	}

	@Test
	void theSecondsFormIsRecognized() {
		Optional<MessageClassifier.Result> result = classify(
				"» You must wait another 4 seconds before sending your next message in Trade.");

		assertTrue(result.isPresent());
		assertEquals(MessageCategory.SYSTEM, result.get().category());
		TradeWait wait = (TradeWait) result.get().payload();
		assertEquals(4_000L, wait.remainingMillis());
	}

	@Test
	void theMinutesAndSecondsFormIsRecognized() {
		Optional<MessageClassifier.Result> result = classify(
				"» You must wait another 4 minutes and 58 seconds before sending your next message in Trade.");

		assertTrue(result.isPresent());
		TradeWait wait = (TradeWait) result.get().payload();
		assertEquals(298_000L, wait.remainingMillis());
	}

	@Test
	void theSingularFormsAreRecognized() {
		assertEquals(1_000L, ((TradeWait) classify(
				"» You must wait another 1 second before sending your next message in Trade.")
				.orElseThrow().payload()).remainingMillis());
		assertEquals(60_000L, ((TradeWait) classify(
				"» You must wait another 1 minute and 0 seconds before sending your next message in Trade.")
				.orElseThrow().payload()).remainingMillis());
	}

	@Test
	void similarLinesAreNotTradeWaits() {
		assertTrue(classify("» You must wait another 4 seconds before sending your next message in Global.")
				.isEmpty());
		assertTrue(classify("You must wait another 4 seconds before sending your next message in Trade.")
				.isEmpty());
		assertTrue(classify("» You must wait before sending your next message in Trade.").isEmpty());
		assertTrue(classify("EarthMC » Vote for the server!").isEmpty());
		assertTrue(classify("[Germany|Berlin] KoenigLuis: you must wait another 4 seconds").isEmpty());
	}

	@Test
	void thePipelineClassifiesTheWaitLineAheadOfEverythingElse() {
		ClassificationPipeline pipeline = new ClassificationPipeline();
		var classified = pipeline.classify(CapturedMessage.of(Component.literal(
				"» You must wait another 4 minutes and 58 seconds before sending your next message in Trade."),
				Instant.EPOCH));

		assertEquals(MessageCategory.SYSTEM, classified.category());
		assertTrue(classified.payload() instanceof TradeWait);
		assertEquals(298_000L, ((TradeWait) classified.payload()).remainingMillis());
	}
}
