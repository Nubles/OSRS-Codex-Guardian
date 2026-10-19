package com.codex.guardian.ui;

import com.codex.guardian.core.Journal;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The run's seal as a card to share: whose run it is and how it is played, then the seal itself, drawn as the journal
 * draws it. The seal carries every figure (hours, the day the run began, close calls, closest call), so the card's
 * heading adds only the name and the mode.
 */
public final class SealShare
{
	static final int WIDTH = 260;
	static final int SCALE = 2;
	private static final int PAD = 16;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

	private SealShare()
	{
	}

	/** Everything the card shows, taken once so a saved card and a copied one read the same. */
	public static final class Spec
	{
		final Journal journal;
		final boolean oneLife;
		final long worth;
		final String player;
		final String mode;
		final long now;
		final ZoneId zone;

		private Spec(Journal journal, boolean oneLife, long worth, String player, String mode, long now, ZoneId zone)
		{
			this.journal = journal;
			this.oneLife = oneLife;
			this.worth = worth;
			this.player = player;
			this.mode = mode;
			this.now = now;
			this.zone = zone;
		}

		/**
		 * @param worth  the account's worth from Codex Ledger, or -1; shown on the seal as the journal shows it
		 * @param player the name to print, or null to leave it off
		 * @param mode   how the account is played ("Hardcore Ironman"), or null
		 * @return the card, or null with no journal yet
		 */
		public static Spec of(Journal journal, boolean oneLife, long worth, String player, String mode, long now, ZoneId zone)
		{
			return of(journal, oneLife, worth, player, mode, now, zone, true);
		}

		/**
		 * @param showWorth false to leave the account's worth off the card (the seal then shows the biggest hit, as it
		 *                  does without Ledger): a shared card says what an account is worth only when the player asks
		 */
		public static Spec of(Journal journal, boolean oneLife, long worth, String player, String mode, long now, ZoneId zone,
			boolean showWorth)
		{
			if (!showWorth)
			{
				worth = -1;
			}
			if (journal == null)
			{
				return null;
			}
			String name = player == null || player.trim().isEmpty() ? null : player.trim();
			String how = mode == null || mode.trim().isEmpty() ? null : mode.trim();
			return new Spec(journal, oneLife, worth, name, how, now, zone);
		}

		/** The large line: the player's name, else the mode, else "One life". */
		String heading()
		{
			return player != null ? player : mode != null ? mode : oneLife ? "One life" : "The run";
		}

		/** The line under it: the mode when the name is the heading, otherwise nothing (it would repeat). */
		String subheading()
		{
			return player != null ? mode : null;
		}
	}

	/** "codex-seal-2026-10-19.png". */
	public static String fileName(long now, ZoneId zone)
	{
		return "codex-seal-" + Instant.ofEpochMilli(now).atZone(zone).toLocalDate() + ".png";
	}

