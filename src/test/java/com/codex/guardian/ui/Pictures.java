package com.codex.guardian.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JLabel;

/**
 * Paints the side panel headless at the sidebar's width for tests, and keeps the picture when CODEX_SHOTS names a
 * folder. Also finds what a page says and clicks its buttons.
 */
final class Pictures
{
	static
	{
		System.setProperty("java.awt.headless", "true");
	}

	static final int WIDTH = 225;

	private Pictures()
	{
	}

	static BufferedImage shot(Component panel, int height)
	{
		panel.setSize(WIDTH, height);
		layoutAll(panel);
		BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		panel.printAll(g);
		g.dispose();
		return img;
	}

	static void keep(BufferedImage img, String name)
	{
		String dir = System.getenv("CODEX_SHOTS");
		if (dir == null || dir.isEmpty())
		{
			return;
		}
		try
		{
			ImageIO.write(img, "png", new File(dir, name));
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException(e);
		}
	}

	static void layoutAll(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				layoutAll(k);
			}
		}
	}

	/** Everything a page says, labels and buttons, in one string. */
	static String text(Component c)
	{
		List<String> out = new ArrayList<>();
		texts(c, out);
		return String.join(" | ", out);
	}

	private static void texts(Component c, List<String> out)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null)
		{
			out.add(((JLabel) c).getText());
		}
		if (c instanceof CodexButton)
		{
			out.add(((CodexButton) c).getText());
		}
		if (c instanceof Pill)
		{
			out.add(((Pill) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				texts(k, out);
			}
		}
	}

	static CodexButton button(Component c, String text)
	{
		if (c instanceof CodexButton && ((CodexButton) c).getText().equals(text))
		{
			return (CodexButton) c;
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				CodexButton found = button(k, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	static void click(Component c)
	{
		if (c.getWidth() == 0)
		{
			c.setSize(c.getPreferredSize());
		}
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1));
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1));
	}
}
