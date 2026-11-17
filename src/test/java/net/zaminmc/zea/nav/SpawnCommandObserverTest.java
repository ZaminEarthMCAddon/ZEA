package net.zaminmc.zea.nav;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnCommandObserverTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);

	private final SpawnCommandObserver observer = new SpawnCommandObserver();

	@Test
	void parsesNationAndTownSpawns() {
		Optional<SpawnCommandObserver.ParsedSpawn> nation =
				SpawnCommandObserver.parse("/n spawn france");
		assertTrue(nation.isPresent());
		assertEquals(SpawnAttempt.Type.NATION, nation.get().type());
		assertEquals("france", nation.get().requested());
		assertEquals("france", nation.get().normalized());

		Optional<SpawnCommandObserver.ParsedSpawn> town =
				SpawnCommandObserver.parse("/t spawn Paris");
		assertTrue(town.isPresent());
		assertEquals(SpawnAttempt.Type.TOWN, town.get().type());
		// The requested form keeps the player's capitals; the normalized
		// form is the lookup key.
		assertEquals("Paris", town.get().requested());
		assertEquals("paris", town.get().normalized());
	}

	@Test
	void unrelatedCommandsAreNotRecorded() {
		assertFalse(SpawnCommandObserver.parse("/tp Abasing").isPresent());
		assertFalse(SpawnCommandObserver.parse("/n spawn").isPresent());
		assertFalse(SpawnCommandObserver.parse("/market").isPresent());
		assertFalse(SpawnCommandObserver.parse(null).isPresent());
		assertFalse(SpawnCommandObserver.parse("/n spawn france extra").isEmpty());
	}

	@Test
	void aConfirmedArrivalFillsTheEvidence() {
		observer.observe(start("/n spawn france", T));
		assertTrue(observer.attempts().get(0).requested().equals("france"));
		assertFalse(observer.attempts().get(0).arrivalEstablished());

		// A 32-block jump inside the window confirms the teleport.
		observer.tick(new double[] {10, 10, 0, 0}, T.plusMillis(500));
		observer.tick(new double[] {500, 640, 0, 0}, T.plusMillis(1_000));

		SpawnAttempt attempt = observer.attempts().get(0);
		assertTrue(attempt.arrivalEstablished());
		assertEquals(500.0, attempt.arrivalX());
		assertEquals(640.0, attempt.arrivalZ());
		assertEquals(Optional.of(attempt), observer.lastArrival());
		assertTrue(observer.lastArrivalWas("france"));
	}

	@Test
	void noMovementLeavesTheArrivalUnconfirmed() {
		observer.observe(start("/t spawn paris", T));
		// Small drift is not a teleport.
		observer.tick(new double[] {11, 11}, T.plusSeconds(1));
		observer.tick(new double[] {11.5, 12}, T.plusSeconds(10));
		// The window closes without evidence.
		observer.tick(new double[] {12, 12}, T.plusSeconds(21));

		SpawnAttempt attempt = observer.attempts().get(0);
		assertFalse(attempt.arrivalEstablished());
		assertEquals(Optional.empty(), observer.lastArrival());
		assertFalse(observer.lastArrivalWas("paris"));
	}

	@Test
	void movementOutsideTheWindowDoesNotConfirm() {
		observer.observe(start("/n spawn france", T));
		// Far inside the window nothing happens; the jump lands only
		// after the window has closed.
		observer.tick(new double[] {10, 10}, T.plusSeconds(19));
		observer.tick(new double[] {900, 900}, T.plusSeconds(21));

		assertFalse(observer.attempts().get(0).arrivalEstablished());
	}

	@Test
	void aLoadedPlayerDisappearingRetiresThePendingAttempt() {
		observer.observe(start("/n spawn france", T));
		observer.tick(null, T.plusMillis(100));

		assertFalse(observer.attempts().get(0).arrivalEstablished());
		// And no later movement can confirm the retired attempt.
		observer.tick(new double[] {900, 900}, T.plusMillis(200));
		assertFalse(observer.attempts().get(0).arrivalEstablished());
	}

	@Test
	void theHistoryIsBoundedAndOrderedByDispatch() {
		for (int i = 0; i < 12; i++) {
			observer.observe(start("/n spawn town" + i, T.plusSeconds(i)));
		}

		List<SpawnAttempt> attempts = observer.attempts();
		assertEquals(SpawnCommandObserver.MAX_ATTEMPTS, attempts.size());
		assertEquals("town4", attempts.get(0).requested());
		assertEquals("town11", attempts.get(attempts.size() - 1).requested());
	}

	@Test
	void resetForgetsEverything() {
		observer.observe(start("/n spawn france", T));
		observer.tick(new double[] {500, 500}, T.plusMillis(100));
		observer.reset();

		assertEquals(List.of(), observer.attempts());
		assertEquals(Optional.empty(), observer.lastArrival());
		assertFalse(observer.lastArrivalWas("france"));
	}

	private static SpawnAttempt start(String command, Instant at) {
		return SpawnCommandObserver.startFrom(command, "minecraft:overworld",
				"earthmc.net", 10, 10, at).orElseThrow();
	}
}
