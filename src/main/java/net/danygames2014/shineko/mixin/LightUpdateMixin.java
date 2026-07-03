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
    private static final ThreadLocal<int[]> QUEUE_CACHE = ThreadLocal.withInitial(() -> new int[65536]);
    @Unique
    private static final ThreadLocal<int[]> REMOVE_QUEUE_CACHE = ThreadLocal.withInitial(() -> new int[65536]);
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

        int[] queue = QUEUE_CACHE.get();
        int[] removeQueue = REMOVE_QUEUE_CACHE.get();
        int[] removeVal = REMOVE_VAL_CACHE.get();

        int head = 0; int tail = 0;
        int rHead = 0; int rTail = 0;
        int maxCapacity = queue.length;

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
                        // Bit-pack coordinates relative to pMinX and pMinZ
                        int packedPos = ((x - pMinX) << 14) | (y << 6) | (z - pMinZ);

                        if (targetLight < currentLight) {
                            if (rTail < maxCapacity) {
                                removeQueue[rTail] = packedPos;
                                removeVal[rTail] = currentLight;
                                rTail++;
                            }
                            world.setLight(this.lightType, x, y, z, 0);
                        } else {
                            world.setLight(this.lightType, x, y, z, targetLight);
                            if (tail < maxCapacity) {
                                queue[tail++] = packedPos;
                            }
                        }
                    }
                }
            }
        }

        // 2. Depropagation Stage
        while (rHead < rTail) {
            int packed = removeQueue[rHead];
            int oldVal = removeVal[rHead++];

            // Unpack coordinates
            int cx = pMinX + (packed >> 14);
            int cy = (packed >> 6) & 0xFF;
            int cz = pMinZ + (packed & 0x3F);

            for (int i = 0; i < 6; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                int nz = cz + DZ[i];

                if (nx < pMinX || nx > pMaxX || nz < pMinZ || nz > pMaxZ || ny < bottomY || ny > topY) continue;

                int neighborLight = world.getBrightness(this.lightType, nx, ny, nz);
                int opacity = Block.BLOCKS_LIGHT_OPACITY[world.getBlockId(nx, ny, nz)];
                if (opacity <= 0) opacity = 1;

                int nPacked = ((nx - pMinX) << 14) | (ny << 6) | (nz - pMinZ);

                if (neighborLight != 0 && neighborLight == oldVal - opacity) {
                    if (rTail < maxCapacity) {
                        removeQueue[rTail] = nPacked;
                        removeVal[rTail] = neighborLight;
                        rTail++;
                    }
                    world.setLight(this.lightType, nx, ny, nz, 0);
                } else if (neighborLight >= oldVal) {
                    if (tail < maxCapacity) {
                        queue[tail++] = nPacked;
                    }
                }
            }
        }

        // 3. Propagation Stage
        while (head < tail) {
            int packed = queue[head++];
            int cx = pMinX + (packed >> 14);
            int cy = (packed >> 6) & 0xFF;
            int cz = pMinZ + (packed & 0x3F);

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
                        int nPacked = ((nx - pMinX) << 14) | (ny << 6) | (nz - pMinZ);
                        queue[tail++] = nPacked;
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
