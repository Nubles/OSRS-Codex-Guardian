package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An inquest into a death or a close call: the last ticks before it, frozen tick by tick (hitpoints, prayer points
 * and which protection prayer was on, every hit with who dealt it, food and potions, teleport attempts, the
 * Wilderness level, poison, venom, a freeze or a teleblock), and the findings read out of them afterwards: a
 * plain verdict, the tick it went past saving, and what the player had in hand that could have saved them.
 *
 * <p>It is read only after the fact. Nothing here runs during a fight or tells the player what to pray or where to
 * stand; the panel shows it once the danger has passed or the run has ended.</p>
 *
 * <p>Plain data with int arrays so Gson saves it compactly. Every array has the same length, oldest tick first.</p>
 */
public final class Inquest
{
	public enum Kind
	{
		CLOSE_CALL,
		DEATH
	}

	/** At most this many ticks are kept per inquest: half a minute. */
	public static final int MAX_TICKS = 50;
	/** Ticks kept from before hitpoints first fell low in a close call, so the hits and the last meal are in it. */
	static final int LEAD_TICKS = 20;
	/** Hits kept per inquest; past this the record is damage. */
	static final int MAX_HITS = 400;

	/** Protection prayers, as a mask on {@link #prot}. */
	public static final int MELEE = 1;
	public static final int MISSILES = 2;
	public static final int MAGIC = 4;

	/** Conditions, as a mask on {@link #flags}. */
	public static final int POISONED = 1;
	public static final int VENOMED = 2;
	public static final int FROZEN = 4;
	public static final int TELEBLOCKED = 8;
	/** A teleport carried would have worked on this tick, by Guardian's way-out rules (level and teleblock). */
	public static final int WAY_OUT = 16;

	/**
	 * Prayer levels for the protection prayers, from the wiki: Protect from Magic "unlocked at level 37 Prayer",
	 * Protect from Missiles "unlocked at level 40 Prayer", Protect from Melee "a Prayer level of at least 43".
	 */
	public static final int MAGIC_LEVEL = 37;
	public static final int MISSILES_LEVEL = 40;
	public static final int MELEE_LEVEL = 43;

	/** One hit on the player. */
	public static final class Hit
	{
		/** The tick it landed on, an index into the arrays. */
		int t;
		int amount;
		/** Who or what dealt it, as Guardian names attackers ("Ice troll runt", "A player", "Venom"). */
		String from;
		/** "Poison", "Venom", "Burn", "Bleed" or "Disease" for damage over time; null for a blow. */
		String kind;
		/** The game showed it as a max hit. */
		boolean max;

		Hit()
		{
		}

		Hit(int t, int amount, String from, String kind, boolean max)
		{
			this.t = t;
			this.amount = amount;
			this.from = from;
			this.kind = kind;
			this.max = max;
		}

		public int getTick()
		{
			return t;
		}

		public int getAmount()
		{
			return amount;
		}

		public String getFrom()
		{
			return from;
		}

		public String getKind()
		{
			return kind;
		}

		public boolean isMax()
		{
			return max;
		}

		/** The name the damage is put down to: the toxin for damage over time, otherwise the attacker. */
		String blame()
		{
			return kind != null ? kind : from == null || from.isEmpty() ? "Unseen" : from;
		}
	}

	/** How a finding reads: something held and not used, something missing, or something that did its job. */
	public enum Tone
	{
		UNUSED,
		LACKING,
		HELD,
		NOTE
	}

	/** One line of "what would have saved you". */
	public static final class Finding
	{
		private final Tone tone;
		private final String text;

		Finding(Tone tone, String text)
		{
			this.tone = tone;
			this.text = text;
		}

		public Tone getTone()
		{
			return tone;
		}

		public String getText()
		{
			return text;
		}

		@Override
		public String toString()
		{
			return tone + ": " + text;
		}
	}

