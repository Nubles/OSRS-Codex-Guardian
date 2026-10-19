package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The check before a trip: are you carrying what gets you home from where you are going? It weighs food,
 * a way out, the protection the destination is known for (dragonfire, venom, smoke, a burning floor), the
 * survival supplies your Quartermaster kit packs, and a last resort. When the bank is known (seen this session,
 * or shared by Codex Bank Steward) a missing teleport, protection or kit item says whether one waits there, so
 * the check is a reminder to grab it rather than a shopping trip. It is general preparation, never advice about how a fight plays out.
 */
public final class TripCheck
{
	/** One line of the check. */
	public static final class Line
	{
		private final Peril.Tone tone;
		private final String text;
		private final boolean inBank;

		Line(Peril.Tone tone, String text)
		{
			this(tone, text, false);
		}

		Line(Peril.Tone tone, String text, boolean inBank)
		{
			this.tone = tone;
			this.text = text;
			this.inBank = inBank;
		}

		/** True when the line points at something missing from the pack that waits in the bank. */
		public boolean pointsAtBank()
		{
			return inBank;
		}

		public Peril.Tone getTone()
		{
			return tone;
		}

		public String getText()
		{
			return text;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Line && ((Line) o).tone == tone && ((Line) o).text.equals(text);
		}

		@Override
		public int hashCode()
		{
			return tone.hashCode() * 31 + text.hashCode();
		}
	}

	/** A kind of protection a place is known to need, and the carried or worn items that give it. */
	private static final class Ward
	{
		final String label;
		final String missing;
		final String covers;
		final String[] items;

		Ward(String label, String missing, String covers, String... items)
		{
			this.label = label;
			this.missing = missing;
			this.covers = covers;
			this.items = items;
		}
	}

	private static final Ward DRAGONFIRE = new Ward("Dragonfire", "No dragonfire protection", "ANTIFIRE", "antifire",
		"anti-dragon shield", "dragonfire shield", "dragonfire ward");
	private static final Ward VENOM = new Ward("Venom", "No anti-venom", "ANTIVENOM", "anti-venom", "antidote++", "serpentine helm",
		"tanzanite helm", "magma helm");
	private static final Ward WYVERN = new Ward("Icy breath", "No wyvern shield", null, "elemental shield", "mind shield",
		"dragonfire shield", "dragonfire ward", "ancient wyvern shield");
	private static final Ward SMOKE = new Ward("Smoke", "No face mask", null, "facemask", "face mask", "gas mask", "slayer helmet");
	private static final Ward FLOOR = new Ward("Burning floor", "No boots of stone", null, "boots of stone", "boots of brimstone",
		"granite boots");
	private static final Ward HORRORS = new Ward("Cave horrors", "No witchwood icon", null, "witchwood icon");

	private static final Map<String, List<Ward>> WARDS = new HashMap<>();
	private static final Pattern DOSES = Pattern.compile("^(.*?)\\s*\\((\\d+)\\)$");

	static
	{
		WARDS.put("Vorkath", Arrays.asList(DRAGONFIRE, VENOM));
		WARDS.put("Zulrah", Collections.singletonList(VENOM));
		WARDS.put("Crandor Dungeon", Collections.singletonList(DRAGONFIRE));
		WARDS.put("Ancient Cavern", Collections.singletonList(DRAGONFIRE));
		WARDS.put("Asgarnian Ice Caves", Collections.singletonList(WYVERN));
		WARDS.put("Wyvern Cave", Collections.singletonList(WYVERN));
		WARDS.put("Smoke Dungeon", Collections.singletonList(SMOKE));
		WARDS.put("Karuulm Slayer Dungeon", Collections.singletonList(FLOOR));
		WARDS.put("Alchemical Hydra", Collections.singletonList(FLOOR));
		WARDS.put("Mos Le'Harmless Caves", Collections.singletonList(HORRORS));
	}

	private final String destination;
	private final String kit;
	private final List<Line> lines;

	private TripCheck(String destination, String kit, List<Line> lines)
	{
		this.destination = destination;
		this.kit = kit;
		this.lines = Collections.unmodifiableList(lines);
	}

	/** Where the trip is headed, or null when Guardian cannot tell. */
	public String getDestination()
	{
		return destination;
	}

	/** The Quartermaster kit the check follows, or null. */
	public String getKit()
	{
		return kit;
	}

	public List<Line> getLines()
	{
		return lines;
	}

	/**
	 * The same check with what the account has lived through at the destination added as notes, such as "Vorkath
	 * hit you for 58 here". Notes never make a trip look unready; they are there to be remembered at the bank.
	 */
	public TripCheck withMemory(List<String> memory)
	{
		if (memory == null || memory.isEmpty())
		{
			return this;
		}
		List<Line> more = new ArrayList<>(lines);
		for (String text : memory)
		{
			more.add(new Line(Peril.Tone.NOTE, text));
		}
		return new TripCheck(destination, kit, more);
	}

