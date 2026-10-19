package com.codex.guardian.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.Journal;
import com.codex.guardian.core.Peril;
import com.codex.guardian.core.PerilAssessor;
import com.codex.guardian.core.Readiness;
import com.codex.guardian.core.Situation;
import com.codex.guardian.core.Supplies;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import org.junit.Test;

/** Each figure in one place: the crest's vitals are not repeated below it, and the seal card adds only a name. */
public class CohesionTest
{
	private static void texts(Component c, List<String> out)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null)
		{
			out.add(((JLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				texts(k, out);
			}
		}
	}

	private static Situation praying()
	{
		Readiness r = Supplies.assess(Arrays.asList(new Supplies.Item("Shark", 18), new Supplies.Item("Prayer potion(4)", 4)),
			Collections.emptyList(), 99, true);
		return new Situation.Builder().loggedIn(true).playerName("Nubles").mode("Hardcore Ironman", true)
			.hitpoints(70, 85).prayer(60, 70).readiness(r).runEnergy(90).build();
	}

	@Test
	public void thePrayerPointsShowOnceInTheCrestNotAgainInReadiness()
	{
		Situation s = praying();
		Peril peril = new PerilAssessor(80).assess(s);
		List<String> ward = new ArrayList<>();
		texts(WardView.build(s, peril, new PerilGauge(), 80, null), ward);
		for (String t : ward)
		{
			assertFalse(t, t.contains("60/70"));
		}
		assertTrue(ward.toString(), ward.contains("16 doses"));

		WardCrest crest = new WardCrest();
		crest.update(s, peril, Journal.fresh(0));
		List<String> top = new ArrayList<>();
		texts(crest, top);
		assertTrue(top.contains("60/70"));
		assertTrue(top.contains("90%"));
		// Close calls are the seal's to tell.
		assertFalse(top.contains("Close calls"));
	}

	@Test
	public void runEnergyIsADashUntilItIsKnown()
	{
		Situation s = new Situation.Builder().loggedIn(true).hitpoints(10, 10).prayer(1, 1).build();
		WardCrest crest = new WardCrest();
		crest.update(s, Peril.calm(), null);
		List<String> top = new ArrayList<>();
		texts(crest, top);
		assertFalse(top.toString(), top.contains("-1%"));
	}

	@Test
	public void theSealCardPutsTheNameAboveTheModeAndNeverTwice()
	{
		Journal run = Journal.fresh(0);
		SealShare.Spec named = SealShare.Spec.of(run, true, -1, " Nubles ", "Hardcore Ironman", 0, ZoneOffset.UTC);
		assertEquals("Nubles", named.heading());
		assertEquals("Hardcore Ironman", named.subheading());
		// Names off: the mode moves up and is not repeated under itself.
		SealShare.Spec anonymous = SealShare.Spec.of(run, true, -1, null, "Hardcore Ironman", 0, ZoneOffset.UTC);
		assertEquals("Hardcore Ironman", anonymous.heading());
		assertNull(anonymous.subheading());
		assertEquals("One life", SealShare.Spec.of(run, true, -1, "", null, 0, ZoneOffset.UTC).heading());
		assertNull(SealShare.Spec.of(null, true, -1, "Nubles", null, 0, ZoneOffset.UTC));
		assertEquals("codex-seal-1970-01-01.png", SealShare.fileName(0, ZoneOffset.UTC));
		BufferedImage card = SealShare.render(named);
		assertNotNull(card);
		assertEquals(SealShare.WIDTH * SealShare.SCALE, card.getWidth());
	}
}
