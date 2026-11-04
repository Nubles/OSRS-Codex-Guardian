package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The PKer alarm only fires for players who can attack you where you both stand, and says the plain next move. */
public class PkWatchTest
{
	private static final PkWatch.Footing GLORY_AT_25 = new PkWatch.Footing(25, false, "Glory", 30, 40, 0, 200);

	private static PkWatch.Sighting pker(String name, int combat, int wild, int distance)
	{
		return new PkWatch.Sighting(name, combat, wild, distance, false, -1, false, false);
	}

	@Test
	public void theWikisExampleHolds()
	{
		// "a player who is level 100 can attack players who are level 99, level 101, or the same level" at level 1.
		assertTrue(PkWatch.canAttack(100, 1, 99, 1));
		assertTrue(PkWatch.canAttack(100, 1, 101, 1));
		assertTrue(PkWatch.canAttack(100, 1, 100, 1));
		assertFalse(PkWatch.canAttack(100, 1, 102, 1));
		// "a level 5 player couldn't attack a level 10 player in level 1 Wilderness"
		assertFalse(PkWatch.canAttack(5, 1, 10, 1));
	}

	@Test
	public void theShallowerLevelSetsTheBracketBothWays()
	{
		// You stand at 10, they stand at 3: a five-level gap is too wide for them until they walk deeper.
		assertFalse(PkWatch.canAttack(90, 10, 95, 3));
		assertTrue(PkWatch.canAttack(90, 10, 95, 5));
		// And the other way round.
		assertFalse(PkWatch.canAttack(90, 3, 95, 10));
		// Outside the Wilderness nobody can.
		assertFalse(PkWatch.canAttack(90, 10, 90, 0));
		assertFalse(PkWatch.canAttack(90, 0, 90, 10));
	}

	@Test
	public void theBracketStaysWithinRealCombatLevels()
	{
		assertEquals("77-117", PkWatch.bracket(97, 20));
		assertEquals("3-26", PkWatch.bracket(10, 16));
		assertEquals("96-126", PkWatch.bracket(120, 24));
		assertEquals("", PkWatch.bracket(97, 0));
	}

	@Test
	public void offTheWildernessItSaysNothing()
	{
		PkWatch w = new PkWatch();
		PkWatch.Alarm a = w.update(90, 0, false, Collections.singletonList(pker("Zezima", 90, 0, 2)), null);
		assertFalse(a.isActive());
	}

	@Test
	public void playersOutOfRangeAndFriendsDoNotSetItOff()
	{
		PkWatch w = new PkWatch();
		List<PkWatch.Sighting> seen = Arrays.asList(
			pker("Main", 126, 10, 3), // 36 levels away at level 10
			pker("Lowbie", 40, 10, 3),
			new PkWatch.Sighting("Clanmate", 90, 10, 1, true, ItemID.AGS, true, false));
		assertFalse(w.update(90, 10, false, seen, GLORY_AT_25).isActive());
	}

	@Test
	public void someoneWhoCouldAttackIsWatchedQuietly()
	{
		PkWatch w = new PkWatch();
		PkWatch.Alarm a = w.update(90, 10, false, Collections.singletonList(pker("Pker", 95, 10, 9)), GLORY_AT_25);
		assertEquals(PkWatch.Strength.WATCH, a.getStrength());
		assertEquals("PKer in range", a.headline());
		assertEquals("Lvl 95, 9 tiles", a.detail());
		assertEquals("Your Glory works here", a.getMove());
	}

	@Test
	public void aSkullOrAKnockOutWeaponRaisesIt()
	{
		PkWatch w = new PkWatch();
		PkWatch.Sighting skulled = new PkWatch.Sighting("Pker", 95, 10, 9, true, -1, false, false);
		assertEquals(PkWatch.Strength.WARN, w.update(90, 10, false, Collections.singletonList(skulled), GLORY_AT_25).getStrength());

		PkWatch armed = new PkWatch();
		PkWatch.Sighting claws = new PkWatch.Sighting("Pker", 95, 10, 9, false, ItemID.DRAGON_CLAWS, false, false);
		PkWatch.Alarm a = armed.update(90, 10, false, Collections.singletonList(claws), GLORY_AT_25);
		assertEquals(PkWatch.Strength.WARN, a.getStrength());
		assertTrue(a.getLead().isArmed());
		// A whip is no knock-out special.
		PkWatch plain = new PkWatch();
		PkWatch.Sighting whip = new PkWatch.Sighting("Pker", 95, 10, 9, false, ItemID.ABYSSAL_WHIP, false, false);
		assertEquals(PkWatch.Strength.WATCH, plain.update(90, 10, false, Collections.singletonList(whip), GLORY_AT_25).getStrength());
	}

