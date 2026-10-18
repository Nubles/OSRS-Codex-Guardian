package com.codex.guardian.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Compact card lines: a verdict mark, a name, a quiet second line and an optional value. */
public final class Rows
{
	private Rows()
	{
	}

	public static Card check(Mark.Kind kind, String title, String detail, String value)
	{
		return line(new Mark(kind, 14), title, detail, value == null ? null : Ui.label(value, Theme.small(), Mark.color(kind)),
			kind == Mark.Kind.BAD ? Theme.BLOOD : null);
	}

	/** A verdict with a sentence that may run to two lines. */
	public static Card reason(Mark.Kind kind, String text)
	{
		Card card = new Card(new BorderLayout(8, 0));
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		if (kind == Mark.Kind.BAD)
		{
			card.accent(Theme.BLOOD);
		}
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(new EmptyBorder(1, 0, 0, 0));
		holder.add(new Mark(kind, 14), BorderLayout.NORTH);
		card.add(holder, BorderLayout.WEST);
		card.add(Ui.wrapped(text, Theme.body(), Theme.TEXT, 160), BorderLayout.CENTER);
		return card;
	}

	public static Card line(JComponent lead, String title, String detail, JComponent trailing, Color accent)
	{
		Card card = new Card(new BorderLayout(8, 0));
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		if (accent != null)
		{
			card.accent(accent);
		}
		if (lead != null)
		{
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.setBorder(new EmptyBorder(1, 0, 0, 0));
			holder.add(lead, BorderLayout.NORTH);
			card.add(holder, BorderLayout.WEST);
		}
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JLabel name = Ui.label(title, Theme.body(), Theme.TEXT);
		name.setToolTipText(title);
		text.add(name);
		if (detail != null && !detail.isEmpty())
		{
			JLabel sub = Ui.label(detail, Theme.small(), Theme.TEXT_MUTED);
			sub.setToolTipText(detail);
			text.add(sub);
		}
		// Long names clip with an ellipsis rather than push the value off the card.
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
		card.add(text, BorderLayout.CENTER);
		if (trailing != null)
		{
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(trailing, BorderLayout.NORTH);
			card.add(holder, BorderLayout.EAST);
		}
		return card;
	}

	/**
	 * One card holding several compact rows split by hairlines, so a list of short facts reads as one
	 * ledger instead of a tower of cards.
	 */
	public static Card ledger(List<JComponent> rows)
	{
		Card card = new Card(null);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(new EmptyBorder(2, 8, 2, 8));
		for (int i = 0; i < rows.size(); i++)
		{
			JComponent row = rows.get(i);
			row.setAlignmentX(0f);
			if (i > 0)
			{
				row.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, Theme.STROKE_SOFT), row.getBorder()));
			}
			card.add(row);
		}
		return card;
	}

	/** A ledger row: verdict mark, a name, and a right-aligned value that clips before it collides. */
	public static JComponent fact(Mark.Kind kind, String title, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(5, 0, 5, 0));
		row.add(new Mark(kind, 12), BorderLayout.WEST);
		JLabel name = Ui.label(title, Theme.body(), Theme.TEXT);
		JLabel val = Ui.label(value == null ? "" : value, Theme.small(), valueColor == null ? Theme.TEXT_MUTED : valueColor);
		val.setHorizontalAlignment(SwingConstants.RIGHT);
		val.setToolTipText(value);
		val.setMinimumSize(new Dimension(10, 10));
		val.setPreferredSize(new Dimension(10, val.getPreferredSize().height));
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		line.add(name, BorderLayout.WEST);
		line.add(val, BorderLayout.CENTER);
		row.add(line, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** A ledger row whose sentence may wrap to two lines. */
	public static JComponent sentence(Mark.Kind kind, String text, int width)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(5, 0, 5, 0));
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(new EmptyBorder(1, 0, 0, 0));
		holder.add(new Mark(kind, 12), BorderLayout.NORTH);
		row.add(holder, BorderLayout.WEST);
		row.add(Ui.wrapped(text, Theme.body(), Theme.TEXT, width), BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** A quiet line of text for empty states. */
	public static JComponent note(String text, int width)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(new EmptyBorder(2, 4, 2, 4));
		holder.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_FAINT, width), BorderLayout.CENTER);
		return holder;
	}
}
