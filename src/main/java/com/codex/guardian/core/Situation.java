package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Everything Guardian knows about this moment, read once per game tick on the client thread
 * and then handed to the assessor, the panel and the overlays without touching the game again.
 */
public final class Situation
{
	public enum Toxin
	{
		NONE,
		POISON,
		VENOM
	}

	private final boolean loggedIn;
	private final String playerName;
	private final String mode;
	private final boolean oneLife;
	private final int world;
	private final int combatLevel;
	private final int hitpoints;
	private final int maxHitpoints;
	private final int prayer;
	private final int maxPrayer;
	/** Seconds of prayer left at the measured drain, counting restore doses carried, or -1 when it is not draining. */
	private final int prayerLeft;
	private final Toxin toxin;
	private final Zone zone;
	private final int wildernessLevel;
	private final boolean nearWilderness;
	private final boolean multiCombat;
	private final boolean teleblocked;
	private final List<Threat> attackers;
	private final double damagePerTick;
	private final int recentMaxHit;
	private final Readiness readiness;
	private final int tilesToReach;
	private final int tilesToDitch;
	private final Lag.State lag;
	private final String lagText;

	private Situation(Builder b)
	{
		loggedIn = b.loggedIn;
		playerName = b.playerName;
		mode = b.mode;
		oneLife = b.oneLife;
		world = b.world;
		combatLevel = b.combatLevel;
		hitpoints = b.hitpoints;
		maxHitpoints = b.maxHitpoints;
		prayer = b.prayer;
		maxPrayer = b.maxPrayer;
		prayerLeft = b.prayerLeft;
		toxin = b.toxin;
		zone = b.zone;
		wildernessLevel = b.wildernessLevel;
		nearWilderness = b.nearWilderness;
		multiCombat = b.multiCombat;
		teleblocked = b.teleblocked;
		attackers = Collections.unmodifiableList(new ArrayList<>(b.attackers));
		damagePerTick = b.damagePerTick;
		recentMaxHit = b.recentMaxHit;
		readiness = b.readiness;
		tilesToReach = b.tilesToReach;
		tilesToDitch = b.tilesToDitch;
		lag = b.lag;
		lagText = b.lagText;
	}

	public static Situation loggedOut()
	{
		return new Builder().build();
	}

	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	public String getPlayerName()
	{
		return playerName;
	}

	/** A short name for the account type, such as "Hardcore Ironman". */
	public String getMode()
	{
		return mode;
	}

	public boolean isOneLife()
	{
		return oneLife;
	}

