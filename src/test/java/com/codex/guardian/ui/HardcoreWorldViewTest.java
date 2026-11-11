package com.codex.guardian.ui;

import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.Journal;
import com.codex.guardian.core.Peril;
import com.codex.guardian.core.Readiness;
import com.codex.guardian.core.Situation;
import com.codex.guardian.core.Supplies;
import com.codex.guardian.core.Zones;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** The Hardcore Worlds death list on the Ward and the Journal, with pictures (set CODEX_SHOTS to keep them). */
public class HardcoreWorldViewTest
{
	static final long NOW = 1_790_100_000_000L;

	static Situation hcw(int region)
	{
		Readiness r = Supplies.assess(Arrays.asList(new Supplies.Item("Shark", 12), new Supplies.Item("Prayer potion(4)", 2),
			new Supplies.Item("Varrock teleport", 3)), Collections.emptyList(), 70);
		return new Situation.Builder().loggedIn(true).playerName("Nubles").mode("Hardcore World", true)
			.hitpoints(70, 70).prayer(52, 52).runEnergy(88).combatLevel(84)
			.zone(region < 0 ? Zones.atSea() : Zones.forRegion(region, true)).readiness(r).build();
	}

	static GuardianPanel panel(Situation s, int tab)
	{
		GuardianPanel panel = new GuardianPanel(80, () -> {}, v -> {});
		panel.onActivate();
		Journal j = Journal.fresh(NOW - 30L * 3_600_000);
		for (int i = 0; i < 6000 * 31; i++)
		{
			j.tick(false);
		}
		panel.updateJournal(j);
		panel.update(s, Peril.calm());
		panel.selectTab(tab);
		return panel;
	}

	@Test
	public void theWardSaysWhatADeathHereMeans()
	{
		GuardianPanel safe = panel(hcw(9520), 0);
		String said = Pictures.text(safe.wardView());
		assertTrue(said, said.contains("Safe death") && said.contains("does not end your run"));
		Pictures.keep(Pictures.shot(safe, 560), "hcw-ward-castle-wars.png");

		GuardianPanel nmz = panel(hcw(9033), 0);
		said = Pictures.text(nmz.wardView());
		assertTrue(said, said.contains("Ends your run"));
		Pictures.keep(Pictures.shot(nmz, 560), "hcw-ward-nmz.png");

		GuardianPanel sea = panel(hcw(-1), 0);
		said = Pictures.text(sea.wardView());
		assertTrue(said, said.contains("Capsize only") && said.contains("Capsizing your boat is a safe death"));
		Pictures.keep(Pictures.shot(sea, 560), "hcw-ward-at-sea.png");
	}

	@Test
	public void theJournalListsTheRules()
	{
		GuardianPanel p = panel(hcw(9520), 1);
		String said = Pictures.text(p.journalPage());
		assertTrue(said, said.contains("DEATH RULES HERE") && said.contains("Clan Wars") && said.contains("Fortis Colosseum"));
		Pictures.keep(Pictures.shot(p, 900), "hcw-journal-rules.png");
	}

	@Test
	public void aFallenRunGetsTheResetOrTransferCard()
	{
		com.codex.guardian.core.KeptOnDeath kept = com.codex.guardian.core.KeptOnDeath.of(Arrays.asList(
			new com.codex.guardian.core.KeptOnDeath.Carried("Abyssal whip", 1, 1_400_000),
			new com.codex.guardian.core.KeptOnDeath.Carried("Amulet of glory", 1, 12_000),
			new com.codex.guardian.core.KeptOnDeath.Carried("Dragon boots", 1, 180_000)), false, false);
		com.codex.guardian.core.DeathChoice choice = com.codex.guardian.core.DeathChoice.of(NOW, 1212, 87, 6000L * 41, 4, kept,
			3_240_000, 412, "At Vorkath, to Vorkath", NOW - 1000);
		GuardianPanel p = panel(hcw(9023), 0);
		p.updateChoice(choice, null);
		assertTrue("not shown before the run is known to have fallen", !Pictures.text(p.wardView()).contains("RESET OR TRANSFER"));
		choice.confirm();
		p.updateChoice(choice, "Your best life: Life 2, 55 h, total 1,348, rune seal");
		String said = Pictures.text(p.wardView());
		assertTrue(said, said.contains("RESET OR TRANSFER") && said.contains("3 protected items, 1.6M")
			&& said.contains("3 days without Stronghold GP") && said.contains("See the inquest"));
		Pictures.keep(Pictures.shot(p, 760), "hcw-reset-or-transfer.png");
		Pictures.click(Pictures.button(p.wardView(), "Hide"));
		assertTrue(!Pictures.text(p.wardView()).contains("RESET OR TRANSFER"));
	}
}
