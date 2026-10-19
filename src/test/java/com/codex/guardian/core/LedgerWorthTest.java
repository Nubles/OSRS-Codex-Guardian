package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Codex Ledger's saved {@code codexledger/ledger}, read for the run's seal. {@code ledger-seen.json} and
 * {@code ledger-unseen.json} were written by Ledger's own Saver from its SavedShapeTest ledger (bank seen) and a
 * ledger that has only seen the pack: if Ledger renames {@code total} or {@code since}, these stop matching it.
 */
public class LedgerWorthTest
{
	private static final Gson GSON = new Gson();

	private static String fixture(String name) throws Exception
	{
		try (InputStream in = LedgerWorthTest.class.getResourceAsStream(name))
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	public void readsTheTotalOnceLedgerHasSeenTheBank() throws Exception
	{
		assertEquals("group", "codexledger", LedgerWorth.GROUP);
		assertEquals("key", "ledger", LedgerWorth.KEY);
		assertEquals(3_500_000L, LedgerWorth.read(GSON, fixture("ledger-seen.json")));
	}

	@Test
	public void aLedgerThatHasOnlySeenThePackGivesNoFigure() throws Exception
	{
		assertEquals(-1L, LedgerWorth.read(GSON, fixture("ledger-unseen.json")));
	}

	@Test
	public void absentOrUnreadableSavesGiveNoFigure()
	{
		assertEquals(-1L, LedgerWorth.read(GSON, null));
		assertEquals(-1L, LedgerWorth.read(GSON, "  "));
		assertEquals(-1L, LedgerWorth.read(GSON, "{not json"));
		assertEquals(-1L, LedgerWorth.read(GSON, "[1,2]"));
		assertEquals(-1L, LedgerWorth.read(GSON, "\"text\""));
		assertEquals(-1L, LedgerWorth.read(GSON, "{\"total\":\"lots\",\"since\":5}"));
		assertEquals(-1L, LedgerWorth.read(GSON, "{\"total\":{},\"since\":5}"));
		assertEquals(-1L, LedgerWorth.read(GSON, "{\"total\":5000}"));
		assertEquals(-1L, LedgerWorth.read(GSON, "{\"total\":-5,\"since\":5}"));
		assertEquals(-1L, LedgerWorth.read(GSON, "{\"total\":5000,\"since\":5}trailing"));
	}

	@Test
	public void shortCoinsNeverReadAThousandOfAUnit()
	{
		// Rounding up at the top of a unit used to give "1000K" and "1000M".
		assertEquals("999k", LedgerWorth.shortCoins(999_600));
		assertEquals("999.9M", LedgerWorth.shortCoins(999_960_000));
		assertEquals("9.9M", LedgerWorth.shortCoins(9_990_000));
		assertEquals("1.0M", LedgerWorth.shortCoins(1_000_000));
		assertEquals("9.99B", LedgerWorth.shortCoins(9_999_000_000L));
	}
}
