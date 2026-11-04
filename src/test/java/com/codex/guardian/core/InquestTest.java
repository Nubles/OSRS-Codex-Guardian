package com.codex.guardian.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Inquests, played from scripted fights tick by tick through the same pieces the plugin drives: the situation it
 * reads, the close-call watch, and the inquest recorder. Each script ends by checking the verdict word for word.
 */
public class InquestTest
{
	private static final Gson GSON = new Gson();

	/** A scripted account: what it carries, where it stands, who is on it; one {@link #tick} per game tick. */
	private static final class Fight
	{
		final Inquest.Recorder recorder = new Inquest.Recorder();
		final CloseCallWatch watch = new CloseCallWatch();
		final int max;
		final int percent;
		int hp;
		int prayer = 52;
		int prayerLevel = 52;
		int prot;
		int sharks = 8;
		int prayerPotions;
		String teleport = "Amulet of glory(4)";
		int wild;
		int foes;
		boolean frozen;
		boolean teleblocked;
		boolean ringOfLife;
		String from = "Ice troll runt";
		Inquest last;

		Fight(int max, int percent)
		{
			this.max = max;
			this.percent = percent;
			hp = max;
		}

		Readiness readiness()
		{
			List<Supplies.Item> pack = new ArrayList<>();
			if (sharks > 0)
			{
				pack.add(new Supplies.Item("Shark", sharks));
			}
			if (prayerPotions > 0)
			{
				pack.add(new Supplies.Item("Prayer potion(4)", prayerPotions));
			}
			if (teleport != null)
			{
				pack.add(new Supplies.Item(teleport, 1));
			}
			List<Supplies.Item> worn = ringOfLife ? Collections.singletonList(new Supplies.Item("Ring of life", 1))
				: Collections.emptyList();
			return Supplies.assess(pack, worn, max, wild > 0);
		}

		Situation situation()
		{
			Situation.Builder b = new Situation.Builder().loggedIn(true).hitpoints(hp, max).prayer(prayer, prayerLevel)
				.wilderness(wild, false).teleblocked(teleblocked).readiness(readiness());
			for (int i = 0; i < foes; i++)
			{
				b.attacker(new Threat(from, 74, from.equals("A player")));
			}
			return b.build();
		}

		/** Hits land, the tick is read, and the close-call watch runs, in the plugin's order. */
		void tick(int... hits)
		{
			for (int h : hits)
			{
				recorder.hit(h, from, null, false);
			}
			Situation s = situation();
			recorder.tick(Inquest.Frame.of(s, prot, frozen, Supplies.potionDoses(packOf())));
			CloseCallWatch.Result done = watch.update(hp, max, percent, "Fremennik Isles", "Ice troll runt");
			if (watch.isActive())
			{
				recorder.mark();
			}
			if (done != null)
			{
				last = recorder.closeCall("Fremennik Isles", Inquest.Kit.of(s.getReadiness()), 1L);
			}
		}

		List<Supplies.Item> packOf()
		{
			return prayerPotions > 0 ? Collections.singletonList(new Supplies.Item("Prayer potion(4)", prayerPotions))
				: Collections.emptyList();
		}

		void calm(int ticks)
		{
			for (int i = 0; i < ticks; i++)
			{
				tick();
			}
		}

		/** Takes hits this tick. */
		void hit(int... amounts)
		{
			for (int a : amounts)
			{
				hp -= a;
			}
			tick(amounts);
		}

		void eat()
		{
			sharks--;
			// A shark heals 20 (Supplies holds the audited figure).
			hp = Math.min(max, hp + 20);
			tick();
		}
	}