	/** The worst lines first, at most {@code n}: what a short plaque has room for. */
	public List<Line> worst(int n)
	{
		List<Line> sorted = new ArrayList<>(lines);
		sorted.sort(Comparator.comparingInt(l -> -severity(l.getTone())));
		return sorted.size() > n ? new ArrayList<>(sorted.subList(0, n)) : sorted;
	}

	/** True when a missing item on the check waits in the bank: worth a reminder even without a kit. */
	public boolean pointsAtBank()
	{
		for (Line l : lines)
		{
			if (l.pointsAtBank())
			{
				return true;
			}
		}
		return false;
	}

	/** True when nothing on the check is a problem. */
	public boolean isClear()
	{
		for (Line l : lines)
		{
			if (l.getTone() == Peril.Tone.BAD || l.getTone() == Peril.Tone.WARN)
			{
				return false;
			}
		}
		return true;
	}

	private static int severity(Peril.Tone tone)
	{
		switch (tone)
		{
			case BAD:
				return 3;
			case WARN:
				return 2;
			case NOTE:
				return 1;
			default:
				return 0;
		}
	}

	/**
	 * Whether a pack looks like one for a fight: food, prayer or a cure. Closing the bank with one of these is
	 * worth a briefing; closing it with a skilling inventory is not. Readiness is read when the pack changes,
	 * which the bank's own withdrawals do, so it is current when the bank closes.
	 */
	public static boolean packedForTrip(Readiness r)
	{
		return r.getFoodCount() > 0 || r.getPrayerDoses() > 0 || r.getAntivenomDoses() > 0 || r.getAntipoisonDoses() > 0;
	}

	/** The place a kit is named for, such as "Vorkath" for a Vorkath kit or "Barrows crypts" for "Barrows". */
	public static Zone destinationFor(String kitName)
	{
		return kitName == null ? null : Zones.byName(kitName);
	}

	/**
	 * Builds the check.
	 *
	 * @param destination where the trip goes, or null
	 * @param plan        Quartermaster's chosen kit and bank, or null without Quartermaster
	 * @param itemNames   names for the kit's single items, by item id, as the client reports them
	 * @param inventory   what is carried
	 * @param worn        what is worn
	 * @param readiness   Guardian's read of the same pack
	 * @param bankGraces  last resorts seen in the bank by name ("Ring of life" to 2), or empty
	 * @param foodReserve the hitpoints of food the player wants for danger
	 */
	public static TripCheck build(Zone destination, KitPlan plan, Map<Integer, String> itemNames, List<Supplies.Item> inventory,
		List<Supplies.Item> worn, Readiness readiness, Map<String, Integer> bankGraces, int foodReserve)
	{
		return build(destination, plan, itemNames, inventory, worn, readiness, bankGraces, null, foodReserve);
	}

	/**
	 * Builds the check, also knowing the whole bank.
	 *
	 * @param bank everything in the bank by name, or null when the bank is not known
	 */
	public static TripCheck build(Zone destination, KitPlan plan, Map<Integer, String> itemNames, List<Supplies.Item> inventory,
		List<Supplies.Item> worn, Readiness readiness, Map<String, Integer> bankGraces, List<Supplies.Item> bank, int foodReserve)
	{
		List<Line> lines = new ArrayList<>();
		List<Supplies.Item> all = new ArrayList<>(inventory);
		all.addAll(worn);
		List<KitPlan.Need> needs = plan == null ? Collections.emptyList() : plan.getNeeds();

		int foodTarget = foodReserve;
		for (KitPlan.Need n : needs)
		{
			if ("FOOD".equals(n.getSupply()))
			{
				foodTarget = Math.max(foodTarget, n.getPerTrip());
			}
		}
		int healing = readiness.getFoodHealing();
		if (readiness.getFoodCount() == 0)
		{
			lines.add(new Line(Peril.Tone.BAD, "No food packed"));
		}
		else
		{
			lines.add(new Line(healing >= foodTarget ? Peril.Tone.GOOD : Peril.Tone.WARN,
				healing >= foodTarget ? "Food: " + healing + " hp" : "Food: " + healing + " of " + foodTarget + " hp"));
		}

		if (readiness.hasEscape())
		{
			lines.add(new Line(Peril.Tone.GOOD, "Way out: " + Advice.shortName(readiness.getBestEscape())));
		}
		else
		{
			String banked = bank == null ? null : Supplies.assess(bank, Collections.emptyList(), 99).getBestEscape();
			if (banked != null)
			{
				lines.add(new Line(Peril.Tone.BAD, "No teleport: " + Advice.shortName(banked) + " in the bank", true));
			}
			else if (readiness.getLawRunes() > 0)
			{
				lines.add(new Line(Peril.Tone.BAD, "Way out: law runes only"));
			}
			else
			{
				lines.add(new Line(Peril.Tone.BAD, bank == null ? "No teleport out" : "No teleport out, none banked"));
			}
		}

		// A protection line already speaks for the kit's matching potion: missing, it says so; carried, the kit
		// line only adds something when it is short of the doses the kit plans.
		Map<String, Boolean> covered = new HashMap<>();
		if (destination != null)
		{
			for (Ward ward : WARDS.getOrDefault(destination.getName(), Collections.emptyList()))
			{
				String found = find(all, ward.items);
				int banked = found != null || bank == null ? 0 : count(bank, ward.items);
				lines.add(found != null ? new Line(Peril.Tone.GOOD, ward.label + ": " + found)
					: banked > 0 ? new Line(Peril.Tone.BAD, ward.missing + ": " + banked + " in the bank", true)
					: new Line(Peril.Tone.BAD, ward.missing));
				if (ward.covers != null)
				{
					covered.put(ward.covers, found != null);
				}
			}
		}

		for (KitPlan.Need n : needs)
		{
			Line line = kitLine(n, all, readiness, itemNames, bank);
			Boolean wardMet = covered.get(n.getSupply().replace("SUPER_", ""));
			if (line == null || lines.contains(line) || wardMet != null && (!wardMet || line.getTone() != Peril.Tone.WARN))
			{
				continue;
			}
			lines.add(line);
		}

		List<String> graces = readiness.getSavingGraces();
		if (!graces.isEmpty())
		{
			lines.add(new Line(Peril.Tone.GOOD, "Last resort: " + graces.get(0)));
		}
		else
		{
			String banked = null;
			int count = 0;
			for (Map.Entry<String, Integer> e : bankGraces.entrySet())
			{
				if (e.getValue() != null && e.getValue() > 0)
				{
					banked = e.getKey();
					count = e.getValue();
					break;
				}
			}
			lines.add(banked != null ? new Line(Peril.Tone.WARN, banked + ": " + count + " in the bank", true)
				: new Line(Peril.Tone.NOTE, "No ring of life worn"));
		}
		return new TripCheck(destination == null ? null : destination.getName(), plan == null ? null : plan.getName(), lines);
	}

