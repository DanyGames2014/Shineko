package net.danygames2014.shineko.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
//    @WrapOperation(method = "markDirty", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;dirty:Z", opcode = Opcodes.GETFIELD))
//    public boolean fixRaceCondition(ChunkBuilder chunkBuilder, Operation<Boolean> original) {
//        if (chunkBuilder == null) {
//            return true;
//        }
//        
//        return original.call(chunkBuilder);
//    }
//    
//    @WrapOperation(method = "compileChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;squaredDistanceTo(Lnet/minecraft/entity/Entity;)F", ordinal = 1))
//    public float fixRaceCondition2(ChunkBuilder chunkBuilder, Entity entity, Operation<Float> original) {
//        if (chunkBuilder == null) {
//            return 257.0F;
//        }
//        
//        return original.call(chunkBuilder, entity);
//    }
}
