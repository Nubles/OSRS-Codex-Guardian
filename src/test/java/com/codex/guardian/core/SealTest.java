package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import org.junit.Test;

public class SealTest
{
	private static final long HOUR = Seal.TICKS_PER_HOUR;

	@Test
	public void tiersFollowHoursPlayed()
	{
		assertEquals(Seal.Tier.BRONZE, Seal.tier(0));
		assertEquals(Seal.Tier.BRONZE, Seal.tier(5 * HOUR - 1));
		assertEquals(Seal.Tier.IRON, Seal.tier(5 * HOUR));
		assertEquals(Seal.Tier.ADAMANT, Seal.tier(131 * HOUR));
		assertEquals(Seal.Tier.DRAGON, Seal.tier(5000 * HOUR));
		assertNull(Seal.Tier.DRAGON.next());
		assertEquals(Seal.Tier.RUNE, Seal.Tier.ADAMANT.next());
	}

	@Test
	public void roadToTheNextSeal()
	{
		assertEquals(0, Seal.progress(0), 1e-9);
		assertEquals(0.5, Seal.progress(10 * HOUR), 1e-9);
		assertEquals(5, Seal.hoursToNext(10 * HOUR));
		// Part of an hour left still reads as an hour, never "in 0h".
		assertEquals(1, Seal.hoursToNext(15 * HOUR - 1));
		assertEquals(119, Seal.hoursToNext(131 * HOUR));
		assertEquals(1, Seal.progress(600 * HOUR), 1e-9);
		assertEquals(0, Seal.hoursToNext(600 * HOUR));
	}

	@Test
	public void ledgerWorthIsReadOnlyOnceLedgerHasSeenTheBank()
	{
		Gson gson = new Gson();
		// Ledger's own save carries much more; only total and since are read.
		String saved = "{\"stores\":{\"BANK\":{\"items\":{\"995\":5}}},\"prices\":{\"995\":1},\"history\":[{\"t\":1,\"v\":2}],"
			+ "\"days\":[],\"total\":12400000,\"since\":1760000000000,\"exchange\":{},\"slotsKnown\":true}";
		assertEquals(12_400_000L, LedgerWorth.read(gson, saved));
		assertEquals(-1, LedgerWorth.read(gson, "{\"total\":5000,\"since\":0}"));
		assertEquals(-1, LedgerWorth.read(gson, "{\"total\":5000}"));
		assertEquals(-1, LedgerWorth.read(gson, null));
		assertEquals(-1, LedgerWorth.read(gson, "not json"));
		assertEquals(-1, LedgerWorth.read(gson, "[1,2]"));
		assertEquals(-1, LedgerWorth.read(gson, "{\"total\":\"lots\",\"since\":5}"));
	}

	@Test
	public void coinsFitOneColumn()
	{
		assertEquals("950 gp", LedgerWorth.shortCoins(950));
		// The suite's one rule: whole thousands below a million, millions to one decimal, billions to two, so
		// a figure never changes precision as it grows within a unit ("9.9M" then "12.4M", never "12M").
		assertEquals("830k", LedgerWorth.shortCoins(830_000));
		assertEquals("1.2M", LedgerWorth.shortCoins(1_290_000));
		assertEquals("12.4M", LedgerWorth.shortCoins(12_400_000));
		assertEquals("1.23B", LedgerWorth.shortCoins(1_234_567_890L));
		assertEquals("2.00B", LedgerWorth.shortCoins(2_000_000_000L));
	}
}
