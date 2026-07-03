package net.danygames2014.shineko.mixin;

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

    @Inject(method = "updateLight", at = @At(value = "HEAD"), cancellable = true)
    public void smileyFace(World world, CallbackInfo ci) {
        if (this.minY < world.getBottomY()) this.minY = world.getBottomY();
        if (this.maxY >= world.getTopY()) this.maxY = world.getTopY();

        int pMinX = this.minX - 15;
        int pMaxX = this.maxX + 15;
        int pMinZ = this.minZ - 15;
        int pMaxZ = this.maxZ + 15;

        // Fetch our pre-allocated reusable arrays from cache (Instant, 0ns overhead)
        int[] queue = QUEUE_CACHE.get();
        int[] removeQueue = REMOVE_QUEUE_CACHE.get();
        int[] removeVal = REMOVE_VAL_CACHE.get();

        int head = 0; int tail = 0;
        int rHead = 0; int rTail = 0;
        int maxCapacity = queue.length;

        // 1. Seed Stage
        for (int x = this.minX; x <= this.maxX; ++x) {
            for (int z = this.minZ; z <= this.maxZ; ++z) {
                if (!world.isRegionLoaded(x, 0, z, 1)) continue;

                // Micro-optimization: Cache the active chunk column lookup
                Chunk chunk = world.getChunk(x >> 4, z >> 4);
                if (chunk == null || chunk.isEmpty()) continue;

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

        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};

        // 2. Depropagation Stage
        while (rHead < rTail) {
            int packed = removeQueue[rHead];
            int oldVal = removeVal[rHead++];

            // Unpack coordinates
            int cx = pMinX + (packed >> 14);
            int cy = (packed >> 6) & 0xFF;
            int cz = pMinZ + (packed & 0x3F);

            for (int i = 0; i < 6; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                int nz = cz + dz[i];

                if (nx < pMinX || nx > pMaxX || nz < pMinZ || nz > pMaxZ || ny < 0 || ny > 127) continue;

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
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                int nz = cz + dz[i];

                if (nx < pMinX || nx > pMaxX || nz < pMinZ || nz > pMaxZ || ny < 0 || ny > 127) continue;

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
        
        ci.cancel();
    }

    // Helper method to look up expected lighting based on rules
    @Unique
    private int calculateTargetLight(World world, int x, int y, int z) {
        int blockId = world.getBlockId(x, y, z);
        int opacity = Block.BLOCKS_LIGHT_OPACITY[blockId];
        if (opacity <= 0) opacity = 1;

        int baseLight = 0;
        if (this.lightType == LightType.SKY) {
            if (world.isTopY(x, y, z)) return 15;
        } else {
            baseLight = Block.BLOCKS_LIGHT_LUMINANCE[blockId];
        }

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
