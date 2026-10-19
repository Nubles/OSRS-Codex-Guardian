package com.codex.guardian.core;

/**
 * Watches the rhythm of game ticks for lag. The server sends a tick every 600 ms; when they arrive late, or
 * stop and then rush in, the game you see is behind the game the server is playing, and eating or teleporting
 * lands late. Lag kills more one-life accounts than any boss, so Guardian says so the moment it starts.
 *
 * <p>It cannot tell a bad connection from a stalling computer, and does not need to: either way your clicks land
 * late. Only the gap between ticks is measured, from the client's own clock.
 */
public final class Lag
{
	public enum State
	{
		/** Ticks arriving on time. */
		STEADY,
		/** Several late ticks lately: inputs may land a tick or two behind. */
		SHAKY,
		/** A long freeze just now: the game stopped for seconds. */
		FROZEN
	}

	/** Ticks that arrive this late are counted as late. */
	static final long LATE_MS = 1_000;
	/** A gap this long is a freeze, not just a slow tick. */
	static final long FREEZE_MS = 2_400;
	/** How many recent ticks are weighed. */
	static final int WINDOW = 20;
	/** Late ticks in the window that make the connection shaky. */
	static final int SHAKY_LATE = 2;
	/** A freeze keeps the state FROZEN for this many ticks after it ends, so it is seen. */
	static final int FREEZE_HOLD = 10;

	private final long[] gaps = new long[WINDOW];
	private int cursor;
	private int filled;
	private long last = -1;
	private int sinceFreeze = Integer.MAX_VALUE;
	private long worstGap;

	/** Records a tick arriving at {@code nowMs} (any steady clock). */
	public void tick(long nowMs)
	{
		if (last >= 0)
		{
			long gap = nowMs - last;
			gaps[cursor] = gap;
			cursor = (cursor + 1) % WINDOW;
			filled = Math.min(WINDOW, filled + 1);
			if (gap >= FREEZE_MS)
			{
				sinceFreeze = 0;
				worstGap = gap;
			}
			else if (sinceFreeze < Integer.MAX_VALUE)
			{
				sinceFreeze++;
			}
		}
		last = nowMs;
	}

	/** Ignores the gap before the next tick, as after a region load, whose pause is on purpose. */
	public void skip()
	{
		last = -1;
	}

	/** Forgets everything, as after a login or a world hop, whose own pauses are not lag. */
	public void reset()
	{
		java.util.Arrays.fill(gaps, 0);
		cursor = 0;
		filled = 0;
		last = -1;
		sinceFreeze = Integer.MAX_VALUE;
		worstGap = 0;
	}

	/** Late ticks among the recent ones. */
	public int lateTicks()
	{
		int late = 0;
		for (int i = 0; i < filled; i++)
		{
			if (gaps[i] >= LATE_MS)
			{
				late++;
			}
		}
		return late;
	}

	public State state()
	{
		if (sinceFreeze < FREEZE_HOLD)
		{
			return State.FROZEN;
		}
		return lateTicks() >= SHAKY_LATE ? State.SHAKY : State.STEADY;
	}

	/** The length of the last freeze, in milliseconds, or 0 if there has been none. */
	public long getWorstGap()
	{
		return worstGap;
	}

	/** A short line for the panel: "Steady", "3 late ticks", "Froze for 4s". */
	public String describe()
	{
		switch (state())
		{
			case FROZEN:
				return "Froze for " + Math.round(worstGap / 1000.0) + "s";
			case SHAKY:
				return lateTicks() + " late ticks";
			default:
				return "Steady";
		}
	}
}
