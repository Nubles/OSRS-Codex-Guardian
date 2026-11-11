package com.codex.guardian.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The death rules of Hardcore Worlds, as the wiki lists them (wiki: Hardcore_Worlds, read 2026-11-11): "The following
 * activities are UNSAFE and dying in them will result in an official death" and "The following activities are SAFE
 * and dying within them will not count as an official death". The mode "follows the same death ruleset as Hardcore
 * Group Ironman". Anything the wiki does not list (the Colosseum, the Gauntlet) stays unconfirmed: a wrong "safe"
 * costs a run, a wrong "deadly" costs nothing.
 */
public final class HardcoreRules
{
	/** The safe list, as a player reads it. */
	public static final List<String> SAFE = Collections.unmodifiableList(Arrays.asList(
		"Capsizing your boat (no other death on it)",
		"Castle Wars",
		"Clan Wars",
		"Emir's Arena",
		"Fremennik Trials: Koschei's 4th phase",
		"Last Man Standing",
		"Pete Kayer's Challenges",
		"The Galvek and Glough replays",
		"Soul Wars",
		"TzHaar Fight Pit",
		"Zulrah: the Elite Western Provinces resurrection only"));

	/** The unsafe list, as a player reads it. */
	public static final List<String> UNSAFE = Collections.unmodifiableList(Arrays.asList(
		"Barbarian Assault",
		"Camelot training room",
		"Chambers of Xeric",
		"Dream World in Lunar Diplomacy",
		"Fishing Trawler",
		"The Inferno",
		"Magic Training Arena",
		"Nightmare Zone",
		"Pest Control",
		"Your player-owned house",
		"PvM Arena",
		"TzHaar Fight Cave (its elite diary resurrection is safe)",
		"Random events",
		"Rogues' Den maze"));

	/** Places the wiki does not list yet, which Guardian keeps as unconfirmed. */
	public static final List<String> UNLISTED = Collections.unmodifiableList(Arrays.asList(
		"Fortis Colosseum", "The Gauntlet", "Corrupted Gauntlet", "Trouble Brewing"));

	public static final String SAFE_ADVICE = "On a Hardcore World a death here is safe: it does not end your run.";
	public static final String UNSAFE_ADVICE = "On a Hardcore World a death here ends your run, as it would anywhere else.";
	public static final String DIARY_ADVICE = "On a Hardcore World a death here ends your run. Only the elite diary's "
		+ "resurrection is safe.";
	public static final String BOAT_ADVICE = "Capsizing your boat is a safe death. Any other death on your boat ends your run.";
	public static final String UNCONFIRMED_ADVICE = "Not on the wiki's Hardcore World list yet, so Guardian can't confirm a "
		+ "death here is safe. Treat it as the end of the run.";

	private static final Map<String, Zone.DeathRule> BY_ZONE = new HashMap<>();

	static
	{
		// Names as Guardian's atlas has them (see Zones).
		for (String safe : new String[]{"Castle Wars", "Soul Wars", "Fight Pits", "Emir's Arena", "Last Man Standing",
			"Clan Wars"})
		{
			BY_ZONE.put(safe, Zone.DeathRule.SAFE);
		}
		for (String unsafe : new String[]{"The Inferno", "Pest Control", "Barbarian Assault", "Nightmare Zone",
			"Chambers of Xeric", "Magic Training Arena", "Fishing Trawler", "Rogues' Den", "Player-owned house"})
		{
			BY_ZONE.put(unsafe, Zone.DeathRule.UNSAFE);
		}
		// "TzHaar Fight Cave (the elite diary resurrection is safe)"; "Zulrah - Resurrection reward from Elite Western
		// Provinces Diary only".
		BY_ZONE.put("Fight Caves", Zone.DeathRule.UNSAFE_UNLESS_DIARY);
		BY_ZONE.put("Zulrah", Zone.DeathRule.UNSAFE_UNLESS_DIARY);
		for (String unlisted : UNLISTED)
		{
			BY_ZONE.put(unlisted, Zone.DeathRule.UNCONFIRMED);
		}
	}

	private HardcoreRules()
	{
	}

	/** The rule for a place in Guardian's atlas, by its name, or null when the wiki's list does not name it. */
	public static Zone.DeathRule forZone(String name)
	{
		return name == null ? null : BY_ZONE.get(name);
	}

	/** Every atlas place the rules name, for tests that check each one is on the map. */
	static java.util.Set<String> namedPlaces()
	{
		return Collections.unmodifiableSet(BY_ZONE.keySet());
	}

	/** The advice line that goes with a rule. */
	public static String advice(Zone.DeathRule rule)
	{
		switch (rule)
		{
			case SAFE:
				return SAFE_ADVICE;
			case UNSAFE:
				return UNSAFE_ADVICE;
			case UNSAFE_UNLESS_DIARY:
				return DIARY_ADVICE;
			case BOAT:
				return BOAT_ADVICE;
			default:
				return UNCONFIRMED_ADVICE;
		}
	}

	/** A few words for the rule, for a pill or a briefing line. */
	public static String verdict(Zone.DeathRule rule)
	{
		switch (rule)
		{
			case SAFE:
				return "Safe death here";
			case BOAT:
				return "Only capsizing is safe";
			case UNCONFIRMED:
				return "Safe death unconfirmed";
			default:
				return "A death ends your run";
		}
	}
}