	Kind kind;
	long at;
	String place;
	int max;
	int prayerLevel;
	/** The teleport carried when the inquest was frozen, and the Wilderness level it works to; null without one. */
	String escape;
	int reach;
	/** Last resorts worn or carried when it was frozen (ring of life, phoenix necklace, escape crystal). */
	List<String> graces;
	int[] hp;
	int[] prayer;
	int[] prot;
	int[] food;
	int[] heal;
	int[] prayerDoses;
	int[] ate;
	int[] drank;
	int[] tele;
	int[] wild;
	int[] flags;
	int[] foes;
	/**
	 * The hits, saved column by column so a busy fight stays small: the tick, the amount, who dealt it (an index into
	 * {@link #names}) and flags ({@link #OVER_TIME}, {@link #MAX_HIT}).
	 */
	List<String> names;
	int[] hitTick;
	int[] hitAmount;
	int[] hitBy;
	int[] hitFlags;
	/** The hits read back from the columns, built on first use. */
	private transient List<Hit> hits;

	static final int OVER_TIME = 1;
	static final int MAX_HIT = 2;

	Inquest()
	{
	}

	// ---- reading ---------------------------------------------------------------------------

	public Kind getKind()
	{
		return kind;
	}

	public long getAt()
	{
		return at;
	}

	public String getPlace()
	{
		return place;
	}

	public int getMax()
	{
		return max;
	}

	public int getPrayerLevel()
	{
		return prayerLevel;
	}

	public int ticks()
	{
		return hp == null ? 0 : hp.length;
	}

	public int hpAt(int i)
	{
		return hp[i];
	}

	public int prayerAt(int i)
	{
		return prayer[i];
	}

	public int protAt(int i)
	{
		return prot[i];
	}

	public int ateAt(int i)
	{
		return ate[i];
	}

	public int drankAt(int i)
	{
		return drank[i];
	}

	public boolean teleportedAt(int i)
	{
		return tele[i] > 0;
	}

	public int flagsAt(int i)
	{
		return flags[i];
	}

	public int wildernessAt(int i)
	{
		return wild[i];
	}

	public List<Hit> getHits()
	{
		if (hits == null)
		{
			List<Hit> read = new ArrayList<>();
			int count = hitTick == null ? 0 : hitTick.length;
			for (int k = 0; k < count; k++)
			{
				String by = names.get(hitBy[k]);
				read.add(new Hit(hitTick[k], hitAmount[k], by, (hitFlags[k] & OVER_TIME) != 0 ? by : null, (hitFlags[k] & MAX_HIT) != 0));
			}
			hits = read;
		}
		return Collections.unmodifiableList(hits);
	}

	/** Damage landed on tick {@code i}, every hit added up. */
	public int hitAt(int i)
	{
		int total = 0;
		for (Hit h : getHits())
		{
			if (h.t == i)
			{
				total += h.amount;
			}
		}
		return total;
	}

	/** True when the arrays are whole and of one length, as a saved inquest must be before it is read. */
	public boolean isValid()
	{
		int n = ticks();
		if (kind == null || n < 2 || n > MAX_TICKS || max <= 0 || names == null || hitTick == null || hitTick.length > MAX_HITS
			|| hitAmount == null || hitBy == null || hitFlags == null || hitAmount.length != hitTick.length
			|| hitBy.length != hitTick.length || hitFlags.length != hitTick.length)
		{
			return false;
		}
		for (int[] a : new int[][]{prayer, prot, food, heal, prayerDoses, ate, drank, tele, wild, flags, foes})
		{
			if (a == null || a.length != n)
			{
				return false;
			}
		}
		return true;
	}

	/** Drops hits that point outside the record and odd values, so a hand-edited save cannot throw. */
	Inquest repair()
	{
		int n = ticks();
		List<Integer> keep = new ArrayList<>();
		for (int k = 0; k < hitTick.length; k++)
		{
			if (hitTick[k] >= 0 && hitTick[k] < n && hitAmount[k] > 0 && hitAmount[k] <= HitMemory.MAX_HIT && hitBy[k] >= 0
				&& hitBy[k] < names.size() && names.get(hitBy[k]) != null)
			{
				keep.add(k);
			}
		}
		if (keep.size() != hitTick.length)
		{
			int[][] cols = {hitTick, hitAmount, hitBy, hitFlags};
			int[][] kept = new int[4][keep.size()];
			for (int c = 0; c < 4; c++)
			{
				for (int j = 0; j < keep.size(); j++)
				{
					kept[c][j] = cols[c][keep.get(j)];
				}
			}
			hitTick = kept[0];
			hitAmount = kept[1];
			hitBy = kept[2];
			hitFlags = kept[3];
		}
		hits = null;
		if (graces == null)
		{
			graces = new ArrayList<>();
		}
		graces.removeIf(g -> g == null || g.isEmpty());
		prayerLevel = Math.max(0, Math.min(99, prayerLevel));
		return this;
	}

