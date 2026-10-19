package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The per-account one-life choice, the pre-trip check with Quartermaster's kit, and walking distances. */
public class TripAndAccountTest
{
	private static final Gson GSON = new Gson();

	private static List<Supplies.Item> items(Object... pairs)
	{
		List<Supplies.Item> list = new ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			list.add(new Supplies.Item((String) pairs[i], (Integer) pairs[i + 1]));
		}
		return list;
	}

	private static KitPlan plan(String json)
	{
		return KitPlan.from(GSON.fromJson(json, JsonObject.class));
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
	public void oneLifeIsKeptPerAccountAndTheOldSettingCarriesOverOnce()
	{
		// The account's own choice always wins, either way.
		assertTrue(OneLife.resolve("true", false, false).isOneLife());
		assertFalse(OneLife.resolve("false", true, true).isOneLife());
		assertFalse(OneLife.resolve("true", false, false).shouldSave());
		// An account played under the old global setting inherits it, and it is saved as its own.
		OneLife.Decision migrated = OneLife.resolve(null, true, true);
		assertTrue(migrated.isOneLife());
		assertTrue(migrated.shouldSave());
		// A brand new account is not swept up by the old setting.
		assertFalse(OneLife.resolve(null, true, false).isOneLife());
		assertFalse(OneLife.resolve(null, false, true).isOneLife());
		assertFalse(OneLife.resolve("garbage", false, true).isOneLife());
	}

	@Test
	public void quartermasterKitIsReadOnlyWhenTheChoiceIsTheirs()
	{
		KitPlan saved = plan("{\"active\":\"kit:2\",\"bank\":{\"2570\":3,\"oops\":1},\"kits\":["
			+ "{\"id\":\"kit:1\",\"name\":\"Other\",\"needs\":[]},"
			+ "{\"id\":\"kit:2\",\"name\":\"Zulrah trip\",\"needs\":[{\"supply\":\"ANTIVENOM\",\"perTrip\":4},"
			+ "{\"supply\":\"item:12938\",\"perTrip\":1},{\"supply\":\"item:x\",\"perTrip\":1},{\"perTrip\":3}]}]}");
		assertEquals("Zulrah trip", saved.getName());
		assertEquals(2, saved.getNeeds().size());
		assertEquals(12938, saved.getNeeds().get(1).getItemId());
		assertEquals(3, saved.inBank(2570));
		assertEquals("Zulrah", TripCheck.destinationFor(saved.getName()).getName());

		KitPlan preset = plan("{\"active\":\"preset:tob\"}");
		assertEquals("Theatre of Blood", preset.getName());
		assertTrue(preset.getNeeds().isEmpty());

		// Nothing chosen: Quartermaster would show its first kit, which says nothing about this trip.
		assertNull(plan("{\"kits\":[{\"id\":\"kit:1\",\"name\":\"Vorkath\"}]}").getName());
		// A shape it does not expect is read as far as it goes, never thrown.
		assertNull(plan("{\"active\":5,\"kits\":{},\"bank\":[1,2]}").getName());
		assertNull(KitPlan.from(null));
	}

	@Test
	public void placesAreFoundByTheNamesPlayersGiveKits()
	{
		assertEquals("Vorkath", Zones.byName("vorkath").getName());
		assertEquals("Barrows crypts", Zones.byName("Barrows").getName());
		assertEquals("Vorkath", Zones.byName("Vorkath trip").getName());
		assertEquals("Nex", Zones.byName("Nex").getName());
		assertEquals("Abyssal Sire", Zones.byName("Abyssal Sire").getName());
		assertNull(Zones.byName("Slayer task"));
		assertNull(Zones.byName("ab"));
		assertNull(Zones.byName(null));
	}

	@Test
	public void vorkathCheckNamesWhatIsMissingOnceAndPointsAtTheBank()
	{
		List<Supplies.Item> inv = items("Shark", 14, "Prayer potion(4)", 2, "Extended super antifire(4)", 1, "Teleport to house", 1);
		List<Supplies.Item> worn = items("Amulet of glory(4)", 1, "Dragonfire ward", 1);
		Readiness r = Supplies.assess(inv, worn, 85, false);
		KitPlan kit = plan("{\"active\":\"kit:1\",\"kits\":[{\"id\":\"kit:1\",\"name\":\"Vorkath\",\"needs\":["
			+ "{\"supply\":\"FOOD\",\"perTrip\":300},{\"supply\":\"PRAYER\",\"perTrip\":12},{\"supply\":\"ANTIVENOM\",\"perTrip\":4},"
			+ "{\"supply\":\"SUPER_ANTIFIRE\",\"perTrip\":4},{\"supply\":\"item:8013\",\"perTrip\":1},{\"supply\":\"RANGING\",\"perTrip\":4}]}]}");
		Map<Integer, String> names = new HashMap<>();
		names.put(8013, "Teleport to house");
		Map<String, Integer> bank = new LinkedHashMap<>();
		bank.put("Ring of life", 2);
		TripCheck check = TripCheck.build(TripCheck.destinationFor(kit.getName()), kit, names, inv, worn, r, bank, 80);
		List<String> lines = texts(check);
		assertTrue(lines.toString(), lines.contains("WARN Food: 280 of 300 hp"));
		assertTrue(lines.contains("GOOD Way out: Glory(4)"));
		assertTrue(lines.contains("GOOD Dragonfire: Extended super antifire(4)"));
		assertTrue(lines.contains("BAD No anti-venom"));
		assertTrue(lines.contains("WARN Prayer: 8 of 12 doses"));
		assertTrue(lines.contains("GOOD Teleport to house"));
		assertTrue(lines.contains("WARN Ring of life: 2 in the bank"));
		// The venom line already says it; the kit's own anti-venom line would only repeat it. Boosts are not checked.
		for (String line : lines)
		{
			assertFalse(line, line.contains("Anti-venom") || line.contains("Antifire") || line.contains("Ranging"));
		}
		assertFalse(check.isClear());
		assertEquals("BAD No anti-venom", check.worst(1).get(0).getTone() + " " + check.worst(1).get(0).getText());
	}

	@Test
	public void kitDosesStillWarnWhenProtectionIsCarriedButShort()
	{
		List<Supplies.Item> inv = items("Shark", 20, "Anti-venom+(4)", 1);
		Readiness r = Supplies.assess(inv, items("Amulet of glory(4)", 1), 99, false);
		KitPlan kit = plan("{\"active\":\"kit:1\",\"kits\":[{\"id\":\"kit:1\",\"name\":\"Zulrah\",\"needs\":["
			+ "{\"supply\":\"ANTIVENOM\",\"perTrip\":8}]}]}");
		List<String> lines = texts(TripCheck.build(Zones.byName("Zulrah"), kit, Collections.emptyMap(), inv,
			items("Amulet of glory(4)", 1), r, Collections.emptyMap(), 80));
		assertTrue(lines.toString(), lines.contains("GOOD Venom: Anti-venom+(4)"));
		assertTrue(lines.contains("WARN Anti-venom: 4 of 8 doses"));
		assertTrue(lines.contains("NOTE No ring of life worn"));
	}

	@Test
	public void withoutAKitTheCheckFallsBackToGuardiansOwn()
	{
		List<Supplies.Item> worn = items("Ring of life", 1);
		Readiness r = Supplies.assess(items(), worn, 60, false);
		TripCheck check = TripCheck.build(null, null, Collections.emptyMap(), items(), worn, r, Collections.emptyMap(), 80);
		assertNull(check.getDestination());
		assertNull(check.getKit());
		assertEquals(3, check.getLines().size());
		assertTrue(texts(check).contains("BAD No food packed"));
		assertTrue(texts(check).contains("BAD No teleport out"));
		assertTrue(texts(check).contains("GOOD Last resort: Ring of life"));
	}

	@Test
	public void onlyAFightingPackIsBriefedAtTheBank()
	{
		assertFalse(TripCheck.packedForTrip(Supplies.assess(items("Yew logs", 27, "Rune axe", 1), items(), 99, false)));
		assertTrue(TripCheck.packedForTrip(Supplies.assess(items("Shark", 5), items(), 99, false)));
		assertTrue(TripCheck.packedForTrip(Supplies.assess(items("Prayer potion(4)", 4), items(), 99, false)));
	}

	@Test
	public void distancesSouthMatchTheLevelLines()
	{
		// Level 26 starts 200 tiles north of the ditch; level 20's last row is 159 tiles north of it.
		int y = 3520 + 25 * 8 + 3;
		assertEquals(26, Zones.wildernessLevel(3100, y));
		assertEquals(y - (3520 + 159), Zones.tilesSouthTo(3100, y, 20));
		assertEquals(y - 3519, Zones.tilesSouthTo(3100, y, 0));
		assertEquals(0, Zones.tilesSouthTo(3100, y, 30));
		// One step south of the line puts you on level 20.
		assertEquals(20, Zones.wildernessLevel(3100, y - Zones.tilesSouthTo(3100, y, 20)));
		assertEquals(21, Zones.wildernessLevel(3100, y - Zones.tilesSouthTo(3100, y, 20) + 1));
		// Underground mirrors the surface; off the map there is no promise.
		assertEquals(5, Zones.tilesSouthTo(3200, 9920 + 20 * 8 + 4, 20));
		assertEquals(-1, Zones.tilesSouthTo(3200, 3400, 0));
	}

	@Test
	public void adviceCountsTheTilesWhenItKnowsThem()
	{
		Readiness r = Supplies.assess(items("Shark", 3, "Varrock teleport", 1), items(), 99, true);
		Situation.Builder b = new Situation.Builder().loggedIn(true).combatLevel(100).hitpoints(15, 99).prayer(50, 70)
			.damage(1, 8).attacker(new Threat("Revenant knight", 126, false)).zone(Zones.wilderness(25))
			.wilderness(25, false).readiness(r);
		Situation peril = b.distances(36, 196).build();
		assertEquals("Eat, walk 36 tiles south", Advice.now(peril, new PerilAssessor(80).assess(peril)));
		Situation calm = new Situation.Builder().loggedIn(true).hitpoints(99, 99).zone(Zones.wilderness(25)).wilderness(25, false)
			.readiness(r).distances(36, 196).build();
		assertEquals("Walk 36 tiles south", Advice.now(calm, new PerilAssessor(80).assess(calm)));
		// Teleblocked and out of food: the ditch is the only way.
		Readiness tab = Supplies.assess(items("Varrock teleport", 1), items(), 99, true);
		Situation blocked = new Situation.Builder().loggedIn(true).combatLevel(100).hitpoints(10, 99).damage(2, 12)
			.attacker(new Threat("Pker", 110, true)).zone(Zones.wilderness(25)).wilderness(25, false).teleblocked(true)
			.readiness(tab).distances(36, 196).build();
		Peril p = new PerilAssessor(80).assess(blocked);
		assertEquals(Peril.Level.PERIL, p.getLevel());
		assertEquals("Run 196 tiles south", Advice.now(blocked, p));
	}
}
