package com.codex.guardian.core;

/**
 * The last seconds of a close call or a death, tick by tick: hitpoints, the damage taken, and the food and
 * potions used. It answers the question every survivor asks afterwards, "what actually happened there?", from
 * what the client showed, and is kept in the journal beside the entry it belongs to.
 *
 * <p>Plain data with int arrays so Gson saves it compactly; every array has the same length, oldest tick first.</p>
 */
public final class Replay
{
	/** At most this many ticks are kept per replay: about 29 seconds. */
	public static final int MAX_TICKS = 48;
	/** Ticks kept from before hitpoints first fell low, so the hits that took them there are in the picture. */
	static final int LEAD_TICKS = 8;
	/** A death keeps the ticks that led to it. */
	static final int DEATH_TICKS = 30;

	int max;
	int[] hp;
	int[] hit;
	int[] ate;
	int[] drank;
	/** Who dealt the single biggest hit, and how big it was. */
	String worstSource;
	int worstHit;

	Replay()
	{
	}

	public int getMax()
	{
		return max;
	}

	public int ticks()
	{
		return hp == null ? 0 : hp.length;
	}

	public int hpAt(int i)
	{
		return hp[i];
	}

	public int hitAt(int i)
	{
		return hit[i];
	}

	public int ateAt(int i)
	{
		return ate[i];
	}

	public int drankAt(int i)
	{
		return drank[i];
	}

	public String getWorstSource()
	{
		return worstSource;
	}

	public int getWorstHit()
	{
		return worstHit;
	}

	/** The tick with the fewest hitpoints, the first one if several tie. */
	public int lowestIndex()
	{
		int best = 0;
		for (int i = 1; i < ticks(); i++)
		{
			if (hp[i] < hp[best])
			{
				best = i;
			}
		}
		return best;
	}

	/** True when the arrays are whole and of one length, as a saved replay must be before it is drawn. */
	public boolean isValid()
	{
		int n = ticks();
		return n >= 2 && n <= MAX_TICKS && max > 0 && hit != null && hit.length == n && ate != null && ate.length == n
			&& drank != null && drank.length == n;
	}

	/**
	 * Damage taken over the fall ({@link #fallTicks}): the hits after its starting tick up to and including the
	 * lowest point, so chip damage taken while still healthy is not counted in "Took 68 in 1s".
	 */
	public int damageToLowest()
	{
		int total = 0;
		int low = lowestIndex();
		for (int i = fallStart() + (low > 0 ? 1 : 0); i <= low; i++)
		{
			total += hit[i];
		}
		return total;
	}

	/**
	 * The ticks over which most of the fall happened: from the last tick at or above three quarters of the
	 * starting hitpoints (or the first tick) to the lowest point.
	 */
	public int fallTicks()
	{
		return Math.max(1, lowestIndex() - fallStart());
	}

	/** The last tick before the lowest point at or above three quarters of the starting hitpoints, or 0. */
	private int fallStart()
	{
		int low = lowestIndex();
		int from = 0;
		int top = hp[0];
		for (int i = 0; i < low; i++)
		{
			if (hp[i] >= top * 3 / 4)
			{
				from = i;
			}
		}
		return from;
	}

	public int totalAte()
	{
		return sum(ate);
	}

	public int totalDrank()
	{
		return sum(drank);
	}

	/** "Lost 61 in 4s · ate 3 · drank 1" — the replay in a line. */
	public String summary()
	{
		StringBuilder b = new StringBuilder();
		int lost = damageToLowest();
		if (lost > 0)
		{
			long seconds = Math.max(1, Math.round(fallTicks() * 0.6));
			b.append("Took ").append(lost).append(" in ").append(seconds).append('s');
		}
		else
		{
			b.append("No hits seen");
		}
		int eaten = totalAte();
		int drunk = totalDrank();
		if (eaten > 0)
		{
			b.append(" · ate ").append(eaten);
		}
		if (drunk > 0)
		{
			b.append(" · drank ").append(drunk);
		}
		if (eaten == 0 && drunk == 0)
		{
			b.append(" · nothing eaten");
		}
		return b.toString();
	}

	/** "Biggest hit 38, Vorkath", or null without a hit. */
	public String worstLine()
	{
		if (worstHit <= 0)
		{
			return null;
		}
		return "Biggest hit " + worstHit + (worstSource != null ? ", " + worstSource : "");
	}

	private static int sum(int[] values)
	{
		int total = 0;
		for (int v : values)
		{
			total += v;
		}
		return total;
	}

	/**
	 * Keeps the last minute or so of ticks and cuts a replay from them when a close call ends or a death comes.
	 * The plugin feeds it every tick while logged in; it costs a few ints per tick.
	 */
	public static final class Recorder
	{
		/** A little over a close call's longest replay plus its lead. */
		static final int CAPACITY = 96;

