package com.codex.guardian.ui;

import com.codex.guardian.core.Inquest;
import com.codex.guardian.core.Inquests;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The Inquest page: one death or close call taken apart tick by tick, with the verdict, the tick it went past saving,
 * and what the player had in hand; below it, the other inquests kept for this account, to open any of them.
 */
final class InquestView
{
	private static final int TEXT = 178;

	private InquestView()
	{
	}

	/**
	 * @param selected the time of the inquest to open, or -1 for the newest
	 * @param onSelect called with an inquest's time when its row is clicked
	 */
	static StackPanel build(Inquests inquests, boolean loggedIn, long selected, long now, LongConsumer onSelect)
	{
		StackPanel stack = new StackPanel(6, 8);
		if (inquests == null || inquests.isEmpty())
		{
			stack.add(empty(loggedIn));
			return stack;
		}
		List<Inquest> list = inquests.getList();
		Inquest open = list.get(list.size() - 1);
		for (Inquest q : list)
		{
			if (q.getAt() == selected)
			{
				open = q;
			}
		}
		int place = list.indexOf(open);
		stack.add(new SectionHeader("Inquest", (list.size() - place) + " of " + list.size()));
		stack.add(head(open, now));
		stack.add(verdict(open));
		stack.add(brink(open));

		stack.add(new SectionHeader(open.getKind() == Inquest.Kind.DEATH ? "What would have saved you" : "What you had in hand"));
		List<JComponent> rows = new ArrayList<>();
		for (Inquest.Finding f : open.findings())
		{
			rows.add(Rows.sentence(mark(f.getTone()), f.getText(), 160));
		}
		stack.add(Rows.ledger(rows));

		if (list.size() > 1)
		{
			stack.add(new SectionHeader("Every inquest", String.valueOf(list.size())));
			for (int i = list.size() - 1; i >= 0; i--)
			{
				stack.add(row(list.get(i), list.get(i) == open, now, onSelect));
			}
		}
		stack.add(Rows.note("Read afterwards only. Nothing here runs during a fight.", 190));
		return stack;
	}

	private static Card empty(boolean loggedIn)
	{
		Card card = new Card(new BorderLayout(0, 4)).highlighted(true);
		card.setBorder(new EmptyBorder(10, 12, 12, 12));
		JPanel text = column();
		text.add(Ui.label("INQUEST", Theme.small(), Theme.GOLD));
		text.add(Ui.wrapped("When a close call ends or a death comes, Guardian freezes the last thirty seconds here: "
			+ "every hit, your prayer, every bite of food, and what you had in hand that could have saved you.",
			Theme.body(), Theme.TEXT, 182));
		JLabel own = Ui.wrapped(loggedIn ? "No close calls yet on this account." : "Log in to see this account's inquests.",
			Theme.small(), Theme.TEXT_MUTED, 182);
		own.setBorder(new EmptyBorder(6, 0, 0, 0));
		text.add(own);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	/** The title, when it was, the chart and its key. */
	private static Card head(Inquest q, long now)
	{
		boolean death = q.getKind() == Inquest.Kind.DEATH;
		Card card = new Card(new BorderLayout(0, 6)).highlighted(true);
		if (death)
		{
			card.accent(Theme.BLOOD);
		}
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		top.add(Ui.label(death ? "DEATH" : "CLOSE CALL", Theme.small(), death ? Theme.BLOOD : Theme.EMBER), BorderLayout.WEST);
		top.add(Ui.label(Format.ago(q.getAt(), now), Theme.small(), Theme.TEXT_FAINT), BorderLayout.EAST);
		top.setAlignmentX(0f);
		JPanel text = column();
		text.add(top);
		JLabel title = Ui.wrapped(q.title(), Theme.title(), Theme.TEXT, TEXT);
		title.setAlignmentX(0f);
		text.add(title);
		card.add(text, BorderLayout.NORTH);
		card.add(new InquestChart(q), BorderLayout.CENTER);
		card.add(key(), BorderLayout.SOUTH);
		return card;
	}

	/** What the chart's marks mean, in one quiet line. */
	private static JComponent key()
	{
		JPanel key = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
		key.setOpaque(false);
		key.add(Ui.label("hp", Theme.small(), Theme.GOLD));
		key.add(Ui.label("prayer", Theme.small(), Theme.TEAL));
		key.add(Ui.label("hit", Theme.small(), Theme.BLOOD));
		key.add(Ui.label("max hit", Theme.small(), Theme.EMBER));
		JLabel lanes = Ui.wrapped("Lanes below: a protection prayer on, food (teal) or a potion (gold), a teleport tried.",
			Theme.small(), Theme.TEXT_FAINT, TEXT);
		JPanel both = column();
		key.setAlignmentX(0f);
		lanes.setAlignmentX(0f);
		both.add(key);
		both.add(lanes);
		return both;
	}

	private static Card verdict(Inquest q)
	{
		Card card = new Card(new BorderLayout(0, 2));
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		JPanel text = column();
		text.add(Ui.label("VERDICT", Theme.small(), Theme.GOLD));
		JLabel body = Ui.wrapped(q.verdict(), Theme.body(), Theme.TEXT, TEXT);
		body.setAlignmentX(0f);
		text.add(body);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private static Card brink(Inquest q)
	{
		boolean death = q.getKind() == Inquest.Kind.DEATH;
		String line = q.brinkLine();
		Card card = new Card(new BorderLayout(0, 2));
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		if (line != null)
		{
			card.accent(Theme.BLOOD);
		}
		JPanel text = column();
		text.add(Ui.label(death ? "PAST SAVING" : "ON THE BRINK", Theme.small(), line != null ? Theme.BLOOD : Theme.TEXT_MUTED));
		JLabel body = Ui.wrapped(line != null ? line : "It never came within one hit of the end.", Theme.small(), Theme.TEXT_MUTED, TEXT);
		body.setAlignmentX(0f);
		text.add(body);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	/** One kept inquest, to open with a click. */
	private static Card row(Inquest q, boolean open, long now, LongConsumer onSelect)
	{
		boolean death = q.getKind() == Inquest.Kind.DEATH;
		Mark.Kind kind = death ? Mark.Kind.BAD : Mark.Kind.WARN;
		String detail = q.fallDamage() > 0 ? "Took " + q.fallDamage() + " in " + q.fallTicks() + (q.fallTicks() == 1 ? " tick" : " ticks")
			: "No hits seen";
		Card card = Rows.line(new Mark(kind, 12), q.title(), detail + " · " + Format.ago(q.getAt(), now), null,
			open ? Theme.GOLD : null);
		if (open)
		{
			card.highlighted(true);
		}
		else if (onSelect != null)
		{
			long at = q.getAt();
			card.onClick(() -> onSelect.accept(at));
			card.setToolTipText("Open this inquest");
		}
		return card;
	}

	static Mark.Kind mark(Inquest.Tone tone)
	{
		switch (tone)
		{
			case UNUSED:
				return Mark.Kind.WARN;
			case LACKING:
				return Mark.Kind.BAD;
			case HELD:
				return Mark.Kind.GOOD;
			default:
				return Mark.Kind.NOTE;
		}
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setMinimumSize(new Dimension(10, 10));
		return p;
	}
}
