package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.guardian.ui.Format;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Every saved entry Guardian reads (its journal, hit memory and one-life choice, and Quartermaster's kits, Ledger's
 * worth and Bank Steward's bank) is read back from empty, cut-off, wrong-type, huge, negative, future-version and
 * garbage values without throwing, keeps whatever good part there is, and says when something was left out so the
 * saved text is kept aside rather than written over. Long histories and big banks stay quick.
 */
public class DamagedDataTest
{
	private static final Gson GSON = new Gson();
	private static final long NOW = 1_760_000_000_000L;

	/** Values a damaged or hand-edited profile could hold under any key. */
	static List<String> garbage()
	{
		StringBuilder deep = new StringBuilder();
		for (int i = 0; i < 200; i++)
		{
			deep.append("{\"a\":");
		}
		StringBuilder longText = new StringBuilder("\"");
		for (int i = 0; i < 100_000; i++)
		{
			longText.append('x');
		}
		longText.append('"');
		return Arrays.asList(null, "", " ", "null", "{", "[", "{\"deaths\":", "\"", "garbage", "{}", "[]", "0", "-1", "true",
			"NaN", "1e999999", "[null]", "{\"a\":null}", "{\"entries\":5}", "{\"entries\":[5,\"x\",null,{}]}",
			"{\"records\":[1,2]}", "{\"version\":99,\"runs\":[{\"started\":1}]}", "{\"playedTicks\":1e100000000}",
			"{\"total\":\"lots\",\"since\":1}", "{\"items\":{\"995\":\"x\"}}", "\u0000\u0001", "<html>", deep.toString(),
			longText.toString());
	}

	private static Journal journal()
	{
		Journal j = Journal.fresh(NOW - 86_400_000L);
		for (int i = 0; i < 2000; i++)
		{
			j.tick(i % 4 == 0);
		}
		j.damage("Vorkath", 32, "Vorkath", NOW - 5000);
		j.closeCall(9, 99, "Vorkath", "Vorkath", NOW - 4000);
		j.closeCall(12, 99, "Zulrah", "Zulrah", NOW - 3000);
		j.milestone("Combat 100", null, NOW - 2000);
		return j;
	}

	@Test
	public void theJournalSurvivesAnySavedValue()
	{
		for (String value : garbage())
		{
			SavedJson.Read<Journal> read = Journal.load(GSON, value);
			Journal j = read.getValue();
			if (j != null)
			{
				assertTrue(value, j.getCloseCalls() >= 0 && j.getDeaths() >= 0 && j.getPlayedTicks() >= 0);
				assertTrue(value, j.getEntries().size() <= Journal.MAX_ENTRIES);
				assertFalse(value, Format.runTime(j.getPlayedTicks()).startsWith("-"));
			}
			// Anything that was there but could not be read in full is flagged, so it is kept aside, not saved over.
			boolean saved = value != null && !value.trim().isEmpty();
			if (saved && j == null)
			{
				assertTrue(value, read.isDamaged());
			}
		}
	}

	@Test
	public void oneBadFieldNeverLosesTheRun()
	{
		// One field of the wrong type used to fail the whole journal, and a fresh one was saved over the run at once.
		JsonObject saved = GSON.toJsonTree(journal()).getAsJsonObject();
		saved.addProperty("deaths", "none");
		saved.getAsJsonArray("entries").get(1).getAsJsonObject().addProperty("at", "a while ago");
		JsonObject future = new JsonObject();
		future.addProperty("at", NOW);
		future.addProperty("type", "SOMETHING_NEWER");
		future.addProperty("title", "From a newer Guardian");
		saved.getAsJsonArray("entries").add(future);
		saved.getAsJsonArray("entries").add(5);
		SavedJson.Read<Journal> read = Journal.load(GSON, GSON.toJson(saved));
		Journal j = read.getValue();
		assertNotNull(j);
		assertTrue(read.isDamaged());
		assertEquals(2, j.getCloseCalls());
		assertEquals(2000, j.getPlayedTicks());
		assertEquals(0, j.getDeaths());
		assertEquals(32, j.getBiggestHit().getAmount());
		// The entry with the odd time keeps its words; the unknown kind and the stray number are left out.
		assertEquals(journal().getEntries().size(), j.getEntries().size());
		// A journal saved as it should be reads whole and is not flagged.
		assertFalse(Journal.load(GSON, GSON.toJson(journal())).isDamaged());
	}

	@Test
	public void anEntryFromANewerGuardianIsKeptAside()
	{
		// A kind of entry this version does not know reads as no kind at all and is dropped; the next save would
		// lose it for good, so the read says it left something out.
		JsonObject saved = GSON.toJsonTree(journal()).getAsJsonObject();
		JsonObject future = new JsonObject();
		future.addProperty("at", NOW);
		future.addProperty("type", "SOMETHING_NEWER");
		saved.getAsJsonArray("entries").add(future);
		SavedJson.Read<Journal> read = Journal.load(GSON, GSON.toJson(saved));
		assertTrue(read.isDamaged());
		assertEquals(journal().getEntries().size(), read.getValue().getEntries().size());
	}

	@Test
	public void absurdFiguresAreBroughtBackToSense()
	{
		SavedJson.Read<Journal> read = Journal.load(GSON, "{\"runStartedAt\":1,\"playedTicks\":9000000000000000000,"
			+ "\"dangerTicks\":-50,\"closeCalls\":-3,\"deaths\":-1,\"lowestHitpoints\":-40,\"disconnects\":2147483647,"
			+ "\"damageBySource\":{\"Vorkath\":-5,\"Zulrah\":60},\"closeCallsByPlace\":{\"Vorkath\":-2},\"entries\":[]}");
		Journal j = read.getValue();
		assertTrue(j.getPlayedTicks() >= 0 && j.getPlayedTicks() <= Journal.MAX_TICKS);
		assertEquals(0, j.getDangerTicks());
		assertEquals(0, j.getCloseCalls());
		assertEquals(0, j.getDeaths());
		assertEquals(-1, j.getLowestHitpoints());
		assertFalse(j.getDamageBySource().containsKey("Vorkath"));
		assertEquals(60, (int) j.getDamageBySource().get("Zulrah"));
		assertFalse(Format.runTime(j.getPlayedTicks()).startsWith("-"));
	}

	@Test
	public void aFullJournalLoadsInAFewMilliseconds()
	{
		// The most a journal holds: 250 entries, 30 of them with a replay.
		Journal j = Journal.fresh(NOW - 86_400_000L);
		for (int i = 0; i < 300; i++)
		{
			Replay replay = new Replay();
			replay.max = 99;
			replay.hp = new int[]{99, 80, 60, 30, 12, 20, 40, 60, 80, 99, 99, 99, 99, 99, 99, 99};
			replay.hit = new int[]{0, 19, 20, 30, 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
			replay.ate = new int[16];
			replay.drank = new int[16];
			replay.worstSource = "Vorkath";
			replay.worstHit = 30;
			j.closeCall(12, 99, "Vorkath", "Vorkath", replay, NOW - 1000 * i);
		}
		String json = GSON.toJson(j);
		for (int warm = 0; warm < 5; warm++)
		{
			Journal.load(GSON, json);
		}
		long start = System.nanoTime();
		Journal loaded = Journal.load(GSON, json).getValue();
		double ms = (System.nanoTime() - start) / 1e6;
		System.out.println("full journal (" + json.length() + " chars) read in " + ms + " ms");
		assertEquals(Journal.MAX_ENTRIES, loaded.getEntries().size());
		assertTrue(ms + " ms", ms < 10);
	}

	@Test
	public void aTenThousandEntryJournalLoadsQuicklyAndIsTrimmed()
	{
		JsonObject saved = GSON.toJsonTree(journal()).getAsJsonObject();
		for (int i = 0; i < 12_000; i++)
		{
			JsonObject e = new JsonObject();
			e.addProperty("at", NOW - i);
			e.addProperty("type", "ZONE");
			e.addProperty("title", "Entered the Wilderness");
			e.addProperty("detail", "Level " + (i % 50));
			saved.getAsJsonArray("entries").add(e);
		}
		String json = GSON.toJson(saved);
		Journal.load(GSON, json);
		long start = System.nanoTime();
		Journal j = Journal.load(GSON, json).getValue();
		long ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("12,000 entry journal read in " + ms + " ms");
		assertTrue(ms + " ms", ms < 150);
		assertEquals(Journal.MAX_ENTRIES, j.getEntries().size());
		// A damaged one that size is taken apart without freezing.
		saved.addProperty("deaths", "none");
		start = System.nanoTime();
		assertEquals(2, Journal.load(GSON, GSON.toJson(saved)).getValue().getCloseCalls());
		ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("damaged 12,000 entry journal read in " + ms + " ms");
		assertTrue(ms + " ms", ms < 1500);
	}

	@Test
	public void theHitMemorySurvivesAnySavedValue()
	{
		for (String value : garbage())
		{
			HitMemory memory = HitMemory.load(GSON, value).getValue();
			assertNotNull(value, memory);
			assertTrue(memory.size() <= HitMemory.MAX_RECORDS);
		}
		HitMemory good = new HitMemory();
		good.record("Vorkath", 732, 46, "Vorkath", NOW);
		good.record("Zulrah", 725, 41, "Zulrah", NOW);
		JsonObject saved = GSON.toJsonTree(good).getAsJsonObject();
		JsonObject odd = new JsonObject();
		odd.addProperty("name", "Mystery");
		odd.addProperty("max", "big");
		saved.getAsJsonObject("records").add("Mystery|1", odd);
		JsonObject absurd = new JsonObject();
		absurd.addProperty("name", "Glitch");
		absurd.addProperty("max", 2_000_000_000);
		saved.getAsJsonObject("records").add("Glitch|1", absurd);
		SavedJson.Read<HitMemory> read = HitMemory.load(GSON, GSON.toJson(saved));
		assertTrue(read.isDamaged());
		assertEquals(46, read.getValue().max("Vorkath", 732));
		assertEquals(41, read.getValue().max("Zulrah", 725));
		// No hit in the game comes near two billion: that one is damage, not a warning to give.
		assertEquals(0, read.getValue().max("Glitch", 1));
		assertEquals(2, read.getValue().size());
	}

	@Test
	public void aHugeHitMemoryIsTrimmed()
	{
		JsonObject records = new JsonObject();
		for (int i = 0; i < 5_000; i++)
		{
			JsonObject r = new JsonObject();
			r.addProperty("name", "Monster " + i);
			r.addProperty("level", i);
			r.addProperty("max", 1 + i % 90);
			r.addProperty("hits", 3);
			records.add("Monster " + i + "|" + i, r);
		}
		JsonObject saved = new JsonObject();
		saved.add("records", records);
		long start = System.nanoTime();
		HitMemory memory = HitMemory.load(GSON, GSON.toJson(saved)).getValue();
		long ms = (System.nanoTime() - start) / 1_000_000;
		assertTrue(ms + " ms", ms < 200);
		assertEquals(HitMemory.MAX_RECORDS, memory.size());
		// The hardest hitters are the ones kept.
		assertEquals(90, memory.hardest(1).get(0).getMax());
	}

	@Test
	public void theOneLifeChoiceReadsOnlyWhatGuardianWrote()
	{
		for (String value : garbage())
		{
			if ("true".equals(value))
			{
				continue;
			}
			OneLife.Decision d = OneLife.resolve(value, false, true);
			assertFalse(value, d.isOneLife());
		}
		// A hand-edited "TRUE" is still a one-life account, and is saved back as Guardian writes it.
		OneLife.Decision upper = OneLife.resolve(" TRUE ", false, true);
		assertTrue(upper.isOneLife());
		assertTrue(upper.shouldSave());
		assertFalse(OneLife.resolve("true", false, true).shouldSave());
	}

	@Test
	public void quartermastersKitsSurviveAnySavedValue()
	{
		for (String kits : garbage())
		{
			for (String quarters : garbage())
			{
				JsonElement k = SavedJson.tree(GSON, kits);
				JsonElement q = SavedJson.tree(GSON, quarters);
				KitPlan.from(k != null && k.isJsonObject() ? k.getAsJsonObject() : null,
					q != null && q.isJsonObject() ? q.getAsJsonObject() : null);
			}
		}
		JsonObject quarters = GSON.fromJson("{\"active\":\"mine\",\"bank\":{\"995\":1e100000000,\"2434\":-4,\"385\":20,"
			+ "\"x\":1,\"391\":99999999999999999999},\"kits\":[{\"id\":\"mine\",\"name\":\"Vorkath\",\"needs\":[{\"supply\":\"FOOD\","
			+ "\"perTrip\":-200},{\"supply\":\"PRAYER\",\"perTrip\":1e100000000},{\"supply\":\"item:12625\",\"perTrip\":2},"
			+ "{\"supply\":\"item:-7\",\"perTrip\":1},{\"supply\":\"item:9999999999\",\"perTrip\":1},5,null]}]}", JsonObject.class);
		long start = System.nanoTime();
		KitPlan plan = KitPlan.from(null, quarters);
		assertTrue((System.nanoTime() - start) / 1_000_000 < 50);
		assertEquals("Vorkath", plan.getName());
		assertEquals(20, plan.inBank(385));
		assertEquals(0, plan.inBank(995));
		assertEquals(0, plan.inBank(2434));
		assertEquals(0, plan.inBank(391));
		for (KitPlan.Need need : plan.getNeeds())
		{
			assertTrue(need.getSupply(), need.getPerTrip() >= 0 && need.getPerTrip() <= KitPlan.MAX_PER_TRIP);
			assertTrue(need.getSupply(), need.getItemId() >= -1);
		}
		assertEquals(Arrays.asList("FOOD", "PRAYER", "item:12625"), supplies(plan));
	}

	private static List<String> supplies(KitPlan plan)
	{
		List<String> out = new ArrayList<>();
		for (KitPlan.Need n : plan.getNeeds())
		{
			out.add(n.getSupply());
		}
		return out;
	}

	@Test
	public void ledgersWorthSurvivesAnySavedValue()
	{
		for (String value : garbage())
		{
			assertEquals(value, -1, LedgerWorth.read(GSON, value));
		}
		assertEquals(-1, LedgerWorth.read(GSON, "{\"total\":-5,\"since\":1}"));
		assertEquals(-1, LedgerWorth.read(GSON, "{\"total\":1e30,\"since\":1}"));
		assertEquals(-1, LedgerWorth.read(GSON, "{\"total\":1e100000000,\"since\":1}"));
		assertEquals(-1, LedgerWorth.read(GSON, "{\"total\":5000,\"since\":-1}"));
		assertEquals(12_400_000L, LedgerWorth.read(GSON, "{\"version\":7,\"total\":12400000,\"since\":1,\"future\":{\"x\":[1]}}"));
		// A ten-thousand point history is stepped over, not built.
		StringBuilder json = new StringBuilder("{\"total\":12400000,\"since\":1,\"history\":[");
		for (int i = 0; i < 12_000; i++)
		{
			json.append(i > 0 ? "," : "").append("{\"t\":").append(NOW - i * 60_000L).append(",\"v\":").append(12_000_000 + i).append('}');
		}
		String big = json.append("]}").toString();
		LedgerWorth.read(GSON, big);
		long start = System.nanoTime();
		assertEquals(12_400_000L, LedgerWorth.read(GSON, big));
		long ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("12,000 point Ledger history stepped over in " + ms + " ms");
		assertTrue(ms + " ms", ms < 50);
	}

	@Test
	public void bankStewardsBankSurvivesAnySavedValue()
	{
		for (String value : garbage())
		{
			Map<Integer, Long> bank = SharedBank.read(GSON, value);
			if (bank != null)
			{
				for (Long qty : bank.values())
				{
					assertTrue(value, qty > 0);
				}
			}
		}
		Map<Integer, Long> bank = SharedBank.read(GSON, "{\"version\":2,\"savedAt\":\"soon\",\"truncated\":\"no\",\"items\":{"
			+ "\"995\":1e100000000,\"2434\":-4,\"385\":20,\"x\":1,\"391\":99999999999999999999,\"-5\":3,\"12625\":2.5,"
			+ "\"2440\":[1],\"2436\":9223372036854775807}}");
		assertEquals(Long.valueOf(20), bank.get(385));
		assertFalse(bank.containsKey(995));
		assertFalse(bank.containsKey(2434));
		assertFalse(bank.containsKey(391));
		assertFalse(bank.containsKey(-5));
		assertFalse(bank.containsKey(2440));
		assertEquals(Long.valueOf(Long.MAX_VALUE), bank.get(2436));
		// Two last resorts at the most a bank can say still count without wrapping round to a negative.
		int[][] ids = {{2436, 2437}};
		bank.put(2437, Long.MAX_VALUE);
		assertEquals(Integer.MAX_VALUE, (int) SharedBank.graces(bank, ids, new String[]{"Super attack"}).get("Super attack"));
	}

	@Test
	public void aBankOfFifteenHundredItemsReadsInAFewMilliseconds()
	{
		StringBuilder json = new StringBuilder("{\"version\":1,\"savedAt\":" + NOW + ",\"truncated\":false,\"items\":{");
		for (int i = 0; i < 1_500; i++)
		{
			json.append(i > 0 ? "," : "").append('"').append(1000 + i * 7).append("\":").append(1 + i * 1_000L);
		}
		String saved = json.append("}}").toString();
		SharedBank.read(GSON, saved);
		long start = System.nanoTime();
		Map<Integer, Long> bank = SharedBank.read(GSON, saved);
		List<Supplies.Item> named = SharedBank.byName(bank, id -> "Item " + id);
		long ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("1,500 item bank read in " + ms + " ms");
		assertEquals(1_500, named.size());
		assertTrue(ms + " ms", ms < 20);
		Collections.emptyList();
	}

	@Test
	public void aReplayOfAbsurdLengthIsDropped()
	{
		StringBuilder hp = new StringBuilder("[");
		for (int i = 0; i < 100_000; i++)
		{
			hp.append(i > 0 ? "," : "").append(50);
		}
		hp.append(']');
		String arr = hp.toString();
		Journal j = Journal.load(GSON, "{\"entries\":[{\"at\":1,\"type\":\"CLOSE_CALL\",\"title\":\"Survived\",\"replay\":{\"max\":99,"
			+ "\"hp\":" + arr + ",\"hit\":" + arr + ",\"ate\":" + arr + ",\"drank\":" + arr + "}}]}").getValue();
		assertEquals(1, j.getEntries().size());
		assertNull(j.getEntries().get(0).getReplay());
	}
}
