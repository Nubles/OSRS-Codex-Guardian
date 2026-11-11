package com.codex.guardian.core;

import java.util.Objects;

/**
 * A named place with a risk tier and one line of advice for whoever is about to walk in. On a Hardcore World a place
 * may also carry that mode's own death rule ({@link DeathRule}); everywhere else the rule is null and the usual rules
 * of the account's mode apply.
 */
public final class Zone
{
	public enum Kind
	{
		WILDERNESS,
		BOSS,
		RAID,
		DUNGEON,
		SAFE_MINIGAME,
		PVP_WORLD,
		/** A place named only for a Hardcore World's death rule (a house, the Trawler, at sea). */
		ACTIVITY
	}

	/**
	 * What a death here means on a Hardcore World, from the wiki's list (see {@link HardcoreRules}).
	 */
	public enum DeathRule
	{
		/** On the wiki's safe list: the run goes on. */
		SAFE,
		/** On the wiki's unsafe list: the run ends, as anywhere else. */
		UNSAFE,
		/** Unsafe, except for an elite diary's resurrection, which the wiki lists as safe. */
		UNSAFE_UNLESS_DIARY,
		/** On your boat: capsizing is safe, any other death is not. */
		BOAT,
		/** Not on the wiki's list either way yet. */
		UNCONFIRMED;

		/** True when Guardian cannot tell from the place alone whether a death here ended the run. */
		public boolean undecided()
		{
			return this != SAFE && this != UNSAFE;
		}
	}

	private final String name;
	private final Kind kind;
	private final Risk risk;
	private final String advice;
	private final DeathRule deathRule;

	public Zone(String name, Kind kind, Risk risk, String advice)
	{
		this(name, kind, risk, advice, null);
	}

	public Zone(String name, Kind kind, Risk risk, String advice, DeathRule deathRule)
	{
		this.name = name;
		this.kind = kind;
		this.risk = risk;
		this.advice = advice;
		this.deathRule = deathRule;
	}

	/** The Hardcore World death rule for this place, or null off a Hardcore World or where none is listed. */
	public DeathRule getDeathRule()
	{
		return deathRule;
	}

	public String getName()
	{
		return name;
	}

	public Kind getKind()
	{
		return kind;
	}

	public Risk getRisk()
	{
		return risk;
	}

	public String getAdvice()
	{
		return advice;
	}

	public String kindLabel()
	{
		switch (kind)
		{
			case WILDERNESS:
				return "Wilderness";
			case BOSS:
				return "Boss lair";
			case RAID:
				return "Raid";
			case SAFE_MINIGAME:
				// The label promises a safe death only where the death really is safe.
				return risk == Risk.SAFE ? "Safe minigame" : "Minigame";
			case PVP_WORLD:
				return "PvP world";
			case ACTIVITY:
				return "Activity";
			default:
				return "Dungeon";
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof Zone))
		{
			return false;
		}
		Zone zone = (Zone) o;
		return name.equals(zone.name) && kind == zone.kind && risk == zone.risk && Objects.equals(advice, zone.advice)
			&& deathRule == zone.deathRule;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, kind, risk, advice, deathRule);
	}
}
