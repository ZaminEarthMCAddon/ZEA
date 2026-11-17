package net.zaminmc.zea.nav;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * One observed spawn navigation: the command the player dispatched, and
 * whatever arrival evidence the client could gather afterwards.
 *
 * <p>The command may have been typed, clicked through a link or sent by
 * a macro — the observer sees them all at the same choke point. {@code
 * requested} keeps the argument exactly as the player wrote it (the
 * form the server accepted or rejected); {@code normalized} is the
 * lowercase lookup form for comparisons.
 *
 * <p>Arrival is evidence, never an assumption: the attempt records one
 * only when the player actually jumped far enough after the dispatch —
 * a command that was sent says nothing about whether it succeeded. A
 * permission failure, a cost the player could not pay, a cooldown or an
 * unknown destination all look identical to the client: no teleport.
 * {@link #arrivalEstablished()} therefore states what could be
 * established, not what the server decided.
 *
 * <p>These records are context for locating shops. The clicked sign's
 * position — never an inferred nation membership — stays the
 * authoritative location of a shop.
 */
public record SpawnAttempt(Type type, String requested, String normalized,
		String world, String serverId,
		double fromX, double fromZ, Instant dispatchedAt,
		@Nullable Double arrivalX, @Nullable Double arrivalZ, @Nullable Instant arrivalAt) {

	public SpawnAttempt {
		if (type == null || requested == null || normalized == null || world == null
				|| serverId == null || dispatchedAt == null) {
			throw new NullPointerException("spawn attempt fields must not be null");
		}
	}

	/** The spawn command kinds ZEA tracks. */
	public enum Type {
		/** {@code /n spawn <nation>} */
		NATION,
		/** {@code /t spawn <town>} */
		TOWN
	}

	/** An arrival was actually observed: the player jumped after dispatch. */
	public boolean arrivalEstablished() {
		return arrivalX != null && arrivalZ != null && arrivalAt != null;
	}

	/** The arrival-filled copy of a still-unconfirmed attempt. */
	public SpawnAttempt confirmed(double x, double z, Instant at) {
		if (arrivalEstablished()) {
			return this;
		}
		return new SpawnAttempt(type, requested, normalized, world, serverId,
				fromX, fromZ, dispatchedAt, x, z, at);
	}

	/** A fresh attempt at dispatch time. */
	public static SpawnAttempt start(Type type, String requested, String world, String serverId,
			double x, double z, Instant at) {
		return new SpawnAttempt(type, requested,
				requested.toLowerCase(Locale.ROOT), world, serverId, x, z, at,
				null, null, null);
	}

	/** Equality with dispatch-time precision; arrival evidence included. */
	@Override
	public boolean equals(@Nullable Object other) {
		return other instanceof SpawnAttempt attempt
				&& type == attempt.type
				&& requested.equals(attempt.requested)
				&& normalized.equals(attempt.normalized)
				&& world.equals(attempt.world)
				&& serverId.equals(attempt.serverId)
				&& fromX == attempt.fromX
				&& fromZ == attempt.fromZ
				&& dispatchedAt.equals(attempt.dispatchedAt)
				&& Objects.equals(arrivalX, attempt.arrivalX)
				&& Objects.equals(arrivalZ, attempt.arrivalZ)
				&& Objects.equals(arrivalAt, attempt.arrivalAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, requested, normalized, world, serverId,
				fromX, fromZ, dispatchedAt, arrivalX, arrivalZ, arrivalAt);
	}
}
