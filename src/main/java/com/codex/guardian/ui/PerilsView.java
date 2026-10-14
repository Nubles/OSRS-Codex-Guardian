package com.codex.guardian.ui;

import com.codex.guardian.core.Journal;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The run in numbers: how close it has come, what hurts you most and where. */
final class PerilsView
{
	private PerilsView()
	{
	}

	static StackPanel build(Journal journal)
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
			for (Map.Entry<String, Integer> e : sources)
			{
				stack.add(bar(e.getKey(), e.getValue(), max, total));
			}
		}

		List<Map.Entry<String, Integer>> places = top(journal.getCloseCallsByPlace(), 5);
		if (!places.isEmpty())
		{
			stack.add(new SectionHeader("Where it got close"));
			for (Map.Entry<String, Integer> e : places)
			{
				stack.add(Rows.check(Mark.Kind.WARN, e.getKey(), null, e.getValue() + (e.getValue() == 1 ? " time" : " times")));
			}
		}

		if (journal.getDeaths() > 0 || journal.getDisconnects() > 0)
		{
			stack.add(new SectionHeader("Scars"));
			if (journal.getDeaths() > 0)
			{
				stack.add(Rows.check(Mark.Kind.BAD, "Deaths", null, String.valueOf(journal.getDeaths())));
			}
			if (journal.getDisconnects() > 0)
			{
				stack.add(Rows.check(Mark.Kind.WARN, "Disconnects in danger", null, String.valueOf(journal.getDisconnects())));
			}
		}
		return stack;
	}

	private static Card bar(String name, int value, int max, int total)
	{
		Card card = new Card(new BorderLayout(0, 3));
		card.setBorder(new EmptyBorder(6, 10, 7, 10));
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.add(Ui.label(name, Theme.body(), Theme.TEXT), BorderLayout.CENTER);
		int share = total <= 0 ? 0 : (int) Math.round(value * 100.0 / total);
		line.add(Ui.label(value + " dmg  ·  " + share + "%", Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
		card.add(line, BorderLayout.CENTER);
		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(value / (double) Math.max(1, max), false, false);
		card.add(bar, BorderLayout.SOUTH);
		return card;
	}

	private static List<Map.Entry<String, Integer>> top(Map<String, Integer> map, int limit)
	{
		List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list.subList(0, Math.min(limit, list.size()));
	}
}
