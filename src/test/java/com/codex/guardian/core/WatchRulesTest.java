package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Watch rules: reading and saving them, when each one fires and rests, and that no rule can hurt the client. */
public class WatchRulesTest
{
	private static final Gson GSON = new Gson();

	private static WatchRules of(WatchRule... rules)
	{
		WatchRules set = new WatchRules();
		for (WatchRule r : rules)
		{
			assertNotNull(set.add(r));
		}
		return set;
	}

	private static RuleWatch.Reading prayer(int tick, int points)
	{
		return new RuleWatch.Reading(tick).stat("Prayer", points);
	}

	// ---- Reading and saving ----

	@Test
	public void aSavedSetReadsBackAsItWas()
	{
		WatchRules set = of(WatchRules.Preset.LOW_PRAYER.rule(),
			WatchRule.of("Bones", WatchRule.Trigger.CHAT_REGEX, "You (bury|scatter) the \\w+", null, 1)
				.actions(false, true, WatchRule.Tint.BLOOD, true).cooldown(45),
			WatchRule.of("Sharks", WatchRule.Trigger.ITEM_BELOW, "Shark", null, 4));
		set.find(2).setOn(false);
		String json = GSON.toJson(set);
		assertTrue(json, json.contains("\"version\":1"));
		SavedJson.Read<WatchRules> read = WatchRules.load(GSON, json);
		assertFalse(read.isDamaged());
		WatchRules back = read.getValue();
		assertEquals(3, back.getRules().size());
		assertEquals(json, GSON.toJson(back));
		WatchRule bones = back.find(2);
		assertEquals(WatchRule.Trigger.CHAT_REGEX, bones.getTrigger());
		assertEquals(WatchRule.Tint.BLOOD, bones.getTint());
		assertEquals(45, bones.getCooldown());
		assertFalse(bones.isOn());
		assertTrue(bones.isChat() && bones.isFlash() && !bones.isNotify());
	}

	@Test
	public void aRuleWrittenByHandReads()
	{
		String json = "{\"version\":1,\"rules\":[{\"id\":7,\"name\":\"Low run\",\"on\":true,\"trigger\":\"RUN_BELOW\","
			+ "\"value\":20,\"notify\":true,\"cooldown\":60}]}";
		SavedJson.Read<WatchRules> read = WatchRules.load(GSON, json);
		WatchRule r = read.getValue().find(7);
		assertEquals("Run energy falls below 20%", r.describe());
		assertEquals(WatchRule.Tint.GOLD, r.getTint());
		assertEquals(60, r.getCooldown());
	}

	@Test
	public void nothingSavedIsAnEmptySetAndNotDamage()
	{
		for (String json : new String[]{null, "", "   "})
		{
			SavedJson.Read<WatchRules> read = WatchRules.load(GSON, json);
			assertFalse(read.isDamaged());
			assertTrue(read.getValue().getRules().isEmpty());
		}
	}

	@Test
	public void aNewerSaveIsReadButKeptAside()
	{
		String json = "{\"version\":2,\"rules\":[{\"id\":1,\"name\":\"Low\",\"trigger\":\"STAT_BELOW\",\"skill\":\"Prayer\","
			+ "\"value\":10,\"notify\":true},{\"id\":2,\"name\":\"Future\",\"trigger\":\"BOSS_SPAWNS\",\"notify\":true}],"
			+ "\"sound\":\"bell\"}";
		SavedJson.Read<WatchRules> read = WatchRules.load(GSON, json);
		assertTrue("a newer save must be kept aside, not written over", read.isDamaged());
		assertEquals(1, read.getValue().getRules().size());
		assertEquals("Low", read.getValue().getRules().get(0).getName());
		assertEquals(WatchRules.VERSION, read.getValue().version);
	}

	@Test
	public void damageCostsOnlyTheDamagedRule()
	{
		String json = "{\"version\":1,\"rules\":[{\"id\":1,\"name\":\"Good\",\"trigger\":\"PACK_FULL\",\"notify\":true},"
			+ "{\"id\":2,\"name\":\"Bad\",\"trigger\":{\"x\":1},\"value\":\"lots\"}]}";
		SavedJson.Read<WatchRules> read = WatchRules.load(GSON, json);
		assertTrue(read.isDamaged());
		assertEquals(1, read.getValue().getRules().size());
		assertEquals("Good", read.getValue().getRules().get(0).getName());

		read = WatchRules.load(GSON, "not json at all {{{");
		assertTrue(read.isDamaged());
		assertTrue(read.getValue().getRules().isEmpty());
	}

