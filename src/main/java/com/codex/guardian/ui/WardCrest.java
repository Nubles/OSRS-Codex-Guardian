package com.codex.guardian.ui;

import com.codex.guardian.core.Journal;
import com.codex.guardian.core.Peril;
import com.codex.guardian.core.Situation;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Path2D;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The crest at the top of the panel: the hitpoint shield, the account's name and mode,
 * and three ledgers for prayer, the length of the run and the close calls survived.
 */
public class WardCrest extends JPanel
{
	private final ShieldGauge shield = new ShieldGauge();
	private final JLabel name = Ui.label("Guardian", Theme.title(), Theme.GOLD_BRIGHT);
	private final JLabel mode = Ui.label("Not logged in", Theme.small(), Theme.TEXT_MUTED);
	private final Ledger prayer = new Ledger("Prayer", true);
	private final Ledger run = new Ledger("This run", false);
	private final Ledger closeCalls = new Ledger("Close calls", false);
	private Peril.Level level = Peril.Level.CALM;

	public WardCrest()
	{
		super(new BorderLayout(10, 0));
		setOpaque(false);
		setBorder(new EmptyBorder(12, 10, 12, 10));

		JPanel shieldHolder = new JPanel(new BorderLayout());
		shieldHolder.setOpaque(false);
		shieldHolder.add(shield, BorderLayout.NORTH);
		add(shieldHolder, BorderLayout.WEST);

		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		name.setAlignmentX(LEFT_ALIGNMENT);
		mode.setAlignmentX(LEFT_ALIGNMENT);
		right.add(name);
		right.add(mode);

		JPanel ledgers = new JPanel(new GridLayout(3, 1, 0, 2));
		ledgers.setOpaque(false);
		ledgers.setBorder(new EmptyBorder(4, 0, 0, 0));
		ledgers.add(prayer);
		ledgers.add(run);
		ledgers.add(closeCalls);
		ledgers.setAlignmentX(LEFT_ALIGNMENT);
		right.add(ledgers);
		add(right, BorderLayout.CENTER);
	}

	public void update(Situation s, Peril peril, Journal journal)
	{
		boolean in = s.isLoggedIn();
		level = in ? peril.getLevel() : Peril.Level.CALM;
		shield.set(s.getHitpoints(), s.getMaxHitpoints(), level, in);
		name.setText(in && s.getPlayerName() != null ? s.getPlayerName() : "Guardian");
		mode.setText(in ? s.getMode() : "Not logged in");
		mode.setToolTipText(in && s.getWorld() > 0 ? s.getMode() + " on world " + s.getWorld() : null);
		mode.setForeground(in && s.isOneLife() ? Theme.GOLD : Theme.TEXT_MUTED);
		if (in)
		{
			prayer.set(s.getPrayer() + "/" + s.getMaxPrayer(), s.getMaxPrayer() <= 0 ? 0 : s.getPrayer() / (double) s.getMaxPrayer());
		}
		else
		{
			prayer.set("-", 0);
		}
		run.set(journal == null ? "-" : Format.duration(journal.getPlayedTicks()), -1);
		closeCalls.set(journal == null ? "-" : String.valueOf(journal.getCloseCalls()), -1);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		Color warmth = level.atLeast(Peril.Level.DANGER) ? Theme.mix(Theme.GOLD_DIM, Theme.peril(level), 0.6) : Theme.GOLD_DIM;
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, warmth, 0.18), 0, h, Theme.INK));
		g.fillRect(0, 0, w, h);

		// Gilt rule along the bottom with a small diamond at its centre.
		int y = h - 2;
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(0, y, w, y);
		Path2D diamond = new Path2D.Float();
		diamond.moveTo(w / 2f, y - 3);
		diamond.lineTo(w / 2f + 3, y);
		diamond.lineTo(w / 2f, y + 3);
		diamond.lineTo(w / 2f - 3, y);
		diamond.closePath();
		g.setColor(Theme.GOLD);
		g.fill(diamond);
		g.dispose();
		super.paintComponent(graphics);
	}

	/** One labelled value, with a hairline bar under it when the value is a share of something. */
	private static final class Ledger extends JPanel
	{
		private final JLabel value = Ui.label("-", Theme.small(), Theme.TEXT);
		private final ProgressBar bar;

		Ledger(String label, boolean withBar)
		{
			super(new BorderLayout(0, 1));
			setOpaque(false);
			JPanel line = new JPanel(new BorderLayout());
			line.setOpaque(false);
			line.add(Ui.label(label, Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
			line.add(value, BorderLayout.EAST);
			add(line, BorderLayout.CENTER);
			bar = withBar ? new ProgressBar(3) : null;
			if (bar != null)
			{
				add(bar, BorderLayout.SOUTH);
			}
			setPreferredSize(new Dimension(10, 17));
		}

		void set(String text, double share)
		{
			value.setText(text);
			if (bar != null)
			{
				bar.setProgress(Math.max(0, share), false, true);
			}
		}
	}
}
