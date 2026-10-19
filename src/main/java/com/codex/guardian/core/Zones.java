package com.codex.guardian.core;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The atlas of dangerous places. Regions come from the same map squares RuneLite's own
 * Discord plugin uses, so they match what the client reports. The Wilderness is worked
 * out from coordinates instead, because its danger grows with every level.
 */
public final class Zones
{
	private static final String BOSS = "A boss lair. Bring a full inventory of food and an escape you can use the moment it turns.";
	private static final String RAID = "Raid deaths are unforgiving on one life. Know your exit and keep an escape in reach.";
	private static final String SLAYER = "Aggressive monsters can stack up here. Watch your hitpoints and keep an escape ready.";
	private static final String QUIET = "Monsters here can still hit hard if you are underlevelled. Keep some food on you.";
	private static final String SAFE = "Deaths here are normally safe, but check your mode's rules before you rely on that.";
	private static final String SKILLING = "A skilling boss. Its damage adds up while you work, so keep food and leave early.";

	private static final Map<Integer, Zone> BY_REGION = new HashMap<>();
	private static final Pattern LEVEL_TEXT = Pattern.compile("Level:\\s*(\\d{1,2})");

	static
	{
		// Boss lairs
		boss("Abyssal Sire", Risk.HIGH, 11851, 11850, 12363, 12362);
		boss("Araxxor", Risk.HIGH, 14489);
		boss("Cerberus", Risk.HIGH, 4883, 5140, 5395);
		boss("Commander Zilyana", Risk.HIGH, 11602);
		boss("Corporeal Beast", Risk.HIGH, 11842, 11844);
		boss("Dagannoth Kings", Risk.HIGH, 11588, 11589);
		boss("Duke Sucellus", Risk.HIGH, 12132);
		boss("General Graardor", Risk.HIGH, 11347);
		boss("Giant Mole", Risk.MEDIUM, 6993, 6992);
		boss("Grotesque Guardians", Risk.HIGH, 6727);
		boss("Hespori", Risk.MEDIUM, 5021);
		boss("Alchemical Hydra", Risk.HIGH, 5536);
		boss("Kalphite Queen", Risk.HIGH, 13972);
		boss("Kraken", Risk.MEDIUM, 9116);
		boss("Kree'arra", Risk.HIGH, 11346);
		boss("K'ril Tsutsaroth", Risk.HIGH, 11603);
		boss("Nex", Risk.EXTREME, 11601);
		boss("The Nightmare", Risk.HIGH, 15515);
		boss("Phantom Muspah", Risk.HIGH, 11330);
		boss("Sarachnis", Risk.MEDIUM, 7322);
		boss("Skotizo", Risk.HIGH, 9048);
		boss("Thermonuclear smoke devil", Risk.MEDIUM, 9363, 9619);
		boss("The Leviathan", Risk.HIGH, 8291);
		boss("The Royal Titans", Risk.HIGH, 11669);
		boss("The Whisperer", Risk.HIGH, 10595);
		boss("Vardorvis", Risk.HIGH, 4405);
		boss("Vorkath", Risk.HIGH, 9023);
		boss("Yama", Risk.EXTREME, 6045);
		boss("Zulrah", Risk.HIGH, 9007);
		put(new Zone("Wintertodt", Zone.Kind.BOSS, Risk.MEDIUM, SKILLING), 6462);
		put(new Zone("Zalcano", Zone.Kind.BOSS, Risk.MEDIUM, SKILLING), 12126);
		put(new Zone("Tempoross", Zone.Kind.BOSS, Risk.LOW, SKILLING), 12076);

		dungeon("Barrows crypts", Risk.HIGH, "Six brothers, one after another, and Ahrim and Karil hit through armour. "
			+ "Bring prayer, food and a way out.", 14231);
		dungeon("God Wars Dungeon", Risk.HIGH, "Aggressive followers of every god roam here. Wear the right items and keep food handy.",
			11578);

		// Raids
		raid("Chambers of Xeric", 12889, 13136, 13137, 13138, 13139, 13140, 13141, 13145, 13393, 13394, 13395, 13396, 13397, 13401);
		raid("Theatre of Blood", 12611, 12612, 12613, 12867, 12869, 13122, 13123, 13125, 13379);
		raid("Tombs of Amascut", 14160, 14162, 14164, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700, 13454, 14672);

		// Dungeons where one-life accounts most often come unstuck
		dungeon("Abyss", Risk.HIGH, "Abyssal monsters crowd you and you are skulled. Get in, craft, and get out.", 12107);
		dungeon("Ancient Cavern", Risk.HIGH, "Brutal and mithril dragons hit through dragonfire protection. Come prepared.", 6483, 6995);
		dungeon("Lizardman Caves", Risk.HIGH, "Shamans jump and spawn exploding spawn in groups. Watch your hitpoints closely.", 5275);
		dungeon("Crash Site Cavern", Risk.HIGH, "Demonic gorillas switch styles and hit hard. Keep food and an escape ready.", 8280, 8536);
		dungeon("Waterbirth Dungeon", Risk.HIGH, "Multi-combat dagannoths swarm the lower levels. Do not wander in unprepared.", 9886, 10142, 7492, 7748);
		dungeon("Catacombs of Kourend", Risk.MEDIUM, SLAYER, 6557, 6556, 6813, 6812);
		dungeon("Karuulm Slayer Dungeon", Risk.MEDIUM, "The floor burns without boots of stone or better. Wyrms and drakes hit hard.",
			5280, 5279, 5023, 5535, 5022, 4766, 4510, 4511, 4767, 4768, 4512);
		dungeon("Stronghold Slayer Cave", Risk.MEDIUM, SLAYER, 9624, 9625, 9880, 9881);
		dungeon("Taverley Dungeon", Risk.MEDIUM, SLAYER, 11416, 11417, 11671, 11672, 11673, 11928, 11929);
		dungeon("Brimhaven Dungeon", Risk.MEDIUM, SLAYER, 10901, 10900, 10899, 10645, 10644, 10643);
		dungeon("Fremennik Slayer Dungeon", Risk.MEDIUM, SLAYER, 10907, 10908, 11164);
		dungeon("Asgarnian Ice Caves", Risk.MEDIUM, "Skeletal wyverns deeper in breathe ice. Bring the right shield or stay out.", 11925, 12181);
		dungeon("Wyvern Cave", Risk.MEDIUM, "Fossil wyverns breathe through weak shields. Bring the right protection.", 14495, 14496);
		dungeon("Smoke Dungeon", Risk.MEDIUM, "Without a face mask or slayer helmet the smoke damages you constantly.", 12946, 13202);
		dungeon("Iorwerth Dungeon", Risk.MEDIUM, SLAYER, 12737, 12738, 12993, 12994);
		dungeon("Kruk's Dungeon", Risk.MEDIUM, SLAYER, 9358, 9359, 9360, 9615, 9616, 9871, 10125, 10126, 10127, 10128,
			10381, 10382, 10383, 10384, 10637, 10638, 10639, 10640);
		dungeon("Mos Le'Harmless Caves", Risk.MEDIUM, "Cave horrors hit hard without a witchwood icon. Bring one.", 14994, 14995, 15251);
		dungeon("Chasm of Fire", Risk.MEDIUM, SLAYER, 5789);
		dungeon("Forthos Dungeon", Risk.MEDIUM, SLAYER, 7323);
		dungeon("Crandor Dungeon", Risk.MEDIUM, "A green dragon lair. Dragonfire needs an anti-dragon shield or potion.", 11414);
		dungeon("Experiment Cave", Risk.MEDIUM, QUIET, 14235, 13979);
		dungeon("Underground Pass", Risk.MEDIUM, "Traps and swarms fill the pass. Move carefully and eat early.", 9369, 9370);
		dungeon("Shade Catacombs", Risk.MEDIUM, QUIET, 13975);
		dungeon("Meiyerditch Laboratories", Risk.MEDIUM, QUIET, 14232, 14233, 14487, 14488);
		dungeon("Viyeldi Caves", Risk.MEDIUM, QUIET, 9545, 11153);
		dungeon("Edgeville Dungeon", Risk.MEDIUM, "The north end runs into the Wilderness. Watch where you walk.", 12441, 12442, 12443, 12698);
		dungeon("Lizardman Temple", Risk.MEDIUM, SLAYER, 5277);
		dungeon("Slayer Tower", Risk.MEDIUM, SLAYER, 13623, 13723);

		// Minigames where a death is normally safe
		safe("Fight Caves", 9551);
		safe("The Inferno", 9043);
		safe("Fortis Colosseum", 7216);
		safe("The Gauntlet", 12127, 7512);
		safe("Corrupted Gauntlet", 7768);
		safe("Pest Control", 10536);
		safe("Castle Wars", 9520, 9620);
		safe("Soul Wars", 8493, 8748, 8749, 9005);
		safe("Barbarian Assault", 7508, 7509);
		safe("Nightmare Zone", 9033);
		safe("Fight Pits", 9552);
		safe("Emir's Arena", 13362, 13363);
		safe("Trouble Brewing", 15150);
		safe("Last Man Standing", 13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);
	}

