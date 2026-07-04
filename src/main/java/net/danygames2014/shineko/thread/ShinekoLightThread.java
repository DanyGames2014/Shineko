package net.danygames2014.shineko.thread;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.mixininterface.ShinekoChunk;
import net.danygames2014.shineko.mixininterface.ShinekoLightUpdate;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.light.LightUpdate;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

public class ShinekoLightThread extends Thread {
    private final World world;
    private final ShinekoWorld shinekoWorld;
    private final LinkedTransferQueue<LightUpdate> queue;

    public ShinekoLightThread(String name, World world) {
        this.setName(name);
        this.world = world;
        if (world instanceof ShinekoWorld shinekoWorldO) {
            this.shinekoWorld = shinekoWorldO;
            this.queue = shinekoWorldO.shineko$getLiqhtUpdateQueue();
        } else {
            this.shinekoWorld = null;
            this.queue = null;
        }
        this.setDaemon(true);
        this.setPriority(2);
    }

    @Override
    public synchronized void start() {
        if (world == null || queue == null) {
            Shineko.LOGGER.warn("World or queue is null! Thread " + this.getName() + " will not start.");
            return;
        }

        super.start();
        Shineko.LOGGER.info("Started thread " + this.getName());
    }

    @Override
    public void run() {
        if (world == null || queue == null) {
            return;
        }

        int batchSize = Shineko.CONFIG.lightThreadUpdateBatchSize;

        // Local array list reusable buffer to capture drained updates at high velocity
        ObjectArrayList<LightUpdate> batchBuffer = new ObjectArrayList<>(batchSize);

        // A list of chunks which has been visited during light updates
        LongOpenHashSet visitedChunks = new LongOpenHashSet(1024);

        while (!this.isInterrupted()) {
            try {
                // Wait up to 100ms for a task. This prevents the thread from being stuck 
                LightUpdate firstUpdate = this.queue.poll(100, TimeUnit.MILLISECONDS);

                // If there's a task, update it and then process the queue'
                if (firstUpdate != null) {
                    // Add the first update to the batch buffer
                    batchBuffer.add(firstUpdate);

                    // Draining the queue in batches is more efficient than polling repeatedly
                    this.queue.drainTo(batchBuffer, batchSize);

                    // Execute the light updates
                    for (LightUpdate update : batchBuffer) {
                        update.updateLight(this.world);
                        
                        // Retrieve the pre-packed bit keys directly from the LightUpdate mixin interface
                        LongOpenHashSet packedKeys = ((ShinekoLightUpdate) update).shineko$getVisitedChunks();

                        if (!packedKeys.isEmpty()) {
                            visitedChunks.addAll(packedKeys);
                        }
                    }

                    if (!visitedChunks.isEmpty()) {
                        java.util.concurrent.LinkedTransferQueue<Long> renderQueue = shinekoWorld.shineko$getVisitedChunks();
                        for (long packedKey : visitedChunks) {
                            renderQueue.offer(packedKey);
                        }
                    }
                    visitedChunks.clear();
                    
                    // Clear the tasks we have processed
                    batchBuffer.clear();

                    //noinspection BusyWait
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Shineko.LOGGER.error("Error in lighting thread " + this.getName(), e);
            }
        }

        Shineko.LOGGER.info("Stopped thread " + this.getName());
    }
}
