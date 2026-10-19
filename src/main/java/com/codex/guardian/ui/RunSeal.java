package com.codex.guardian.ui;

import com.codex.guardian.core.Journal;
import com.codex.guardian.core.LedgerWorth;
import com.codex.guardian.core.Seal;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * The run's seal, the card at the top of the journal worth a screenshot: a metal seal earned by hours on one life,
 * its tier on a ribbon, the day the run began, and the three numbers that tell its story. A fallen run keeps its
 * seal, cracked. Everything is drawn here once per journal update, so it costs nothing while the panel is idle.
 */
final class RunSeal extends JComponent
{
	static final int HEIGHT = 204;
	private static final int R = 30;

	private final boolean fallen;
	private final boolean oneLife;
	private final long played;
	private final String began;
	private final long days;
	private final Seal.Tier tier;
	private final double toNext;
	private final long hoursToNext;
	private final int closeCalls;
	private final int lowest;
	private final String third;
	private final String thirdLabel;

	/**
	 * @param worth what the account is worth in coins as Codex Ledger last saw it, or -1 without Ledger; shown as its
	 *              worth, or the biggest hit taken when there is no figure
	 */
	RunSeal(Journal journal, boolean oneLife, long worth, long now)
	{
		this.oneLife = oneLife;
		fallen = journal.isStatusLost() || oneLife && journal.getDeaths() > 0;
		played = journal.getPlayedTicks();
		began = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(new Date(journal.getRunStartedAt()));
		days = Math.max(1, (now - journal.getRunStartedAt()) / 86_400_000L + 1);
		tier = Seal.tier(played);
		toNext = Seal.progress(played);
		hoursToNext = Seal.hoursToNext(played);
		closeCalls = journal.getCloseCalls();
		lowest = journal.getLowestHitpoints();
		Journal.Hit hit = journal.getBiggestHit();
		if (worth > 0)
		{
			third = LedgerWorth.shortCoins(worth);
			thirdLabel = "worth";
		}
		else
		{
			third = hit == null ? "-" : String.valueOf(hit.getAmount());
			thirdLabel = "biggest hit";
		}
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		StringBuilder tip = new StringBuilder("<html>Seals are earned by hours played");
		tip.append(oneLife ? " on one life" : "").append(":<br>");
		for (Seal.Tier t : Seal.Tier.values())
		{
			tip.append(t == tier ? "<b>" : "").append(t.getLabel()).append(" ").append(t.getHours()).append("h")
				.append(t == tier ? "</b>" : "").append(t.next() != null ? "  ·  " : "");
		}
		if (worth > 0)
		{
			tip.append("<br>Worth: the account's worth as Codex Ledger last saw it.");
		}
		setToolTipText(tip.append("</html>").toString());
	}