	@Test
	public void repairBringsEveryFieldBackToSense()
	{
		StringBuilder longName = new StringBuilder();
		StringBuilder longText = new StringBuilder();
		for (int i = 0; i < 500; i++)
		{
			longName.append('n');
			longText.append('t');
		}
		String json = "{\"rules\":[{\"id\":3,\"name\":\"" + longName + "\",\"trigger\":\"CHAT\",\"text\":\"" + longText
			+ "\",\"cooldown\":0,\"notify\":true},{\"id\":3,\"name\":\"Tab\\there\",\"trigger\":\"IDLE\",\"value\":-5,"
			+ "\"cooldown\":999999,\"chat\":true},{\"name\":\"\",\"trigger\":\"STAT_BELOW\",\"value\":1000,\"notify\":true}]}";
		WatchRules set = WatchRules.load(GSON, json).getValue();
		List<WatchRule> rules = set.getRules();
		assertEquals(3, rules.size());
		assertEquals(WatchRule.NAME_MAX, rules.get(0).getName().length());
		assertEquals(WatchRule.TEXT_MAX, rules.get(0).getText().length());
		assertEquals(WatchRule.COOLDOWN_MIN, rules.get(0).getCooldown());
		assertEquals("Tab here", rules.get(1).getName());
		assertEquals(3, rules.get(1).getValue());
		assertEquals(WatchRule.COOLDOWN_MAX, rules.get(1).getCooldown());
		assertEquals("Hitpoints", rules.get(2).getSkill());
		assertEquals(150, rules.get(2).getValue());
		assertEquals(WatchRule.Trigger.STAT_BELOW.getLabel(), rules.get(2).getName());
		// Repeated or missing ids are given fresh ones.
		assertEquals(3, rules.stream().mapToInt(WatchRule::getId).distinct().count());
		assertTrue(rules.stream().allMatch(r -> r.getId() > 0));
	}

	@Test
	public void noMoreThanTwelveRules()
	{
		WatchRules set = new WatchRules();
		for (int i = 0; i < WatchRules.MAX_RULES; i++)
		{
			assertNotNull(set.add(WatchRule.of("R" + i, WatchRule.Trigger.PACK_FULL, null, null, 1)));
		}
		assertTrue(set.isFull());
		assertNull(set.add(WatchRule.blank()));
		StringBuilder json = new StringBuilder("{\"rules\":[");
		for (int i = 0; i < 40; i++)
		{
			json.append(i == 0 ? "" : ",").append("{\"trigger\":\"PACK_FULL\",\"notify\":true}");
		}
		SavedJson.Read<WatchRules> read = WatchRules.load(GSON, json.append("]}").toString());
		assertEquals(WatchRules.MAX_RULES, read.getValue().getRules().size());
		assertTrue(read.isDamaged());
	}

	@Test
	public void editingReplacesAndDeletingRemoves()
	{
		WatchRules set = of(WatchRule.blank(), WatchRule.blank());
		WatchRule edit = set.find(2).copy();
		edit.setName("Edited");
		assertTrue(set.replace(edit));
		assertEquals("Edited", set.find(2).getName());
		assertTrue(set.remove(1));
		assertNull(set.find(1));
		edit.setTrigger(null);
		assertFalse(set.replace(edit));
	}

	// ---- When rules fire ----

	@Test
	public void aStatFiresWhenItFallsNotWhileItStaysLow()
	{
		WatchRules set = of(WatchRules.Preset.LOW_PRAYER.rule());
		RuleWatch w = new RuleWatch();
		// Logged in already low: nothing has fallen yet.
		assertTrue(w.tick(set, prayer(1, 4)).isEmpty());
		assertTrue(w.tick(set, prayer(2, 30)).isEmpty());
		List<RuleWatch.Alert> fired = w.tick(set, prayer(3, 9));
		assertEquals(1, fired.size());
		assertEquals("Prayer running low: Prayer 9, below 10", fired.get(0).line());
		// Still low: it does not nag.
		for (int t = 4; t < 400; t++)
		{
			assertTrue(w.tick(set, prayer(t, 5)).isEmpty());
		}
	}

