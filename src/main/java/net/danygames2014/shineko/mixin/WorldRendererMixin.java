package net.danygames2014.shineko.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @WrapOperation(method = "markDirty", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;dirty:Z", opcode = Opcodes.GETFIELD))
    public boolean fixRaceCondition(ChunkBuilder chunkBuilder, Operation<Boolean> original) {
        if (chunkBuilder == null) {
            return true;
        }
        
        return original.call(chunkBuilder);
    }
    
    @WrapOperation(method = "compileChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;squaredDistanceTo(Lnet/minecraft/entity/Entity;)F", ordinal = 1))
    public float fixRaceCondition2(ChunkBuilder chunkBuilder, Entity entity, Operation<Float> original) {
        if (chunkBuilder == null) {
            return 257.0F;
        }
        
        return original.call(chunkBuilder, entity);
    }
}
