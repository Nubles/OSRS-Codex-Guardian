package com.codex.guardian.ui;

import com.codex.guardian.core.Peril;
import com.codex.guardian.core.Readiness;
import com.codex.guardian.core.Risk;
import com.codex.guardian.core.Situation;
import com.codex.guardian.core.Threat;
import com.codex.guardian.core.Zone;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

		Zone zone = s.getZone();
		stack.add(new SectionHeader("Where you are", zone == null ? null : zone.getRisk().getLabel() + " risk"));
		stack.add(place(s));

		stack.add(new SectionHeader("Readiness"));
		addReadiness(stack, s, foodReserve);

		List<Threat> attackers = s.getAttackers();
		if (!attackers.isEmpty())
		{
			stack.add(new SectionHeader("Attacking you", String.valueOf(attackers.size())));
			for (Threat t : attackers)
			{
				boolean outclassed = t.isPlayer() || t.getCombatLevel() >= s.getCombatLevel() + 20;
				stack.add(Rows.line(new Mark(outclassed ? Mark.Kind.BAD : Mark.Kind.WARN, 14), t.getName(),
					t.isPlayer() ? "Another player" : "Level " + t.getCombatLevel(),
					t.isPlayer() ? new Pill("Player", Theme.BLOOD) : null, outclassed ? Theme.BLOOD : null));
			}
		}

		List<Peril.Reason> reasons = peril.getReasons();
		if (!reasons.isEmpty())
		{
			stack.add(new SectionHeader("Guardian's read"));
			Set<String> seen = new HashSet<>();
			int shown = 0;
			for (Peril.Reason r : reasons)
			{
				if (shown >= 6 || !seen.add(r.getText()))
				{
					continue;
				}
				stack.add(Rows.reason(Mark.of(r.getTone()), r.getText()));
				shown++;
			}
		}
		return stack;
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
		if (peril.getSecondsLeft() >= 0 && peril.getSecondsLeft() < 60)
		{
			text.add(centred(Ui.label("About " + peril.getSecondsLeft() + "s of hitpoints at this pace", Theme.small(), Theme.BLOOD)));
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

	private static Card place(Situation s)
	{
		Zone zone = s.getZone();
		Card card = new Card(new BorderLayout(0, 4));
		Risk risk = s.risk();
		if (zone != null)
		{
			card.accent(Theme.risk(risk));
		}
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Ui.label(zone == null ? "Open ground" : zone.getName(), Theme.title(), Theme.GOLD_BRIGHT), BorderLayout.CENTER);
		card.add(top, BorderLayout.NORTH);

		JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		pills.setOpaque(false);
		if (zone != null)
		{
			pills.add(new Pill(zone.kindLabel(), Theme.GOLD));
			pills.add(new Pill(risk.getLabel() + " risk", Theme.risk(risk)));
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
		javax.swing.JLabel adviceLabel = Ui.wrapped(advice, Theme.small(), Theme.TEXT_MUTED, TEXT);
		adviceLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
		adviceLabel.setAlignmentX(0f);
		body.add(adviceLabel);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	private static void addReadiness(StackPanel stack, Situation s, int foodReserve)
	{
		Readiness r = s.getReadiness();
		boolean exposed = s.inHarmsWay() || s.getWildernessLevel() > 0;
		boolean safe = s.risk() == Risk.SAFE;

		Mark.Kind food = r.getFoodCount() == 0 ? (exposed && !safe ? Mark.Kind.BAD : Mark.Kind.NONE)
			: r.getFoodHealing() >= foodReserve ? Mark.Kind.GOOD : Mark.Kind.WARN;
		stack.add(Rows.check(food, "Food",
			r.getFoodCount() == 0 ? "Nothing to eat in your pack" : r.getFoodCount() + (r.getFoodCount() == 1 ? " piece" : " pieces"),
			r.getFoodCount() == 0 ? null : r.getFoodHealing() + " hp"));

		int wild = s.getWildernessLevel();
		Mark.Kind escape;
		String escapeDetail;
		if (r.canEscapeAt(wild))
		{
			escape = Mark.Kind.GOOD;
			escapeDetail = r.getBestEscape() + (r.getEscapeCount() > 1 ? " and " + (r.getEscapeCount() - 1) + " more" : "");
		}
		else if (r.hasEscape())
		{
			escape = Mark.Kind.BAD;
			escapeDetail = "Teleports blocked at level " + wild;
		}
		else
		{
			escape = exposed && !safe ? Mark.Kind.BAD : Mark.Kind.NONE;
			escapeDetail = r.getLawRunes() > 0 ? "No item, but law runes" : "No teleport carried";
		}
		stack.add(Rows.check(escape, "Way out", escapeDetail, null));

		List<String> graces = r.getSavingGraces();
		stack.add(Rows.check(graces.isEmpty() ? Mark.Kind.NONE : wild > 30 ? Mark.Kind.WARN : Mark.Kind.GOOD, "Saving grace",
			graces.isEmpty() ? "None worn" : wild > 30 ? "No help above level 30" : String.join(", ", graces), null));

		boolean prayerLow = s.getMaxPrayer() > 1 && s.getPrayer() * 4 < s.getMaxPrayer();
		stack.add(Rows.check(r.getPrayerDoses() > 0 ? Mark.Kind.GOOD : prayerLow && exposed ? Mark.Kind.WARN : Mark.Kind.NONE, "Prayer",
			s.getPrayer() + "/" + s.getMaxPrayer() + " points" + (r.getPrayerDoses() > 0 ? ", " + r.getPrayerDoses() + " doses to restore" : ""), null));

		Situation.Toxin toxin = s.getToxin();
		int cures = r.getAntipoisonDoses() + r.getAntivenomDoses();
		if (toxin == Situation.Toxin.VENOM)
		{
			stack.add(Rows.check(r.getAntivenomDoses() > 0 ? Mark.Kind.WARN : Mark.Kind.BAD, "Venomed",
				r.getAntivenomDoses() > 0 ? r.getAntivenomDoses() + " anti-venom doses" : "No anti-venom on you", null));
		}
		else if (toxin == Situation.Toxin.POISON)
		{
			stack.add(Rows.check(cures > 0 ? Mark.Kind.NOTE : Mark.Kind.WARN, "Poisoned",
				cures > 0 ? cures + " doses of cure on you" : "No antipoison on you", null));
		}
		else if (cures > 0)
		{
			stack.add(Rows.check(Mark.Kind.GOOD, "Cures", cures + " doses ready", null));
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
