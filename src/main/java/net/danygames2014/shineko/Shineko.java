package net.danygames2014.shineko;

import net.danygames2014.shineko.config.ShinekoConfig;
import net.glasslauncher.mods.gcapi3.api.ConfigRoot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Shineko {
    public static Logger LOGGER = LogManager.getLogger("Shineko");
    
    @ConfigRoot(value = "general", visibleName = "Shineko Config")
    public static final ShinekoConfig CONFIG = new ShinekoConfig();
}
