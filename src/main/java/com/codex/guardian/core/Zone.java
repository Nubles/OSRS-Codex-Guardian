package com.codex.guardian.core;

import java.util.Objects;

/** A named place with a risk tier and one line of advice for whoever is about to walk in. */
public final class Zone
{
	public enum Kind
	{
		WILDERNESS,
		BOSS,
		RAID,
		DUNGEON,
		SAFE_MINIGAME,
		PVP_WORLD
	}

	private final String name;
	private final Kind kind;
	private final Risk risk;
	private final String advice;

	public Zone(String name, Kind kind, Risk risk, String advice)
	{
		this.name = name;
		this.kind = kind;
		this.risk = risk;
		this.advice = advice;
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
				return "Safe minigame";
			case PVP_WORLD:
				return "PvP world";
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
		return name.equals(zone.name) && kind == zone.kind && risk == zone.risk && Objects.equals(advice, zone.advice);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, kind, risk, advice);
	}
}
