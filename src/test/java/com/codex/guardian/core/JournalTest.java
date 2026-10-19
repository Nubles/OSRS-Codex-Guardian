package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class JournalTest
{
	@Test
	public void beginsOnce()
	{
		Journal j = new Journal();
		assertTrue(j.begin(1000));
		assertFalse(j.begin(2000));
		assertEquals(1, j.getEntries().size());
		assertEquals(Journal.Type.RUN_STARTED, j.getEntries().get(0).getType());
	}

	@Test
	public void tracksDamageAndBiggestHit()
	{
		Journal j = Journal.fresh(0);
		j.damage("Hill giant", 8, "Edgeville Dungeon", 1);
		j.damage("Hill giant", 5, "Edgeville Dungeon", 2);
		j.damage("Vorkath", 32, "Vorkath", 3);
		assertEquals(13, (int) j.getDamageBySource().get("Hill giant"));
		assertEquals(32, j.getBiggestHit().getAmount());
		assertEquals("Vorkath", j.getBiggestHit().getSource());
	}

	@Test
	public void zoneVisitsAreNotRepeated()
	{
		Journal j = Journal.fresh(0);
		Zone vorkath = Zones.forRegion(9023);
		assertTrue(j.zone(vorkath, 1000));
		assertFalse(j.zone(vorkath, 2000));
		assertTrue(j.zone(vorkath, 1000 + Journal.ZONE_REPEAT_MS + 1));
		assertFalse(j.zone(Zones.forRegion(10536), 5000));
	}

	@Test
	public void keepsDeathsWhenTrimming()
	{
		Journal j = Journal.fresh(0);
		j.death("Vorkath", "Vorkath", true, false, 1);
		for (int i = 0; i < Journal.MAX_ENTRIES + 20; i++)
		{
			j.milestone("Level", null, i + 2);
		}
		assertEquals(Journal.MAX_ENTRIES, j.getEntries().size());
		assertEquals(Journal.Type.RUN_STARTED, j.getEntries().get(0).getType());
		assertEquals(Journal.Type.DEATH, j.getEntries().get(1).getType());
	}

	@Test
	public void closeCallIsReportedOnRecovery()
	{
		CloseCallWatch watch = new CloseCallWatch();
		assertNull(watch.update(60, 99, 20, "Taverley Dungeon", null));
		assertNull(watch.update(18, 99, 20, "Taverley Dungeon", "Black demon"));
		assertNull(watch.update(9, 99, 20, "Taverley Dungeon", null));
		assertNull(watch.update(30, 99, 20, "Taverley Dungeon", null));
		CloseCallWatch.Result r = watch.update(60, 99, 20, "Taverley Dungeon", null);
		assertNotNull(r);
		assertEquals(9, r.lowest);
		assertEquals("Black demon", r.source);
		assertFalse(watch.isActive());
	}

	@Test
	public void repairFillsMissingFields()
	{
		Journal j = new Journal();
		j.entries = null;
		j.damageBySource = null;
		j.closeCallsByPlace = null;
		j.repair();
		assertNotNull(j.getEntries());
		assertNotNull(j.getDamageBySource());
	}

	@Test
	public void safeDeathDoesNotEndTheRun()
	{
		Journal j = Journal.fresh(0);
		j.death("Fight Caves", "TzTok-Jad", true, true, 5);
		assertEquals(0, j.getDeaths());
		assertEquals(1, j.getSafeDeaths());
		Journal.Entry last = j.getEntries().get(j.getEntries().size() - 1);
		assertEquals(Journal.Type.SAFE_DEATH, last.getType());
		assertEquals("At Fight Caves, to TzTok-Jad", last.getDetail());
	}

	@Test
	public void unsafeDeathOnOneLifeFallsTheRun()
	{
		Journal j = Journal.fresh(0);
		j.death("Vorkath", "Vorkath", true, false, 5);
		assertEquals(1, j.getDeaths());
		assertEquals("The run has fallen", j.getEntries().get(1).getTitle());
	}

	@Test
	public void wildernessCloseCallsRankTogether()
	{
		Journal j = Journal.fresh(0);
		j.closeCall(9, 99, "Wilderness level 18", "Revenant imp", 1);
		j.closeCall(12, 99, "Wilderness level 31", null, 2);
		assertEquals(2, (int) j.getCloseCallsByPlace().get("Wilderness"));
		assertEquals("Wilderness level 18", j.getLowestPlace());
		assertTrue(j.getEntries().get(1).getDetail().contains("in Wilderness level 18"));
	}

	@Test
	public void repairMergesOldWildernessRows()
	{
		Journal j = Journal.fresh(0);
		j.closeCallsByPlace.put("Wilderness level 18", 2);
		j.closeCallsByPlace.put("Wilderness level 24", 1);
		j.closeCallsByPlace.put("Vorkath", 1);
		j.repair();
		assertEquals(3, (int) j.getCloseCallsByPlace().get("Wilderness"));
		assertEquals(1, (int) j.getCloseCallsByPlace().get("Vorkath"));
		assertEquals(2, j.getCloseCallsByPlace().size());
	}

	@Test
	public void statusLossIsRecordedOnce()
	{
		Journal j = Journal.fresh(0);
		j.noteHardcore();
		assertTrue(j.isHardcoreSeen());
		j.statusLost(5, "Noticed at login");
		j.statusLost(6);
		assertTrue(j.isStatusLost());
		assertEquals(2, j.getEntries().size());
		assertEquals("Noticed at login", j.getEntries().get(1).getDetail());
	}

	@Test
	public void worstTickAddsUpHitsLandingTogether()
	{
		DamageLog log = new DamageLog();
		log.hit(15);
		log.hit(14);
		log.tick();
		log.hit(20);
		log.tick();
		assertEquals(20, log.recentMaxHit());
		assertEquals(29, log.recentWorstTick());
	}

	@Test
	public void aMinigameDeathOnAHardcoreWorldWaitsForThePlayersWord()
	{
		Journal j = Journal.fresh(0);
		j.unconfirmedDeath("Pest Control", "a brawler", null, 1_000);
		// Neither a death that ends the run nor a safe one.
		assertEquals(0, j.getDeaths());
		assertEquals(0, j.getSafeDeaths());
		assertEquals(1, j.getUnconfirmedDeaths());
		Journal.Entry e = j.getEntries().get(j.getEntries().size() - 1);
		assertEquals(Journal.Type.UNCONFIRMED_DEATH, e.getType());
		assertEquals(Journal.UNCONFIRMED_TITLE, e.getTitle());

		assertFalse("only the entry at that moment", j.markFallen(999));
		assertTrue(j.markFallen(1_000));
		assertEquals(1, j.getDeaths());
		assertEquals(0, j.getUnconfirmedDeaths());
		assertEquals(Journal.Type.DEATH, e.getType());
		assertEquals("The run has fallen", e.getTitle());
		assertFalse("marked once", j.markFallen(1_000));

		// The pending entry survives a save and is never pruned like an ordinary line.
		com.google.gson.Gson gson = new com.google.gson.Gson();
		Journal k = Journal.fresh(0);
		k.unconfirmedDeath("The Inferno", null, null, 5);
		for (int i = 0; i < Journal.MAX_ENTRIES + 20; i++)
		{
			k.closeCall(10, 99, "Somewhere", "a goblin", 10 + i);
		}
		Journal back = gson.fromJson(gson.toJson(k), Journal.class).repair();
		assertEquals(1, back.getUnconfirmedDeaths());
		assertTrue(back.markFallen(5));
	}
}
