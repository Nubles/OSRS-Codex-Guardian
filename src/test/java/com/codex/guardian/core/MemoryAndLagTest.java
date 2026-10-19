package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Remembered hits, lag, and what a place remembers of you. */
public class MemoryAndLagTest
{
	private final PerilAssessor assessor = new PerilAssessor(80);

	private static Readiness stocked()
	{
		return Supplies.assess(Arrays.asList(new Supplies.Item("Shark", 10), new Supplies.Item("Varrock teleport", 2)),
			Collections.emptyList(), 99);
	}

	private static Situation.Builder base()
	{
		return new Situation.Builder().loggedIn(true).playerName("Guardian").combatLevel(100).hitpoints(99, 99)
			.prayer(70, 70).readiness(stocked());
	}

	// ---- Hit memory ----

	@Test
	public void memoryKeepsEachMonstersHardestHitByNameAndLevel()
	{
		HitMemory hits = new HitMemory();
		assertTrue(hits.record("Black demon", 172, 12, "Taverley Dungeon", 1));
		assertFalse(hits.record("Black demon", 172, 9, "Taverley Dungeon", 2));
		assertTrue(hits.record("Black demon", 172, 16, "Catacombs of Kourend", 3));
		hits.record("Skeleton", 22, 3, null, 4);
		hits.record("Skeleton", 85, 11, null, 5);
		assertEquals(16, hits.max("Black demon", 172));
		assertEquals(3, hits.max("Skeleton", 22));
		assertEquals(11, hits.max("Skeleton", 85));
		assertEquals(0, hits.max("Greater demon", 92));
		assertEquals(0, hits.max(null, 1));
		assertEquals("Black demon", hits.hardest(1).get(0).getName());
		assertEquals(3, hits.hardest(10).get(0).getHits());
		assertEquals("Catacombs of Kourend", hits.hardest(1).get(0).getPlace());
		// Nothing for a hit of nothing.
		assertFalse(hits.record("Imp", 2, 0, null, 6));
		assertEquals(0, hits.max("Imp", 2));
	}

	@Test
	public void itemsThatHurtToUseAreKnownSoTheirHitIsNotBlamedOnAMonster()
	{
		assertTrue(HitMemory.hurtsToUse("Divine super combat potion(4)"));
		assertTrue(HitMemory.hurtsToUse("Divine ranging potion(1)"));
		assertTrue(HitMemory.hurtsToUse("Overload (4)"));
		assertTrue(HitMemory.hurtsToUse("Zamorak brew(3)"));
		assertTrue(HitMemory.hurtsToUse("Zamorak mix(2)"));
		assertTrue(HitMemory.hurtsToUse("Dwarven rock cake"));
		assertTrue(HitMemory.hurtsToUse("Locator orb"));
		assertFalse(HitMemory.hurtsToUse("Shark"));
		assertFalse(HitMemory.hurtsToUse("Saradomin brew(4)"));
		assertFalse(HitMemory.hurtsToUse("Super combat potion(4)"));
		assertFalse(HitMemory.hurtsToUse(null));
	}

	@Test
	public void theWildernessCountsAsOnePlaceForMemory()
	{
		HitMemory hits = new HitMemory();
		hits.record("Revenant knight", 126, 30, "Wilderness level 31", 1);
		hits.record("Chaos druid", 13, 4, "Wilderness level 12", 2);
		assertEquals("Revenant knight", hits.hardestAt("Wilderness level 5").getName());
		assertNull(hits.hardestAt("Vorkath"));
		assertNull(hits.hardestAt(null));
	}

	@Test
	public void aFullMemoryForgetsTheSoftestHitterButNeverTheNewOne()
	{
		HitMemory hits = new HitMemory();
		for (int i = 0; i < HitMemory.MAX_RECORDS; i++)
		{
			hits.record("Goblin " + i, 2, 2 + i % 5, null, i);
		}
		hits.record("Rat", 1, 1, null, 9_999);
		assertEquals(HitMemory.MAX_RECORDS, hits.size());
		assertEquals(1, hits.max("Rat", 1));
		// The oldest of the softest (a goblin that hit for 2 at time 0) went instead.
		assertEquals(0, hits.max("Goblin 0", 2));
	}

