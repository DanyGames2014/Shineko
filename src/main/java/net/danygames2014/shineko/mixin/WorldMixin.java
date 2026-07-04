package net.danygames2014.shineko.mixin;

import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.thread.ShinekoLightThread;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightUpdate;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.storage.WorldStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;

@Mixin(World.class)
public abstract class WorldMixin implements ShinekoWorld {
    @Shadow
    @Final
    public Dimension dimension;

    @SuppressWarnings("rawtypes")
    @Shadow
    private List lightingQueue;

    // Keeping track of light updates
    @Unique
    LinkedTransferQueue<LightUpdate> lightUpdates = new LinkedTransferQueue<>();

    @Unique
    LinkedTransferQueue<LightUpdate> lightUpdatesInternal = new LinkedTransferQueue<>();

    // Light Thread 
    @Unique
    ShinekoLightThread lightThread;
    
    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldStorage;Ljava/lang/String;JLnet/minecraft/world/dimension/Dimension;)V", at = @At("TAIL"))
    public void initLightThread(WorldStorage storage, String name, long seed, Dimension dimension, CallbackInfo ci) {
        if (Shineko.CONFIG.threadedLighting) {
            lightThread = new ShinekoLightThread("Shineko Light Thread (" + this.dimension.id + ") " + ZonedDateTime.now(), (World) (Object) this);
            lightThread.start();
        }
    }
    
    @Override
    public LinkedTransferQueue<LightUpdate> shineko$getLiqhtUpdateQueue() {
        return lightUpdates;
    }

    @Override
    public ShinekoLightThread shineko$getLightThread() {
        return lightThread;
    }

    @Inject(method = "doLightingUpdates", at = @At(value = "HEAD"), cancellable = true)
    public void cancelVanillaLightUpdateProcessing(CallbackInfoReturnable<Boolean> cir) {
        if (!Shineko.CONFIG.threadedLighting) {
            return;
        }
        
        lightUpdatesInternal.drainTo(lightUpdates, Shineko.CONFIG.lightThreadUpdateBatchSize);
        
        // Check the vanilla lighting queue, which should be empty in this case
        if (!this.lightingQueue.isEmpty()) {
            Shineko.LOGGER.warn("Lighting queue is not empty! This is a bug!");
        }
        
        cir.setReturnValue(false);
    }

    @Inject(method = "queueLightUpdate(Lnet/minecraft/world/LightType;IIIIIIZ)V", at = @At("HEAD"), cancellable = true)
    public void hijackLightUpdate(LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean allowMerge, CallbackInfo ci) {
        if (!Shineko.CONFIG.threadedLighting) {
            ci.cancel();
            return;
        }
        
        if (this.dimension.hasCeiling && type == LightType.SKY) {
            ci.cancel();
            return;
        }

        if (!this.lightUpdatesInternal.offer(new LightUpdate(type, minX, minY, minZ, maxX, maxY, maxZ))) {
            Shineko.LOGGER.warn("Failed to queue light update! Queue is full!");
            if (this.lightUpdatesInternal.size() > 1000000) {
                Shineko.LOGGER.warn("Over 1,000,000 updates pending! Clearing queue.");
                this.lightUpdatesInternal.clear();
            }
        }

        ci.cancel();
    }
}
