package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SuppliesTest
{
	private static Supplies.Item item(String name, int quantity)
	{
		return new Supplies.Item(name, quantity);
	}

	private static Readiness pack(Supplies.Item... items)
	{
		return Supplies.assess(Arrays.asList(items), Collections.emptyList(), 99);
	}

	@Test
	public void countsFoodAndHealing()
	{
		Readiness r = pack(item("Shark", 1), item("Shark", 1), item("Monkfish", 1), item("Cooked karambwan", 1));
		assertEquals(4, r.getFoodCount());
		assertEquals(20 + 20 + 16 + 18, r.getFoodHealing());
	}

	@Test
	public void brewDosesHealByMaxHitpoints()
	{
		Readiness r = pack(item("Saradomin brew(4)", 1));
		assertEquals(4, r.getFoodCount());
		assertEquals((99 * 15 / 100 + 2) * 4, r.getFoodHealing());
	}

	@Test
	public void piesAndPizzasHaveTwoBites()
	{
		Readiness r = pack(item("Pineapple pizza", 1), item("1/2 pineapple pizza", 1), item("Half a meat pie", 1));
		assertEquals(22 + 11 + 6, r.getFoodHealing());
	}

	@Test
	public void gloryWorksToThirtyButDuelingToTwenty()
	{
		Readiness glory = pack(item("Amulet of glory(4)", 1));
		assertTrue(glory.canEscapeAt(30));
		assertFalse(glory.canEscapeAt(31));
		Readiness duel = pack(item("Ring of dueling(8)", 1));
		assertTrue(duel.canEscapeAt(20));
		assertFalse(duel.canEscapeAt(21));
		assertTrue(pack(item("Amulet of glory(t4)", 1)).hasEscape());
		assertTrue(pack(item("Ring of wealth (5)", 1)).canEscapeAt(30));
	}

	@Test
	public void emptyJewelleryIsNotAnEscape()
	{
		assertFalse(pack(item("Amulet of glory", 1)).hasEscape());
		assertFalse(pack(item("Ring of wealth", 1)).hasEscape());
		assertFalse(pack(item("Skills necklace", 1)).hasEscape());
		assertTrue(pack(item("Amulet of eternal glory", 1)).hasEscape());
	}

	@Test
	public void tabletsIntoTheWildernessAreNotEscapes()
	{
		assertFalse(pack(item("Annakarl teleport", 1)).hasEscape());
		assertFalse(pack(item("Burning amulet(5)", 1)).hasEscape());
		assertTrue(pack(item("Varrock teleport", 3)).hasEscape());
		assertTrue(pack(item("Teleport to house", 1)).hasEscape());
	}

	@Test
	public void wornJewelleryCountsAndSavingGracesAreFound()
	{
		List<Supplies.Item> worn = Arrays.asList(item("Ring of life", 1), item("Amulet of glory(6)", 1));
		Readiness r = Supplies.assess(Collections.singletonList(item("Escape crystal", 1)), worn, 70);
		assertEquals(Arrays.asList("Ring of life", "Escape crystal"), r.getSavingGraces());
		assertTrue(r.canEscapeAt(25));
	}

	@Test
	public void potionsAreCounted()
	{
		Readiness r = pack(item("Prayer potion(4)", 2), item("Super restore(3)", 1), item("Anti-venom+(4)", 1), item("Antidote++(2)", 1),
			item("Superantipoison(1)", 1));
		assertEquals(11, r.getPrayerDoses());
		assertEquals(6, r.getAntivenomDoses());
		assertEquals(1, r.getAntipoisonDoses());
	}

	@Test
	public void nothingUsefulIsEmpty()
	{
		Readiness r = pack(item("Coins", 1000), item("Rune scimitar", 1));
		assertEquals(0, r.getFoodCount());
		assertNull(r.getBestEscape());
	}

	@Test
	public void blightedSuppliesOnlyCountInTheWilderness()
	{
		List<Supplies.Item> inv = Arrays.asList(item("Blighted manta ray", 10), item("Shark", 1));
		Readiness town = Supplies.assess(inv, Collections.emptyList(), 99, false);
		Readiness wild = Supplies.assess(inv, Collections.emptyList(), 99, true);
		assertEquals(1, town.getFoodCount());
		assertEquals(11, wild.getFoodCount());
	}

	@Test
	public void escapeReachFollowsTheBestTeleport()
	{
		assertEquals(30, pack(item("Amulet of glory(4)", 1)).getEscapeReach());
		assertEquals(20, pack(item("Varrock teleport", 1)).getEscapeReach());
		assertEquals(-1, pack(item("Shark", 1)).getEscapeReach());
		assertFalse(pack(item("Chronicle", 1)).hasEscape());
	}
}
