package net.danygames2014.shineko.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.mixininterface.ShinekoLightUpdate;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.minecraft.block.Block;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.light.LightUpdate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.LinkedTransferQueue;

@Mixin(LightUpdate.class)
public class LightUpdateMixin implements ShinekoLightUpdate {
    @Shadow
    public int minX;
    @Shadow
    public int maxX;
    @Shadow
    public int minY;
    @Shadow
    public int maxY;
    @Shadow
    public int minZ;
    @Shadow
    public int maxZ;
    @Shadow
    @Final
    public LightType lightType;
    
    @Unique
    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    @Unique
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    @Unique
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};
    
    @Unique
    private static final ThreadLocal<int[]> QUEUE_X = ThreadLocal.withInitial(() -> new int[Shineko.CONFIG.lightUpdateQueueSize]);
    @Unique
    private static final ThreadLocal<int[]> QUEUE_Y = ThreadLocal.withInitial(() -> new int[Shineko.CONFIG.lightUpdateQueueSize]);
    @Unique
    private static final ThreadLocal<int[]> QUEUE_Z = ThreadLocal.withInitial(() -> new int[Shineko.CONFIG.lightUpdateQueueSize]);
    @Unique
    private static final ThreadLocal<int[]> REMOVE_QUEUE_X = ThreadLocal.withInitial(() -> new int[Shineko.CONFIG.lightUpdateQueueSize]);
    @Unique
    private static final ThreadLocal<int[]> REMOVE_QUEUE_Y = ThreadLocal.withInitial(() -> new int[Shineko.CONFIG.lightUpdateQueueSize]);
    @Unique
    private static final ThreadLocal<int[]> REMOVE_QUEUE_Z = ThreadLocal.withInitial(() -> new int[Shineko.CONFIG.lightUpdateQueueSize]);
    @Unique
    private static final ThreadLocal<int[]> REMOVE_VAL_CACHE = ThreadLocal.withInitial(() -> new int[Shineko.CONFIG.lightUpdateQueueSize]);
    @Unique
    private static final ThreadLocal<Long2ObjectOpenHashMap<Chunk>> VISITED_CHUNKS = ThreadLocal.withInitial(() -> new Long2ObjectOpenHashMap<>(512, 0.5f));
    @Unique
    private static final ThreadLocal<LongOpenHashSet> VISITED_CHUNK_KEYS = ThreadLocal.withInitial(() -> new LongOpenHashSet(512, 0.5f));
    
    @Unique
    private int worldBottomY;

    @Unique
    private int worldTopY;

    @Override
    public LongOpenHashSet shineko$getVisitedChunks() {
        return VISITED_CHUNK_KEYS.get();
    }

    @Unique
    private void setLight(LongOpenHashSet visitedChunkKeys, Chunk chunk, LightType type, int x, int y, int z, int lightLevel) {
        boolean wasDirty = chunk.dirty;
        chunk.setLight(type, x & 15, y, z & 15, lightLevel);

        int cx = x >> 4;
        int cy = y >> 4;
        int cz = z >> 4;


        long key = (((long) cx & 0xFFFFFFL) << 40)
                | (((long) cz & 0xFFFFFFL) << 16)
                | ((long) cy & 0xFFFFL);
        visitedChunkKeys.add(key);

        // Extract local position inside the 16x16x16 section
        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;

        // 2. X-Axis Borders
        if (lx == 0) {
            visitedChunkKeys.add((((long) (cx - 1) & 0xFFFFFFL) << 40) | (((long) cz & 0xFFFFFFL) << 16) | ((long) cy & 0xFFFFL));
        } else if (lx == 15) {
            visitedChunkKeys.add((((long) (cx + 1) & 0xFFFFFFL) << 40) | (((long) cz & 0xFFFFFFL) << 16) | ((long) cy & 0xFFFFL));
        }

        // 3. Y-Axis Borders (Fixes the dark floors/ceilings seen in obrazek.jpg)
        if (ly == 0) {
            visitedChunkKeys.add((((long) cx & 0xFFFFFFL) << 40) | (((long) cz & 0xFFFFFFL) << 16) | ((long) (cy - 1) & 0xFFFFL));
        } else if (ly == 15) {
            visitedChunkKeys.add((((long) cx & 0xFFFFFFL) << 40) | (((long) cz & 0xFFFFFFL) << 16) | ((long) (cy + 1) & 0xFFFFL));
        }

        // 4. Z-Axis Borders
        if (lz == 0) {
            visitedChunkKeys.add((((long) cx & 0xFFFFFFL) << 40) | (((long) (cz - 1) & 0xFFFFFFL) << 16) | ((long) cy & 0xFFFFL));
        } else if (lz == 15) {
            visitedChunkKeys.add((((long) cx & 0xFFFFFFL) << 40) | (((long) (cz + 1) & 0xFFFFFFL) << 16) | ((long) cy & 0xFFFFL));
        }

        chunk.dirty = wasDirty;
    }

    @Unique
    public Chunk getChunk(Long2ObjectOpenHashMap<Chunk> visitedChunks, World world, int chunkX, int chunkZ) {
        long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);

        if (visitedChunks.containsKey(chunkKey)) {
            return visitedChunks.get(chunkKey);
        }

        Chunk chunk = null;
        if (world.chunkSource.isChunkLoaded(chunkX, chunkZ)) {
            chunk = world.chunkSource.getChunk(chunkX, chunkZ);
        }

        visitedChunks.put(chunkKey, chunk);
        return chunk;
    }

    @Inject(method = "updateLight", at = @At(value = "HEAD"), cancellable = true)
    public void smileyFace(World world, CallbackInfo ci) {
        long startTime = System.nanoTime();

        boolean threaded = Shineko.CONFIG.threadedLighting;

        worldBottomY = world.getBottomY();
        worldTopY = world.getTopY();
        if (this.minY < worldBottomY) this.minY = worldBottomY;
        if (this.maxY >= worldTopY) this.maxY = worldTopY - 1;

        int[] queueX = QUEUE_X.get();
        int[] queueY = QUEUE_Y.get();
        int[] queueZ = QUEUE_Z.get();
        int[] removeX = REMOVE_QUEUE_X.get();
        int[] removeY = REMOVE_QUEUE_Y.get();
        int[] removeZ = REMOVE_QUEUE_Z.get();
        int[] removeVal = REMOVE_VAL_CACHE.get();

        int head = 0;
        int tail = 0;
        int rHead = 0;
        int rTail = 0;
        int maxCapacity = queueX.length;

        // 1. Seed Stage
        Long2ObjectOpenHashMap<Chunk> visitedChunks = VISITED_CHUNKS.get();
        visitedChunks.clear();
        LongOpenHashSet visitedChunkKeys = VISITED_CHUNK_KEYS.get();
        visitedChunkKeys.clear();

        for (int x = this.minX; x <= this.maxX; ++x) {
            int chunkX = x >> 4;
            for (int z = this.minZ; z <= this.maxZ; ++z) {
                int chunkZ = z >> 4;

                Chunk chunk = getChunk(visitedChunks, world, chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                for (int y = this.minY; y <= this.maxY; ++y) {
                    int currentLight = world.getBrightness(this.lightType, x, y, z);
                    int targetLight = calculateTargetLight(world, visitedChunks, x, y, z);

                    if (currentLight != targetLight) {
                        if (targetLight < currentLight) {
                            if (rTail < maxCapacity) {
                                removeX[rTail] = x;
                                removeY[rTail] = y;
                                removeZ[rTail] = z;
                                removeVal[rTail] = currentLight;
                                rTail++;
                            }
                            setLight(visitedChunkKeys, chunk, this.lightType, x, y, z, 0);
                        } else {
                            setLight(visitedChunkKeys, chunk, this.lightType, x, y, z, targetLight);
                            if (tail < maxCapacity) {
                                queueX[tail] = x;
                                queueY[tail] = y;
                                queueZ[tail] = z;
                                tail++;
                            }
                        }
                    }
                }
            }
        }

        // 2. Depropagation Stage
        while (rHead < rTail) {
            int cx = removeX[rHead];
            int cy = removeY[rHead];
            int cz = removeZ[rHead];
            int oldVal = removeVal[rHead++];

            for (int i = 0; i < 6; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                int nz = cz + DZ[i];

                if (ny < worldBottomY || ny >= worldTopY) continue;

                Chunk chunk = getChunk(visitedChunks, world, nx >> 4, nz >> 4);
                if (chunk == null) continue;

                int neighborLight = world.getBrightness(this.lightType, nx, ny, nz);
                int opacity = Block.BLOCKS_LIGHT_OPACITY[world.getBlockId(nx, ny, nz)];
                if (opacity <= 0) opacity = 1;

                if (neighborLight != 0 && neighborLight == oldVal - opacity) {
                    if (rTail < maxCapacity) {
                        removeX[rTail] = nx;
                        removeY[rTail] = ny;
                        removeZ[rTail] = nz;
                        removeVal[rTail] = neighborLight;
                        rTail++;
                    }
                    setLight(visitedChunkKeys, chunk, this.lightType, nx, ny, nz, 0);
                } else if (neighborLight >= oldVal) {
                    if (tail < maxCapacity) {
                        queueX[tail] = nx;
                        queueY[tail] = ny;
                        queueZ[tail] = nz;
                        tail++;
                    }
                }
            }
        }

        // 3. Propagation Stage
        while (head < tail) {
            int cx = queueX[head];
            int cy = queueY[head];
            int cz = queueZ[head];
            head++;

            int currentLight = world.getBrightness(this.lightType, cx, cy, cz);

            for (int i = 0; i < 6; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                int nz = cz + DZ[i];

                if (ny < worldBottomY || ny >= worldTopY) continue;

                Chunk chunk = getChunk(visitedChunks, world, nx >> 4, nz >> 4);
                if (chunk == null) continue;

                int neighborLight = world.getBrightness(this.lightType, nx, ny, nz);
                int opacity = Block.BLOCKS_LIGHT_OPACITY[world.getBlockId(nx, ny, nz)];
                if (opacity <= 0) opacity = 1;

                if (neighborLight < currentLight - opacity) {
                    setLight(visitedChunkKeys, chunk, this.lightType, nx, ny, nz, currentLight - opacity);
                    if (tail < maxCapacity) {
                        queueX[tail] = nx;
                        queueY[tail] = ny;
                        queueZ[tail] = nz;
                        tail++;
                    }
                }
            }
        }

        if (!threaded) {
            if (!visitedChunks.isEmpty()) {
                LinkedTransferQueue<Long> renderQueue = ((ShinekoWorld) world).shineko$getVisitedChunks();
                for (long packedKey : visitedChunkKeys) {
                    renderQueue.offer(packedKey);
                }
            }
        }

        long endTime = System.nanoTime();
        //System.out.println("Light update took " + (endTime - startTime) / 1000 + "us on thread " + Thread.currentThread().getName());

        ci.cancel();
    }

    @Unique
    private int calculateTargetLight(World world, Long2ObjectOpenHashMap<Chunk> visitedChunks, int x, int y, int z) {
        int blockId = world.getBlockId(x, y, z);
        int opacity = Block.BLOCKS_LIGHT_OPACITY[blockId];
        if (opacity <= 0) opacity = 1;

        int baseLight = switch (this.lightType) {
            case SKY -> world.isTopY(x, y, z) ? 15 : 0;
            case BLOCK -> Block.BLOCKS_LIGHT_LUMINANCE[blockId];
        };

        if (this.lightType == LightType.SKY && baseLight == 15) return 15;
        if (opacity >= 15 && baseLight == 0) return 0;

        int maxNeighbor = 0;
        for (int i = 0; i < 6; i++) {
            int nx = x + DX[i];
            int ny = y + DY[i];
            int nz = z + DZ[i];

            // Guard against vertical world boundaries
            if (ny < worldBottomY || ny >= worldTopY) continue;

            // Guard against doing lighting in chunks that are not valid
            if (getChunk(visitedChunks, world, nx >> 4, nz >> 4) == null) continue;

            int neighborLight = world.getBrightness(this.lightType, nx, ny, nz);
            if (neighborLight > maxNeighbor) {
                maxNeighbor = neighborLight;
            }
        }

        return Math.max(baseLight, Math.max(0, maxNeighbor - opacity));
    }
}