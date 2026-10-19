package com.codex.guardian.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The trip the player has packed for, as Codex Quartermaster saved it: the chosen kit's name and what it
 * takes, plus how many of each item the bank held when Quartermaster last saw it. Guardian only reads this,
 * from two RuneScape profile keys of the {@code codexquartermaster} group: {@code kitsV1}, the kits in the
 * shape Quartermaster documents for other plugins, and {@code quarters}, its own record, which alone holds
 * the bank and says whether the player ever chose a kit. Without Quartermaster there is simply no plan.
 *
 * <p>Parsing is defensive: any field may be missing or of an unexpected shape, and then that part is left out.
 */
public final class KitPlan
{
	/** One thing the kit takes: a pooled supply such as {@code PRAYER}, or a single item. */
	public static final class Need
	{
		private final String supply;
		private final int perTrip;
		private final int itemId;

		Need(String supply, int perTrip, int itemId)
		{
			this.supply = supply;
			this.perTrip = perTrip;
			this.itemId = itemId;
		}

		/** Quartermaster's supply key, such as FOOD, PRAYER, ANTIVENOM, or "item:8013". */
		public String getSupply()
		{
			return supply;
		}

		/** Hitpoints for food, doses for potions, a count for items. */
		public int getPerTrip()
		{
			return perTrip;
		}

		/** The item for a single-item need, or -1 for a pooled supply. */
		public int getItemId()
		{
			return itemId;
		}
	}

	/** Quartermaster's built-in kits are not saved in its data, only chosen by id; their names are these. */
	private static final Map<String, String> PRESETS = new HashMap<>();

	static
	{
		PRESETS.put("vorkath", "Vorkath");
		PRESETS.put("zulrah", "Zulrah");
		PRESETS.put("barrows", "Barrows");
		PRESETS.put("graardor", "General Graardor");
		PRESETS.put("vardorvis", "Vardorvis");
		PRESETS.put("nex", "Nex");
		PRESETS.put("tob", "Theatre of Blood");
		PRESETS.put("slayer", "Slayer task");
		PRESETS.put("wintertodt", "Wintertodt");
	}

	private final String name;
	private final List<Need> needs;
	private final Map<Integer, Long> bank;

	private KitPlan(String name, List<Need> needs, Map<Integer, Long> bank)
	{
		this.name = name;
		this.needs = Collections.unmodifiableList(needs);
		this.bank = Collections.unmodifiableMap(bank);
	}

	/** The chosen kit's name, or null when the player has not chosen one. */
	public String getName()
	{
		return name;
	}

	/** What the chosen kit takes; empty for a built-in kit, whose amounts Guardian does not know. */
	public List<Need> getNeeds()
	{
		return needs;
	}

	/** How many of an item the bank held when Quartermaster last saw it. */
	public long inBank(int itemId)
	{
		return bank.getOrDefault(itemId, 0L);
	}

	public boolean hasBank()
	{
		return !bank.isEmpty();
	}

