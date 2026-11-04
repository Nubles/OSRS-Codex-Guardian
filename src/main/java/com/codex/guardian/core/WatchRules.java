package com.codex.guardian.core;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One account's watch rules, saved as JSON in its RuneLite profile with a version, so a later Guardian can read an
 * older save and an older one can tell a newer save apart and keep it aside rather than write over it.
 */
public final class WatchRules
{
	public static final int VERSION = 1;
	public static final int MAX_RULES = 12;

	int version = VERSION;
	List<WatchRule> rules = new ArrayList<>();

	/**
	 * Reads saved rules, keeping every one that reads on its own; an empty set when nothing was saved. The result is
	 * damaged when anything was left out or the save is from a newer version, so the text can be kept aside.
	 */
	public static SavedJson.Read<WatchRules> load(Gson gson, String json)
	{
		SavedJson.Read<WatchRules> read = SavedJson.read(gson, json, WatchRules.class);
		WatchRules kept = read.getValue() == null ? new WatchRules() : read.getValue();
		boolean dropped = kept.version > VERSION
			|| kept.rules != null && (kept.rules.size() > MAX_RULES || kept.rules.stream().anyMatch(r -> r == null || !r.isValid()));
		return new SavedJson.Read<>(kept.repair(), read.isDamaged() || dropped);
	}

	public WatchRules repair()
	{
		if (rules == null)
		{
			rules = new ArrayList<>();
		}
		rules.removeIf(r -> r == null || !r.isValid());
		while (rules.size() > MAX_RULES)
		{
			rules.remove(rules.size() - 1);
		}
		rules.forEach(WatchRule::repair);
		// Every rule needs its own id, so the watcher can keep each one's rest apart; repeats and blanks get new ones.
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (WatchRule r : rules)
		{
			if (r.id <= 0 || !seen.add(r.id))
			{
				r.id = nextId();
				seen.add(r.id);
			}
		}
		version = VERSION;
		return this;
	}

	public WatchRules copy()
	{
		WatchRules c = new WatchRules();
		for (WatchRule r : rules)
		{
			c.rules.add(r.copy());
		}
		return c;
	}

	private int nextId()
	{
		int max = 0;
		for (WatchRule r : rules)
		{
			max = Math.max(max, r.id);
		}
		return max + 1;
	}

	/** Adds a rule with a fresh id, unless the set is full; returns the rule kept, or null. */
	public WatchRule add(WatchRule rule)
	{
		if (rule == null || !rule.isValid() || rules.size() >= MAX_RULES)
		{
			return null;
		}
		WatchRule kept = rule.copy().repair();
		kept.id = nextId();
		rules.add(kept);
		return kept;
	}

	/** Puts an edited rule in place of the one with its id; false when there is none. */
	public boolean replace(WatchRule rule)
	{
		if (rule == null || !rule.isValid())
		{
			return false;
		}
		for (int i = 0; i < rules.size(); i++)
		{
			if (rules.get(i).id == rule.id)
			{
				rules.set(i, rule.copy().repair());
				return true;
			}
		}
		return false;
	}

	public boolean remove(int id)
	{
		return rules.removeIf(r -> r.id == id);
	}

	public WatchRule find(int id)
	{
		for (WatchRule r : rules)
		{
			if (r.id == id)
			{
				return r;
			}
		}
		return null;
	}

	public List<WatchRule> getRules()
	{
		return Collections.unmodifiableList(rules);
	}

	public boolean isFull()
	{
		return rules.size() >= MAX_RULES;
	}

	/** True when a rule already watches for what this preset does, so the preset is not offered twice. */
	public boolean has(Preset preset)
	{
		String what = preset.rule().what();
		return rules.stream().anyMatch(r -> r.what().equals(what));
	}

	/**
	 * Ready-made rules for what Guardian already worries about. Every game message is quoted from the wiki; see the
	 * pull request's table of facts.
	 */
	public enum Preset
	{
		/** Prayer is a skill (wiki: Skills); the threshold is a choice, not a game fact. */
		LOW_PRAYER("Prayer running low"),
		/** Food as Guardian reads it in the pack. */
		LOW_FOOD("Food running low"),
		/** Wiki, Tele Block: "A Tele Block has been cast on you by [player name]. It will expire in [time]." */
		TELE_BLOCK("Tele Blocked"),
		/** Wiki, Freeze: the chat box reports "You have been frozen!" */
		FROZEN("Frozen"),
		/** Wiki, Ring of life: "Your Ring of Life saves you and is destroyed in the process." */
		RING_OF_LIFE("Ring of life used"),
		/** Wiki, Inventory: the backpack "can hold up to 28 items". */
		PACK_FULL("Backpack full");

		private final String label;

		Preset(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		public WatchRule rule()
		{
			switch (this)
			{
				case LOW_PRAYER:
					return WatchRule.of(label, WatchRule.Trigger.STAT_BELOW, null, "Prayer", 10)
						.actions(true, true, WatchRule.Tint.TEAL, false).cooldown(30);
				case LOW_FOOD:
					return WatchRule.of(label, WatchRule.Trigger.FOOD_BELOW, null, null, 3)
						.actions(true, true, WatchRule.Tint.EMBER, false).cooldown(60);
				case TELE_BLOCK:
					return WatchRule.of(label, WatchRule.Trigger.CHAT, "A Tele Block has been cast on you", null, 1)
						.actions(true, true, WatchRule.Tint.BLOOD, false).cooldown(10);
				case FROZEN:
					return WatchRule.of(label, WatchRule.Trigger.CHAT, "You have been frozen!", null, 1)
						.actions(false, true, WatchRule.Tint.TEAL, false).cooldown(5);
				case RING_OF_LIFE:
					return WatchRule.of(label, WatchRule.Trigger.CHAT, "Your Ring of Life saves you", null, 1)
						.actions(true, false, WatchRule.Tint.GOLD, true).cooldown(10);
				case PACK_FULL:
				default:
					return WatchRule.of(label, WatchRule.Trigger.PACK_FULL, null, null, 1)
						.actions(true, false, WatchRule.Tint.GOLD, false).cooldown(30);
			}
		}

		public static List<Preset> all()
		{
			return Arrays.asList(values());
		}
	}
}
