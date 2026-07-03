package net.danygames2014.shineko.thread;

import net.danygames2014.shineko.Shineko;
import net.danygames2014.shineko.mixininterface.ShinekoWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightUpdate;

import java.util.ArrayList;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

public class ShinekoLightThread extends Thread {
    private final World world;
    private final LinkedTransferQueue<LightUpdate> queue;
    private volatile boolean run;
    
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
        run = true;
        super.start();
        Shineko.LOGGER.info("Started thread " + this.getName());
    }
    
    public synchronized void stopThread() {
        this.run = false;
    }

    @Override
    public void run() {
        // Fast-path safety escape
        if (world == null || queue == null) {
            run = false;
            return;
        }

        // Local array list reusable buffer to capture drained updates at high velocity
        ArrayList<LightUpdate> batchBuffer = new ArrayList<>(256);

        while (run && !Thread.currentThread().isInterrupted()) {
            // If the game is paused, don't crash or exit—just poll slowly and wait
            if (world.pauseTicking) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            try {
                // 1. Wait up to 500ms for a task. This prevents the thread from being stuck 
                // permanently in .take() if the server/world is closing down.
                LightUpdate firstUpdate = this.queue.poll(500, TimeUnit.MILLISECONDS);

                if (firstUpdate != null) {
                    batchBuffer.add(firstUpdate);

                    // 2. Instantly grab any other backlogged light updates waiting in the pipeline
                    this.queue.drainTo(batchBuffer);

                    // 3. Process the entire batch contiguously
                    int batchSize = batchBuffer.size();
                    for (int i = 0; i < batchSize; i++) {
                        LightUpdate update = batchBuffer.get(i);

                        // Execute the update logic. Because your Mixin targets the `updateLight`
                        // method inside LightUpdate, calling it directly triggers your optimized code!
                        update.updateLight(this.world);
                    }

                    // 4. Clear the local storage references for the next cycle
                    batchBuffer.clear();
                }

            } catch (InterruptedException e) {
                // Clean exit on thread interruption request
                break;
            }
        }

        Shineko.LOGGER.info("Stopped thread " + this.getName());
    }
}
