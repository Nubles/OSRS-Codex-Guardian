package com.codex.guardian.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.Situation;
import com.codex.guardian.core.WatchRule;
import com.codex.guardian.core.WatchRules;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.Test;

/**
 * The Watch page: what it shows logged out and with rules, the editor's round trip to a saved set, and pictures of
 * it at the sidebar's width (set CODEX_SHOTS to a folder to keep them).
 */
public class WatchViewTest
{
	static
	{
		System.setProperty("java.awt.headless", "true");
	}

	private static final int WIDTH = 225;

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
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				texts(k, out);
			}
		}
	}

	private static <T extends Component> List<T> find(Component c, Class<T> type, List<T> out)
	{
		if (type.isInstance(c))
		{
			out.add(type.cast(c));
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				find(k, type, out);
			}
		}
		return out;
	}

	private static CodexButton button(Component root, String text)
	{
		for (CodexButton b : find(root, CodexButton.class, new ArrayList<>()))
		{
			if (b.getText().equals(text))
			{
				return b;
			}
		}
		throw new AssertionError("no button " + text);
	}

	private static void click(Component c)
	{
		if (c.getWidth() == 0)
		{
			c.setSize(c.getPreferredSize());
		}
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1));
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1));
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

	private static WatchRules sample()
	{
		WatchRules set = new WatchRules();
		set.add(WatchRules.Preset.LOW_PRAYER.rule());
		set.add(WatchRule.of("Clue drop", WatchRule.Trigger.CHAT_REGEX, "clue scroll|sneaking suspicion", null, 1)
			.actions(true, true, WatchRule.Tint.GOLD, true).cooldown(10));
		WatchRule sharks = WatchRule.of("Sharks", WatchRule.Trigger.ITEM_BELOW, "Shark", null, 4)
			.actions(true, false, null, false).cooldown(120);
		sharks.setOn(false);
		set.add(sharks);
		return set;
	}

	private static Situation loggedIn()
	{
		return new Situation.Builder().loggedIn(true).playerName("Nubles").mode("Hardcore Ironman", true)
			.hitpoints(78, 85).prayer(43, 70).runEnergy(64).build();
	}

	/** The whole side panel on its Watch tab, painted at the sidebar's width. */
	private static BufferedImage shot(GuardianPanel panel, int height)
	{
		panel.setSize(WIDTH, height);
		layoutAll(panel);
		BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		panel.printAll(g);
		g.dispose();
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

	private static GuardianPanel panel(WatchRules rules, AtomicReference<WatchRules> saved, AtomicReference<WatchRule> tried)
	{
		GuardianPanel panel = new GuardianPanel(80, () -> {}, v -> {});
		panel.setWatchActions(saved::set, tried::set);
		panel.onActivate();
		panel.update(loggedIn(), com.codex.guardian.core.Peril.calm());
		panel.updateWatch(rules, true, Collections.emptySet());
		panel.selectTab(4);
		return panel;
	}

	@Test
	public void loggedOutThePagePromisesRules()
	{
		WatchView view = new WatchView(() -> {});
		view.setRules(null, false, true, null);
		List<String> found = new ArrayList<>();
		texts(view.build(), found);
		assertTrue(found.toString(), String.join(" ", found).contains("Log in"));
	}

	@Test
	public void rulesShowAsCardsAndAddedPresetsAreNotOfferedAgain() throws Exception
	{
		AtomicReference<WatchRules> saved = new AtomicReference<>();
		AtomicReference<WatchRule> tried = new AtomicReference<>();
		GuardianPanel panel = panel(sample(), saved, tried);
		List<String> found = new ArrayList<>();
		texts(panel.watchPage(), found);
		String all = String.join(" | ", found);
		assertTrue(all, all.contains("Prayer running low"));
		assertTrue(all, all.contains("Prayer falls below 10"));
		assertTrue(all, all.contains("Message matches /clue scroll|sneaking suspicion/"));
		assertTrue(all, all.contains("Notify, teal flash  ·  rests 30s"));
		assertTrue(all, all.contains("Shark falls below 4"));
		// Prayer is added, so it is not offered; the others are.
		assertEquals(1, Collections.frequency(found, "Prayer running low"));
		assertTrue(all, found.contains("Tele Blocked"));
		keep(shot(panel, 760), "watch-rules.png");

		// Adding a ready-made rule saves the set with it.
		click(button(panel.watchPage(), "Add"));
		assertNotNull(saved.get());
		assertEquals(4, saved.get().getRules().size());
	}

	@Test
	public void anEmptySetOffersEveryPreset() throws Exception
	{
		GuardianPanel panel = panel(new WatchRules(), new AtomicReference<>(), new AtomicReference<>());
		List<String> found = new ArrayList<>();
		texts(panel.watchPage(), found);
		for (WatchRules.Preset p : WatchRules.Preset.all())
		{
			assertTrue(p.getLabel(), found.contains(p.getLabel()));
		}
		keep(shot(panel, 640), "watch-empty.png");
	}

	@Test
	public void theEditorSavesAWholeNewRule() throws Exception
	{
		AtomicReference<WatchRules> saved = new AtomicReference<>();
		AtomicReference<WatchRule> tried = new AtomicReference<>();
		GuardianPanel panel = panel(sample(), saved, tried);
		click(button(panel.watchPage(), "Write a rule"));
		WatchView view = panel.watchView();
		WatchRule draft = view.draft();
		assertNotNull(draft);
		// A bad pattern is told, and Save keeps nothing.
		draft.setTrigger(WatchRule.Trigger.CHAT_REGEX);
		draft.setText("(unclosed");
		assertNotNull(WatchView.problemWith(draft));
		click(button(panel.watchPage(), "Save"));
		assertNull(saved.get());

		// The draft stays open with what was typed; mend it and the page shows the mended rule.
		draft.setText("tele ?block has been cast");
		draft.setName("Teleblock");
		draft.setFlash(true);
		draft.setTint(WatchRule.Tint.BLOOD);
		draft.setChat(true);
		panel.updateWatch(sample(), true, Collections.emptySet());
		List<JTextField> fields = find(panel.watchPage(), JTextField.class, new ArrayList<>());
		assertFalse(fields.isEmpty());
		keep(shot(panel, 900), "watch-editor.png");

		click(button(panel.watchPage(), "Try it"));
		assertNotNull(tried.get());
		assertEquals("Teleblock", tried.get().getName());

		click(button(panel.watchPage(), "Save"));
		assertNotNull(saved.get());
		WatchRule kept = saved.get().getRules().get(3);
		assertEquals("Teleblock", kept.getName());
		assertEquals(WatchRule.Tint.BLOOD, kept.getTint());
		assertTrue(kept.isComplete());
		assertNull(view.draft());
	}

	@Test
	public void theEditorTellsWhatIsWrongWithAPattern() throws Exception
	{
		WatchRules rules = sample();
		GuardianPanel panel = panel(rules, new AtomicReference<>(), new AtomicReference<>());
		WatchView view = panel.watchView();
		view.open(2);
		JTextField pattern = null;
		for (JTextField f : find(panel.watchPage(), JTextField.class, new ArrayList<>()))
		{
			if (f.getText().startsWith("clue"))
			{
				pattern = f;
			}
		}
		assertNotNull(pattern);
		pattern.setText("clue (scroll");
		List<String> found = new ArrayList<>();
		texts(panel.watchPage(), found);
		assertTrue(found.toString(), String.join(" ", found).contains("Unclosed group"));
		keep(shot(panel, 700), "watch-editor-bad-pattern.png");
	}

	@Test
	public void deletingTakesTwoClicks()
	{
		AtomicReference<WatchRules> saved = new AtomicReference<>();
		GuardianPanel panel = panel(sample(), saved, new AtomicReference<>());
		panel.watchView().open(3);
		click(button(panel.watchPage(), "Delete rule"));
		assertNull(saved.get());
		click(button(panel.watchPage(), "Click again to delete"));
		assertEquals(2, saved.get().getRules().size());
		assertNull(saved.get().find(3));
	}

	@Test
	public void aCardSwitchTurnsARuleOffWithoutOpeningIt()
	{
		AtomicReference<WatchRules> saved = new AtomicReference<>();
		GuardianPanel panel = panel(sample(), saved, new AtomicReference<>());
		Controls.Switch first = find(panel.watchPage(), Controls.Switch.class, new ArrayList<>()).get(0);
		first.dispatchEvent(new MouseEvent(first, MouseEvent.MOUSE_RELEASED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1));
		assertNotNull(saved.get());
		assertFalse(saved.get().find(1).isOn());
		assertNull(panel.watchView().draft());
	}

	@Test
	public void everyCardFitsTheSidebar()
	{
		GuardianPanel panel = panel(sample(), new AtomicReference<>(), new AtomicReference<>());
		panel.watchView().open(2);
		shot(panel, 1200);
		for (Component c : ((Container) panel.watchPage()).getComponents())
		{
			assertTrue(c + " is " + c.getWidth(), c.getWidth() <= WIDTH - 16);
			assertTrue(c + " wants " + c.getPreferredSize().width, c.getPreferredSize().width <= WIDTH - 16);
		}
	}
}
