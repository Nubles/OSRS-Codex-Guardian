package com.codex.guardian.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * The whole bank as Codex Bank Steward last saw it, read only from its RS-profile key {@code codexbanksteward/bankV1}:
 * <pre>{"version":1,"savedAt":1760000000000,"items":{"995":1234567,"26950":1},"truncated":false}</pre>
 * Item ids are canonical (placeholders and noted forms folded in). Without Bank Steward, or with its sharing
 * switched off, the key is absent and Guardian simply knows less; it never guesses.
 */
public final class SharedBank
{
	public static final String GROUP = "codexbanksteward";
	public static final String KEY = "bankV1";

	private SharedBank()
	{
	}

	/** Quantities by item id, or null when the snapshot is missing, damaged or of a shape this reader does not know. */
	public static Map<Integer, Long> read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return null;
			}
			JsonElement items = root.getAsJsonObject().get("items");
			if (items == null || !items.isJsonObject())
			{
				return null;
			}
			Map<Integer, Long> out = new TreeMap<>();
			for (Map.Entry<String, JsonElement> e : ((JsonObject) items).entrySet())
			{
				try
				{
					int id = Integer.parseInt(e.getKey());
					long qty = e.getValue().getAsLong();
					if (id > 0 && qty > 0)
					{
						out.merge(id, qty, Long::sum);
					}
				}
				catch (RuntimeException skip)
				{
					// One odd entry does not spoil the rest of the bank.
				}
			}
			return out;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * The bank by name, as the trip check reads packs. {@code names} gives each id's name as the client knows it,
	 * or null for an id it does not know, which is then left out.
	 */
	public static List<Supplies.Item> byName(Map<Integer, Long> bank, IntFunction<String> names)
	{
		if (bank == null)
		{
			return null;
		}
		List<Supplies.Item> out = new ArrayList<>(bank.size());
		for (Map.Entry<Integer, Long> e : bank.entrySet())
		{
			String name = names.apply(e.getKey());
			if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name))
			{
				out.add(new Supplies.Item(name, (int) Math.min(Integer.MAX_VALUE, e.getValue())));
			}
		}
		return out;
	}

	/** How many of each last resort the bank holds, by its display name, in the order given. */
	public static Map<String, Integer> graces(Map<Integer, Long> bank, int[][] ids, String[] names)
	{
		if (bank == null)
		{
			return Collections.emptyMap();
		}
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < ids.length; i++)
		{
			long count = 0;
			for (int id : ids[i])
			{
				count += bank.getOrDefault(id, 0L);
			}
			if (count > 0)
			{
				out.put(names[i], (int) Math.min(Integer.MAX_VALUE, count));
			}
		}
		return out;
	}
}
