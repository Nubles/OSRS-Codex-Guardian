package com.codex.guardian.ui;

import com.codex.guardian.core.Peril;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Path2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The crest's heart: a heater shield that fills with your hitpoints like a vial, tinted by
 * Guardian's level of worry. In peril its rim beats like a pulse.
 */
public class ShieldGauge extends JComponent
{
	private static final int W = 60;
	private static final int H = 70;

	private final Animator fill = new Animator(this);
	private final Timer pulse = new Timer(50, e -> repaint());
	private String hitpoints = "-";
	private Peril.Level level = Peril.Level.CALM;
	private boolean active;

	public ShieldGauge()
	{
		Dimension d = new Dimension(W, H);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
		setOpaque(false);
	}

	public void set(int current, int max, Peril.Level level, boolean loggedIn)
	{
		this.active = loggedIn;
		this.level = level;
		this.hitpoints = loggedIn ? String.valueOf(current) : "-";
		fill.set(loggedIn && max > 0 ? Math.max(0, Math.min(1, current / (double) max)) : 0, true);
		if (loggedIn && level == Peril.Level.PERIL)
		{
			if (!pulse.isRunning())
			{
				pulse.start();
			}
		}
		else
		{
			pulse.stop();
		}
		setToolTipText(loggedIn ? "Hitpoints " + current + " of " + max + "  ·  " + level.getLabel() : null);
		repaint();
	}

	@Override
	public void removeNotify()
	{
		pulse.stop();
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		Color tint = active ? Theme.peril(level) : Theme.GOLD_DIM;
		Path2D outer = Shield.path(1.5f, 1.5f, W - 3, H - 3);
		Path2D inner = Shield.path(5f, 5.5f, W - 10, H - 11);

		// Body: deep ink with a faint warm sheen at the top.
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.CARD_TOP, Theme.GOLD_DIM, 0.18), 0, H, Theme.INK_DEEP));
		g.fill(outer);

		// The vial: hitpoints rise from the point of the shield.
		double value = fill.get();
		if (value > 0)
		{
			Shape clip = g.getClip();
			g.clip(inner);
			float top = (float) (5.5f + (H - 11) * (1 - value));
			g.setPaint(new GradientPaint(0, top, Theme.alpha(tint, 200), 0, H, Theme.alpha(Theme.mix(tint, Theme.INK_DEEP, 0.55), 220)));
			g.fillRect(0, Math.round(top), W, H);
			g.setColor(Theme.alpha(Color.WHITE, 70));
			g.drawLine(0, Math.round(top), W, Math.round(top));
			g.setClip(clip);
		}
		g.setColor(Theme.alpha(Theme.INK_DEEP, 160));
		g.setStroke(new BasicStroke(1f));
		g.draw(inner);

		// Gilt rim; in peril it breathes.
		float beat = 0;
		if (pulse.isRunning())
		{
			beat = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 140.0));
		}
		Color rim = active && level.atLeast(Peril.Level.DANGER) ? Theme.mix(Theme.GOLD, tint, 0.5 + beat * 0.5) : Theme.GOLD;
		g.setStroke(new BasicStroke(2f));
		g.setColor(rim);
		g.draw(outer);
		if (beat > 0)
		{
			g.setStroke(new BasicStroke(4f));
			g.setColor(Theme.alpha(tint, (int) (40 + beat * 70)));
			g.draw(outer);
		}

		// The number, set in the game's bold font.
		g.setFont(Theme.title());
		FontMetrics fm = g.getFontMetrics();
		int tx = (W - fm.stringWidth(hitpoints)) / 2;
		int ty = H / 2 + fm.getAscent() / 2 - 4;
		ProgressRing.drawShadowed(g, hitpoints, tx, ty, Theme.TEXT);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String caption = "HP";
		ProgressRing.drawShadowed(g, caption, (W - small.stringWidth(caption)) / 2, ty + small.getAscent() + 1, Theme.alpha(Theme.TEXT, 190));
		g.dispose();
	}
}