	@Test
	public void aCloseCallToIceTrollRuntsReadsAsItHappened()
	{
		Fight f = new Fight(60, 25);
		f.calm(10);
		f.foes = 1;
		f.hit(8);
		f.eat();
		f.foes = 3;
		f.calm(6);
		// 47 in three ticks: 14, then 12 and 7 together, then 14; no single runt hit over its wiki max hit of 15.
		f.hit(14);
		f.hit(12, 7);
		f.hit(14);
		assertEquals(13, f.hp);
		assertNull("Still low: no inquest yet", f.last);
		f.foes = 0;
		f.eat();
		Inquest q = f.last;
		assertNotNull(q);
		assertTrue(q.isValid());
		assertEquals(Inquest.Kind.CLOSE_CALL, q.getKind());
		assertEquals(13, q.hpAt(q.lowestIndex()));
		assertEquals(47, q.fallDamage());
		assertEquals(3, q.fallTicks());
		assertEquals(19, q.worstTick());
		assertEquals("You took 47 in 3 ticks from 3 Ice troll runts with no protection prayer on; "
			+ "your last food was 9 ticks before. You came back by eating 1.", q.verdict());
		assertEquals(q.lowestIndex(), q.brinkIndex());
		assertEquals("Tick " + (q.brinkIndex() + 1) + " of " + q.ticks() + ": 13 hp, under the 19 you took in one tick. "
			+ "You ate 1 tick later.", q.brinkLine());
		assertEquals(Arrays.asList(
			"UNUSED: 7 food (140 hp) still in your pack at the worst of it",
			"UNUSED: Your Amulet of glory(4) worked there and was not used",
			"UNUSED: Prayer 52 opens all three protection prayers; none was on as the hits landed"),
			strings(q.findings()));
		assertEquals("Close call in Fremennik Isles, 13 hp", q.title());
	}

	@Test
	public void aDeathPastTheTeleportsReachFindsThePointOfNoReturn()
	{
		Fight f = new Fight(80, 20);
		// A player in the Wilderness: harder hits than any Ice troll runt (max hit 15) can deal.
		f.from = "A player";
		f.teleport = "Varrock teleport";
		f.wild = 24;
		f.sharks = 2;
		f.prot = Inquest.MAGIC;
		f.prayerPotions = 1;
		f.calm(5);
		f.foes = 1;
		f.frozen = true;
		f.hit(22);
		f.eat();
		f.hit(25);
		f.hit(18);
		f.recorder.teleport();
		f.hit(16);
		// 19 hp, one shark left and never eaten; the next hit kills.
		f.recorder.hit(25, "A player", null, true);
		f.recorder.deathTick();
		Inquest q = f.recorder.death("Wilderness (level 24)", Inquest.Kit.of(f.readiness()), 2L);
		assertNotNull(q);
		assertEquals(Inquest.Kind.DEATH, q.getKind());
		assertEquals(0, q.hpAt(q.ticks() - 1));
		assertEquals(25, q.worstTick());
		// From 19 hp on, every tick was within one hit of the end and nothing was eaten.
		assertEquals(q.ticks() - 2, q.brinkIndex());
		assertEquals("You took 84 in 4 ticks from a player while praying Protect from Magic; your last food was "
			+ "4 ticks before. You were frozen. You tried to teleport 1 tick before the end.", q.verdict());
		assertEquals(Arrays.asList(
			"UNUSED: 1 food (20 hp) still in your pack, never eaten",
			"LACKING: Your Varrock teleport stops at level 20; you were at level 24, 4 levels too deep",
			"HELD: Protect from Magic was on",
			"NOTE: No ring of life or phoenix necklace worn"),
			strings(q.findings()));
		assertTrue(q.brinkLine().endsWith("and no food was eaten after it."));
		assertTrue(q.getHits().get(q.getHits().size() - 1).isMax());
	}

	@Test
	public void eatingAndSippingAreToldFromBankingAndDamageOverTimeIsNamed()
	{
		Fight f = new Fight(70, 20);
		f.sharks = 28;
		f.calm(3);
		f.recorder.hit(6, "Venom", "Venom", false);
		f.hp -= 6;
		f.tick();
		f.sharks = 8;
		f.tick();
		f.prayerPotions = 4;
		f.tick();
		f.prayerPotions = 3;
		f.recorder.deathTick();
		Inquest q = f.recorder.death("Zul-Andra", Inquest.Kit.of(f.readiness()), 3L);
		int ate = 0;
		for (int i = 0; i < q.ticks(); i++)
		{
			ate += q.ateAt(i);
		}
		assertEquals("Banking 20 sharks is not eating", 0, ate);
		assertEquals("Venom", q.getHits().get(0).blame());
	}

	@Test
	public void aLongCloseCallKeepsTheFallAndFitsTheWindow()
	{
		Fight f = new Fight(99, 30);
		f.calm(40);
		f.hit(70);
		f.calm(70);
		f.eat();
		f.eat();
		f.eat();
		assertNotNull(f.last);
		assertTrue(f.last.ticks() <= Inquest.MAX_TICKS);
		assertEquals(29, f.last.hpAt(f.last.lowestIndex()));
		assertTrue(f.last.lowestIndex() > 0);
	}

