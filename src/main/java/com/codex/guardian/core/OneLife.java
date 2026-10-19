package com.codex.guardian.core;

/**
 * Whether an account that is not a hardcore ironman should be treated as having one life, such as an
 * account on Hardcore Worlds. The choice is kept per account, so a main and a one-life account on the same
 * RuneLite profile each keep their own.
 *
 * <p>Earlier versions kept one setting for every account. On upgrade, an account that already has a
 * Guardian journal inherits that old setting once, so a run that was being treated as one-life stays so;
 * an account Guardian has never seen starts as an ordinary account until the player says otherwise.
 */
public final class OneLife
{
	/** What to use for this account, and whether to save it now as its own choice. */
	public static final class Decision
	{
		private final boolean oneLife;
		private final boolean save;

		Decision(boolean oneLife, boolean save)
		{
			this.oneLife = oneLife;
			this.save = save;
		}

		public boolean isOneLife()
		{
			return oneLife;
		}

		/** True when the old global setting was just carried over and should be stored for the account. */
		public boolean shouldSave()
		{
			return save;
		}
	}

	private OneLife()
	{
	}

	/**
	 * @param saved        the account's own saved choice ("true" / "false"), or null when it has none yet
	 * @param legacyGlobal the old setting shared by every account
	 * @param hadJournal   whether the account had a journal before this login, meaning it was played under
	 *                     the old setting
	 */
	public static Decision resolve(String saved, boolean legacyGlobal, boolean hadJournal)
	{
		if ("true".equals(saved) || "false".equals(saved))
		{
			return new Decision(Boolean.parseBoolean(saved), false);
		}
		if (legacyGlobal && hadJournal)
		{
			return new Decision(true, true);
		}
		return new Decision(false, false);
	}
}
