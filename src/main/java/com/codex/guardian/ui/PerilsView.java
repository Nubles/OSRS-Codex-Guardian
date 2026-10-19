package com.codex.guardian.ui;

import com.codex.guardian.core.HitMemory;
import com.codex.guardian.core.Journal;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The run in numbers: how close it has come, what hurts you most and where. */
final class PerilsView
{
	private PerilsView()
	{
	}

	/** Hardest hitters listed. */
	private static final int HITTERS = 6;

	static StackPanel build(Journal journal)
	{
		return build(journal, null);
	}

	static StackPanel build(Journal journal, HitMemory hits)
	{
		StackPanel stack = new StackPanel(6, 8);
		if (journal == null)
		{
			stack.add(Rows.note("Log in to see this account's run in numbers.", 190));
			return stack;
		}

		JPanel tiles = new JPanel(new GridLayout(2, 2, 6, 6));
		tiles.setOpaque(false);
		tiles.add(new StatTile(String.valueOf(journal.getCloseCalls()), "Close calls",
			journal.getCloseCalls() == 0 ? "None yet" : "All survived", Theme.EMBER));
		int lowest = journal.getLowestHitpoints();
		tiles.add(new StatTile(lowest < 0 ? "-" : lowest + " hp", "Lowest",
			lowest < 0 ? "Never been close" : journal.getLowestPlace(), Theme.BLOOD));
		Journal.Hit hit = journal.getBiggestHit();
		tiles.add(new StatTile(hit == null ? "-" : String.valueOf(hit.getAmount()), "Biggest hit",
			hit == null ? "Untouched" : hit.getSource(), Theme.GOLD_BRIGHT));
		long played = Math.max(1, journal.getPlayedTicks());
		int share = (int) Math.round(journal.getDangerTicks() * 100.0 / played);
		tiles.add(new StatTile(Format.duration(journal.getDangerTicks()), "In danger", share + "% of the run", Theme.TEAL));
		stack.add(tiles);

		List<Map.Entry<String, Integer>> sources = top(journal.getDamageBySource(), 6);
		stack.add(new SectionHeader("What hurts you most"));
		if (sources.isEmpty())
		{
			stack.add(Rows.note("Nothing has drawn blood yet.", 190));
		}
		else
		{
			int max = sources.get(0).getValue();
			int total = 0;
			for (int v : journal.getDamageBySource().values())
			{
				total += v;
			}
			List<JComponent> rows = new ArrayList<>();
			for (Map.Entry<String, Integer> e : sources)
			{
				rows.add(bar(e.getKey(), e.getValue(), max, total));
			}
			stack.add(Rows.ledger(rows));
		}

		if (hits != null && hits.size() > 0)
		{
			stack.add(new SectionHeader("Hardest hitters", "remembered"));
			List<JComponent> rows = new ArrayList<>();
			for (HitMemory.Record r : hits.hardest(HITTERS))
			{
				rows.add(hitter(r));
			}
			stack.add(Rows.ledger(rows));
			stack.add(Rows.note("When one of these is on you, Guardian warns before your hitpoints fall under its best hit.", 190));
		}

		List<Map.Entry<String, Integer>> places = top(journal.getCloseCallsByPlace(), 5);
		if (!places.isEmpty())
		{
			stack.add(new SectionHeader("Where it got close"));
			List<JComponent> rows = new ArrayList<>();
			for (Map.Entry<String, Integer> e : places)
			{
				rows.add(Rows.fact(Mark.Kind.WARN, e.getKey(), e.getValue() + (e.getValue() == 1 ? " time" : " times"), Theme.EMBER));
			}
			stack.add(Rows.ledger(rows));
		}

		if (journal.getDeaths() > 0 || journal.getDisconnects() > 0 || journal.getSafeDeaths() > 0 || journal.getLagSpikes() > 0)
		{
			stack.add(new SectionHeader("Scars"));
			if (journal.getDeaths() > 0)
			{
				stack.add(Rows.check(Mark.Kind.BAD, "Deaths", null, String.valueOf(journal.getDeaths())));
			}
			if (journal.getSafeDeaths() > 0)
			{
				stack.add(Rows.check(Mark.Kind.NOTE, "Safe deaths", "In minigames, at no cost", String.valueOf(journal.getSafeDeaths())));
			}
			if (journal.getDisconnects() > 0)
			{
				stack.add(Rows.check(Mark.Kind.WARN, "Disconnects in danger", null, String.valueOf(journal.getDisconnects())));
			}
			if (journal.getLagSpikes() > 0)
			{
				stack.add(Rows.check(Mark.Kind.WARN, "Lag in danger", "Survived", String.valueOf(journal.getLagSpikes())));
			}
		}
		return stack;
	}

	/** A monster, its level and where it hit hardest, with that hit in large type. */
	private static JComponent hitter(HitMemory.Record r)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(5, 0, 5, 0));
		JPanel text = new JPanel(new BorderLayout(0, 1));
		text.setOpaque(false);
		javax.swing.JLabel name = Ui.label(r.getName(), Theme.body(), Theme.TEXT);
		name.setToolTipText(r.getName() + ", level " + r.getLevel());
		text.add(name, BorderLayout.NORTH);
		String where = r.getPlace() == null ? "Level " + r.getLevel() : "Level " + r.getLevel() + "  ·  " + r.getPlace();
		javax.swing.JLabel sub = Ui.label(where, Theme.small(), Theme.TEXT_MUTED);
		sub.setToolTipText(where);
		text.add(sub, BorderLayout.SOUTH);
		text.setMinimumSize(new java.awt.Dimension(10, 10));
		text.setPreferredSize(new java.awt.Dimension(10, text.getPreferredSize().height));
		row.add(text, BorderLayout.CENTER);
		JPanel value = new JPanel(new BorderLayout());
		value.setOpaque(false);
		value.add(Ui.label(String.valueOf(r.getMax()), Theme.title(), Theme.BLOOD), BorderLayout.NORTH);
		value.add(Ui.label("max", Theme.small(), Theme.TEXT_FAINT), BorderLayout.SOUTH);
		row.add(value, BorderLayout.EAST);
		row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static JComponent bar(String name, int value, int max, int total)
	{
		JPanel card = new JPanel(new BorderLayout(0, 3));
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(6, 0, 7, 0));
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.add(Ui.label(name, Theme.body(), Theme.TEXT), BorderLayout.CENTER);
		int share = total <= 0 ? 0 : (int) Math.round(value * 100.0 / total);
		line.add(Ui.label(value + " dmg  ·  " + share + "%", Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
		card.add(line, BorderLayout.CENTER);
		ProgressBar bar = new ProgressBar(4).ramp(Theme.mix(Theme.EMBER, Theme.GOLD_DIM, 0.4), Theme.BLOOD);
		bar.setProgress(value / (double) Math.max(1, max), false, false);
		card.add(bar, BorderLayout.SOUTH);
		card.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private static List<Map.Entry<String, Integer>> top(Map<String, Integer> map, int limit)
	{
		List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list.subList(0, Math.min(limit, list.size()));
	}
}
