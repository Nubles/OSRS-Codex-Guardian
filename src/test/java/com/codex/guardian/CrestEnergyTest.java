package com.codex.guardian;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codex.guardian.core.Situation;
import org.junit.Test;

/**
 * The crest's run energy follows the game even when nothing else moves. Situations that differ only in energy are
 * equal (so the Ward is not rebuilt every tick while running), which left the crest showing the energy of the last
 * full publish: stand still after a run and it read 100% while the orb read 40%.
 */
public class CrestEnergyTest
{
	private static Situation at(int energy)
	{
		return new Situation.Builder().loggedIn(true).hitpoints(50, 99).prayer(40, 70).runEnergy(energy).build();
	}

	@Test
	public void energyAloneMovesOnlyTheCrest()
	{
		assertTrue(at(100).equals(at(40)));
		assertTrue(CodexGuardianPlugin.energyOnly(at(100), at(40)));
		assertFalse(CodexGuardianPlugin.energyOnly(at(40), at(40)));
		assertFalse(CodexGuardianPlugin.energyOnly(null, at(40)));
		Situation hurt = new Situation.Builder().loggedIn(true).hitpoints(20, 99).prayer(40, 70).runEnergy(40).build();
		// Anything else changing is a full publish, not a crest-only one.
		assertFalse(CodexGuardianPlugin.energyOnly(at(100), hurt));
	}
}