	/** A line for one survival supply the kit packs; boosts and ammunition are Quartermaster's business. */
	private static Line kitLine(KitPlan.Need n, List<Supplies.Item> all, Readiness readiness, Map<Integer, String> itemNames,
		List<Supplies.Item> bank)
	{
		String supply = n.getSupply();
		int want = n.getPerTrip();
		switch (supply)
		{
			case "PRAYER":
				return dosesLine("Prayer", readiness.getPrayerDoses(), want);
			case "ANTIVENOM":
				return dosesLine("Anti-venom", readiness.getAntivenomDoses(), want);
			case "BREW":
				return dosesLine("Brews", doses(all, "saradomin brew"), want);
			case "SUPER_ANTIFIRE":
			case "ANTIFIRE":
				return dosesLine("Antifire", doses(all, "antifire"), want);
			default:
				break;
		}
		if (n.getItemId() < 0 || want <= 0)
		{
			return null;
		}
		String name = itemNames.get(n.getItemId());
		if (name == null)
		{
			return null;
		}
		int have = 0;
		for (Supplies.Item item : all)
		{
			if (item.name != null && item.name.equalsIgnoreCase(name))
			{
				have += item.quantity;
			}
		}
		if (have > 0)
		{
			return new Line(Peril.Tone.GOOD, name + (have > 1 ? " x" + have : ""));
		}
		int banked = 0;
		for (Supplies.Item item : bank == null ? Collections.<Supplies.Item>emptyList() : bank)
		{
			if (item.name != null && item.name.equalsIgnoreCase(name))
			{
				banked += item.quantity;
			}
		}
		return banked > 0 ? new Line(Peril.Tone.WARN, name + ": " + banked + " in the bank", true)
			: new Line(Peril.Tone.WARN, name + ": not packed");
	}

	private static Line dosesLine(String label, int have, int want)
	{
		if (want <= 0)
		{
			return null;
		}
		if (have <= 0)
		{
			return new Line(Peril.Tone.BAD, label + ": none packed");
		}
		return have >= want ? new Line(Peril.Tone.GOOD, label + ": " + have + " doses")
			: new Line(Peril.Tone.WARN, label + ": " + have + " of " + want + " doses");
	}

	/** Doses carried of every potion whose name contains {@code part}, such as "antifire". */
	private static int doses(List<Supplies.Item> all, String part)
	{
		int total = 0;
		for (Supplies.Item item : all)
		{
			Matcher m = DOSES.matcher(lower(item.name));
			if (m.matches() && m.group(1).contains(part))
			{
				total += Integer.parseInt(m.group(2)) * item.quantity;
			}
		}
		return total;
	}

	/** How many of the items whose names contain one of {@code parts} there are, counting each stack's size. */
	private static int count(List<Supplies.Item> items, String[] parts)
	{
		long total = 0;
		for (Supplies.Item item : items)
		{
			String name = lower(item.name);
			for (String part : parts)
			{
				if (name.contains(part))
				{
					total += item.quantity;
					break;
				}
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	/** The first carried or worn item whose name contains one of {@code parts}, as the game names it. */
	private static String find(List<Supplies.Item> all, String[] parts)
	{
		for (Supplies.Item item : all)
		{
			String name = lower(item.name);
			for (String part : parts)
			{
				if (name.contains(part))
				{
					return item.name;
				}
			}
		}
		return null;
	}

	private static String lower(String s)
	{
		return s == null ? "" : s.toLowerCase(Locale.ENGLISH);
	}
}
