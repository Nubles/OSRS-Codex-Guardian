package com.codex.guardian;

import com.codex.guardian.core.Advice;
import com.codex.guardian.core.CloseCallWatch;
import com.codex.guardian.core.DamageLog;
import com.codex.guardian.core.HitMemory;
import com.codex.guardian.core.Journal;
import com.codex.guardian.core.KitPlan;
import com.codex.guardian.core.Lag;
import com.codex.guardian.core.OneLife;
import com.codex.guardian.core.Peril;
import com.codex.guardian.core.PerilAssessor;
import com.codex.guardian.core.PlaceMemory;
import com.codex.guardian.core.Readiness;
import com.codex.guardian.core.Risk;
import com.codex.guardian.core.Situation;
import com.codex.guardian.core.Supplies;
import com.codex.guardian.core.Threat;
import com.codex.guardian.core.TripCheck;
import com.codex.guardian.core.Zone;
import com.codex.guardian.core.Zones;
import com.codex.guardian.overlay.PerilVignette;
import com.codex.guardian.overlay.WardOverlay;
import com.codex.guardian.ui.GuardianPanel;
import com.codex.guardian.ui.Mark;
import com.codex.guardian.ui.Shield;
import com.codex.guardian.ui.Theme;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Codex Guardian",
	description = "A companion for one-life accounts: risk warnings, supply checks, a run journal and death-risk stats",
	tags = {"hardcore", "hcim", "safety", "risk", "warning", "journal", "death", "codex"}
)
public class CodexGuardianPlugin extends Plugin
{
	private static final String JOURNAL_KEY = "journal";
	/** The hardest each monster has hit this account, per profile; kept apart from the journal so it outlives a new run. */
	static final String HITS_KEY = "hitMemory";
	/** A lag warning comes back at most once a minute, so a bad evening is not a stream of notifications. */
	private static final int LAG_COOLDOWN_TICKS = 100;
	/** This account's own one-life choice, "true" or "false"; absent until the account has one. */
	static final String ONE_LIFE_KEY = "oneLifeAccount";
	/** Codex Quartermaster's saved data, read only, for the kit you packed and what your bank holds. */
	static final String QUARTERMASTER_GROUP = "codexquartermaster";
	static final String QUARTERMASTER_KEY = "quarters";
	/** Quartermaster's kits in the shape it documents for other plugins; preferred, with {@code quarters} beside it. */
	static final String QUARTERMASTER_KITS_KEY = "kitsV1";
	/** Last resorts worth pointing out in the bank, by the item ids Quartermaster's bank record uses. */
	private static final int[][] BANKED_GRACES = {{ItemID.RING_OF_LIFE}, {ItemID.JEWL_NECKLACE_OF_PHOENIX}};
	private static final String[] BANKED_GRACE_NAMES = {"Ring of life", "Phoenix necklace"};
	/** Save the journal about once a minute while playing, and at every important moment. */
	private static final int SAVE_TICKS = 100;
	/** The journal's run time and danger time tick constantly; send the panel a fresh copy every half minute. */
	private static final int JOURNAL_PUBLISH_TICKS = 50;
	/** About ten seconds. */
	private static final int BRIEFING_TICKS = 17;
	private static final int PERIL_NOTIFY_COOLDOWN_TICKS = 25;
	private static final int VENOM_THRESHOLD = 1_000_000;
	/** Tele Block's varbit counts down past 100 while it lasts; 100 and under is the immunity after it ends. */
	private static final int TELEBLOCK_ACTIVE_ABOVE = 100;
	/** The ditch warning comes back at most once every five minutes, so skilling near it is not a nag. */
	private static final int APPROACH_COOLDOWN_TICKS = 500;
	private static final EnumSet<WorldType> PVP_WORLDS = EnumSet.of(WorldType.PVP, WorldType.HIGH_RISK, WorldType.DEADMAN);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private Gson gson;

	@Inject
	private CodexGuardianConfig config;

	@Inject
	private PerilVignette vignette;