	public static BufferedImage render(Spec spec)
	{
		RunSeal seal = new RunSeal(spec.journal, spec.oneLife, spec.worth, spec.now);
		int sealW = WIDTH - 2 * PAD;
		seal.setSize(sealW, RunSeal.HEIGHT);

		String sub = spec.subheading();
		int headTop = PAD + 16;
		int sealTop = headTop + (sub != null ? 52 : 40);
		int footTop = sealTop + RunSeal.HEIGHT + 12;
		int height = footTop + 26;

		BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(SCALE, SCALE);
		Theme.smooth(g);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		boolean fallen = spec.journal.isStatusLost() || spec.oneLife && spec.journal.getDeaths() > 0;
		Color rim = fallen ? Theme.BLOOD : Theme.GOLD;

		RoundRectangle2D outer = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 14, 14);
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.GOLD_DIM, 0.16), 0, height, Theme.INK_DEEP));
		g.fill(outer);
		g.setColor(Theme.mix(Theme.GOLD_DIM, rim, fallen ? 0.6 : 0));
		g.draw(outer);
		g.setColor(Theme.alpha(rim, 60));
		g.draw(new RoundRectangle2D.Float(4.5f, 4.5f, WIDTH - 9, height - 9, 10, 10));

		// The masthead: the plugin's mark on the left, today's date on the right.
		g.setFont(Theme.small());
		FontMetrics sm = g.getFontMetrics();
		int mastY = PAD + 4 + sm.getAscent() / 2 - 1;
		diamond(g, PAD + 3, PAD + 4, 3, Theme.GOLD);
		g.setColor(Theme.GOLD);
		g.drawString("CODEX GUARDIAN", PAD + 11, mastY);
		String today = Instant.ofEpochMilli(spec.now).atZone(spec.zone).format(DATE);
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(today, WIDTH - PAD - sm.stringWidth(today), mastY);

		String heading = spec.heading();
		int textW = WIDTH - 2 * PAD;
		if (g.getFontMetrics(Theme.title()).stringWidth(heading) * 2 <= textW)
		{
			large(g, heading, PAD, headTop);
		}
		else
		{
			g.setFont(Theme.title());
			g.setColor(Theme.GOLD_BRIGHT);
			g.drawString(clip(heading, g.getFontMetrics(), textW), PAD, headTop + g.getFontMetrics().getAscent() + 6);
		}
		if (sub != null)
		{
			g.setFont(Theme.small());
			g.setColor(spec.oneLife ? Theme.GOLD : Theme.TEXT_MUTED);
			g.drawString(clip(sub, g.getFontMetrics(), textW), PAD, headTop + 44);
		}

		Graphics2D sg = (Graphics2D) g.create(PAD, sealTop, sealW, RunSeal.HEIGHT);
		seal.paint(sg);
		sg.dispose();

		int fy = footTop;
		g.setPaint(new GradientPaint(PAD, 0, Theme.alpha(Theme.GOLD, 0), WIDTH / 2f, 0, Theme.alpha(Theme.GOLD, 140), true));
		g.drawLine(PAD, fy, WIDTH - PAD, fy);
		diamond(g, WIDTH / 2, fy, 3, Theme.GOLD);
		g.setFont(Theme.small());
		String foot = "Kept by Codex Guardian";
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(foot, (WIDTH - g.getFontMetrics().stringWidth(foot)) / 2, fy + 16);
		g.dispose();
		return image;
	}

	/** The game's bold font doubled pixel for pixel, the way the game draws large lettering. */
	private static void large(Graphics2D g, String text, int x, int y)
	{
		Font font = Theme.title();
		FontMetrics fm = g.getFontMetrics(font);
		int w = Math.max(1, fm.stringWidth(text) + 1);
		int h = fm.getHeight() + 1;
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = small.createGraphics();
		sg.setFont(font);
		sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int base = fm.getAscent();
		sg.setColor(Color.BLACK);
		sg.drawString(text, 1, base + 1);
		sg.setPaint(new GradientPaint(0, 3, Theme.GOLD_BRIGHT, 0, base, Theme.GOLD));
		sg.drawString(text, 0, base);
		sg.dispose();
		Graphics2D lg = (Graphics2D) g.create();
		lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		lg.drawImage(small, x, y, w * 2, h * 2, null);
		lg.dispose();
	}

	private static void diamond(Graphics2D g, int cx, int cy, int r, Color c)
	{
		Path2D d = new Path2D.Float();
		d.moveTo(cx, cy - r);
		d.lineTo(cx + r, cy);
		d.lineTo(cx, cy + r);
		d.lineTo(cx - r, cy);
		d.closePath();
		g.setColor(c);
		g.fill(d);
	}

	private static String clip(String s, FontMetrics fm, int width)
	{
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		String out = s;
		while (out.length() > 1 && fm.stringWidth(out.trim() + "…") > width)
		{
			out = out.substring(0, out.length() - 1);
		}
		return out.trim() + "…";
	}
}