	/** The tick with the fewest hitpoints, the first one if several tie: where the fall bottomed out. */
	public int lowestIndex()
	{
		int best = 0;
		for (int i = 1; i < ticks(); i++)
		{
			if (hp[i] < hp[best])
			{
				best = i;
			}
		}
		return best;
	}

	/**
	 * The tick the fall began: walking back from the lowest point while hitpoints were no lower, so a pause between
	 * hits stays in the fall and a meal (hitpoints rising) ends it, then off the level stretch it started from.
	 */
	public int fallStart()
	{
		int low = lowestIndex();
		int start = low;
		while (start > 0 && hp[start - 1] >= hp[start])
		{
			start--;
		}
		while (start < low && hp[start + 1] == hp[start])
		{
			start++;
		}
		return start;
	}

	/** Damage of the fall: the hits after its start up to and including the lowest point. */
	public int fallDamage()
	{
		int start = fallStart();
		int low = lowestIndex();
		int total = 0;
		for (Hit h : getHits())
		{
			if (h.t > start && h.t <= low)
			{
				total += h.amount;
			}
		}
		return total;
	}

	/** How many ticks the fall took. */
	public int fallTicks()
	{
		return Math.max(1, lowestIndex() - fallStart());
	}

	/** The most damage landed in any one tick of the record: the hit that, landing again, would have killed. */
	public int worstTick()
	{
		int worst = 0;
		for (int i = 0; i < ticks(); i++)
		{
			worst = Math.max(worst, hitAt(i));
		}
		return worst;
	}

	/**
	 * The tick it went past saving. For a death, the first tick from which hitpoints stayed at or below the worst
	 * tick of damage taken and no food was eaten again to the end; for a close call, the first tick of the stretch
	 * into the lowest point that one more tick like the worst would have ended. -1 when it never got that close.
	 */
	public int brinkIndex()
	{
		int line = worstTick();
		int n = ticks();
		if (line <= 0)
		{
			return -1;
		}
		if (kind == Kind.DEATH)
		{
			int t = -1;
			for (int i = n - 1; i >= 0; i--)
			{
				if (hp[i] <= line && ate[i] == 0)
				{
					t = i;
				}
				else
				{
					break;
				}
			}
			return t;
		}
		int low = lowestIndex();
		if (hp[low] > line)
		{
			return -1;
		}
		int t = low;
		while (t > 0 && hp[t - 1] <= line)
		{
			t--;
		}
		return t;
	}

	/** The tick the verdict is read at: past saving if it got there, else the lowest point. */
	private int focus()
	{
		int b = brinkIndex();
		return b >= 0 ? b : lowestIndex();
	}

	/** "Died in the Wilderness (level 24)" or "Close call at Vorkath, 6 hp". */
	public String title()
	{
		String where = place == null || place.isEmpty() ? "" : " in " + place;
		if (kind == Kind.DEATH)
		{
			return "Died" + where;
		}
		return "Close call" + where + ", " + hp[lowestIndex()] + " hp";
	}