	@Test
	public void someoneOnYouIsDanger()
	{
		PkWatch w = new PkWatch();
		PkWatch.Sighting on = new PkWatch.Sighting("Pker", 95, 10, 2, false, -1, false, true);
		PkWatch.Alarm a = w.update(90, 10, false, Collections.singletonList(on), GLORY_AT_25);
		assertEquals(PkWatch.Strength.DANGER, a.getStrength());
		assertEquals("PKer on you", a.headline());
	}

	@Test
	public void twoInMultiIsDanger()
	{
		PkWatch w = new PkWatch();
		List<PkWatch.Sighting> two = Arrays.asList(pker("A", 95, 10, 9), pker("B", 88, 10, 12));
		PkWatch.Alarm a = w.update(90, 10, true, two, GLORY_AT_25);
		assertEquals(PkWatch.Strength.DANGER, a.getStrength());
		assertEquals("2 PKers in range", a.headline());
		assertTrue(a.detail().endsWith("multi"));
		// One in multi is a warning: anyone else nearby can pile on.
		assertEquals(PkWatch.Strength.WARN, new PkWatch().update(90, 10, true, two.subList(0, 1), GLORY_AT_25).getStrength());
	}

	@Test
	public void theMostWorryingPlayerLeads()
	{
		PkWatch w = new PkWatch();
		List<PkWatch.Sighting> seen = Arrays.asList(pker("Near", 95, 10, 3),
			new PkWatch.Sighting("Skulled", 95, 10, 12, true, ItemID.AGS, false, false));
		assertEquals("Skulled", w.update(90, 10, false, seen, GLORY_AT_25).getLead().getName());
	}

	@Test
	public void closingInIsRememberedForAFewTicks()
	{
		PkWatch w = new PkWatch();
		assertFalse(w.update(90, 10, false, Collections.singletonList(pker("P", 95, 10, 12)), GLORY_AT_25).getLead().isClosing());
		assertTrue(w.update(90, 10, false, Collections.singletonList(pker("P", 95, 10, 10)), GLORY_AT_25).getLead().isClosing());
		// Standing still for a tick does not drop it...
		assertTrue(w.update(90, 10, false, Collections.singletonList(pker("P", 95, 10, 10)), GLORY_AT_25).getLead().isClosing());
		// ...but stepping away does.
		assertFalse(w.update(90, 10, false, Collections.singletonList(pker("P", 95, 10, 11)), GLORY_AT_25).getLead().isClosing());
	}

	@Test
	public void theNextMoveIsPlain()
	{
		assertEquals("Teleport now: Glory works here", PkWatch.move(GLORY_AT_25));
		assertEquals("Run 40 tiles south to level 30, then Glory",
			PkWatch.move(new PkWatch.Footing(35, false, "Glory", 30, 120, 40, 280)));
		assertEquals("Run 14 tiles south to level 20, then Varrock teleport",
			PkWatch.move(new PkWatch.Footing(22, false, "Varrock teleport", 20, 14, 0, 174)));
		assertEquals("Teleblocked: run 174 tiles south to the ditch",
			PkWatch.move(new PkWatch.Footing(22, true, "Glory", 30, 14, 0, 174)));
		assertEquals("No teleport: run 60 tiles south to the ditch",
			PkWatch.move(new PkWatch.Footing(8, false, null, -1, 0, 0, 60)));
		// Underground there is no ditch to walk to.
		assertEquals("No teleport: head for the way out", PkWatch.move(new PkWatch.Footing(8, false, null, -1, 0, 0, -1)));
		assertEquals("Get below level 21, then Varrock teleport",
			PkWatch.move(new PkWatch.Footing(22, false, "Varrock teleport", 20, -1, -1, -1)));
	}

