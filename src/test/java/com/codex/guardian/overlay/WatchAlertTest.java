package com.codex.guardian.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.RuleWatch;
import com.codex.guardian.core.WatchRule;
import com.codex.guardian.core.WatchRules;
import com.codex.guardian.ui.Theme;
import com.codex.guardian.ui.WatchView;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * A watch rule's alert in the game view: the edge flash in the rule's colour and the banner naming it, painted
 * headless. Set CODEX_SHOTS to a folder to keep the pictures.
 */
public class WatchAlertTest
{
	private static final int W = 765;
	private static final int H = 503;

	private static BufferedImage scene(RuleWatch.Alert alert, long age)
	{
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setPaint(new GradientPaint(0, 0, new Color(52, 60, 44), 0, H, new Color(30, 34, 26)));
		g.fillRect(0, 0, W, H);
		g.setColor(new Color(64, 74, 52));
		for (int i = 0; i < 40; i++)
		{
			g.fillOval((i * 97) % W, (i * 53) % H, 30 + i % 20, 12 + i % 9);
		}
		Color color = WatchView.color(alert.getRule().getTint());
		PerilVignette.paintFlash(g, new PerilVignette.Flash(color, 0.55, null), 0, 0, W, H, 108);
		Graphics2D b = (Graphics2D) g.create((W - WatchBanner.WIDTH) / 2, 12, WatchBanner.WIDTH, 80);
		Dimension d = WatchBanner.paint(b, new WatchBanner.Shown(alert.getRule().getName(), alert.getDetail(), color, 0), age);
		b.dispose();
		g.dispose();
		assertNotNull(d);
		assertEquals(WatchBanner.WIDTH, d.width);
		assertTrue(d.height > 20);
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

	private static RuleWatch.Alert fire(WatchRules set, RuleWatch.Reading before, RuleWatch.Reading now)
	{
		RuleWatch w = new RuleWatch();
		w.tick(set, before);
		List<RuleWatch.Alert> fired = w.tick(set, now);
		assertEquals(1, fired.size());
		return fired.get(0);
	}

	@Test
	public void lowPrayerFlashesTealWithItsName() throws Exception
	{
		WatchRules set = new WatchRules();
		set.add(WatchRules.Preset.LOW_PRAYER.rule());
		RuleWatch.Alert a = fire(set, new RuleWatch.Reading(0).stat("Prayer", 22), new RuleWatch.Reading(1).stat("Prayer", 9));
		assertEquals("Prayer 9, below 10", a.getDetail());
		keep(scene(a, 300), "watch-alert-prayer.png");
	}

	@Test
	public void aTeleBlockFlashesBlood() throws Exception
	{
		WatchRules set = new WatchRules();
		set.add(WatchRules.Preset.TELE_BLOCK.rule());
		List<RuleWatch.Alert> fired = new RuleWatch().message(set,
			"A Tele Block has been cast on you by Pkerr. It will expire in 5 minutes.", 10);
		assertEquals(1, fired.size());
		assertEquals(WatchRule.Tint.BLOOD, fired.get(0).getRule().getTint());
		keep(scene(fired.get(0), 300), "watch-alert-teleblock.png");
	}

	@Test
	public void theBannerFadesAndThenIsGone()
	{
		WatchRules set = new WatchRules();
		set.add(WatchRules.Preset.LOW_FOOD.rule());
		RuleWatch.Alert a = fire(set, new RuleWatch.Reading(0).food(6), new RuleWatch.Reading(1).food(2));
		assertEquals("2 pieces of food left, below 3", a.getDetail());
		scene(a, WatchBanner.SHOW_MILLIS - 100);
		WatchBanner banner = new WatchBanner();
		banner.show(new WatchBanner.Shown("x", "y", Theme.GOLD, System.currentTimeMillis() - WatchBanner.SHOW_MILLIS - 1));
		BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		assertEquals(null, banner.render(g));
		g.dispose();
	}
}
