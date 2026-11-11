package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a death would keep and what it would cost, by the rule the wiki gives (wiki: Items_Kept_on_Death): you "keep the
 * three most valuable items"; Protect Item "always protects an additional item"; "If a player is skulled the number of
 * protected items is zero". On Hardcore Worlds: "For most deaths the player will keep 3 items (or 4 if Protect Item is
 * enabled)" (wiki: Hardcore_Worlds). Each single item counts: a stack of sharks is many items.
 *
 * <p>Items are valued at the Grand Exchange price RuneLite gives, so an untradeable reads as worth nothing here; the
 * game's own Items Kept on Death screen has the final word.
 */
public final class KeptOnDeath
{
	/** Items kept on an ordinary death, unskulled and without Protect Item. */
	public static final int KEPT = 3;

	/** One kind of item carried, with its price each. */
	public static final class Carried
	{
		final String name;
		final int quantity;
		final long price;

		public Carried(String name, int quantity, long price)
		{
			this.name = name == null ? "Unknown" : name;
			this.quantity = Math.max(0, quantity);
			this.price = Math.max(0, price);
		}
	}

	private final List<String> kept;
	private final long keptValue;
	private final long lostValue;
	private final int keepCount;

	private KeptOnDeath(List<String> kept, long keptValue, long lostValue, int keepCount)
	{
		this.kept = kept;
		this.keptValue = keptValue;
		this.lostValue = lostValue;
		this.keepCount = keepCount;
	}

	/** How many items a death keeps: three, none when skulled, one more with Protect Item. */
	public static int keepCount(boolean skulled, boolean protectItem)
	{
		return (skulled ? 0 : KEPT) + (protectItem ? 1 : 0);
	}

	/** Splits what is carried into the items a death keeps (the most valuable single items) and the rest. */
	public static KeptOnDeath of(List<Carried> carried, boolean skulled, boolean protectItem)
	{
		int keep = keepCount(skulled, protectItem);
		List<Carried> sorted = new ArrayList<>(carried == null ? Collections.emptyList() : carried);
		sorted.removeIf(c -> c == null || c.quantity <= 0);
		sorted.sort((a, b) -> Long.compare(b.price, a.price));
		List<String> kept = new ArrayList<>();
		long keptValue = 0;
		long total = 0;
		int left = keep;
		for (Carried c : sorted)
		{
			total = saturatedAdd(total, saturatedTimes(c.price, c.quantity));
			int take = Math.min(left, c.quantity);
			for (int i = 0; i < take; i++)
			{
				kept.add(c.name);
			}
			keptValue = saturatedAdd(keptValue, saturatedTimes(c.price, take));
			left -= take;
		}
		return new KeptOnDeath(Collections.unmodifiableList(kept), keptValue, Math.max(0, total - keptValue), keep);
	}

	/** The single items kept, most valuable first; a name repeats for each one of a kind. */
	public List<String> getKept()
	{
		return kept;
	}

	public long getKeptValue()
	{
		return keptValue;
	}

	/** The worth of everything else carried: what the death would cost. */
	public long getLostValue()
	{
		return lostValue;
	}

	public int getKeepCount()
	{
		return keepCount;
	}

	private static long saturatedTimes(long a, long b)
	{
		if (a == 0 || b == 0)
		{
			return 0;
		}
		return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
	}

	private static long saturatedAdd(long a, long b)
	{
		long sum = a + b;
		return sum < 0 ? Long.MAX_VALUE : sum;
	}
}