	@Test
	public void onlyTheGamesOwnSafeWordsSilenceIt()
	{
		assertTrue(PkWatch.safeMarker("<col=00ff00>Protection</col>"));
		assertTrue(PkWatch.safeMarker("Guarded"));
		assertTrue(PkWatch.safeMarker("No PvP"));
		assertFalse(PkWatch.safeMarker("Level: 23"));
		assertFalse(PkWatch.safeMarker("Something new"));
		assertFalse(PkWatch.safeMarker(null));
	}

	@Test
	public void theLineDistancesMatchTheMap()
	{
		// Level 21 starts at y 3680 (3520 + 8 * 20), so from y 3700 it is 21 tiles south to the last row of level 20.
		assertEquals(23, Zones.wildernessLevel(3100, 3700));
		assertEquals(21, Zones.tilesSouthTo(3100, 3700, 20));
		assertEquals(20, Zones.wildernessLevel(3100, 3679));
		assertEquals(0, Zones.tilesSouthTo(3100, 3679, 20));
	}

	@Test
	public void notificationsComeOnTheFirstSightingAndWhenItGetsWorse()
	{
		assertTrue(PkWatch.shouldNotify(PkWatch.Strength.NONE, PkWatch.Strength.WATCH, 30));
		assertFalse(PkWatch.shouldNotify(PkWatch.Strength.WATCH, PkWatch.Strength.WATCH, 0));
		assertFalse(PkWatch.shouldNotify(PkWatch.Strength.WATCH, PkWatch.Strength.WARN, 30));
		assertTrue(PkWatch.shouldNotify(PkWatch.Strength.WATCH, PkWatch.Strength.WARN, 0));
		assertTrue(PkWatch.shouldNotify(PkWatch.Strength.WARN, PkWatch.Strength.DANGER, 30));
		assertFalse(PkWatch.shouldNotify(PkWatch.Strength.DANGER, PkWatch.Strength.WARN, 0));
	}

	/**
	 * A replay of the classic jump at level 25 with a glory: a skulled PKer walks in from the edge of view,
	 * targets you, then you walk south out of their bracket. Each tick is what the plugin would read.
	 */
	@Test
	public void replayOfAPkerWalkingIn()
	{
		PkWatch w = new PkWatch();
		int you = 92;
		int[] distances = {15, 13, 11, 9, 7, 5, 3, 2, 2, 4};
		PkWatch.Strength[] expect = {
			PkWatch.Strength.WARN, // skulled
			PkWatch.Strength.WARN, PkWatch.Strength.WARN, // skulled and closing
			PkWatch.Strength.DANGER, PkWatch.Strength.DANGER, PkWatch.Strength.DANGER, PkWatch.Strength.DANGER, // and claws out
			PkWatch.Strength.DANGER, // targets you
			PkWatch.Strength.DANGER,
			PkWatch.Strength.NONE}; // you stepped down to level 20... and they are 12 levels away
		int notifications = 0;
		PkWatch.Strength before = PkWatch.Strength.NONE;
		int cooldown = 0;
		for (int t = 0; t < distances.length; t++)
		{
			boolean targeting = t == 7 || t == 8;
			int yourWild = t == 9 ? 11 : 25;
			int theirWild = t == 9 ? 12 : 25;
			PkWatch.Sighting s = new PkWatch.Sighting("Pker", you + 12, theirWild, distances[t], true,
				t >= 3 ? ItemID.DRAGON_CLAWS : -1, false, targeting);
			PkWatch.Alarm a = w.update(you, yourWild, false, Collections.singletonList(s), GLORY_AT_25);
			assertEquals("tick " + t, expect[t], a.getStrength());
			if (t >= 1 && t <= 6)
			{
				assertTrue("tick " + t, a.getLead().isClosing());
			}
			if (t == 5)
			{
				assertEquals("Lvl 104, 5 tiles, skulled, KO weapon, closing", a.detail());
				assertEquals("Teleport now: Glory works here", a.getMove());
			}
			if (cooldown > 0)
			{
				cooldown--;
			}
			if (PkWatch.shouldNotify(before, a.getStrength(), cooldown))
			{
				notifications++;
				cooldown = 50;
			}
			before = a.getStrength();
		}
		// One when they came into view, one when they drew claws on the way in; none for every step.
		assertEquals(2, notifications);
	}
}
