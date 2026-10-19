package com.codex.guardian.ui;

/** Turns ticks and timestamps into the short phrases the panel uses. */
public final class Format
{
	private static final double SECONDS_PER_TICK = 0.6;

	private Format()
	{
	}

	/** "3h 12m", "45m" or "20s" for a number of game ticks. */
	/** A run's play time: as {@link #duration}, but "<1m" for its first minute rather than a ticking seconds count. */
	public static String runTime(long ticks)
	{
		return Math.round(ticks * SECONDS_PER_TICK) < 60 ? "<1m" : duration(ticks);
	}

	public static String duration(long ticks)
	{
		long seconds = Math.round(ticks * SECONDS_PER_TICK);
		long hours = seconds / 3600;
		long minutes = seconds % 3600 / 60;
		if (hours > 0)
		{
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0)
		{
			return minutes + "m";
		}
		return seconds + "s";
	}

	/** "just now", "5m ago", "3h ago", "2d ago". */
	public static String ago(long then, long now)
	{
		long seconds = Math.max(0, (now - then) / 1000);
		if (seconds < 60)
		{
			return "just now";
		}
		if (seconds < 3600)
		{
			return seconds / 60 + "m ago";
		}
		if (seconds < 86400)
		{
			return seconds / 3600 + "h ago";
		}
		return seconds / 86400 + "d ago";
	}
}
