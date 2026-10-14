package com.codex.guardian.overlay;

import com.codex.guardian.ui.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * In peril the edges of the game view glow with a slow blood-red pulse, so the warning
 * reaches you even when your eyes are on the fight and not on a panel.
 */
public class PerilVignette extends Overlay
{
	private final Client client;
	private volatile boolean active;

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

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!active)
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
		double beat = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0);
		int alpha = (int) (60 + beat * 70);
		Color edge = Theme.alpha(Theme.BLOOD, alpha);
		Color clear = Theme.alpha(Theme.BLOOD, 0);
		int depth = Math.max(24, Math.min(w, h) / 7);

		g.setPaint(new GradientPaint(x, y, edge, x, y + depth, clear));
		g.fillRect(x, y, w, depth);
		g.setPaint(new GradientPaint(x, y + h, edge, x, y + h - depth, clear));
		g.fillRect(x, y + h - depth, w, depth);
		g.setPaint(new GradientPaint(x, y, edge, x + depth, y, clear));
		g.fillRect(x, y, depth, h);
		g.setPaint(new GradientPaint(x + w, y, edge, x + w - depth, y, clear));
		g.fillRect(x + w - depth, y, depth, h);
		return null;
	}
}
