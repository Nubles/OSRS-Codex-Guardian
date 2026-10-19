package com.codex.guardian.ui;

import com.codex.guardian.core.Journal;
import com.codex.guardian.core.Replay;
import com.codex.guardian.core.Situation;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Function;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The run journal: a summary of the run and every moment worth remembering, newest first. */
final class JournalView
{
	private static final int TEXT = 150;

	private JournalView()
	{
	}

	static StackPanel build(Journal journal, Situation situation, long now, CodexButton newRun, CodexButton oneLifeToggle)
	{
		return build(journal, situation, now, newRun, oneLifeToggle, -1);
	}

	/** @param worth the account's worth from Codex Ledger, or -1 without it; shown on the run's seal */
	static StackPanel build(Journal journal, Situation situation, long now, CodexButton newRun, CodexButton oneLifeToggle,
		long worth)
	{
		return build(journal, situation, now, newRun, oneLifeToggle, worth, null);
	}

	/**
	 * @param fallenButton makes the "Mark as fallen" button under a minigame death on a Hardcore World, or null for
	 *                     none
	 */
	static StackPanel build(Journal journal, Situation situation, long now, CodexButton newRun, CodexButton oneLifeToggle,
		long worth, Function<Journal.Entry, CodexButton> fallenButton)
	{
		return build(journal, situation, now, newRun, oneLifeToggle, worth, fallenButton, null, null);
	}

	/**
	 * @param share     the seal card's save and copy buttons, or null for none
	 * @param shareNote what became of the last save or copy, or null
	 */
	static StackPanel build(Journal journal, Situation situation, long now, CodexButton newRun, CodexButton oneLifeToggle,
		long worth, Function<Journal.Entry, CodexButton> fallenButton, JComponent share, String shareNote)
	{
		boolean oneLife = situation.isOneLife();
		StackPanel stack = new StackPanel(6, 8);
		if (journal == null)
		{
			stack.add(waiting());
			return stack;
		}
		stack.add(summary(journal, oneLife, worth, now, newRun, share, shareNote));
		if (situation.isLoggedIn())
		{
			stack.add(mode(situation, oneLifeToggle));
		}

		List<Journal.Entry> entries = journal.getEntries();
		stack.add(new SectionHeader("The story so far", String.valueOf(entries.size())));
		Card page = new Card(null);
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setBorder(new EmptyBorder(8, 6, 8, 8));
		for (int i = entries.size() - 1; i >= 0; i--)
		{
			Journal.Entry e = entries.get(i);
			page.add(entry(e, i == entries.size() - 1, i == 0, now, fallenButton));
		}
		stack.add(page);
		return stack;
	}

	/** Logged out: what the journal opens on, so the page is a promise rather than a blank. */
	private static Card waiting()
	{
		Card card = new Card(new BorderLayout(0, 4)).highlighted(true);
		card.setBorder(new EmptyBorder(10, 12, 12, 12));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label("THE RUN'S SEAL", Theme.small(), Theme.GOLD));
		text.add(Ui.wrapped("Log in and the journal opens on this account's seal: a metal earned by every hour on one life, "
			+ "bronze to dragon, with the close calls that tested it.", Theme.body(), Theme.TEXT, 182));
		JLabel own = Ui.wrapped("Each account keeps its own journal.", Theme.small(), Theme.TEXT_MUTED, 182);
		own.setBorder(new EmptyBorder(6, 0, 0, 0));
		text.add(own);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	/** The run's seal, with what belongs to the run as a whole under it: sharing it, and starting over. */
	private static JPanel summary(Journal journal, boolean oneLife, long worth, long now, CodexButton newRun, JComponent share,
		String shareNote)
	{
		JPanel holder = new JPanel(new BorderLayout(0, 4));
		holder.setOpaque(false);
		holder.add(new RunSeal(journal, oneLife, worth, now), BorderLayout.NORTH);
		JPanel actions = new JPanel(new BorderLayout(6, 0));
		actions.setOpaque(false);
		if (share != null)
		{
			actions.add(share, BorderLayout.WEST);
		}
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		right.setOpaque(false);
		right.add(newRun);
		actions.add(right, BorderLayout.CENTER);
		holder.add(actions, BorderLayout.CENTER);
		if (shareNote != null)
		{
			holder.add(Ui.wrapped(shareNote, Theme.small(), Theme.TEAL, 200), BorderLayout.SOUTH);
		}
		return holder;
	}

