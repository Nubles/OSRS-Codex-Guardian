package com.codex.guardian.core;

/** How dangerous a place is for an account with one life, from a safe minigame to a boss lair. */
public enum Risk
{
	SAFE("Safe"),
	LOW("Low"),
	MEDIUM("Medium"),
	HIGH("High"),
	EXTREME("Extreme");

	private final String label;

	Risk(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	public boolean atLeast(Risk other)
	{
		return ordinal() >= other.ordinal();
	}

	public static Risk max(Risk a, Risk b)
	{
		if (a == null)
		{
			return b;
		}
		if (b == null)
		{
			return a;
		}
		return a.ordinal() >= b.ordinal() ? a : b;
	}
}
