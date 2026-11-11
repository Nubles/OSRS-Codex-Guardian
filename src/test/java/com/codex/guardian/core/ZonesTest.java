package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ZonesTest
{
	@Test
	public void wildernessLevels()
	{
		assertEquals(0, Zones.wildernessLevel(3093, 3493));
		assertEquals(1, Zones.wildernessLevel(3093, 3523));
		assertEquals(20, Zones.wildernessLevel(3100, 3520 + 19 * 8));
		assertEquals(0, Zones.wildernessLevel(2900, 3700));
		assertTrue(Zones.wildernessLevel(3100, 10100) > 0);
		assertTrue(Zones.nearWilderness(3093, 3515));
		assertFalse(Zones.nearWilderness(3093, 3490));
	}

	@Test
	public void knownRegions()
	{
		assertEquals("Vorkath", Zones.forRegion(9023).getName());
		assertEquals(Risk.SAFE, Zones.forRegion(9551).getRisk());
		assertEquals(Zone.Kind.RAID, Zones.forRegion(12889).getKind());
		assertNull(Zones.forRegion(12850));
	}

	@Test
	public void wildernessRiskRisesWithLevel()
	{
		assertEquals(Risk.MEDIUM, Zones.wilderness(5).getRisk());
		assertEquals(Risk.HIGH, Zones.wilderness(25).getRisk());
		assertEquals(Risk.EXTREME, Zones.wilderness(40).getRisk());
	}

	@Test
	public void readsTheGamesWildernessText()
	{
		assertEquals(23, Zones.parseWildernessLevel("Level: 23"));
		assertEquals(35, Zones.parseWildernessLevel("<col=ffff00>Level: 35</col>"));
		assertEquals(0, Zones.parseWildernessLevel(""));
		assertEquals(0, Zones.parseWildernessLevel(null));
	}

	@Test
	public void slayerCaveDepthsAreRead()
	{
		// The north end of the Wilderness Slayer Cave lies east of the surface Wilderness and above level 30.
		assertTrue(Zones.wildernessLevel(3420, 10165) > 30);
	}

	@Test
	public void hardcoreWorldsFollowTheWikisDeathList()
	{
		// Wiki, Hardcore_Worlds: "The following activities are SAFE..." Castle Wars, Clan Wars, TzHaar Fight Pit.
		Zone castleWars = Zones.forRegion(9520, true);
		assertEquals(Risk.SAFE, castleWars.getRisk());
		assertEquals(Zone.DeathRule.SAFE, castleWars.getDeathRule());
		assertEquals(Zone.DeathRule.SAFE, Zones.forRegion(9552, true).getDeathRule());
		assertEquals("Clan Wars", Zones.forRegion(13130, true).getName());
		assertEquals(Risk.SAFE, Zones.forRegion(13130, true).getRisk());
		// "...UNSAFE and dying in them will result in an official death": Nightmare Zone, Inferno, Pest Control, houses.
		Zone nmz = Zones.forRegion(9033, true);
		assertEquals(Zone.DeathRule.UNSAFE, nmz.getDeathRule());
		assertEquals(Risk.MEDIUM, nmz.getRisk());
		assertFalse(Zones.isUnconfirmedSafe(nmz));
		assertEquals(Risk.EXTREME, Zones.forRegion(9043, true).getRisk());
		assertEquals(Zone.DeathRule.UNSAFE, Zones.forRegion(10536, true).getDeathRule());
		assertEquals(Zone.DeathRule.UNSAFE, Zones.forRegion(7534, true).getDeathRule());
		assertEquals(Zone.DeathRule.UNSAFE, Zones.forRegion(12889, true).getDeathRule());
		// "TzHaar Fight Cave (the elite diary resurrection is safe)": kept open until the game says.
		Zone caves = Zones.forRegion(9551, true);
		assertEquals(Zone.DeathRule.UNSAFE_UNLESS_DIARY, caves.getDeathRule());
		assertEquals(Risk.HIGH, caves.getRisk());
		assertEquals("Minigame", caves.kindLabel());
		assertTrue(Zones.isUnconfirmedSafe(caves));
		assertEquals(Zone.DeathRule.UNSAFE_UNLESS_DIARY, Zones.forRegion(9007, true).getDeathRule());
		// Not on the list either way: the Colosseum and the Gauntlet stay unconfirmed.
		assertEquals(Zone.DeathRule.UNCONFIRMED, Zones.forRegion(7216, true).getDeathRule());
		assertEquals(Risk.EXTREME, Zones.forRegion(7216, true).getRisk());
		assertEquals(Zone.DeathRule.UNCONFIRMED, Zones.forRegion(12127, true).getDeathRule());
		assertTrue(Zones.forRegion(7216, true).getAdvice().contains("can't confirm"));
		// A boat: only capsizing is safe.
		assertEquals(Zone.DeathRule.BOAT, Zones.atSea().getDeathRule());
		assertTrue(Zones.isUnconfirmedSafe(Zones.atSea()));
		// Places the list does not name are unchanged, and the extra places exist only on a Hardcore World.
		assertSame(Zones.forRegion(9023), Zones.forRegion(9023, true));
		assertNull(Zones.forRegion(13130));
		assertNull(Zones.forRegion(7534, false));
		assertNull(Zones.onHardcoreWorld(null));
		assertNull(Zones.forRegion(9551).getDeathRule());
		assertFalse(Zones.isUnconfirmedSafe(Zones.forRegion(9551)));
		assertEquals("Safe minigame", Zones.forRegion(9551).kindLabel());
	}

	@Test
	public void everyPlaceTheRulesNameIsOnTheMap()
	{
		java.util.Set<String> mapped = new java.util.HashSet<>();
		for (int region = 0; region < 32768; region++)
		{
			Zone zone = Zones.forRegion(region, true);
			if (zone != null)
			{
				mapped.add(zone.getName());
				if (HardcoreRules.forZone(zone.getName()) != null)
				{
					assertNotNull(zone.getName(), zone.getDeathRule());
				}
			}
		}
		for (String name : HardcoreRules.namedPlaces())
		{
			assertTrue(name, mapped.contains(name));
		}
	}
}
