package net.danygames2014.shineko.mixininterface;

import net.minecraft.world.chunk.light.LightUpdate;

import java.util.concurrent.LinkedTransferQueue;

public interface ShinekoWorld {
    LinkedTransferQueue<LightUpdate> shineko$getLiqhtUpdateQueue();
}
