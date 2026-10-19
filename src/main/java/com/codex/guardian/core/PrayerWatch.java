package com.codex.guardian.core;

/**
 * Times how fast prayer is draining, from the points the client shows, and so how long it will last. A
 * protection prayer that runs dry halfway through a fight is how many one-life accounts end: the hits that
 * were blocked start landing all at once. Guardian does not know which prayers are on or what your prayer
 * bonus is, and does not need to: it measures the fall itself, and a restore simply starts a new measurement.
 */
public final class PrayerWatch
{
	/** A drain is only trusted once it has been watched this many ticks. */
	static final int MIN_TICKS = 6;
	/** ...and has cost at least this many points, so one point lost to rounding is not a drain. */
	static final int MIN_DROP = 2;
	/** The drain is measured over at most this many recent ticks, so turning on one more prayer shows quickly. */
	static final int WINDOW = 30;

	private final int[] ticks = new int[WINDOW];
	private final int[] points = new int[WINDOW];
	/** Readings since the last restore, at most {@link #WINDOW}; the newest is at {@code (head - 1) % WINDOW}. */
	private int size;
	private int head;
	/** Points per tick over the window, or 0. */
	private double rate;

	/** Records the prayer points shown at a game tick. */
	public void observe(int now, int tick)
	{
		if (size > 0)
		{
			int last = (head - 1 + WINDOW) % WINDOW;
			if (now > points[last] || tick <= ticks[last])
			{
				// A restore, or a new login: the next fall is measured from here.
				size = 0;
			}
		}
		ticks[head] = tick;
		points[head] = now;
		head = (head + 1) % WINDOW;
		size = Math.min(WINDOW, size + 1);
		// The faster of the whole window and its newer half: a prayer just turned on shows at once, and a
		// stretch with prayers off never makes the drain look slower (and the prayer longer) than it is.
		rate = Math.max(rateOver(size), rateOver(Math.min(size, WINDOW / 2)));
	}

	/**
	 * Points per tick over the newest {@code readings} readings, or 0 when too short or too flat to trust. Points
	 * fall in whole steps, so the drain is timed from the first fall in the window to the last: timing from the
	 * window's edges would count part of a step and read a little slow.
	 */
	private double rateOver(int readings)
	{
		int firstTick = -1;
		int firstPoints = 0;
		int lastTick = -1;
		int lastPoints = 0;
		for (int k = 1; k < readings; k++)
		{
			int i = (head - readings + k + WINDOW) % WINDOW;
			int before = (i - 1 + WINDOW) % WINDOW;
			if (points[i] < points[before])
			{
				if (firstTick < 0)
				{
					firstTick = ticks[i];
					firstPoints = points[i];
				}
				lastTick = ticks[i];
				lastPoints = points[i];
			}
		}
		int span = lastTick - firstTick;
		int drop = firstPoints - lastPoints;
		if (firstTick < 0 || span < MIN_TICKS || drop < MIN_DROP)
		{
			return 0;
		}
		double rate = drop / (double) span;
		// No fall for longer than two of the drain's own steps: the prayers were turned off (or ran out), so
		// the drain measured before is no longer what is happening.
		int newest = ticks[(head - 1 + WINDOW) % WINDOW];
		return newest - lastTick > 2 / rate + 1 ? 0 : rate;
	}

	/** Forgets everything, as at logout. */
	public void reset()
	{
		size = 0;
		head = 0;
		rate = 0;
	}

	/** Points lost per tick, or 0 when prayer is not known to be draining. */
	public double getRate()
	{
		return rate;
	}

	/** Seconds until prayer runs out at the measured drain, or -1 when it is not draining. */
	public int secondsLeft(int points)
	{
		if (rate <= 0)
		{
			return -1;
		}
		return (int) Math.round(points / rate * 0.6);
	}

	/**
	 * Seconds of prayer counting the restore doses carried, each worth what a prayer potion gives at this prayer
	 * level (7 plus a quarter of the level; a super restore gives one more), or -1 when prayer is not draining.
	 */
	public int secondsWithDoses(int points, int maxPrayer, int doses)
	{
		if (rate <= 0)
		{
			return -1;
		}
		int perDose = 7 + Math.max(0, maxPrayer) / 4;
		return (int) Math.round((points + (long) Math.max(0, doses) * perDose) / rate * 0.6);
	}

	/**
	 * Seconds rounded to what the panel shows, so a drain does not send the panel a new read every tick: to 5s
	 * under a minute, 30s under ten minutes, whole minutes above. -1 stays -1.
	 */
	public static int rounded(int seconds)
	{
		if (seconds < 0)
		{
			return -1;
		}
		int step = seconds < 60 ? 5 : seconds < 600 ? 30 : 60;
		// Rounded down, so the panel never promises more prayer than there is.
		return seconds / step * step;
	}

	/** "45s", "3m", "1h 5m": short, for a ledger row. */
	public static String describe(int seconds)
	{
		if (seconds < 0)
		{
			return "";
		}
		if (seconds < 90)
		{
			return seconds + "s";
		}
		int minutes = (int) Math.round(seconds / 60.0);
		return minutes < 60 ? minutes + "m" : minutes / 60 + "h " + minutes % 60 + "m";
	}
}
