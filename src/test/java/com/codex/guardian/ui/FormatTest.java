package com.codex.guardian.ui;

import static org.junit.Assert.assertEquals;
import com.codex.guardian.core.LedgerWorth;
import org.junit.Test;

/** Coin figures follow the suite's one rule, so the seal's worth reads as Codex Ledger's own panel does. */
public class FormatTest
{
	@Test
	public void amountsFollowTheSuiteRule()
	{
		// In full below 10,000; whole k below a million; M to one decimal; B to two.
		assertEquals("950", LedgerWorth.shortCoins(950));
		assertEquals("8,240", LedgerWorth.shortCoins(8_240));
		assertEquals("10k", LedgerWorth.shortCoins(10_000));
		assertEquals("96k", LedgerWorth.shortCoins(95_700));
		assertEquals("830k", LedgerWorth.shortCoins(830_000));
		assertEquals("1.3M", LedgerWorth.shortCoins(1_290_000));
		assertEquals("12.4M", LedgerWorth.shortCoins(12_400_000));
		assertEquals("812.4M", LedgerWorth.shortCoins(812_400_000));
		assertEquals("1.23B", LedgerWorth.shortCoins(1_234_567_890L));
		assertEquals("2.10B", LedgerWorth.shortCoins(2_100_000_000L));
		// Rounding that reaches the next unit is written in it, never "1000k".
		assertEquals("100k", LedgerWorth.shortCoins(99_960));
		assertEquals("999k", LedgerWorth.shortCoins(999_499));
		assertEquals("1.0M", LedgerWorth.shortCoins(999_500));
		assertEquals("1.0M", LedgerWorth.shortCoins(999_600));
		assertEquals("1.00B", LedgerWorth.shortCoins(999_960_000));
	}
}