		private final int[] hp = new int[CAPACITY];
		private final int[] max = new int[CAPACITY];
		private final int[] hit = new int[CAPACITY];
		private final int[] ate = new int[CAPACITY];
		private final int[] drank = new int[CAPACITY];
		private final String[] worstSource = new String[CAPACITY];
		private final int[] worstHit = new int[CAPACITY];
		/** How many ticks have ever been recorded; the newest is at {@code (count - 1) % CAPACITY}. */
		private long count;
		/** The tick (in {@link #count} terms) when hitpoints first fell low in this episode, or -1. */
		private long markedAt = -1;

		private int pendingHit;
		private int pendingWorst;
		private String pendingSource;
		private int lastFood = -1;
		private int lastDoses = -1;

		/** A hit on the player, between ticks; it lands on the next recorded tick. */
		public void hit(int amount, String source)
		{
			if (amount <= 0)
			{
				return;
			}
			pendingHit += amount;
			if (amount > pendingWorst)
			{
				pendingWorst = amount;
				pendingSource = source;
			}
		}

		/**
		 * Records one tick.
		 *
		 * @param food  food items carried now
		 * @param doses potion doses carried now
		 */
		public void tick(int hitpoints, int maxHitpoints, int food, int doses)
		{
			int i = (int) (count % CAPACITY);
			hp[i] = Math.max(0, hitpoints);
			max[i] = maxHitpoints;
			hit[i] = pendingHit;
			worstHit[i] = pendingWorst;
			worstSource[i] = pendingSource;
			// A small drop is eating or drinking; a big one is the bank or a drop, which is not.
			ate[i] = lastFood >= 0 && food < lastFood && lastFood - food <= 3 ? lastFood - food : 0;
			drank[i] = lastDoses >= 0 && doses < lastDoses && lastDoses - doses <= 2 ? lastDoses - doses : 0;
			lastFood = food;
			lastDoses = doses;
			pendingHit = 0;
			pendingWorst = 0;
			pendingSource = null;
			count++;
		}

		/** Hitpoints are low now: the replay will start a little before this tick. Later marks keep the first. */
		public void mark()
		{
			if (markedAt < 0 && count > 0)
			{
				markedAt = count - 1;
			}
		}

		public boolean isMarked()
		{
			return markedAt >= 0;
		}

		/**
		 * The replay of the close call that just ended: from a few ticks before hitpoints fell low to now. A long
		 * episode keeps the window of {@link Replay#MAX_TICKS} that holds its lowest point. Null without a mark.
		 */
		public Replay closeCall()
		{
			if (markedAt < 0 || count == 0)
			{
				return null;
			}
			long first = Math.max(Math.max(0, count - CAPACITY), markedAt - LEAD_TICKS);
			long last = count - 1;
			markedAt = -1;
			if (last - first + 1 > MAX_TICKS)
			{
				long low = first;
				for (long t = first; t <= last; t++)
				{
					if (hp[(int) (t % CAPACITY)] < hp[(int) (low % CAPACITY)])
					{
						low = t;
					}
				}
				// Keep the lead into the lowest point and as much of the recovery as fits.
				first = Math.max(first, low - (MAX_TICKS - 12));
				last = Math.min(last, first + MAX_TICKS - 1);
			}
			return cut(first, last);
		}

		/** The replay of a death: the ticks that led up to it. Null when nothing has been recorded. */
		public Replay death()
		{
			markedAt = -1;
			if (count == 0)
			{
				return null;
			}
			long last = count - 1;
			long first = Math.max(Math.max(0, count - CAPACITY), last - DEATH_TICKS + 1);
			return cut(first, last);
		}

		/** Forgets an episode in progress, as after a death. */
		public void unmark()
		{
			markedAt = -1;
		}

		/** Forgets everything, as at logout or a world hop. */
		public void clear()
		{
			count = 0;
			markedAt = -1;
			pendingHit = 0;
			pendingWorst = 0;
			pendingSource = null;
			lastFood = -1;
			lastDoses = -1;
		}

		private Replay cut(long first, long last)
		{
			int n = (int) (last - first + 1);
			if (n < 2)
			{
				return null;
			}
			Replay r = new Replay();
			r.hp = new int[n];
			r.hit = new int[n];
			r.ate = new int[n];
			r.drank = new int[n];
			for (int k = 0; k < n; k++)
			{
				int i = (int) ((first + k) % CAPACITY);
				r.hp[k] = hp[i];
				r.hit[k] = hit[i];
				r.ate[k] = ate[i];
				r.drank[k] = drank[i];
				r.max = Math.max(r.max, max[i]);
				if (worstHit[i] > r.worstHit)
				{
					r.worstHit = worstHit[i];
					r.worstSource = worstSource[i];
				}
			}
			return r;
		}
	}
}
