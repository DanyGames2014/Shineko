package net.danygames2014.shineko.mixin;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.danygames2014.shineko.thread.ShinekoLightThread;
import net.minecraft.client.Minecraft;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightUpdate;
import net.minecraft.world.dimension.Dimension;
import net.modificationstation.stationapi.api.world.StationFlatteningWorld;
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
public abstract class WorldMixin implements ShinekoWorld, StationFlatteningWorld {
    @Shadow
    @Final
    public Dimension dimension;
    
    // Keeping track of light updates
    @Unique
    LinkedTransferQueue<LightUpdate> lightUpdates = new LinkedTransferQueue<>();
    
    @Unique
    LinkedTransferQueue<LightUpdate> lightUpdatesInternal = new LinkedTransferQueue<>();
    
    @Unique
    LinkedTransferQueue<Long> visitedChunks = new LinkedTransferQueue<>();
    
    @Unique
    private final LongArrayList visitedChunksInternal = new LongArrayList(512);
    
    // Light Thread 
    @Unique
    ShinekoLightThread lightThread;
    
    @SuppressWarnings("rawtypes")
    @Shadow
    private List lightingQueue;

    @Inject(method = {"<init>(Lnet/minecraft/world/storage/WorldStorage;Ljava/lang/String;JLnet/minecraft/world/dimension/Dimension;)V", "<init>(Lnet/minecraft/world/storage/WorldStorage;Ljava/lang/String;Lnet/minecraft/world/dimension/Dimension;J)V", "<init>(Lnet/minecraft/world/World;Lnet/minecraft/world/dimension/Dimension;)V"}, at = @At("TAIL"))
    public void initLightThread(CallbackInfo ci) {
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
    public LinkedTransferQueue<Long> shineko$getVisitedChunks() {
        return visitedChunks;
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

        if (Minecraft.INSTANCE.worldRenderer != null && Minecraft.INSTANCE.worldRenderer.world != null && !visitedChunks.isEmpty()) {
            visitedChunks.drainTo(visitedChunksInternal);

            for (long key : visitedChunksInternal) {
                int chunkX = (int) (key >> 40);
                int chunkZ = (int) ((key << 24) >> 40);
                int sectionY = (int) ((key << 48) >> 48);

                int blockX = chunkX << 4;
                int blockZ = chunkZ << 4;
                int minBlockY = sectionY << 4;

                Minecraft.INSTANCE.worldRenderer.markDirty(
                        blockX, minBlockY, blockZ,
                        blockX, minBlockY + 15, blockZ
                );
            }
            
            visitedChunksInternal.clear();
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
