package net.danygames2014.shineko.mixin;

import net.danygames2014.shineko.LightWrite;
import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.thread.ShinekoLightThread;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.light.LightUpdate;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.storage.WorldStorage;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;

@Mixin(World.class)
public abstract class WorldMixin implements ShinekoWorld {
    @Shadow
    @Final
    public Dimension dimension;

    @Shadow
    public abstract boolean isPosLoaded(int x, int y, int z);

    @Shadow
    public abstract Chunk getChunkFromPos(int x, int z);

    @Shadow
    static int lightingQueueCount;

    @SuppressWarnings("rawtypes")
    @Shadow
    private List lightingQueue;

    @Shadow
    public abstract void setLight(LightType lightType, int x, int y, int z, int value);

    // Keeping track of light updates
    @Unique
    LinkedTransferQueue<LightUpdate> lightUpdates = new LinkedTransferQueue<>();

    @Unique private final LightUpdate[] recentUpdates = new LightUpdate[5];
    
    @Unique private int recentIdx = 0;

    // Light thread and communication with it
    @Unique
    ShinekoLightThread lightThread;
    
    @Unique
    LinkedTransferQueue<LightWrite> lightWrites = new LinkedTransferQueue<>();
    
    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldStorage;Ljava/lang/String;JLnet/minecraft/world/dimension/Dimension;)V", at = @At("TAIL"))
    public void initLightThread(WorldStorage storage, String name, long seed, Dimension dimension, CallbackInfo ci) {
        lightThread = new ShinekoLightThread("Shineko Light Thread (" + this.dimension.id + ")", (World) (Object) this);
        lightThread.start();
    }
    
    @Override
    public LinkedTransferQueue<LightUpdate> shineko$getLiqhtUpdateQueue() {
        return lightUpdates;
    }

    @Override
    public LinkedTransferQueue<LightWrite> shineko$getCompletedWritesQueue() {
        return lightWrites;
    }

    @Inject(method = "doLightingUpdates", at = @At(value = "HEAD"), cancellable = true)
    public void cancelVanillaLightUpdateProcessing(CallbackInfoReturnable<Boolean> cir) {
        System.err.println("Processing " + lightWrites.size() + " light writes");
        
        LightWrite write;
        while ((write = lightWrites.poll()) != null) {
            this.setLight(write.type(), write.x(), write.y(), write.z(), write.val());
        }
        
        if (!this.lightingQueue.isEmpty()) {
            Shineko.LOGGER.warn("Lighting queue is not empty! This is a bug!");
        }
        
        cir.setReturnValue(false);
    }

    @Inject(method = "queueLightUpdate(Lnet/minecraft/world/LightType;IIIIIIZ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;dimension:Lnet/minecraft/world/dimension/Dimension;", ordinal = 0, opcode = Opcodes.GETFIELD), cancellable = true)
    public void cancelVanillaLightUpdate(LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean allowMerge, CallbackInfo ci) {
        ci.cancel();
    }
    
    @Inject(method = "queueLightUpdate(Lnet/minecraft/world/LightType;IIIIIIZ)V", at = @At("HEAD"))
    public void hijackLightUpdate(LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean allowMerge, CallbackInfo ci) {
        if (this.dimension.hasCeiling && type == LightType.SKY) {
            return;
        }
        
        if (++lightingQueueCount == 50) { 
            --lightingQueueCount;
            return; 
        }

        try {
            int centerX = (maxX + minX) >> 1;
            int centerZ = (maxZ + minZ) >> 1;

            if (!this.isPosLoaded(centerX, 64, centerZ)) {
                return;
            }
            
            Chunk chunk = this.getChunkFromPos(centerX, centerZ);
            if (chunk == null || chunk.isEmpty()) {
                return;
            }

            // 1. Merge tracking happens ONLY on the main thread's local copies
            if (allowMerge) {
                for (int i = 0; i < 5; i++) {
                    LightUpdate pastUpdate = recentUpdates[i];
                    if (pastUpdate != null && pastUpdate.lightType == type) {
                        if (pastUpdate.expand(minX, minY, minZ, maxX, maxY, maxZ)) {
                            // Crucial: The tracking object expanded successfully.
                            // We must send a fresh snapshot of this new size to the background thread.
                            this.lightUpdates.offer(new LightUpdate(type, pastUpdate.minX, pastUpdate.minY, pastUpdate.minZ, pastUpdate.maxX, pastUpdate.maxY, pastUpdate.maxZ));
                            return;
                        }
                    }
                }
            }

            if (this.lightUpdates.size() > 1000000) {
                Shineko.LOGGER.warn("Over 1,000,000 updates pending! Clearing queue.");
                this.lightUpdates.clear();
                Arrays.fill(recentUpdates, null);
                return;
            }

            // 2. Fresh independent objects for both tracking and queuing
            LightUpdate trackingUpdate = new LightUpdate(type, minX, minY, minZ, maxX, maxY, maxZ);
            LightUpdate queuedUpdate = new LightUpdate(type, minX, minY, minZ, maxX, maxY, maxZ);

            if (allowMerge) {
                recentUpdates[recentIdx] = trackingUpdate;
                recentIdx = (recentIdx + 1) % 5;
            }

            this.lightUpdates.offer(queuedUpdate);

        } finally {
            --lightingQueueCount;
        }
    }
}