	@Test
	public void aRuleRestsForItsCooldownThenFiresIfTheMomentStillHolds()
	{
		WatchRule rule = WatchRules.Preset.LOW_PRAYER.rule().cooldown(30);
		WatchRules set = of(rule);
		int rest = RuleWatch.cooldownTicks(set.find(1));
		assertEquals(50, rest);
		RuleWatch w = new RuleWatch();
		w.tick(set, prayer(0, 20));
		assertEquals(1, w.tick(set, prayer(1, 9)).size());
		// Restored, then low again inside the rest: held back...
		w.tick(set, prayer(2, 40));
		for (int t = 3; t < 1 + rest; t++)
		{
			assertTrue("tick " + t, w.tick(set, prayer(t, 8)).isEmpty());
		}
		// ...and fired as soon as the rest is over, because it is still low.
		assertEquals(1, w.tick(set, prayer(1 + rest, 8)).size());
		// Restored inside a rest and still fine when it ends: nothing.
		w.tick(set, prayer(2 + rest, 40));
		assertTrue(w.tick(set, prayer(2 + rest * 2, 40)).isEmpty());
	}

	@Test
	public void anEditedRuleStartsAfresh()
	{
		WatchRules set = of(WatchRules.Preset.LOW_PRAYER.rule());
		RuleWatch w = new RuleWatch();
		w.tick(set, prayer(0, 20));
		assertEquals(1, w.tick(set, prayer(1, 9)).size());
		WatchRule edit = set.find(1).copy();
		edit.setValue(15);
		set.replace(edit);
		// A new number is a new rule: no old rest, and it must see prayer above it before it fires.
		assertTrue(w.tick(set, prayer(2, 9)).isEmpty());
		w.tick(set, prayer(3, 20));
		assertEquals(1, w.tick(set, prayer(4, 14)).size());
	}

	@Test
	public void offOrUnfinishedRulesNeverFire()
	{
		WatchRule off = WatchRules.Preset.LOW_PRAYER.rule();
		off.setOn(false);
		WatchRule silent = WatchRule.of("Quiet", WatchRule.Trigger.STAT_BELOW, null, "Prayer", 10).actions(false, false, null, false);
		WatchRules set = of(off, silent);
		RuleWatch w = new RuleWatch();
		w.tick(set, prayer(0, 20));
		assertTrue(w.tick(set, prayer(1, 2)).isEmpty());
		assertTrue(w.message(of(WatchRule.of("Empty", WatchRule.Trigger.CHAT, "  ", null, 1)), "anything", 1).isEmpty());
	}

	@Test
	public void unknownFiguresLeaveRulesAsTheyWere()
	{
		WatchRules set = of(WatchRule.of("Run", WatchRule.Trigger.RUN_BELOW, null, null, 20),
			WatchRule.of("Food", WatchRule.Trigger.FOOD_BELOW, null, null, 3));
		RuleWatch w = new RuleWatch();
		assertTrue(w.tick(set, new RuleWatch.Reading(0).runEnergy(50).food(5)).isEmpty());
		assertTrue(w.tick(set, new RuleWatch.Reading(1)).isEmpty());
		List<RuleWatch.Alert> fired = w.tick(set, new RuleWatch.Reading(2).runEnergy(10).food(1));
		assertEquals(2, fired.size());
		assertEquals("Run energy 10%, below 20%", fired.get(0).getDetail());
		assertEquals("1 piece of food left, below 3", fired.get(1).getDetail());
	}

