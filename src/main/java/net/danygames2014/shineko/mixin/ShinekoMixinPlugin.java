package net.danygames2014.shineko.mixin;

import net.danygames2014.shineko.Shineko;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ShinekoMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
//        if (!FabricLoader.getInstance().isModLoaded("smoothbeta") && !FabricLoader.getInstance().isModLoaded("amphetamine") && !FabricLoader.getInstance().isModLoaded("nitch")) {
//            Shineko.LOGGER.info("SmoothBeta or its derivates not installed, optimizing ChunkCache");
//            return List.of("optimization.ChunkCacheMixin");
//        }

        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
