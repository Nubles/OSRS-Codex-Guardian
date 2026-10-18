package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PerilAssessorTest
{
	private final PerilAssessor assessor = new PerilAssessor(80);

	private static Readiness wellStocked()
	{
		return Supplies.assess(Arrays.asList(new Supplies.Item("Shark", 10), new Supplies.Item("Varrock teleport", 2)),
			Collections.emptyList(), 99);
	}

	private static Situation.Builder base()
	{
		return new Situation.Builder().loggedIn(true).playerName("Guardian").combatLevel(100).hitpoints(99, 99)
			.prayer(70, 70).readiness(wellStocked());
	}

	@Test
	public void loggedOutIsCalm()
	{
		assertEquals(Peril.Level.CALM, assessor.assess(Situation.loggedOut()).getLevel());
	}

	@Test
	public void fullHealthInTownIsCalm()
	{
		Peril p = assessor.assess(base().build());
		assertEquals(Peril.Level.CALM, p.getLevel());
		assertEquals("All quiet. Guardian is keeping watch.", p.headline());
	}

	@Test
	public void criticalHitpointsWhileBeingHitIsPeril()
	{
		Peril p = assessor.assess(base().hitpoints(15, 99).damage(3, 12).attacker(new Threat("Hill giant", 28, false)).build());
		assertEquals(Peril.Level.PERIL, p.getLevel());
		assertTrue(p.headline().startsWith("Hitpoints critical"));
	}

	@Test
	public void deepWildernessWithoutEscapeIsDanger()
	{
		Readiness food = Supplies.assess(Collections.singletonList(new Supplies.Item("Shark", 10)), Collections.emptyList(), 99);
		Peril p = assessor.assess(base().zone(Zones.wilderness(35)).wilderness(35, false).readiness(food).build());
		assertTrue(p.getLevel().atLeast(Peril.Level.DANGER));
	}

	@Test
	public void bossLairWithNoFoodWorries()
	{
		Peril p = assessor.assess(base().zone(Zones.forRegion(9023)).readiness(Readiness.empty()).build());
		assertTrue(p.getLevel().atLeast(Peril.Level.WARY));
		assertTrue(p.getReasons().stream().anyMatch(r -> r.getText().equals("No food in your pack")));
	}

	@Test
	public void safeMinigameSoftensTheRead()
	{
		Situation fightCaves = base().zone(Zones.forRegion(9551)).hitpoints(30, 99).damage(2, 14)
			.attacker(new Threat("TzTok-Jad", 702, false)).build();
		Situation open = base().hitpoints(30, 99).damage(2, 14).attacker(new Threat("TzTok-Jad", 702, false)).build();
		assertTrue(assessor.assess(fightCaves).getScore() < assessor.assess(open).getScore());
	}

	@Test
	public void ringOfLifeNeverTalksTheReadDown()
	{
		Readiness withRing = Supplies.assess(Collections.singletonList(new Supplies.Item("Shark", 10)),
			Collections.singletonList(new Supplies.Item("Ring of life", 1)), 99);
		Readiness without = Supplies.assess(Collections.singletonList(new Supplies.Item("Shark", 10)), Collections.emptyList(), 99);
		Situation.Builder hurt = base().hitpoints(40, 99).damage(2, 10).attacker(new Threat("Ankou", 75, false));
		Peril with = assessor.assess(hurt.readiness(withRing).build());
		assertEquals(assessor.assess(hurt.readiness(without).build()).getScore(), with.getScore());
		assertTrue(with.getReasons().stream().anyMatch(r -> r.getTone() == Peril.Tone.GOOD));
	}

	@Test
	public void secondsFromDeathIsPerilEvenAboveTwentyPercent()
	{
		// Two black demons on a player at 29/86: about seven seconds of hitpoints left.
		Peril p = assessor.assess(base().combatLevel(90).hitpoints(29, 86).damage(2.5, 18)
			.attacker(new Threat("Black demon", 172, false)).attacker(new Threat("Black demon", 172, false)).build());
		assertEquals(Peril.Level.PERIL, p.getLevel());
		assertTrue(p.getSecondsLeft() < 10);
	}

	@Test
	public void oneHitFromDeathUnderFireIsPeril()
	{
		Peril p = assessor.assess(base().hitpoints(40, 99).damage(0, 45).attacker(new Threat("Vorkath", 732, false)).build());
		assertEquals(Peril.Level.PERIL, p.getLevel());
		assertTrue(p.getReasons().stream().anyMatch(r -> r.getText().equals("One more hit of 45 could end the run")));
	}

	@Test
	public void oneHitFromDeathAfterTheFightIsOnlyAWarning()
	{
		Peril p = assessor.assess(base().hitpoints(40, 99).damage(0, 45).build());
		assertTrue(p.getLevel().ordinal() < Peril.Level.PERIL.ordinal());
		assertTrue(p.getReasons().stream().anyMatch(r -> r.getTone() == Peril.Tone.WARN && r.getText().startsWith("Eat up")));
	}

	@Test
	public void safeMinigameIsNeverPeril()
	{
		Peril p = assessor.assess(base().zone(Zones.forRegion(9033)).hitpoints(1, 99).damage(0.2, 1)
			.attacker(new Threat("Dagannoth mother", 100, false)).build());
		assertTrue(p.getLevel().ordinal() < Peril.Level.PERIL.ordinal());
	}

	@Test
	public void secondsLeftFollowsThePace()
	{
		Peril p = assessor.assess(base().hitpoints(30, 99).damage(5, 10).build());
		assertEquals(4, p.getSecondsLeft());
		assertEquals(Peril.Level.PERIL, p.getLevel());
	}
}
