package net.danygames2014.shineko.config;

import net.glasslauncher.mods.gcapi3.api.ConfigEntry;

public class ShinekoConfig {
    @ConfigEntry(name = "Threaded Lighting", requiresRestart = true)
    public Boolean threadedLighting = true;

    @ConfigEntry(name = "Light Update Queue Size", minValue = 32768, maxValue = 1048576, requiresRestart = true)
    public Integer lightUpdateQueueSize = 65536;

    @ConfigEntry(name = "Light Thread Update Batch Size", minValue = 256, maxValue = 32768, requiresRestart = true)
    public Integer lightThreadUpdateBatchSize = 4096;
}
