package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the player's watch rules. Each rule fires as its moment comes, not for as long as it lasts: a stat must have
 * been at or above the number and then fall below it, the pack must have had room and then fill, you must have moved
 * and then stood still. After firing a rule rests for its cooldown; if its moment comes again while it rests, it
 * fires when the rest ends, provided the moment still holds. Game messages fire on each match outside the rest.
 *
 * <p>Plain logic with no client: the plugin hands it a {@link Reading} each tick and each game message.
 */
public final class RuleWatch
{
	/** A game tick is 0.6 seconds. */
	static final double SECONDS_PER_TICK = 0.6;

	/** What the game shows this tick; a figure that is not known is -1 and leaves its rules as they were. */
	public static final class Reading
	{
		int tick;
		final Map<String, Integer> stats = new HashMap<>();
		int runEnergy = -1;
		int food = -1;
		/** Unnoted items in the pack by lower-case name, or null when the pack is not known. */
		Map<String, Long> items;
		int filledSlots = -1;
		int idleTicks = -1;

		public Reading(int tick)
		{
			this.tick = tick;
		}

		/** A skill's current (boosted or drained) level, by the skill's name. */
		public Reading stat(String skill, int level)
		{
			stats.put(WatchRule.fold(skill), level);
			return this;
		}

		public Reading runEnergy(int percent)
		{
			runEnergy = percent;
			return this;
		}

		public Reading food(int pieces)
		{
			food = pieces;
			return this;
		}

		public Reading item(String name, long quantity)
		{
			if (items == null)
			{
				items = new HashMap<>();
			}
			if (name != null)
			{
				items.merge(WatchRule.fold(name), quantity, Long::sum);
			}
			return this;
		}

		/** Every unnoted item in the pack, as Guardian reads it. */
		public Reading pack(java.util.List<Supplies.Item> pack)
		{
			packKnown();
			for (Supplies.Item item : pack)
			{
				item(item.name, item.quantity);
			}
			return this;
		}

		/** The pack is known even when it holds nothing. */
		public Reading packKnown()
		{
			if (items == null)
			{
				items = new HashMap<>();
			}
			return this;
		}

		public Reading filledSlots(int slots)
		{
			filledSlots = slots;
			return this;
		}

		public Reading idleTicks(int ticks)
		{
			idleTicks = ticks;
			return this;
		}
	}

	/** A rule that fired, with the words to tell the player. */
	public static final class Alert
	{
		private final WatchRule rule;
		private final String detail;

		Alert(WatchRule rule, String detail)
		{
			this.rule = rule;
			this.detail = detail;
		}

		public WatchRule getRule()
		{
			return rule;
		}

		/** What set it off, such as "Prayer 9, below 10" or the message itself. */
		public String getDetail()
		{
			return detail;
		}

		/** A rule fired by hand from the editor's Try button, to show the player how it will look. */
		public static Alert trial(WatchRule rule)
		{
			return new Alert(rule, "A try: this is how it will look");
		}

		/** One line for a notification or the chat: "Prayer running low: Prayer 9, below 10". */
		public String line()
		{
			return rule.getName() + ": " + detail;
		}
	}

	private static final class State
	{
		final String signature;
		/** Ready to fire the next time its moment holds; false until the moment has been seen not to hold. */
		boolean armed;
		int lastFired = Integer.MIN_VALUE / 2;
		SafeRegex.Compiled pattern;

		State(String signature)
		{
			this.signature = signature;
		}
	}

	private final Map<Integer, State> states = new HashMap<>();
	/** Rules whose pattern took too long on a message; paused for the session, and said once. */
	private final Set<Integer> paused = new HashSet<>();
	private final List<WatchRule> newlyPaused = new ArrayList<>();

	/** Forgets every rule's rest and readiness, as on a new login. */
	public void reset()
	{
		states.clear();
		paused.clear();
		newlyPaused.clear();
	}

	private State state(WatchRule rule)
	{
		String sig = rule.signature();
		State s = states.get(rule.getId());
		if (s == null || !s.signature.equals(sig))
		{
			s = new State(sig);
			if (rule.getTrigger() == WatchRule.Trigger.CHAT_REGEX)
			{
				s.pattern = SafeRegex.compile(rule.getText());
			}
			states.put(rule.getId(), s);
			paused.remove(rule.getId());
		}
		return s;
	}

	static int cooldownTicks(WatchRule rule)
	{
		return (int) Math.ceil(rule.getCooldown() / SECONDS_PER_TICK);
	}

	private static boolean rested(State s, WatchRule rule, int tick)
	{
		return tick - s.lastFired >= cooldownTicks(rule);
	}