	/**
	 * The verdict in plain words, such as "You took 47 in 3 ticks from 3 Ice troll runts with no protection prayer
	 * on; your last food was 9 ticks before."
	 */
	public String verdict()
	{
		int low = lowestIndex();
		int start = fallStart();
		StringBuilder b = new StringBuilder();
		int lost = fallDamage();
		if (lost <= 0)
		{
			b.append(kind == Kind.DEATH ? "You died with no hit on you in the last " : "No hits landed in the last ")
				.append(ticks()).append(" ticks");
		}
		else
		{
			b.append("You took ").append(lost).append(" in ").append(plural(fallTicks(), "tick"))
				.append(" from ").append(blame(start, low));
			String prayerClause = prayerClause(start, low);
			if (prayerClause != null)
			{
				b.append(' ').append(prayerClause);
			}
		}
		int lastAte = -1;
		for (int i = 0; i <= low; i++)
		{
			if (ate[i] > 0)
			{
				lastAte = i;
			}
		}
		int ref = kind == Kind.DEATH ? ticks() - 1 : low;
		if (lastAte >= 0)
		{
			int ago = ref - lastAte;
			b.append(ago == 0 ? "; you ate on the last tick" : "; your last food was " + plural(ago, "tick") + " before");
		}
		else
		{
			b.append("; you ate nothing in the ").append(plural(ref + 1, "tick")).append(" before");
		}
		b.append('.');

		int f = focus();
		List<String> states = new ArrayList<>();
		int seen = 0;
		for (int i = start; i <= low; i++)
		{
			seen |= flags[i];
		}
		if ((seen & FROZEN) != 0)
		{
			states.add("frozen");
		}
		if ((seen & VENOMED) != 0)
		{
			states.add("venomed");
		}
		else if ((seen & POISONED) != 0)
		{
			states.add("poisoned");
		}
		if ((seen & TELEBLOCKED) != 0)
		{
			states.add("teleblocked");
		}
		if (!states.isEmpty())
		{
			b.append(" You were ").append(join(states)).append('.');
		}
		int lastTele = -1;
		for (int i = 0; i < ticks(); i++)
		{
			if (tele[i] > 0)
			{
				lastTele = i;
			}
		}
		if (kind == Kind.DEATH && lastTele >= 0)
		{
			b.append(" You tried to teleport ").append(plural(ticks() - 1 - lastTele, "tick")).append(" before the end.");
		}
		if (kind == Kind.CLOSE_CALL)
		{
			int eaten = 0;
			int drunk = 0;
			for (int i = low; i < ticks(); i++)
			{
				eaten += ate[i];
				drunk += drank[i];
			}
			if (lastTele > f)
			{
				b.append(" You teleported out.");
			}
			else if (eaten > 0 || drunk > 0)
			{
				b.append(" You came back by ").append(eaten > 0 ? "eating " + eaten : "")
					.append(eaten > 0 && drunk > 0 ? " and " : "").append(drunk > 0 ? "drinking " + drunk : "").append('.');
			}
			else
			{
				b.append(" The hits stopped and you came back.");
			}
		}
		return b.toString();
	}

	/** "Tick 14: 9 hp, under the 18 you took in one tick" for the brink, or null when it never got that close. */
	public String brinkLine()
	{
		int b = brinkIndex();
		if (b < 0)
		{
			return null;
		}
		int line = worstTick();
		StringBuilder s = new StringBuilder("Tick ").append(b + 1).append(" of ").append(ticks()).append(": ")
			.append(hp[b]).append(" hp, ").append(hp[b] == line ? "level with" : "under").append(" the ").append(line)
			.append(" you took in one tick");
		if (kind == Kind.DEATH)
		{
			s.append(", and no food was eaten after it");
		}
		else
		{
			for (int i = b; i < ticks(); i++)
			{
				if (ate[i] > 0)
				{
					int later = i - b;
					s.append(later == 0 ? ". You ate on that tick" : ". You ate " + plural(later, "tick") + " later");
					break;
				}
			}
		}
		return s.append('.').toString();
	}

