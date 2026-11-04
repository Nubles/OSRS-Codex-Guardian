package com.codex.guardian.core;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regular expressions a player types, made safe to run on the client thread. A pattern is bounded in length and
 * compiled with every error caught; a match runs over a bounded message through a character budget, so a pattern
 * that backtracks without end (such as {@code (a+)+b}) gives up after a fixed amount of work instead of freezing the
 * game. Nothing here throws.
 */
public final class SafeRegex
{
	/** The longest pattern a rule may hold. */
	public static final int MAX_PATTERN = 120;
	/** Only this much of a message is matched; game messages are far shorter. */
	public static final int MAX_INPUT = 400;
	/** Characters a single match may read, counting every re-read while backtracking: well under a millisecond's work. */
	static final int BUDGET = 40_000;

	/** A compiled pattern, or why it could not be. */
	public static final class Compiled
	{
		private final Pattern pattern;
		private final String error;

		Compiled(Pattern pattern, String error)
		{
			this.pattern = pattern;
			this.error = error;
		}

		public boolean isOk()
		{
			return pattern != null;
		}

		/** A short reason the pattern does not read, for the editor; null when it does. */
		public String getError()
		{
			return error;
		}

		Pattern getPattern()
		{
			return pattern;
		}
	}

	/** What a match gave: found, not found, or gave up for taking too long. */
	public enum Outcome
	{
		FOUND,
		NOT_FOUND,
		TOO_SLOW
	}

	private SafeRegex()
	{
	}

	public static Compiled compile(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return new Compiled(null, "Write a pattern");
		}
		if (text.length() > MAX_PATTERN)
		{
			return new Compiled(null, "Keep it under " + MAX_PATTERN + " characters");
		}
		try
		{
			return new Compiled(Pattern.compile(text, Pattern.CASE_INSENSITIVE), null);
		}
		catch (PatternSyntaxException e)
		{
			String why = e.getDescription();
			return new Compiled(null, (why == null ? "Does not read" : why) + (e.getIndex() >= 0 ? " at " + (e.getIndex() + 1) : ""));
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return new Compiled(null, "Does not read");
		}
	}

	/** Looks for the pattern anywhere in the message, within the budget. */
	public static Outcome find(Compiled compiled, String message)
	{
		if (compiled == null || !compiled.isOk() || message == null)
		{
			return Outcome.NOT_FOUND;
		}
		String input = message.length() > MAX_INPUT ? message.substring(0, MAX_INPUT) : message;
		try
		{
			return compiled.getPattern().matcher(new Budgeted(input, new int[]{BUDGET})).find() ? Outcome.FOUND : Outcome.NOT_FOUND;
		}
		catch (OutOfBudget e)
		{
			return Outcome.TOO_SLOW;
		}
		catch (RuntimeException | StackOverflowError e)
		{
			// A pattern that recurses too deep on this message: treated as too slow, so the rule is paused.
			return Outcome.TOO_SLOW;
		}
	}

	private static final class OutOfBudget extends RuntimeException
	{
		OutOfBudget()
		{
			super(null, null, false, false);
		}
	}

	/** The message as the matcher reads it, one character at a time, until the shared budget runs out. */
	private static final class Budgeted implements CharSequence
	{
		private final String text;
		private final int[] budget;

		Budgeted(String text, int[] budget)
		{
			this.text = text;
			this.budget = budget;
		}

		@Override
		public int length()
		{
			return text.length();
		}

		@Override
		public char charAt(int index)
		{
			if (--budget[0] < 0)
			{
				throw new OutOfBudget();
			}
			return text.charAt(index);
		}

		@Override
		public CharSequence subSequence(int start, int end)
		{
			return new Budgeted(text.substring(start, end), budget);
		}

		@Override
		public String toString()
		{
			return text;
		}
	}
}