	@Test
	public void anItemCountsInEveryDose()
	{
		WatchRules set = of(WatchRule.of("Restores", WatchRule.Trigger.ITEM_BELOW, "prayer potion", null, 3));
		RuleWatch w = new RuleWatch();
		assertTrue(w.tick(set, new RuleWatch.Reading(0).item("Prayer potion(4)", 2).item("Prayer potion(1)", 1)
			.item("Super restore(4)", 6)).isEmpty());
		List<RuleWatch.Alert> fired = w.tick(set, new RuleWatch.Reading(1).item("Prayer potion(2)", 2).item("Prayer potion", 0));
		assertEquals(1, fired.size());
		assertEquals("prayer potion: 2 left, below 3", fired.get(0).getDetail());
		// Every one gone, and the pack known to be empty, is still a count: zero.
		set = of(WatchRule.of("Sharks", WatchRule.Trigger.ITEM_BELOW, "Shark", null, 1));
		w = new RuleWatch();
		w.tick(set, new RuleWatch.Reading(0).item("Shark", 1));
		assertEquals(1, w.tick(set, new RuleWatch.Reading(1).packKnown()).size());
		assertEquals(0, RuleWatch.count(java.util.Collections.singletonMap("shark(2)", 4L), "Shark(1)"));
	}

	@Test
	public void aFullPackAndStandingIdle()
	{
		WatchRules set = of(WatchRules.Preset.PACK_FULL.rule(), WatchRule.of("Idle", WatchRule.Trigger.IDLE, null, null, 30));
		RuleWatch w = new RuleWatch();
		assertTrue(w.tick(set, new RuleWatch.Reading(0).filledSlots(27).idleTicks(0)).isEmpty());
		List<RuleWatch.Alert> fired = w.tick(set, new RuleWatch.Reading(1).filledSlots(WatchRule.PACK_SLOTS).idleTicks(49));
		assertEquals(1, fired.size());
		assertEquals("Backpack full", fired.get(0).getDetail());
		fired = w.tick(set, new RuleWatch.Reading(2).filledSlots(28).idleTicks(50));
		assertEquals(1, fired.size());
		assertEquals("Idle for 30 seconds", fired.get(0).getDetail());
	}

	@Test
	public void aMessageMatchesIgnoringCaseAndRests()
	{
		WatchRules set = of(WatchRules.Preset.TELE_BLOCK.rule());
		RuleWatch w = new RuleWatch();
		// As the wiki quotes it, with a caster's name.
		String message = "A Tele Block has been cast on you by Zezima. It will expire in 2 minutes, 30 seconds.";
		List<RuleWatch.Alert> fired = w.message(set, message, 100);
		assertEquals(1, fired.size());
		assertEquals(message, fired.get(0).getDetail());
		assertTrue(w.message(set, message, 101).isEmpty());
		assertEquals(1, w.message(set, message.toUpperCase(), 100 + RuleWatch.cooldownTicks(set.find(1))).size());
		assertTrue(w.message(set, "Your Tele Block has been removed because you killed Zezima.", 999).isEmpty());
		// Message rules never fire from the tick, nor tick rules from a message.
		assertTrue(w.tick(set, new RuleWatch.Reading(5000)).isEmpty());
		assertTrue(w.message(of(WatchRules.Preset.PACK_FULL.rule()), "Backpack full", 1).isEmpty());
	}

	@Test
	public void everyPresetIsCompleteAndMatchesItsQuotedMessage()
	{
		for (WatchRules.Preset p : WatchRules.Preset.all())
		{
			assertTrue(p.name(), p.rule().isComplete());
		}
		// The game's messages, quoted from the wiki pages named in WatchRules.Preset.
		assertFires(WatchRules.Preset.FROZEN, "You have been frozen!");
		assertFires(WatchRules.Preset.RING_OF_LIFE, "Your Ring of Life saves you and is destroyed in the process.");
		assertFires(WatchRules.Preset.TELE_BLOCK, "A Tele Block has been cast on you by Someone. It will expire in 5 minutes.");
		WatchRules set = of(WatchRules.Preset.LOW_FOOD.rule());
		assertTrue(set.has(WatchRules.Preset.LOW_FOOD));
		assertFalse(set.has(WatchRules.Preset.LOW_PRAYER));
	}

	private static void assertFires(WatchRules.Preset p, String message)
	{
		assertEquals(p.name(), 1, new RuleWatch().message(of(p.rule()), message, 0).size());
	}

	// ---- Patterns that could hurt ----

