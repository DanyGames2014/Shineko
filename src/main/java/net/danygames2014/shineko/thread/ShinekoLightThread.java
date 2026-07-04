package net.danygames2014.shineko.thread;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightUpdate;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

public class ShinekoLightThread extends Thread {
    private final World world;
    private final LinkedTransferQueue<LightUpdate> queue;
    
    public ShinekoLightThread(String name, World world) {
        this.setName(name);
        this.world = world;
        if (world instanceof ShinekoWorld shinekoWorldO) {
            this.queue = shinekoWorldO.shineko$getLiqhtUpdateQueue();
        } else {
            this.queue = null;
        }
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
        
        while (!this.isInterrupted()) {
            try {
                // If the game is paused, wait a bit before checking the queue again
                if (world.pauseTicking) {
                    //noinspection BusyWait
                    Thread.sleep(100);
                    continue;
                }

                // Wait up to 500ms for a task. This prevents the thread from being stuck 
                LightUpdate firstUpdate = this.queue.poll(500, TimeUnit.MILLISECONDS);

                // If there's a task, update it and then process the queue'
                if (firstUpdate != null) {
                    // Add the first update to the batch buffer
                    batchBuffer.add(firstUpdate);
                    
                    // Draining the queue in batches is more efficient than polling repeatedly
                    this.queue.drainTo(batchBuffer, batchSize);
                    
                    // Execute the light updates
                    for (LightUpdate update : batchBuffer) {
                        update.updateLight(this.world);
                    }
                    
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
