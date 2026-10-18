package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The run journal: one account's story on its single life. It is plain data so the plugin can
 * keep it in the RuneLite profile as JSON, and every change goes through a method here so the
 * rules for what counts as a close call or a milestone live in one place.
 */
public final class Journal
{
	public enum Type
	{
		RUN_STARTED,
		ZONE,
		CLOSE_CALL,
		DEATH,
		STATUS_LOST,
		DISCONNECT,
		MILESTONE,
		/** A death in a safe minigame: worth remembering, but it costs nothing. */
		SAFE_DEATH
	}

	public static final class Entry
	{
		long at;
		Type type;
		String title;
		String detail;

		Entry(long at, Type type, String title, String detail)
		{
			this.at = at;
			this.type = type;
			this.title = title;
			this.detail = detail;
		}

		public long getAt()
		{
			return at;
		}

		public Type getType()
		{
			return type;
		}

		public String getTitle()
		{
			return title;
		}

		public String getDetail()
		{
			return detail;
		}
	}

	public static final class Hit
	{
		int amount;
		String source;
		String place;
		long at;

		public int getAmount()
		{
			return amount;
		}

		public String getSource()
		{
			return source;
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

	static final int MAX_ENTRIES = 250;
	/** Re-entering the same zone within this long does not write a new entry. */
	static final long ZONE_REPEAT_MS = 30 * 60 * 1000L;

	long runStartedAt;
	long playedTicks;
	long dangerTicks;
	int closeCalls;
	int deaths;
	/** Deaths in safe minigames, which do not end a run. */
	int safeDeaths;
	int disconnects;
	boolean statusLost;
	/** Set once Guardian has seen this account as hardcore, so a loss of status while away is still noticed. */
	boolean hardcoreSeen;
	int lowestHitpoints = -1;
	String lowestPlace;
	Hit biggestHit;
	Map<String, Integer> damageBySource = new LinkedHashMap<>();
	Map<String, Integer> closeCallsByPlace = new LinkedHashMap<>();
	List<Entry> entries = new ArrayList<>();

	/** Gson leaves fields it did not find as null; this puts them back. */
	public Journal repair()
	{
		if (damageBySource == null)
		{
			damageBySource = new LinkedHashMap<>();
		}
		if (closeCallsByPlace == null)
		{
			closeCallsByPlace = new LinkedHashMap<>();
		}
		if (entries == null)
		{
			entries = new ArrayList<>();
		}
		entries.removeIf(e -> e == null || e.type == null);
		// Older journals split Wilderness close calls by level; one row for the Wilderness ranks it honestly.
		Map<String, Integer> places = new LinkedHashMap<>();
		closeCallsByPlace.forEach((place, count) ->
		{
			if (place != null && count != null)
			{
				places.merge(area(place), count, Integer::sum);
			}
		});
		closeCallsByPlace = places;
		return this;
	}

	/** Opens the journal the first time Guardian sees this account. Returns true if it was new. */
	public boolean begin(long now)
	{
		if (runStartedAt != 0)
		{
			return false;
		}
		runStartedAt = now;
		add(now, Type.RUN_STARTED, "The watch began", null);
		return true;
	}

	public void tick(boolean inDanger)
	{
		playedTicks++;
		if (inDanger)
		{
			dangerTicks++;
		}
	}

	public void damage(String source, int amount, String place, long now)
	{
		if (amount <= 0)
		{
			return;
		}
		String who = source == null || source.isEmpty() ? "Unknown" : source;
		damageBySource.merge(who, amount, Integer::sum);
		if (biggestHit == null || amount > biggestHit.amount)
		{
			Hit hit = new Hit();
			hit.amount = amount;
			hit.source = who;
			hit.place = place;
			hit.at = now;
			biggestHit = hit;
		}
	}

	public void closeCall(int lowest, int max, String place, String source, long now)
	{
		closeCalls++;
		String where = place == null ? "Gielinor" : place;
		closeCallsByPlace.merge(area(where), 1, Integer::sum);
		if (lowestHitpoints < 0 || lowest < lowestHitpoints)
		{
			lowestHitpoints = lowest;
			lowestPlace = where;
		}
		String detail = "Down to " + lowest + "/" + max + " hitpoints " + at(where) + (source != null ? ", against " + source : "");
		add(now, Type.CLOSE_CALL, "Survived a close call", detail);
	}

	/**
	 * Records a death.
	 *
	 * @param safe true for a death in a safe minigame, which costs nothing and does not end the run
	 */
	public void death(String place, String source, boolean oneLife, boolean safe, long now)
	{
		String detail = (place == null ? "Somewhere in Gielinor" : capitalise(at(place))) + (source != null ? ", to " + source : "");
		if (safe)
		{
			safeDeaths++;
			add(now, Type.SAFE_DEATH, "A safe death", detail);
			return;
		}
		deaths++;
		add(now, Type.DEATH, oneLife ? "The run has fallen" : "Died", detail);
	}

	/** Notes that the account is hardcore right now. */
	public void noteHardcore()
	{
		hardcoreSeen = true;
	}

	public boolean isHardcoreSeen()
	{
		return hardcoreSeen;
	}

	public void statusLost(long now)
	{
		statusLost(now, "The account is no longer hardcore");
	}

	public void statusLost(long now, String detail)
	{
		if (statusLost)
		{
			return;
		}
		statusLost = true;
		add(now, Type.STATUS_LOST, "Hardcore status lost", detail);
	}

	public void disconnect(String place, long now)
	{
		disconnects++;
		add(now, Type.DISCONNECT, "Disconnected in danger", place == null ? null : capitalise(at(place)));
	}

	/** "in the Wilderness level 20", "at Vorkath": the Wilderness is a region, everything else a place you stand at. */
	static String at(String place)
	{
		return place.startsWith("Wilderness") ? "in " + place : "at " + place;
	}

	/** The row a close call ranks under: the Wilderness as a whole, any other place by its name. */
	static String area(String place)
	{
		return place.startsWith("Wilderness level") ? "Wilderness" : place;
	}

	private static String capitalise(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/** Notes the first visit to a risky zone. Returns true if an entry was written. */
	public boolean zone(Zone zone, long now)
	{
		return zone(zone, 0, now);
	}

	/**
	 * Notes the first visit to a risky zone, with the Wilderness level it was entered at.
	 * Returns true if an entry was written.
	 */
	public boolean zone(Zone zone, int wildernessLevel, long now)
	{
		if (zone == null || !zone.getRisk().atLeast(Risk.HIGH) && zone.getKind() != Zone.Kind.WILDERNESS)
		{
			return false;
		}
		for (int i = entries.size() - 1; i >= 0; i--)
		{
			Entry e = entries.get(i);
			if (now - e.at > ZONE_REPEAT_MS)
			{
				break;
			}
			if (e.type == Type.ZONE && zone.getName().equals(e.title))
			{
				return false;
			}
		}
		String kind = zone.getKind() == Zone.Kind.WILDERNESS
			? wildernessLevel > 0 ? "Entered at level " + wildernessLevel : "Other players can attack you"
			: zone.kindLabel();
		add(now, Type.ZONE, zone.getName(), kind + "  ·  " + zone.getRisk().getLabel() + " risk");
		return true;
	}

	public void milestone(String title, String detail, long now)
	{
		add(now, Type.MILESTONE, title, detail);
	}

	/** Starts a fresh run, for a new account or a new attempt. */
	public static Journal fresh(long now)
	{
		Journal journal = new Journal();
		journal.begin(now);
		return journal;
	}

	private void add(long at, Type type, String title, String detail)
	{
		entries.add(new Entry(at, type, title, detail));
		while (entries.size() > MAX_ENTRIES)
		{
			// Keep the first entry, deaths and status changes; drop the oldest of the rest.
			int drop = -1;
			for (int i = 1; i < entries.size(); i++)
			{
				Type t = entries.get(i).type;
				if (t != Type.DEATH && t != Type.STATUS_LOST)
				{
					drop = i;
					break;
				}
			}
			entries.remove(drop < 0 ? 0 : drop);
		}
	}

	public long getRunStartedAt()
	{
		return runStartedAt;
	}

	public long getPlayedTicks()
	{
		return playedTicks;
	}

	public long getDangerTicks()
	{
		return dangerTicks;
	}

	public int getCloseCalls()
	{
		return closeCalls;
	}

	public int getDeaths()
	{
		return deaths;
	}

	public int getSafeDeaths()
	{
		return safeDeaths;
	}

	public int getDisconnects()
	{
		return disconnects;
	}

	public boolean isStatusLost()
	{
		return statusLost;
	}

	public int getLowestHitpoints()
	{
		return lowestHitpoints;
	}

	public String getLowestPlace()
	{
		return lowestPlace;
	}

	public Hit getBiggestHit()
	{
		return biggestHit;
	}

	public Map<String, Integer> getDamageBySource()
	{
		return Collections.unmodifiableMap(damageBySource);
	}

	public Map<String, Integer> getCloseCallsByPlace()
	{
		return Collections.unmodifiableMap(closeCallsByPlace);
	}

	/** Oldest first. */
	public List<Entry> getEntries()
	{
		return Collections.unmodifiableList(entries);
	}
}