	@Test
	public void aBadPatternIsCaughtAndNeverFires()
	{
		for (String bad : new String[]{"(unclosed", "[a-", "*start", "a{2,1}", "\\", "(?<name", "x{99999999999}"})
		{
			SafeRegex.Compiled c = SafeRegex.compile(bad);
			assertFalse(bad, c.isOk());
			assertNotNull(bad, c.getError());
			WatchRule rule = WatchRule.of("Bad", WatchRule.Trigger.CHAT_REGEX, bad, null, 1);
			assertFalse(rule.isComplete());
			assertTrue(new RuleWatch().message(of(rule), "(unclosed [a- *start", 0).isEmpty());
		}
		StringBuilder tooLong = new StringBuilder();
		for (int i = 0; i <= SafeRegex.MAX_PATTERN; i++)
		{
			tooLong.append('a');
		}
		assertFalse(SafeRegex.compile(tooLong.toString()).isOk());
		assertFalse(SafeRegex.compile("a{2000}").isOk());
		assertFalse(SafeRegex.compile("a{1,99999}").isOk());
		assertTrue(SafeRegex.compile("a{3}").isOk());
		assertTrue(SafeRegex.compile("a{2,5}b").isOk());
		assertTrue(SafeRegex.compile("\\{99999\\}").isOk());
		assertFalse(SafeRegex.compile("").isOk());
		assertFalse(SafeRegex.compile(null).isOk());
	}

	@Test(timeout = 5000)
	public void aRunawayPatternGivesUpAndIsPausedOnce()
	{
		StringBuilder as = new StringBuilder();
		for (int i = 0; i < 300; i++)
		{
			as.append('a');
		}
		String message = as.append('!').toString();
		for (String evil : new String[]{"(a+)+b", "(a|a)*b", "(a*)*$x", "^(a|aa)+$"})
		{
			long start = System.nanoTime();
			// Either the budget stops it or Java's own matcher finds the answer quickly; never a hang.
			assertTrue(evil, SafeRegex.find(SafeRegex.compile(evil), message) != SafeRegex.Outcome.FOUND);
			assertTrue(evil + " took too long", System.nanoTime() - start < 500_000_000L);
		}
		assertEquals(SafeRegex.Outcome.TOO_SLOW, SafeRegex.find(SafeRegex.compile("(a+)+b"), message));
		WatchRules set = of(WatchRule.of("Evil", WatchRule.Trigger.CHAT_REGEX, "(a+)+b", null, 1));
		RuleWatch w = new RuleWatch();
		assertTrue(w.message(set, message, 0).isEmpty());
		assertTrue(w.isPaused(1));
		assertEquals(1, w.takePaused().size());
		assertTrue(w.takePaused().isEmpty());
		// Paused, it is not tried again on the next message.
		assertTrue(w.message(set, "aaab", 100).isEmpty());
		assertTrue(w.takePaused().isEmpty());
		// A good pattern on the same message is fine.
		assertEquals(SafeRegex.Outcome.FOUND, SafeRegex.find(SafeRegex.compile("a+!$"), "aaaa!"));
	}

	@Test
	public void aGoodPatternMatches()
	{
		WatchRules set = of(WatchRule.of("Clue", WatchRule.Trigger.CHAT_REGEX, "you have a sneaking suspicion|clue", null, 1));
		RuleWatch w = new RuleWatch();
		assertEquals(1, w.message(set, "A CLUE appears", 0).size());
		assertTrue(w.message(set, "Nothing", 100).isEmpty());
	}

	// ---- Fuzz ----

