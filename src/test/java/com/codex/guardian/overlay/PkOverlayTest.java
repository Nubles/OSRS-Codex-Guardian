package com.codex.guardian.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.Peril;
import com.codex.guardian.core.PkWatch;
import com.codex.guardian.ui.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * The PKer alarm on the plaque and the screen edge, painted headless. Set CODEX_SHOTS to a folder to keep the
 * pictures.
 */
public class PkOverlayTest
{
	private static final int W = 765;
	private static final int H = 503;

	/** A sighting, and the same player two tiles further off a tick before, so they read as closing when they moved. */
	private static PkWatch.Sighting[] seen(String name, int combat, int wild, int distance, boolean skulled, int weapon,
		boolean onYou, boolean closing)
	{
		return new PkWatch.Sighting[]{
			new PkWatch.Sighting(name, combat, wild, distance + (closing ? 2 : 0), skulled, weapon, false, false),
			new PkWatch.Sighting(name, combat, wild, distance, skulled, weapon, false, onYou)};
	}

	private static PkWatch.Alarm alarm(int wild, boolean multi, PkWatch.Footing footing, PkWatch.Sighting[]... players)
	{
		PkWatch w = new PkWatch();
		PkWatch.Sighting[] before = new PkWatch.Sighting[players.length];
		PkWatch.Sighting[] now = new PkWatch.Sighting[players.length];
		for (int i = 0; i < players.length; i++)
		{
			before[i] = players[i][0];
			now[i] = players[i][1];
		}
		w.update(92, wild, multi, Arrays.asList(before), footing);
		return w.update(92, wild, multi, Arrays.asList(now), footing);
	}

	private static BufferedImage scene(WardOverlay.View view, PerilVignette.Flash flash, long millis)
	{
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		// Something like the Wilderness at dusk under the overlay.
		g.setPaint(new GradientPaint(0, 0, new Color(58, 52, 40), 0, H, new Color(36, 30, 24)));
		g.fillRect(0, 0, W, H);
		g.setColor(new Color(70, 62, 46));
		for (int i = 0; i < 40; i++)
		{
			g.fillOval((i * 97) % W, (i * 53) % H, 30 + i % 20, 12 + i % 9);
		}
		if (flash != null)
		{
			PerilVignette.paintFlash(g, flash, 0, 0, W, H, millis);
		}
		WardOverlay overlay = new WardOverlay();
		overlay.setView(view);
		Graphics2D o = (Graphics2D) g.create(10, 10, W, H);
		Dimension d = overlay.render(o);
		o.dispose();
		g.dispose();
		assertNotNull(d);
		assertTrue(d.height > 40);
		return img;
	}

	private static void keep(BufferedImage img, String name) throws Exception
	{
		String dir = System.getenv("CODEX_SHOTS");
		if (dir != null && !dir.isEmpty())
		{
			ImageIO.write(img, "png", new File(dir, name));
		}
	}

	private static WardOverlay.View view(Peril.Level level, boolean plaque, PkWatch.Alarm a, String bracket)
	{
		return new WardOverlay.View(level, 71, 92, "11 food  220 hp", "Glory", true, plaque, null, null,
			WardOverlay.Pk.of(a, bracket));
	}

	@Test
	public void aQuietAlarmDrawsNothing()
	{
		assertNull(WardOverlay.Pk.of(new PkWatch().update(92, 0, false, Collections.emptyList(), null), ""));
		assertNull(PerilVignette.Flash.of(PkWatch.Strength.NONE, null));
	}

	@Test
	public void theFlashBeatsTwiceThenRests()
	{
		assertEquals(1.0, PerilVignette.beat(108), 0.01);
		assertEquals(0.8, PerilVignette.beat(396), 0.01);
		assertEquals(0.0, PerilVignette.beat(800), 0.0);
	}

	@Test
	public void watchingAtLevelEight() throws Exception
	{
		PkWatch.Footing f = new PkWatch.Footing(8, false, "Glory", 30, 0, 0, 60);
		PkWatch.Alarm a = alarm(8, false, f, seen("Wanderer", 96, 8, 11, false, -1, false, false));
		assertEquals(PkWatch.Strength.WATCH, a.getStrength());
		keep(scene(view(Peril.Level.CALM, true, a, PkWatch.bracket(92, 8)), PerilVignette.Flash.of(a.getStrength(), null), 108),
			"pk-watch.png");
	}

	@Test
	public void skulledAndClosingAtTwentySix() throws Exception
	{
		PkWatch.Footing f = new PkWatch.Footing(26, false, "Varrock teleport", 20, 47, 0, 207);
		PkWatch.Alarm a = alarm(26, false, f, seen("Skulled", 101, 26, 8, true, -1, false, true));
		assertEquals(PkWatch.Strength.WARN, a.getStrength());
		keep(scene(view(Peril.Level.WARY, true, a, PkWatch.bracket(92, 26)),
			PerilVignette.Flash.of(a.getStrength(), new Point(640, 140)), 108), "pk-warn.png");
	}

	@Test
	public void clawsOutInMultiWithThePlaqueOff() throws Exception
	{
		PkWatch.Footing f = new PkWatch.Footing(34, false, "Glory", 30, 111, 31, 271);
		PkWatch.Alarm a = alarm(34, true, f,
			seen("Clawer", 104, 34, 4, true, ItemID.DRAGON_CLAWS, false, true),
			seen("Second", 88, 34, 9, true, -1, false, false));
		assertEquals(PkWatch.Strength.DANGER, a.getStrength());
		keep(scene(view(Peril.Level.CALM, false, a, PkWatch.bracket(92, 34)),
			PerilVignette.Flash.of(a.getStrength(), new Point(120, 420)), 108), "pk-danger-multi.png");
	}

	@Test
	public void teleblockedAndOnYou() throws Exception
	{
		PkWatch.Footing f = new PkWatch.Footing(14, true, "Glory", 30, 0, 0, 108);
		PkWatch.Alarm a = alarm(14, false, f, seen("Tbler", 99, 14, 1, true, ItemID.AGS, true, true));
		assertEquals("PKer on you", a.headline());
		keep(scene(view(Peril.Level.DANGER, true, a, PkWatch.bracket(92, 14)),
			PerilVignette.Flash.of(a.getStrength(), new Point(400, 260)), 396), "pk-teleblocked.png");
	}
}