	@Test
	public void memorySurvivesASaveAndShrugsOffDamage()
	{
		Gson gson = new Gson();
		HitMemory hits = new HitMemory();
		hits.record("Vorkath", 732, 58, "Vorkath", 1);
		HitMemory back = gson.fromJson(gson.toJson(hits), HitMemory.class).repair();
		assertEquals(58, back.max("Vorkath", 732));
		HitMemory broken = gson.fromJson("{\"records\":{\"x\":null,\"y\":{\"max\":3},\"z\":{\"name\":\"Imp\",\"level\":2,\"max\":2}}}",
			HitMemory.class).repair();
		assertEquals(1, broken.size());
		assertEquals(0, gson.fromJson("{}", HitMemory.class).repair().size());
	}

	@Test
	public void aRememberedHitCallsPerilBeforeTheFirstHitLands()
	{
		// 15 hitpoints, a black demon on you that has hit for 16 before, and nothing has landed yet this fight.
		Situation s = base().hitpoints(15, 99).attacker(new Threat("Black demon", 172, false, 16)).build();
		assertEquals(0, s.getRecentMaxHit());
		Peril p = assessor.assess(s);
		assertEquals(Peril.Level.PERIL, p.getLevel());
		assertTrue(p.getReasons().stream().anyMatch(r -> r.getText().equals("Black demon has hit you for 16: one hit could end the run")));
		// The advice is to leave, not to trade blows.
		assertEquals("Teleport out now: Varrock tab", Advice.now(s, p));
		// With hitpoints above anything it has done, it is just a fight.
		Peril safe = assessor.assess(base().hitpoints(60, 99).attacker(new Threat("Black demon", 172, false, 16)).build());
		assertFalse(safe.getLevel().atLeast(Peril.Level.DANGER));
	}

	@Test
	public void twoRememberedHittersCanLandOnOneTick()
	{
		Situation s = base().hitpoints(25, 99).multiCombat(true).attacker(new Threat("Black demon", 172, false, 16))
			.attacker(new Threat("Black demon", 172, false, 16)).attacker(new Threat("Imp", 2, false, 1)).build();
		assertEquals(32, s.rememberedWorst());
		Peril p = assessor.assess(s);
		assertEquals(Peril.Level.PERIL, p.getLevel());
		assertTrue(p.getReasons().stream().anyMatch(r -> r.getText().equals("Together they have hit for 32: one tick could end the run")));
		// In single-way combat only one of them can be hitting you, so only the harder hit counts.
		Situation single = base().hitpoints(25, 99).attacker(new Threat("Black demon", 172, false, 16))
			.attacker(new Threat("Black demon", 172, false, 16)).build();
		assertEquals(16, single.rememberedWorst());
		assertFalse(assessor.assess(single).getLevel().atLeast(Peril.Level.PERIL));
		// A monster that has never hit you alone adds nothing.
		assertEquals(16, base().multiCombat(true).attacker(new Threat("Black demon", 172, false, 16)).attacker(new Threat("Ghost", 19, false))
			.build().rememberedWorst());
	}

	// ---- Lag ----

	private static Lag ticks(long... gaps)
	{
		Lag lag = new Lag();
		long now = 0;
		lag.tick(now);
		for (long gap : gaps)
		{
			now += gap;
			lag.tick(now);
		}
		return lag;
	}

	@Test
	public void ticksOnTimeAreSteady()
	{
		Lag lag = ticks(600, 600, 610, 590, 640, 600);
		assertEquals(Lag.State.STEADY, lag.state());
		assertEquals("Steady", lag.describe());
		// One slow tick is a hiccup, not lag.
		assertEquals(Lag.State.STEADY, ticks(600, 1_100, 600, 600).state());
	}

	@Test
	public void lateTicksAreShakyAndAFreezeIsFrozen()
	{
		Lag shaky = ticks(600, 1_200, 600, 1_300, 600);
		assertEquals(Lag.State.SHAKY, shaky.state());
		assertEquals("2 late ticks", shaky.describe());
		Lag frozen = ticks(600, 600, 4_200, 600);
		assertEquals(Lag.State.FROZEN, frozen.state());
		assertEquals("Froze for 4s", frozen.describe());
		// A freeze stays on screen for a few ticks, then fades once the ticks are steady again.
		for (int i = 0; i < Lag.FREEZE_HOLD; i++)
		{
			frozen.tick(5_400 + 600L * (i + 1));
		}
		assertEquals(Lag.State.STEADY, frozen.state());
		// Late ticks leave the window after twenty steady ones.
		long now = 4_200;
		for (int i = 0; i < Lag.WINDOW; i++)
		{
			now += 600;
			shaky.tick(4_300 + now);
		}
		assertEquals(Lag.State.STEADY, shaky.state());
	}