	/**
	 * What the player had in hand at the brink (or the lowest point), from their own readiness: food not eaten, a
	 * teleport that worked there, the protection prayers their level opens, and a last resort.
	 */
	public List<Finding> findings()
	{
		List<Finding> out = new ArrayList<>();
		int f = focus();
		int end = ticks() - 1;

		// Food.
		if (food[f] > 0)
		{
			String held = plural(food[f], "food") + (heal[f] > 0 ? " (" + heal[f] + " hp)" : "");
			out.add(new Finding(Tone.UNUSED, kind == Kind.DEATH ? held + " still in your pack, never eaten"
				: held + " still in your pack at the worst of it"));
		}
		else
		{
			int lastHeld = -1;
			for (int i = 0; i < f; i++)
			{
				if (food[i] > 0)
				{
					lastHeld = i;
				}
			}
			out.add(new Finding(Tone.LACKING, lastHeld >= 0
				? "Your food ran out " + plural(f - lastHeld, "tick") + " before"
				: "No food carried"));
		}

		// A way out.
		int lastTele = -1;
		for (int i = f; i <= end; i++)
		{
			if (tele[i] > 0)
			{
				lastTele = i;
			}
		}
		String name = escape == null || escape.isEmpty() ? "A teleport you carried" : "Your " + escape;
		if ((flags[f] & WAY_OUT) != 0)
		{
			if (lastTele >= 0)
			{
				out.add(new Finding(kind == Kind.DEATH ? Tone.NOTE : Tone.HELD, kind == Kind.DEATH
					? "You tried to teleport " + plural(end - lastTele, "tick") + " before the end; the hit came first"
					: "You teleported out " + plural(lastTele - f, "tick") + " after the worst of it"));
			}
			else
			{
				String where = wild[f] > 0 ? " at level " + wild[f] + " Wilderness" : " there";
				out.add(new Finding(Tone.UNUSED, name + " worked" + where + " and was not used"));
			}
		}
		else if ((flags[f] & TELEBLOCKED) != 0)
		{
			out.add(new Finding(Tone.LACKING, "You were teleblocked: no teleport could work"));
		}
		else if (escape != null && wild[f] > reach)
		{
			out.add(new Finding(Tone.LACKING, "Your " + escape + " stops at level " + reach + "; you were at level "
				+ wild[f] + ", " + plural(wild[f] - reach, "level") + " too deep"));
		}
		else
		{
			out.add(new Finding(Tone.LACKING, "No teleport carried"));
		}

		// Prayer.
		int start = fallStart();
		int low = lowestIndex();
		int mask = protDuringHits(start, low);
		if (prayerLevel > 0 && prayerLevel < MAGIC_LEVEL)
		{
			out.add(new Finding(Tone.NOTE, "At Prayer " + prayerLevel + " no protection prayer is open yet: Protect from Magic comes at "
				+ MAGIC_LEVEL));
		}
		else if (prayerLevel >= MAGIC_LEVEL && mask == 0 && fallDamage() > 0)
		{
			if (prayer[f] == 0)
			{
				out.add(new Finding(prayerDoses[f] > 0 ? Tone.UNUSED : Tone.LACKING, "Your prayer was empty"
					+ (prayerDoses[f] > 0 ? " with " + plural(prayerDoses[f], "prayer dose") + " left" : "")));
			}
			else
			{
				out.add(new Finding(Tone.UNUSED, "Prayer " + prayerLevel + " opens " + openProtections()
					+ "; none was on as the hits landed"));
			}
		}
		else if (mask != 0)
		{
			out.add(new Finding(Tone.HELD, protName(mask) + " was on"));
		}

		// A last resort.
		if (kind == Kind.DEATH && (graces == null || graces.isEmpty()))
		{
			out.add(new Finding(Tone.NOTE, "No ring of life or phoenix necklace worn"));
		}
		return out;
	}

	/** The protection prayers the player's level opens, in words. */
	private String openProtections()
	{
		if (prayerLevel >= MELEE_LEVEL)
		{
			return "all three protection prayers";
		}
		if (prayerLevel >= MISSILES_LEVEL)
		{
			return "Protect from Magic and Missiles";
		}
		return "Protect from Magic";
	}

	/** Every protection prayer on during a direct hit of the fall, as a mask; 0 when none was ever on. */
	private int protDuringHits(int start, int low)
	{
		int mask = 0;
		for (Hit h : getHits())
		{
			if (h.kind == null && h.t > start && h.t <= low)
			{
				mask |= prot[h.t];
			}
		}
		return mask;
	}

	private String prayerClause(int start, int low)
	{
		boolean any = false;
		boolean empty = false;
		int seen = -1;
		boolean mixed = false;
		for (Hit h : getHits())
		{
			if (h.kind != null || h.t <= start || h.t > low)
			{
				continue;
			}
			any = true;
			int p = prot[h.t];
			if (seen >= 0 && p != seen)
			{
				mixed = true;
			}
			seen = p;
			if (prayer[h.t] == 0 && prayerLevel > 0)
			{
				empty = true;
			}
		}
		if (!any)
		{
			return null;
		}
		if (mixed)
		{
			return "while switching protection prayers";
		}
		if (seen == 0)
		{
			return empty ? "with your prayer empty" : "with no protection prayer on";
		}
		return "while praying " + protName(seen);
	}

	/** "Protect from Melee", or "Protect from Melee and Magic" for more than one. */
	static String protName(int mask)
	{
		List<String> names = new ArrayList<>();
		if ((mask & MELEE) != 0)
		{
			names.add("Melee");
		}
		if ((mask & MISSILES) != 0)
		{
			names.add("Missiles");
		}
		if ((mask & MAGIC) != 0)
		{
			names.add("Magic");
		}
		return "Protect from " + join(names);
	}

