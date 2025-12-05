package com.simulation.simulation;

import com.simulation.ui.VisualizerPanel;

/**
 * Engine.java
 * 
 * Manages the simulation loop. It triggers the physics updates in the LatticeModel
 * and requests the UI to repaint.
 */
public class Engine implements Runnable {

    private final LatticeModel model;
    private final VisualizerPanel view;
    private boolean running = false;
    private boolean paused = false;
    private Thread thread;
    
    // Target FPS
    private final int FPS = 60;
    private final long TARGET_TIME = 1000 / FPS;
    
    private int stepsPerFrame = 2;

    public Engine(LatticeModel model, VisualizerPanel view) {
        this.model = model;
        this.view = view;
    }
    
    public void setStepsPerFrame(int steps) {
        this.stepsPerFrame = steps;
    }
    
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        long start;
        long elapsed;
        long wait;

        while (running) {
            start = System.nanoTime();

            // 1. Update Physics
            if (!paused) {
                // We can perform multiple physics steps per frame for faster simulation
                for (int i = 0; i < stepsPerFrame; i++) {
                    model.step();
                }
            }

            // 2. Render
            view.repaint();

            // 3. Cap Frame Rate
            elapsed = System.nanoTime() - start;
            wait = TARGET_TIME - (elapsed / 1000000);

            if (wait < 0) wait = 5;

            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
