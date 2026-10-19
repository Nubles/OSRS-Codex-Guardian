package com.codex.guardian.ui;

import com.codex.guardian.core.HitMemory;
import com.codex.guardian.core.Journal;
import com.codex.guardian.core.Peril;
import com.codex.guardian.core.PlaceMemory;
import com.codex.guardian.core.Situation;
import com.codex.guardian.core.TripCheck;
import java.util.function.Consumer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The Guardian side panel: the hitpoint crest, then three pages. Ward is the live read,
 * Journal is the story of the run, and Perils is the run in numbers.
 */
public class GuardianPanel extends PluginPanel
{
	private static final String[] TABS = {"ward", "journal", "perils"};
	private static final String CONFIRM = "Click again to start over";

	private final WardCrest crest = new WardCrest();
	private final Segmented tabs = new Segmented(true, "Ward", "Journal", "Perils");
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane ward = scroller();
	private final JScrollPane journalView = scroller();
	private final JScrollPane perils = scroller();
	private final PerilGauge gauge = new PerilGauge();
	private final Runnable onNewRun;
	private final Consumer<Boolean> onOneLife;
	private final Timer confirmTimeout;

	private Situation situation = Situation.loggedOut();
	private Peril peril = Peril.calm();
	private Journal journal;
	private HitMemory hits;
	private TripCheck trip;
	private int foodReserve;
	private boolean confirming;
	private boolean active;
	private boolean wardStale;
	private boolean journalStale;
	/** Called on the Swing thread with the time of the unconfirmed death the player marked as fallen. */
	private java.util.function.LongConsumer onMarkFallen = at -> {};
	/** The unconfirmed death waiting for a second click to be marked as fallen, or -1. */
	private long markingAt = -1;
	private long worth = -1;

	/**
	 * @param onNewRun  called on the Swing thread once the player confirms a fresh run
	 * @param onOneLife called on the Swing thread when the player marks this account one-life (true) or not
	 */
	public GuardianPanel(int foodReserve, Runnable onNewRun, Consumer<Boolean> onOneLife)
	{
		super(false);
		this.foodReserve = foodReserve;
		this.onNewRun = onNewRun;
		this.onOneLife = onOneLife;
		this.confirmTimeout = new Timer(4000, e -> setConfirming(false));
		confirmTimeout.setRepeats(false);

		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		content.setOpaque(false);
		content.add(ward, TABS[0]);
		content.add(journalView, TABS[1]);
		content.add(perils, TABS[2]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(tab -> cards.show(content, TABS[tab]));
		rebuildWard();
		rebuildJournal();
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return scroll;
	}

	/** Called on the Swing thread whenever the live read changes. */
	public void update(Situation situation, Peril peril)
	{
		boolean modeChanged = situation.isOneLife() != this.situation.isOneLife()
			|| !java.util.Objects.equals(situation.getMode(), this.situation.getMode());
		this.situation = situation;
		if (modeChanged)
		{
			journalChanged();
		}
		this.peril = peril;
		crest.update(situation, peril, journal);
		// In a fight the read changes every tick; rebuilding a page nobody is looking at is wasted work.
		if (active || isShowing())
		{
			rebuildWard();
		}
		else
		{
			wardStale = true;
		}
	}

	@Override
	public void onActivate()
	{
		active = true;
		if (wardStale)
		{
			rebuildWard();
		}
		if (journalStale)
		{
			rebuildJournal();
		}
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	/** Called on the Swing thread with a private copy of the journal whenever it changes. */
	public void updateJournal(Journal journal)
	{
		updateJournal(journal, hits);
	}

	/** Called on the Swing thread with private copies of the journal and the hit memory whenever they change. */
	public void updateJournal(Journal journal, HitMemory hits)
	{
		this.journal = journal;
		this.hits = hits;
		crest.update(situation, peril, journal);
		journalChanged();
	}

	/** Where a click on "Mark as fallen" goes, with the time of the death it was on. */
	public void setOnMarkFallen(java.util.function.LongConsumer onMarkFallen)
	{
		this.onMarkFallen = onMarkFallen == null ? at -> {} : onMarkFallen;
	}

	/** Called on the Swing thread with the account's worth from Codex Ledger, or -1 when there is none. */
	public void updateWorth(long worth)
	{
		if (this.worth != worth)
		{
			this.worth = worth;
			journalChanged();
		}
	}

	/** The journal pages are rebuilt now if they can be seen, otherwise the next time the panel opens. */
	private void journalChanged()
	{
		if (active || isShowing())
		{
			rebuildJournal();
		}
		else
		{
			journalStale = true;
		}
	}

	/** Called on the Swing thread with a fresh trip check whenever the pack, the kit or the bank changes. */
	public void updateTrip(TripCheck trip)
	{
		this.trip = trip;
		if (active || isShowing())
		{
			rebuildWard();
		}
		else
		{
			wardStale = true;
		}
	}

	public void setFoodReserve(int foodReserve)
	{
		this.foodReserve = foodReserve;
		rebuildWard();
	}

	private void rebuildWard()
	{
		wardStale = false;
		replace(ward, WardView.build(situation, peril, gauge, foodReserve, trip,
			PlaceMemory.lines(journal, hits, PlaceMemory.placeOf(situation))));
	}

	private void rebuildJournal()
	{
		journalStale = false;
		replace(journalView, JournalView.build(journal, situation, System.currentTimeMillis(), newRunButton(), oneLifeButton(), worth,
			this::fallenButton));
		replace(perils, PerilsView.build(journal, hits));
	}

	/** The per-account one-life switch, or null for a hardcore account, which is one-life by its nature. */
	private CodexButton oneLifeButton()
	{
		if (!situation.isLoggedIn() || situation.getMode() != null && situation.getMode().startsWith("Hardcore"))
		{
			return null;
		}
		boolean on = situation.isOneLife();
		return new CodexButton(on ? "Not one-life" : "Mark one-life", false, () -> onOneLife.accept(!on));
	}

	/** "Mark as fallen" under an unconfirmed death; like a new run, it takes a second click. */
	private CodexButton fallenButton(Journal.Entry entry)
	{
		long at = entry.getAt();
		boolean asking = markingAt == at;
		return new CodexButton(asking ? "Click again: the run fell" : "Mark as fallen", asking, () ->
		{
			if (markingAt != at)
			{
				markingAt = at;
				confirmTimeout.restart();
				rebuildJournal();
				return;
			}
			markingAt = -1;
			confirmTimeout.stop();
			rebuildJournal();
			onMarkFallen.accept(at);
		});
	}

	private CodexButton newRunButton()
	{
		return new CodexButton(confirming ? CONFIRM : "Start a new run", confirming, () ->
		{
			if (!confirming)
			{
				setConfirming(true);
				return;
			}
			setConfirming(false);
			onNewRun.run();
		});
	}

	private void setConfirming(boolean value)
	{
		confirming = value;
		if (!value)
		{
			markingAt = -1;
		}
		if (value)
		{
			confirmTimeout.restart();
		}
		else
		{
			confirmTimeout.stop();
		}
		rebuildJournal();
	}

	private static void replace(JScrollPane scroll, JComponent view)
	{
		int position = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(view);
		scroll.revalidate();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(Theme.INK);
		g.fillRect(0, 0, getWidth(), getHeight());
	}
}
