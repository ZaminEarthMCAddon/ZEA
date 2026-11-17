package net.zaminmc.zea.nav;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopNavigatorTest {
	private static final Instant T = Instant.ofEpochMilli(1_000_000L);

	private static ShopDestination destination(String town, String nation) {
		return new ShopDestination("s|1", "earthmc.net", "minecraft:overworld",
				new BlockPos(120, 64, -340), new BlockPos(120, 64, -341),
				"Ayskow", town, nation, T);
	}

	@Test
	void thePlayerAlreadyInTheNationGetsAWaypointOnly() {
		// The spec's France scenario: the player is in France, the shop
		// is in France — issuing /n spawn france would teleport them
		// backwards for no reason.
		ShopNavigator.Plan plan = ShopNavigator.plan(destination("Paris", "France"),
				"France", "minecraft:overworld");

		assertNull(plan.spawnCommand());
		assertTrue(plan.waypoint());
		assertTrue(plan.explanation().contains("Already in France"));
	}

	@Test
	void theTownCommandIsPreferredWhenTheTownIsKnown() {
		ShopNavigator.Plan plan = ShopNavigator.plan(destination("Paris", "France"),
				"Germany", "minecraft:overworld");

		assertEquals("t spawn Paris", plan.spawnCommand());
		assertTrue(plan.waypoint());
		// The explanation never claims the command teleports.
		assertTrue(plan.explanation().contains("the server decides"));
	}

	@Test
	void theNationCommandFillsInWhenOnlyTheNationIsKnown() {
		ShopNavigator.Plan plan = ShopNavigator.plan(destination(null, "France"),
				"Germany", "minecraft:overworld");

		assertEquals("n spawn France", plan.spawnCommand());
		assertTrue(plan.waypoint());
	}

	@Test
	void anUnknownPlayerNationStillTravelsWhenTheDestinationIsKnown() {
		// "Already in the nation" is an affirmative finding; unknown is
		// not it. The player may be anywhere — the spawn goes out.
		ShopNavigator.Plan plan = ShopNavigator.plan(destination("Paris", "France"),
				null, "minecraft:overworld");

		assertEquals("t spawn Paris", plan.spawnCommand());
		assertTrue(plan.waypoint());
	}

	@Test
	void anUnknownDestinationInventsNoCommand() {
		ShopNavigator.Plan plan = ShopNavigator.plan(destination(null, null),
				"Germany", "minecraft:overworld");

		assertNull(plan.spawnCommand());
		assertTrue(plan.waypoint());
		assertTrue(plan.explanation().contains("unknown"));
	}

	@Test
	void leavingTheNationCaseCannotMisfireOnCase() {
		// Nation names compare case-insensitively: the claim data's
		// capitalization never forks the rule.
		ShopNavigator.Plan plan = ShopNavigator.plan(destination("Paris", "France"),
				"FRANCE", "minecraft:overworld");

		assertNull(plan.spawnCommand());
		assertTrue(plan.waypoint());
	}

	@Test
	void aDifferentWorldNeverCountsAsAlreadyThere() {
		ShopNavigator.Plan plan = ShopNavigator.plan(destination("Paris", "France"),
				"France", "minecraft:the_moon");

		// The Moon is not France, whatever the claim lookup said.
		assertEquals("t spawn Paris", plan.spawnCommand());
		assertTrue(plan.waypoint());
	}

	@Test
	void theTrackerRunsTheLifecycleRules() {
		ShopDestinationTracker tracker = new ShopDestinationTracker();
		assertTrue(tracker.destination() == null);

		tracker.set(destination("Paris", "France"));
		assertTrue(tracker.destination() != null);

		// Reaching and inspecting the shop clears it.
		tracker.clearFor("other|2");
		assertTrue(tracker.destination() != null);
		tracker.clearFor("s|1");
		assertTrue(tracker.destination() == null);

		// Leaving the target nation clears it — but only on affirmative
		// evidence.
		tracker.set(destination("Paris", "France"));
		tracker.tick("minecraft:overworld", "France");
		assertTrue(tracker.destination() != null);
		tracker.tick("minecraft:overworld", null);
		assertTrue(tracker.destination() != null);
		tracker.tick("minecraft:overworld", "Germany");
		assertTrue(tracker.destination() == null);

		// A world change ends the waypoint's session.
		tracker.set(destination("Paris", "France"));
		tracker.tick("minecraft:the_moon", "France");
		assertTrue(tracker.destination() == null);

		// A new navigation replaces the old one; one destination lives here.
		tracker.set(destination("Paris", "France"));
		tracker.set(destination("Lyon", "France"));
		assertEquals("Lyon", tracker.destination().town());
		assertFalse(tracker.destination().town().equals("Paris"));

		tracker.reset();
		assertTrue(tracker.destination() == null);
	}

	@Test
	void theDestinationLabelIsHonestAboutUnknowns() {
		assertEquals("unknown area · Ayskow",
				destination(null, null).label());
		assertEquals("Paris (France) · Ayskow",
				destination("Paris", "France").label());
		assertEquals("France · Ayskow", destination(null, "France").label());
	}
}