	/** The seal's metal, kept a little warm so every tier sits in the ink and gold palette. */
	static Color metal(Seal.Tier tier)
	{
		Color raw;
		switch (tier)
		{
			case IRON:
				raw = new Color(140, 132, 124);
				break;
			case STEEL:
				raw = new Color(176, 178, 178);
				break;
			case BLACK:
				raw = new Color(92, 84, 78);
				break;
			case MITHRIL:
				raw = new Color(108, 116, 176);
				break;
			case ADAMANT:
				raw = new Color(96, 154, 108);
				break;
			case RUNE:
				raw = new Color(96, 168, 190);
				break;
			case DRAGON:
				raw = new Color(204, 66, 52);
				break;
			default:
				raw = new Color(184, 124, 74);
		}
		return Theme.mix(raw, Theme.GOLD, 0.15);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		Color accent = fallen ? Theme.BLOOD : Theme.GOLD;

		// The card: lit vellum, a gilt rim and an inner rule with diamonds at its corners.
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.CARD_TOP, metal(tier), fallen ? 0.04 : 0.08), 0, h, Theme.CARD));
		g.fill(shape);
		g.setColor(Theme.alpha(accent, 150));
		g.draw(shape);
		g.setColor(Theme.alpha(Theme.GOLD, 44));
		g.drawRoundRect(4, 4, w - 9, h - 9, 6, 6);
		for (int[] c : new int[][]{{4, 4}, {w - 5, 4}, {4, h - 5}, {w - 5, h - 5}})
		{
			diamond(g, c[0], c[1], 2, Theme.alpha(Theme.GOLD, 150));
		}

		Font small = Theme.small();
		Font bold = Theme.title();
		FontMetrics sm = g.getFontMetrics(small);
		FontMetrics bm = g.getFontMetrics(bold);
		int cx = w / 2;

		// Heading, flanked by hairlines that fade outwards.
		String heading = fallen ? "THE RUN HAS FALLEN" : oneLife ? "THE RUN LIVES" : "THE ACCOUNT'S RUN";
		int y = 12 + sm.getAscent();
		text(g, small, heading, cx - sm.stringWidth(heading) / 2, y, fallen ? Theme.BLOOD : Theme.GOLD);
		int hw = sm.stringWidth(heading) / 2 + 6;
		int ly = y - sm.getAscent() / 2;
		g.setPaint(new GradientPaint(12, 0, Theme.alpha(accent, 0), cx - hw, 0, Theme.alpha(accent, 140)));
		g.drawLine(12, ly, cx - hw, ly);
		g.setPaint(new GradientPaint(cx + hw, 0, Theme.alpha(accent, 140), w - 12, 0, Theme.alpha(accent, 0)));
		g.drawLine(cx + hw, ly, w - 12, ly);

		int my = y + 8 + R + 2;
		medallion(g, cx, my, bold, small, bm, sm);

		// The tier on a ribbon across the seal's foot.
		int ry = my + R - 4;
		ribbon(g, cx, ry, tier.getLabel().toUpperCase() + " SEAL", small, sm);

		y = ry + 18 + sm.getAscent();
		String when = "Begun " + began + "  ·  day " + days;
		text(g, small, when, cx - sm.stringWidth(when) / 2, y, Theme.TEXT_MUTED);

		// Three numbers: close calls survived, the closest of them, and what a death would cost.
		y += 9;
		g.setPaint(new GradientPaint(14, 0, Theme.alpha(Theme.GOLD, 0), cx, 0, Theme.alpha(Theme.GOLD, 110), true));
		g.drawLine(14, y, w - 14, y);
		int colW = (w - 16) / 3;
		String[] values = {String.valueOf(closeCalls), lowest < 0 ? "-" : lowest + " hp", third};
		String[] labels = {closeCalls == 1 ? "close call" : "close calls", "closest", thirdLabel};
		Color[] tints = {Theme.EMBER, Theme.BLOOD, Theme.GOLD_BRIGHT};
		int vy = y + 6 + bm.getAscent();
		for (int i = 0; i < 3; i++)
		{
			int colCx = 8 + colW * i + colW / 2;
			String v = clip(values[i], bm, colW - 4);
			text(g, bold, v, colCx - bm.stringWidth(v) / 2, vy, tints[i]);
			text(g, small, labels[i], colCx - sm.stringWidth(labels[i]) / 2, vy + 2 + sm.getAscent(), Theme.TEXT_FAINT);
			if (i > 0)
			{
				g.setColor(Theme.alpha(Theme.STROKE, 200));
				g.drawLine(8 + colW * i, y + 7, 8 + colW * i, vy + sm.getAscent() + 1);
			}
		}

		// The road to the next seal, or the word that the run broke it.
		int by = h - 13;
		String road;
		Color roadTint = Theme.TEXT_MUTED;
		Seal.Tier next = tier.next();
		if (fallen)
		{
			road = "The seal is broken";
			roadTint = Theme.BLOOD;
		}
		else if (next == null)
		{
			road = "The last seal. A legend of a run";
			roadTint = Theme.GOLD;
		}
		else
		{
			road = next.getLabel() + " seal in " + hoursToNext + "h";
		}
		text(g, small, road, cx - sm.stringWidth(road) / 2, by - 5, roadTint);
		if (!fallen && next != null)
		{
			int bx = 22;
			int bw = w - 44;
			g.setColor(Theme.INK_DEEP);
			g.fillRoundRect(bx, by, bw, 3, 3, 3);
			int fill = (int) Math.max(3, Math.round(bw * toNext));
			g.setPaint(new GradientPaint(bx, 0, metal(tier), bx + bw, 0, metal(next)));
			g.fillRoundRect(bx, by, fill, 3, 3, 3);
		}
		g.dispose();
	}

	/** The seal itself: a scalloped metal disc with the hours played struck into its ink face. */
	private void medallion(Graphics2D g, int cx, int cy, Font bold, Font small, FontMetrics bm, FontMetrics sm)
	{
		Color metal = fallen ? Theme.mix(metal(tier), Theme.INK, 0.45) : metal(tier);
		// A soft glow under the seal, warmer the higher the tier.
		float glow = R + 14;
		g.setPaint(new RadialGradientPaint(cx, cy, glow, new float[]{0f, 1f},
			new Color[]{Theme.alpha(fallen ? Theme.BLOOD : metal, 60), Theme.alpha(metal, 0)}));
		g.fill(new Ellipse2D.Float(cx - glow, cy - glow, glow * 2, glow * 2));

		Path2D scallops = new Path2D.Float();
		int bumps = 22;
		for (int i = 0; i <= bumps * 2; i++)
		{
			double a = Math.PI * 2 * i / (bumps * 2) - Math.PI / 2;
			double r = i % 2 == 0 ? R + 2 : R - 1;
			double x = cx + Math.cos(a) * r;
			double yy = cy + Math.sin(a) * r;
			if (i == 0)
			{
				scallops.moveTo(x, yy);
			}
			else
			{
				scallops.lineTo(x, yy);
			}
		}
		scallops.closePath();
		g.setPaint(new GradientPaint(cx - R, cy - R, Theme.mix(metal, Color.WHITE, 0.35), cx + R, cy + R,
			Theme.mix(metal, Theme.INK_DEEP, 0.45)));
		g.fill(scallops);
		g.setColor(Theme.alpha(Theme.INK_DEEP, 160));
		g.draw(scallops);

		int face = R - 7;
		g.setPaint(new GradientPaint(cx, cy - face, Theme.mix(Theme.INK, metal, 0.12), cx, cy + face, Theme.INK_DEEP));
		g.fill(new Ellipse2D.Float(cx - face, cy - face, face * 2, face * 2));
		g.setColor(Theme.alpha(Theme.GOLD, fallen ? 70 : 130));
		g.draw(new Ellipse2D.Float(cx - face + 2, cy - face + 2, face * 2 - 4, face * 2 - 4));

		if (fallen)
		{
			// A crack from rim to rim, through the face.
			g.setColor(Theme.BLOOD);
			g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D crack = new Path2D.Float();
			crack.moveTo(cx - 9, cy - R + 1);
			crack.lineTo(cx - 3, cy - 12);
			crack.lineTo(cx - 7, cy - 4);
			crack.lineTo(cx + 2, cy + 4);
			crack.lineTo(cx - 1, cy + 12);
			crack.lineTo(cx + 6, cy + R - 2);
			g.draw(crack);
			g.setStroke(new BasicStroke(1f));
		}

		String hours = hours(played);
		int ty = cy + bm.getAscent() / 2 - 5;
		text(g, bold, hours, cx - bm.stringWidth(hours) / 2, ty, fallen ? Theme.TEXT_MUTED : Theme.GOLD_BRIGHT);
		String under = "played";
		text(g, small, under, cx - sm.stringWidth(under) / 2, ty + sm.getAscent() - 2, Theme.TEXT_FAINT);

	}

	private void ribbon(Graphics2D g, int cx, int cy, String label, Font small, FontMetrics sm)
	{
		int half = Math.max(46, sm.stringWidth(label) / 2 + 12);
		int bh = 15;
		Color cloth = Theme.mix(fallen ? Theme.mix(metal(tier), Theme.INK, 0.45) : metal(tier), Theme.INK_DEEP, 0.55);
		// Forked tails behind the band.
		for (int side = -1; side <= 1; side += 2)
		{
			Path2D tail = new Path2D.Float();
			int inner = cx + side * (half - 8);
			int outer = cx + side * (half + 12);
			tail.moveTo(inner, cy + 3);
			tail.lineTo(outer, cy + 3);
			tail.lineTo(outer - side * 6, cy + 3 + bh / 2);
			tail.lineTo(outer, cy + 3 + bh);
			tail.lineTo(inner, cy + 3 + bh);
			tail.closePath();
			g.setColor(Theme.mix(cloth, Theme.INK_DEEP, 0.4));
			g.fill(tail);
			g.setColor(Theme.alpha(Theme.GOLD, 60));
			g.draw(tail);
		}
		g.setPaint(new GradientPaint(0, cy, Theme.mix(cloth, Color.WHITE, 0.08), 0, cy + bh, cloth));
		g.fillRect(cx - half, cy, half * 2, bh);
		g.setColor(Theme.alpha(Theme.GOLD, 150));
		g.drawLine(cx - half, cy, cx + half, cy);
		g.drawLine(cx - half, cy + bh, cx + half, cy + bh);
		text(g, small, label, cx - sm.stringWidth(label) / 2, cy + (bh + sm.getAscent()) / 2 - 1,
			fallen ? Theme.TEXT_MUTED : Theme.GOLD_BRIGHT);
	}

	/** Hours on the seal's face: "42h" from an hour, minutes before that, "<1m" at the very start. */
	static String hours(long ticks)
	{
		long minutes = Math.round(ticks * 0.6) / 60;
		if (minutes < 1)
		{
			return "<1m";
		}
		return minutes < 60 ? minutes + "m" : minutes / 60 + "h";
	}

	private static void diamond(Graphics2D g, int x, int y, int r, Color c)
	{
		Path2D d = new Path2D.Float();
		d.moveTo(x, y - r);
		d.lineTo(x + r, y);
		d.lineTo(x, y + r);
		d.lineTo(x - r, y);
		d.closePath();
		g.setColor(c);
		g.fill(d);
	}

	/** Text with the one-pixel shadow the game's own interfaces use. */
	private static void text(Graphics2D g, Font font, String s, int x, int y, Color c)
	{
		g.setFont(font);
		g.setColor(Theme.alpha(Color.BLACK, 170));
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	private static String clip(String s, FontMetrics fm, int width)
	{
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		String out = s;
		while (out.length() > 1 && fm.stringWidth(out + "…") > width)
		{
			out = out.substring(0, out.length() - 1);
		}
		return out + "…";
	}
}
