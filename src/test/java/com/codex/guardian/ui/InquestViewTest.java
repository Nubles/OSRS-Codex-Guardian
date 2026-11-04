package com.codex.guardian.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.Inquest;
import com.codex.guardian.core.Inquests;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import org.junit.Test;

/** The Inquest page: what it opens on, that it fits the sidebar, and that a row opens its inquest. */
public class InquestViewTest
{
	static
	{
		System.setProperty("java.awt.headless", "true");
	}

	private static Inquest death(long at, int hit)
	{
		Inquest.Recorder r = new Inquest.Recorder();
		for (int i = 0; i < 6; i++)
		{
			r.tick(new Inquest.Frame().hitpoints(60, 60).prayer(40, 52, 0).supplies(4, 80, 0, 0).foes(1));
		}
		r.hit(hit, "A player", null, false);
		r.tick(new Inquest.Frame().hitpoints(60 - hit, 60).prayer(40, 52, 0).supplies(4, 80, 0, 0).foes(1));
		r.hit(60, "A player", null, true);
		r.deathTick();
		return r.death("Fremennik Isles", new Inquest.Kit(null, 0, Collections.emptyList()), at);
	}

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

	@Test
	public void withoutInquestsThePagePromisesOne()
	{
		List<String> found = new ArrayList<>();
		texts(InquestView.build(null, false, -1, 0, at -> {}), found);
		assertTrue(found.toString(), found.contains("INQUEST"));
		assertTrue(found.toString(), String.join(" ", found).contains("Log in"));
	}

	@Test
	public void theNewestOpensAndARowOpensAnother()
	{
		Inquests kept = new Inquests();
		kept.add(death(1_000L, 10));
		kept.add(death(2_000L, 20));
		List<String> found = new ArrayList<>();
		StackPanel page = InquestView.build(kept, true, -1, 3_000L, at -> {});
		texts(page, found);
		String all = String.join(" | ", found);
		assertTrue(all, all.contains("You took 80 in 2 ticks"));
		assertTrue(all, all.contains("Took 70 in 2 ticks"));

		GuardianPanel panel = new GuardianPanel(80, () -> {}, v -> {});
		panel.onActivate();
		panel.updateInquests(kept);
		panel.openInquest(1_000L);
		panel.selectTab(3);
		found.clear();
		texts(panel, found);
		assertTrue(found.toString(), String.join(" ", found).contains("You took 70 in 2 ticks"));
		// A new inquest opens in place of the one being read.
		kept.add(death(4_000L, 15));
		panel.updateInquests(kept);
		found.clear();
		texts(panel, found);
		assertTrue(found.toString(), String.join(" ", found).contains("You took 75 in 2 ticks"));
	}

	@Test
	public void theChartAndRowsFitTheSidebar()
	{
		Inquests kept = new Inquests();
		kept.add(death(1_000L, 25));
		StackPanel page = InquestView.build(kept, true, -1, 3_000L, at -> {});
		page.setSize(209, 2000);
		layoutAll(page);
		for (Component c : page.getComponents())
		{
			assertTrue(c + " " + c.getWidth(), c.getX() >= 0 && c.getX() + c.getWidth() <= 209);
		}
		assertEquals(Mark.Kind.WARN, InquestView.mark(Inquest.Tone.UNUSED));
		assertEquals(Mark.Kind.BAD, InquestView.mark(Inquest.Tone.LACKING));
	}
}
