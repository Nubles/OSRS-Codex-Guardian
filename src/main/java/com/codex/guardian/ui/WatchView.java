package com.codex.guardian.ui;

import com.codex.guardian.core.SafeRegex;
import com.codex.guardian.core.WatchRule;
import com.codex.guardian.core.WatchRules;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;

/**
 * The Watch page: the player's own alarms for this account, each a card that opens into its editor in place, and
 * below them the ready-made rules Guardian offers. Swing thread only. Edits are made on a draft and handed back
 * whole on Save, so the game never reads a rule half typed.
 */
public class WatchView
{
	private static final int TEXT_WIDTH = 170;
	private static final List<WatchRule.Trigger> TRIGGERS = Arrays.asList(WatchRule.Trigger.values());
	private static final List<String> SKILLS = skills();

	private final Runnable rebuild;
	private Consumer<WatchRules> onSave = r -> {};
	private Consumer<WatchRule> onTry = r -> {};

	private WatchRules rules;
	private boolean loggedIn;
	private boolean enabled = true;
	private Set<Integer> paused = Collections.emptySet();
	/** The rule open in the editor by id, 0 for a new one not yet kept, -1 for none. */
	private int openId = -1;
	private WatchRule draft;
	private int deletingId = -1;
	private JLabel problem;
	private String tried;

	/** @param rebuild called when the page must be built again, to show it in place of the old one */
	public WatchView(Runnable rebuild)
	{
		this.rebuild = rebuild;
	}

	private static List<String> skills()
	{
		List<String> names = new ArrayList<>();
		for (Skill s : Skill.values())
		{
			names.add(s.getName());
		}
		return Collections.unmodifiableList(names);
	}

	/** Where Save sends the account's whole set of rules, and where Try sends a rule to fire once now. */
	public void setActions(Consumer<WatchRules> onSave, Consumer<WatchRule> onTry)
	{
		this.onSave = onSave == null ? r -> {} : onSave;
		this.onTry = onTry == null ? r -> {} : onTry;
	}

	/** A private copy of this account's rules, or null when logged out; the open editor stays open if its rule does. */
	public void setRules(WatchRules rules, boolean loggedIn, boolean enabled, Set<Integer> paused)
	{
		this.rules = rules;
		this.loggedIn = loggedIn;
		this.enabled = enabled;
		this.paused = paused == null ? Collections.emptySet() : paused;
		if (rules == null || openId > 0 && rules.find(openId) == null)
		{
			openId = -1;
			draft = null;
		}
	}

	/** Opens a rule in the editor, as a click on its card would; 0 opens a new one. For tests and the panel. */
	void open(int id)
	{
		if (rules == null)
		{
			return;
		}
		if (id == 0)
		{
			draft = WatchRule.blank();
		}
		else
		{
			WatchRule r = rules.find(id);
			if (r == null)
			{
				return;
			}
			draft = r.copy();
		}
		openId = id;
		deletingId = -1;
		tried = null;
		rebuild.run();
	}

	WatchRule draft()
	{
		return draft;
	}

	public JComponent build()
	{
		StackPanel page = new StackPanel(6, Theme.GUTTER);
		int count = rules == null ? 0 : rules.getRules().size();
		page.add(new SectionHeader("Watch rules", rules == null ? null : count + " of " + WatchRules.MAX_RULES));
		if (!loggedIn || rules == null)
		{
			page.add(Rows.note("Log in to see and write this account's watch rules: your own alarms for a game message, "
				+ "a skill or your food falling low, a full pack or standing idle.", TEXT_WIDTH + 10));
			return page;
		}
		if (!enabled)
		{
			page.add(Rows.reason(Mark.Kind.WARN, "Watch rules are off in Guardian's settings; they are kept, but none will fire."));
		}
		if (count == 0 && openId != 0)
		{
			page.add(Rows.note("Your own alarms, kept for this account. Each fires as its moment comes, then rests "
				+ "before it may fire again. Start from one below or write your own.", TEXT_WIDTH + 10));
		}
		for (WatchRule r : rules.getRules())
		{
			page.add(r.getId() == openId ? editor() : card(r));
		}
		if (openId == 0)
		{
			page.add(editor());
		}
		else if (!rules.isFull())
		{
			JPanel add = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			add.setOpaque(false);
			add.add(new CodexButton("Write a rule", true, () -> open(0)));
			page.add(add);
		}

		List<WatchRules.Preset> offered = new ArrayList<>();
		for (WatchRules.Preset p : WatchRules.Preset.all())
		{
			if (!rules.has(p))
			{
				offered.add(p);
			}
		}
		if (!offered.isEmpty() && !rules.isFull())
		{
			page.space(2);
			page.add(new SectionHeader("Ready-made"));
			List<JComponent> rows = new ArrayList<>();
			for (WatchRules.Preset p : offered)
			{
				rows.add(presetRow(p));
			}
			page.add(Rows.ledger(rows));
		}
		return page;
	}

