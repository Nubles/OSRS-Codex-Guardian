package com.codex.guardian.overlay;

import com.codex.guardian.core.PkWatch;
import com.codex.guardian.ui.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * In peril the edges of the game view glow with a slow blood-red pulse, so the warning
 * reaches you even when your eyes are on the fight and not on a panel.
 *
 * <p>The PKer alarm uses the same edges: a quicker double beat in ember, or blood when a player is coming for
 * you, with a bloom on the edge nearest the player when they stand on screen.
 */
public class PerilVignette extends Overlay
{
	private final Client client;
	private volatile boolean active;
	private volatile Flash flash;

	/** A PKer flash: its color, how hard it beats, and where on the canvas the player stands (or null). */
	public static final class Flash
	{
		final Color color;
		final double strength;
		final Point toward;

		public Flash(Color color, double strength, Point toward)
		{
			this.color = color;
			this.strength = Math.max(0, Math.min(1, strength));
			this.toward = toward;
		}

		/** Blood when someone is coming for you, ember when they might, a warm gold when they only could. */
		public static Flash of(PkWatch.Strength strength, Point toward)
		{
			switch (strength)
			{
				case DANGER:
					return new Flash(Theme.BLOOD, 1, toward);
				case WARN:
					return new Flash(Theme.EMBER, 0.6, toward);
				case WATCH:
					return new Flash(Theme.GOLD, 0.2, toward);
				default:
					return null;
			}
		}
	}

	@Inject
	public PerilVignette(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void setActive(boolean active)
	{
		this.active = active;
	}

	/** The PKer flash to draw when not in peril, or null. */
	public void setFlash(Flash flash)
	{
		this.flash = flash;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Flash f = flash;
		if (!active && f == null)
		{
			return null;
		}
		int x = client.getViewportXOffset();
		int y = client.getViewportYOffset();
		int w = client.getViewportWidth();
		int h = client.getViewportHeight();
		if (w <= 0 || h <= 0)
		{
			return null;
		}
		if (active)
		{
			double beat = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0);
			edges(g, x, y, w, h, Theme.BLOOD, (int) (60 + beat * 70));
		}
		else
		{
			paintFlash(g, f, x, y, w, h, System.currentTimeMillis());
		}
		return null;
	}

	/**
	 * A double beat every 1.2 seconds: two quick swells, then rest, so it reads as an alarm and not as the slow
	 * breath of peril. Strength scales how far it rises; a watching alarm only warms the edges.
	 */
	static double beat(long millis)
	{
		double t = (millis % 1200) / 1200.0;
		if (t < 0.18)
		{
			return Math.sin(Math.PI * t / 0.18);
		}
		return t >= 0.24 && t < 0.42 ? 0.8 * Math.sin(Math.PI * (t - 0.24) / 0.18) : 0;
	}

	static void paintFlash(Graphics2D g, Flash f, int x, int y, int w, int h, long millis)
	{
		double b = beat(millis);
		int base = (int) (12 + f.strength * 20);
		int alpha = (int) (base + b * (30 + f.strength * 60));
		edges(g, x, y, w, h, f.color, alpha);
		if (f.toward != null)
		{
			// A bloom on the nearest edge to where the player stands.
			int px = Math.max(x, Math.min(x + w, f.toward.x));
			int py = Math.max(y, Math.min(y + h, f.toward.y));
			int toLeft = px - x;
			int toRight = x + w - px;
			int toTop = py - y;
			int toBottom = y + h - py;
			int min = Math.min(Math.min(toLeft, toRight), Math.min(toTop, toBottom));
			if (min == toLeft)
			{
				px = x;
			}
			else if (min == toRight)
			{
				px = x + w;
			}
			else if (min == toTop)
			{
				py = y;
			}
			else
			{
				py = y + h;
			}
			float radius = Math.max(60, Math.min(w, h) / 3f);
			g.setPaint(new RadialGradientPaint(px, py, radius, new float[]{0f, 1f},
				new Color[]{Theme.alpha(f.color, Math.min(255, alpha + 20)), Theme.alpha(f.color, 0)}));
			g.fillRect(x, y, w, h);
		}
	}

	private static void edges(Graphics2D g, int x, int y, int w, int h, Color color, int alpha)
	{
		Color edge = Theme.alpha(color, Math.max(0, Math.min(255, alpha)));
		Color clear = Theme.alpha(color, 0);
		int depth = Math.max(24, Math.min(w, h) / 7);

		g.setPaint(new GradientPaint(x, y, edge, x, y + depth, clear));
		g.fillRect(x, y, w, depth);
		g.setPaint(new GradientPaint(x, y + h, edge, x, y + h - depth, clear));
		g.fillRect(x, y + h - depth, w, depth);
		g.setPaint(new GradientPaint(x, y, edge, x + depth, y, clear));
		g.fillRect(x, y, depth, h);
		g.setPaint(new GradientPaint(x + w, y, edge, x + w - depth, y, clear));
		g.fillRect(x + w - depth, y, depth, h);
	}
}
