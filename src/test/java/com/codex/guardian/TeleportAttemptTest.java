package com.codex.guardian;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What an inquest counts as trying to teleport: read from the click, which goes through untouched. */
public class TeleportAttemptTest
{
	@Test
	public void teleportsAreToldFromHandlingTheItem()
	{
		assertTrue(CodexGuardianPlugin.teleportAttempt("Rub", "<col=ff9040>Amulet of glory(4)</col>"));
		assertTrue(CodexGuardianPlugin.teleportAttempt("Edgeville", "Amulet of glory(3)"));
		assertTrue(CodexGuardianPlugin.teleportAttempt("Break", "Varrock teleport"));
		assertTrue(CodexGuardianPlugin.teleportAttempt("Cast", "<col=00ff00>Varrock Teleport</col>"));
		assertFalse(CodexGuardianPlugin.teleportAttempt("Drop", "Varrock teleport"));
		assertFalse(CodexGuardianPlugin.teleportAttempt("Withdraw-1", "Amulet of glory(4)"));
		assertFalse(CodexGuardianPlugin.teleportAttempt("Wear", "Amulet of glory(4)"));
		assertFalse(CodexGuardianPlugin.teleportAttempt("Cast", "Ice Barrage"));
		assertFalse(CodexGuardianPlugin.teleportAttempt("Eat", "Shark"));
		// An empty glory and a tablet into the Wilderness take no one out.
		assertFalse(CodexGuardianPlugin.teleportAttempt("Rub", "Amulet of glory"));
		assertFalse(CodexGuardianPlugin.teleportAttempt("Break", "Annakarl teleport"));
	}

	@Test
	public void theInquestsKeyIsItsOwn()
	{
		assertEquals("inquestsV1", CodexGuardianPlugin.INQUESTS_KEY);
	}
}
