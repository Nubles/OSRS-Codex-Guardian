package com.codex.guardian.overlay;

import com.codex.guardian.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * When a watch rule flashes the screen, a small gilt banner at the top of the game view names the rule and what set
 * it off, so a flash is never a mystery. It fades after a few seconds. It draws only what the plugin hands it.
 */
public class WatchBanner extends Overlay
{
	static final int WIDTH = 230;
	static final long SHOW_MILLIS = 4000;
	private static final long FADE_MILLIS = 600;

	/** What the banner says, in which colour, and when it went up. */
	public static final class Shown
	{
		final String name;
		final String detail;
		final Color color;
		final long at;

		public Shown(String name, String detail, Color color, long at)
		{
			this.name = name == null ? "" : name;
			this.detail = detail == null ? "" : detail;
			this.color = color == null ? Theme.GOLD : color;
			this.at = at;
		}
	}

	private volatile Shown shown;

	public WatchBanner()
	{
		setPosition(OverlayPosition.TOP_CENTER);
	}

	public void show(Shown shown)
	{
		this.shown = shown;
	}

	public void clear()
	{
		shown = null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Shown s = shown;
		if (s == null)
		{
			return null;
		}
		long age = System.currentTimeMillis() - s.at;
		if (age > SHOW_MILLIS || age < 0)
		{
			shown = null;
			return null;
		}
		return paint(g, s, age);
	}

	/** Paints the banner as it looks {@code age} milliseconds after it went up. */
	static Dimension paint(Graphics2D g, Shown s, long age)
	{
		float fade = age > SHOW_MILLIS - FADE_MILLIS ? Math.max(0f, (SHOW_MILLIS - age) / (float) FADE_MILLIS) : 1f;
		Graphics2D c = (Graphics2D) g.create();
		Theme.smooth(c);
		c.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, fade));
		Font bold = Theme.title();
		Font small = Theme.small();
		FontMetrics fb = c.getFontMetrics(bold);
		FontMetrics fs = c.getFontMetrics(small);
		int textX = 30;
		int textW = WIDTH - textX - 10;
		java.util.List<String> lines = wrap(s.detail, fs, textW, 2);
		int h = 8 + fb.getHeight() + lines.size() * fs.getHeight() + 9;
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, h - 1, 10, 10);
		c.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 236), 0, h, Theme.alpha(Theme.INK_DEEP, 236)));
		c.fill(shape);
		// A glow in the rule's colour that settles over the first second.
		double settle = Math.max(0, 1 - age / 1000.0);
		c.setStroke(new BasicStroke(3f));
		c.setColor(Theme.alpha(s.color, (int) (40 + 110 * settle)));
		c.draw(shape);
		c.setStroke(new BasicStroke(1f));
		c.setColor(Theme.mix(Theme.GOLD, s.color, 0.45));
		c.draw(shape);
		Shape before = c.getClip();
		c.clip(shape);
		c.setPaint(new GradientPaint(0, 0, s.color, 0, h, Theme.alpha(s.color, 90)));
		c.fillRect(0, 0, 3, h);
		c.setClip(before);

		eye(c, 9, h / 2 - 6, s.color);

		c.setFont(bold);
		int y = 6 + fb.getAscent();
		shadow(c, clip(s.name, fb, textW), textX, y, Theme.GOLD_BRIGHT);
		c.setFont(small);
		for (String line : lines)
		{
			y += fs.getHeight();
			shadow(c, line, textX, y + 2, Theme.TEXT);
		}
		c.dispose();
		return new Dimension(WIDTH, h);
	}

	/** A watchful eye: Guardian's mark for the player's own rules. */
	private static void eye(Graphics2D g, int x, int y, Color color)
	{
		Path2D lid = new Path2D.Float();
		lid.moveTo(x, y + 6);
		lid.quadTo(x + 7, y - 2, x + 14, y + 6);
		lid.quadTo(x + 7, y + 14, x, y + 6);
		lid.closePath();
		g.setColor(Theme.alpha(color, 60));
		g.fill(lid);
		g.setColor(color);
		g.setStroke(new BasicStroke(1.3f));
		g.draw(lid);
		g.fill(new Ellipse2D.Float(x + 4.5f, y + 2.5f, 5, 5));
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(x + 6, y + 4, 2, 2));
		g.setStroke(new BasicStroke(1f));
	}

	private static void shadow(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/** The detail in at most {@code max} lines, broken between words; the last line clips with an ellipsis. */
	static java.util.List<String> wrap(String text, FontMetrics fm, int width, int max)
	{
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		String[] words = text.split(" ");
		for (int i = 0; i < words.length; i++)
		{
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (line.length() > 0 && fm.stringWidth(next) > width)
			{
				if (lines.size() == max - 1)
				{
					StringBuilder rest = new StringBuilder(line);
					for (int j = i; j < words.length; j++)
					{
						rest.append(' ').append(words[j]);
					}
					lines.add(clip(rest.toString(), fm, width));
					return lines;
				}
				lines.add(line.toString());
				line = new StringBuilder(words[i]);
			}
			else
			{
				line = new StringBuilder(next);
			}
		}
		lines.add(clip(line.toString(), fm, width));
		return lines;
	}

	private static String clip(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + "...") > width)
		{
			end--;
		}
		return text.substring(0, end) + "...";
	}
}
