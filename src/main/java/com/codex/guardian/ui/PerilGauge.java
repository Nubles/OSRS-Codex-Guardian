package com.codex.guardian.ui;

import com.codex.guardian.core.Peril;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * Guardian's dial: a half ring engraved in four bands from calm to peril, with a gilt needle
 * that swings to the current reading and the level set beneath it.
 */
public class PerilGauge extends JComponent
{
	private static final int HEIGHT = 114;
	/** Where each band starts on the 0 to 100 scale, matching the assessor's thresholds. */
	private static final double[] BANDS = {0, 18, 40, 65, 100};
	private static final Peril.Level[] LEVELS = Peril.Level.values();

	private final Animator needle = new Animator(this);
	private Peril.Level level = Peril.Level.CALM;
	private boolean active;

	public PerilGauge()
	{
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
		setOpaque(false);
	}

	public void set(Peril peril, boolean loggedIn)
	{
		active = loggedIn;
		level = peril.getLevel();
		needle.set(loggedIn ? peril.getScore() / 100.0 : 0, true);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		float r = Math.min(w / 2f - 18, 66);
		float cx = w / 2f;
		float cy = 12 + r;
		float thick = 9;
		double value = needle.get();
		Color lit = active ? Theme.peril(level) : Theme.GOLD_DIM;

		// A soft glow behind the dial in the colour of the current level.
		g.setPaint(new RadialGradientPaint(cx, cy, r + 14, new float[]{0f, 1f},
			new Color[]{Theme.alpha(lit, active ? 46 : 18), Theme.alpha(lit, 0)}));
		g.fill(new Ellipse2D.Float(cx - r - 14, cy - r - 14, (r + 14) * 2, (r + 14) * 2));

		// The four bands, dim where the needle has not reached and bright where it has.
		Arc2D.Float box = new Arc2D.Float();
		for (int i = 0; i < LEVELS.length; i++)
		{
			double from = BANDS[i] / 100;
			double to = BANDS[i + 1] / 100;
			Color c = Theme.peril(LEVELS[i]);
			g.setStroke(new BasicStroke(thick, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
			g.setColor(Theme.alpha(c, 52));
			box.setArc(cx - r, cy - r, r * 2, r * 2, angle(from) - 0.8, -(to - from) * 180 + 1.6, Arc2D.OPEN);
			g.draw(box);
			if (active && value > from)
			{
				double end = Math.min(value, to);
				g.setColor(Theme.alpha(c, i == level.ordinal() ? 255 : 170));
				box.setArc(cx - r, cy - r, r * 2, r * 2, angle(from) - 0.8, -(end - from) * 180 + 1.6, Arc2D.OPEN);
				g.draw(box);
			}
		}

		// Fine gold rims inside and outside the bands, and small engraved ticks.
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 140));
		float outer = r + thick / 2 + 3;
		float inner = r - thick / 2 - 3;
		g.draw(new Arc2D.Float(cx - outer, cy - outer, outer * 2, outer * 2, 0, 180, Arc2D.OPEN));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 80));
		g.draw(new Arc2D.Float(cx - inner, cy - inner, inner * 2, inner * 2, 0, 180, Arc2D.OPEN));
		for (int i = 0; i <= 20; i++)
		{
			double a = Math.toRadians(180 - i * 9);
			float len = i % 5 == 0 ? 4 : 2;
			float x1 = (float) (cx + Math.cos(a) * (outer + 1));
			float y1 = (float) (cy - Math.sin(a) * (outer + 1));
			float x2 = (float) (cx + Math.cos(a) * (outer + 1 + len));
			float y2 = (float) (cy - Math.sin(a) * (outer + 1 + len));
			g.setColor(Theme.alpha(Theme.GOLD, i % 5 == 0 ? 150 : 80));
			g.drawLine(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2));
		}

		// The needle: a long gilt blade from a jewelled hub.
		double a = Math.toRadians(angle(value));
		float tipX = (float) (cx + Math.cos(a) * (r + 2));
		float tipY = (float) (cy - Math.sin(a) * (r + 2));
		float nx = (float) -Math.sin(a) * 3.2f;
		float ny = (float) -Math.cos(a) * 3.2f;
		Path2D blade = new Path2D.Float();
		blade.moveTo(tipX, tipY);
		blade.lineTo(cx + nx, cy + ny);
		blade.lineTo(cx - (float) Math.cos(a) * 8, cy + (float) Math.sin(a) * 8);
		blade.lineTo(cx - nx, cy - ny);
		blade.closePath();
		g.setColor(Theme.alpha(Color.BLACK, 120));
		g.translate(1, 1);
		g.fill(blade);
		g.translate(-1, -1);
		g.setColor(active ? Theme.GOLD_BRIGHT : Theme.GOLD_DIM);
		g.fill(blade);
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(cx - 6, cy - 6, 12, 12));
		g.setColor(Theme.GOLD);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(new Ellipse2D.Float(cx - 6, cy - 6, 12, 12));
		g.setColor(lit);
		g.fill(new Ellipse2D.Float(cx - 3, cy - 3, 6, 6));

		// The level, set in bold under the hub.
		g.setFont(Theme.title());
		FontMetrics fm = g.getFontMetrics();
		String text = active ? level.getLabel().toUpperCase() : "AT REST";
		ProgressRing.drawShadowed(g, text, Math.round(cx - fm.stringWidth(text) / 2f), Math.round(cy + 8 + fm.getAscent()),
			active ? lit : Theme.TEXT_MUTED);

		// Band names at either end.
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setColor(Theme.TEXT_FAINT);
		g.drawString("Calm", Math.round(cx - outer - small.stringWidth("Calm") / 2f + 4), Math.round(cy + small.getAscent() + 3));
		g.drawString("Peril", Math.round(cx + outer - small.stringWidth("Peril") / 2f - 4), Math.round(cy + small.getAscent() + 3));
		g.dispose();
	}

	/** Maps 0 to 1 onto the dial, left (180 degrees) to right (0 degrees). */
	private static double angle(double value)
	{
		return 180 - Math.max(0, Math.min(1, value)) * 180;
	}
}