	/**
	 * How Guardian treats this account: a hardcore account is one-life by its nature; any other account can be
	 * marked one-life here, such as one on Hardcore Worlds. The choice belongs to this account alone.
	 */
	private static Card mode(Situation s, CodexButton toggle)
	{
		Card card = new Card(new BorderLayout(6, 4));
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		boolean hardcore = toggle == null;
		text.add(Ui.label(s.isOneLife() ? "ONE LIFE" : "ORDINARY ACCOUNT", Theme.small(), s.isOneLife() ? Theme.GOLD : Theme.TEXT_MUTED));
		String line = hardcore ? "Hardcore World".equals(s.getMode()) ? "On a Hardcore World. A death ends the run."
			: "Recognised as " + s.getMode()
			: s.isOneLife() ? "Marked one-life by you. A death ends the run." : "A death is journaled, not the end of the run.";
		text.add(Ui.wrapped(line, Theme.small(), Theme.TEXT_MUTED, 190));
		card.add(text, BorderLayout.CENTER);
		if (toggle != null)
		{
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			actions.setOpaque(false);
			actions.add(toggle);
			card.add(actions, BorderLayout.SOUTH);
		}
		return card;
	}

	private static JPanel entry(Journal.Entry e, boolean first, boolean last, long now,
		Function<Journal.Entry, CodexButton> fallenButton)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JPanel glyph = new JPanel(new BorderLayout());
		glyph.setOpaque(false);
		glyph.add(new EntryGlyph(e.getType(), first, last), BorderLayout.CENTER);
		row.add(glyph, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBorder(new EmptyBorder(2, 0, last ? 0 : 8, 0));
		JPanel head = new JPanel(new BorderLayout(4, 0));
		head.setOpaque(false);
		JLabel title = Ui.label(e.getTitle(), Theme.body(), e.getType() == Journal.Type.DEATH || e.getType() == Journal.Type.STATUS_LOST
			? Theme.BLOOD : e.getType() == Journal.Type.UNCONFIRMED_DEATH ? Theme.EMBER : Theme.TEXT);
		title.setToolTipText(e.getTitle());
		head.add(title, BorderLayout.CENTER);
		head.add(Ui.label(Format.ago(e.getAt(), now), Theme.small(), Theme.TEXT_FAINT), BorderLayout.EAST);
		head.setAlignmentX(0f);
		text.add(head);
		if (e.getDetail() != null && !e.getDetail().isEmpty())
		{
			JLabel detail = Ui.wrapped(e.getDetail(), Theme.small(), Theme.TEXT_MUTED, TEXT);
			detail.setAlignmentX(0f);
			text.add(detail);
		}
		Replay replay = e.getReplay();
		if (replay != null)
		{
			JPanel strip = new JPanel(new BorderLayout());
			strip.setOpaque(false);
			strip.setBorder(new EmptyBorder(4, 0, 2, 0));
			strip.add(new ReplayStrip(replay, TEXT), BorderLayout.CENTER);
			strip.setAlignmentX(0f);
			strip.setMaximumSize(new java.awt.Dimension(TEXT, ReplayStrip.HEIGHT + 6));
			text.add(strip);
			JLabel line = Ui.wrapped(replay.summary(), Theme.small(), Theme.TEXT_FAINT, TEXT);
			line.setAlignmentX(0f);
			text.add(line);
		}
		if (e.getType() == Journal.Type.UNCONFIRMED_DEATH)
		{
			// Nobody can yet confirm a minigame death is safe on a Hardcore World, so the run is not called fallen.
			JLabel why = Ui.wrapped("On a Hardcore World this may not count. If the game ended your run, mark it.",
				Theme.small(), Theme.TEXT_FAINT, TEXT);
			why.setAlignmentX(0f);
			text.add(why);
			CodexButton button = fallenButton == null ? null : fallenButton.apply(e);
			if (button != null)
			{
				JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
				actions.setOpaque(false);
				actions.setBorder(new EmptyBorder(4, 0, 0, 0));
				actions.add(button);
				actions.setAlignmentX(0f);
				text.add(actions);
			}
		}
		row.add(text, BorderLayout.CENTER);
		// The glyph column stretches with the text so the thread reaches the next entry.
		row.setAlignmentX(0f);
		return row;
	}
}