	@Test
	public void lowLevelsAreToldWhichProtectionComesFirst()
	{
		Fight f = new Fight(40, 25);
		f.prayerLevel = 31;
		f.prayer = 31;
		f.teleport = null;
		f.sharks = 0;
		f.calm(4);
		f.foes = 1;
		f.hit(15);
		f.hit(15);
		f.recorder.hit(10, "Ice troll runt", null, false);
		f.recorder.deathTick();
		Inquest q = f.recorder.death("Fremennik Isles", Inquest.Kit.of(f.readiness()), 4L);
		List<String> found = strings(q.findings());
		assertTrue(found.toString(), found.contains("LACKING: No food carried"));
		assertTrue(found.toString(), found.contains("LACKING: No teleport carried"));
		assertTrue(found.toString(), found.contains("NOTE: At Prayer 31 no protection prayer is open yet: Protect from Magic comes at 37"));
		assertTrue(q.verdict(), q.verdict().contains("you ate nothing in the"));
	}

	@Test
	public void inquestsAreKeptToTwentyAndSurviveASave()
	{
		Inquests kept = new Inquests();
		for (int i = 0; i < 25; i++)
		{
			Fight f = new Fight(50, 20);
			f.calm(2);
			f.recorder.hit(50, "A player", null, false);
			f.recorder.deathTick();
			kept.add(f.recorder.death("Fremennik Isles", Inquest.Kit.of(f.readiness()), i));
		}
		assertEquals(Inquests.MAX_KEPT, kept.getList().size());
		assertEquals(5L, kept.getList().get(0).getAt());
		String json = GSON.toJson(kept);
		SavedJson.Read<Inquests> read = Inquests.load(GSON, json);
		assertFalse(read.isDamaged());
		assertEquals(20, read.getValue().getList().size());
		Inquest back = read.getValue().getList().get(19);
		assertEquals(kept.getList().get(19).verdict(), back.verdict());
		// Each one is small enough that twenty never make a heavy save.
		assertTrue(json.length() / 20 < 2500);
	}

	@Test
	public void aFullInquestStaysSmall()
	{
		Fight f = new Fight(99, 20);
		f.prayerPotions = 6;
		f.foes = 2;
		for (int i = 0; i < 60; i++)
		{
			f.recorder.hit(12, "Ice troll runt", null, false);
			f.recorder.hit(1, "Venom", "Venom", false);
			f.hp = 99 - i;
			f.tick();
		}
		f.recorder.deathTick();
		Inquest q = f.recorder.death("Fremennik Isles", Inquest.Kit.of(f.readiness()), 5L);
		assertEquals(Inquest.MAX_TICKS, q.ticks());
		String json = GSON.toJson(q);
		// Twenty of the busiest kind (two hits every tick) stay under a hundred kilobytes.
		assertTrue(json.length() + " chars", json.length() < 5000);
	}

	@Test
	public void aDamagedOrNewerInquestIsLeftOutAndFlagged()
	{
		Inquests kept = new Inquests();
		Fight f = new Fight(50, 20);
		f.calm(3);
		f.recorder.deathTick();
		kept.add(f.recorder.death("Fremennik Isles", Inquest.Kit.of(f.readiness()), 9L));
		String json = GSON.toJson(kept);
		String broken = json.replace("[{", "[{\"kind\":\"FROM_THE_FUTURE\"},{");
		SavedJson.Read<Inquests> read = Inquests.load(GSON, broken);
		assertTrue(read.isDamaged());
		assertEquals(1, read.getValue().getList().size());
		SavedJson.Read<Inquests> garbage = Inquests.load(GSON, "{\"list\":[{\"hp\":\"nope\"}]}");
		assertTrue(garbage.isDamaged());
		assertTrue(garbage.getValue().isEmpty());
		assertFalse(Inquests.load(GSON, null).isDamaged());
	}

	@Test
	public void namesArePluralisedPlainly()
	{
		assertEquals("Ice troll runts", Inquest.pluralName("Ice troll runt"));
		assertEquals("Men", Inquest.pluralName("Man"));
		assertEquals("Black demons", Inquest.pluralName("Black demons".substring(0, 11)));
		assertEquals("Protect from Melee and Magic", Inquest.protName(Inquest.MELEE | Inquest.MAGIC));
	}

	private static List<String> strings(List<Inquest.Finding> findings)
	{
		List<String> out = new ArrayList<>();
		for (Inquest.Finding f : findings)
		{
			out.add(f.toString());
		}
		return out;
	}
}
