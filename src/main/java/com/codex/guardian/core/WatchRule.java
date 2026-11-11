package com.codex.guardian.core;

import java.util.Locale;

/**
 * One alarm the player writes for themselves: a trigger (a game message, a stat falling below a number, a full pack,
 * standing idle...) and what to do when it comes (a notification or a flash at the screen edge),
 * then a rest before it may come again. Plain data, saved as JSON in the account's RuneLite profile; every field is
 * brought back to sense by {@link #repair()}, so a hand-edited or damaged save can never hold a rule that misbehaves.
 */
public final class WatchRule
{
	public static final int NAME_MAX = 32;
	public static final int TEXT_MAX = SafeRegex.MAX_PATTERN;
	public static final int COOLDOWN_MIN = 2;
	public static final int COOLDOWN_MAX = 3600;
	public static final int COOLDOWN_DEFAULT = 30;
	/** A full backpack: "A player's backpack inventory can hold up to 28 items" (wiki: Inventory). */
	public static final int PACK_SLOTS = 28;

	/** What sets a rule off. */
	public enum Trigger
	{
		CHAT("Game message contains", "Words", 1, 1, true),
		CHAT_REGEX("Game message matches", "Pattern", 1, 1, true),
		STAT_BELOW("Skill falls below", "Level", 1, 150, false),
		RUN_BELOW("Run energy falls below", "Percent", 1, 100, false),
		FOOD_BELOW("Food falls below", "Pieces", 1, PACK_SLOTS, false),
		ITEM_BELOW("Item count falls below", "Count", 1, 1_000_000_000, false),
		PACK_FULL("Backpack is full", null, 1, 1, false),
		IDLE("Idle for", "Seconds", 3, 3600, false);

		private final String label;
		private final String unit;
		private final int min;
		private final int max;
		private final boolean chat;

		Trigger(String label, String unit, int min, int max, boolean chat)
		{
			this.label = label;
			this.unit = unit;
			this.min = min;
			this.max = max;
			this.chat = chat;
		}

		public String getLabel()
		{
			return label;
		}

		/** What the number counts, or null when the trigger takes none. */
		public String getUnit()
		{
			return unit;
		}

		public int getMin()
		{
			return min;
		}

		public int getMax()
		{
			return max;
		}

		/** True for the triggers read from game messages rather than from the game each tick. */
		public boolean isChat()
		{
			return chat;
		}

		public boolean takesNumber()
		{
			return unit != null && !chat;
		}