	/** Who did the damage of the fall: "3 Ice troll runts", "Vorkath and venom", "a player and 2 others". */
	private String blame(int start, int low)
	{
		Map<String, Integer> by = new LinkedHashMap<>();
		for (Hit h : getHits())
		{
			if (h.t > start && h.t <= low)
			{
				by.merge(h.blame(), h.amount, Integer::sum);
			}
		}
		List<Map.Entry<String, Integer>> list = new ArrayList<>(by.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		if (list.isEmpty())
		{
			return "something unseen";
		}
		String top = mid(list.get(0).getKey());
		if (list.size() == 1)
		{
			int most = 0;
			for (int i = start; i <= low; i++)
			{
				most = Math.max(most, foes[i]);
			}
			boolean toxin = false;
			for (Hit h : getHits())
			{
				if (mid(h.blame()).equals(top) && h.kind != null)
				{
					toxin = true;
				}
			}
			if (most > 1 && !toxin && !"a player".equals(top) && !"something unseen".equals(top))
			{
				return most + " " + pluralName(top);
			}
			return top;
		}
		if (list.size() == 2)
		{
			return top + " and " + mid(list.get(1).getKey());
		}
		return top + " and " + (list.size() - 1) + " others";
	}

	/** A name as it reads mid-sentence: "A player" becomes "a player", "Unseen" becomes "something unseen". */
	private static String mid(String name)
	{
		if ("A player".equals(name))
		{
			return "a player";
		}
		switch (name)
		{
			case "Unseen":
				return "something unseen";
			case "Poison":
			case "Venom":
			case "Burn":
			case "Bleed":
			case "Disease":
				return name.toLowerCase(Locale.ENGLISH);
			default:
				return name;
		}
	}

	/** "Ice troll runt" to "Ice troll runts", "Guard" to "Guards", "Man" to "Men". */
	static String pluralName(String name)
	{
		String lower = name.toLowerCase(Locale.ENGLISH);
		if (lower.endsWith("man"))
		{
			return name.substring(0, name.length() - 2) + "en";
		}
		if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("ch") || lower.endsWith("sh"))
		{
			return name;
		}
		return name + "s";
	}

	static String plural(int count, String noun)
	{
		return count + " " + noun + (count == 1 || "food".equals(noun) ? "" : "s");
	}

