package com.codex.guardian.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The inquests kept for one account: the newest {@link #MAX_KEPT} deaths and close calls, oldest first. Plain data,
 * saved as JSON in the RuneLite profile beside the journal; a few kilobytes each at most, so the save stays bounded.
 */
public final class Inquests
{
	public static final int MAX_KEPT = 20;

	List<Inquest> list = new ArrayList<>();

	/**
	 * Reads saved inquests, keeping every one that reads on its own; an empty set when nothing can be read. The
	 * result says when anything was left out, so the saved text can be kept aside.
	 */
	public static SavedJson.Read<Inquests> load(com.google.gson.Gson gson, String json)
	{
		SavedJson.Read<Inquests> read = SavedJson.read(gson, json, Inquests.class);
		Inquests kept = read.getValue() == null ? new Inquests() : read.getValue();
		boolean dropped = false;
		if (kept.list != null)
		{
			// One with a kind this version does not know (from a newer Guardian), or a broken one, is left out.
			dropped = kept.list.stream().anyMatch(q -> q == null || !q.isValid()) || kept.list.size() > MAX_KEPT;
		}
		return new SavedJson.Read<>(kept.repair(), read.isDamaged() || dropped);
	}

	public Inquests repair()
	{
		if (list == null)
		{
			list = new ArrayList<>();
		}
		list.removeIf(q -> q == null || !q.isValid());
		list.forEach(Inquest::repair);
		trim();
		return this;
	}

	/** Keeps a new inquest; the oldest goes once there are more than {@link #MAX_KEPT}. */
	public void add(Inquest inquest)
	{
		if (inquest == null || !inquest.isValid())
		{
			return;
		}
		list.add(inquest);
		trim();
	}

	private void trim()
	{
		while (list.size() > MAX_KEPT)
		{
			list.remove(0);
		}
	}

	/** Oldest first. */
	public List<Inquest> getList()
	{
		return Collections.unmodifiableList(list);
	}

	public boolean isEmpty()
	{
		return list.isEmpty();
	}
}