	public int getWorld()
	{
		return world;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public int getHitpoints()
	{
		return hitpoints;
	}

	public int getMaxHitpoints()
	{
		return maxHitpoints;
	}

	public double hitpointsFraction()
	{
		return maxHitpoints <= 0 ? 1 : Math.max(0, Math.min(1, hitpoints / (double) maxHitpoints));
	}

	public int getPrayer()
	{
		return prayer;
	}

	public int getMaxPrayer()
	{
		return maxPrayer;
	}

	/** Seconds of prayer left at the drain Guardian measured, restore doses included, or -1 when it is not draining. */
	public int getPrayerLeft()
	{
		return prayerLeft;
	}

	public Toxin getToxin()
	{
		return toxin;
	}

	/** The named place you are in, or null for ordinary ground. */
	public Zone getZone()
	{
		return zone;
	}

	public int getWildernessLevel()
	{
		return wildernessLevel;
	}

	public boolean isNearWilderness()
	{
		return nearWilderness;
	}

	public boolean isMultiCombat()
	{
		return multiCombat;
	}

	/** True while a player's Tele Block stops every teleport, wherever you stand. */
	public boolean isTeleblocked()
	{
		return teleblocked;
	}

	/** Whether a teleport you carry would get you out from here, right now. */
	public boolean canEscape()
	{
		return !teleblocked && readiness.canEscapeAt(wildernessLevel);
	}

	public List<Threat> getAttackers()
	{
		return attackers;
	}

	/** Average damage taken per game tick over the last few ticks. */
	public double getDamagePerTick()
	{
		return damagePerTick;
	}

	/** The biggest single hit taken in the last half minute or so. */
	public int getRecentMaxHit()
	{
		return recentMaxHit;
	}

	public Readiness getReadiness()
	{
		return readiness;
	}

	/**
	 * Tiles to walk due south until your best teleport works again, or -1 when that is unknown or does not
	 * apply (no teleport, already within its reach, or a place off Guardian's Wilderness map).
	 */
	public int getTilesToReach()
	{
		return tilesToReach;
	}

	/** Tiles to walk due south to leave the Wilderness, or -1 outside it or off its map. */
	public int getTilesToDitch()
	{
		return tilesToDitch;
	}

	/** How steadily game ticks are arriving; see {@link Lag}. */
	public Lag.State getLag()
	{
		return lag;
	}

	/** The lag in a few words, such as "3 late ticks"; "Steady" when there is none. */
	public String getLagText()
	{
		return lagText;
	}

	/**
	 * What the attackers on you have hit you for before, as one figure: the hardest hitter's best, plus the next
	 * hardest's when two are on you in multi-combat, since two hits can land on one tick there. In single-way combat
	 * only one can be attacking you at a time. 0 when none of them has hit you before.
	 */
	public int rememberedWorst()
	{
		int first = 0;
		int second = 0;
		for (Threat t : attackers)
		{
			int max = t.getKnownMax();
			if (max > first)
			{
				second = first;
				first = max;
			}
			else if (max > second)
			{
				second = max;
			}
		}
		return multiCombat ? first + second : first;
	}

	public Risk risk()
	{
		return zone == null ? Risk.LOW : zone.getRisk();
	}

	/** True when anything around you is trying to hurt you or the place itself is dangerous. */
	public boolean inHarmsWay()
	{
		return !attackers.isEmpty() || damagePerTick > 0 || risk().atLeast(Risk.MEDIUM);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Situation))
		{
			return false;
		}
		Situation s = (Situation) o;
		return loggedIn == s.loggedIn && oneLife == s.oneLife && world == s.world && combatLevel == s.combatLevel
			&& hitpoints == s.hitpoints && maxHitpoints == s.maxHitpoints && prayer == s.prayer && maxPrayer == s.maxPrayer
			&& prayerLeft == s.prayerLeft
			&& wildernessLevel == s.wildernessLevel && nearWilderness == s.nearWilderness && multiCombat == s.multiCombat
			&& teleblocked == s.teleblocked && tilesToReach == s.tilesToReach && tilesToDitch == s.tilesToDitch
			&& Double.compare(damagePerTick, s.damagePerTick) == 0 && recentMaxHit == s.recentMaxHit
			&& Objects.equals(playerName, s.playerName) && Objects.equals(mode, s.mode) && toxin == s.toxin
			&& Objects.equals(zone, s.zone) && attackers.equals(s.attackers) && Objects.equals(readiness, s.readiness)
			&& lag == s.lag && Objects.equals(lagText, s.lagText);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(loggedIn, playerName, mode, oneLife, world, combatLevel, hitpoints, maxHitpoints, prayer,
			maxPrayer, prayerLeft, toxin, zone, wildernessLevel, nearWilderness, multiCombat, teleblocked, attackers, damagePerTick, recentMaxHit,
			readiness, tilesToReach, tilesToDitch, lag, lagText);
	}

	public static final class Builder
	{
		private boolean loggedIn;
		private String playerName;
		private String mode = "Standard account";
		private boolean oneLife;
		private int world;
		private int combatLevel = 3;
		private int hitpoints = 10;
		private int maxHitpoints = 10;
		private int prayer = 1;
		private int maxPrayer = 1;
		private int prayerLeft = -1;
		private Toxin toxin = Toxin.NONE;
		private Zone zone;
		private int wildernessLevel;
		private boolean nearWilderness;
		private boolean multiCombat;
		private boolean teleblocked;
		private int tilesToReach = -1;
		private int tilesToDitch = -1;
		private final List<Threat> attackers = new ArrayList<>();
		private double damagePerTick;
		private int recentMaxHit;
		private Readiness readiness = Readiness.empty();
		private Lag.State lag = Lag.State.STEADY;
		private String lagText = "Steady";

		public Builder loggedIn(boolean value)
		{
			loggedIn = value;
			return this;
		}

		public Builder playerName(String value)
		{
			playerName = value;
			return this;
		}

		public Builder mode(String value, boolean oneLife)
		{
			mode = value;
			this.oneLife = oneLife;
			return this;
		}

		public Builder world(int value)
		{
			world = value;
			return this;
		}

		public Builder combatLevel(int value)
		{
			combatLevel = value;
			return this;
		}

		public Builder hitpoints(int current, int max)
		{
			hitpoints = current;
			maxHitpoints = max;
			return this;
		}

		public Builder prayer(int current, int max)
		{
			prayer = current;
			maxPrayer = max;
			return this;
		}

		/** Seconds of prayer left with restores, or -1 when prayer is not draining. */
		public Builder prayerLeft(int seconds)
		{
			prayerLeft = seconds;
			return this;
		}

		public Builder toxin(Toxin value)
		{
			toxin = value;
			return this;
		}

		public Builder zone(Zone value)
		{
			zone = value;
			return this;
		}

		public Builder wilderness(int level, boolean near)
		{
			wildernessLevel = level;
			nearWilderness = near;
			return this;
		}

		public Builder multiCombat(boolean value)
		{
			multiCombat = value;
			return this;
		}

		public Builder teleblocked(boolean value)
		{
			teleblocked = value;
			return this;
		}

		public Builder attacker(Threat threat)
		{
			attackers.add(threat);
			return this;
		}

		public Builder damage(double perTick, int recentMax)
		{
			damagePerTick = perTick;
			recentMaxHit = recentMax;
			return this;
		}

		/** Walking distances south: to your best teleport's reach and out of the Wilderness, -1 when unknown. */
		public Builder distances(int toReach, int toDitch)
		{
			tilesToReach = toReach;
			tilesToDitch = toDitch;
			return this;
		}

		public Builder readiness(Readiness value)
		{
			readiness = value;
			return this;
		}

		public Builder lag(Lag.State state, String text)
		{
			lag = state == null ? Lag.State.STEADY : state;
			lagText = text == null ? "Steady" : text;
			return this;
		}

		public Situation build()
		{
			return new Situation(this);
		}
	}
}
