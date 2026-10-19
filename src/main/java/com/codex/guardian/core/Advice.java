package com.codex.guardian.core;

/**
 * The one thing to do right now, in plain words, when Guardian reads danger or peril. It only
 * uses what you carry and where you stand, so it is general survival sense and never a call on
 * what a particular monster is about to do.
 */
public final class Advice
{
	private Advice()
	{
	}

	/** What to do now, or null when nothing urgent needs saying. */
	/** With less prayer than this left, restores included, the advice is to leave before it fails. */
	static final int PRAYER_LEAVE_SECONDS = 15;
	/** Under this much run energy (in percent) running away is not a plan: anyone running catches you. */
	public static final int LOW_RUN_ENERGY = 10;

	public static String now(Situation s, Peril p)
	{
		if (!s.isLoggedIn() || !p.getLevel().atLeast(Peril.Level.DANGER) || s.risk() == Risk.SAFE)
		{
			return null;
		}
		Readiness r = s.getReadiness();
		int wild = s.getWildernessLevel();
		boolean food = r.getFoodCount() > 0;
		boolean escape = s.canEscape();
		String way = shortName(r.getBestEscape());
		// Walking back under your teleport's reach only helps when there is a teleport that will work there.
		boolean walkBack = r.hasEscape() && !s.isTeleblocked();
		int walkTo = r.getEscapeReach();
		if (p.getLevel() == Peril.Level.PERIL)
		{
			int remembered = s.rememberedWorst();
			boolean oneHit = s.getRecentMaxHit() > 0 && s.getHitpoints() <= s.getRecentMaxHit()
				|| remembered > 0 && s.getHitpoints() <= remembered;
			if (escape && (oneHit || !food))
			{
				return "Teleport out now: " + way;
			}
			if (escape)
			{
				return "Eat, or teleport: " + way;
			}
			int toReach = s.getTilesToReach();
			int toDitch = s.getTilesToDitch();
			if (food)
			{
				if (wild <= 0)
				{
					return "Eat now and back away";
				}
				// South only lowers the level where the coordinates are on the Wilderness map; it only leads out of
				// the Wilderness on the surface. Anywhere else (a boss lair, an unmapped cave) no direction is given.
				boolean southLowers = toReach >= 0;
				boolean southLeadsOut = toDitch >= 0;
				if (walkBack)
				{
					if (s.cannotRun() && r.getEnergyDoses() > 0)
					{
						// The plaque has room for one short line; the Ward's place card carries the tile count.
						return southLowers ? "Eat, sip energy, run south" : "Eat, sip energy, run";
					}
					return toReach > 0 ? "Eat, walk " + toReach + " tiles south"
						: southLowers ? "Eat, walk south to level " + walkTo : "Eat, walk to level " + walkTo;
				}
				if (s.cannotRun() && r.getEnergyDoses() > 0)
				{
					return southLeadsOut ? "Eat, sip energy, run south" : "Eat, sip, run for the exit";
				}
				return southLeadsOut ? "Eat, and run south" : "Eat, and run for the exit";
			}
			if (wild > 0)
			{
				// Underground there is no ditch to the south: the way out is a ladder or a cave mouth.
				String run = walkBack && toReach > 0 ? "Run " + toReach + " tiles south" : toDitch > 0 ? "Run " + toDitch + " tiles south"
					: "Run for the exit now";
				// Out of energy, the run is a walk: a sip first is what makes it a run again.
				return s.cannotRun() && r.getEnergyDoses() > 0 ? (toDitch >= 0 ? "Sip energy, run south now" : "Sip energy, run for the exit") : run;
			}
			return s.isTeleblocked() ? "Run: teleblocked, no food" : "Run: no food, no teleport";
		}
		// A protection prayer failing mid-fight: once it is gone the blocked hits land, so leave while it holds.
		int prayerLeft = s.getPrayerLeft();
		if (s.getPrayer() > 0 && prayerLeft >= 0 && prayerLeft < PRAYER_LEAVE_SECONDS && !s.getAttackers().isEmpty())
		{
			return escape ? "Prayer almost out: teleport" : "Prayer almost out: back off";
		}
		// Lag in danger: every input lands late, so the way out is worth taking while it still works.
		if (s.getLag() != Lag.State.STEADY && escape)
		{
			return "Lagging: teleport out";
		}
		if (wild > 0 && !escape && walkBack)
		{
			int toReach = s.getTilesToReach();
			return toReach > 0 ? "Walk " + toReach + " tiles south" : toReach == 0 ? "Walk south to level " + walkTo : "Walk to level " + walkTo;
		}
		if (!food && s.inHarmsWay())
		{
			return "Bank for food before going on";
		}
		if (food && s.hitpointsFraction() <= 0.5)
		{
			return "Eat up before the next fight";
		}
		return null;
	}

	/**
	 * The walk that makes a teleport work again, for a plaque line. Teleblocked, no teleport works at any
	 * level, so walking down to its reach would only waste the walk: the line points out of the Wilderness.
	 */
	public static String walkLine(Situation s)
	{
		if (s.isTeleblocked())
		{
			int toDitch = s.getTilesToDitch();
			return toDitch > 0 ? "Run " + toDitch + " tiles south" : "Leave the Wilderness";
		}
		int toReach = s.getTilesToReach();
		int reach = Math.max(0, s.getReadiness().getEscapeReach());
		// Off the Wilderness map (a boss lair, an unmapped cave) south is no promise, so no direction is given.
		return toReach > 0 ? "Walk " + toReach + " tiles south" : toReach == 0 ? "Walk south to level " + reach : "Walk to level " + reach;
	}

	/** A short name for a carried teleport, so it fits a plaque line. */
	public static String shortName(String item)
	{
		if (item == null)
		{
			return "";
		}
		return item.replace("Amulet of eternal glory", "Eternal glory").replace("Amulet of glory", "Glory")
			.replace("Ring of dueling", "Dueling").replace(" teleport", " tab");
	}
}
