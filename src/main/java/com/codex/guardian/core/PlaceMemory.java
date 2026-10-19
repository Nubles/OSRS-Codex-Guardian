package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.List;

/**
 * What this account has lived through at a place, in a line or two for its doorway: the close calls survived
 * there and the hardest hit taken there. A place you nearly died at deserves a reminder every time you go back.
 */
public final class PlaceMemory
{
	private PlaceMemory()
	{
	}

	/**
	 * At most two lines, the close calls first: "2 close calls here, 4 hp low", "Black demon hit you for 16
	 * here". Empty when nothing has happened at the place, or when there is no place.
	 *
	 * @param place the place as the journal writes it: a zone name, or "Wilderness level N"
	 */
	public static List<String> lines(Journal journal, HitMemory hits, String place)
	{
		List<String> lines = new ArrayList<>();
		if (place == null)
		{
			return lines;
		}
		String calls = journal == null ? null : journal.memoryOf(place);
		if (calls != null)
		{
			lines.add(calls);
		}
		HitMemory.Record hardest = hits == null ? null : hits.hardestAt(place);
		if (hardest != null)
		{
			lines.add(hardest.getName() + " hit you for " + hardest.getMax() + " here");
		}
		return lines;
	}

	/** The place a situation is at, as the journal writes it, or null on open ground. */
	public static String placeOf(Situation s)
	{
		Zone zone = s.getZone();
		if (zone == null)
		{
			return null;
		}
		return zone.getKind() == Zone.Kind.WILDERNESS ? "Wilderness level " + s.getWildernessLevel() : zone.getName();
	}
}
