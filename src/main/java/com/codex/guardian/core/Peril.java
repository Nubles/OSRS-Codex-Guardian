package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Guardian's read on how close you are to losing your one life, with the reasons behind it. */
public final class Peril
{
	public enum Level
	{
		CALM("Calm"),
		WARY("Wary"),
		DANGER("Danger"),
		PERIL("Peril");

		private final String label;

		Level(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		public boolean atLeast(Level other)
		{
			return ordinal() >= other.ordinal();
		}
	}

	/** How a reason should be read: good news, a note, a warning or a threat to your life. */
	public enum Tone
	{
		GOOD,
		NOTE,
		WARN,
		BAD
	}

	public static final class Reason
	{
		private final String text;
		private final Tone tone;
		private final int weight;

		Reason(String text, Tone tone, int weight)
		{
			this.text = text;
			this.tone = tone;
			this.weight = weight;
		}

		public String getText()
		{
			return text;
		}

		public Tone getTone()
		{
			return tone;
		}

		public int getWeight()
		{
			return weight;
		}
	}

	private final Level level;
	private final int score;
	private final List<Reason> reasons;
	/** Seconds until your hitpoints run out at the current pace, or -1 when you are not losing any. */
	private final int secondsLeft;

	Peril(Level level, int score, List<Reason> reasons, int secondsLeft)
	{
		this.level = level;
		this.score = score;
		List<Reason> sorted = new ArrayList<>(reasons);
		sorted.sort((a, b) -> Integer.compare(Math.abs(b.weight), Math.abs(a.weight)));
		this.reasons = Collections.unmodifiableList(sorted);
		this.secondsLeft = secondsLeft;
	}

	public static Peril calm()
	{
		return new Peril(Level.CALM, 0, Collections.emptyList(), -1);
	}

	public Level getLevel()
	{
		return level;
	}

	/** 0 to 100. */
	public int getScore()
	{
		return score;
	}

	public List<Reason> getReasons()
	{
		return reasons;
	}

	public int getSecondsLeft()
	{
		return secondsLeft;
	}

	/** The single line that explains the level best. */
	public String headline()
	{
		for (Tone tone : new Tone[]{Tone.BAD, Tone.WARN})
		{
			for (Reason r : reasons)
			{
				if (r.tone == tone)
				{
					return r.text;
				}
			}
		}
		return level == Level.CALM ? "All quiet. Guardian is keeping watch." : reasons.isEmpty() ? "" : reasons.get(0).text;
	}
}