	private static Color tint(WatchRule.Tint tint)
	{
		switch (tint)
		{
			case TEAL:
				return Theme.TEAL;
			case EMBER:
				return Theme.EMBER;
			case BLOOD:
				return Theme.BLOOD;
			default:
				return Theme.GOLD;
		}
	}

	/** The colour a tint flashes in, for the overlay. */
	public static Color color(WatchRule.Tint tint)
	{
		return tint(tint == null ? WatchRule.Tint.GOLD : tint);
	}

	static String actions(WatchRule r)
	{
		List<String> out = new ArrayList<>();
		if (r.isNotify())
		{
			out.add("notify");
		}
		if (r.isFlash())
		{
			out.add(r.getTint().getLabel().toLowerCase() + " flash");
		}
		if (r.isChat())
		{
			out.add("chat");
		}
		String said = out.isEmpty() ? "Nothing to do" : String.join(", ", out);
		return Character.toUpperCase(said.charAt(0)) + said.substring(1) + "  ·  rests " + rest(r.getCooldown());
	}

	static String rest(int seconds)
	{
		if (seconds < 60)
		{
			return seconds + "s";
		}
		return seconds % 60 == 0 ? seconds / 60 + "m" : seconds / 60 + "m " + seconds % 60 + "s";
	}

	private JComponent card(WatchRule r)
	{
		boolean pausedNow = paused.contains(r.getId());
		Card card = new Card(new BorderLayout(8, 0));
		card.setBorder(new EmptyBorder(7, 8, 7, 8));
		card.accent(!r.isOn() ? Theme.STROKE : pausedNow ? Theme.EMBER : r.isFlash() ? tint(r.getTint()) : Theme.GOLD_DIM);

		JPanel lead = new JPanel(new BorderLayout());
		lead.setOpaque(false);
		lead.setBorder(new EmptyBorder(2, 0, 0, 0));
		lead.add(new Controls.Switch(r.isOn(), on ->
		{
			WatchRules next = rules.copy();
			WatchRule kept = next.find(r.getId());
			if (kept != null)
			{
				kept.setOn(on);
				save(next);
			}
		}), BorderLayout.NORTH);
		card.add(lead, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JLabel name = Ui.label(r.getName(), Theme.body(), r.isOn() ? Theme.TEXT : Theme.TEXT_MUTED);
		JLabel what = Ui.label(r.describe(), Theme.small(), r.isOn() ? Theme.GOLD : Theme.TEXT_FAINT);
		what.setToolTipText(r.describe());
		String how = !r.isComplete() ? "Not finished: open it to finish"
			: pausedNow ? "Paused: its pattern took too long" : actions(r);
		JLabel act = Ui.label(how, Theme.small(), pausedNow || !r.isComplete() ? Theme.EMBER : Theme.TEXT_FAINT);
		text.add(name);
		text.add(Box.createVerticalStrut(1));
		text.add(what);
		text.add(act);
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
		card.add(text, BorderLayout.CENTER);
		card.setToolTipText("Click to edit");
		card.onClick(() -> open(r.getId()));
		return card;
	}

	private JComponent presetRow(WatchRules.Preset p)
	{
		WatchRule rule = p.rule();
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(5, 0, 5, 0));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label(p.getLabel(), Theme.body(), Theme.TEXT));
		JLabel what = Ui.label(rule.describe(), Theme.small(), Theme.TEXT_MUTED);
		what.setToolTipText(rule.describe());
		text.add(what);
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
		row.add(text, BorderLayout.CENTER);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(new EmptyBorder(3, 0, 0, 0));
		CodexButton add = new CodexButton("Add", false, () ->
		{
			WatchRules next = rules.copy();
			if (next.add(rule) != null)
			{
				save(next);
			}
		});
		add.setToolTipText("Add \"" + p.getLabel() + "\" to your rules");
		holder.add(add, BorderLayout.NORTH);
		row.add(holder, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private void save(WatchRules next)
	{
		rules = next;
		onSave.accept(next.copy());
		rebuild.run();
	}

	private static JComponent labelled(String caps, JComponent body)
	{
		JPanel p = new JPanel(new BorderLayout(0, 3));
		p.setOpaque(false);
		JLabel l = Ui.label(caps.toUpperCase(), Theme.small(), Theme.GOLD_DIM);
		p.add(l, BorderLayout.NORTH);
		p.add(body, BorderLayout.CENTER);
		return p;
	}

	private static JPanel flow(int gap)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
		p.setOpaque(false);
		return p;
	}

