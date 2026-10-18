package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class AdviceTest
{
	private final PerilAssessor assessor = new PerilAssessor(80);

	private static Readiness kit(String... names)
	{
		Supplies.Item[] items = new Supplies.Item[names.length];
		for (int i = 0; i < names.length; i++)
		{
			items[i] = new Supplies.Item(names[i], 1);
		}
		return Supplies.assess(Arrays.asList(items), Collections.emptyList(), 99, true);
	}

	private static Situation.Builder fight(int hp, int worst)
	{
		return new Situation.Builder().loggedIn(true).combatLevel(100).hitpoints(hp, 99).prayer(50, 70)
			.damage(1, worst).attacker(new Threat("Black demon", 172, false));
	}

	private String advise(Situation s)
	{
		return Advice.now(s, assessor.assess(s));
	}

	@Test
	public void calmNeedsNoAdvice()
	{
		assertNull(advise(new Situation.Builder().loggedIn(true).hitpoints(99, 99).build()));
	}

	@Test
	public void oneHitFromDeathWithATeleportSaysLeave()
	{
		assertEquals("Teleport out now: Glory(4)", advise(fight(20, 25).readiness(kit("Shark", "Amulet of glory(4)")).build()));
	}

	@Test
	public void lowButNotOneHitSaysEatOrLeave()
	{
		assertEquals("Eat, or teleport: Glory(4)", advise(fight(15, 8).readiness(kit("Shark", "Amulet of glory(4)")).build()));
	}

	@Test
	public void pastTheTeleportLimitSaysWalkSouth()
	{
		Situation s = fight(15, 8).zone(Zones.wilderness(25)).wilderness(25, false).readiness(kit("Shark", "Varrock teleport")).build();
		assertEquals("Eat, walk south to level 20", advise(s));
	}

	@Test
	public void nothingToSaveYouSaysRun()
	{
		assertEquals("Run: no food, no teleport", advise(fight(15, 8).readiness(Readiness.empty()).build()));
	}

	@Test
	public void safeMinigamesNeedNoAdvice()
	{
		assertNull(advise(fight(5, 8).zone(Zones.forRegion(9551)).readiness(Readiness.empty()).build()));
	}

	@Test
	public void teleblockedNeverSaysTeleport()
	{
		// A PKer's Tele Block at level 10: the glory works at this level, but nothing will teleport you.
		Situation s = fight(20, 25).zone(Zones.wilderness(10)).wilderness(10, false).teleblocked(true)
			.readiness(kit("Shark", "Amulet of glory(4)")).build();
		assertEquals("Eat, and run south", advise(s));
		Situation noFood = fight(20, 25).teleblocked(true).readiness(kit("Amulet of glory(4)")).build();
		assertEquals("Run: teleblocked, no food", advise(noFood));
	}

	@Test
	public void noTeleportAtAllDoesNotPromiseLevelTwenty()
	{
		Situation s = fight(15, 8).zone(Zones.wilderness(25)).wilderness(25, false).readiness(kit("Shark")).build();
		assertEquals("Eat, and run south", advise(s));
	}
}
