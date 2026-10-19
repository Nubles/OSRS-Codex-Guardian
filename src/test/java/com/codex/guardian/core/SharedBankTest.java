package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Bank Steward's shared bank, read from a copy of the fixture its own writer test produces byte for byte
 * ({@code bank-steward-bankV1.json}), and what the trip check makes of a whole bank.
 */
public class SharedBankTest
{
	private static final Gson GSON = new Gson();

	private static String fixture() throws Exception
	{
		try (InputStream in = SharedBankTest.class.getResourceAsStream("bank-steward-bankV1.json"))
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
	}

	/** The names the client gives the fixture's items. */
	private static String name(int id)
	{
		switch (id)
		{
			case ItemID.SHARK:
				return "Shark";
			case ItemID.COINS:
				return "Coins";
			case ItemID._4DOSEPRAYERRESTORE:
				return "Prayer potion(4)";
			case ItemID.PHARAOHS_SCEPTRE_CHARGED_INITIAL:
				return "Pharaoh's sceptre";
			case ItemID.PEST_VOID_KNIGHT_TOP:
				return "Void knight top";
			case ItemID.PEST_SEAL_8:
				return "Void seal(8)";
			default:
				return null;
		}
	}

	private static List<Supplies.Item> items(Object... pairs)
	{
		List<Supplies.Item> list = new ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			list.add(new Supplies.Item((String) pairs[i], (Integer) pairs[i + 1]));
		}
		return list;
	}

	private static List<String> texts(TripCheck check)
	{
		List<String> out = new ArrayList<>();
		for (TripCheck.Line line : check.getLines())
		{
			out.add(line.getTone() + " " + line.getText());
		}
		return out;
	}

	@Test
	public void readsBankStewardsRealSnapshot() throws Exception
	{
		assertEquals("codexbanksteward", SharedBank.GROUP);
		assertEquals("bankV1", SharedBank.KEY);
		Map<Integer, Long> bank = SharedBank.read(GSON, fixture());
		assertEquals(6, bank.size());
		assertEquals(240L, (long) bank.get(ItemID.SHARK));
		assertEquals(1_234_567L, (long) bank.get(ItemID.COINS));
		assertEquals(30L, (long) bank.get(ItemID._4DOSEPRAYERRESTORE));
		assertEquals(1L, (long) bank.get(ItemID.PHARAOHS_SCEPTRE_CHARGED_INITIAL));
		List<Supplies.Item> named = SharedBank.byName(bank, SharedBankTest::name);
		assertEquals(6, named.size());
		// No last resort in this bank.
		assertTrue(SharedBank.graces(bank, new int[][]{{ItemID.RING_OF_LIFE}}, new String[]{"Ring of life"}).isEmpty());
	}

	@Test
	public void aMissingOrDamagedSnapshotIsNoBankAtAll()
	{
		assertNull(SharedBank.read(GSON, null));
		assertNull(SharedBank.read(GSON, ""));
		assertNull(SharedBank.read(GSON, "not json {"));
		assertNull(SharedBank.read(GSON, "[1,2]"));
		assertNull(SharedBank.read(GSON, "{\"version\":1}"));
		assertNull(SharedBank.read(GSON, "{\"items\":[1]}"));
		assertNull(SharedBank.byName(null, SharedBankTest::name));
		// A cut-short snapshot cannot prove an item is not banked, so it is not taken as the whole bank.
		assertNull(SharedBank.read(GSON, "{\"version\":1,\"items\":{\"2570\":2},\"truncated\":true}"));
		// Odd entries are skipped, the rest is kept; a later version that adds fields still reads.
		Map<Integer, Long> odd = SharedBank.read(GSON, "{\"version\":2,\"items\":{\"x\":1,\"385\":\"many\",\"2570\":2,\"-4\":9,\"995\":0},\"more\":true}");
		assertEquals(Collections.singletonMap(ItemID.RING_OF_LIFE, 2L), odd);
		assertEquals(Collections.singletonMap("Ring of life", 2),
			SharedBank.graces(odd, new int[][]{{ItemID.RING_OF_LIFE}, {ItemID.JEWL_NECKLACE_OF_PHOENIX}}, new String[]{"Ring of life", "Phoenix necklace"}));
	}

	@Test
	public void theCheckSaysWhichForgottenTeleportWaitsInTheBank() throws Exception
	{
		List<Supplies.Item> bank = SharedBank.byName(SharedBank.read(GSON, fixture()), SharedBankTest::name);
		List<Supplies.Item> inv = items("Shark", 20, "Prayer potion(4)", 4);
		Readiness r = Supplies.assess(inv, items(), 99, false);
		TripCheck check = TripCheck.build(null, null, Collections.emptyMap(), inv, items(), r, Collections.emptyMap(), bank, 80);
		assertTrue(texts(check).toString(), texts(check).contains("BAD No teleport: Pharaoh's sceptre in the bank"));
		// Pointing at the bank is what makes the card worth showing without a kit.
		assertTrue(check.pointsAtBank());

		// A bank with no teleport at all says so: the fix is a shop, not a withdrawal.
		Map<Integer, Long> plain = new HashMap<>();
		plain.put(ItemID.SHARK, 100L);
		TripCheck none = TripCheck.build(null, null, Collections.emptyMap(), inv, items(), r, Collections.emptyMap(),
			SharedBank.byName(plain, SharedBankTest::name), 80);
		assertTrue(texts(none).contains("BAD No teleport out, none banked"));
		assertFalse(none.pointsAtBank());
		// Without a bank at all, the line is as before.
		TripCheck unknown = TripCheck.build(null, null, Collections.emptyMap(), inv, items(), r, Collections.emptyMap(), 80);
		assertTrue(texts(unknown).contains("BAD No teleport out"));
		assertFalse(unknown.pointsAtBank());
	}

	@Test
	public void protectionAndKitItemsLeftInTheBankAreNamed()
	{
		List<Supplies.Item> inv = items("Shark", 20, "Prayer potion(4)", 4);
		List<Supplies.Item> worn = items("Amulet of glory(4)", 1);
		Readiness r = Supplies.assess(inv, worn, 99, false);
		KitPlan kit = KitPlan.from(GSON.fromJson("{\"active\":\"kit:1\",\"kits\":[{\"id\":\"kit:1\",\"name\":\"Vorkath\",\"needs\":["
			+ "{\"supply\":\"item:8013\",\"perTrip\":1}]}]}", com.google.gson.JsonObject.class));
		Map<Integer, String> names = Collections.singletonMap(8013, "Teleport to house");
		List<Supplies.Item> bank = items("Extended super antifire(4)", 3, "Anti-venom+(4)", 9, "Teleport to house", 40,
			"Ring of life", 1);
		TripCheck check = TripCheck.build(Zones.byName("Vorkath"), kit, names, inv, worn, r, Collections.emptyMap(), bank, 80);
		List<String> lines = texts(check);
		assertTrue(lines.toString(), lines.contains("BAD No dragonfire protection: 3 in the bank"));
		assertTrue(lines.contains("BAD No anti-venom: 9 in the bank"));
		assertTrue(lines.contains("WARN Teleport to house: 40 in the bank"));
		assertTrue(lines.contains("GOOD Way out: Glory(4)"));
		// Carried protection is never looked up in the bank.
		List<Supplies.Item> packed = items("Shark", 20, "Anti-venom+(4)", 1);
		List<String> ready = texts(TripCheck.build(Zones.byName("Vorkath"), kit, names, packed, worn,
			Supplies.assess(packed, worn, 99, false), Collections.emptyMap(), bank, 80));
		assertTrue(ready.contains("GOOD Venom: Anti-venom+(4)"));
	}
}
