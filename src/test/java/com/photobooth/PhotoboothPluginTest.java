package com.photobooth;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Dev launcher: run this main() to start RuneLite with Photobooth loaded. */
public class PhotoboothPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PhotoboothPlugin.class);
		RuneLite.main(args);
	}
}
