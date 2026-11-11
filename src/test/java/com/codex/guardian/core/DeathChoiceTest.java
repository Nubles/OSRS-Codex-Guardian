package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** What a death keeps, and the Reset or Transfer card's record. */
public class DeathChoiceTest
{
	private static final Gson GSON = new Gson();

	private static KeptOnDeath.Carried c(String name, int qty, long price)
	{
		return new KeptOnDeath.Carried(name, qty, price);
	}

	@Test
	public void aDeathKeepsTheThreeMostValuableSingleItems()
	{
		// Wiki, Items_Kept_on_Death: "keep the three most valuable items"; each item of a stack counts on its own.
		KeptOnDeath k = KeptOnDeath.of(Arrays.asList(c("Shark", 20, 900), c("Abyssal whip", 1, 1_400_000),
			c("Amulet of fury", 1, 2_100_000), c("Dragon defender", 1, 0), c("Rune platebody", 1, 38_000)), false, false);
		assertEquals(Arrays.asList("Amulet of fury", "Abyssal whip", "Rune platebody"), k.getKept());
		assertEquals(3_538_000, k.getKeptValue());
		assertEquals(18_000, k.getLostValue());
		// Protect Item "always protects an additional item".
		assertEquals(4, KeptOnDeath.of(Arrays.asList(c("Shark", 20, 900)), false, true).getKept().size());
		// "If a player is skulled the number of protected items is zero", one with Protect Item.
		assertEquals(0, KeptOnDeath.of(Arrays.asList(c("Shark", 20, 900)), true, false).getKept().size());
		assertEquals(Collections.singletonList("Shark"), KeptOnDeath.of(Arrays.asList(c("Shark", 20, 900)), true, true).getKept());
		assertEquals(17_100, KeptOnDeath.of(Arrays.asList(c("Shark", 20, 900)), true, true).getLostValue());
		// Many of one item: the three kept are three of that item.
		assertEquals(Arrays.asList("Shark", "Shark", "Shark"), KeptOnDeath.of(Arrays.asList(c("Shark", 5, 900)), false, false).getKept());
		// Nothing carried, nulls and absurd figures do not throw.
		assertEquals(0, KeptOnDeath.of(null, false, false).getLostValue());
		assertEquals(Long.MAX_VALUE, KeptOnDeath.of(Arrays.asList(c("Coins", Integer.MAX_VALUE, Long.MAX_VALUE / 2), null), true, false)
			.getLostValue());
	}

	@Test
	public void theChoiceIsSavedReadAndKnowsAReset()
	{
		KeptOnDeath kept = KeptOnDeath.of(Arrays.asList(c("Abyssal whip", 1, 1_400_000)), false, false);
		DeathChoice choice = DeathChoice.of(1_000, 1212, 87, 6000L * 41, 4, kept, 3_200_000, 412, "At Vorkath, to Vorkath", 999);
		assertFalse(choice.isConfirmed());
		choice.confirm();
		String json = GSON.toJson(choice);
		assertTrue(json, json.contains("\"version\":1"));
		DeathChoice back = DeathChoice.load(GSON, json);
		assertTrue(back.isConfirmed());
		assertEquals(1212, back.getTotalLevel());
		assertEquals(Collections.singletonList("Abyssal whip"), back.getKept());
		assertEquals(3_200_000, back.getBankValue());
		assertEquals(999, back.getInquestAt());
		// Real levels never fall, so a lower total on the same account means the character was reset.
		assertTrue(back.looksReset(33));
		assertFalse(back.looksReset(1212));
		assertFalse(back.looksReset(1300));
		assertFalse("not read yet", back.looksReset(0));
	}

	@Test
	public void aDamagedOrNewerChoiceReadsAsNone()
	{
		for (String bad : new String[]{null, "", "null", "[]", "{", "{\"version\":2,\"at\":5}", "{\"at\":-1,\"version\":1}",
			"{\"version\":1,\"at\":5,\"kept\":7}", "{\"version\":1,\"at\":\"soon\"}"})
		{
			DeathChoice c = DeathChoice.load(GSON, bad);
			if (c != null)
			{
				assertTrue(bad, c.getKept().isEmpty());
			}
		}
		assertNull(DeathChoice.load(GSON, "{\"version\":2,\"at\":5}"));
		DeathChoice odd = DeathChoice.load(GSON, "{\"version\":1,\"at\":5,\"totalLevel\":-4,\"bankValue\":-9,\"kept\":[null,\"\",\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}");
		assertEquals(0, odd.getTotalLevel());
		assertEquals(-1, odd.getBankValue());
		assertEquals(DeathChoice.MAX_KEPT, odd.getKept().size());
	}
}