	@Test
	public void aRegionLoadOrLoginIsNotLag()
	{
		Lag lag = ticks(600, 600);
		lag.skip();
		lag.tick(10_000);
		lag.tick(10_600);
		assertEquals(Lag.State.STEADY, lag.state());
		lag.reset();
		lag.tick(50_000);
		assertEquals(Lag.State.STEADY, lag.state());
		assertEquals(0, lag.lateTicks());
	}

	@Test
	public void lagCountsOnlyWhereSomethingCanHurtYou()
	{
		Situation town = base().lag(Lag.State.SHAKY, "3 late ticks").build();
		assertFalse(assessor.assess(town).getReasons().stream().anyMatch(r -> r.getTopic() == Peril.Topic.LINK));
		Situation fight = base().hitpoints(50, 99).damage(2, 8).attacker(new Threat("Hill giant", 28, false))
			.zone(Zones.forRegion(9023)).lag(Lag.State.FROZEN, "Froze for 4s").build();
		Peril p = assessor.assess(fight);
		assertTrue(p.getReasons().stream().anyMatch(r -> r.getText().equals("The game froze: froze for 4s")));
		assertTrue(p.getLevel().atLeast(Peril.Level.DANGER));
		assertEquals("Lagging: teleport out", Advice.now(fight, p));
	}

	// ---- Places ----

	@Test
	public void aPlaceRemembersItsCloseCallsAndHardestHit()
	{
		Journal journal = Journal.fresh(0);
		journal.closeCall(9, 99, "Taverley Dungeon", "Black demon", 1);
		journal.closeCall(4, 99, "Taverley Dungeon", "Black demon", 2);
		journal.closeCall(20, 99, "Wilderness level 14", "a player", 3);
		HitMemory hits = new HitMemory();
		hits.record("Black demon", 172, 16, "Taverley Dungeon", 4);
		List<String> lines = PlaceMemory.lines(journal, hits, "Taverley Dungeon");
		assertEquals(Arrays.asList("2 close calls here, 4 hp low", "Black demon hit you for 16 here"), lines);
		assertEquals(Collections.singletonList("A close call here, 20 hp low"), PlaceMemory.lines(journal, hits, "Wilderness level 30"));
		assertTrue(PlaceMemory.lines(journal, hits, "Lumbridge").isEmpty());
		assertTrue(PlaceMemory.lines(null, null, null).isEmpty());
	}

	@Test
	public void oldJournalsReadWithoutTheNewFields()
	{
		Gson gson = new Gson();
		Journal old = gson.fromJson("{\"runStartedAt\":5,\"closeCalls\":1,\"closeCallsByPlace\":{\"Vorkath\":1},\"entries\":[]}",
			Journal.class).repair();
		assertEquals(0, old.getLagSpikes());
		assertEquals("A close call here", old.memoryOf("Vorkath"));
		old.closeCall(30, 99, "Vorkath", "Vorkath", 9);
		assertEquals("2 close calls here, 30 hp low", old.memoryOf("Vorkath"));
		old.lag("3 late ticks", "Vorkath", 10);
		assertEquals(1, old.getLagSpikes());
		Journal.Entry last = old.getEntries().get(old.getEntries().size() - 1);
		assertEquals(Journal.Type.LAG, last.getType());
		assertEquals("3 late ticks at Vorkath", last.getDetail());
		// An entry type this version does not know (from a newer one) is dropped, not a crash.
		Journal future = gson.fromJson("{\"entries\":[{\"at\":1,\"type\":\"SOMETHING_NEW\",\"title\":\"x\"}]}", Journal.class).repair();
		assertTrue(future.getEntries().isEmpty());
	}

	@Test
	public void memoryNotesNeverMakeATripLookUnready()
	{
		Readiness ready = stocked();
		TripCheck trip = TripCheck.build(null, null, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
			ready, Collections.emptyMap(), 80);
		boolean clear = trip.isClear();
		TripCheck noted = trip.withMemory(Collections.singletonList("Vorkath hit you for 58 here"));
		assertEquals(clear, noted.isClear());
		assertEquals(trip.getLines().size() + 1, noted.getLines().size());
		assertEquals(Peril.Tone.NOTE, noted.getLines().get(noted.getLines().size() - 1).getTone());
		assertSame(trip, trip.withMemory(Collections.emptyList()));
	}
}
