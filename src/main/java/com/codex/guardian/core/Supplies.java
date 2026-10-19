package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a pack and a set of worn items by name and works out what would keep you alive:
 * how much food, which way out, and which items step in at the last moment. Names are used
 * rather than item ids so every charge state and variant of an item is recognised.
 */
public final class Supplies
{
	/** One carried item, as the plugin reads it from the client. */
	public static final class Item
	{
		final String name;
		final int quantity;

		public Item(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	private static final Pattern DOSES = Pattern.compile("^(.*?)\\s*\\((\\d+)\\)$");

	/** A trimmed glory reads "Amulet of glory(t4)" and an imbued ring of wealth "Ring of wealth (i5)". */
	private static final Pattern TRIMMED = Pattern.compile("^(amulet of glory|ring of wealth)\\s*\\([ti](\\d+)\\)$");
	/** Jewellery that shows its charges in its name, so one without a number is empty. */
	private static final Set<String> CHARGED = new HashSet<>(Arrays.asList("amulet of glory", "combat bracelet",
		"skills necklace", "ring of wealth", "ring of dueling", "games necklace", "necklace of passage", "digsite pendant",
		"slayer ring", "teleport crystal", "enchanted lyre", "ring of returning"));

	private static final Map<String, Integer> FOOD = new HashMap<>();
	private static final Map<String, Integer> TWO_BITE = new HashMap<>();
	/** Teleports that still work up to level 30 Wilderness; everything else stops at 20. */
	private static final String[] LEVEL_30 = {"amulet of glory", "amulet of eternal glory", "combat bracelet",
		"skills necklace", "ring of wealth", "royal seed pod"};
	/** Charged jewellery and tablets that take you somewhere safe. */
	private static final String[] TELEPORT_JEWELLERY = {"amulet of glory", "amulet of eternal glory", "combat bracelet",
		"skills necklace", "ring of wealth", "ring of dueling", "games necklace", "necklace of passage", "digsite pendant",
		"slayer ring", "royal seed pod", "teleport crystal", "eternal teleport crystal", "enchanted lyre", "xeric's talisman",
		"drakan's medallion", "ectophial", "ring of the elements", "ring of shadows", "pharaoh's sceptre",
		"construct. cape", "max cape", "crafting cape", "achievement diary cape", "ardougne cloak 2", "ardougne cloak 3",
		"ardougne cloak 4", "explorer's ring 2", "explorer's ring 3", "explorer's ring 4", "karamja gloves 3",
		"karamja gloves 4", "desert amulet 2", "desert amulet 3", "desert amulet 4", "quetzal whistle",
		"ring of returning"};
	/** Tablets and scrolls whose destination is itself in the Wilderness: not an escape. */
	private static final String[] INTO_WILDERNESS = {"annakarl", "carrallanger", "carrallangar", "dareeyak", "ghorrock",
		"cemetery", "ice plateau", "wilderness crabs", "revenant cave", "lava maze", "target teleport", "burning amulet"};

	static
	{
		food(1, "anchovies");
		food(3, "shrimps", "cooked chicken", "cooked meat", "cooked rabbit");
		food(4, "sardine", "cake", "2/3 cake", "slice of cake", "chocolate slice");
		food(5, "bread", "herring", "chocolate cake", "2/3 chocolate cake");
		food(6, "mackerel", "cooked slimy eel");
		food(7, "trout", "cod", "baked potato", "cooked chompy");
		food(8, "pike", "roast beast meat", "cooked fishcake");
		food(9, "salmon", "cooked moonlight antelope");
		food(10, "tuna", "spicy stew");
		food(11, "stew", "rainbow fish", "jug of wine", "cave eel");
		food(12, "lobster", "cooked sunlight antelope");
		food(13, "bass");
		food(14, "swordfish", "potato with butter", "chilli potato", "egg potato");
		food(15, "cooked jubbly");
		food(16, "monkfish", "potato with cheese", "cooked dashing kebbit");
		food(18, "cooked karambwan", "blighted karambwan");
		food(19, "curry", "mushroom potato");
		food(20, "shark", "ugthanki kebab");
		food(21, "sea turtle");
		food(22, "manta ray", "blighted manta ray", "tuna potato", "dark crab", "anglerfish", "blighted anglerfish");
		twoBite(6, "meat pie", "redberry pie");
		twoBite(7, "apple pie", "plain pizza", "garden pie");
		twoBite(8, "meat pizza", "fish pie", "admiral pie");
		twoBite(9, "anchovy pizza");
		twoBite(11, "pineapple pizza", "summer pie", "wild pie", "dragonfruit pie");
	}

	private Supplies()
	{
	}

	/** Works out readiness outside the Wilderness. */
	public static Readiness assess(List<Item> inventory, List<Item> worn, int maxHitpoints)
	{
		return assess(inventory, worn, maxHitpoints, false);
	}

	/**
	 * Works out readiness from the inventory and worn items.
	 *
	 * @param maxHitpoints the account's hitpoints level, which sets how much a brew heals
	 * @param inWilderness blighted food and potions can only be used in the Wilderness, so anywhere
	 *                     else they are not counted: a pack of blighted mantas outside it is no food at all
	 */
	public static Readiness assess(List<Item> inventory, List<Item> worn, int maxHitpoints, boolean inWilderness)
	{
		Readiness.Builder b = new Readiness.Builder();
		List<Item> all = new ArrayList<>(inventory);
		all.addAll(worn);
		for (Item item : inventory)
		{
			if (!inWilderness && lower(item.name).startsWith("blighted "))
			{
				continue;
			}
			readFood(b, item, maxHitpoints);
			readPotions(b, item);
		}
		for (Item item : all)
		{
			readEscape(b, item);
		}
		for (Item item : worn)
		{
			String name = lower(item.name);
			if (name.equals("ring of life"))
			{
				b.savingGrace("Ring of life");
			}
			else if (name.equals("phoenix necklace"))
			{
				b.savingGrace("Phoenix necklace");
			}
		}
		for (Item item : inventory)
		{
			if (lower(item.name).equals("escape crystal"))
			{
				b.savingGrace("Escape crystal");
			}
		}
		return b.build();
	}

	/**
	 * Every potion dose carried, whatever the potion: what a replay counts as drinking. Jewellery shows its
	 * charges the same way ("Amulet of glory(4)") and is left out, so a teleport is never counted as a drink.
	 */
	public static int potionDoses(List<Item> inventory)
	{
		int total = 0;
		for (Item item : inventory)
		{
			Matcher m = DOSES.matcher(lower(item.name));
			if (!m.matches())
			{
				continue;
			}
			String base = m.group(1);
			if (CHARGED.contains(base) || base.contains("amulet") || base.contains("ring") || base.contains("necklace")
				|| base.contains("bracelet") || base.contains("pendant") || base.contains("glory") || base.contains("crystal")
				|| base.contains("lyre") || base.contains("talisman"))
			{
				continue;
			}
			total += Integer.parseInt(m.group(2)) * Math.max(1, item.quantity);
		}
		return total;
	}

	private static void readFood(Readiness.Builder b, Item item, int maxHitpoints)
	{
		String name = lower(item.name);
		Integer heal = FOOD.get(name);
		if (heal != null)
		{
			b.food(item.quantity, heal * item.quantity);
			return;
		}
		Integer half = TWO_BITE.get(name);
		if (half != null)
		{
			b.food(item.quantity, half * 2 * item.quantity);
			return;
		}
		Integer halfOf = TWO_BITE.get(stripHalf(name));
		if (halfOf != null && !stripHalf(name).equals(name))
		{
			b.food(item.quantity, halfOf * item.quantity);
			return;
		}
		Matcher m = DOSES.matcher(name);
		if (m.matches())
		{
			String base = m.group(1);
			int doses = Integer.parseInt(m.group(2)) * item.quantity;
			if (base.equals("saradomin brew"))
			{
				b.food(doses, (maxHitpoints * 15 / 100 + 2) * doses);
			}
			else if (base.equals("guthix rest"))
			{
				b.food(doses, 5 * doses);
			}
		}
	}

	private static void readPotions(Readiness.Builder b, Item item)
	{
		Matcher m = DOSES.matcher(lower(item.name));
		if (!m.matches())
		{
			return;
		}
		String base = m.group(1);
		int doses = Integer.parseInt(m.group(2)) * item.quantity;
		if (base.equals("prayer potion") || base.equals("super restore") || base.equals("sanfew serum")
			|| base.equals("blighted super restore") || base.equals("prayer regeneration potion"))
		{
			b.prayerDoses(doses);
		}
		if (base.contains("anti-venom") || base.equals("antidote++"))
		{
			b.antivenomDoses(doses);
		}
		else if (base.contains("antipoison") || base.startsWith("antidote") || base.equals("sanfew serum"))
		{
			b.antipoisonDoses(doses);
		}
	}

	private static void readEscape(Readiness.Builder b, Item item)
	{
		String name = lower(item.name);
		for (String bad : INTO_WILDERNESS)
		{
			if (name.contains(bad))
			{
				return;
			}
		}
		String base = name;
		Integer charges = null;
		Matcher m = DOSES.matcher(name);
		Matcher trimmed = TRIMMED.matcher(name);
		if (m.matches())
		{
			base = m.group(1);
			charges = Integer.parseInt(m.group(2));
		}
		else if (trimmed.matches())
		{
			base = trimmed.group(1);
			charges = Integer.parseInt(trimmed.group(2));
		}
		if (charges != null && charges <= 0)
		{
			return;
		}
		if (base.endsWith("(t)"))
		{
			base = base.substring(0, base.length() - 3).trim();
		}
		boolean eternal = base.endsWith(" (eternal)");
		if (eternal)
		{
			base = base.substring(0, base.length() - " (eternal)".length());
		}
		for (String jewel : TELEPORT_JEWELLERY)
		{
			if (base.equals(jewel))
			{
				// An empty glory or ring of wealth looks the same as a charged one minus the number.
				if (charges == null && !eternal && CHARGED.contains(jewel))
				{
					return;
				}
				b.escape(item.name, maxWilderness(base));
				return;
			}
		}
		if (name.endsWith(" teleport") || name.startsWith("teleport to ") || name.endsWith(" teleport scroll"))
		{
			b.escape(item.name, 20);
			return;
		}
		if (name.equals("law rune"))
		{
			b.spellRunes(item.quantity);
		}
	}

	private static int maxWilderness(String base)
	{
		for (String jewel : LEVEL_30)
		{
			if (base.startsWith(jewel))
			{
				return 30;
			}
		}
		return 20;
	}

	private static void food(int heal, String... names)
	{
		for (String name : names)
		{
			FOOD.put(name, heal);
		}
	}

	private static void twoBite(int perBite, String... names)
	{
		for (String name : names)
		{
			TWO_BITE.put(name, perBite);
		}
	}

	/** "Half a meat pie" and "1/2 meat pizza" become the whole item's name. */
	private static String stripHalf(String name)
	{
		for (String prefix : new String[]{"half an ", "half a ", "1/2 "})
		{
			if (name.startsWith(prefix))
			{
				return name.substring(prefix.length());
			}
		}
		return name;
	}

	private static String lower(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ENGLISH);
	}
}
