package net.danygames2014.shineko.mixin;

import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.danygames2014.shineko.thread.ShinekoLightThread;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    public World world;

    @Inject(method = "setWorld(Lnet/minecraft/world/World;Ljava/lang/String;Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At(value = "HEAD"))
    public void stopLightThread(World newWorld, String message, PlayerEntity player, CallbackInfo ci) {
        if (!Shineko.CONFIG.threadedLighting) {
            return;
        }

        if (this.world instanceof ShinekoWorld shinekoWorld) {
            ShinekoLightThread lightThread = shinekoWorld.shineko$getLightThread();
            if (lightThread != null) {
                lightThread.interrupt();
            }
        }
    }
}