	/**
	 * Reads both of Quartermaster's entries, preferring the documented {@code kitsV1} for the kit itself.
	 * {@code kitsV1} always names an active kit, even on a fresh install where nothing was chosen and
	 * Quartermaster merely shows its first preset; so its choice is believed only when {@code quarters}
	 * names a chosen kit too, or when {@code quarters} cannot be read at all. The bank comes from
	 * {@code quarters}, the only place it is kept. Either may be null.
	 */
	public static KitPlan from(JsonObject kitsV1, JsonObject quarters)
	{
		KitPlan old = from(quarters);
		if (kitsV1 == null)
		{
			return old;
		}
		Map<Integer, Long> bank = old == null ? new HashMap<>() : new HashMap<>(old.bank);
		String active = string(kitsV1, "active");
		if (quarters != null && string(quarters, "active") == null)
		{
			// Never chosen: kitsV1's active kit is only what Quartermaster happens to show first.
			active = null;
		}
		JsonElement kits = kitsV1.get("kits");
		if (active == null || kits == null || !kits.isJsonArray())
		{
			return new KitPlan(null, new ArrayList<>(), bank);
		}
		for (JsonElement k : kits.getAsJsonArray())
		{
			if (!k.isJsonObject() || !active.equals(string(k.getAsJsonObject(), "id")))
			{
				continue;
			}
			JsonObject kit = k.getAsJsonObject();
			List<Need> needs = new ArrayList<>();
			JsonElement preset = kit.get("preset");
			boolean isPreset = active.startsWith("preset:")
				|| preset != null && preset.isJsonPrimitive() && "true".equals(preset.getAsString());
			// A preset's amounts are a typical multi-kill trip, not the player's own; only their own kits set targets.
			if (!isPreset && kit.has("needs") && kit.get("needs").isJsonArray())
			{
				for (JsonElement n : kit.getAsJsonArray("needs"))
				{
					Need need = need(n);
					if (need != null)
					{
						needs.add(need);
					}
				}
			}
			String name = string(kit, "name");
			return new KitPlan(name != null ? name : old != null ? old.name : null, needs, bank);
		}
		// The chosen kit is missing from kitsV1, say from a save between the two writes: the older record decides.
		return old != null ? old : new KitPlan(null, new ArrayList<>(), bank);
	}

	/**
	 * Reads Quartermaster's {@code quarters} entry alone. Only a kit the player chose counts: with none chosen Quartermaster
	 * shows its first kit, which says nothing about where the player is going.
	 */
	public static KitPlan from(JsonObject quarters)
	{
		if (quarters == null)
		{
			return null;
		}
		Map<Integer, Long> bank = new HashMap<>();
		JsonObject bankJson = object(quarters, "bank");
		if (bankJson != null)
		{
			for (Map.Entry<String, JsonElement> e : bankJson.entrySet())
			{
				try
				{
					bank.put(Integer.parseInt(e.getKey()), e.getValue().getAsLong());
				}
				catch (RuntimeException ignored)
				{
					// A key or count of an unexpected shape is skipped, not fatal.
				}
			}
		}
		String active = string(quarters, "active");
		String name = null;
		List<Need> needs = new ArrayList<>();
		if (active != null && active.startsWith("preset:"))
		{
			String id = active.substring("preset:".length());
			name = PRESETS.getOrDefault(id, id.isEmpty() ? null : id.substring(0, 1).toUpperCase(Locale.ENGLISH) + id.substring(1));
		}
		else if (active != null && quarters.has("kits") && quarters.get("kits").isJsonArray())
		{
			JsonArray kits = quarters.getAsJsonArray("kits");
			for (JsonElement k : kits)
			{
				if (!k.isJsonObject() || !active.equals(string(k.getAsJsonObject(), "id")))
				{
					continue;
				}
				JsonObject kit = k.getAsJsonObject();
				name = string(kit, "name");
				if (kit.has("needs") && kit.get("needs").isJsonArray())
				{
					for (JsonElement n : kit.getAsJsonArray("needs"))
					{
						Need need = need(n);
						if (need != null)
						{
							needs.add(need);
						}
					}
				}
				break;
			}
		}
		return new KitPlan(name, needs, bank);
	}

	private static Need need(JsonElement n)
	{
		if (!n.isJsonObject())
		{
			return null;
		}
		String supply = string(n.getAsJsonObject(), "supply");
		if (supply == null)
		{
			return null;
		}
		int perTrip;
		try
		{
			perTrip = n.getAsJsonObject().has("perTrip") ? n.getAsJsonObject().get("perTrip").getAsInt() : 0;
		}
		catch (RuntimeException e)
		{
			perTrip = 0;
		}
		int itemId = -1;
		if (supply.startsWith("item:"))
		{
			try
			{
				itemId = Integer.parseInt(supply.substring("item:".length()));
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
		return new Need(supply, perTrip, itemId);
	}

	private static JsonObject object(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static String string(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		try
		{
			return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