	private Zones()
	{
	}

	/** The named zone for a map region, or null when it is not one Guardian knows. */
	public static Zone forRegion(int regionId)
	{
		return BY_REGION.get(regionId);
	}

	/**
	 * The known place a name refers to, matched without case: an exact name first, then the longest place
	 * name that contains it or is contained in it ("Barrows" finds "Barrows crypts", "Vorkath trip" finds
	 * "Vorkath"). A partial match needs four letters or more, so a stray short word cannot claim a place.
	 */
	public static Zone byName(String name)
	{
		if (name == null || name.trim().length() < 3)
		{
			return null;
		}
		String wanted = name.trim().toLowerCase(java.util.Locale.ENGLISH);
		Zone partial = null;
		for (Zone zone : BY_REGION.values())
		{
			String have = zone.getName().toLowerCase(java.util.Locale.ENGLISH);
			if (have.equals(wanted))
			{
				return zone;
			}
			boolean overlaps = wanted.length() >= 4 && have.length() >= 4 && (have.contains(wanted) || wanted.contains(have));
			if (overlaps && (partial == null || zone.getName().length() > partial.getName().length()))
			{
				partial = zone;
			}
		}
		return partial;
	}

	/**
	 * The Wilderness level for a world coordinate, or 0 outside it. Surface levels start at the
	 * ditch and rise one every eight tiles north; the dungeons below mirror the surface.
	 */
	public static int wildernessLevel(int x, int y)
	{
		if (x >= 2944 && x <= 3391 && y >= 3520 && y <= 3967)
		{
			return (y - 3520) / 8 + 1;
		}
		// Underground reaches further east than the surface: the Wilderness Slayer Cave runs to about x 3460,
		// and its northern end is above level 30.
		if (x >= 2944 && x <= 3460 && y >= 9920 && y <= 10367)
		{
			return (y - 9920) / 8 + 1;
		}
		return 0;
	}

