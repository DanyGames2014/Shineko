package net.danygames2014.shineko;

import net.danygames2014.shineko.config.ShinekoConfig;
import net.glasslauncher.mods.gcapi3.api.ConfigRoot;
import net.modificationstation.stationapi.api.mod.entrypoint.Entrypoint;
import org.apache.logging.log4j.Logger;

public class Shineko {
    @Entrypoint.Logger
    public static Logger LOGGER;
    
    @ConfigRoot(value = "general", visibleName = "Shineko Config")
    public static final ShinekoConfig CONFIG = new ShinekoConfig();
}
