package net.zaminmc.zea.browser;

/**
 * A value that eases toward a target over a fixed duration — NEU's
 * {@code LerpingFloat}, ported verbatim so the pane, the info pane and
 * the edge tab slide exactly the way NEU's slide (200 ms, the same
 * curve). {@code tick()} advances the animation once per frame.
 */
final class Lerp {
	private final int timeToReachTarget;
	private int timeSpent;
	private long lastMillis;
	private float targetValue;
	private float lerpValue;

	Lerp(float initialValue) {
		this(initialValue, 200);
	}

	Lerp(float initialValue, int timeToReachTarget) {
		this.targetValue = initialValue;
		this.lerpValue = initialValue;
		this.timeToReachTarget = timeToReachTarget;
		this.lastMillis = System.currentTimeMillis();
	}

	void tick() {
		long now = System.currentTimeMillis();
		int lastTimeSpent = timeSpent;
		timeSpent += now - lastMillis;

		float lastDistPercentToTarget = lastTimeSpent / (float) timeToReachTarget;
		float distPercentToTarget = timeSpent / (float) timeToReachTarget;
		if (distPercentToTarget <= 0) {
			lastMillis = now;
			return;
		}
		float fac = (1 - lastDistPercentToTarget) / distPercentToTarget;

		float startValue = lerpValue - (targetValue - lerpValue) / fac;

		float dist = targetValue - startValue;
		if (dist == 0) {
			lastMillis = now;
			return;
		}

		float oldLerpValue = lerpValue;
		if (distPercentToTarget >= 1) {
			lerpValue = targetValue;
		} else {
			lerpValue = startValue + dist * distPercentToTarget;
		}

		if (lerpValue == oldLerpValue) {
			timeSpent = lastTimeSpent;
		} else {
			lastMillis = now;
		}
	}

	void setTarget(float targetValue) {
		this.targetValue = targetValue;
	}

	/** Jumps straight to a value, clearing any animation in flight. */
	void setValue(float value) {
		this.targetValue = value;
		this.lerpValue = value;
		this.timeSpent = timeToReachTarget;
	}

	float getValue() {
		return lerpValue;
	}

	float getTarget() {
		return targetValue;
	}
}
