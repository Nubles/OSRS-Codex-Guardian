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

	@Test
	public void theSealCardLeavesWorthOffUnlessAskedButTheJournalKeepsIt()
	{
		Journal run = Journal.fresh(0);
		// The card is shared, so worth goes on it only with "Worth on cards" on; the seal in the panel always shows it.
		SealShare.Spec hidden = SealShare.Spec.of(run, true, 1_234_567_890L, "Nubles", "Hardcore Ironman", 0, ZoneOffset.UTC, false);
		assertEquals(-1, hidden.worth);
		SealShare.Spec shown = SealShare.Spec.of(run, true, 1_234_567_890L, "Nubles", "Hardcore Ironman", 0, ZoneOffset.UTC, true);
		assertEquals(1_234_567_890L, shown.worth);
	}

	@Test
	public void theCrestShowsNewEnergyWithoutRebuildingTheWard()
	{
		GuardianPanel panel = new GuardianPanel(80, () -> {}, v -> {});
		Situation s = praying();
		panel.update(s, Peril.calm());
		Component wardBefore = panel.wardView();
		panel.updateCrest(new Situation.Builder().loggedIn(true).playerName("Nubles").mode("Hardcore Ironman", true)
			.hitpoints(70, 85).prayer(60, 70).readiness(s.getReadiness()).runEnergy(41).build());
		List<String> top = new ArrayList<>();
		texts(panel, top);
		assertTrue(top.toString(), top.contains("41%"));
		assertTrue(wardBefore == panel.wardView());
	}

	private static Journal longRun()
	{
		Journal run = Journal.fresh(0);
		for (int i = 0; i < 786_000; i++)
		{
			run.tick(false);
		}
		return run;
	}

	private static void buttons(Component c, List<CodexButton> out)
	{
		if (c instanceof CodexButton)
		{
			out.add((CodexButton) c);
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				buttons(k, out);
			}
		}
	}

	@Test
	public void theJournalTabShowsTheRunsLengthOnlyOnTheSeal()
	{
		// The seal's face reads "131h played"; the crest above it said "This run 131h 0m" as well.
		GuardianPanel panel = new GuardianPanel(80, () -> {}, v -> {});
		panel.onActivate();
		panel.update(praying(), Peril.calm());
		panel.updateJournal(longRun());
		panel.selectTab(1);
		List<String> top = new ArrayList<>();
		texts(panel, top);
		assertFalse(top.toString(), top.contains("This run"));
		assertFalse(top.toString(), top.contains("131h 0m"));
		assertTrue(top.toString(), top.contains("Combat"));
		// The Ward and Perils tabs have no seal, so the crest keeps the run there.
		panel.selectTab(0);
		top.clear();
		texts(panel, top);
		assertTrue(top.toString(), top.contains("This run"));
		assertTrue(top.toString(), top.contains("131h 0m"));
	}

	@Test
	public void theSealsButtonsNameTheCardAsTheOtherPluginsDoAndFit()
	{
		GuardianPanel panel = new GuardianPanel(80, () -> {}, v -> {});
		panel.setShare(new ShareActions()
		{
			public void save(BufferedImage card, String file)
			{
			}

			public void copy(BufferedImage card)
			{
			}
		}, () -> true);
		panel.onActivate();
		panel.update(praying(), Peril.calm());
		panel.updateJournal(longRun());
		panel.setSize(225, 900);
		panel.selectTab(1);
		Component page = panel.journalPage();
		page.setSize(209, 2000);
		layoutAll(page);
		List<CodexButton> found = new ArrayList<>();
		buttons(page, found);
		List<String> labels = new ArrayList<>();
		for (CodexButton b : found)
		{
			labels.add(b.getText());
			// Every button sits whole inside the page, none cut at the sidebar's edge.
			java.awt.Point at = javax.swing.SwingUtilities.convertPoint(b.getParent(), b.getLocation(), page);
			assertTrue(b.getText() + " at " + at.x, at.x >= 0 && at.x + b.getWidth() <= 209);
			assertEquals(b.getText(), b.getPreferredSize().width, b.getWidth());
		}
		assertTrue(labels.toString(), labels.contains("Save seal card"));
		assertTrue(labels.toString(), labels.contains("Copy"));
		assertTrue(labels.toString(), labels.contains("Start a new run"));
	}

	private static void layoutAll(Component c)
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
}
