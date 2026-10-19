package com.codex.guardian.core;

/**
 * The run's seal: a metal earned by hours played on one life, bronze to dragon, the way the game ranks its own
 * metals. It only ever counts time Guardian watched, so it cannot be bought or rushed; a fallen run keeps the seal
 * it reached, cracked.
 */
public final class Seal
{
	public enum Tier
	{
		BRONZE("Bronze", 0),
		IRON("Iron", 5),
		STEEL("Steel", 15),
		BLACK("Black", 30),
		MITHRIL("Mithril", 60),
		ADAMANT("Adamant", 120),
		RUNE("Rune", 250),
		DRAGON("Dragon", 500);

		private final String label;
		private final int hours;

		Tier(String label, int hours)
		{
			this.label = label;
			this.hours = hours;
		}

		public String getLabel()
		{
			return label;
		}

		/** Hours of play the tier is earned at. */
		public int getHours()
		{
			return hours;
		}

		/** The tier after this one, or null for dragon, the last. */
		public Tier next()
		{
			Tier[] all = values();
			return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
		}
	}

	/** Game ticks in an hour of play. */
	static final long TICKS_PER_HOUR = 6000;

	private Seal()
	{
	}

	/** The seal a run has earned after this many ticks of play. */
	public static Tier tier(long playedTicks)
	{
		Tier found = Tier.BRONZE;
		for (Tier t : Tier.values())
		{
			if (playedTicks >= t.hours * TICKS_PER_HOUR)
			{
				found = t;
			}
		}
		return found;
	}

	/** How far the run is from its seal to the next one, 0 to 1; 1 once the last seal is earned. */
	public static double progress(long playedTicks)
	{
		Tier now = tier(playedTicks);
		Tier next = now.next();
		if (next == null)
		{
			return 1;
		}
		long from = now.hours * TICKS_PER_HOUR;
		long to = next.hours * TICKS_PER_HOUR;
		return Math.max(0, Math.min(1, (playedTicks - from) / (double) (to - from)));
	}

	/** Whole hours of play still needed for the next seal, at least 1; 0 once the last seal is earned. */
	public static long hoursToNext(long playedTicks)
	{
		Tier next = tier(playedTicks).next();
		if (next == null)
		{
			return 0;
		}
		long left = next.hours * TICKS_PER_HOUR - Math.max(0, playedTicks);
		return Math.max(1, (left + TICKS_PER_HOUR - 1) / TICKS_PER_HOUR);
	}
}