	private final WardOverlay overlay = new WardOverlay();
	private final DamageLog damage = new DamageLog();
	private final CloseCallWatch closeCalls = new CloseCallWatch();
	private final Lag lag = new Lag();
	private HitMemory hits = new HitMemory();
	private boolean hitsChanged;
	private Lag.State lastLag = Lag.State.STEADY;
	private int lagCooldown;
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);

	private PerilAssessor assessor;
	private GuardianPanel panel;
	private NavigationButton navButton;

	private Journal journal;
	private String journalProfile;
	private boolean journalChanged;
	/** This account's one-life choice; false until a profile is loaded. */
	private boolean accountOneLife;
	/** The kit chosen in Codex Quartermaster and its bank record, or null without one. */
	private KitPlan kitPlan;
	/** Names of the kit's single items, resolved on the client thread when the kit is read. */
	private final Map<Integer, String> kitItemNames = new HashMap<>();
	/** Last resorts in the bank by name, from the bank itself this session or else Quartermaster's record. */
	private final Map<String, Integer> bankGraces = new LinkedHashMap<>();
	private boolean bankSeenThisSession;
	private List<Supplies.Item> lastInventory = Collections.emptyList();
	private List<Supplies.Item> lastWorn = Collections.emptyList();
	private TripCheck trip;
	private boolean tripDirty = true;
	/** The bank just closed; brief the trip check on the next tick if the pack looks like a fight. */
	private boolean tripBriefPending;
	private int ticksSinceSave;
	private int ticksSincePublish;

	private Situation situation = Situation.loggedOut();
	private Peril peril = Peril.calm();
	private Situation shownSituation;
	private Peril.Level shownLevel;
	private int shownScore = -1;
	private Readiness readiness = Readiness.empty();
	private boolean readinessDirty = true;
	private int readinessMaxHp;

	private String lastZoneName;
	private Briefing briefingKind;
	private Zone briefingZone;
	private int briefingTicks;
	private boolean wasNearWilderness;
	private int approachCooldown;
	private int lastWilderness;
	private boolean lastEscapeOk;
	private boolean readinessInWilderness;
	private Peril.Level lastLevel = Peril.Level.CALM;
	private int perilCooldown;
	private boolean lowHitpointsNotified;
	private String lastHitSource;
	private int accountType = -1;
	private int lastCombatLevel = -1;
	/** False until the first tick after login, so the stats the game sends on login are not read as level ups. */
	private boolean settled;

	@Provides
	CodexGuardianConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexGuardianConfig.class);
	}

	@Override
	protected void startUp()
	{
		assessor = new PerilAssessor(config.foodReserve());
		panel = new GuardianPanel(config.foodReserve(), () -> clientThread.invokeLater(this::newRun),
			value -> clientThread.invokeLater(() -> setOneLife(value)));
		navButton = NavigationButton.builder()
			.tooltip("Codex Guardian")
			.icon(Shield.icon(16))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		overlayManager.add(vignette);
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadJournal();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			saveJournal();
			journal = null;
			journalProfile = null;
			forgetProfile();
		});
		overlayManager.remove(overlay);
		overlayManager.remove(vignette);
		clientToolbar.removeNavigation(navButton);
		overlay.setView(null);
		vignette.setActive(false);
		panel = null;
		resetSession();
	}

	private void resetSession()
	{
		damage.clear();
		closeCalls.reset();
		levels.clear();
		situation = Situation.loggedOut();
		peril = Peril.calm();
		shownSituation = null;
		shownLevel = null;
		shownScore = -1;
		readinessDirty = true;
		lastZoneName = null;
		briefingKind = null;
		briefingZone = null;
		briefingTicks = 0;
		wasNearWilderness = false;
		lastWilderness = 0;
		lastEscapeOk = false;
		lastLevel = Peril.Level.CALM;
		lowHitpointsNotified = false;
		lastHitSource = null;
		lag.reset();
		lastLag = Lag.State.STEADY;
		accountType = -1;
		lastCombatLevel = -1;
		settled = false;
		trip = null;
		tripDirty = true;
		tripBriefPending = false;
	}

	/** Forgets everything read for the previous account: its one-life choice, its kit and its bank. */
	private void forgetProfile()
	{
		accountOneLife = false;
		hits = new HitMemory();
		hitsChanged = false;
		kitPlan = null;
		kitItemNames.clear();
		bankGraces.clear();
		bankSeenThisSession = false;
		trip = null;
		tripDirty = true;
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// ConfigManager writes its changes to disk in this same event, before a save queued on the
		// client thread runs, so a save made here has to be sent on by hand or it is never written.
		CompletableFuture<Void> done = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				saveJournal();
				configManager.sendConfig();
			}
			finally
			{
				done.complete(null);
			}
		});
		event.waitFor(done);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.CONNECTION_LOST)
		{
			if (journal != null && situation.isLoggedIn() && (peril.getLevel().atLeast(Peril.Level.DANGER) || !situation.getAttackers().isEmpty()))
			{
				journal.disconnect(place(situation), System.currentTimeMillis());
				journalChanged = true;
				saveJournal();
			}
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			saveJournal();
			resetSession();
			overlay.setView(null);
			vignette.setActive(false);
			publishSituation(Situation.loggedOut(), Peril.calm());
		}
		else if (state == GameState.LOGGED_IN)
		{
			readinessDirty = true;
		}
		else if (state == GameState.LOADING)
		{
			// A region load holds the next tick back on purpose; that pause is not lag.
			lag.skip();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		saveJournal();
		journal = null;
		journalProfile = null;
		forgetProfile();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadJournal();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (QUARTERMASTER_GROUP.equals(event.getGroup())
			&& (QUARTERMASTER_KEY.equals(event.getKey()) || QUARTERMASTER_KITS_KEY.equals(event.getKey())))
		{
			// Quartermaster saved: the kit may have changed. Read it again on the client thread, where it is used.
			clientThread.invokeLater(this::readKit);
			return;
		}
		if (!CodexGuardianConfig.GROUP.equals(event.getGroup()) || JOURNAL_KEY.equals(event.getKey())
			|| ONE_LIFE_KEY.equals(event.getKey()) || HITS_KEY.equals(event.getKey()))
		{
			return;
		}
		int reserve = config.foodReserve();
		// Settings change on the Swing thread; the assessor and the published read belong to the client thread.
		clientThread.invokeLater(() ->
		{
			assessor = new PerilAssessor(reserve);
			shownSituation = null;
		});
		GuardianPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.setFoodReserve(reserve));
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			readinessDirty = true;
		}
		else if (event.getContainerId() == InventoryID.BANK && event.getItemContainer() != null)
		{
			readBankGraces(event.getItemContainer());
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		// Walking away from the bank is the last moment to fix a pack: brief the trip check there.
		// Decided on the next tick, once the pack read includes whatever was withdrawn on the way out.
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload() && settled && config.briefings()
			&& client.getGameState() == GameState.LOGGED_IN)
		{
			tripDirty = true;
			tripBriefPending = true;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int level = event.getLevel();
		Integer previous = levels.put(skill, level);
		if (!settled || previous == null || level <= previous || journal == null)
		{
			return;
		}
		if (level == 99 || level >= 50 && level % 10 == 0)
		{
			journal.milestone("Reached " + level + " " + skill.getName(), level == 99 ? "A skill mastered on one life" : null,
				System.currentTimeMillis());
			journalChanged = true;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player local = client.getLocalPlayer();
		if (local == null || event.getActor() != local)
		{
			return;
		}
		Hitsplat hitsplat = event.getHitsplat();
		int amount = hitsplat.getAmount();
		if (amount <= 0 || !damagesHitpoints(hitsplat.getHitsplatType()))
		{
			return;
		}
		damage.hit(amount);
		String source = sourceOf(hitsplat.getHitsplatType(), local);
		lastHitSource = source;
		rememberHit(hitsplat.getHitsplatType(), amount, local);
		if (journal != null)
		{
			journal.damage(source, amount, place(situation), System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() != client.getLocalPlayer() || journal == null)
		{
			return;
		}
		journal.death(place(situation), lastHitSource, situation.isOneLife(), situation.risk() == Risk.SAFE,
			System.currentTimeMillis());
		journalChanged = true;
		closeCalls.reset();
		damage.clear();
		saveJournal();
		publishJournal();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Player local = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || local == null)
		{
			return;
		}
		if (journal == null)
		{
			loadJournal();
		}
		long now = System.currentTimeMillis();
		damage.tick();
		lag.tick(System.nanoTime() / 1_000_000L);

		Situation s = read(local);
		updateTrip();
		if (tripBriefPending)
		{
			tripBriefPending = false;
			// Only a pack that looks like a fight is briefed: a skiller banking logs is not told about Vorkath.
			if (TripCheck.packedForTrip(readiness))
			{
				brief(Briefing.TRIP, null);
			}
		}
		Peril p = assessor.assess(s);
		situation = s;
		peril = p;

		if (journal != null)
		{
			journal.tick(p.getLevel().atLeast(Peril.Level.DANGER));
			watchStatus(now);
			watchZone(s, now);
			watchCloseCall(s, now);
			watchCombatLevel(s, now);
		}
		watchWilderness(s);
		watchLag(s, now);
		alert(s, p);
		updateOverlays(s, p);
		publishSituation(s, p);

		ticksSincePublish++;
		if (journalChanged || ticksSincePublish >= JOURNAL_PUBLISH_TICKS)
		{
			publishJournal();
		}
		ticksSinceSave++;
		if (ticksSinceSave >= SAVE_TICKS)
		{
			saveJournal();
		}
		settled = true;
	}

	/** Reads this tick's situation from the client. */
	private Situation read(Player local)
	{
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		Situation.Builder b = new Situation.Builder()
			.loggedIn(true)
			.playerName(local.getName())
			.world(client.getWorld())
			.combatLevel(local.getCombatLevel())
			.hitpoints(client.getBoostedSkillLevel(Skill.HITPOINTS), maxHp)
			.prayer(client.getBoostedSkillLevel(Skill.PRAYER), client.getRealSkillLevel(Skill.PRAYER));

		int type = client.getVarbitValue(VarbitID.IRONMAN);
		boolean hardcore = type == 3 || type == 5;
		String mode = modeName(type);
		if (!hardcore && accountOneLife)
		{
			b.mode("One-life account", true);
		}
		else
		{
			b.mode(mode, hardcore);
		}

		int poison = client.getVarpValue(VarPlayerID.POISON);
		b.toxin(poison >= VENOM_THRESHOLD ? Situation.Toxin.VENOM : poison > 0 ? Situation.Toxin.POISON : Situation.Toxin.NONE);

		WorldPoint where = location(local);
		boolean inWilderness = client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1;
		int wild = inWilderness ? wildernessLevel(where) : 0;
		Zone zone = where == null ? null : Zones.forRegion(where.getRegionID());
		if (wild > 0)
		{
			Zone wilderness = Zones.wilderness(wild);
			if (zone == null || !zone.getRisk().atLeast(wilderness.getRisk()))
			{
				zone = wilderness;
			}
		}
		else if (!Collections.disjoint(client.getWorldType(), PVP_WORLDS) && (zone == null || !zone.getRisk().atLeast(Risk.HIGH)))
		{
			zone = Zones.pvpWorld();
		}
		b.zone(zone).wilderness(wild, !inWilderness && where != null && Zones.nearWilderness(where.getX(), where.getY()));
		b.multiCombat(client.getVarbitValue(VarbitID.MULTIWAY_INDICATOR) == 1);
		b.teleblocked(client.getVarbitValue(VarbitID.TELEBLOCK_CYCLES) > TELEBLOCK_ACTIVE_ABOVE);

		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc.getInteracting() == local && !npc.isDead() && npc.getCombatLevel() > 0)
			{
				b.attacker(new Threat(npc.getName(), npc.getCombatLevel(), false, hits.max(npc.getName(), npc.getCombatLevel())));
			}
		}
		b.lag(lag.state(), lag.describe());
		// Outside PvP areas other players "interact" with you just by following or trading.
		if (wild > 0 || zone != null && zone.getKind() == Zone.Kind.PVP_WORLD)
		{
			for (Player other : client.getTopLevelWorldView().players())
			{
				if (other != local && other.getInteracting() == local)
				{
					b.attacker(new Threat(other.getName(), other.getCombatLevel(), true));
				}
			}
		}
		b.damage(damage.pace(), damage.recentWorstTick());

		if (readinessDirty || maxHp != readinessMaxHp || inWilderness != readinessInWilderness)
		{
			lastInventory = items(InventoryID.INV);
			lastWorn = items(InventoryID.WORN);
			readiness = Supplies.assess(lastInventory, lastWorn, maxHp, inWilderness);
			readinessDirty = false;
			tripDirty = true;
			readinessMaxHp = maxHp;
			readinessInWilderness = inWilderness;
		}
		b.readiness(readiness);
		// Read after the pack, so the distance is to the reach of the teleport carried this tick.
		if (wild > 0 && where != null)
		{
			int reach = readiness.getEscapeReach();
			b.distances(reach >= 0 && wild > reach ? Zones.tilesSouthTo(where.getX(), where.getY(), reach) : -1,
				Zones.tilesSouthTo(where.getX(), where.getY(), 0));
		}
		return b.build();
	}

	/**
	 * The Wilderness level as the game shows it, falling back to coordinates. When both are known the
	 * higher wins: reading a level too low is the mistake that tells you a teleport works when it does not.
	 */
	private int wildernessLevel(WorldPoint where)
	{
		int fromCoordinates = where == null ? 0 : Zones.wildernessLevel(where.getX(), where.getY());
		Widget widget = client.getWidget(InterfaceID.PvpIcons.WILDERNESSLEVEL);
		int fromGame = widget == null || widget.isHidden() ? 0 : Zones.parseWildernessLevel(widget.getText());
		return Math.max(1, Math.max(fromCoordinates, fromGame));
	}

	private WorldPoint location(Player local)
	{
		if (client.getTopLevelWorldView().isInstance() && local.getLocalLocation() != null)
		{
			return WorldPoint.fromLocalInstance(client, local.getLocalLocation());
		}
		return local.getWorldLocation();
	}

	private List<Supplies.Item> items(int containerId)
	{
		List<Supplies.Item> list = new ArrayList<>();
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return list;
		}
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ItemComposition def = client.getItemDefinition(item.getId());
			// A noted item cannot be eaten or worn, so it saves no one.
			if (def.getNote() != -1)
			{
				continue;
			}
			list.add(new Supplies.Item(def.getName(), item.getQuantity()));
		}
		return list;
	}

	private static String modeName(int type)
	{
		switch (type)
		{
			case 1:
				return "Ironman";
			case 2:
				return "Ultimate Ironman";
			case 3:
				return "Hardcore Ironman";
			case 4:
				return "Group Ironman";
			case 5:
				return "Hardcore Group Ironman";
			case 6:
				return "Unranked Group Ironman";
			default:
				return "Standard account";
		}
	}

	private static boolean damagesHitpoints(int type)
	{
		switch (type)
		{
			case HitsplatID.HEAL:
			case HitsplatID.BLOCK_ME:
			case HitsplatID.BLOCK_OTHER:
			case HitsplatID.PRAYER_DRAIN:
			case HitsplatID.SANITY_DRAIN:
			case HitsplatID.SANITY_RESTORE:
			case HitsplatID.CYAN_UP:
			case HitsplatID.CYAN_DOWN:
			case HitsplatID.DISEASE_BLOCKED:
				return false;
			default:
				return true;
		}
	}

	/** Who or what a hit on you came from: the toxin, or the strongest thing attacking you. */
	private String sourceOf(int type, Player local)
	{
		switch (type)
		{
			case HitsplatID.POISON:
				return "Poison";
			case HitsplatID.VENOM:
				return "Venom";
			case HitsplatID.DISEASE:
				return "Disease";
			case HitsplatID.BURN:
				return "Burn";
			case HitsplatID.BLEED:
				return "Bleed";
			default:
				break;
		}
		Actor strongest = null;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc.getInteracting() == local && (strongest == null || npc.getCombatLevel() > strongest.getCombatLevel()))
			{
				strongest = npc;
			}
		}
		if (strongest == null)
		{
			for (Player other : client.getTopLevelWorldView().players())
			{
				if (other != local && other.getInteracting() == local)
				{
					return "A player";
				}
			}
		}
		return strongest == null || strongest.getName() == null ? "Unseen" : strongest.getName();
	}

	private void watchStatus(long now)
	{
		int type = client.getVarbitValue(VarbitID.IRONMAN);
		if (type == 3 || type == 5)
		{
			if (!journal.isHardcoreSeen())
			{
				journal.noteHardcore();
				journalChanged = true;
			}
		}
		// Losing hardcore turns HCIM (3) into a regular ironman (1) and HCGIM (5) into a group ironman (4).
		// The journal remembers the account was hardcore, so a loss on mobile or another client is caught at the next login.
		else if ((type == 1 || type == 4) && journal.isHardcoreSeen() && !journal.isStatusLost())
		{
			boolean seenHere = accountType == 3 || accountType == 5;
			journal.statusLost(now, seenHere ? "The account is no longer hardcore"
				: "Noticed at login: the account lost hardcore status while Guardian was not watching");
			journalChanged = true;
			saveJournal();
		}
		accountType = type;
	}

	/**
	 * Remembers a hit when exactly one monster is attacking you and no player is: only then is there no doubt
	 * whose hit it was. Poison, venom, burns and other damage over time belong to no single hit.
	 */
	private void rememberHit(int type, int amount, Player local)
	{
		switch (type)
		{
			case HitsplatID.POISON:
			case HitsplatID.VENOM:
			case HitsplatID.DISEASE:
			case HitsplatID.BURN:
			case HitsplatID.BLEED:
				return;
			default:
				break;
		}
		NPC only = null;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc.getInteracting() == local && !npc.isDead() && npc.getCombatLevel() > 0)
			{
				if (only != null)
				{
					return;
				}
				only = npc;
			}
		}
		if (only == null || only.getName() == null)
		{
			return;
		}
		for (Player other : client.getTopLevelWorldView().players())
		{
			if (other != local && other.getInteracting() == local)
			{
				return;
			}
		}
		if (hits.record(only.getName(), only.getCombatLevel(), amount, place(situation), System.currentTimeMillis()))
		{
			hitsChanged = true;
			// The Perils page lists the hardest hitters; a new record is worth showing.
			journalChanged = true;
		}
	}

	/** Lag that starts while something can hurt you: a notification and a line in the journal, once a minute at most. */
	private void watchLag(Situation s, long now)
	{
		if (lagCooldown > 0)
		{
			lagCooldown--;
		}
		Lag.State state = s.getLag();
		boolean exposed = (!s.getAttackers().isEmpty() || s.getDamagePerTick() > 0 || s.risk().atLeast(Risk.HIGH)
			|| s.getWildernessLevel() > 0) && s.risk() != Risk.SAFE;
		if (settled && exposed && state != Lag.State.STEADY && lastLag == Lag.State.STEADY && lagCooldown == 0)
		{
			lagCooldown = LAG_COOLDOWN_TICKS;
			if (journal != null)
			{
				journal.lag(s.getLagText(), place(s), now);
				journalChanged = true;
			}
			if (config.notifyLag())
			{
				notifier.notify("Guardian: lag in danger (" + s.getLagText().toLowerCase() + ")"
					+ (s.canEscape() ? ". Teleport out while it works." : ""));
			}
		}
		lastLag = state;
	}

	private void watchZone(Situation s, long now)
	{
		Zone zone = s.getZone();
		String name = zone == null ? null : zone.getName();
		if (Objects.equals(name, lastZoneName))
		{
			return;
		}
		lastZoneName = name;
		// The place you log in to is not somewhere you walked into.
		if (zone == null || !settled)
		{
			return;
		}
		boolean risky = zone.getRisk().atLeast(Risk.MEDIUM) || zone.getKind() == Zone.Kind.WILDERNESS
			|| zone.getRisk() == Risk.SAFE;
		if (risky && config.briefings())
		{
			brief(Briefing.ZONE, zone);
		}
		if (journal.zone(zone, s.getWildernessLevel(), now))
		{
			journalChanged = true;
		}
		if (config.notifyZone() && (zone.getRisk().atLeast(Risk.HIGH) || zone.getKind() == Zone.Kind.WILDERNESS))
		{
			notifier.notify("Guardian: entering " + zone.getName() + " (" + zone.getRisk().getLabel().toLowerCase() + " risk)");
		}
	}

	private void watchCloseCall(Situation s, long now)
	{
		CloseCallWatch.Result r = closeCalls.update(s.getHitpoints(), s.getMaxHitpoints(), config.closeCallPercent(), place(s), strongestName(s));
		if (r != null)
		{
			journal.closeCall(r.lowest, s.getMaxHitpoints(), r.place, r.source, now);
			journalChanged = true;
		}
	}

	private void watchCombatLevel(Situation s, long now)
	{
		int level = s.getCombatLevel();
		if (settled && lastCombatLevel > 0 && level > lastCombatLevel && (level % 10 == 0 || level == 126))
		{
			journal.milestone("Combat level " + level, null, now);
			journalChanged = true;
		}
		lastCombatLevel = level;
	}

	/** The ditch warning before you cross, and the moment you walk past the reach of your best teleport. */
	private void watchWilderness(Situation s)
	{
		if (approachCooldown > 0)
		{
			approachCooldown--;
		}
		boolean near = s.isNearWilderness();
		if (near && !wasNearWilderness && settled && approachCooldown == 0 && config.briefings())
		{
			brief(Briefing.APPROACH, null);
			approachCooldown = APPROACH_COOLDOWN_TICKS;
		}
		wasNearWilderness = near;

		int wild = s.getWildernessLevel();
		Readiness r = s.getReadiness();
		boolean escapeOk = r.canEscapeAt(wild);
		if (settled && wild > 0 && lastWilderness > 0 && wild > lastWilderness && lastEscapeOk && !escapeOk)
		{
			if (config.briefings())
			{
				brief(Briefing.LIMIT, null);
			}
			if (config.notifyTeleportLimit())
			{
				notifier.notify("Guardian: level " + wild + ", past the reach of your " + shortName(r.getBestEscape()));
			}
		}
		lastWilderness = wild;
		lastEscapeOk = escapeOk;
	}

	private void brief(Briefing kind, Zone zone)
	{
		briefingKind = kind;
		briefingZone = zone;
		briefingTicks = BRIEFING_TICKS;
	}

	private static String strongestName(Situation s)
	{
		Threat strongest = null;
		for (Threat t : s.getAttackers())
		{
			if (strongest == null || t.getCombatLevel() > strongest.getCombatLevel())
			{
				strongest = t;
			}
		}
		return strongest == null ? null : strongest.isPlayer() ? "a player" : strongest.getName();
	}

	private static String place(Situation s)
	{
		Zone zone = s.getZone();
		if (zone == null)
		{
			return null;
		}
		return zone.getKind() == Zone.Kind.WILDERNESS ? "Wilderness level " + s.getWildernessLevel() : zone.getName();
	}

	private void alert(Situation s, Peril p)
	{
		Peril.Level level = p.getLevel();
		if (perilCooldown > 0)
		{
			perilCooldown--;
		}
		boolean notified = false;
		if (level == Peril.Level.PERIL && lastLevel != Peril.Level.PERIL && perilCooldown == 0 && config.notifyPeril())
		{
			String action = Advice.now(s, p);
			notifier.notify("Guardian: peril. " + p.headline() + (action == null ? "" : ". " + action));
			perilCooldown = PERIL_NOTIFY_COOLDOWN_TICKS;
			notified = true;
		}
		lastLevel = level;

		int max = Math.max(1, s.getMaxHitpoints());
		int percent = s.getHitpoints() * 100 / max;
		int threshold = config.lowHitpointsPercent();
		if (percent <= threshold)
		{
			// Low hitpoints are the point of a safe minigame like Nightmare Zone; saying so every time is noise.
			if (!lowHitpointsNotified && !notified && config.notifyLowHitpoints() && s.risk() != Risk.SAFE)
			{
				notifier.notify("Guardian: hitpoints low (" + s.getHitpoints() + "/" + s.getMaxHitpoints() + ")");
			}
			lowHitpointsNotified = true;
		}
		else if (percent > threshold + 10)
		{
			lowHitpointsNotified = false;
		}
	}

	private void updateOverlays(Situation s, Peril p)
	{
		vignette.setActive(config.perilVignette() && p.getLevel() == Peril.Level.PERIL);

		WardOverlay.Briefing briefing = null;
		if (briefingTicks > 0)
		{
			briefingTicks--;
			briefing = briefing(s);
			if (briefing == null)
			{
				// The moment has passed: you left the place, or stepped back from the ditch.
				briefingTicks = 0;
			}
		}

		boolean showPlaque;
		switch (config.plaqueMode())
		{
			case ALWAYS:
				showPlaque = true;
				break;
			case WARY:
				showPlaque = p.getLevel().atLeast(Peril.Level.WARY);
				break;
			case DANGER:
				showPlaque = p.getLevel().atLeast(Peril.Level.DANGER);
				break;
			default:
				showPlaque = false;
		}
		// A doorway briefing keeps the plaque's action line quiet, but never in peril: getting jumped at the ditch
		// is exactly when the briefing is still on screen and the one line that matters is what to do.
		Readiness r = s.getReadiness();
		String supplies = r.getFoodCount() == 0 ? "No food" : r.getFoodCount() + " food  " + r.getFoodHealing() + " hp";
		boolean escapeOk = s.canEscape();
		String escape = escapeOk ? shortName(r.getBestEscape()) : s.isTeleblocked() ? "Teleblocked"
			: r.hasEscape() ? "Teleport blocked" : "No way out";
		overlay.setView(new WardOverlay.View(p.getLevel(), s.getHitpoints(), s.getMaxHitpoints(), supplies, escape, escapeOk,
			showPlaque, briefing, briefing == null || p.getLevel() == Peril.Level.PERIL ? Advice.now(s, p) : null));
	}

	/** The briefing for the moment Guardian is announcing, or null once it no longer applies. */
	private WardOverlay.Briefing briefing(Situation s)
	{
		if (briefingKind == null)
		{
			return null;
		}
		int wild = s.getWildernessLevel();
		switch (briefingKind)
		{
			case TRIP:
			{
				TripCheck t = trip;
				// Only a known trip is briefed; once you are somewhere dangerous, that place's own briefing takes over.
				if (t == null || t.getDestination() == null && t.getKit() == null || s.inHarmsWay() || wild > 0)
				{
					return null;
				}
				List<WardOverlay.Check> checks = new ArrayList<>();
				for (TripCheck.Line line : t.worst(3))
				{
					checks.add(new WardOverlay.Check(Mark.of(line.getTone()), line.getText()));
				}
				String where = t.getDestination() != null ? t.getDestination() : t.getKit();
				return new WardOverlay.Briefing("BEFORE YOU GO", where, t.isClear() ? "READY" : "CHECK",
					t.isClear() ? Theme.TEAL : Theme.EMBER, checks);
			}
			case APPROACH:
				if (!s.isNearWilderness() && wild == 0)
				{
					return null;
				}
				return new WardOverlay.Briefing("AHEAD", "The Wilderness", "PVP", Theme.EMBER, kitChecks(s));
			case LIMIT:
			{
				if (wild == 0 || s.getReadiness().canEscapeAt(wild))
				{
					return null;
				}
				List<WardOverlay.Check> checks = new ArrayList<>();
				String best = shortName(s.getReadiness().getBestEscape());
				int reach = Math.max(0, s.getReadiness().getEscapeReach());
				checks.add(new WardOverlay.Check(Mark.Kind.BAD, s.isTeleblocked() ? "Teleblocked" : best + " stops at level " + reach));
				checks.add(new WardOverlay.Check(Mark.Kind.WARN, Advice.walkLine(s)));
				checks.addAll(kitChecks(s).subList(0, 1));
				return new WardOverlay.Briefing("LEVEL " + wild, "Past your teleport", "NO ESCAPE", Theme.BLOOD, checks);
			}
			default:
			{
				Zone zone = s.getZone();
				if (zone == null || briefingZone == null || !briefingZone.getName().equals(zone.getName()))
				{
					return null;
				}
				List<WardOverlay.Check> checks;
				if (zone.getRisk() == Risk.SAFE)
				{
					checks = Collections.singletonList(new WardOverlay.Check(Mark.Kind.GOOD, "Deaths here are normally safe"));
				}
				else
				{
					checks = new ArrayList<>(kitChecks(s));
					// A place you nearly died at gets a reminder at its door: the first thing remembered there.
					List<String> memory = PlaceMemory.lines(journal, hits, PlaceMemory.placeOf(s));
					if (!memory.isEmpty())
					{
						checks.add(new WardOverlay.Check(Mark.Kind.WARN, memory.get(0)));
					}
				}
				String risk = zone.getKind() == Zone.Kind.WILDERNESS ? "LEVEL " + wild : zone.getRisk().getLabel().toUpperCase() + " RISK";
				return new WardOverlay.Briefing("ENTERING", zone.getName(), risk, Theme.risk(zone.getRisk()), checks);
			}
		}
	}

	/** Food, a way out that works here, and a saving grace: the three things worth checking at a doorway. */
	private List<WardOverlay.Check> kitChecks(Situation s)
	{
		List<WardOverlay.Check> checks = new ArrayList<>();
		Readiness r = s.getReadiness();
		int reserve = config.foodReserve();
		checks.add(new WardOverlay.Check(r.getFoodCount() == 0 ? Mark.Kind.BAD : r.getFoodHealing() >= reserve ? Mark.Kind.GOOD : Mark.Kind.WARN,
			r.getFoodCount() == 0 ? "No food" : r.getFoodCount() + " food, " + r.getFoodHealing() + " hp"));
		int wild = s.getWildernessLevel();
		boolean escape = s.canEscape();
		checks.add(new WardOverlay.Check(escape ? Mark.Kind.GOOD : Mark.Kind.BAD,
			escape ? shortName(r.getBestEscape()) : s.isTeleblocked() ? "Teleblocked"
				: r.hasEscape() ? "Teleports blocked here" : "No teleport"));
		List<String> graces = r.getSavingGraces();
		checks.add(new WardOverlay.Check(graces.isEmpty() || wild > 30 ? Mark.Kind.NONE : Mark.Kind.GOOD,
			graces.isEmpty() ? "No saving grace" : wild > 30 ? graces.get(0) + " fails above 30" : graces.get(0)));
		return checks;
	}

	/** The moments that open the plaque into a briefing. */
	private enum Briefing
	{
		TRIP,
		ZONE,
		APPROACH,
		LIMIT
	}

	private static String shortName(String item)
	{
		return Advice.shortName(item);
	}

	/** Sends the live read to the panel, but only when something it shows has changed. */
	private void publishSituation(Situation s, Peril p)
	{
		if (s.equals(shownSituation) && p.getLevel() == shownLevel && p.getScore() == shownScore)
		{
			return;
		}
		shownSituation = s;
		shownLevel = p.getLevel();
		shownScore = p.getScore();
		GuardianPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(s, p));
		}
	}

	/** Hands the panel a private copy of the journal, so Swing never reads it while the game writes it. */
	private void publishJournal()
	{
		ticksSincePublish = 0;
		journalChanged = false;
		GuardianPanel target = panel;
		if (target == null)
		{
			return;
		}
		Journal copy = journal == null ? null : gson.fromJson(gson.toJson(journal), Journal.class).repair();
		HitMemory hitCopy = journal == null ? null : gson.fromJson(gson.toJson(hits), HitMemory.class).repair();
		SwingUtilities.invokeLater(() -> target.updateJournal(copy, hitCopy));
	}

	private void loadJournal()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return;
		}
		Journal loaded = null;
		String json = configManager.getConfiguration(CodexGuardianConfig.GROUP, profile, JOURNAL_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				loaded = gson.fromJson(json, Journal.class);
			}
			catch (RuntimeException e)
			{
				loaded = null;
			}
		}
		journal = loaded == null ? Journal.fresh(System.currentTimeMillis()) : loaded.repair();
		journalProfile = profile;
		hits = loadHits(profile);
		hitsChanged = false;
		OneLife.Decision oneLife = OneLife.resolve(configManager.getConfiguration(CodexGuardianConfig.GROUP, profile, ONE_LIFE_KEY),
			config.oneLife(), loaded != null);
		accountOneLife = oneLife.isOneLife();
		if (oneLife.shouldSave())
		{
			configManager.setConfiguration(CodexGuardianConfig.GROUP, profile, ONE_LIFE_KEY, String.valueOf(accountOneLife));
		}
		readKit();
		journalChanged = true;
		if (loaded == null)
		{
			saveJournal();
		}
		publishJournal();
	}

	private void saveJournal()
	{
		ticksSinceSave = 0;
		if (journal == null || journalProfile == null)
		{
			return;
		}
		configManager.setConfiguration(CodexGuardianConfig.GROUP, journalProfile, JOURNAL_KEY, gson.toJson(journal));
		if (hitsChanged)
		{
			hitsChanged = false;
			configManager.setConfiguration(CodexGuardianConfig.GROUP, journalProfile, HITS_KEY, gson.toJson(hits));
		}
	}

	private HitMemory loadHits(String profile)
	{
		String json = configManager.getConfiguration(CodexGuardianConfig.GROUP, profile, HITS_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				HitMemory loaded = gson.fromJson(json, HitMemory.class);
				if (loaded != null)
				{
					return loaded.repair();
				}
			}
			catch (RuntimeException e)
			{
				// A damaged record is not worth a crash; the memory starts again.
			}
		}
		return new HitMemory();
	}

	/** Called on the client thread when the player marks this account one-life, or not, in the panel. */
	private void setOneLife(boolean value)
	{
		if (journalProfile == null)
		{
			return;
		}
		accountOneLife = value;
		configManager.setConfiguration(CodexGuardianConfig.GROUP, journalProfile, ONE_LIFE_KEY, String.valueOf(value));
		// The next tick's read carries the new mode to the panel and the journal summary.
		shownSituation = null;
		publishJournal();
	}

	/**
	 * Reads the kit chosen in Codex Quartermaster, if it is installed and one is chosen, and resolves the
	 * names of the single items it packs. Without Quartermaster this leaves no plan, and the trip check falls
	 * back to Guardian's own food, teleport and last-resort checks.
	 */
	private void readKit()
	{
		String profile = configManager.getRSProfileKey();
		KitPlan plan = null;
		if (profile != null)
		{
			JsonObject kits = readJson(configManager.getConfiguration(QUARTERMASTER_GROUP, profile, QUARTERMASTER_KITS_KEY));
			JsonObject quarters = readJson(configManager.getConfiguration(QUARTERMASTER_GROUP, profile, QUARTERMASTER_KEY));
			try
			{
				plan = KitPlan.from(kits, quarters);
			}
			catch (RuntimeException e)
			{
				plan = null;
			}
		}
		kitPlan = plan;
		kitItemNames.clear();
		if (plan != null && client.getGameState() == GameState.LOGGED_IN)
		{
			for (KitPlan.Need need : plan.getNeeds())
			{
				if (need.getItemId() >= 0)
				{
					kitItemNames.put(need.getItemId(), client.getItemDefinition(need.getItemId()).getName());
				}
			}
		}
		if (!bankSeenThisSession)
		{
			bankGraces.clear();
			if (plan != null)
			{
				for (int i = 0; i < BANKED_GRACES.length; i++)
				{
					long count = 0;
					for (int id : BANKED_GRACES[i])
					{
						count += plan.inBank(id);
					}
					if (count > 0)
					{
						bankGraces.put(BANKED_GRACE_NAMES[i], (int) Math.min(Integer.MAX_VALUE, count));
					}
				}
			}
		}
		tripDirty = true;
	}

	/** Another plugin's saved JSON object, or null when it is missing or not an object. */
	private JsonObject readJson(String json)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, JsonObject.class);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Counts the last resorts in the bank while it is open; this beats Quartermaster's older record. */
	private void readBankGraces(ItemContainer bank)
	{
		Map<String, Integer> found = new LinkedHashMap<>();
		for (Item item : bank.getItems())
		{
			if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			for (int i = 0; i < BANKED_GRACES.length; i++)
			{
				for (int id : BANKED_GRACES[i])
				{
					if (item.getId() == id)
					{
						found.merge(BANKED_GRACE_NAMES[i], item.getQuantity(), Integer::sum);
					}
				}
			}
		}
		bankGraces.clear();
		bankGraces.putAll(found);
		bankSeenThisSession = true;
		tripDirty = true;
	}

	/** Works the trip check out again when the pack, the kit or the bank changed, and hands it to the panel. */
	private void updateTrip()
	{
		if (!tripDirty)
		{
			return;
		}
		tripDirty = false;
		KitPlan plan = kitPlan;
		Zone destination = TripCheck.destinationFor(plan == null ? null : plan.getName());
		TripCheck next = TripCheck.build(destination, plan, kitItemNames, lastInventory, lastWorn, readiness, bankGraces,
			config.foodReserve()).withMemory(destination == null ? null
				: PlaceMemory.lines(journal, hits, destination.getName()));
		trip = next;
		GuardianPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.updateTrip(next));
		}
	}

	private void newRun()
	{
		if (journalProfile == null)
		{
			return;
		}
		journal = Journal.fresh(System.currentTimeMillis());
		closeCalls.reset();
		saveJournal();
		publishJournal();
	}
}
