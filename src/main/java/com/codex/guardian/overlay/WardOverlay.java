package com.codex.guardian.overlay;

import com.codex.guardian.core.Peril;
import com.codex.guardian.core.PkWatch;
import com.codex.guardian.ui.Mark;
import com.codex.guardian.ui.Shield;
import com.codex.guardian.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
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

	/** The PKer alarm's lines at the head of the plaque: who can attack you, why they worry Guardian, the next move. */
	public static final class Pk
	{
		final Color color;
		final boolean pulse;
		final String heading;
		final String strength;
		final String who;
		final List<String> tags;
		final String move;
		final String footnote;

		Pk(Color color, boolean pulse, String heading, String strength, String who, List<String> tags, String move,
			String footnote)
		{
			this.color = color;
			this.pulse = pulse;
			this.heading = heading;
			this.strength = strength;
			this.who = who;
			this.tags = tags;
			this.move = move;
			this.footnote = footnote;
		}

		/**
		 * The lines for an alarm, or null when it is quiet.
		 *
		 * @param bracket the combat levels that can attack you here, as "77-117", or empty
		 */
		public static Pk of(PkWatch.Alarm alarm, String bracket)
		{
			if (alarm == null || !alarm.isActive())
			{
				return null;
			}
			PkWatch.Foe lead = alarm.getLead();
			Color color;
			switch (alarm.getStrength())
			{
				case DANGER:
					color = Theme.BLOOD;
					break;
				case WARN:
					color = Theme.EMBER;
					break;
				default:
					color = Theme.GOLD;
			}
			String who = "Level " + lead.getCombatLevel() + "  \u00b7  " + lead.getDistance()
				+ (lead.getDistance() == 1 ? " tile" : " tiles");
			List<String> tags = new ArrayList<>();
			if (lead.isTargetingYou())
			{
				tags.add("on you");
			}
			else if (lead.isClosing())
			{
				tags.add("closing");
			}
			if (lead.isSkulled())
			{
				tags.add("skulled");
			}
			if (lead.isArmed())
			{
				tags.add("KO weapon");
			}
			if (alarm.isMulti())
			{
				tags.add("multi");
			}
			String footnote = bracket == null || bracket.isEmpty() ? null : "Levels " + bracket + " can attack you";
			return new Pk(color, alarm.getStrength() == PkWatch.Strength.DANGER, alarm.headline().toUpperCase(),
				alarm.getStrength().name(), who, tags, alarm.getMove(), footnote);
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
		/** The one thing to do now, shown under the plaque in danger and peril, or null. */
		final String action;
		/** The PKer alarm, shown whatever the plaque setting, or null. */
		final Pk pk;

		public View(Peril.Level level, int hitpoints, int maxHitpoints, String supplies, String escape, boolean escapeOk,
			boolean showPlaque, Briefing briefing, String action)
		{
			this(level, hitpoints, maxHitpoints, supplies, escape, escapeOk, showPlaque, briefing, action, null);
		}

		public View(Peril.Level level, int hitpoints, int maxHitpoints, String supplies, String escape, boolean escapeOk,
			boolean showPlaque, Briefing briefing, String action, Pk pk)
		{
			this.pk = pk;
			this.action = action;
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
		if (v == null || !v.showPlaque && v.briefing == null && v.pk == null)
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
		boolean action = v.showPlaque && v.action != null;
		int plaqueHeight = v.showPlaque ? small.getHeight() + bold.getHeight() + 6 + small.getHeight() + 4
			+ (action ? small.getHeight() + 8 : 0) : 0;
		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();
		List<String> moveLines = v.pk == null || v.pk.move == null ? Collections.<String>emptyList()
			: wrap(v.pk.move, body, WIDTH - PAD * 2, 2);
		int pkHeight = 0;
		if (v.pk != null)
		{
			pkHeight = small.getHeight() + bold.getHeight() + tagRows(v.pk.tags, small) * (small.getHeight() + 2)
				+ (moveLines.isEmpty() ? 0 : moveLines.size() * body.getHeight() + 8) + (v.pk.footnote == null ? 0 : small.getHeight() + 2) + 4;
		}
		boolean below = v.briefing != null || v.showPlaque;
		int height = PAD + pkHeight + (v.pk != null && below ? 6 : 0) + briefingHeight
			+ (v.briefing != null && v.showPlaque ? 6 : 0) + plaqueHeight + PAD - 2;

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
		float pkBeat = 0;
		if (v.pk != null && v.pk.pulse && v.level != Peril.Level.PERIL)
		{
			pkBeat = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
			g.setStroke(new BasicStroke(3f));
			g.setColor(Theme.alpha(v.pk.color, (int) (40 + pkBeat * 80)));
			g.draw(shape);
		}
		g.setStroke(new BasicStroke(1f));
		Color frame = v.level.atLeast(Peril.Level.DANGER) ? Theme.mix(Theme.GOLD, tint, 0.4 + beat * 0.6) : Theme.alpha(Theme.GOLD, 150);
		if (v.pk != null && v.level != Peril.Level.PERIL)
		{
			frame = Theme.mix(Theme.GOLD, v.pk.color, 0.35 + pkBeat * 0.5);
		}
		g.setColor(frame);
		g.draw(shape);

		int y = PAD;
		if (v.pk != null)
		{
			y = paintPk(g, v.pk, moveLines, y, small, bold, body, pkBeat);
			if (below)
			{
				g.setPaint(new GradientPaint(PAD, 0, Theme.alpha(Theme.GOLD, 0), WIDTH / 2f, 0, Theme.alpha(Theme.GOLD, 120), true));
				g.drawLine(PAD, y + 1, WIDTH - PAD, y + 1);
				y += 6;
			}
		}
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
			y += small.getHeight() + 4;

			if (action)
			{
				// What to do, set apart on a tinted band so it is the line the eye lands on.
				g.setFont(Theme.body());
				int bandH = body.getHeight() + 2;
				g.setColor(Theme.alpha(tint, 46 + (int) (beat * 40)));
				g.fillRoundRect(PAD - 3, y, WIDTH - PAD * 2 + 6, bandH, 6, 6);
				g.setColor(Theme.alpha(tint, 150));
				g.drawRoundRect(PAD - 3, y, WIDTH - PAD * 2 + 6, bandH, 6, 6);
				String text = clip(v.action, body, WIDTH - PAD * 2);
				shadow(g, text, (WIDTH - body.stringWidth(text)) / 2, y + 1 + body.getAscent(), Theme.GOLD_BRIGHT);
			}
		}
		return new Dimension(WIDTH, height);
	}

	/** The PKer lines; returns the y below them. */
	private static int paintPk(Graphics2D g, Pk pk, List<String> moveLines, int y, FontMetrics small, FontMetrics bold,
		FontMetrics body, float beat)
	{
		g.setFont(Theme.small());
		skull(g, PAD, y + 1, 10, pk.color);
		shadow(g, clip(pk.heading, small, WIDTH - PAD * 2 - 14 - small.stringWidth(pk.strength) - 6), PAD + 14,
			y + small.getAscent(), pk.color);
		shadow(g, pk.strength, WIDTH - PAD - small.stringWidth(pk.strength), y + small.getAscent(), Theme.alpha(pk.color, 220));
		y += small.getHeight();

		g.setFont(Theme.title());
		shadow(g, clip(pk.who, bold, WIDTH - PAD * 2), PAD, y + bold.getAscent(), Theme.GOLD_BRIGHT);
		y += bold.getHeight();

		if (!pk.tags.isEmpty())
		{
			g.setFont(Theme.small());
			int x = PAD;
			for (String tag : pk.tags)
			{
				int w = small.stringWidth(tag) + 8;
				if (x + w > WIDTH - PAD && x > PAD)
				{
					x = PAD;
					y += small.getHeight() + 2;
				}
				g.setColor(Theme.alpha(pk.color, 40));
				g.fillRoundRect(x, y, w, small.getHeight(), 6, 6);
				g.setColor(Theme.alpha(pk.color, 150));
				g.drawRoundRect(x, y, w, small.getHeight(), 6, 6);
				shadow(g, tag, x + 4, y + small.getAscent(), Theme.TEXT);
				x += w + 4;
			}
			y += small.getHeight() + 2;
		}

		if (!moveLines.isEmpty())
		{
			y += 4;
			int bandH = moveLines.size() * body.getHeight() + 2;
			g.setColor(Theme.alpha(pk.color, 46 + (int) (beat * 40)));
			g.fillRoundRect(PAD - 3, y, WIDTH - PAD * 2 + 6, bandH, 6, 6);
			g.setColor(Theme.alpha(pk.color, 150));
			g.drawRoundRect(PAD - 3, y, WIDTH - PAD * 2 + 6, bandH, 6, 6);
			g.setFont(Theme.body());
			int ly = y + 1;
			for (String line : moveLines)
			{
				shadow(g, line, (WIDTH - body.stringWidth(line)) / 2, ly + body.getAscent(), Theme.GOLD_BRIGHT);
				ly += body.getHeight();
			}
			y += bandH + 4;
		}

		if (pk.footnote != null)
		{
			g.setFont(Theme.small());
			shadow(g, clip(pk.footnote, small, WIDTH - PAD * 2), PAD, y + small.getAscent(), Theme.TEXT_MUTED);
			y += small.getHeight() + 2;
		}
		return y;
	}

	/** Rows the tags take when they wrap at the plaque's width. */
	private static int tagRows(List<String> tags, FontMetrics small)
	{
		if (tags.isEmpty())
		{
			return 0;
		}
		int rows = 1;
		int x = PAD;
		for (String tag : tags)
		{
			int w = small.stringWidth(tag) + 8;
			if (x + w > WIDTH - PAD && x > PAD)
			{
				rows++;
				x = PAD;
			}
			x += w + 4;
		}
		return rows;
	}

	/** A small skull, the game's own sign for someone who has attacked another player. */
	private static void skull(Graphics2D g, int x, int y, int s, Color color)
	{
		float cx = x + s / 2f;
		g.setColor(Color.BLACK);
		g.fill(new Ellipse2D.Float(x + 1, y + 1, s, s * 0.8f));
		g.setColor(color);
		g.fill(new Ellipse2D.Float(x, y, s, s * 0.8f));
		g.fill(new RoundRectangle2D.Float(cx - s * 0.3f, y + s * 0.55f, s * 0.6f, s * 0.42f, 2, 2));
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(cx - s * 0.36f, y + s * 0.28f, s * 0.28f, s * 0.26f));
		g.fill(new Ellipse2D.Float(cx + s * 0.08f, y + s * 0.28f, s * 0.28f, s * 0.26f));
		g.fillRect((int) (cx - 0.5f), (int) (y + s * 0.72f), 1, (int) (s * 0.25f));
	}

	/** Breaks text into at most {@code max} lines that fit, the last clipped if it still runs over. */
	static List<String> wrap(String text, FontMetrics fm, int width, int max)
	{
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : text.split(" "))
		{
			String next = line.isEmpty() ? word : line + " " + word;
			if (fm.stringWidth(next) <= width || line.isEmpty())
			{
				line = next;
			}
			else
			{
				lines.add(line);
				line = word;
			}
		}
		if (!line.isEmpty())
		{
			lines.add(line);
		}
		if (lines.size() == 2 && max >= 2)
		{
			// Two lines read best when they are near the same length, not a full line and a stray word.
			String[] words = text.split(" ");
			String best0 = lines.get(0);
			String best1 = lines.get(1);
			int bestWidth = Math.max(fm.stringWidth(best0), fm.stringWidth(best1));
			for (int i = 1; i < words.length; i++)
			{
				String a = String.join(" ", Arrays.copyOfRange(words, 0, i));
				String b = String.join(" ", Arrays.copyOfRange(words, i, words.length));
				int widest = Math.max(fm.stringWidth(a), fm.stringWidth(b));
				if (widest <= width && widest < bestWidth)
				{
					best0 = a;
					best1 = b;
					bestWidth = widest;
				}
			}
			lines.set(0, best0);
			lines.set(1, best1);
		}
		if (lines.size() > max)
		{
			List<String> cut = new ArrayList<>(lines.subList(0, max));
			StringBuilder rest = new StringBuilder(cut.get(max - 1));
			for (int i = max; i < lines.size(); i++)
			{
				rest.append(' ').append(lines.get(i));
			}
			cut.set(max - 1, clip(rest.toString(), fm, width));
			return cut;
		}
		for (int i = 0; i < lines.size(); i++)
		{
			lines.set(i, clip(lines.get(i), fm, width));
		}
		return lines;
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