	private JComponent editor()
	{
		WatchRule d = draft;
		Card card = new Card(null);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.highlighted(true);
		card.accent(d.isFlash() ? tint(d.getTint()) : Theme.GOLD);
		card.setBorder(new EmptyBorder(8, 10, 10, 10));

		List<JComponent> parts = new ArrayList<>();
		parts.add(labelled("Name", Controls.field(d.getName(), WatchRule.NAME_MAX, d::setName)));
		parts.add(labelled("When", new Controls.Picker<>(TRIGGERS, d.getTrigger(), WatchRule.Trigger::getLabel, t ->
		{
			boolean renamed = d.getName() == null || d.getName().isEmpty() || d.getName().equals("New rule")
				|| d.getTrigger() != null && d.getName().equals(d.getTrigger().getLabel());
			d.setTrigger(t);
			d.setValue(defaultValue(t));
			if (renamed)
			{
				d.setName(t.getLabel());
			}
			d.repair();
			rebuild.run();
		})));
		WatchRule.Trigger t = d.getTrigger();
		if (t == WatchRule.Trigger.STAT_BELOW)
		{
			parts.add(labelled("Skill", new Controls.Picker<>(SKILLS, d.getSkill(), s -> s, d::setSkill)));
		}
		if (t.takesText())
		{
			String caps = t == WatchRule.Trigger.CHAT ? "Words in the message" : t == WatchRule.Trigger.CHAT_REGEX
				? "Pattern (Java regex)" : "Item name";
			parts.add(labelled(caps, Controls.field(d.getText(), WatchRule.TEXT_MAX, s ->
			{
				d.setText(s);
				refreshProblem();
			})));
		}
		if (t.takesNumber())
		{
			String caps = t == WatchRule.Trigger.IDLE ? "For at least" : "Below";
			parts.add(labelled(caps, new Controls.Stepper(d.getValue(), t.getMin(), t.getMax(), step(t), t.getUnit(), d::setValue)));
		}
		if (t.isChat())
		{
			parts.add(Ui.wrapped("Game messages only, ignoring case. Your own chat and other players' are never read.",
				Theme.small(), Theme.TEXT_FAINT, TEXT_WIDTH));
		}

		JPanel then = flow(0);
		then.add(new Controls.Chip("Notify", d.isNotify(), on ->
		{
			d.setNotify(on);
			refreshProblem();
		}));
		then.add(Box.createHorizontalStrut(4));
		then.add(new Controls.Chip("Flash", d.isFlash(), on ->
		{
			d.setFlash(on);
			rebuild.run();
		}));
		then.add(Box.createHorizontalStrut(4));
		then.add(new Controls.Chip("Chat", d.isChat(), on ->
		{
			d.setChat(on);
			refreshProblem();
		}));
		parts.add(labelled("Then", then));
		if (d.isFlash())
		{
			JPanel swatches = flow(0);
			for (WatchRule.Tint tint : WatchRule.Tint.values())
			{
				swatches.add(new Controls.Swatch(tint(tint), tint == d.getTint(), tint.getLabel(), () ->
				{
					d.setTint(tint);
					rebuild.run();
				}));
			}
			JLabel named = Ui.label(d.getTint().getLabel(), Theme.small(), tint(d.getTint()));
			named.setBorder(new EmptyBorder(0, 4, 0, 0));
			swatches.add(named);
			parts.add(labelled("Flash colour", swatches));
		}
		parts.add(labelled("Then rest for", new Controls.Stepper(d.getCooldown(), WatchRule.COOLDOWN_MIN, WatchRule.COOLDOWN_MAX,
			5, "seconds", d::setCooldown)));

		problem = Ui.label(" ", Theme.small(), Theme.EMBER);
		// Stretched across the card, so a reason typed in later is never cut to the width of the blank before it.
		int lineHeight = problem.getPreferredSize().height;
		problem.setPreferredSize(new Dimension(10, lineHeight));
		problem.setMaximumSize(new Dimension(Integer.MAX_VALUE, lineHeight));
		parts.add(problem);
		refreshProblem();
		if (tried != null)
		{
			parts.add(Ui.label(tried, Theme.small(), Theme.TEAL));
		}

		JPanel buttons = flow(0);
		buttons.add(new CodexButton("Save", true, this::commit));
		buttons.add(Box.createHorizontalStrut(4));
		buttons.add(new CodexButton("Try it", false, () ->
		{
			if (d.isNotify() || d.isFlash() || d.isChat())
			{
				onTry.accept(d.copy().repair());
				tried = "Fired once, as it will in game";
				rebuild.run();
			}
		}));
		buttons.add(Box.createHorizontalStrut(4));
		buttons.add(new CodexButton("Cancel", false, () ->
		{
			openId = -1;
			draft = null;
			rebuild.run();
		}));
		parts.add(buttons);
		if (openId > 0)
		{
			boolean asking = deletingId == openId;
			JPanel del = flow(0);
			del.add(new CodexButton(asking ? "Click again to delete" : "Delete rule", asking, () ->
			{
				if (deletingId != openId)
				{
					deletingId = openId;
					rebuild.run();
					return;
				}
				WatchRules next = rules.copy();
				next.remove(openId);
				openId = -1;
				draft = null;
				deletingId = -1;
				save(next);
			}));
			parts.add(del);
		}

		for (int i = 0; i < parts.size(); i++)
		{
			JComponent part = parts.get(i);
			part.setAlignmentX(0f);
			if (i > 0)
			{
				card.add(Box.createVerticalStrut(part == problem ? 4 : 7));
			}
			card.add(part);
		}
		return card;
	}

