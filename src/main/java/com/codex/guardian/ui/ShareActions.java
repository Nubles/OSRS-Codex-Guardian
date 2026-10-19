package com.codex.guardian.ui;

import java.awt.image.BufferedImage;

/** What the seal card's buttons do; the plugin saves files and fills the clipboard. */
public interface ShareActions
{
	/** Asks where to save the card, offering that file name. */
	void save(BufferedImage card, String fileName);

	void copy(BufferedImage card);
}
