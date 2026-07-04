package net.danygames2014.shineko.config;

import net.glasslauncher.mods.gcapi3.api.ConfigEntry;

public class ShinekoConfig {
    @ConfigEntry(name = "Threaded Lighting", requiresRestart = true)
    public Boolean threadedLighting = true;
}
