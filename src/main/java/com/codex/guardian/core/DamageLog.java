package com.codex.guardian.core;

/**
 * A short memory of the damage you have taken, one slot per game tick, so Guardian can tell
 * a scratch from a beating and estimate how long your hitpoints will last.
 */
public final class DamageLog
{
	private static final int PACE_TICKS = 8;
	private static final int MAX_HIT_TICKS = 50;

	private final int[] perTick = new int[MAX_HIT_TICKS];
	private final int[] biggest = new int[MAX_HIT_TICKS];
	private int cursor;
	private int pending;
	private int pendingBiggest;

	/** Records one hitsplat landing on you during the current tick. */
	public void hit(int amount)
	{
		if (amount <= 0)
		{
			return;
		}
		pending += amount;
		pendingBiggest = Math.max(pendingBiggest, amount);
	}

	/** Closes the current tick and starts the next. */
	public void tick()
	{
		cursor = (cursor + 1) % MAX_HIT_TICKS;
		perTick[cursor] = pending;
		biggest[cursor] = pendingBiggest;
		pending = 0;
		pendingBiggest = 0;
	}

	public void clear()
	{
		java.util.Arrays.fill(perTick, 0);
		java.util.Arrays.fill(biggest, 0);
		pending = 0;
		pendingBiggest = 0;
	}

	/** Average damage per tick over the last few ticks. */
	public double pace()
	{
		int sum = 0;
		for (int i = 0; i < PACE_TICKS; i++)
		{
			sum += perTick[(cursor - i + MAX_HIT_TICKS) % MAX_HIT_TICKS];
		}
		return sum / (double) PACE_TICKS;
	}

	/** The biggest single hit in the last thirty seconds. */
	public int recentMaxHit()
	{
		int max = 0;
		for (int value : biggest)
		{
			max = Math.max(max, value);
		}
		return max;
	}
}
