package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hardest each kind of monster has ever hit this account. Guardian learns it from the hits you take, so the
 * next time one of them is on you it knows, before the first hit lands, whether a hit like that could end the run.
 *
 * <p>A hit is only credited to a monster when it is the one thing attacking you: with two on you there is no
 * telling which hit was whose. Monsters are told apart by name and combat level, since a level 22 skeleton and a
 * level 85 one hit very differently. What is remembered is the most you have taken, not the most the monster can
 * do: protection prayers and armour hold the figure down, so it is a floor, and the panel says "has hit you for".
 *
 * <p>Plain data, saved as JSON per account; it outlives a new run, since a black demon hits as hard on the next one.
 */
public final class HitMemory
{
	/** The most monsters remembered; past this the least threatening are forgotten first. */
	static final int MAX_RECORDS = 300;

	/** What one kind of monster has done to you. */
	public static final class Record
	{
		String name;
		int level;
		int max;
		int hits;
		/** Where the hardest hit landed, or null. */
		String place;
		long at;

		public String getName()
		{
			return name;
		}

		public int getLevel()
		{
			return level;
		}

		/** The hardest single hit taken from it. */
		public int getMax()
		{
			return max;
		}

		/** How many hits from it have been counted. */
		public int getHits()
		{
			return hits;
		}

		public String getPlace()
		{
			return place;
		}

		public long getAt()
		{
			return at;
		}
	}

	Map<String, Record> records = new LinkedHashMap<>();

	/** Gson leaves fields it did not find as null, and an edited file may hold nonsense; this puts both right. */
	public HitMemory repair()
	{
		if (records == null)
		{
			records = new LinkedHashMap<>();
		}
		records.values().removeIf(r -> r == null || r.name == null || r.max <= 0);
		return this;
	}

	/**
	 * True for an item whose use hurts you: divine potions, overloads, Zamorak brews and mixes, the dwarven rock
	 * cake and the locator orb. Their hitsplat looks like any other, so a hit landing just after one is used is
	 * never credited to the monster you are fighting (a goblin has not "hit you for 10" because you drank a divine
	 * super combat).
	 */
	public static boolean hurtsToUse(String item)
	{
		if (item == null)
		{
			return false;
		}
		String name = item.trim().toLowerCase();
		return name.startsWith("divine ") || name.startsWith("overload") || name.startsWith("zamorak brew")
			|| name.startsWith("zamorak mix") || name.contains("rock cake") || name.startsWith("locator orb");
	}

	private static String key(String name, int level)
	{
		return name + "|" + level;
	}

	/**
	 * Counts one hit from a monster that was alone in attacking you.
	 *
	 * @return true when this is the hardest it has hit you yet
	 */
	public boolean record(String name, int level, int amount, String place, long now)
	{
		if (name == null || name.isEmpty() || amount <= 0)
		{
			return false;
		}
		Record r = records.get(key(name, level));
		if (r == null)
		{
			r = new Record();
			r.name = name;
			r.level = level;
			records.put(key(name, level), r);
		}
		r.hits++;
		if (amount <= r.max)
		{
			return false;
		}
		r.max = amount;
		r.place = place;
		r.at = now;
		trim(r);
		return true;
	}

	/** The hardest hit taken from this monster, or 0 when it has never hit you alone. */
	public int max(String name, int level)
	{
		Record r = name == null ? null : records.get(key(name, level));
		return r == null ? 0 : r.max;
	}

	/** The monsters that have hit hardest, hardest first. */
	public List<Record> hardest(int limit)
	{
		List<Record> list = new ArrayList<>(records.values());
		list.sort((a, b) -> b.max != a.max ? Integer.compare(b.max, a.max) : Long.compare(b.at, a.at));
		return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
	}

	/** The hardest hit that landed at this place, or null when none did. Wilderness levels count as one place. */
	public Record hardestAt(String place)
	{
		if (place == null)
		{
			return null;
		}
		String area = Journal.area(place);
		Record best = null;
		for (Record r : records.values())
		{
			if (r.place != null && Journal.area(r.place).equals(area) && (best == null || r.max > best.max))
			{
				best = r;
			}
		}
		return best;
	}

	public int size()
	{
		return records.size();
	}

	public Map<String, Record> getRecords()
	{
		return Collections.unmodifiableMap(records);
	}

	/** Forgets the softest hitters once there are too many to keep; never the one just learned. */
	private void trim(Record keep)
	{
		while (records.size() > MAX_RECORDS)
		{
			String weakest = null;
			Record low = null;
			for (Map.Entry<String, Record> e : records.entrySet())
			{
				Record r = e.getValue();
				if (r != keep && (low == null || r.max < low.max || r.max == low.max && r.at < low.at))
				{
					low = r;
					weakest = e.getKey();
				}
			}
			records.remove(weakest);
		}
	}
}
