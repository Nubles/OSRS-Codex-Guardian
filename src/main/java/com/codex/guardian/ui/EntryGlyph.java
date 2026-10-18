package com.codex.guardian.ui;

import com.codex.guardian.core.Journal;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * The journal's margin: a drawn emblem for each kind of entry, threaded on a gilt line
 * that runs down the page from the first entry to the last.
 */
class EntryGlyph extends JComponent
{
	static final int WIDTH = 22;
	private static final int ICON = 16;

	private final Journal.Type type;
	private final boolean first;
	private final boolean last;

	EntryGlyph(Journal.Type type, boolean first, boolean last)
	{
		this.type = type;
		this.first = first;
		this.last = last;
		setPreferredSize(new Dimension(WIDTH, ICON + 4));
		setOpaque(false);
	}

	static Color color(Journal.Type type)
	{
		switch (type)
		{
			case DEATH:
			case STATUS_LOST:
				return Theme.BLOOD;
			case CLOSE_CALL:
				return Theme.EMBER;
			case DISCONNECT:
				return Theme.BLOCKED;
			case MILESTONE:
				return Theme.GOLD_BRIGHT;
			case ZONE:
				return Theme.GOLD;
			case SAFE_DEATH:
				return Theme.GREEN;
			default:
				return Theme.TEAL;
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int cx = WIDTH / 2;
		int top = 2;
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 110));
		if (!first)
		{
			g.drawLine(cx, 0, cx, top);
		}
		if (!last)
		{
			g.drawLine(cx, top + ICON, cx, getHeight());
		}
		paintIcon(g, type, cx - ICON / 2f, top, ICON);
		g.dispose();
	}

	static void paintIcon(Graphics2D g, Journal.Type type, float x, float y, float s)
	{
		Color c = color(type);
		float cx = x + s / 2;
		float cy = y + s / 2;
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(x, y, s, s));
		g.setColor(Theme.alpha(c, 150));
		g.setStroke(new BasicStroke(1.2f));
		g.draw(new Ellipse2D.Float(x + 0.5f, y + 0.5f, s - 1, s - 1));
		g.setColor(c);
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		switch (type)
		{
			case RUN_STARTED:
			{
				g.fill(Shield.path(cx - s * 0.22f, y + s * 0.24f, s * 0.44f, s * 0.54f));
				break;
			}
			case ZONE:
			{
				// An archway.
				Path2D arch = new Path2D.Float();
				arch.moveTo(cx - s * 0.2f, y + s * 0.74f);
				arch.lineTo(cx - s * 0.2f, y + s * 0.44f);
				arch.append(new Arc2D.Float(cx - s * 0.2f, y + s * 0.24f, s * 0.4f, s * 0.4f, 180, -180, Arc2D.OPEN), true);
				arch.lineTo(cx + s * 0.2f, y + s * 0.74f);
				g.draw(arch);
				break;
			}
			case CLOSE_CALL:
			{
				g.fill(Shield.flame(cx, y + s * 0.78f, s * 0.56f));
				break;
			}
			case DEATH:
			case SAFE_DEATH:
			{
				// A small skull.
				g.fill(new Ellipse2D.Float(cx - s * 0.24f, y + s * 0.2f, s * 0.48f, s * 0.42f));
				g.fill(new RoundRectangle2D.Float(cx - s * 0.14f, y + s * 0.52f, s * 0.28f, s * 0.22f, 2, 2));
				g.setColor(Theme.INK_DEEP);
				g.fill(new Ellipse2D.Float(cx - s * 0.16f, y + s * 0.36f, s * 0.12f, s * 0.12f));
				g.fill(new Ellipse2D.Float(cx + s * 0.04f, y + s * 0.36f, s * 0.12f, s * 0.12f));
				break;
			}
			case STATUS_LOST:
			{
				Path2D shield = Shield.path(cx - s * 0.22f, y + s * 0.22f, s * 0.44f, s * 0.56f);
				g.draw(shield);
				Path2D crack = new Path2D.Float();
				crack.moveTo(cx + s * 0.02f, y + s * 0.24f);
				crack.lineTo(cx - s * 0.06f, y + s * 0.44f);
				crack.lineTo(cx + s * 0.06f, y + s * 0.54f);
				crack.lineTo(cx - s * 0.02f, y + s * 0.74f);
				g.draw(crack);
				break;
			}
			case DISCONNECT:
			{
				// Two links, pulled apart.
				g.draw(new RoundRectangle2D.Float(cx - s * 0.34f, cy - s * 0.1f, s * 0.3f, s * 0.2f, s * 0.2f, s * 0.2f));
				g.draw(new RoundRectangle2D.Float(cx + s * 0.04f, cy - s * 0.1f, s * 0.3f, s * 0.2f, s * 0.2f, s * 0.2f));
				break;
			}
			default:
			{
				// A four point star for a milestone.
				Path2D star = new Path2D.Float();
				float r = s * 0.3f;
				float k = s * 0.09f;
				star.moveTo(cx, cy - r);
				star.lineTo(cx + k, cy - k);
				star.lineTo(cx + r, cy);
				star.lineTo(cx + k, cy + k);
				star.lineTo(cx, cy + r);
				star.lineTo(cx - k, cy + k);
				star.lineTo(cx - r, cy);
				star.lineTo(cx - k, cy - k);
				star.closePath();
				g.fill(star);
			}
		}
	}
}