		public boolean takesText()
		{
			return chat || this == ITEM_BELOW;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** The flash's colour, from the Codex palette. */
	public enum Tint
	{
		GOLD("Gold"),
		TEAL("Teal"),
		EMBER("Ember"),
		BLOOD("Blood");

		private final String label;

		Tint(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	int id;
	String name;
	boolean on = true;
	Trigger trigger;
	/** The words or pattern for a message, or the item's name. */
	String text;
	/** The skill's name, for {@link Trigger#STAT_BELOW}. */
	String skill;
	int value;
	boolean notify;
	boolean flash;
	Tint tint;
	/**
	 * Older versions could also write a line in the game's chatbox. Codex never writes in the chatbox any more, so a
	 * saved rule that asked for it is read as a notification instead (see {@link #repair()}); kept only to read it.
	 */
	boolean chat;
	int cooldown = COOLDOWN_DEFAULT;

	public WatchRule()
	{
	}

	public static WatchRule of(String name, Trigger trigger, String text, String skill, int value)
	{
		WatchRule r = new WatchRule();
		r.name = name;
		r.trigger = trigger;
		r.text = text;
		r.skill = skill;
		r.value = value;
		r.notify = true;
		r.tint = Tint.GOLD;
		return r.repair();
	}

	/** A fresh rule for the editor: a game message, told by notification. */
	public static WatchRule blank()
	{
		return of("New rule", Trigger.CHAT, "", null, 1);
	}

	public WatchRule actions(boolean notify, boolean flash, Tint tint, boolean chat)
	{
		this.notify = notify;
		this.flash = flash;
		this.tint = tint == null ? Tint.GOLD : tint;
		this.chat = chat;
		return this;
	}

	public WatchRule cooldown(int seconds)
	{
		cooldown = clamp(seconds, COOLDOWN_MIN, COOLDOWN_MAX);
		return this;
	}

	public WatchRule copy()
	{
		WatchRule r = new WatchRule();
		r.id = id;
		r.name = name;
		r.on = on;
		r.trigger = trigger;
		r.text = text;
		r.skill = skill;
		r.value = value;
		r.notify = notify;
		r.flash = flash;
		r.tint = tint;
		r.chat = chat;
		r.cooldown = cooldown;
		return r;
	}

	/**
	 * True when this rule can be kept: a trigger this version knows. A rule saved by a newer Guardian with a trigger
	 * this one does not know reads with no trigger and is left out (and the save kept aside), never guessed at.
	 */
	public boolean isValid()
	{
		return trigger != null;
	}

	/** Brings every field back to sense: lengths bounded, numbers in range, a name and a colour always there. */
	public WatchRule repair()
	{
		name = clean(name, NAME_MAX);
		if (name.isEmpty())
		{
			name = trigger == null ? "Rule" : trigger.getLabel();
		}
		text = clean(text, TEXT_MAX);
		skill = skill == null ? null : clean(skill, 20);
		if (trigger != null)
		{
			value = clamp(value, trigger.getMin(), trigger.getMax());
			if (trigger == Trigger.STAT_BELOW && (skill == null || skill.isEmpty()))
			{
				skill = "Hitpoints";
			}
		}
		if (tint == null)
		{
			tint = Tint.GOLD;
		}
		if (chat)
		{
			// The chatbox line is gone; the rule still tells you, with a notification.
			chat = false;
			notify = true;
		}
		cooldown = clamp(cooldown, COOLDOWN_MIN, COOLDOWN_MAX);
		return this;
	}

	/** True when the rule has what it needs to fire: words for a message, a name for an item, an action to take. */
	public boolean isComplete()
	{
		if (trigger == null || !notify && !flash)
		{
			return false;
		}
		if (trigger.takesText() && (text == null || text.trim().isEmpty()))
		{
			return false;
		}
		return trigger != Trigger.CHAT_REGEX || SafeRegex.compile(text).isOk();
	}

	/** The rule in a few words, as its card shows it: "Prayer falls below 10". */
	public String describe()
	{
		if (trigger == null)
		{
			return "";
		}
		String words = text == null || text.isEmpty() ? "..." : text;
		switch (trigger)
		{
			case CHAT:
				return "Message has \"" + words + "\"";
			case CHAT_REGEX:
				return "Message matches /" + words + "/";
			case STAT_BELOW:
				return skill + " falls below " + value;
			case RUN_BELOW:
				return "Run energy falls below " + value + "%";
			case FOOD_BELOW:
				return "Food falls below " + value;
			case ITEM_BELOW:
				return words + " falls below " + value;
			case PACK_FULL:
				return "Backpack is full";
			case IDLE:
				return "Idle for " + value + "s";
			default:
				return trigger.getLabel();
		}
	}

	/**
	 * What decides when the rule fires. A rule whose signature changes in the editor starts afresh: its rest and its
	 * readiness to fire are forgotten, so an edited rule never inherits an old cooldown.
	 */
	String signature()
	{
		return what() + "|" + cooldown;
	}

	/** What the rule watches for, whatever it is called and however it tells you. */
	String what()
	{
		return trigger + "|" + fold(text) + "|" + fold(skill) + "|" + value;
	}

	static String clean(String s, int max)
	{
		if (s == null)
		{
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < s.length() && out.length() < max; i++)
		{
			char c = s.charAt(i);
			// Control characters (a pasted newline, a tab) have no place in a one-line rule.
			out.append(Character.isISOControl(c) ? ' ' : c);
		}
		return out.toString().trim();
	}

	static int clamp(int v, int min, int max)
	{
		return Math.max(min, Math.min(max, v));
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public boolean isOn()
	{
		return on;
	}

	public void setOn(boolean on)
	{
		this.on = on;
	}

	public Trigger getTrigger()
	{
		return trigger;
	}

	public void setTrigger(Trigger trigger)
	{
		this.trigger = trigger;
	}

	public String getText()
	{
		return text;
	}

	public void setText(String text)
	{
		this.text = text;
	}

	public String getSkill()
	{
		return skill;
	}

	public void setSkill(String skill)
	{
		this.skill = skill;
	}

	public int getValue()
	{
		return value;
	}

	public void setValue(int value)
	{
		this.value = value;
	}

	public boolean isNotify()
	{
		return notify;
	}

	public void setNotify(boolean notify)
	{
		this.notify = notify;
	}

	public boolean isFlash()
	{
		return flash;
	}

	public void setFlash(boolean flash)
	{
		this.flash = flash;
	}

	public Tint getTint()
	{
		return tint;
	}

	public void setTint(Tint tint)
	{
		this.tint = tint;
	}

	public boolean isChat()
	{
		return chat;
	}

	public void setChat(boolean chat)
	{
		this.chat = chat;
	}

	public int getCooldown()
	{
		return cooldown;
	}

	public void setCooldown(int cooldown)
	{
		this.cooldown = cooldown;
	}

	/** The lower-case form names are compared in. */
	static String fold(String s)
	{
		return s == null ? "" : s.trim().toLowerCase(Locale.ENGLISH);
	}
}
