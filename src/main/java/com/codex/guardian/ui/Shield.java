package com.codex.guardian.ui;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** The Guardian heater shield, shared by the sidebar icon, the crest and the overlay. */
public final class Shield
{
	private Shield()
	{
	}

	/** A heater shield filling the box: a dipped top edge, straight shoulders and a point. */
	public static Path2D path(float x, float y, float w, float h)
	{
		float cx = x + w / 2f;
		Path2D p = new Path2D.Float();
		p.moveTo(x, y);
		p.quadTo(cx, y + h * 0.08f, x + w, y);
		p.lineTo(x + w, y + h * 0.44f);
		p.curveTo(x + w, y + h * 0.74f, x + w * 0.64f, y + h * 0.9f, cx, y + h);
		p.curveTo(x + w * 0.36f, y + h * 0.9f, x, y + h * 0.74f, x, y + h * 0.44f);
		p.closePath();
		return p;
	}

	/** A small flame, the life the shield protects. */
	public static Path2D flame(float cx, float baseY, float height)
	{
		float w = height * 0.62f;
		Path2D p = new Path2D.Float();
		p.moveTo(cx, baseY - height);
		p.curveTo(cx + w * 0.15f, baseY - height * 0.62f, cx + w * 0.58f, baseY - height * 0.5f, cx + w * 0.5f, baseY - height * 0.24f);
		p.curveTo(cx + w * 0.44f, baseY - height * 0.02f, cx + w * 0.2f, baseY, cx, baseY);
		p.curveTo(cx - w * 0.2f, baseY, cx - w * 0.44f, baseY - height * 0.02f, cx - w * 0.5f, baseY - height * 0.24f);
		p.curveTo(cx - w * 0.58f, baseY - height * 0.5f, cx - w * 0.15f, baseY - height * 0.62f, cx, baseY - height);
		p.closePath();
		return p;
	}

	/** The sidebar button: an ink shield, gilt rim, and a teal flame at its heart. */
	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		float pad = Math.max(1f, size / 16f);
		float w = size - pad * 2 - size * 0.08f;
		float h = size - pad * 2;
		float x = (size - w) / 2f;
		Path2D shield = path(x, pad, w, h);
		g.setPaint(new GradientPaint(0, pad, Theme.mix(Theme.CARD_TOP, Theme.GOLD_DIM, 0.25), 0, pad + h, Theme.INK_DEEP));
		g.fill(shield);
		g.setStroke(new BasicStroke(Math.max(1f, size / 14f)));
		g.setColor(Theme.GOLD);
		g.draw(shield);
		float cx = size / 2f;
		Path2D flame = flame(cx, pad + h * 0.74f, h * 0.5f);
		g.setPaint(new GradientPaint(0, pad + h * 0.24f, Theme.GOLD_BRIGHT, 0, pad + h * 0.74f, Theme.TEAL));
		g.fill(flame);
		g.dispose();
		return image;
	}
}