	private static String join(List<String> parts)
	{
		if (parts.size() <= 1)
		{
			return parts.isEmpty() ? "" : parts.get(0);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	// ---- recording -------------------------------------------------------------------------

	/** One tick of what the client showed, as the plugin reads it. */
	public static final class Frame
	{
		int hp;
		int max;
		int prayer;
		int prayerLevel;
		int prot;
		int food;
		int heal;
		int prayerDoses;
		int potionDoses;
		int wild;
		int flags;
		int foes;

		/**
		 * The frame for a tick, from the situation Guardian read this tick and the few things it does not hold.
		 *
		 * @param protections the protection prayers on, as {@link #MELEE}, {@link #MISSILES} and {@link #MAGIC}
		 * @param frozen      a freeze is holding the player
		 * @param allDoses    every potion dose carried ({@link Supplies#potionDoses})
		 */
		public static Frame of(Situation s, int protections, boolean frozen, int allDoses)
		{
			Readiness r = s.getReadiness() == null ? Readiness.empty() : s.getReadiness();
			int flags = (s.getToxin() == Situation.Toxin.VENOM ? VENOMED : s.getToxin() == Situation.Toxin.POISON ? POISONED : 0)
				| (frozen ? FROZEN : 0) | (s.isTeleblocked() ? TELEBLOCKED : 0) | (s.canEscape() ? WAY_OUT : 0);
			return new Frame()
				.hitpoints(s.getHitpoints(), s.getMaxHitpoints())
				.prayer(s.getPrayer(), s.getMaxPrayer(), protections)
				.supplies(r.getFoodCount(), r.getFoodHealing(), r.getPrayerDoses(), allDoses)
				.wilderness(s.getWildernessLevel())
				.flags(flags)
				.foes(s.getAttackers() == null ? 0 : s.getAttackers().size());
		}

		public Frame hitpoints(int hitpoints, int maxHitpoints)
		{
			hp = Math.max(0, hitpoints);
			max = Math.max(0, maxHitpoints);
			return this;
		}

		public Frame prayer(int points, int level, int protections)
		{
			prayer = Math.max(0, points);
			prayerLevel = Math.max(0, level);
			prot = protections & (MELEE | MISSILES | MAGIC);
			return this;
		}

		/**
		 * @param count       food items carried
		 * @param healing     hitpoints they would heal, as Guardian's readiness counts it
		 * @param prayerDoses prayer-restoring doses carried
		 * @param allDoses    every potion dose carried, to notice a sip
		 */
		public Frame supplies(int count, int healing, int prayerDoses, int allDoses)
		{
			food = Math.max(0, count);
			heal = Math.max(0, healing);
			this.prayerDoses = Math.max(0, prayerDoses);
			potionDoses = Math.max(0, allDoses);
			return this;
		}

		public Frame wilderness(int level)
		{
			wild = Math.max(0, level);
			return this;
		}

		public Frame flags(int value)
		{
			flags = value;
			return this;
		}

		public Frame foes(int count)
		{
			foes = Math.max(0, count);
			return this;
		}

		Frame copyAsDead()
		{
			Frame f = new Frame();
			f.hp = 0;
			f.max = max;
			f.prayer = prayer;
			f.prayerLevel = prayerLevel;
			f.prot = prot;
			f.food = food;
			f.heal = heal;
			f.prayerDoses = prayerDoses;
			f.potionDoses = potionDoses;
			f.wild = wild;
			f.flags = flags;
			f.foes = foes;
			return f;
		}
	}

	/** What the player carried when the inquest is frozen: the teleport and its reach, and their last resorts. */
	public static final class Kit
	{
		final String escape;
		final int reach;
		final List<String> graces;

		public Kit(String escape, int reach, List<String> graces)
		{
			this.escape = escape;
			this.reach = reach;
			this.graces = graces == null ? new ArrayList<>() : new ArrayList<>(graces);
		}

		public static Kit of(Readiness r)
		{
			return r == null ? new Kit(null, 0, null) : new Kit(r.getBestEscape(), Math.max(0, r.getEscapeReach()), r.getSavingGraces());
		}
	}

	/**
	 * The rolling buffer: the last {@link #CAPACITY} ticks, from which an inquest is frozen when a close call ends
	 * or a death comes. The plugin feeds it every tick while logged in.
	 */
	public static final class Recorder
	{
		/** Enough for a close call's longest window plus its lead. */
		static final int CAPACITY = 100;

		private final Frame[] frames = new Frame[CAPACITY];
		private final int[] ate = new int[CAPACITY];
		private final int[] drank = new int[CAPACITY];
		private final int[] tele = new int[CAPACITY];
		@SuppressWarnings("unchecked")
		private final List<Hit>[] hits = new List[CAPACITY];
		private long count;
		private long markedAt = -1;
		private List<Hit> pending = new ArrayList<>();
		private boolean pendingTele;
		private Frame last;

		/** A hit on the player, between ticks; it lands on the next recorded tick. */
		public void hit(int amount, String from, String kind, boolean max)
		{
			if (amount > 0 && pending.size() < 32)
			{
				pending.add(new Hit(-1, amount, from, kind, max));
			}
		}

		/** The player tried to teleport; it lands on the next recorded tick. */
		public void teleport()
		{
			pendingTele = true;
		}

		public void tick(Frame f)
		{
			int i = (int) (count % CAPACITY);
			frames[i] = f;
			hits[i] = pending;
			// A small drop is eating or drinking; a big one is the bank or a drop, which is not.
			ate[i] = last != null && f.food < last.food && last.food - f.food <= 3 ? last.food - f.food : 0;
			drank[i] = last != null && f.potionDoses < last.potionDoses && last.potionDoses - f.potionDoses <= 2
				? last.potionDoses - f.potionDoses : 0;
			tele[i] = pendingTele ? 1 : 0;
			pending = new ArrayList<>();
			pendingTele = false;
			last = f;
			count++;
		}

		/** The killing blow waits for the next tick; this records the death tick at zero hitpoints. */
		public void deathTick()
		{
			if (last != null)
			{
				tick(last.copyAsDead());
			}
		}

		/** Hitpoints are low: the inquest will start a little before this tick. Later marks keep the first. */
		public void mark()
		{
			if (markedAt < 0 && count > 0)
			{
				markedAt = count - 1;
			}
		}

		public boolean isMarked()
		{
			return markedAt >= 0;
		}

		public void unmark()
		{
			markedAt = -1;
		}

		public void clear()
		{
			count = 0;
			markedAt = -1;
			pending = new ArrayList<>();
			pendingTele = false;
			last = null;
		}

		/** The inquest of the close call that just ended, or null without a mark. */
		public Inquest closeCall(String place, Kit kit, long at)
		{
			if (markedAt < 0 || count == 0)
			{
				return null;
			}
			long first = Math.max(Math.max(0, count - CAPACITY), markedAt - LEAD_TICKS);
			long end = count - 1;
			markedAt = -1;
			if (end - first + 1 > MAX_TICKS)
			{
				long low = first;
				for (long t = first; t <= end; t++)
				{
					if (frames[(int) (t % CAPACITY)].hp < frames[(int) (low % CAPACITY)].hp)
					{
						low = t;
					}
				}
				// Keep the fall into the lowest point and as much of the recovery as fits.
				first = Math.max(first, low - (MAX_TICKS - 12));
				end = Math.min(end, first + MAX_TICKS - 1);
			}
			return cut(Kind.CLOSE_CALL, first, end, place, kit, at);
		}

		/** The inquest of a death: the ticks that led up to it. Null when nothing has been recorded. */
		public Inquest death(String place, Kit kit, long at)
		{
			markedAt = -1;
			if (count == 0)
			{
				return null;
			}
			long end = count - 1;
			long first = Math.max(Math.max(0, count - CAPACITY), end - MAX_TICKS + 1);
			return cut(Kind.DEATH, first, end, place, kit, at);
		}

		private Inquest cut(Kind kind, long first, long end, String place, Kit kit, long at)
		{
			int n = (int) (end - first + 1);
			if (n < 2)
			{
				return null;
			}
			Inquest q = new Inquest();
			q.kind = kind;
			q.at = at;
			q.place = place;
			q.escape = kit == null ? null : kit.escape;
			q.reach = kit == null ? 0 : kit.reach;
			q.graces = kit == null ? new ArrayList<>() : new ArrayList<>(kit.graces);
			q.hp = new int[n];
			q.prayer = new int[n];
			q.prot = new int[n];
			q.food = new int[n];
			q.heal = new int[n];
			q.prayerDoses = new int[n];
			q.ate = new int[n];
			q.drank = new int[n];
			q.tele = new int[n];
			q.wild = new int[n];
			q.flags = new int[n];
			q.foes = new int[n];
			List<Hit> all = new ArrayList<>();
			for (int k = 0; k < n; k++)
			{
				int i = (int) ((first + k) % CAPACITY);
				Frame f = frames[i];
				q.hp[k] = f.hp;
				q.prayer[k] = f.prayer;
				q.prot[k] = f.prot;
				q.food[k] = f.food;
				q.heal[k] = f.heal;
				q.prayerDoses[k] = f.prayerDoses;
				q.ate[k] = ate[i];
				q.drank[k] = drank[i];
				q.tele[k] = tele[i];
				q.wild[k] = f.wild;
				q.flags[k] = f.flags;
				q.foes[k] = f.foes;
				q.max = Math.max(q.max, f.max);
				q.prayerLevel = Math.max(q.prayerLevel, f.prayerLevel);
				for (Hit h : hits[i])
				{
					if (all.size() < MAX_HITS)
					{
						all.add(new Hit(k, h.amount, h.kind != null ? h.kind : h.from == null ? "Unseen" : h.from, h.kind, h.max));
					}
				}
			}
			q.names = new ArrayList<>();
			q.hitTick = new int[all.size()];
			q.hitAmount = new int[all.size()];
			q.hitBy = new int[all.size()];
			q.hitFlags = new int[all.size()];
			for (int k = 0; k < all.size(); k++)
			{
				Hit h = all.get(k);
				int by = q.names.indexOf(h.from);
				if (by < 0)
				{
					by = q.names.size();
					q.names.add(h.from);
				}
				q.hitTick[k] = h.t;
				q.hitAmount[k] = h.amount;
				q.hitBy[k] = by;
				q.hitFlags[k] = (h.kind != null ? OVER_TIME : 0) | (h.max ? MAX_HIT : 0);
			}
			return q.max > 0 ? q : null;
		}
	}
}
