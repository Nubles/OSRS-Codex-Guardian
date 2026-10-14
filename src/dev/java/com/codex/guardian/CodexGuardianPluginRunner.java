package com.codex.guardian;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexGuardianPluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexGuardianPlugin.class);
		RuneLite.main(args);
	}
}
