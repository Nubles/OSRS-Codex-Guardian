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

	@Test
	public void outOfRunEnergyInTheWildernessSaysSipFirst()
	{
		// Level 35, no teleport works: running south is the plan, and with 3% energy only a sip makes it a run.
		Situation.Builder b = fight(20, 25).wilderness(35, false).distances(-1, 120).runEnergy(3);
		assertEquals("Sip energy, run south now", advise(b.readiness(kit("Amulet of glory(4)", "Stamina potion(2)")).build()));
		assertEquals("Run 120 tiles south", advise(fight(20, 25).wilderness(35, false).distances(-1, 120).runEnergy(3)
			.readiness(kit("Amulet of glory(4)")).build()));
		// With energy to spare the run is a run.
		assertEquals("Run 120 tiles south", advise(fight(20, 25).wilderness(35, false).distances(-1, 120).runEnergy(80)
			.readiness(kit("Amulet of glory(4)", "Stamina potion(2)")).build()));
	}

	@Test
	public void runEnergyOnlyMattersWhenRunningIsThePlan()
	{
		// A teleport that works here is the way out, so energy is no reason for alarm.
		Situation withTeleport = fight(60, 10).wilderness(12, false).runEnergy(0).readiness(kit("Shark", "Amulet of glory(4)")).build();
		org.junit.Assert.assertFalse(withTeleport.cannotRun());
		// Out of the Wilderness nobody chases you to the ditch.
		org.junit.Assert.assertFalse(fight(60, 10).runEnergy(0).readiness(kit("Shark")).build().cannotRun());
		Situation stuck = fight(60, 10).wilderness(35, false).runEnergy(0).readiness(kit("Shark")).build();
		org.junit.Assert.assertTrue(stuck.cannotRun());
		boolean named = false;
		for (Peril.Reason reason : assessor.assess(stuck).getReasons())
		{
			named |= reason.getText().startsWith("Out of run energy");
		}
		org.junit.Assert.assertTrue(named);
		// Unread energy (-1) never counts as none.
		org.junit.Assert.assertFalse(fight(60, 10).wilderness(35, false).readiness(kit("Shark")).build().cannotRun());
	}

	@Test
	public void energyDosesAreCountedButNotAsPrayerOrCures()
	{
		Readiness r = kit("Stamina potion(4)", "Super energy(3)", "Energy mix(2)", "Prayer potion(4)");
		assertEquals(9, r.getEnergyDoses());
		assertEquals(4, r.getPrayerDoses());
	}

	@Test
	public void aHardcoreWorldIsKnownByItsWorldTypeName()
	{
		org.junit.Assert.assertTrue(OneLife.hardcoreWorld(java.util.EnumSet.of(FakeWorld.MEMBERS, FakeWorld.HARDCORE_MAIN)));
		org.junit.Assert.assertFalse(OneLife.hardcoreWorld(java.util.EnumSet.of(FakeWorld.MEMBERS)));
		org.junit.Assert.assertFalse(OneLife.hardcoreWorld(null));
		// The real client's enum carries the type this matches.
		org.junit.Assert.assertTrue(OneLife.hardcoreWorld(java.util.EnumSet.allOf(net.runelite.api.WorldType.class)));
	}

	private enum FakeWorld
	{
		MEMBERS,
		HARDCORE_MAIN
	}
}
