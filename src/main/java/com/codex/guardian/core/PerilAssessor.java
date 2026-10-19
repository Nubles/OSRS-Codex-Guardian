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
	/** Prayer that will run out sooner than this, restores included, is worth a word while under fire. */
	static final int PRAYER_SHORT_SECONDS = 30;

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
			score += add(reasons, Peril.Topic.VITALS, "Hitpoints critical at " + hpText, Peril.Tone.BAD, 45);
		}
		else if (hp <= 0.35)
		{
			score += add(reasons, Peril.Topic.VITALS, "Hitpoints low at " + hpText, Peril.Tone.BAD, 28);
		}
		else if (hp <= 0.5)
		{
			score += add(reasons, Peril.Topic.VITALS, "Hitpoints under half", Peril.Tone.WARN, 12);
		}
		// Under fire: something is targeting you, or you took damage in the last few ticks.
		boolean underFire = !s.getAttackers().isEmpty() || s.getDamagePerTick() > 0;
		int worst = s.getRecentMaxHit();
		boolean oneHitAway = worst > 0 && s.getHitpoints() <= worst;
		if (oneHitAway)
		{
			score += underFire
				? add(reasons, Peril.Topic.VITALS, "One more hit of " + worst + " could end the run", Peril.Tone.BAD, 30)
				: add(reasons, Peril.Topic.VITALS, "Eat up: a hit of " + worst + " would end the run", Peril.Tone.WARN, 10);
		}
		// What the monsters on you have hit you for before: known before the first hit of this fight lands. Only
		// attackers carry a remembered hit, so this is always under fire.
		int remembered = s.rememberedWorst();
		boolean rememberedLethal = remembered > 0 && s.getHitpoints() <= remembered && !oneHitAway;
		if (rememberedLethal)
		{
			score += add(reasons, Peril.Topic.VITALS, rememberedText(s, remembered),
				Peril.Tone.BAD, 30);
		}
		int secondsLeft = -1;
		double ticksLeft = Double.POSITIVE_INFINITY;
		if (s.getDamagePerTick() > 0)
		{
			ticksLeft = s.getHitpoints() / s.getDamagePerTick();
			secondsLeft = (int) Math.round(ticksLeft * SECONDS_PER_TICK);
			if (ticksLeft < LETHAL_PACE_TICKS)
			{
				score += add(reasons, Peril.Topic.VITALS, "At this pace your hitpoints last about " + secondsLeft + "s", Peril.Tone.BAD, 24);
			}
			else if (ticksLeft < 40)
			{
				score += add(reasons, Peril.Topic.VITALS, "Taking steady damage", Peril.Tone.WARN, 8);
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
					score += add(reasons, Peril.Topic.PLACE, zone.getName() + " is one of the deadliest places in the game", Peril.Tone.WARN, 22);
					break;
				case HIGH:
					score += add(reasons, Peril.Topic.PLACE, zone.getName() + " is high risk", Peril.Tone.WARN, 15);
					break;
				case MEDIUM:
					score += add(reasons, Peril.Topic.PLACE, zone.getName() + " is medium risk", Peril.Tone.NOTE, 8);
					break;
				default:
					break;
			}
		}
		int wild = s.getWildernessLevel();
		if (wild > 0)
		{
			score += add(reasons, Peril.Topic.PLACE, "Wilderness level " + wild + ": other players can attack you", Peril.Tone.WARN, 10 + Math.min(20, wild / 2));
			if (wild > 30)
			{
				score += add(reasons, Peril.Topic.PLACE, "Above level 30 no teleport works", Peril.Tone.BAD, 14);
			}
			else if (wild > 20 && !s.isTeleblocked() && !s.getReadiness().canEscapeAt(wild))
			{
				score += add(reasons, Peril.Topic.PLACE, "Your teleports stop working above level 20", Peril.Tone.BAD, 12);
			}
		}
		else if (s.isNearWilderness())
		{
			score += add(reasons, Peril.Topic.PLACE, "The Wilderness starts just north of you", Peril.Tone.NOTE, 4);
		}

		// Who is attacking you.
		List<Threat> attackers = s.getAttackers();
		if (attackers.size() >= 2)
		{
			score += add(reasons, Peril.Topic.THREAT, attackers.size() + " attackers on you at once", Peril.Tone.BAD, Math.min(24, 8 * (attackers.size() - 1)));
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
			score += add(reasons, Peril.Topic.THREAT, "A player is attacking you", Peril.Tone.BAD, 18);
		}
		if (strongest != null && !strongest.isPlayer() && strongest.getCombatLevel() >= s.getCombatLevel() + 20)
		{
			score += add(reasons, Peril.Topic.THREAT, strongest.getName() + " (level " + strongest.getCombatLevel() + ") outclasses you", Peril.Tone.WARN, 8);
		}
		if (s.isMultiCombat() && !attackers.isEmpty())
		{
			score += add(reasons, Peril.Topic.THREAT, "Multi-combat: more can join in", Peril.Tone.NOTE, 4);
		}

		// Lag: a late tick makes every eat and teleport late too. Only worth a word where something can hurt you.
		Lag.State lag = s.getLag();
		if (lag != Lag.State.STEADY && (underFire || risk.atLeast(Risk.HIGH) || wild > 0) && risk != Risk.SAFE)
		{
			score += lag == Lag.State.FROZEN
				? add(reasons, Peril.Topic.LINK, "The game froze: " + s.getLagText().toLowerCase(), Peril.Tone.BAD, 18)
				: add(reasons, Peril.Topic.LINK, "Lagging: " + s.getLagText(), Peril.Tone.WARN, 12);
		}

		// Poison and venom.
		Readiness r = s.getReadiness();
		if (s.getToxin() == Situation.Toxin.VENOM)
		{
			boolean cure = r.getAntivenomDoses() > 0;
			score += add(reasons, Peril.Topic.KIT, cure ? "Venomed" : "Venomed with no anti-venom on you", cure ? Peril.Tone.WARN : Peril.Tone.BAD, cure ? 5 : 10);
		}
		else if (s.getToxin() == Situation.Toxin.POISON)
		{
			score += add(reasons, Peril.Topic.KIT, "Poisoned", Peril.Tone.NOTE, r.getAntipoisonDoses() + r.getAntivenomDoses() > 0 ? 2 : 4);
		}

		// What you brought, which only matters when something can hurt you.
		boolean exposed = s.inHarmsWay() || wild > 0;
		if (exposed && risk != Risk.SAFE)
		{
			if (r.getFoodCount() == 0)
			{
				score += add(reasons, Peril.Topic.KIT, "No food in your pack", Peril.Tone.BAD, 16);
			}
			else if (r.getFoodHealing() < foodReserve)
			{
				score += add(reasons, Peril.Topic.KIT, "Only " + r.getFoodHealing() + " hitpoints of food left", Peril.Tone.WARN, 10);
			}
			// Above level 20 the Wilderness reason already says why the teleport fails.
			if (s.isTeleblocked())
			{
				score += add(reasons, Peril.Topic.KIT, "Teleblocked: no teleport works", Peril.Tone.BAD, 14);
			}
			else if (!r.canEscapeAt(wild) && wild <= 20)
			{
				score += add(reasons, Peril.Topic.KIT, r.hasEscape() ? "Your teleport cannot reach out of here" : "No teleport to escape with",
					Peril.Tone.WARN, 12);
			}
			if (risk.atLeast(Risk.HIGH) && s.getMaxPrayer() > 1 && s.getPrayer() == 0)
			{
				score += add(reasons, Peril.Topic.KIT, "Out of prayer points", Peril.Tone.WARN, 5);
			}
			else if (s.getPrayer() > 0 && s.getPrayerLeft() >= 0 && s.getPrayerLeft() < PRAYER_SHORT_SECONDS && underFire)
			{
				// A protection prayer about to fail mid-fight: the hits it was blocking land all at once.
				boolean soon = s.getPrayerLeft() < PRAYER_SHORT_SECONDS / 3;
				score += add(reasons, Peril.Topic.VITALS, "Prayer runs out in about " + PrayerWatch.describe(s.getPrayerLeft()),
					soon ? Peril.Tone.BAD : Peril.Tone.WARN, soon ? 10 : 6);
			}
		}
		// A ring of life is a comfort, not a plan: one hit from above its threshold skips it entirely,
		// so it is mentioned but never allowed to talk the read down.
		if (wild <= 30 && !r.getSavingGraces().isEmpty())
		{
			add(reasons, Peril.Topic.KIT, r.getSavingGraces().get(0) + " worn as a last resort", Peril.Tone.GOOD, 0);
		}

		// A safe minigame still deserves attention, but its deaths are not the end.
		if (risk == Risk.SAFE)
		{
			score = (int) Math.round(score * 0.4);
			add(reasons, Peril.Topic.PLACE, (zone != null ? zone.getName() : "This place") + ": deaths here are normally safe", Peril.Tone.GOOD, -1);
		}

		score = Math.max(0, Math.min(100, score));
		Peril.Level level = score >= 65 ? Peril.Level.PERIL : score >= 40 ? Peril.Level.DANGER : score >= 18 ? Peril.Level.WARY : Peril.Level.CALM;
		// Any of these is a death that can happen in the next few ticks, so it is always the top
		// level whatever else is true: critically low under fire, one hit from dead under fire, or
		// a pace of damage that empties your hitpoints in seconds.
		boolean lethal = underFire && (hp <= 0.2 || oneHitAway || rememberedLethal) || ticksLeft < LETHAL_PACE_TICKS;
		if (risk != Risk.SAFE && lethal)
		{
			level = Peril.Level.PERIL;
			score = Math.max(score, 65);
		}
		return new Peril(level, score, reasons, secondsLeft);
	}

	/** "Black demon has hit you for 16", or "Two on you have hit for 16 + 14" when the figure is a pair. */
	static String rememberedText(Situation s, int remembered)
	{
		Threat first = null;
		for (Threat t : s.getAttackers())
		{
			if (t.getKnownMax() > 0 && (first == null || t.getKnownMax() > first.getKnownMax()))
			{
				first = t;
			}
		}
		if (first == null)
		{
			return "Hit for " + remembered + " before: one hit could end the run";
		}
		int rest = remembered - first.getKnownMax();
		return rest > 0 ? "Together they have hit for " + remembered + ": one tick could end the run"
			: first.getName() + " has hit you for " + first.getKnownMax() + ": one hit could end the run";
	}

	private static int add(List<Peril.Reason> reasons, Peril.Topic topic, String text, Peril.Tone tone, int weight)
	{
		reasons.add(new Peril.Reason(topic, text, tone, weight));
		return weight;
	}
}
