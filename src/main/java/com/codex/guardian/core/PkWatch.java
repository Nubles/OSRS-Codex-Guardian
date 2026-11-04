package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * The Wilderness alarm, built for one life: it only counts players who can attack you where you both stand
 * right now, weighs how much each one should worry you, and says the plain next move.
 *
 * <p>Who can attack whom (oldschool.runescape.wiki/w/Wilderness): players "within combat level range of each
 * other can attack each other in the Wilderness", the range being "the combat level ... adding or subtracting
 * the current Wilderness level". The game checks it from both sides, so a player standing shallower than you
 * has to step deeper before the gap is small enough: the bracket that counts is the smaller of the two levels.
 * On a PvP world the Wilderness level counts 15 higher (same page).
 *
 * <p>It never acts for you. It reads who is near and draws and notifies; what to do stays your call.
 */
public final class PkWatch
{
	/** On a PvP world "level 20 Wilderness ... acts like level 35" (oldschool.runescape.wiki/w/Wilderness). */
	public static final int PVP_WORLD_BONUS = 15;

	/**
	 * Weapons the wiki names as PKing knock-out or finishing specials, by item id (RuneLite gameval ItemID).
	 * Dragon dagger and its poisoned versions: "a staple 'knock-out' weapon in PvP combat". Dragon claws: "a
	 * popular weapon for PKing". Armadyl godsword: "most commonly used as a 'knock-out' weapon". Granite maul and
	 * its ornate-handle and later versions: "a popular choice for player killing". Voidwaker: "a consistent
	 * finishing blow". Ancient godsword: its blood sacrifice finishes a frozen target.
	 */
	static final Set<Integer> KO_WEAPONS = new HashSet<>(Arrays.asList(
		ItemID.DRAGON_DAGGER, ItemID.DRAGON_DAGGER_P, ItemID.DRAGON_DAGGER_P_, ItemID.DRAGON_DAGGER_P__,
		ItemID.DRAGON_CLAWS,
		ItemID.AGS,
		ItemID.GRANITE_MAUL, ItemID.GRANITE_MAUL_PRETTY, ItemID.GRANITE_MAUL_PLUS, ItemID.GRANITE_MAUL_PRETTY_PLUS,
		ItemID.VOIDWAKER,
		ItemID.ANCIENT_GODSWORD));

	/** How long a step toward you keeps a player marked as closing in, so one still tick does not drop it. */
	static final int CLOSING_MEMORY_TICKS = 3;

	public enum Strength
	{
		/** Nobody here can attack you. */
		NONE,
		/** Someone can attack you, but nothing says they mean to yet. */
		WATCH,
		/** Skulled, armed to kill, or walking at you: be ready. */
		WARN,
		/** Coming for you, or more than one in multi: move now. */
		DANGER;

		public boolean atLeast(Strength other)
		{
			return compareTo(other) >= 0;
		}
	}

	/** A player in view this tick, as the plugin read it. */
	public static final class Sighting
	{
		final String name;
		final int combatLevel;
		final int wildernessLevel;
		final int distance;
		final boolean skulled;
		final int weaponId;
		final boolean trusted;
		final boolean targetingYou;

		/**
		 * @param wildernessLevel the level where they stand (0 outside the Wilderness)
		 * @param distance tiles from you
		 * @param weaponId the item in their weapon slot, or -1
		 * @param trusted a friend, clan mate or friends chat member, when the setting trusts them
		 * @param targetingYou they are interacting with you: attacking or walking to attack
		 */
		public Sighting(String name, int combatLevel, int wildernessLevel, int distance, boolean skulled, int weaponId,
			boolean trusted, boolean targetingYou)
		{
			this.name = name;
			this.combatLevel = combatLevel;
			this.wildernessLevel = wildernessLevel;
			this.distance = distance;
			this.skulled = skulled;
			this.weaponId = weaponId;
			this.trusted = trusted;
			this.targetingYou = targetingYou;
		}
	}

	/** A player who can attack you, and why they worry Guardian. */
	public static final class Foe
	{
		private final String name;
		private final int combatLevel;
		private final int distance;
		private final boolean skulled;
		private final boolean armed;
		private final boolean closing;
		private final boolean targetingYou;

		Foe(Sighting s, boolean closing)
		{
			name = s.name;
			combatLevel = s.combatLevel;
			distance = s.distance;
			skulled = s.skulled;
			armed = KO_WEAPONS.contains(s.weaponId);
			this.closing = closing;
			targetingYou = s.targetingYou;
		}

		public String getName()
		{
			return name;
		}

		public int getCombatLevel()
		{
			return combatLevel;
		}

		public int getDistance()
		{
			return distance;
		}

		public boolean isSkulled()
		{
			return skulled;
		}

		/** Wielding a weapon PKers use to knock you out (see {@link #KO_WEAPONS}). */
		public boolean isArmed()
		{
			return armed;
		}

