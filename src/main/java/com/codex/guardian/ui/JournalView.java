package com.codex.guardian.ui;

import com.codex.guardian.core.Journal;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
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

	static StackPanel build(Journal journal, boolean oneLife, long now, CodexButton newRun)
	{
		StackPanel stack = new StackPanel(6, 8);
		if (journal == null)
		{
			stack.add(Rows.note("Log in to open this account's journal. Each account keeps its own.", 190));
			return stack;
		}
		stack.add(summary(journal, oneLife, newRun));

		List<Journal.Entry> entries = journal.getEntries();
		stack.add(new SectionHeader("The story so far", String.valueOf(entries.size())));
		Card page = new Card(null);
		page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
		page.setBorder(new EmptyBorder(8, 6, 8, 8));
		for (int i = entries.size() - 1; i >= 0; i--)
		{
			Journal.Entry e = entries.get(i);
			page.add(entry(e, i == entries.size() - 1, i == 0, now));
		}
		stack.add(page);
		return stack;
	}

	private static Card summary(Journal journal, boolean oneLife, CodexButton newRun)
	{
		boolean fallen = journal.isStatusLost() || oneLife && journal.getDeaths() > 0;
		Card card = new Card(new BorderLayout(0, 6)).highlighted(true).accent(fallen ? Theme.BLOOD : Theme.TEAL);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label(fallen ? "THE RUN HAS FALLEN" : "THE RUN LIVES", Theme.small(), fallen ? Theme.BLOOD : Theme.GOLD));
		String began = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(new Date(journal.getRunStartedAt()));
		text.add(Ui.label("Begun " + began, Theme.title(), Theme.GOLD_BRIGHT));
		text.add(Ui.label(Format.duration(journal.getPlayedTicks()) + " played  ·  " + journal.getCloseCalls() + " close calls",
			Theme.small(), Theme.TEXT_MUTED));
		card.add(text, BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		actions.setOpaque(false);
		actions.add(newRun);
		card.add(actions, BorderLayout.SOUTH);
		return card;
	}

	private static JPanel entry(Journal.Entry e, boolean first, boolean last, long now)
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
			? Theme.BLOOD : Theme.TEXT);
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
		row.add(text, BorderLayout.CENTER);
		// The glyph column stretches with the text so the thread reaches the next entry.
		row.setAlignmentX(0f);
		return row;
	}
}