	/**
	 * Tiles to walk due south from a Wilderness coordinate until the level is {@code level} or lower, where 0
	 * means out of the Wilderness altogether. 0 when already there; -1 when the coordinate is not on the
	 * Wilderness map, so no distance can be promised.
	 *
	 * <p>Underground (the Revenant Caves, the Wilderness Slayer Cave and the other dungeons) a level line can
	 * still be walked to, but there is no ditch to the south: the way out is a ladder or a cave mouth, so the
	 * distance out of the Wilderness is -1 there.
	 */
	public static int tilesSouthTo(int x, int y, int level)
	{
		if (wildernessLevel(x, y) <= 0 || level < 0 || level == 0 && y >= 9920)
		{
			return -1;
		}
		// Level n covers eight rows of tiles from the ditch; the last row of level n is base + 8n - 1.
		int base = y >= 9920 ? 9920 : 3520;
		return Math.max(0, y - (base + 8 * level - 1));
	}

	/**
	 * Reads the level from the game's own "Level: 23" Wilderness text, or returns 0 when there is
	 * none. The game's number is the truth; coordinates are only a fallback.
	 */
	public static int parseWildernessLevel(String widgetText)
	{
		if (widgetText == null)
		{
			return 0;
		}
		Matcher m = LEVEL_TEXT.matcher(widgetText);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/** True a few steps south of the Wilderness ditch, so the warning arrives before the first step in. */
	public static boolean nearWilderness(int x, int y)
	{
		return x >= 2944 && x <= 3391 && y >= 3508 && y < 3520;
	}

	public static Zone wilderness(int level)
	{
		Risk risk = level > 30 ? Risk.EXTREME : level > 20 ? Risk.HIGH : Risk.MEDIUM;
		String advice;
		if (level > 30)
		{
			advice = "Level " + level + ". No teleport works up here and other players can attack you. Get down to level 30.";
		}
		else if (level > 20)
		{
			advice = "Level " + level + ". Only a few teleports, like a glory, work above 20. Other players can attack you.";
		}
		else
		{
			advice = "Level " + level + ". Other players can attack you and you lose items on death.";
		}
		return new Zone("Wilderness", Zone.Kind.WILDERNESS, risk, advice);
	}

	public static Zone pvpWorld()
	{
		return new Zone("PvP world", Zone.Kind.PVP_WORLD, Risk.HIGH, "Other players can attack you almost anywhere on this world.");
	}

	private static void boss(String name, Risk risk, int... regions)
	{
		put(new Zone(name, Zone.Kind.BOSS, risk, BOSS), regions);
	}

	private static void raid(String name, int... regions)
	{
		put(new Zone(name, Zone.Kind.RAID, Risk.HIGH, RAID), regions);
	}

	private static void dungeon(String name, Risk risk, String advice, int... regions)
	{
		put(new Zone(name, Zone.Kind.DUNGEON, risk, advice), regions);
	}

	private static void safe(String name, int... regions)
	{
		put(new Zone(name, Zone.Kind.SAFE_MINIGAME, Risk.SAFE, SAFE), regions);
	}

	private static void put(Zone zone, int... regions)
	{
		for (int region : regions)
		{
			BY_REGION.putIfAbsent(region, zone);
		}
	}
}