	@Test(timeout = 30000)
	public void fuzzedSavesNeverThrowAndAlwaysRepair()
	{
		Random random = new Random(7);
		String good = GSON.toJson(of(WatchRules.Preset.LOW_PRAYER.rule(), WatchRules.Preset.TELE_BLOCK.rule(),
			WatchRule.of("Sharks", WatchRule.Trigger.ITEM_BELOW, "Shark", null, 4)));
		String alphabet = "{}[]\":,0123456789-eE.truefalsnul\\ abcXYZ_é\u0000";
		for (int i = 0; i < 3000; i++)
		{
			StringBuilder s = new StringBuilder(good);
			int edits = 1 + random.nextInt(6);
			for (int e = 0; e < edits && s.length() > 0; e++)
			{
				int at = random.nextInt(s.length());
				switch (random.nextInt(3))
				{
					case 0:
						s.deleteCharAt(at);
						break;
					case 1:
						s.insert(at, alphabet.charAt(random.nextInt(alphabet.length())));
						break;
					default:
						s.setCharAt(at, alphabet.charAt(random.nextInt(alphabet.length())));
				}
			}
			SavedJson.Read<WatchRules> read = WatchRules.load(GSON, s.toString());
			WatchRules set = read.getValue();
			assertNotNull(set);
			assertTrue(set.getRules().size() <= WatchRules.MAX_RULES);
			for (WatchRule r : set.getRules())
			{
				assertNotNull(r.getTrigger());
				assertTrue(r.getName().length() <= WatchRule.NAME_MAX && !r.getName().isEmpty());
				assertTrue(r.getText().length() <= WatchRule.TEXT_MAX);
				assertTrue(r.getValue() >= r.getTrigger().getMin() && r.getValue() <= r.getTrigger().getMax());
				assertTrue(r.getCooldown() >= WatchRule.COOLDOWN_MIN && r.getCooldown() <= WatchRule.COOLDOWN_MAX);
				assertNotNull(r.getTint());
				r.describe();
			}
			// Whatever was read saves and reads back clean.
			SavedJson.Read<WatchRules> again = WatchRules.load(GSON, GSON.toJson(set));
			assertFalse(again.isDamaged());
			assertEquals(GSON.toJson(set), GSON.toJson(again.getValue()));
		}
	}

	@Test(timeout = 60000)
	public void fuzzedPatternsAndMessagesNeverThrowOrHang()
	{
		Random random = new Random(11);
		String alphabet = "ab.*+?|()[]{}^$\\-,0123456789dws!? ";
		List<String> messages = Arrays.asList("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!", "You have been frozen!",
			"", "((((((((((", "a b c d e f g h i j k l m n o p q r s t u v w x y z 0 1 2 3 4 5 6 7 8 9");
		long worst = 0;
		for (int i = 0; i < 5000; i++)
		{
			StringBuilder p = new StringBuilder();
			int len = 1 + random.nextInt(24);
			for (int c = 0; c < len; c++)
			{
				p.append(alphabet.charAt(random.nextInt(alphabet.length())));
			}
			SafeRegex.Compiled compiled = SafeRegex.compile(p.toString());
			for (String m : messages)
			{
				long start = System.nanoTime();
				SafeRegex.find(compiled, m);
				worst = Math.max(worst, System.nanoTime() - start);
			}
		}
		assertTrue("worst match took " + worst / 1_000_000 + " ms", worst < 250_000_000L);
	}

	@Test(timeout = 30000)
	public void fuzzedReadingsNeverThrow()
	{
		Random random = new Random(3);
		WatchRules set = new WatchRules();
		for (WatchRule.Trigger t : WatchRule.Trigger.values())
		{
			set.add(WatchRule.of(t.name(), t, t == WatchRule.Trigger.CHAT_REGEX ? "a+" : "Shark", "Prayer", 1 + random.nextInt(50)));
		}
		RuleWatch w = new RuleWatch();
		for (int tick = 0; tick < 20000; tick++)
		{
			RuleWatch.Reading r = new RuleWatch.Reading(tick);
			if (random.nextBoolean())
			{
				r.stat("Prayer", random.nextInt(120) - 10);
			}
			if (random.nextBoolean())
			{
				r.runEnergy(random.nextInt(110) - 5).food(random.nextInt(30) - 1).filledSlots(random.nextInt(30) - 1)
					.idleTicks(random.nextInt(200) - 1);
			}
			if (random.nextBoolean())
			{
				r.item(random.nextBoolean() ? "Shark" : null, random.nextInt(10) - 3);
			}
			for (RuleWatch.Alert a : w.tick(set, r))
			{
				assertNotNull(a.line());
			}
			w.message(set, random.nextBoolean() ? null : "aaa" + tick, tick);
		}
	}
}
