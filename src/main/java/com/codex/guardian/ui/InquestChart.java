package com.codex.guardian.ui;

import com.codex.guardian.core.Inquest;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * An inquest drawn tick by tick: hitpoints as a gilt line (blood where it runs low), prayer points as a thin teal
 * line, each tick's damage as a blood bar falling from the top (a max hit brightest), and lanes under the chart for
 * the protection prayer that was on, food and potions, and teleport attempts. The tick it went past saving gets a
 * dashed blood rule, and the worst tick of damage a dashed line at that many hitpoints: below it, one more like it
 * would have ended the run.
 */
final class InquestChart extends JComponent
{
	static final int HEIGHT = 132;
	private static final int PAD = 6;
	private static final int AXIS = 16;
	private static final int LANE = 7;

	private final Inquest q;

	InquestChart(Inquest q)
	{
		this.q = q;
		setOpaque(false);
		setPreferredSize(new Dimension(180, HEIGHT));
		setMinimumSize(new Dimension(60, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(0f);
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

		int n = q.ticks();
		int max = Math.max(1, q.getMax());
		int prayerMax = Math.max(1, q.getPrayerLevel());
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();

		float left = PAD + 2;
		float right = w - PAD - 2;
		float top = PAD + 2;
		// Three lanes under the chart: protection prayer, food and potions, teleports; then the time axis.
		float lanesTop = h - PAD - AXIS - LANE * 3;
		float bottom = lanesTop - 4;
		float span = bottom - top;
		float step = n > 1 ? (right - left) / (n - 1) : 0;

		// Under a quarter of the maximum is the danger band.
		float quarter = bottom - span * 0.25f;
		g.setColor(Theme.alpha(Theme.BLOOD_DEEP, 60));
		g.fill(new Rectangle2D.Float(left, quarter, right - left, bottom - quarter));

		// The worst tick of damage, as a line at that many hitpoints.
		int worst = q.worstTick();
		if (worst > 0 && worst < max)
		{
			float y = bottom - span * worst / (float) max;
			g.setColor(Theme.alpha(Theme.BLOOD, 150));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0f));
			g.draw(new Line2D.Float(left, y, right, y));
			String label = String.valueOf(worst);
			ProgressRing.drawShadowed(g, label, Math.round(right - fm.stringWidth(label) - 1), Math.round(y - 2), Theme.BLOOD);
		}

		// The tick it went past saving.
		int brink = q.brinkIndex();
		if (brink >= 0)
		{
			float x = left + brink * step;
			g.setColor(Theme.alpha(Theme.BLOOD, 70));
			g.fill(new Rectangle2D.Float(x, top, Math.max(1, right - x), bottom - top));
			g.setColor(Theme.alpha(Theme.BLOOD, 200));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2f, 2f}, 0f));
			g.draw(new Line2D.Float(x, top, x, bottom));
		}

		// Hits fall from the top, scaled to the maximum hitpoints.
		float barWidth = Math.max(1.5f, Math.min(4f, step * 0.6f));
		for (int i = 0; i < n; i++)
		{
			int hit = q.hitAt(i);
			if (hit <= 0)
			{
				continue;
			}
			boolean maxHit = false;
			for (Inquest.Hit each : q.getHits())
			{
				maxHit |= each.getTick() == i && each.isMax();
			}
			float length = Math.max(2f, span * Math.min(1f, hit / (float) max));
			float x = left + i * step - barWidth / 2;
			g.setColor(Theme.alpha(maxHit ? Theme.EMBER : Theme.BLOOD, hit == worst || maxHit ? 230 : 150));
			g.fill(new RoundRectangle2D.Float(x, top, barWidth, length, barWidth, barWidth));
		}

		// Prayer points, thin and teal, scaled to the Prayer level.
		if (q.getPrayerLevel() > 0)
		{
			Path2D.Float prayer = new Path2D.Float();
			for (int i = 0; i < n; i++)
			{
				float x = left + i * step;
				float y = bottom - span * Math.min(1f, q.prayerAt(i) / (float) prayerMax);
				if (i == 0)
				{
					prayer.moveTo(x, y);
				}
				else
				{
					prayer.lineTo(x, y);
				}
			}
			g.setColor(Theme.alpha(Theme.TEAL, 200));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(prayer);
		}

		// Hitpoints.
		Path2D.Float line = new Path2D.Float();
		Path2D.Float area = new Path2D.Float();
		for (int i = 0; i < n; i++)
		{
			float x = left + i * step;
			float y = bottom - span * Math.min(1f, q.hpAt(i) / (float) max);
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
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(line);

		int low = q.lowestIndex();
		float lx = left + low * step;
		float ly = bottom - span * Math.min(1f, q.hpAt(low) / (float) max);
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(lx - 4f, ly - 4f, 8, 8));
		g.setStroke(new BasicStroke(1.4f));
		g.setColor(q.hpAt(low) == 0 ? Theme.BLOOD : Theme.GOLD_BRIGHT);
		g.draw(new Ellipse2D.Float(lx - 3.5f, ly - 3.5f, 7, 7));

		// The maximum, top left, over the chart.
		ProgressRing.drawShadowed(g, String.valueOf(max), Math.round(left + 1), Math.round(top + fm.getAscent()), Theme.TEXT_FAINT);

		// Lanes: protection prayer on, food (teal) and potions (gold), teleport attempts (ember).
		float lane1 = lanesTop + LANE / 2f;
		float lane2 = lane1 + LANE;
		float lane3 = lane2 + LANE;
		g.setColor(Theme.alpha(Theme.STROKE_SOFT, 255));
		g.setStroke(new BasicStroke(1f));
		for (float y : new float[]{lane1, lane2, lane3})
		{
			g.draw(new Line2D.Float(left, y, right, y));
		}
		float cell = Math.max(1.5f, step);
		for (int i = 0; i < n; i++)
		{
			float x = left + i * step;
			if (q.protAt(i) != 0)
			{
				g.setColor(Theme.TEAL);
				float x0 = Math.max(left, x - cell / 2);
				float x1 = Math.min(right, x + cell / 2);
				g.fill(new Rectangle2D.Float(x0, lane1 - 1.5f, Math.max(1, x1 - x0), 3));
			}
			if (q.ateAt(i) > 0)
			{
				g.setColor(Theme.TEAL);
				g.fill(new Ellipse2D.Float(x - 2.5f, lane2 - 2.5f, 5, 5));
			}
			else if (q.drankAt(i) > 0)
			{
				g.setColor(Theme.GOLD);
				g.fill(new Ellipse2D.Float(x - 2.5f, lane2 - 2.5f, 5, 5));
			}
			if (q.teleportedAt(i))
			{
				Path2D.Float d = new Path2D.Float();
				d.moveTo(x, lane3 - 3);
				d.lineTo(x + 3, lane3);
				d.lineTo(x, lane3 + 3);
				d.lineTo(x - 3, lane3);
				d.closePath();
				g.setColor(Theme.EMBER);
				g.fill(d);
			}
		}

		// Time: seconds before the end, as the game counts a tick (0.6 s).
		int axisY = h - PAD - 2;
		String start = "-" + Math.round((n - 1) * 0.6) + "s";
		ProgressRing.drawShadowed(g, start, Math.round(left), axisY, Theme.TEXT_FAINT);
		String end = q.getKind() == Inquest.Kind.DEATH ? "death" : "safe";
		int endX = Math.round(right - fm.stringWidth(end));
		int markRight = Integer.MIN_VALUE;
		if (brink >= 0)
		{
			// Named on the time axis under its rule, kept clear of the start label.
			String mark = q.getKind() == Inquest.Kind.DEATH ? "past saving" : "brink";
			float x = left + brink * step;
			int width = fm.stringWidth(mark);
			int tx = Math.round(Math.max(left + fm.stringWidth(start) + 6, Math.min(right - width, x - width / 2f)));
			ProgressRing.drawShadowed(g, mark, tx, axisY, Theme.BLOOD);
			markRight = tx + width;
		}
		// The end is named unless the brink's name already sits there (a death's ring at zero says it anyway).
		if (markRight + 4 <= endX)
		{
			ProgressRing.drawShadowed(g, end, endX, axisY, q.getKind() == Inquest.Kind.DEATH ? Theme.BLOOD : Theme.TEAL);
		}
		g.dispose();
	}
}
