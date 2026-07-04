package net.danygames2014.shineko.mixin;

import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.NetherDimension;
import net.minecraft.world.dimension.OverworldDimension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chunk.class)
public class ChunkMixin {
    @Shadow
    public World world;

    @Shadow
    @Final
    public int x;

    @Shadow
    @Final
    public int z;

    @Inject(method = "populateBlockLight", at = @At(value = "HEAD"))
    public void populateLight(CallbackInfo ci) {
        if (this.world.dimension instanceof OverworldDimension) {
            return;
        }
        
        int minBlockX = this.x << 4;
        int minBlockZ = this.z << 4;
        int maxBlockX = minBlockX + 15;
        int maxBlockZ = minBlockZ + 15;

        this.world.queueLightUpdate(
                LightType.BLOCK,
                minBlockX,
                this.world.getBottomY(),
                minBlockZ,
                maxBlockX,
                this.world.getTopY(),
                maxBlockZ
        );
    }
}
