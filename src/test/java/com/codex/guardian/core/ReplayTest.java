package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import org.junit.Test;

/** Replays of close calls and deaths: what is recorded, what is cut, and what the journal keeps. */
public class ReplayTest
{
	private static final Gson GSON = new Gson();

	/** Feeds steady full-health ticks. */
	private static void calm(Replay.Recorder r, int ticks, int food)
	{
		for (int i = 0; i < ticks; i++)
		{
			r.tick(90, 90, food, 8);
		}
	}

	@Test
	public void aCloseCallIsCutFromJustBeforeTheFallToTheRecovery()
	{
		Replay.Recorder r = new Replay.Recorder();
		CloseCallWatch watch = new CloseCallWatch();
		calm(r, 30, 10);
		// Three hits in a row from Vorkath, then two sharks and a prayer dose, and back to health.
		int[][] ticks = {
			// hp, hit, food, doses
			{62, 28, 10, 8}, {44, 18, 10, 8}, {12, 32, 10, 8}, {32, 0, 9, 8}, {52, 0, 8, 7}, {72, 0, 7, 7}, {90, 0, 6, 7}};
		Replay replay = null;
		for (int[] t : ticks)
		{
			r.hit(t[1], "Vorkath");
			r.tick(t[0], 90, t[2], t[3]);
			CloseCallWatch.Result done = watch.update(t[0], 90, 25, "Vorkath", "Vorkath");
			if (watch.isActive())
			{
				r.mark();
			}
			if (done != null)
			{
				replay = r.closeCall();
			}
		}
		assertNotNull(replay);
		assertTrue(replay.isValid());
		assertFalse(r.isMarked());
		// Eight ticks of lead before the first low tick, then the low tick and the climb back past half.
		assertEquals(Replay.LEAD_TICKS + 3, replay.ticks());
		assertEquals(52, replay.hpAt(replay.ticks() - 1));
		assertEquals(12, replay.hpAt(replay.lowestIndex()));
		assertEquals(78, replay.damageToLowest());
		assertEquals(32, replay.getWorstHit());
		assertEquals("Biggest hit 32, Vorkath", replay.worstLine());
		assertEquals(2, replay.totalAte());
		assertEquals(1, replay.totalDrank());
		assertEquals("Took 78 in 2s · ate 2 · drank 1", replay.summary());
	}

	@Test
	public void theFallLineCountsOnlyTheHitsOfTheFall()
	{
		Replay.Recorder r = new Replay.Recorder();
		calm(r, 4, 10);
		// Chip damage while still healthy, then two big hits.
		int[][] ticks = {{84, 6}, {80, 4}, {78, 2}, {40, 38}, {10, 30}};
		for (int[] t : ticks)
		{
			r.hit(t[1], "Pker");
			r.tick(t[0], 90, 10, 8);
		}
		r.mark();
		Replay replay = r.death();
		// 68 came off in the two ticks of the fall; the 12 of chip damage before it is not part of that story.
		assertEquals(68, replay.damageToLowest());
		assertEquals("Took 68 in 1s · nothing eaten", replay.summary());
	}

	@Test
	public void bankingIsNotEatingAndADeathKeepsItsLastSeconds()
	{
		Replay.Recorder r = new Replay.Recorder();
		r.tick(90, 90, 28, 0);
		r.tick(90, 90, 0, 16);
		calm(r, 3, 0);
		r.hit(40, "Pker");
		r.tick(50, 90, 0, 16);
		r.hit(50, "Pker");
		r.tick(0, 90, 0, 16);
		Replay death = r.death();
		// Depositing 28 sharks and withdrawing 4 potions is not 28 sharks eaten.
		assertEquals(0, death.totalAte());
		assertEquals(0, death.totalDrank());
		assertEquals(7, death.ticks());
		assertEquals(0, death.hpAt(death.ticks() - 1));
		assertEquals("Took 90 in 1s · nothing eaten", death.summary());
		// Nothing marked: no close call to cut.
		assertNull(r.closeCall());
		r.clear();
		assertNull(r.death());
	}

	@Test
	public void aLongEpisodeKeepsTheWindowAroundItsLowestPoint()
	{
		Replay.Recorder r = new Replay.Recorder();
		calm(r, 10, 5);
		r.mark();
		// A minute and a half hovering low, with the very lowest late in it.
		for (int i = 0; i < 150; i++)
		{
			r.tick(i == 120 ? 3 : 15, 90, 5, 0);
		}
		Replay replay = r.closeCall();
		assertEquals(Replay.MAX_TICKS, replay.ticks());
		assertEquals(3, replay.hpAt(replay.lowestIndex()));
		assertTrue(replay.lowestIndex() >= Replay.MAX_TICKS - 12 - 1 && replay.lowestIndex() < Replay.MAX_TICKS);
	}

	@Test
	public void theJournalKeepsReplaysBoundedAndSurvivesASaveAndADamagedOne()
	{
		Journal j = Journal.fresh(0);
		Replay.Recorder r = new Replay.Recorder();
		calm(r, 3, 4);
		r.hit(70, "Ice troll");
		r.tick(20, 90, 4, 0);
		r.tick(20, 90, 3, 0);
		r.tick(0, 90, 3, 0);
		j.death("Ice troll island", "Ice troll", true, false, r.death(), 5);
		for (int i = 0; i < Journal.MAX_REPLAYS + 5; i++)
		{
			r.mark();
			r.tick(10, 90, 2, 0);
			r.tick(60, 90, 1, 0);
			j.closeCall(10, 90, "Wilderness", "Pker", r.closeCall(), 10 + i);
		}
		long kept = j.getEntries().stream().filter(e -> e.getReplay() != null).count();
		// The newest close calls keep theirs, and the death always keeps its own.
		assertEquals(Journal.MAX_REPLAYS + 1, kept);
		Journal.Entry death = j.getEntries().stream().filter(e -> e.getType() == Journal.Type.DEATH).findFirst().get();
		assertNotNull(death.getReplay());
		assertEquals(70, death.getReplay().getWorstHit());

		Journal back = GSON.fromJson(GSON.toJson(j), Journal.class).repair();
		Replay last = back.getEntries().get(back.getEntries().size() - 1).getReplay();
		assertEquals(Arrays.asList(10, 60), Arrays.asList(last.hpAt(last.ticks() - 2), last.hpAt(last.ticks() - 1)));
		// An old journal has no replays; a hand-damaged one is dropped rather than drawn.
		String damaged = GSON.toJson(j).replaceFirst("\"hit\":\\[[0-9,]*\\]", "\"hit\":[1]");
		Journal fixed = GSON.fromJson(damaged, Journal.class).repair();
		assertEquals(kept - 1, fixed.getEntries().stream().filter(e -> e.getReplay() != null).count());
		assertNull(GSON.fromJson("{\"entries\":[{\"at\":1,\"type\":\"CLOSE_CALL\",\"title\":\"x\"}]}", Journal.class).repair()
			.getEntries().get(0).getReplay());
	}

	@Test
	public void potionDosesLeaveJewelleryOut()
	{
		assertEquals(4 * 2 + 3 + 2, Supplies.potionDoses(Arrays.asList(new Supplies.Item("Prayer potion(4)", 2),
			new Supplies.Item("Saradomin brew(3)", 1), new Supplies.Item("Anti-venom+(2)", 1),
			new Supplies.Item("Amulet of glory(4)", 1), new Supplies.Item("Ring of dueling(8)", 1),
			new Supplies.Item("Games necklace(8)", 1), new Supplies.Item("Shark", 5))));
	}
}
