package com.codex.guardian.core;

/**
 * Notices a close call: hitpoints falling to the threshold or below and then climbing back.
 * It reports once the danger has passed, with the lowest point, so the journal records a
 * survival rather than a scare that is still going on.
 */
public final class CloseCallWatch
{
	private boolean active;
	private int lowest;
	private String place;
	private String source;

	/** The lowest point of a close call that just ended, or null. */
	public static final class Result
	{
		public final int lowest;
		public final String place;
		public final String source;

		Result(int lowest, String place, String source)
		{
			this.lowest = lowest;
			this.place = place;
			this.source = source;
		}
	}

	/**
	 * @param thresholdPercent hitpoints at or below this share of the maximum start a close call
	 * @return the finished close call, once hitpoints have recovered well above the threshold
	 */
	public Result update(int hitpoints, int maxHitpoints, int thresholdPercent, String place, String source)
	{
		if (maxHitpoints <= 0 || hitpoints <= 0)
		{
			return null;
		}
		int threshold = Math.max(1, maxHitpoints * thresholdPercent / 100);
		if (hitpoints <= threshold)
		{
			if (!active || hitpoints < lowest)
			{
				lowest = hitpoints;
				this.place = place;
			}
			if (source != null)
			{
				this.source = source;
			}
			active = true;
			return null;
		}
		// Recovering past half again (or comfortably above the threshold) closes the episode.
		int recovered = Math.max(threshold + Math.max(2, maxHitpoints / 10), maxHitpoints / 2);
		if (active && hitpoints >= Math.min(recovered, maxHitpoints))
		{
			active = false;
			Result r = new Result(lowest, this.place, this.source);
			this.source = null;
			return r;
		}
		return null;
	}

	/** Forgets an episode in progress, for a death or logout. */
	public void reset()
	{
		active = false;
		source = null;
	}

	public boolean isActive()
	{
		return active;
	}
}
