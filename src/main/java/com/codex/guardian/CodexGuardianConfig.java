package com.codex.guardian;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CodexGuardianConfig.GROUP)
public interface CodexGuardianConfig extends Config
{
	String GROUP = "codexguardian";

	enum PlaqueMode
	{
		ALWAYS("Always"),
		WARY("Wary or worse"),
		DANGER("Danger or worse"),
		NEVER("Never");

		private final String label;

		PlaqueMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(
		name = "In game",
		description = "What Guardian draws over the game",
		position = 0
	)
	String inGame = "inGame";

	@ConfigSection(
		name = "Alerts",
		description = "When Guardian sends a notification",
		position = 1
	)
	String alerts = "alerts";

	@ConfigSection(
		name = "Run",
		description = "How Guardian reads your account and your run",
		position = 2
	)
	String run = "run";

	@ConfigItem(
		keyName = "plaqueMode",
		name = "Show plaque",
		description = "When to show the plaque with your level, hitpoints, food and way out",
		position = 1,
		section = inGame
	)
	default PlaqueMode plaqueMode()
	{
		return PlaqueMode.WARY;
	}

	@ConfigItem(
		keyName = "briefings",
		name = "Entry briefings",
		description = "When you walk into a dangerous place, the plaque briefly checks your food, way out and saving grace",
		position = 2,
		section = inGame
	)
	default boolean briefings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "perilVignette",
		name = "Peril glow",
		description = "Pulses the edges of the game view red while Guardian reads peril",
		position = 3,
		section = inGame
	)
	default boolean perilVignette()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyPeril",
		name = "Notify in peril",
		description = "Sends a notification when Guardian's read rises to peril",
		position = 1,
		section = alerts
	)
	default boolean notifyPeril()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyLowHitpoints",
		name = "Notify on low hitpoints",
		description = "Sends a notification when your hitpoints fall to the threshold below",
		position = 2,
		section = alerts
	)
	default boolean notifyLowHitpoints()
	{
		return true;
	}

	@Range(min = 5, max = 90)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "lowHitpointsPercent",
		name = "Low hitpoints at",
		description = "The share of your hitpoints that counts as low",
		position = 3,
		section = alerts
	)
	default int lowHitpointsPercent()
	{
		return 35;
	}

	@ConfigItem(
		keyName = "notifyZone",
		name = "Notify on dangerous places",
		description = "Sends a notification when you enter a high risk place or the Wilderness",
		position = 4,
		section = alerts
	)
	default boolean notifyZone()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyTeleportLimit",
		name = "Notify past teleport range",
		description = "Sends a notification when you walk deeper into the Wilderness than your best teleport works",
		position = 5,
		section = alerts
	)
	default boolean notifyTeleportLimit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyLag",
		name = "Notify lag in danger",
		description = "Sends a notification when game ticks start arriving late while something can hurt you",
		position = 6,
		section = alerts
	)
	default boolean notifyLag()
	{
		return true;
	}

	/**
	 * The old setting shared by every account. One-life is now chosen per account in the panel's Journal tab;
	 * this stays, hidden, so accounts played under it carry it over once. See {@code OneLife}.
	 */
	@ConfigItem(
		keyName = "oneLife",
		name = "One-life account",
		description = "Replaced by the per-account choice in the Guardian panel's Journal tab",
		position = 1,
		section = run,
		hidden = true
	)
	default boolean oneLife()
	{
		return false;
	}

	@Range(min = 0, max = 600)
	@ConfigItem(
		keyName = "foodReserve",
		name = "Food for danger",
		description = "How many hitpoints of food Guardian wants you carrying into danger",
		position = 2,
		section = run
	)
	default int foodReserve()
	{
		return 80;
	}

	@Range(min = 5, max = 50)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "closeCallPercent",
		name = "Close call at",
		description = "Falling to this share of your hitpoints and surviving counts as a close call in the journal",
		position = 3,
		section = run
	)
	default int closeCallPercent()
	{
		return 20;
	}
}
