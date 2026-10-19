package com.codex.guardian.ui;

import com.codex.guardian.core.Replay;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * A close call or a death drawn tick by tick in a slim strip: the hitpoints as a gilt line that turns to blood
 * where it runs low, each tick's damage as a blood bar falling from the top, and a teal bead under every tick on
 * which you ate (a gold bead for a potion). The lowest point gets a ring.
 */
final class ReplayStrip extends JComponent
{
	static final int HEIGHT = 40;
	private static final int PAD = 3;

	private final Replay replay;

	ReplayStrip(Replay replay, int width)
	{
		this.replay = replay;
		setOpaque(false);
		setPreferredSize(new Dimension(width, HEIGHT));
		setMinimumSize(new Dimension(40, HEIGHT));
		setMaximumSize(new Dimension(width, HEIGHT));
		setAlignmentX(0f);
		StringBuilder tip = new StringBuilder("<html>").append(replay.summary());
		String worst = replay.worstLine();
		if (worst != null)
		{
			tip.append("<br>").append(worst);
		}
		setToolTipText(tip.append("</html>").toString());
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g.setColor(Theme.INK_DEEP);
		g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));
		g.setColor(Theme.alpha(Theme.STROKE, 150));
		g.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));

		int n = replay.ticks();
		int max = Math.max(1, replay.getMax());
		// The beads sit in a lane of their own under the chart.
		float top = PAD;
		float bottom = h - PAD - 6;
		float left = PAD + 1;
		float right = w - PAD - 1;
		float step = n > 1 ? (right - left) / (n - 1) : 0;
		float span = bottom - top;

		// Under a quarter of the maximum is the danger band.
		float quarter = bottom - span * 0.25f;
		g.setColor(Theme.alpha(Theme.BLOOD_DEEP, 70));
		g.fill(new java.awt.geom.Rectangle2D.Float(left, quarter, right - left, bottom - quarter));

		int biggest = 1;
		for (int i = 0; i < n; i++)
		{
			biggest = Math.max(biggest, replay.hitAt(i));
		}
		float barWidth = Math.max(1.5f, Math.min(4f, step * 0.6f));
		for (int i = 0; i < n; i++)
		{
			int hit = replay.hitAt(i);
			if (hit <= 0)
			{
				continue;
			}
			// Hits are scaled to the maximum hitpoints, so a hit of a third of your life reaches a third down.
			float length = Math.max(2f, span * Math.min(1f, hit / (float) max));
			float x = left + i * step - barWidth / 2;
			g.setColor(Theme.alpha(Theme.BLOOD, hit == biggest ? 220 : 150));
			g.fill(new RoundRectangle2D.Float(x, top, barWidth, length, barWidth, barWidth));
		}

		Path2D.Float line = new Path2D.Float();
		Path2D.Float area = new Path2D.Float();
		for (int i = 0; i < n; i++)
		{
			float x = left + i * step;
			float y = bottom - span * Math.min(1f, replay.hpAt(i) / (float) max);
			if (i == 0)
			{
				line.moveTo(x, y);
				area.moveTo(x, bottom);
				area.lineTo(x, y);
			}
			else
			{
				line.lineTo(x, y);
				area.lineTo(x, y);
			}
		}
		area.lineTo(left + (n - 1) * step, bottom);
		area.closePath();
		g.setPaint(new GradientPaint(0, top, Theme.alpha(Theme.GOLD, 70), 0, bottom, Theme.alpha(Theme.GOLD, 0)));
		g.fill(area);
		g.setPaint(new GradientPaint(0, top, Theme.GOLD_BRIGHT, 0, bottom, Theme.BLOOD));
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(line);

		int low = replay.lowestIndex();
		float lx = left + low * step;
		float ly = bottom - span * Math.min(1f, replay.hpAt(low) / (float) max);
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(lx - 3.5f, ly - 3.5f, 7, 7));
		g.setStroke(new BasicStroke(1.4f));
		g.setColor(replay.hpAt(low) == 0 ? Theme.BLOOD : Theme.GOLD_BRIGHT);
		g.draw(new Ellipse2D.Float(lx - 3f, ly - 3f, 6, 6));

		float beadY = h - PAD - 3;
		for (int i = 0; i < n; i++)
		{
			float x = left + i * step;
			if (replay.ateAt(i) > 0)
			{
				g.setColor(Theme.TEAL);
				g.fill(new Ellipse2D.Float(x - 2f, beadY - 2f, 4, 4));
			}
			else if (replay.drankAt(i) > 0)
			{
				g.setColor(Theme.GOLD);
				g.fill(new Ellipse2D.Float(x - 2f, beadY - 2f, 4, 4));
			}
		}
		g.dispose();
	}
}
