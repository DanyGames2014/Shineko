package net.danygames2014.shineko.mixin.test;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.storage.ChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ChunkCache.class)
public abstract class ChunkCacheMixin {
    @SuppressWarnings("rawtypes")
    @Shadow
    private Map chunkByPos;

    @Shadow
    public abstract Chunk loadChunk(int chunkX, int chunkZ);

    @Unique
    private Int2ObjectMap<Chunk> shineko$chunksByPos;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void getMap(World world, ChunkStorage storage, ChunkSource generator, CallbackInfo ci) {
        shineko$chunksByPos = new Int2ObjectOpenHashMap<>(1024);
        chunkByPos = shineko$chunksByPos;
    }

    /**
     * @reason Redirecting {@code serverChunkCache.containsKey(Vec2i.hash(chunkX, chunkZ))} still boxes the integer, adding unnecessary memory usage.
     * @author mine_diver
     */
    @Overwrite
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return shineko$chunksByPos.containsKey(ChunkPos.hashCode(chunkX, chunkZ));
    }

    /**
     * @reason This is the only way to avoid integer boxing here.
     * @author mine_diver
     */
    @Overwrite
    public Chunk getChunk(int chunkX, int chunkZ) {
        Chunk var3 = shineko$chunksByPos.get(ChunkPos.hashCode(chunkX, chunkZ));
        return var3 == null ? loadChunk(chunkX, chunkZ) : var3;
    }
}
