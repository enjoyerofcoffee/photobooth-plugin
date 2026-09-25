package com.photobooth;

import com.photobooth.PhotoboothPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PhotoboothPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PhotoboothPlugin.class);
		RuneLite.main(args);
	}
}