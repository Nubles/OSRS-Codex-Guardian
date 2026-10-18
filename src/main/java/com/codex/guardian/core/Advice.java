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
	public static String now(Situation s, Peril p)
	{
		if (!s.isLoggedIn() || !p.getLevel().atLeast(Peril.Level.DANGER) || s.risk() == Risk.SAFE)
		{
			return null;
		}
		Readiness r = s.getReadiness();
		int wild = s.getWildernessLevel();
		boolean food = r.getFoodCount() > 0;
		boolean escape = r.canEscapeAt(wild);
		String way = shortName(r.getBestEscape());
		int walkTo = r.hasEscape() ? r.getEscapeReach() : 20;
		if (p.getLevel() == Peril.Level.PERIL)
		{
			boolean oneHit = s.getRecentMaxHit() > 0 && s.getHitpoints() <= s.getRecentMaxHit();
			if (escape && (oneHit || !food))
			{
				return "Teleport out now: " + way;
			}
			if (escape)
			{
				return "Eat, or teleport: " + way;
			}
			if (food)
			{
				return wild > 0 ? "Eat, walk south to level " + walkTo : "Eat now and back away";
			}
			return wild > 0 ? "Run south now" : "Run: no food, no teleport";
		}
		if (wild > 0 && !escape && r.hasEscape())
		{
			return "Walk south to level " + walkTo;
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
