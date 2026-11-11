package com.codex.guardian.overlay;

import static org.junit.Assert.assertEquals;

import com.codex.guardian.core.Peril;
import com.codex.guardian.core.PkWatch;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.Collections;
import org.junit.Test;

/**
 * Every overlay leaves the renderer's graphics as it found them: the clip above all (clearing it with setClip(null)
 * breaks RuneLite's overlay renderer), and the stroke, font and transform too.
 */
public class ClipTest
{
	static
	{
		System.setProperty("java.awt.headless", "true");
	}

	private interface Paint
	{
		void on(Graphics2D g);
	}

	private static void unchanged(Paint paint)
	{
		BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		Rectangle clip = new Rectangle(10, 10, 500, 400);
		g.setClip(clip);
		Stroke stroke = new BasicStroke(2f);
		g.setStroke(stroke);
		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
		g.setFont(font);
		g.setColor(Color.MAGENTA);
		paint.on(g);
		Shape after = g.getClip();
		assertEquals(clip, after == null ? null : after.getBounds());
		assertEquals(stroke, g.getStroke());
		assertEquals(font, g.getFont());
		assertEquals(Color.MAGENTA, g.getColor());
		assertEquals(0, g.getTransform().getTranslateX(), 0);
		g.dispose();
	}

	@Test
	public void thePlaqueKeepsTheClip()
	{
		WardOverlay overlay = new WardOverlay();
		WardOverlay.Briefing briefing = new WardOverlay.Briefing("ENTERING", "Vorkath", "HIGH RISK", com.codex.guardian.ui.Theme.EMBER,
			Collections.singletonList(new WardOverlay.Check(com.codex.guardian.ui.Mark.Kind.BAD, "No food")));
		overlay.setView(new WardOverlay.View(Peril.Level.PERIL, 4, 99, "No food", "No way out", false, true, briefing,
			"Teleport now", WardOverlay.Pk.of(new PkWatch().getLast(), null)));
		unchanged(overlay::render);
	}

	@Test
	public void theEdgeGlowKeepsTheClip()
	{
		net.runelite.api.Client client = org.mockito.Mockito.mock(net.runelite.api.Client.class);
		org.mockito.Mockito.when(client.getViewportWidth()).thenReturn(700);
		org.mockito.Mockito.when(client.getViewportHeight()).thenReturn(500);
		PerilVignette vignette = new PerilVignette(client);
		vignette.setActive(true);
		vignette.flashRule(Color.RED, System.currentTimeMillis());
		unchanged(vignette::render);
	}

	@Test
	public void aWatchBannerKeepsTheClip()
	{
		WatchBanner banner = new WatchBanner();
		banner.show(new WatchBanner.Shown("Low prayer", "Prayer fell below 10", Color.RED, System.currentTimeMillis()));
		unchanged(banner::render);
		unchanged(g -> WatchBanner.paint(g, new WatchBanner.Shown("Low prayer", "Prayer fell below 10", Color.RED, 0), 200));
	}
}
