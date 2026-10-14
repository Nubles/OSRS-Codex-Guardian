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
		j.death("Vorkath", "Vorkath", true, 1);
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
}
