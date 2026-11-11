package com.codex.guardian.ui;

import com.codex.guardian.core.DeathChoice;
import com.codex.guardian.core.LedgerWorth;
import com.codex.guardian.core.Seal;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The calm card a Hardcore World death brings: what Reset wipes and what Transfer keeps, side by side, with this life
 * set against the best one. It recommends nothing; the choice is made in the game's own window.
 */
final class ChoiceView
{
	private static final int COLUMN = 76;

	private ChoiceView()
	{
	}

	/**
	 * @param best       one line on the account's best life to set this one against, or null
	 * @param seeInquest opens this death's inquest, or null when there is none
	 * @param hide       puts the card away until the next login
	 */
	static Card build(DeathChoice c, String best, Runnable seeInquest, Runnable hide)
	{
		Card card = new Card(null).highlighted(true).accent(Theme.GOLD);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		add(card, Ui.label("RESET OR TRANSFER", Theme.small(), Theme.GOLD), 0);
		add(card, Ui.wrapped("The run has fallen and the game is asking. Take a breath: nothing here chooses for you.",
			Theme.body(), Theme.TEXT, 186), 4);

		JPanel columns = new JPanel(new GridLayout(1, 2, 6, 0));
		columns.setOpaque(false);
		columns.add(column("RESET", "Wiped", resetLines(c)));
		columns.add(column("TRANSFER", "Kept", transferLines(c)));
		add(card, columns, 8);

		add(card, Ui.wrapped("Either way, items bound for a gravestone are deleted: there is nothing to walk back for.",
			Theme.small(), Theme.TEXT_MUTED, 186), 8);
		add(card, Ui.wrapped(lifeLine(c), Theme.small(), Theme.GOLD, 186), 6);
		if (best != null)
		{
			add(card, Ui.wrapped(best, Theme.small(), Theme.TEXT_MUTED, 186), 2);
		}
		if (c.getCause() != null)
		{
			add(card, Ui.wrapped(c.getCause(), Theme.small(), Theme.TEXT_FAINT, 186), 2);
		}
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		if (seeInquest != null)
		{
			buttons.add(new CodexButton("See the inquest", false, seeInquest));
			buttons.add(javax.swing.Box.createHorizontalStrut(4));
		}
		buttons.add(new CodexButton("Hide", false, hide));
		add(card, buttons, 8);
		return card;
	}

	static List<String> resetLines(DeathChoice c)
	{
		List<String> lines = new ArrayList<>();
		lines.add("Total level " + String.format(Locale.ENGLISH, "%,d", c.getTotalLevel()));
		lines.add(c.getQuestPoints() + " quest points");
		lines.add(c.getBankValue() >= 0 ? "Bank, " + LedgerWorth.shortCoins(c.getBankValue()) : "Your bank");
		lines.add("Protected items too");
		lines.add("Start again here, as often as you like");
		lines.add("3 days without Stronghold GP");
		return lines;
	}

	static List<String> transferLines(DeathChoice c)
	{
		List<String> lines = new ArrayList<>();
		int kept = c.getKept().size();
		lines.add(kept == 0 ? "Protected items" : kept + " protected " + (kept == 1 ? "item" : "items")
			+ (c.getKeptValue() > 0 ? ", " + LedgerWorth.shortCoins(c.getKeptValue()) : ""));
		if (kept > 0)
		{
			lines.add(String.join(", ", c.getKept()));
		}
		lines.add(c.getBankValue() >= 0 ? "Bank and storage, " + LedgerWorth.shortCoins(c.getBankValue()) : "Bank and storage");
		lines.add("Leaves Hardcore Worlds for good: no way back");
		return lines;
	}

	static String lifeLine(DeathChoice c)
	{
		long hours = c.getPlayedTicks() / 6000;
		String length = hours >= 1 ? hours + " h" : Format.runTime(c.getPlayedTicks());
		return "This life: " + length + ", total " + String.format(Locale.ENGLISH, "%,d", c.getTotalLevel()) + ", "
			+ Seal.tier(c.getPlayedTicks()).getLabel().toLowerCase(Locale.ENGLISH) + " seal, "
			+ c.getCloseCalls() + (c.getCloseCalls() == 1 ? " close call" : " close calls");
	}

	private static JComponent column(String title, String verb, List<String> lines)
	{
		JPanel col = new Column();
		col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
		col.setBorder(new EmptyBorder(6, 6, 7, 4));
		add(col, Ui.label(title, Theme.title(), Theme.GOLD_BRIGHT), 0);
		add(col, Ui.label(verb, Theme.small(), Theme.GOLD), 2);
		for (String line : lines)
		{
			JLabel l = Ui.wrapped(line, Theme.small(), Theme.TEXT, COLUMN);
			add(col, l, 3);
		}
		return col;
	}

	private static void add(JPanel parent, JComponent c, int top)
	{
		c.setBorder(new EmptyBorder(top, 0, 0, 0));
		c.setAlignmentX(0f);
		parent.add(c);
	}

	/** A column: a darker well with a gold rule along its top, the same for both sides. */
	private static final class Column extends JPanel
	{
		Column()
		{
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			g.setColor(Theme.INK_DEEP);
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
			g.setColor(Theme.STROKE_SOFT);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
			g.setColor(Theme.alpha(Theme.GOLD, 150));
			g.fillRect(6, 0, getWidth() - 12, 2);
			g.dispose();
		}
	}
}
