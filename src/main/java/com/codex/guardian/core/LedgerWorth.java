package com.codex.guardian.core;

import com.google.gson.Gson;

/**
 * What the account is worth, as Codex Ledger keeps it in its RS-profile key {@code codexledger/ledger}:
 * <pre>{"total":12400000,"since":1760000000000,"history":[{"t":1760000000000,"v":12000000}]}</pre>
 * Only {@code total} and {@code since} are read, and only to show on the run's seal what a death would cost.
 * Without Ledger, or before it has seen the bank, there is no figure and the seal shows something else.
 */
public final class LedgerWorth
{
	public static final String GROUP = "codexledger";
	public static final String KEY = "ledger";

	private LedgerWorth()
	{
	}

	/** Only the two fields read; Gson steps over the rest of Ledger's save without building it. */
	static final class Saved
	{
		Long total;
		/** Set once Ledger has seen the bank; before that its total is only the pack. */
		Long since;
	}

	/** The account's worth in coins, or -1 when Ledger has no full figure yet or the saved value can't be read. */
	public static long read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return -1;
		}
		try
		{
			Saved saved = gson.fromJson(json, Saved.class);
			if (saved == null || saved.total == null || saved.since == null)
			{
				return -1;
			}
			return saved.total > 0 && saved.since > 0 ? saved.total : -1;
		}
		catch (RuntimeException e)
		{
			return -1;
		}
	}

	/** "12.4M", "830K", "950 gp": short enough for one column of the seal. */
	public static String shortCoins(long coins)
	{
		if (coins >= 1_000_000_000L)
		{
			return trim(coins / 1e9) + "B";
		}
		if (coins >= 10_000_000L)
		{
			return Math.round(coins / 1e6) + "M";
		}
		if (coins >= 1_000_000L)
		{
			return trim(coins / 1e6) + "M";
		}
		if (coins >= 1_000L)
		{
			return Math.round(coins / 1e3) + "K";
		}
		return coins + " gp";
	}

	private static String trim(double v)
	{
		double r = Math.floor(v * 10) / 10;
		return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
	}
}
