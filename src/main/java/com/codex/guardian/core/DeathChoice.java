package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What stands on each side of a Hardcore World's Reset or Transfer choice, taken at the moment of the death: the
 * life's levels and quests, the items a death keeps, and the bank as last seen. Plain data, saved per account so the
 * card is still there if the player logs out at the choice; it goes when Guardian sees which way they chose.
 *
 * <p>The rules it shows are the wiki's (Hardcore_Worlds): Reset "will reset ALL progress on the player's character,
 * including quests, skills, items" and "the 3-4 items that were protected upon death", with "a 3-day cooldown on
 * receiving GP from the Stronghold of Security", and "Resetting can be done an infinite amount of times". Transfer
 * "will remove the player's character from Hardcore Worlds, with no option to return" and lets them "keep their
 * protected items" "as well as any items in their bank and other storage facilities". Either way "any items that
 * would usually be sent to the player's gravestone will be permanently deleted".
 */
public final class DeathChoice
{
	/** Kept-item names saved at most, so a damaged save stays small. */
	static final int MAX_KEPT = 5;
	static final int VERSION = 1;

	int version = VERSION;
	long at;
	/** Total level when the run fell; a lower total later on a Hardcore World means a Reset. */
	int totalLevel;
	int questPoints;
	long playedTicks;
	int closeCalls;
	/** True once the death is known to have ended the run (the game asked, or the place is on the unsafe list). */
	boolean confirmed;
	List<String> kept = new ArrayList<>();
	long keptValue;
	/** The bank's worth at Grand Exchange prices, or -1 when the bank was never seen. */
	long bankValue = -1;
	int bankKinds = -1;
	/** Where and to what, as the journal tells the death. */
	String cause;
	/** The inquest of this death, by its time, or -1 without one. */
	long inquestAt = -1;

	/** The choice for a death now, with the life as it stood. */
	public static DeathChoice of(long at, int totalLevel, int questPoints, long playedTicks, int closeCalls, KeptOnDeath kept,
		long bankValue, int bankKinds, String cause, long inquestAt)
	{
		DeathChoice c = new DeathChoice();
		c.at = at;
		c.totalLevel = totalLevel;
		c.questPoints = questPoints;
		c.playedTicks = playedTicks;
		c.closeCalls = closeCalls;
		if (kept != null)
		{
			c.kept = new ArrayList<>(kept.getKept());
			c.keptValue = kept.getKeptValue();
		}
		c.bankValue = bankValue;
		c.bankKinds = bankKinds;
		c.cause = cause;
		c.inquestAt = inquestAt;
		return c.repair();
	}

	/** Reads a saved choice; null with nothing saved, damage, or a newer version this one does not read. */
	public static DeathChoice load(com.google.gson.Gson gson, String json)
	{
		SavedJson.Read<DeathChoice> read = SavedJson.read(gson, json, DeathChoice.class);
		DeathChoice c = read.getValue();
		if (c == null || c.version != VERSION || c.at <= 0)
		{
			return null;
		}
		return c.repair();
	}

	DeathChoice repair()
	{
		totalLevel = Math.max(0, totalLevel);
		questPoints = Math.max(0, questPoints);
		playedTicks = Math.max(0, playedTicks);
		closeCalls = Math.max(0, closeCalls);
		keptValue = Math.max(0, keptValue);
		bankValue = Math.max(-1, bankValue);
		bankKinds = Math.max(-1, bankKinds);
		if (kept == null)
		{
			kept = new ArrayList<>();
		}
		kept.removeIf(k -> k == null || k.isEmpty());
		while (kept.size() > MAX_KEPT)
		{
			kept.remove(kept.size() - 1);
		}
		if (cause != null && cause.length() > 80)
		{
			cause = cause.substring(0, 80);
		}
		return this;
	}

	/** Takes the items kept and the confirmation from another read of the same death. */
	public void keepFrom(DeathChoice other)
	{
		kept = new ArrayList<>(other.kept);
		keptValue = other.keptValue;
		confirmed = other.confirmed;
		repair();
	}

	/** The run is known to have fallen: the card shows. */
	public void confirm()
	{
		confirmed = true;
	}

	/**
	 * True when the character is back where a new one starts: its total level is lower than when the run fell. Real
	 * levels never go down in play, so on the same account and a Hardcore World this only happens after a Reset.
	 */
	public boolean looksReset(int totalLevelNow)
	{
		return totalLevelNow > 0 && totalLevel > 0 && totalLevelNow < totalLevel;
	}

	public boolean isConfirmed()
	{
		return confirmed;
	}

	public long getAt()
	{
		return at;
	}

	public int getTotalLevel()
	{
		return totalLevel;
	}

	public int getQuestPoints()
	{
		return questPoints;
	}

	public long getPlayedTicks()
	{
		return playedTicks;
	}

	public int getCloseCalls()
	{
		return closeCalls;
	}

	public List<String> getKept()
	{
		return Collections.unmodifiableList(kept);
	}

	public long getKeptValue()
	{
		return keptValue;
	}

	public long getBankValue()
	{
		return bankValue;
	}

	public int getBankKinds()
	{
		return bankKinds;
	}

	public String getCause()
	{
		return cause;
	}

	public long getInquestAt()
	{
		return inquestAt;
	}
}
