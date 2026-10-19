package com.codex.guardian.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.Readiness;
import com.codex.guardian.core.Situation;
import com.codex.guardian.core.Supplies;
import com.codex.guardian.core.TripCheck;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** When the Ward page shows the "Before you go" card. */
public class WardViewTest
{
	private static List<Supplies.Item> items(Object... pairs)
	{
		List<Supplies.Item> list = new ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			list.add(new Supplies.Item((String) pairs[i], (Integer) pairs[i + 1]));
		}
		return list;
	}

	private static boolean shown(List<Supplies.Item> inventory)
	{
		Readiness r = Supplies.assess(inventory, Collections.emptyList(), 99, false);
		Map<String, Integer> bank = new LinkedHashMap<>();
		bank.put("Ring of life", 1);
		// No Quartermaster kit; a ring of life waits in the bank.
		TripCheck trip = TripCheck.build(null, null, Collections.emptyMap(), inventory, Collections.emptyList(), r, bank, 80);
		Situation s = new Situation.Builder().loggedIn(true).hitpoints(99, 99).readiness(r).build();
		return WardView.showTrip(s, trip);
	}

	@Test
	public void aBankedRingOfLifeDoesNotNagASkiller()
	{
		assertFalse(shown(items("Yew logs", 27, "Rune axe", 1)));
		assertTrue(shown(items("Shark", 10)));
	}
}
