package net.danygames2014.shineko.mixininterface;

import net.danygames2014.shineko.LightWrite;
import net.minecraft.world.chunk.light.LightUpdate;

import java.util.concurrent.LinkedTransferQueue;

public interface ShinekoWorld {
    LinkedTransferQueue<LightUpdate> shineko$getLiqhtUpdateQueue();

    LinkedTransferQueue<LightWrite> shineko$getCompletedWritesQueue();
}
