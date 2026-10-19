package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** How long prayer lasts, measured from the points the client shows, and what Guardian does with it. */
public class PrayerWatchTest
{
	/** One point every {@code every} ticks, from {@code from}, for {@code ticks} ticks starting at {@code tick}. */
	private static int drain(PrayerWatch w, int from, int every, int ticks, int tick)
	{
		int points = from;
		for (int i = 0; i < ticks; i++)
		{
			if (i > 0 && i % every == 0)
			{
				points--;
			}
			w.observe(points, tick + i);
		}
		return points;
	}

	@Test
	public void aSteadyDrainIsTimedAndARestoreStartsAgain()
	{
		PrayerWatch w = new PrayerWatch();
		// Protect from Melee on a modest prayer bonus: about a point every 3 ticks.
		int points = drain(w, 60, 3, 30, 100);
		assertEquals(1 / 3.0, w.getRate(), 0.02);
		assertEquals(Math.round(points * 3 * 0.6), w.secondsLeft(points), 2);
		// Each dose of prayer potion at level 70 gives 7 + 17 = 24 points.
		assertEquals(Math.round((points + 2 * 24) * 3 * 0.6), w.secondsWithDoses(points, 70, 2), 3);
		// A sip: prayer rises, and the drain must be measured again before anything is said.
		w.observe(points + 24, 130);
		assertEquals(-1, w.secondsLeft(points + 24));
		drain(w, points + 24, 3, 12, 131);
		assertTrue(w.getRate() > 0);
	}

	@Test
	public void prayersOffSayNothingAndTurningOneOnShowsQuickly()
	{
		PrayerWatch w = new PrayerWatch();
		// Twenty ticks with prayers off, then Protect from Magic and Piety: a point a tick.
		for (int t = 0; t < 20; t++)
		{
			w.observe(70, t);
		}
		assertEquals(-1, w.secondsLeft(70));
		int points = drain(w, 70, 1, 12, 20);
		// The flat stretch does not make the drain look slower than it now is.
		assertEquals(1.0, w.getRate(), 0.01);
		assertEquals(Math.round(points * 0.6), w.secondsLeft(points));
		// Prayers off again: once the window holds no fall, the estimate goes.
		for (int t = 32; t < 32 + PrayerWatch.WINDOW; t++)
		{
			w.observe(points, t);
		}
		assertEquals(-1, w.secondsLeft(points));
		// A new login (the tick count starts again) is a fresh measurement.
		w.observe(50, 3);
		assertEquals(0, w.getRate(), 0);
	}

	@Test
	public void prayersTurnedOffStopTheEstimateWithinAFewTicks()
	{
		PrayerWatch w = new PrayerWatch();
		// A point every 3 ticks for 18 ticks, then prayers off: no fall for well over two of those intervals.
		int points = drain(w, 60, 3, 18, 0);
		assertTrue(w.secondsLeft(points) > 0);
		for (int t = 18; t < 26; t++)
		{
			w.observe(points, t);
		}
		assertEquals("a flat stretch longer than two drain steps means prayer is off", -1, w.secondsLeft(points));
		// Back on: measured afresh.
		drain(w, points, 3, 12, 26);
		assertTrue(w.getRate() > 0);
	}

	@Test
	public void roundedDownForThePanel()
	{
		assertEquals(-1, PrayerWatch.rounded(-1));
		assertEquals(45, PrayerWatch.rounded(49));
		assertEquals(90, PrayerWatch.rounded(119));
		assertEquals(660, PrayerWatch.rounded(700));
		assertEquals("45s", PrayerWatch.describe(45));
		// What the panel actually gets after rounding: 90s is a minute and a half, never "2m".
		assertEquals("1m", PrayerWatch.describe(PrayerWatch.rounded(119)));
		assertEquals("2m", PrayerWatch.describe(150));
		assertEquals("9m", PrayerWatch.describe(599));
		assertEquals("1h 10m", PrayerWatch.describe(4_200));
	}

	@Test
	public void prayerAboutToFailUnderFireIsWeighedAndAdvised()
	{
		Readiness kit = Supplies.assess(Arrays.asList(new Supplies.Item("Shark", 8), new Supplies.Item("Amulet of glory(4)", 1)),
			Collections.emptyList(), 99, false);
		Situation.Builder fight = new Situation.Builder().loggedIn(true).combatLevel(100).hitpoints(45, 99).prayer(9, 70)
			.zone(Zones.byName("Vorkath")).damage(0.5, 12).attacker(new Threat("Black demon", 172, false)).readiness(kit);
		PerilAssessor assessor = new PerilAssessor(80);
		Situation running = fight.prayerLeft(10).build();
		Peril p = assessor.assess(running);
		assertTrue(p.getReasons().toString(), p.getReasons().stream().anyMatch(r -> r.getText().equals("Prayer runs out in about 10s")));
		assertEquals("Prayer almost out: teleport", Advice.now(running, p));
		// Plenty of prayer, or prayer not draining: nothing said about it.
		Situation plenty = fight.prayerLeft(240).build();
		assertTrue(assessor.assess(plenty).getReasons().stream().noneMatch(r -> r.getText().startsWith("Prayer runs out")));
		Situation off = fight.prayerLeft(-1).build();
		assertTrue(assessor.assess(off).getReasons().stream().noneMatch(r -> r.getText().startsWith("Prayer runs out")));
		// Nothing attacking: no reason to leave over it.
		Situation alone = new Situation.Builder().loggedIn(true).hitpoints(99, 99).prayer(5, 70).prayerLeft(5)
			.zone(Zones.byName("Vorkath")).readiness(kit).build();
		assertNull(Advice.now(alone, assessor.assess(alone)));
	}
}
