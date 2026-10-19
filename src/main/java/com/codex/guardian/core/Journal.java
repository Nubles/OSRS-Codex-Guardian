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
		SAFE_DEATH,
		/** Lag or a freeze while something could hurt you. */
		LAG,
		/**
		 * A death in a minigame on a Hardcore World, where nobody can yet confirm the minigame is still safe: it does not
		 * end the run unless the player marks it as fallen.
		 */
		UNCONFIRMED_DEATH
	}

	public static final class Entry
	{
		long at;
		Type type;
		String title;
		String detail;
		/** The seconds around a close call or before a death, tick by tick; absent on older entries. */
		Replay replay;

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

		/** The replay to draw, or null when there is none or a saved one is damaged. */
		public Replay getReplay()
		{
			return replay != null && replay.isValid() ? replay : null;
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
	/** A hundred years of play in game ticks: more than this is damage, not a run. */
	static final long MAX_TICKS = 100L * 365 * 24 * 6000;
	/** Replays are kept on this many of the newest entries that have one, so the save stays small (about 300 bytes each). */
	static final int MAX_REPLAYS = 30;
	/** Re-entering the same zone within this long does not write a new entry. */
	static final long ZONE_REPEAT_MS = 30 * 60 * 1000L;

	long runStartedAt;
	long playedTicks;
	long dangerTicks;
	int closeCalls;
	int deaths;
	/** Deaths in safe minigames, which do not end a run. */
	int safeDeaths;
	/** Minigame deaths on a Hardcore World not yet marked as the end of the run. */
	int unconfirmedDeaths;
	int disconnects;
	/** Bouts of lag survived while something could hurt you. */
	int lagSpikes;
	boolean statusLost;
	/** Set once Guardian has seen this account as hardcore, so a loss of status while away is still noticed. */
	boolean hardcoreSeen;
	int lowestHitpoints = -1;
	String lowestPlace;
	Hit biggestHit;
	Map<String, Integer> damageBySource = new LinkedHashMap<>();
	Map<String, Integer> closeCallsByPlace = new LinkedHashMap<>();
	/** The lowest hitpoints of any close call at each place, keyed like {@link #closeCallsByPlace}. */
	Map<String, Integer> lowestByPlace = new LinkedHashMap<>();
	List<Entry> entries = new ArrayList<>();

	/**
	 * Reads a saved journal, keeping every field and entry that reads on its own: one odd value never costs the run.
	 * Null with nothing saved, or with nothing in it readable; the result says when anything was left out.
	 */
	public static SavedJson.Read<Journal> load(com.google.gson.Gson gson, String json)
	{
		SavedJson.Read<Journal> read = SavedJson.read(gson, json, Journal.class);
		return new SavedJson.Read<>(read.getValue() == null ? null : read.getValue().repair(), read.isDamaged());
	}

	/**
	 * Gson leaves fields it did not find as null; this puts them back, and brings figures no run could reach
	 * (negative counts, centuries of play) back to sense.
	 */
	public Journal repair()
	{
		playedTicks = Math.max(0, Math.min(MAX_TICKS, playedTicks));
		dangerTicks = Math.max(0, Math.min(playedTicks, dangerTicks));
		closeCalls = Math.max(0, closeCalls);
		deaths = Math.max(0, deaths);
		safeDeaths = Math.max(0, safeDeaths);
		unconfirmedDeaths = Math.max(0, unconfirmedDeaths);
		disconnects = Math.max(0, disconnects);
		lagSpikes = Math.max(0, lagSpikes);
		lowestHitpoints = Math.max(-1, lowestHitpoints);
		runStartedAt = Math.max(0, runStartedAt);
		if (biggestHit != null && biggestHit.amount <= 0)
		{
			biggestHit = null;
		}
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
		if (lowestByPlace == null)
		{
			lowestByPlace = new LinkedHashMap<>();
		}
		lowestByPlace.values().removeIf(v -> v == null || v < 0);
		damageBySource.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0);
		entries.removeIf(e -> e == null || e.type == null);
		for (Entry e : entries)
		{
			if (e.replay != null && !e.replay.isValid())
			{
				e.replay = null;
			}
		}
		// Older journals split Wilderness close calls by level; one row for the Wilderness ranks it honestly.
		Map<String, Integer> places = new LinkedHashMap<>();
		closeCallsByPlace.forEach((place, count) ->
		{
			if (place != null && count != null && count > 0)
			{
				places.merge(area(place), count, Integer::sum);
			}
		});
		closeCallsByPlace = places;
		trim();
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
		closeCall(lowest, max, place, source, null, now);
	}

	/** Records a close call survived, with its replay when one was recorded. */
	public void closeCall(int lowest, int max, String place, String source, Replay replay, long now)
	{
		closeCalls++;
		String where = place == null ? "Gielinor" : place;
		closeCallsByPlace.merge(area(where), 1, Integer::sum);
		lowestByPlace.merge(area(where), lowest, Math::min);
		if (lowestHitpoints < 0 || lowest < lowestHitpoints)
		{
			lowestHitpoints = lowest;
			lowestPlace = where;
		}
		String detail = "Down to " + lowest + "/" + max + " hitpoints " + at(where) + (source != null ? ", against " + source : "");
		add(now, Type.CLOSE_CALL, "Survived a close call", detail, replay);
	}

	/**
	 * Records a death.
	 *
	 * @param safe true for a death in a safe minigame, which costs nothing and does not end the run
	 */
	public void death(String place, String source, boolean oneLife, boolean safe, long now)
	{
		death(place, source, oneLife, safe, null, now);
	}

	/** Records a death with the replay of the seconds before it, when one was recorded. */
	public void death(String place, String source, boolean oneLife, boolean safe, Replay replay, long now)
	{
		String detail = (place == null ? "Somewhere in Gielinor" : capitalise(at(place))) + (source != null ? ", to " + source : "");
		if (safe)
		{
			safeDeaths++;
			add(now, Type.SAFE_DEATH, "A safe death", detail, replay);
			return;
		}
		deaths++;
		add(now, Type.DEATH, oneLife ? "The run has fallen" : "Died", detail, replay);
	}

	/** Title of a minigame death on a Hardcore World until the player says whether it ended the run. */
	public static final String UNCONFIRMED_TITLE = "Unconfirmed death";

	/**
	 * Records a minigame death on a Hardcore World. Whether such a death is still safe there can't be confirmed, so it
	 * neither ends the run nor counts as safe: the player can mark it as fallen with {@link #markFallen}.
	 */
	public void unconfirmedDeath(String place, String source, Replay replay, long now)
	{
		String detail = (place == null ? "Somewhere in Gielinor" : capitalise(at(place))) + (source != null ? ", to " + source : "");
		unconfirmedDeaths++;
		add(now, Type.UNCONFIRMED_DEATH, UNCONFIRMED_TITLE, detail, replay);
	}

	/**
	 * The player says the unconfirmed death recorded at this moment ended the run: it becomes a death like any other.
	 *
	 * @return true when such an entry was found and changed
	 */
	public boolean markFallen(long at)
	{
		for (int i = entries.size() - 1; i >= 0; i--)
		{
			Entry e = entries.get(i);
			if (e.type == Type.UNCONFIRMED_DEATH && e.at == at)
			{
				e.type = Type.DEATH;
				e.title = "The run has fallen";
				unconfirmedDeaths = Math.max(0, unconfirmedDeaths - 1);
				deaths++;
				return true;
			}
		}
		return false;
	}

	public int getUnconfirmedDeaths()
	{
		return unconfirmedDeaths;
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

	/** Notes a bout of lag survived where something could hurt you. */
	public void lag(String what, String place, long now)
	{
		lagSpikes++;
		add(now, Type.LAG, "Lag in danger", what + (place == null ? "" : " " + at(place)));
	}

	/**
	 * What the journal remembers about a place, for a briefing at its door: "2 close calls here, 4 hp low".
	 * Null when nothing has happened there.
	 */
	public String memoryOf(String place)
	{
		if (place == null)
		{
			return null;
		}
		Integer count = closeCallsByPlace.get(area(place));
		if (count == null || count <= 0)
		{
			return null;
		}
		Integer lowest = lowestByPlace.get(area(place));
		return (count == 1 ? "A close call here" : count + " close calls here") + (lowest != null ? ", " + lowest + " hp low" : "");
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
		add(at, type, title, detail, null);
	}

	private void add(long at, Type type, String title, String detail, Replay replay)
	{
		Entry entry = new Entry(at, type, title, detail);
		entry.replay = replay != null && replay.isValid() ? replay : null;
		entries.add(entry);
		trim();
	}

	/**
	 * Keeps replays on only the newest {@link #MAX_REPLAYS} entries (deaths keep theirs), and at most
	 * {@link #MAX_ENTRIES} entries: the first entry, deaths and status changes stay, and the oldest of the rest go.
	 * One pass, so a damaged journal of thousands of entries is trimmed at once.
	 */
	private void trim()
	{
		int kept = 0;
		for (int i = entries.size() - 1; i >= 0; i--)
		{
			Entry e = entries.get(i);
			// A death keeps its replay for as long as the entry lasts; it is the one most worth reading.
			if (e.replay != null && e.type != Type.DEATH && e.type != Type.UNCONFIRMED_DEATH && ++kept > MAX_REPLAYS)
			{
				e.replay = null;
			}
		}
		int excess = entries.size() - MAX_ENTRIES;
		if (excess <= 0)
		{
			return;
		}
		List<Entry> out = new ArrayList<>(MAX_ENTRIES + 1);
		for (int i = 0; i < entries.size(); i++)
		{
			Type t = entries.get(i).type;
			boolean keep = i == 0 || t == Type.DEATH || t == Type.STATUS_LOST || t == Type.UNCONFIRMED_DEATH;
			if (!keep && excess > 0)
			{
				excess--;
				continue;
			}
			out.add(entries.get(i));
		}
		// Only when deaths and status changes alone are too many: the oldest go.
		while (out.size() > MAX_ENTRIES)
		{
			out.remove(0);
		}
		entries = out;
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

	public int getLagSpikes()
	{
		return lagSpikes;
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
