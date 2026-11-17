package net.zaminmc.zea.nav;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Observes the player's spawn navigation commands and the arrivals they
 * produce. Commands reach it at the one choke point every origin
 * shares — vanilla's outgoing command send — so typed commands, ZEA's
 * own links and any macro-driven dispatch are recorded exactly the same
 * way. The observer never intercepts, cancels or rewrites a command;
 * it watches the dispatch go out.
 *
 * <p>Arrival is resolved from movement evidence: a jump of at least
 * {@link #ARRIVAL_MIN_DISTANCE} blocks within {@link #ARRIVAL_WINDOW}
 * of the dispatch confirms the teleport, in whatever direction it
 * landed. The window closing without evidence leaves the attempt
 * unconfirmed — the client does not know whether the command failed,
 * only that no teleport happened, and it never claims more.
 *
 * <p>The history is session-scoped and bounded: it belongs to one
 * connection (a disconnect clears it) and the oldest attempts fall off
 * a bounded deque. The resolved attempts are context for the market's
 * town/nation resolution and for smart navigation; the last confirmed
 * arrival supplements, never replaces, actual claim geometry.
 */
public final class SpawnCommandObserver {
	/** How many recent attempts the history keeps. */
	public static final int MAX_ATTEMPTS = 8;

	/** How long after a dispatch an arrival teleport may confirm. */
	public static final Duration ARRIVAL_WINDOW = Duration.ofSeconds(20);

	/** The position change that counts as a spawn arrival. */
	public static final double ARRIVAL_MIN_DISTANCE = 32.0;

	private final ArrayDeque<SpawnAttempt> attempts = new ArrayDeque<>();
	/** The dispatched attempt still awaiting arrival evidence. */
	private SpawnAttempt pending;
	private Instant pendingAt = Instant.EPOCH;

	/**
	 * Parses one outgoing command line and returns the spawn navigation
	 * it carries, or {@code empty} for anything else — unrelated
	 * commands are not recorded and never altered. The leading slash is
	 * optional: the send path hands the command over with it, but the
	 * parser does not depend on that.
	 */
	public static Optional<SpawnAttempt> startFrom(String command, String world, String serverId,
			double x, double z, Instant at) {
		return parse(command).map(parsed ->
				SpawnAttempt.start(parsed.type(), parsed.requested(), world, serverId, x, z, at));
	}

	/** The spawn navigation inside one command line, or {@code empty}. */
	public static Optional<ParsedSpawn> parse(String command) {
		if (command == null) {
			return Optional.empty();
		}
		String trimmed = command.trim();
		// The send path hands the command over without its leading
		// slash; the parser accepts both so a slash-prefixed caller
		// never slips through unrecorded.
		String stripped = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
		String line = stripped.toLowerCase(Locale.ROOT);
		SpawnAttempt.Type type;
		String body;
		if (line.startsWith("n spawn ")) {
			type = SpawnAttempt.Type.NATION;
			body = stripped.substring("n spawn ".length());
		} else if (line.startsWith("t spawn ")) {
			type = SpawnAttempt.Type.TOWN;
			body = stripped.substring("t spawn ".length());
		} else {
			return Optional.empty();
		}
		// The argument in the form the player wrote it, never rewritten:
		// the server accepts the name it accepts, capitals included.
		String requested = body.trim();
		if (requested.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ParsedSpawn(type, requested,
				requested.toLowerCase(Locale.ROOT)));
	}

	/**
	 * Records one dispatch. The caller reads the player's position
	 * before the command goes out; the attempt becomes the pending one
	 * whose arrival the ticks look for.
	 */
	public void observe(SpawnAttempt attempt) {
		attempts.addLast(attempt);
		while (attempts.size() > MAX_ATTEMPTS) {
			attempts.removeFirst();
		}
		pending = attempt;
		pendingAt = attempt.dispatchedAt();
	}

	/**
	 * Resolves the pending attempt against the player's current
	 * position — {@code position} is {@code null} when no player is
	 * loaded. A confirmed arrival fills the evidence and retires the
	 * pending attempt; the window closing without evidence retires it
	 * unconfirmed.
	 */
	public void tick(@Nullable double[] position, Instant now) {
		if (pending == null) {
			return;
		}
		// The window bounds the evidence: a jump that lands after it
		// cannot be the teleport the dispatch asked for.
		if (Duration.between(pendingAt, now).compareTo(ARRIVAL_WINDOW) >= 0) {
			pending = null;
			return;
		}
		if (position == null) {
			pending = null;
			return;
		}
		double distance = Math.hypot(position[0] - pending.fromX(), position[1] - pending.fromZ());
		if (distance >= ARRIVAL_MIN_DISTANCE) {
			replace(pending, pending.confirmed(position[0], position[1], now));
			pending = null;
		}
	}

	/** The recent attempts, oldest first. */
	public List<SpawnAttempt> attempts() {
		return List.copyOf(attempts);
	}

	/** The most recent confirmed arrival, if one exists. */
	public Optional<SpawnAttempt> lastArrival() {
		var confirmed = attempts.descendingIterator();
		while (confirmed.hasNext()) {
			SpawnAttempt attempt = confirmed.next();
			if (attempt.arrivalEstablished()) {
				return Optional.of(attempt);
			}
		}
		return Optional.empty();
	}

	/**
	 * Whether the last confirmed arrival says the player spawned at
	 * {@code name} (nation or town, in its normalized form) more
	 * recently than any conflicting spawn. This is supplementary
	 * context: claim geometry outranks it wherever claims are known.
	 */
	public boolean lastArrivalWas(String name) {
		return lastArrival()
				.map(SpawnAttempt::normalized)
				.map(attempt -> attempt.equals(name.toLowerCase(Locale.ROOT)))
				.orElse(false);
	}

	/** Forgets everything (disconnect, world change). */
	public void reset() {
		attempts.clear();
		pending = null;
	}

	private void replace(SpawnAttempt oldAttempt, SpawnAttempt confirmed) {
		// History order is dispatch order: swap the confirmed copy in
		// where the unconfirmed one was recorded.
		List<SpawnAttempt> retained = new ArrayList<>();
		for (SpawnAttempt attempt : attempts) {
			retained.add(attempt == oldAttempt ? confirmed : attempt);
		}
		attempts.clear();
		attempts.addAll(retained);
	}

	/** One parsed spawn command: the kind and the requested destination. */
	public record ParsedSpawn(SpawnAttempt.Type type, String requested, String normalized) {
		public ParsedSpawn {
			if (type == null || requested == null || normalized == null) {
				throw new NullPointerException("parsed spawn fields must not be null");
			}
		}
	}
}
