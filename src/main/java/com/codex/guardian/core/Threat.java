package com.codex.guardian.core;

import java.util.Objects;

/** Something that is attacking you right now. */
public final class Threat
{
	private final String name;
	private final int combatLevel;
	private final boolean player;

	public Threat(String name, int combatLevel, boolean player)
	{
		this.name = name;
		this.combatLevel = combatLevel;
		this.player = player;
	}

	public String getName()
	{
		return name;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public boolean isPlayer()
	{
		return player;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Threat))
		{
			return false;
		}
		Threat t = (Threat) o;
		return combatLevel == t.combatLevel && player == t.player && Objects.equals(name, t.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, combatLevel, player);
	}
}
