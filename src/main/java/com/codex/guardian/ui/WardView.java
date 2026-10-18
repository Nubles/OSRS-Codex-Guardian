package com.codex.guardian.ui;

import com.codex.guardian.core.Advice;
import com.codex.guardian.core.Peril;
import com.codex.guardian.core.Readiness;
import com.codex.guardian.core.Risk;
import com.codex.guardian.core.Situation;
import com.codex.guardian.core.Threat;
import com.codex.guardian.core.Zone;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The live view: Guardian's dial, where you stand, what you carry and who is attacking you. */
final class WardView
{
	private static final int TEXT = 190;

	private WardView()
	{
	}

	static StackPanel build(Situation s, Peril peril, PerilGauge gauge, int foodReserve)
	{
		StackPanel stack = new StackPanel(6, 8);
		if (!s.isLoggedIn())
		{
			stack.add(welcome());
			return stack;
		}

		stack.add(dial(s, peril, gauge));

		List<Threat> attackers = s.getAttackers();
		if (!attackers.isEmpty())
		{
			// In a fight, who is on you matters more than the map.
			stack.add(new SectionHeader("Attacking you", String.valueOf(attackers.size())));
			stack.add(attackers(s));
		}

		stack.add(new SectionHeader("Where you are"));
		stack.add(place(s));

		stack.add(new SectionHeader("Readiness"));
		stack.add(readiness(s, foodReserve));

		// Every reason except the ones the dial already says out loud.
		String headline = peril.headline();
		boolean paceShown = paceShown(peril);
		List<JComponent> rows = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Peril.Reason r : peril.getReasons())
		{
			if (rows.size() >= 6 || r.getText().equals(headline) || !seen.add(r.getText()) || saidElsewhere(r)
				|| paceShown && r.getText().startsWith("At this pace"))
			{
				continue;
			}
			rows.add(Rows.sentence(Mark.of(r.getTone()), r.getText(), TEXT - 30));
		}
		if (!rows.isEmpty())
		{
			stack.add(new SectionHeader("Also weighing in"));
			stack.add(Rows.ledger(rows));
		}
		return stack;
	}

	/** The place card and the readiness ledger already carry these, each with its own verdict mark. */
	private static boolean saidElsewhere(Peril.Reason r)
	{
		return r.getTopic() == Peril.Topic.PLACE || r.getTopic() == Peril.Topic.KIT || r.getTone() == Peril.Tone.GOOD;
	}

	private static boolean paceShown(Peril peril)
	{
		return peril.getSecondsLeft() >= 0 && peril.getSecondsLeft() < 60;
	}

	private static Card dial(Situation s, Peril peril, PerilGauge gauge)
	{
		Card card = new Card(new BorderLayout(0, 2)).highlighted(peril.getLevel().atLeast(Peril.Level.DANGER));
		card.setBorder(new EmptyBorder(6, 8, 10, 8));
		if (peril.getLevel().atLeast(Peril.Level.DANGER))
		{
			card.accent(Theme.peril(peril.getLevel()));
		}
		gauge.set(peril, s.isLoggedIn());
		card.add(gauge, BorderLayout.NORTH);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(centred(Ui.wrapped(peril.headline(), Theme.body(), Theme.TEXT, TEXT - 16)));
		if (paceShown(peril))
		{
			text.add(centred(Ui.label("About " + peril.getSecondsLeft() + "s of hitpoints at this pace", Theme.small(), Theme.BLOOD)));
		}
		String action = Advice.now(s, peril);
		if (action != null)
		{
			JLabel label = Ui.wrapped(action, Theme.title(), Theme.GOLD_BRIGHT, TEXT - 16);
			JPanel band = new ActionBand(Theme.peril(peril.getLevel()));
			band.add(label);
			JPanel holder = centred(band);
			holder.setBorder(new EmptyBorder(6, 0, 0, 0));
			text.add(holder);
		}
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private static JPanel centred(javax.swing.JComponent c)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.setOpaque(false);
		row.add(c);
		row.setAlignmentX(0.5f);
		return row;
	}

	/** Attackers grouped by name and level, strongest first, so two black demons read as one line. */
	private static Card attackers(Situation s)
	{
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Threat> first = new LinkedHashMap<>();
		List<Threat> sorted = new ArrayList<>(s.getAttackers());
		sorted.sort((a, b) -> Integer.compare(b.getCombatLevel(), a.getCombatLevel()));
		for (Threat t : sorted)
		{
			String key = (t.isPlayer() ? "p:" : "n:") + t.getName() + ":" + t.getCombatLevel();
			counts.merge(key, 1, Integer::sum);
			first.putIfAbsent(key, t);
		}
		List<JComponent> rows = new ArrayList<>();
		for (Map.Entry<String, Threat> e : first.entrySet())
		{
			Threat t = e.getValue();
			int n = counts.get(e.getKey());
			boolean outclassed = t.isPlayer() || t.getCombatLevel() >= s.getCombatLevel() + 20;
			String name = (t.getName() == null ? "Unknown" : t.getName()) + (n > 1 ? "  x" + n : "");
			String value = t.isPlayer() ? "Player, lvl " + t.getCombatLevel() : "Level " + t.getCombatLevel();
			rows.add(Rows.fact(outclassed ? Mark.Kind.BAD : Mark.Kind.WARN, name, value, outclassed ? Theme.BLOOD : null));
		}
		return Rows.ledger(rows);
	}

	private static Card place(Situation s)
	{
		Zone zone = s.getZone();
		Card card = new Card(new BorderLayout(0, 4));
		Risk risk = s.risk();
		if (zone != null)
		{
			card.accent(Theme.risk(risk));
		}
		String name = zone == null ? "Open ground"
			: zone.getKind() == Zone.Kind.WILDERNESS ? "Wilderness, level " + s.getWildernessLevel() : zone.getName();
		card.add(Ui.label(name, Theme.title(), Theme.GOLD_BRIGHT), BorderLayout.NORTH);

		JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		pills.setOpaque(false);
		if (zone != null)
		{
			if (zone.getKind() != Zone.Kind.WILDERNESS)
			{
				pills.add(new Pill(zone.kindLabel(), Theme.GOLD));
			}
			pills.add(new Pill(risk.getLabel() + " risk", Theme.risk(risk)));
		}
		else if (s.isNearWilderness())
		{
			pills.add(new Pill("Wilderness ahead", Theme.EMBER));
		}
		else
		{
			pills.add(new Pill("No known danger", Theme.TEAL));
		}
		if (s.isMultiCombat())
		{
			pills.add(new Pill("Multi", Theme.EMBER));
		}
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		pills.setAlignmentX(0f);
		body.add(pills);
		String advice = zone != null ? zone.getAdvice()
			: s.isNearWilderness() ? "The Wilderness starts a few steps north. Other players can attack you past the ditch."
			: "Nothing here Guardian knows to be dangerous. Stay sharp all the same.";
		JLabel adviceLabel = Ui.wrapped(advice, Theme.small(), Theme.TEXT_MUTED, TEXT);
		adviceLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
		adviceLabel.setAlignmentX(0f);
		body.add(adviceLabel);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	private static Card readiness(Situation s, int foodReserve)
	{
		Readiness r = s.getReadiness();
		boolean exposed = (s.inHarmsWay() || s.getWildernessLevel() > 0) && s.risk() != Risk.SAFE;
		List<JComponent> rows = new ArrayList<>();

		Mark.Kind food = r.getFoodCount() == 0 ? (exposed ? Mark.Kind.BAD : Mark.Kind.NONE)
			: r.getFoodHealing() >= foodReserve ? Mark.Kind.GOOD : Mark.Kind.WARN;
		rows.add(Rows.fact(food, "Food", r.getFoodCount() == 0 ? "None" : r.getFoodCount() + "  ·  " + r.getFoodHealing() + " hp",
			food == Mark.Kind.NONE ? null : Mark.color(food)));

		int wild = s.getWildernessLevel();
		if (s.isTeleblocked() && r.hasEscape())
		{
			rows.add(Rows.fact(Mark.Kind.BAD, "Way out", "Teleblocked", Theme.BLOOD));
		}
		else if (r.canEscapeAt(wild))
		{
			String more = r.getEscapeCount() > 1 ? "  +" + (r.getEscapeCount() - 1) : "";
			rows.add(Rows.fact(Mark.Kind.GOOD, "Way out", Advice.shortName(r.getBestEscape()) + more, null));
		}
		else if (r.hasEscape())
		{
			rows.add(Rows.fact(Mark.Kind.BAD, "Way out", "Only to level " + r.getEscapeReach(), Theme.BLOOD));
		}
		else
		{
			rows.add(Rows.fact(exposed ? Mark.Kind.BAD : Mark.Kind.NONE, "Way out",
				r.getLawRunes() > 0 ? "Law runes only" : "None carried", exposed ? Theme.BLOOD : null));
		}

		List<String> graces = r.getSavingGraces();
		rows.add(Rows.fact(graces.isEmpty() ? Mark.Kind.NONE : wild > 30 ? Mark.Kind.WARN : Mark.Kind.GOOD, "Last resort",
			graces.isEmpty() ? "None worn" : wild > 30 ? "Fails above 30" : String.join(", ", graces), null));

		boolean prayerLow = s.getMaxPrayer() > 1 && s.getPrayer() * 4 < s.getMaxPrayer();
		rows.add(Rows.fact(r.getPrayerDoses() > 0 ? Mark.Kind.GOOD : prayerLow && exposed ? Mark.Kind.WARN : Mark.Kind.NONE, "Prayer",
			s.getPrayer() + "/" + s.getMaxPrayer() + (r.getPrayerDoses() > 0 ? "  ·  " + r.getPrayerDoses() + " doses" : ""), null));

		Situation.Toxin toxin = s.getToxin();
		int cures = r.getAntipoisonDoses() + r.getAntivenomDoses();
		if (toxin == Situation.Toxin.VENOM)
		{
			boolean cure = r.getAntivenomDoses() > 0;
			rows.add(Rows.fact(cure ? Mark.Kind.WARN : Mark.Kind.BAD, "Venomed",
				cure ? r.getAntivenomDoses() + " anti-venom" : "No anti-venom", cure ? null : Theme.BLOOD));
		}
		else if (toxin == Situation.Toxin.POISON)
		{
			rows.add(Rows.fact(cures > 0 ? Mark.Kind.NOTE : Mark.Kind.WARN, "Poisoned",
				cures > 0 ? cures + " doses of cure" : "No antipoison", null));
		}
		else if (cures > 0)
		{
			rows.add(Rows.fact(Mark.Kind.GOOD, "Cures", cures + " doses", null));
		}
		return Rows.ledger(rows);
	}

	/** A softly tinted band behind the one thing to do now. */
	private static final class ActionBand extends JPanel
	{
		private final Color tint;

		ActionBand(Color tint)
		{
			super(new BorderLayout());
			this.tint = tint;
			setOpaque(false);
			setBorder(new EmptyBorder(4, 8, 4, 8));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			g.setColor(Theme.alpha(tint, 52));
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
			g.setColor(Theme.alpha(tint, 140));
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
			g.dispose();
		}
	}

	private static Card welcome()
	{
		Card card = new Card(new BorderLayout(0, 6)).highlighted(true);
		card.setBorder(new EmptyBorder(12, 12, 12, 12));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label("ONE LIFE. KEEP IT.", Theme.small(), Theme.GOLD));
		text.add(Ui.wrapped("Log in and Guardian watches over your run: your hitpoints, the danger around you, and what you carry to get out alive.",
			Theme.body(), Theme.TEXT, TEXT - 8));
		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setBorder(new EmptyBorder(4, 0, 0, 0));
		text.add(gap);
		text.add(Ui.wrapped("It only reads what your client already shows. Nothing leaves your computer.", Theme.small(), Theme.TEXT_MUTED, TEXT - 8));
		card.add(text, BorderLayout.CENTER);
		return card;
	}
}
