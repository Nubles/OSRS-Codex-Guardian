package com.codex.guardian.core;

import java.util.Objects;

/** Something that is attacking you right now. */
public final class Threat
{
	private final String name;
	private final int combatLevel;
	private final boolean player;
	private final int knownMax;

	public Threat(String name, int combatLevel, boolean player)
	{
		this(name, combatLevel, player, 0);
	}

	/** @param knownMax the hardest this kind of monster has hit you before (see {@link HitMemory}), or 0 */
	public Threat(String name, int combatLevel, boolean player, int knownMax)
	{
		this.name = name;
		this.combatLevel = combatLevel;
		this.player = player;
		this.knownMax = Math.max(0, knownMax);
	}

	/** The hardest this kind of monster has hit you before, or 0 when Guardian has not seen it hit you alone. */
	public int getKnownMax()
	{
		return knownMax;
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
		return combatLevel == t.combatLevel && player == t.player && knownMax == t.knownMax && Objects.equals(name, t.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, combatLevel, player, knownMax);
	}
}
