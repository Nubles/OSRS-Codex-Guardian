package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** What you are carrying that would get you out alive: food, a way out, and last-moment saves. */
public final class Readiness
{
	private final int foodCount;
	private final int foodHealing;
	private final String bestEscape;
	private final int escapeMaxWilderness;
	private final int escapeCount;
	private final int lawRunes;
	private final List<String> savingGraces;
	private final int prayerDoses;
	private final int antipoisonDoses;
	private final int antivenomDoses;
	/** Doses of anything that gives run energy back: stamina, super energy and energy potions and mixes. */
	private final int energyDoses;

	private Readiness(Builder b)
	{
		foodCount = b.foodCount;
		foodHealing = b.foodHealing;
		bestEscape = b.bestEscape;
		escapeMaxWilderness = b.escapeMaxWilderness;
		escapeCount = b.escapeCount;
		lawRunes = b.lawRunes;
		savingGraces = Collections.unmodifiableList(new ArrayList<>(b.savingGraces));
		prayerDoses = b.prayerDoses;
		antipoisonDoses = b.antipoisonDoses;
		antivenomDoses = b.antivenomDoses;
		energyDoses = b.energyDoses;
	}

	public static Readiness empty()
	{
		return new Builder().build();
	}

	public int getFoodCount()
	{
		return foodCount;
	}

	public int getFoodHealing()
	{
		return foodHealing;
	}

	/** The carried teleport that works deepest into the Wilderness, or null. */
	public String getBestEscape()
	{
		return bestEscape;
	}

	/** The deepest Wilderness level your best teleport still works at, or -1 without one. */
	public int getEscapeReach()
	{
		return bestEscape == null ? -1 : escapeMaxWilderness;
	}

	public int getEscapeCount()
	{
		return escapeCount;
	}

	public boolean hasEscape()
	{
		return bestEscape != null;
	}

	/** Whether a carried teleport still works at this Wilderness level (0 outside it). */
	public boolean canEscapeAt(int wildernessLevel)
	{
		return bestEscape != null && wildernessLevel <= escapeMaxWilderness;
	}

	public int getLawRunes()
	{
		return lawRunes;
	}

	public List<String> getSavingGraces()
	{
		return savingGraces;
	}

	public int getPrayerDoses()
	{
		return prayerDoses;
	}

	public int getAntipoisonDoses()
	{
		return antipoisonDoses;
	}

	public int getAntivenomDoses()
	{
		return antivenomDoses;
	}

	public int getEnergyDoses()
	{
		return energyDoses;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Readiness))
		{
			return false;
		}
		Readiness r = (Readiness) o;
		return foodCount == r.foodCount && foodHealing == r.foodHealing && Objects.equals(bestEscape, r.bestEscape)
			&& escapeMaxWilderness == r.escapeMaxWilderness && escapeCount == r.escapeCount && lawRunes == r.lawRunes
			&& savingGraces.equals(r.savingGraces) && prayerDoses == r.prayerDoses
			&& antipoisonDoses == r.antipoisonDoses && antivenomDoses == r.antivenomDoses && energyDoses == r.energyDoses;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(foodCount, foodHealing, bestEscape, escapeMaxWilderness, escapeCount, lawRunes, savingGraces,
			prayerDoses, antipoisonDoses, antivenomDoses, energyDoses);
	}

	public static final class Builder
	{
		private int foodCount;
		private int foodHealing;
		private String bestEscape;
		private int escapeMaxWilderness = -1;
		private int escapeCount;
		private int lawRunes;
		private final List<String> savingGraces = new ArrayList<>();
		private int prayerDoses;
		private int antipoisonDoses;
		private int antivenomDoses;
		private int energyDoses;

		public Builder energyDoses(int doses)
		{
			energyDoses += doses;
			return this;
		}

		public Builder food(int count, int healing)
		{
			foodCount += count;
			foodHealing += healing;
			return this;
		}

		public Builder escape(String name, int maxWilderness)
		{
			escapeCount++;
			if (maxWilderness > escapeMaxWilderness)
			{
				bestEscape = name;
				escapeMaxWilderness = maxWilderness;
			}
			return this;
		}

		public Builder spellRunes(int laws)
		{
			lawRunes += laws;
			return this;
		}

		public Builder savingGrace(String name)
		{
			if (!savingGraces.contains(name))
			{
				savingGraces.add(name);
			}
			return this;
		}

		public Builder prayerDoses(int doses)
		{
			prayerDoses += doses;
			return this;
		}

		public Builder antipoisonDoses(int doses)
		{
			antipoisonDoses += doses;
			return this;
		}

		public Builder antivenomDoses(int doses)
		{
			antivenomDoses += doses;
			return this;
		}

		public Readiness build()
		{
			return new Readiness(this);
		}
	}
}
