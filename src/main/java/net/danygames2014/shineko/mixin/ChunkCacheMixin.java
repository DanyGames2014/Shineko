package net.danygames2014.shineko.mixin;

import net.danygames2014.shineko.mixininterface.ShinekoChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkCache.class)
public class ChunkCacheMixin {
    @Shadow
    private Chunk empty;

    @Inject(method = "loadChunk", at = @At(value = "TAIL"))
    public void populateLight(int chunkX, int chunkZ, CallbackInfoReturnable<Chunk> cir) {
        Chunk chunk = cir.getReturnValue();

        if (chunk == this.empty) {
            return;
        }

        ((ShinekoChunk) chunk).shineko$populateLight();
    }
}
