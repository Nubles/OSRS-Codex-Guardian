package com.codex.guardian.overlay;

import com.codex.guardian.core.Peril;
import com.codex.guardian.ui.Mark;
import com.codex.guardian.ui.Shield;
import com.codex.guardian.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.List;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small gilt plaque in the game view: Guardian's level, your hitpoints and what you carry
 * to get out. When you walk into somewhere dangerous it opens into a briefing for a few
 * seconds. It draws a view the plugin prepares, so rendering never touches game state.
 */
public class WardOverlay extends Overlay
{
	private static final int WIDTH = 184;
	private static final int PAD = 8;

	/** One line of the entry briefing. */
	public static final class Check
	{
		final Mark.Kind kind;
		final String text;

		public Check(Mark.Kind kind, String text)
		{
			this.kind = kind;
			this.text = text;
		}
	}

	/** The briefing shown at a doorway: entering a dangerous place, nearing the ditch, passing a teleport limit. */
	public static final class Briefing
	{
		final String heading;
		final String zone;
		final String risk;
		final Color color;
		final List<Check> checks;

		public Briefing(String heading, String zone, String risk, Color color, List<Check> checks)
		{
			this.heading = heading;
			this.zone = zone;
			this.risk = risk;
			this.color = color;
			this.checks = checks == null ? Collections.emptyList() : checks;
		}
	}

	/** What the plaque shows; the plugin swaps in a new one each tick something changes. */
	public static final class View
	{
		final Peril.Level level;
		final int hitpoints;
		final int maxHitpoints;
		final String supplies;
		final String escape;
		final boolean escapeOk;
		final boolean showPlaque;
		final Briefing briefing;

		public View(Peril.Level level, int hitpoints, int maxHitpoints, String supplies, String escape, boolean escapeOk,
			boolean showPlaque, Briefing briefing)
		{
			this.level = level;
			this.hitpoints = hitpoints;
			this.maxHitpoints = maxHitpoints;
			this.supplies = supplies;
			this.escape = escape;
			this.escapeOk = escapeOk;
			this.showPlaque = showPlaque;
			this.briefing = briefing;
		}
	}

	private volatile View view;

	public WardOverlay()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void setView(View view)
	{
		this.view = view;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View v = view;
		if (v == null || !v.showPlaque && v.briefing == null)
		{
			return null;
		}
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		Color tint = Theme.peril(v.level);

		int briefingHeight = 0;
		if (v.briefing != null)
		{
			briefingHeight = small.getHeight() + bold.getHeight() + 4 + v.briefing.checks.size() * (small.getHeight() + 3) + 6;
		}
		int plaqueHeight = v.showPlaque ? small.getHeight() + bold.getHeight() + 6 + small.getHeight() + 4 : 0;
		int height = PAD + briefingHeight + (v.briefing != null && v.showPlaque ? 6 : 0) + plaqueHeight + PAD - 2;

		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 232), 0, height, Theme.alpha(Theme.INK_DEEP, 232)));
		g.fill(shape);
		float beat = 0;
		if (v.level == Peril.Level.PERIL)
		{
			beat = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 140.0));
			g.setStroke(new BasicStroke(3f));
			g.setColor(Theme.alpha(tint, (int) (50 + beat * 90)));
			g.draw(shape);
		}
		g.setStroke(new BasicStroke(1f));
		g.setColor(v.level.atLeast(Peril.Level.DANGER) ? Theme.mix(Theme.GOLD, tint, 0.4 + beat * 0.6) : Theme.alpha(Theme.GOLD, 150));
		g.draw(shape);

		int y = PAD;
		if (v.briefing != null)
		{
			Briefing b = v.briefing;
			g.setFont(Theme.small());
			shadow(g, b.heading, PAD, y + small.getAscent(), Theme.GOLD);
			shadow(g, b.risk, WIDTH - PAD - small.stringWidth(b.risk), y + small.getAscent(), b.color);
			y += small.getHeight();
			g.setFont(Theme.title());
			shadow(g, clip(b.zone, bold, WIDTH - PAD * 2), PAD, y + bold.getAscent(), Theme.GOLD_BRIGHT);
			y += bold.getHeight() + 4;
			g.setFont(Theme.small());
			for (Check c : b.checks)
			{
				Mark.paint(g, c.kind, PAD, y, 12);
				shadow(g, clip(c.text, small, WIDTH - PAD * 2 - 16), PAD + 16, y + small.getAscent() - 1, Theme.TEXT);
				y += small.getHeight() + 3;
			}
			y += 6;
			if (v.showPlaque)
			{
				g.setPaint(new GradientPaint(PAD, 0, Theme.alpha(Theme.GOLD, 0), WIDTH / 2f, 0, Theme.alpha(Theme.GOLD, 120), true));
				g.drawLine(PAD, y - 4, WIDTH - PAD, y - 4);
				y += 2;
			}
		}

		if (v.showPlaque)
		{
			// Title line: a tiny shield, the name, and the level.
			Path2D mini = Shield.path(PAD, y + 1, 8, 10);
			g.setColor(tint);
			g.fill(mini);
			g.setFont(Theme.small());
			shadow(g, "GUARDIAN", PAD + 12, y + small.getAscent(), Theme.GOLD);
			String level = v.level.getLabel().toUpperCase();
			shadow(g, level, WIDTH - PAD - small.stringWidth(level), y + small.getAscent(), tint);
			y += small.getHeight();

			g.setFont(Theme.title());
			String hp = v.hitpoints + " / " + v.maxHitpoints;
			shadow(g, hp, PAD, y + bold.getAscent(), Theme.TEXT);
			g.setFont(Theme.small());
			shadow(g, "HP", PAD + bold.stringWidth(hp) + 4, y + bold.getAscent(), Theme.TEXT_MUTED);
			y += bold.getHeight() + 1;

			int barW = WIDTH - PAD * 2;
			g.setColor(Theme.INK_DEEP);
			g.fillRoundRect(PAD, y, barW, 4, 4, 4);
			double share = v.maxHitpoints <= 0 ? 0 : Math.max(0, Math.min(1, v.hitpoints / (double) v.maxHitpoints));
			int fill = (int) Math.round(barW * share);
			if (fill > 0)
			{
				g.setPaint(new GradientPaint(PAD, 0, Theme.mix(tint, Theme.INK_DEEP, 0.3), PAD + barW, 0, tint));
				g.fillRoundRect(PAD, y, fill, 4, 4, 4);
			}
			y += 5;

			g.setFont(Theme.small());
			shadow(g, v.supplies, PAD, y + small.getAscent(), Theme.TEXT_MUTED);
			String escape = clip(v.escape, small, WIDTH - PAD * 2 - small.stringWidth(v.supplies) - 8);
			shadow(g, escape, WIDTH - PAD - small.stringWidth(escape), y + small.getAscent(), v.escapeOk ? Theme.TEAL : Theme.BLOOD);
		}
		return new Dimension(WIDTH, height);
	}

	private static void shadow(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private static String clip(String text, FontMetrics fm, int width)
	{
		if (text == null)
		{
			return "";
		}
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		String ellipsis = "...";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > width)
		{
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}
}