	private static int defaultValue(WatchRule.Trigger t)
	{
		switch (t)
		{
			case STAT_BELOW:
				return 10;
			case RUN_BELOW:
				return 10;
			case FOOD_BELOW:
				return 3;
			case ITEM_BELOW:
				return 5;
			case IDLE:
				return 30;
			default:
				return 1;
		}
	}

	private static int step(WatchRule.Trigger t)
	{
		return t == WatchRule.Trigger.IDLE || t == WatchRule.Trigger.RUN_BELOW ? 5 : 1;
	}

	/** What stops the draft from being kept, or null when it is ready. */
	static String problemWith(WatchRule d)
	{
		WatchRule.Trigger t = d.getTrigger();
		if (t == null)
		{
			return "Choose what sets it off";
		}
		if (t.takesText() && (d.getText() == null || d.getText().trim().isEmpty()))
		{
			return t == WatchRule.Trigger.ITEM_BELOW ? "Name the item to count" : t == WatchRule.Trigger.CHAT
				? "Write the words to watch for" : "Write a pattern";
		}
		if (t == WatchRule.Trigger.CHAT_REGEX)
		{
			SafeRegex.Compiled c = SafeRegex.compile(d.getText());
			if (!c.isOk())
			{
				return "Pattern: " + c.getError();
			}
		}
		if (!d.isNotify() && !d.isFlash() && !d.isChat())
		{
			return "Choose notify, flash or chat";
		}
		return null;
	}

	private void refreshProblem()
	{
		if (problem == null || draft == null)
		{
			return;
		}
		String why = problemWith(draft);
		problem.setText(why == null ? " " : why);
		problem.setToolTipText(why);
	}

	private void commit()
	{
		WatchRule d = draft;
		if (d == null || problemWith(d) != null)
		{
			refreshProblem();
			return;
		}
		WatchRules next = rules.copy();
		boolean kept = openId == 0 ? next.add(d) != null : next.replace(d);
		openId = -1;
		draft = null;
		if (kept)
		{
			save(next);
		}
		else
		{
			rebuild.run();
		}
	}
}
