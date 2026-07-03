package net.danygames2014.shineko.mixin;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
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

@Mixin(LightUpdate.class)
public class LightUpdateMixin {
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
    private static final ThreadLocal<int[]> QUEUE_X = ThreadLocal.withInitial(() -> new int[65536]);
    @Unique 
    private static final ThreadLocal<int[]> QUEUE_Y = ThreadLocal.withInitial(() -> new int[65536]);
    @Unique 
    private static final ThreadLocal<int[]> QUEUE_Z = ThreadLocal.withInitial(() -> new int[65536]);
    
    @Unique 
    private static final ThreadLocal<int[]> REMOVE_QUEUE_X = ThreadLocal.withInitial(() -> new int[65536]);
    @Unique 
    private static final ThreadLocal<int[]> REMOVE_QUEUE_Y = ThreadLocal.withInitial(() -> new int[65536]);
    @Unique 
    private static final ThreadLocal<int[]> REMOVE_QUEUE_Z = ThreadLocal.withInitial(() -> new int[65536]);
    
    @Unique
    private static final ThreadLocal<int[]> REMOVE_VAL_CACHE = ThreadLocal.withInitial(() -> new int[65536]);

    @Unique
    private final ThreadLocal<Long2BooleanOpenHashMap> VISITED_CHUNKS = ThreadLocal.withInitial(Long2BooleanOpenHashMap::new);
    
    @Unique
    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    @Unique
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    @Unique
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};
    
    @Inject(method = "updateLight", at = @At(value = "HEAD"), cancellable = true)
    public void smileyFace(World world, CallbackInfo ci) {
        long startTime = System.nanoTime();
        
        int bottomY = world.getBottomY();
        int topY = world.getTopY();
        if (this.minY < bottomY) this.minY = bottomY;
        if (this.maxY >= topY) this.maxY = topY;

        int pMinX = this.minX - 15;
        int pMaxX = this.maxX + 15;
        int pMinZ = this.minZ - 15;
        int pMaxZ = this.maxZ + 15;

        int[] queueX = QUEUE_X.get();
        int[] queueY = QUEUE_Y.get();
        int[] queueZ = QUEUE_Z.get();
        int[] removeX = REMOVE_QUEUE_X.get();
        int[] removeY = REMOVE_QUEUE_Y.get();
        int[] removeZ = REMOVE_QUEUE_Z.get();
        int[] removeVal = REMOVE_VAL_CACHE.get();

        int head = 0; int tail = 0;
        int rHead = 0; int rTail = 0;
        int maxCapacity = queueX.length;

        // 1. Seed Stage
        Long2BooleanOpenHashMap visitedChunks = VISITED_CHUNKS.get();
        visitedChunks.clear();
        
        for (int x = this.minX; x <= this.maxX; ++x) {
            for (int z = this.minZ; z <= this.maxZ; ++z) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                long chunkKey = ((long) chunkX << 32) | chunkZ;

                // Check if we already checked the validity of this chunk
                boolean isChunkValid;
                if (visitedChunks.containsKey(chunkKey)) {
                    // Already checked
                    isChunkValid = visitedChunks.get(chunkKey);
                } else {
                    // Not checked yet
                    if (!world.isRegionLoaded(x, 0, z, 1)) {
                        isChunkValid = false;
                    } else {
                        Chunk chunk = world.getChunk(chunkX, chunkZ);
                        isChunkValid = (chunk != null && !chunk.isEmpty());
                    }
                    visitedChunks.put(chunkKey, isChunkValid);
                }

                // If the chunk is not valid, skip it
                if (!isChunkValid) {
                    continue;
                }
                
                for (int y = this.minY; y <= this.maxY; ++y) {
                    int currentLight = world.getBrightness(this.lightType, x, y, z);
                    int targetLight = calculateTargetLight(world, x, y, z);

                    if (currentLight != targetLight) {
                        if (targetLight < currentLight) {
                            if (rTail < maxCapacity) {
                                removeX[rTail] = x;
                                removeY[rTail] = y;
                                removeZ[rTail] = z;
                                removeVal[rTail] = currentLight;
                                rTail++;
                            }
                            world.setLight(this.lightType, x, y, z, 0);
                        } else {
                            world.setLight(this.lightType, x, y, z, targetLight);
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

                if (nx < pMinX || nx > pMaxX || nz < pMinZ || nz > pMaxZ || ny < bottomY || ny > topY) continue;

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
                    world.setLight(this.lightType, nx, ny, nz, 0);
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

                if (nx < pMinX || nx > pMaxX || nz < pMinZ || nz > pMaxZ || ny < bottomY || ny > topY) continue;

                int neighborLight = world.getBrightness(this.lightType, nx, ny, nz);
                int opacity = Block.BLOCKS_LIGHT_OPACITY[world.getBlockId(nx, ny, nz)];
                if (opacity <= 0) opacity = 1;

                if (neighborLight < currentLight - opacity) {
                    world.setLight(this.lightType, nx, ny, nz, currentLight - opacity);
                    if (tail < maxCapacity) {
                        queueX[tail] = nx;
                        queueY[tail] = ny;
                        queueZ[tail] = nz;
                        tail++;
                    }
                }
            }
        }

        long endTime = System.nanoTime();
        System.out.println("Light update took " + (endTime - startTime) / 1000 + "us");
        
        ci.cancel();
    }

    @Unique
    private int calculateTargetLight(World world, int x, int y, int z) {
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
        maxNeighbor = Math.max(maxNeighbor, world.getBrightness(this.lightType, x - 1, y, z));
        maxNeighbor = Math.max(maxNeighbor, world.getBrightness(this.lightType, x + 1, y, z));
        maxNeighbor = Math.max(maxNeighbor, world.getBrightness(this.lightType, x, y - 1, z));
        maxNeighbor = Math.max(maxNeighbor, world.getBrightness(this.lightType, x, y + 1, z));
        maxNeighbor = Math.max(maxNeighbor, world.getBrightness(this.lightType, x, y, z - 1));
        maxNeighbor = Math.max(maxNeighbor, world.getBrightness(this.lightType, x, y, z + 1));

        return Math.max(baseLight, Math.max(0, maxNeighbor - opacity));
    }
}