	/** Checks every rule read from the game each tick; returns those that fire now. */
	public List<Alert> tick(WatchRules rules, Reading r)
	{
		if (rules == null || r == null)
		{
			return Collections.emptyList();
		}
		List<Alert> out = new ArrayList<>();
		Set<Integer> live = new HashSet<>();
		for (WatchRule rule : rules.getRules())
		{
			live.add(rule.getId());
			if (!rule.isOn() || !rule.isComplete() || rule.getTrigger().isChat())
			{
				continue;
			}
			State s = state(rule);
			String holds = holds(rule, r);
			if (holds == null)
			{
				continue;
			}
			if (holds.isEmpty())
			{
				s.armed = true;
				continue;
			}
			if (s.armed && rested(s, rule, r.tick))
			{
				s.armed = false;
				s.lastFired = r.tick;
				out.add(new Alert(rule, holds));
			}
		}
		states.keySet().retainAll(live);
		return out;
	}

	/** Checks the message rules against one game message, tags already removed; returns those that fire. */
	public List<Alert> message(WatchRules rules, String message, int tick)
	{
		if (rules == null || message == null || message.isEmpty())
		{
			return Collections.emptyList();
		}
		String text = message.length() > SafeRegex.MAX_INPUT ? message.substring(0, SafeRegex.MAX_INPUT) : message;
		String folded = WatchRule.fold(text);
		List<Alert> out = new ArrayList<>();
		for (WatchRule rule : rules.getRules())
		{
			if (!rule.isOn() || !rule.isComplete() || !rule.getTrigger().isChat() || paused.contains(rule.getId()))
			{
				continue;
			}
			State s = state(rule);
			if (!rested(s, rule, tick))
			{
				continue;
			}
			boolean found;
			if (rule.getTrigger() == WatchRule.Trigger.CHAT)
			{
				found = folded.contains(WatchRule.fold(rule.getText()));
			}
			else
			{
				SafeRegex.Outcome outcome = SafeRegex.find(s.pattern, text);
				if (outcome == SafeRegex.Outcome.TOO_SLOW)
				{
					paused.add(rule.getId());
					newlyPaused.add(rule);
					continue;
				}
				found = outcome == SafeRegex.Outcome.FOUND;
			}
			if (found)
			{
				s.lastFired = tick;
				out.add(new Alert(rule, text.trim()));
			}
		}
		return out;
	}

	/** Rules paused since the last call because their pattern took too long; each is told once. */
	public List<WatchRule> takePaused()
	{
		List<WatchRule> out = new ArrayList<>(newlyPaused);
		newlyPaused.clear();
		return out;
	}

	/** The rules paused this session, by id. */
	public Set<Integer> pausedIds()
	{
		return new HashSet<>(paused);
	}

	public boolean isPaused(int id)
	{
		return paused.contains(id);
	}

	/**
	 * Whether a tick rule's moment holds: null when its figure is not known, "" when it does not hold, else the
	 * words for what set it off.
	 */
	static String holds(WatchRule rule, Reading r)
	{
		int n = rule.getValue();
		switch (rule.getTrigger())
		{
			case STAT_BELOW:
			{
				Integer level = r.stats.get(WatchRule.fold(rule.getSkill()));
				if (level == null || level < 0)
				{
					return null;
				}
				return level < n ? rule.getSkill() + " " + level + ", below " + n : "";
			}
			case RUN_BELOW:
				return r.runEnergy < 0 ? null : r.runEnergy < n ? "Run energy " + r.runEnergy + "%, below " + n + "%" : "";
			case FOOD_BELOW:
				return r.food < 0 ? null : r.food < n ? plural(r.food, "piece") + " of food left, below " + n : "";
			case ITEM_BELOW:
			{
				if (r.items == null)
				{
					return null;
				}
				long count = count(r.items, rule.getText());
				return count < n ? rule.getText() + ": " + count + " left, below " + n : "";
			}
			case PACK_FULL:
				return r.filledSlots < 0 ? null : r.filledSlots >= WatchRule.PACK_SLOTS ? "Backpack full" : "";
			case IDLE:
			{
				if (r.idleTicks < 0)
				{
					return null;
				}
				int seconds = (int) Math.floor(r.idleTicks * SECONDS_PER_TICK);
				return seconds >= n ? "Idle for " + seconds + " seconds" : "";
			}
			default:
				return null;
		}
	}

	/**
	 * How many of an item the pack holds by its name, ignoring case. A potion counts in every dose: "Prayer potion"
	 * counts Prayer potion(4) through (1); a name with its dose counts only that dose.
	 */
	static long count(Map<String, Long> items, String name)
	{
		String want = WatchRule.fold(name);
		long total = 0;
		for (Map.Entry<String, Long> e : items.entrySet())
		{
			String have = e.getKey();
			if (have.equals(want) || undosed(have).equals(want))
			{
				total += e.getValue() == null ? 0 : Math.max(0, e.getValue());
			}
		}
		return total;
	}

	private static String undosed(String name)
	{
		int open = name.lastIndexOf('(');
		if (open > 0 && name.endsWith(")"))
		{
			String inside = name.substring(open + 1, name.length() - 1);
			if (!inside.isEmpty() && inside.chars().allMatch(Character::isDigit))
			{
				return name.substring(0, open).trim();
			}
		}
		return name;
	}

	private static String plural(int n, String word)
	{
		return n + " " + word + (n == 1 ? "" : "s");
	}
}