		public boolean isClosing()
		{
			return closing;
		}

		public boolean isTargetingYou()
		{
			return targetingYou;
		}

		/** How much this player should worry you: a step for each sign they mean harm. */
		int menace()
		{
			return (targetingYou ? 3 : 0) + (skulled ? 1 : 0) + (armed ? 1 : 0) + (closing ? 1 : 0);
		}
	}

	/** What the alarm says this tick. */
	public static final class Alarm
	{
		static final Alarm QUIET = new Alarm(Strength.NONE, Collections.<Foe>emptyList(), false, null);

		private final Strength strength;
		private final List<Foe> foes;
		private final boolean multi;
		private final String move;

		Alarm(Strength strength, List<Foe> foes, boolean multi, String move)
		{
			this.strength = strength;
			this.foes = Collections.unmodifiableList(foes);
			this.multi = multi;
			this.move = move;
		}

		public Strength getStrength()
		{
			return strength;
		}

		public boolean isActive()
		{
			return strength != Strength.NONE;
		}

		/** Players who can attack you, the most worrying first. */
		public List<Foe> getFoes()
		{
			return foes;
		}

		/** The most worrying player, or null. */
		public Foe getLead()
		{
			return foes.isEmpty() ? null : foes.get(0);
		}

		public boolean isMulti()
		{
			return multi;
		}

		/** The plain next move, or null when quiet. */
		public String getMove()
		{
			return move;
		}

		/** "PKer in range" or "3 PKers in range". */
		public String headline()
		{
			if (foes.isEmpty())
			{
				return "";
			}
			Foe lead = foes.get(0);
			if (lead.isTargetingYou())
			{
				return foes.size() == 1 ? "PKer on you" : foes.size() + " PKers, one on you";
			}
			return foes.size() == 1 ? "PKer in range" : foes.size() + " PKers in range";
		}

		/** "Lvl 98, 6 tiles, skulled, closing" for the lead player. */
		public String detail()
		{
			Foe lead = getLead();
			if (lead == null)
			{
				return "";
			}
			StringBuilder b = new StringBuilder("Lvl ").append(lead.combatLevel).append(", ")
				.append(lead.distance).append(lead.distance == 1 ? " tile" : " tiles");
			if (lead.skulled)
			{
				b.append(", skulled");
			}
			if (lead.armed)
			{
				b.append(", KO weapon");
			}
			if (lead.closing && !lead.targetingYou)
			{
				b.append(", closing");
			}
			if (multi)
			{
				b.append(", multi");
			}
			return b.toString();
		}
	}

	/** Where you stand and what you carry, for the next move. */
	public static final class Footing
	{
		final int wildernessLevel;
		final boolean teleblocked;
		final String escape;
		final int escapeReach;
		final int tilesTo20;
		final int tilesTo30;
		final int tilesToDitch;

		/**
		 * @param escape your best teleport's short name, or null without one
		 * @param escapeReach the deepest level it works at, or -1
		 * @param tilesTo20 tiles south to the level 20 line (0 at or below it, -1 unknown)
		 * @param tilesTo30 tiles south to the level 30 line (0 at or below it, -1 unknown)
		 * @param tilesToDitch tiles south out of the Wilderness (-1 unknown, as underground)
		 */
		public Footing(int wildernessLevel, boolean teleblocked, String escape, int escapeReach, int tilesTo20,
			int tilesTo30, int tilesToDitch)
		{
			this.wildernessLevel = wildernessLevel;
			this.teleblocked = teleblocked;
			this.escape = escape;
			this.escapeReach = escape == null ? -1 : escapeReach;
			this.tilesTo20 = tilesTo20;
			this.tilesTo30 = tilesTo30;
			this.tilesToDitch = tilesToDitch;
		}
	}

	private final Map<String, Integer> lastDistance = new HashMap<>();
	private final Map<String, Integer> closingFor = new HashMap<>();
	private Alarm last = Alarm.QUIET;

	/**
	 * Whether a player can attack you, and you them, where you both stand now: both in the Wilderness and the
	 * combat level gap no wider than the shallower of the two Wilderness levels.
	 */
	public static boolean canAttack(int yourCombat, int yourWilderness, int theirCombat, int theirWilderness)
	{
		if (yourWilderness <= 0 || theirWilderness <= 0 || yourCombat <= 0 || theirCombat <= 0)
		{
			return false;
		}
		return Math.abs(yourCombat - theirCombat) <= Math.min(yourWilderness, theirWilderness);
	}

	/** True for the Wilderness marker's words for a spot where nobody can attack you, with no level on it. */
	public static boolean safeMarker(String text)
	{
		if (text == null || Zones.parseWildernessLevel(text) > 0)
		{
			return false;
		}
		String plain = text.replaceAll("<[^>]*>", " ").toLowerCase(java.util.Locale.ENGLISH);
		return plain.contains("protection") || plain.contains("guarded") || plain.contains("no pvp");
	}

