package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link Situation} into a {@link Peril}. Every factor adds or removes points and leaves
 * a plain reason behind, so the panel can always say exactly why Guardian is worried.
 * It looks at your own state and your surroundings, never at what a particular boss is doing.
 */
public final class PerilAssessor
{
	private static final double SECONDS_PER_TICK = 0.6;
	/** Twelve seconds of hitpoints left at the current pace: about two food-and-eat cycles. */
	static final int LETHAL_PACE_TICKS = 20;

	private final int foodReserve;

	/** @param foodReserve how many hitpoints of food Guardian wants you carrying into danger */
	public PerilAssessor(int foodReserve)
	{
		this.foodReserve = Math.max(0, foodReserve);
	}

	public Peril assess(Situation s)
	{
		if (!s.isLoggedIn())
		{
			return Peril.calm();
		}
		List<Peril.Reason> reasons = new ArrayList<>();
		int score = 0;

		// Hitpoints, and how fast they are going.
		double hp = s.hitpointsFraction();
		String hpText = s.getHitpoints() + "/" + s.getMaxHitpoints();
		if (hp <= 0.2)
		{
			score += add(reasons, "Hitpoints critical at " + hpText, Peril.Tone.BAD, 45);
		}
		else if (hp <= 0.35)
		{
			score += add(reasons, "Hitpoints low at " + hpText, Peril.Tone.BAD, 28);
		}
		else if (hp <= 0.5)
		{
			score += add(reasons, "Hitpoints under half", Peril.Tone.WARN, 12);
		}
		// Under fire: something is targeting you, or you took damage in the last few ticks.
		boolean underFire = !s.getAttackers().isEmpty() || s.getDamagePerTick() > 0;
		int worst = s.getRecentMaxHit();
		boolean oneHitAway = worst > 0 && s.getHitpoints() <= worst;
		if (oneHitAway)
		{
			score += underFire
				? add(reasons, "One more hit of " + worst + " could end the run", Peril.Tone.BAD, 30)
				: add(reasons, "Eat up: a hit of " + worst + " would end the run", Peril.Tone.WARN, 10);
		}
		int secondsLeft = -1;
		double ticksLeft = Double.POSITIVE_INFINITY;
		if (s.getDamagePerTick() > 0)
		{
			ticksLeft = s.getHitpoints() / s.getDamagePerTick();
			secondsLeft = (int) Math.round(ticksLeft * SECONDS_PER_TICK);
			if (ticksLeft < LETHAL_PACE_TICKS)
			{
				score += add(reasons, "At this pace your hitpoints last about " + secondsLeft + "s", Peril.Tone.BAD, 24);
			}
			else if (ticksLeft < 40)
			{
				score += add(reasons, "Taking steady damage", Peril.Tone.WARN, 8);
			}
		}

		// Where you are.
		Zone zone = s.getZone();
		Risk risk = s.risk();
		if (zone != null && zone.getKind() != Zone.Kind.WILDERNESS)
		{
			switch (risk)
			{
				case EXTREME:
					score += add(reasons, zone.getName() + " is one of the deadliest places in the game", Peril.Tone.WARN, 22);
					break;
				case HIGH:
					score += add(reasons, zone.getName() + " is high risk", Peril.Tone.WARN, 15);
					break;
				case MEDIUM:
					score += add(reasons, zone.getName() + " is medium risk", Peril.Tone.NOTE, 8);
					break;
				default:
					break;
			}
		}
		int wild = s.getWildernessLevel();
		if (wild > 0)
		{
			score += add(reasons, "Wilderness level " + wild + ": other players can attack you", Peril.Tone.WARN, 10 + Math.min(20, wild / 2));
			if (wild > 30)
			{
				score += add(reasons, "Above level 30 no teleport works", Peril.Tone.BAD, 14);
			}
			else if (wild > 20 && !s.getReadiness().canEscapeAt(wild))
			{
				score += add(reasons, "Your teleports stop working above level 20", Peril.Tone.BAD, 12);
			}
		}
		else if (s.isNearWilderness())
		{
			score += add(reasons, "The Wilderness starts just north of you", Peril.Tone.NOTE, 4);
		}

		// Who is attacking you.
		List<Threat> attackers = s.getAttackers();
		if (attackers.size() >= 2)
		{
			score += add(reasons, attackers.size() + " attackers on you at once", Peril.Tone.BAD, Math.min(24, 8 * (attackers.size() - 1)));
		}
		Threat strongest = null;
		boolean player = false;
		for (Threat t : attackers)
		{
			player |= t.isPlayer();
			if (strongest == null || t.getCombatLevel() > strongest.getCombatLevel())
			{
				strongest = t;
			}
		}
		if (player)
		{
			score += add(reasons, "A player is attacking you", Peril.Tone.BAD, 18);
		}
		if (strongest != null && !strongest.isPlayer() && strongest.getCombatLevel() >= s.getCombatLevel() + 20)
		{
			score += add(reasons, strongest.getName() + " (level " + strongest.getCombatLevel() + ") outclasses you", Peril.Tone.WARN, 8);
		}
		if (s.isMultiCombat() && !attackers.isEmpty())
		{
			score += add(reasons, "Multi-combat: more can join in", Peril.Tone.NOTE, 4);
		}

		// Poison and venom.
		Readiness r = s.getReadiness();
		if (s.getToxin() == Situation.Toxin.VENOM)
		{
			boolean cure = r.getAntivenomDoses() > 0;
			score += add(reasons, cure ? "Venomed" : "Venomed with no anti-venom on you", cure ? Peril.Tone.WARN : Peril.Tone.BAD, cure ? 5 : 10);
		}
		else if (s.getToxin() == Situation.Toxin.POISON)
		{
			score += add(reasons, "Poisoned", Peril.Tone.NOTE, r.getAntipoisonDoses() + r.getAntivenomDoses() > 0 ? 2 : 4);
		}

		// What you brought, which only matters when something can hurt you.
		boolean exposed = s.inHarmsWay() || wild > 0;
		if (exposed && risk != Risk.SAFE)
		{
			if (r.getFoodCount() == 0)
			{
				score += add(reasons, "No food in your pack", Peril.Tone.BAD, 16);
			}
			else if (r.getFoodHealing() < foodReserve)
			{
				score += add(reasons, "Only " + r.getFoodHealing() + " hitpoints of food left", Peril.Tone.WARN, 10);
			}
			// Above level 20 the Wilderness reason already says why the teleport fails.
			if (!r.canEscapeAt(wild) && wild <= 20)
			{
				score += add(reasons, r.hasEscape() ? "Your teleport cannot reach out of here" : "No teleport to escape with",
					Peril.Tone.WARN, 12);
			}
			if (risk.atLeast(Risk.HIGH) && s.getMaxPrayer() > 1 && s.getPrayer() == 0)
			{
				score += add(reasons, "Out of prayer points", Peril.Tone.WARN, 5);
			}
		}
		// A ring of life is a comfort, not a plan: one hit from above its threshold skips it entirely,
		// so it is mentioned but never allowed to talk the read down.
		if (wild <= 30 && !r.getSavingGraces().isEmpty())
		{
			add(reasons, r.getSavingGraces().get(0) + " worn as a last resort", Peril.Tone.GOOD, 0);
		}

		// A safe minigame still deserves attention, but its deaths are not the end.
		if (risk == Risk.SAFE)
		{
			score = (int) Math.round(score * 0.4);
			add(reasons, (zone != null ? zone.getName() : "This place") + ": deaths here are normally safe", Peril.Tone.GOOD, -1);
		}

		score = Math.max(0, Math.min(100, score));
		Peril.Level level = score >= 65 ? Peril.Level.PERIL : score >= 40 ? Peril.Level.DANGER : score >= 18 ? Peril.Level.WARY : Peril.Level.CALM;
		// Any of these is a death that can happen in the next few ticks, so it is always the top
		// level whatever else is true: critically low under fire, one hit from dead under fire, or
		// a pace of damage that empties your hitpoints in seconds.
		boolean lethal = underFire && (hp <= 0.2 || oneHitAway) || ticksLeft < LETHAL_PACE_TICKS;
		if (risk != Risk.SAFE && lethal)
		{
			level = Peril.Level.PERIL;
			score = Math.max(score, 65);
		}
		return new Peril(level, score, reasons, secondsLeft);
	}

	private static int add(List<Peril.Reason> reasons, String text, Peril.Tone tone, int weight)
	{
		reasons.add(new Peril.Reason(text, tone, weight));
		return weight;
	}
}
