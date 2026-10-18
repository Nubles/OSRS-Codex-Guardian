package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
}