	/** The combat levels that can attack you at this Wilderness level, as "77-117". */
	public static String bracket(int yourCombat, int wildernessLevel)
	{
		if (yourCombat <= 0 || wildernessLevel <= 0)
		{
			return "";
		}
		return Math.max(3, yourCombat - wildernessLevel) + "-" + Math.min(126, yourCombat + wildernessLevel);
	}

	/**
	 * Reads this tick's players and returns the alarm.
	 *
	 * @param yourWilderness your Wilderness level as the game counts it for attacks (0 off the Wilderness)
	 * @param multi you stand in a multi-combat area
	 */
	public Alarm update(int yourCombat, int yourWilderness, boolean multi, List<Sighting> seen, Footing footing)
	{
		if (yourWilderness <= 0)
		{
			reset();
			return last;
		}
		Map<String, Integer> distances = new HashMap<>();
		List<Foe> foes = new ArrayList<>();
		for (Sighting s : seen)
		{
			if (s.name == null)
			{
				continue;
			}
			distances.put(s.name, s.distance);
			Integer before = lastDistance.get(s.name);
			int memory = closingFor.getOrDefault(s.name, 0);
			if (before != null && s.distance < before)
			{
				memory = CLOSING_MEMORY_TICKS;
			}
			else if (before != null && s.distance > before)
			{
				memory = 0;
			}
			else
			{
				memory = Math.max(0, memory - 1);
			}
			closingFor.put(s.name, memory);
			if (s.trusted || !canAttack(yourCombat, yourWilderness, s.combatLevel, s.wildernessLevel))
			{
				continue;
			}
			foes.add(new Foe(s, memory > 0));
		}
		lastDistance.clear();
		lastDistance.putAll(distances);
		closingFor.keySet().retainAll(distances.keySet());

		if (foes.isEmpty())
		{
			last = Alarm.QUIET;
			return last;
		}
		foes.sort((a, b) -> a.menace() != b.menace() ? b.menace() - a.menace() : a.distance - b.distance);
		Foe lead = foes.get(0);
		Strength strength;
		if (lead.targetingYou || lead.menace() >= 3 || multi && foes.size() >= 2)
		{
			strength = Strength.DANGER;
		}
		else if (lead.menace() >= 1 || multi)
		{
			strength = Strength.WARN;
		}
		else
		{
			strength = Strength.WATCH;
		}
		last = new Alarm(strength, foes, multi, move(footing, strength == Strength.WATCH));
		return last;
	}

	/**
	 * Whether a step from one strength to another deserves a notification: the first sighting, a step up once the
	 * cooldown has passed, or any step up to danger.
	 */
	public static boolean shouldNotify(Strength before, Strength now, int cooldown)
	{
		if (now.compareTo(before) <= 0)
		{
			return false;
		}
		return before == Strength.NONE || cooldown == 0 || now == Strength.DANGER;
	}

	public Alarm getLast()
	{
		return last;
	}

	public void reset()
	{
		lastDistance.clear();
		closingFor.clear();
		last = Alarm.QUIET;
	}

	/**
	 * The plain next move with a PKer near: teleport if your teleport works where you stand, otherwise how far
	 * south to the line where it does, otherwise how far to the ditch. Teleports stop above level 20, a few work to
	 * 30, none deeper; a teleblock stops them all (oldschool.runescape.wiki/w/Wilderness).
	 */
	public static String move(Footing f)
	{
		return move(f, false);
	}

	/** @param quietly nobody shows signs of coming for you yet, so the move is said as a fact, not an order */
	public static String move(Footing f, boolean quietly)
	{
		if (f == null)
		{
			return null;
		}
		if (f.teleblocked)
		{
			return f.tilesToDitch > 0 ? "Teleblocked: run " + f.tilesToDitch + " tiles south to the ditch"
				: "Teleblocked: run for the way out";
		}
		if (f.escape != null && f.wildernessLevel <= f.escapeReach)
		{
			// Only someone who could attack is no reason to leave yet; one who means to is.
			return quietly ? "Your " + f.escape + " works here" : "Teleport now: " + f.escape + " works here";
		}
		if (f.escape != null)
		{
			int tiles = f.escapeReach >= 30 ? f.tilesTo30 : f.tilesTo20;
			int line = f.escapeReach >= 30 ? 30 : 20;
			return tiles > 0 ? "Run " + tiles + " tiles south to level " + line + ", then " + f.escape
				: "Get below level " + (line + 1) + ", then " + f.escape;
		}
		return f.tilesToDitch > 0 ? "No teleport: run " + f.tilesToDitch + " tiles south to the ditch"
			: "No teleport: head for the way out";
	}
}
