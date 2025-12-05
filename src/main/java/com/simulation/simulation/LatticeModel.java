package com.simulation.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.aparapi.Range;

/**
 * LatticeModel.java
 * 
 * Implements the Lattice Boltzmann Method (LBM) using the D2Q9 model.
 * This class handles the core physics calculations for fluid dynamics.
 * 
 * Refactored for GPU acceleration using Aparapi.
 */
public class LatticeModel {

    // --- Simulation Constants ---
    private int width = 200;  // Grid width
    private int height = 100; // Grid height
    private static final int Q = 9;       // Number of discrete velocities (D2Q9)

    // LBM Weights for D2Q9
    private static final float[] WEIGHTS = {
        4.0f/9.0f, 
        1.0f/9.0f, 1.0f/9.0f, 1.0f/9.0f, 1.0f/9.0f, 
        1.0f/36.0f, 1.0f/36.0f, 1.0f/36.0f, 1.0f/36.0f
    };

    // LBM Velocity Vectors (e_i)
    private static final int[] EX = {0, 1, 0, -1, 0, 1, -1, -1, 1};
    private static final int[] EY = {0, 0, 1, 0, -1, 1, 1, -1, -1};

    // --- Simulation State (Flattened for GPU) ---
    
    // Distribution functions: f[ (y * width + x) * Q + i ]
    private float[] f;
    private float[] fNew;

    // Macroscopic variables: rho[ y * width + x ]
    private float[] rho; 
    private float[] ux;  
    private float[] uy;  

    // Obstacle mask
    private boolean[] obstacle;
    
    // GPU Kernel
    private LatticeKernel kernel;
    
    // Tracer Particles
    private List<Particle> particles;
    private Random random = new Random();

    // Relaxation time (tau)
    private float tau = 0.6f; 
    private float omega = 1.0f / tau; 
    
    // Inlet Velocity Vector
    private float inletVelocity = 0.1f;
    private float inletAngle = 0.0f; // Degrees
    private float inletUx = 0.1f;
    private float inletUy = 0.0f;
    
    private int currentStep = 0;

    public LatticeModel() {
        updateInletVector();
        initializeSimulation();
    }
    
    public synchronized void setResolution(int w, int h) {
        this.width = w;
        this.height = h;
        initializeSimulation();
    }
    
    public synchronized void reset() {
        initializeSimulation();
    }
    
    public void setViscosity(double tau) {
        this.tau = (float) tau;
        this.omega = 1.0f / this.tau;
        if (kernel != null) kernel.setOmega(this.omega);
    }
    
    public void setInletVelocity(double u) {
        this.inletVelocity = (float) u;
        updateInletVector();
    }
    
    public void setInletAngle(double degrees) {
        this.inletAngle = (float) degrees;
        updateInletVector();
    }
    
    private void updateInletVector() {
        double rad = Math.toRadians(inletAngle);
        this.inletUx = (float) (inletVelocity * Math.cos(rad));
        this.inletUy = (float) (inletVelocity * Math.sin(rad));
    }
    
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /**
     * Sets up initial conditions and obstacles.
     */
    private void initializeSimulation() {
        int size = width * height;
        currentStep = 0;
        
        // Initialize arrays
        f = new float[size * Q];
        fNew = new float[size * Q];
        rho = new float[size];
        ux = new float[size];
        uy = new float[size];
        obstacle = new boolean[size];

        // 1. Define Obstacles (e.g., a circle in the center)
        int cx = width / 4;
        int cy = height / 2;
        int radius = height / 10; 

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int idx = y * width + x;
                
                // Circle obstacle
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) {
                    obstacle[idx] = true;
                } else {
                    obstacle[idx] = false;
                }

                // Initial flow
                rho[idx] = 1.0f;
                ux[idx] = inletVelocity; 
                uy[idx] = 0.0f;

                // Set equilibrium distribution
                float[] eq = calculateEquilibrium(rho[idx], ux[idx], uy[idx]);
                for (int i = 0; i < Q; i++) {
                    f[idx * Q + i] = eq[i];
                    fNew[idx * Q + i] = eq[i]; // Init both
                }
            }
        }
        
        // Initialize Kernel
        kernel = new LatticeKernel(width, height, f, fNew, rho, ux, uy, obstacle);
        kernel.setOmega(omega);
        
        // Initialize Particles
        particles = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            particles.add(new Particle(random.nextDouble() * width, random.nextDouble() * height));
        }
    }

    // Forces acting on the obstacle (Not calculated on GPU yet)
    private double dragForce = 0;
    private double liftForce = 0;

    /**
     * Performs one time step of the simulation.
     */
    public synchronized void step() {
        currentStep++;
        
        // Soft Start: Ramp up velocity over first 600 steps to prevent shockwaves
        float ramp = 1.0f;
        if (currentStep < 600) {
            ramp = currentStep / 600.0f;
        }
        float currentInletUx = inletUx * ramp;
        float currentInletUy = inletUy * ramp;

        // 1. Execute GPU Kernel (Collision + Streaming)
        // We must use a safe local work group size (e.g., 128 or 256) to avoid driver crashes.
        // The global size must be a multiple of the local size.
        int globalSize = width * height;
        int localSize = 128; // Safe for most GPUs
        int paddedSize = ((globalSize + localSize - 1) / localSize) * localSize;
        
        kernel.execute(Range.create(paddedSize, localSize));
        
        // 2. Swap Arrays (Pointers only)
        float[] temp = f;
        f = fNew;
        fNew = temp;
        
        // Re-bind arrays to kernel for next step
        // Reuse the existing kernel instance to prevent memory leaks
        kernel.updateBuffers(f, fNew, rho, ux, uy, obstacle);
        kernel.setOmega(omega);

        // 3. Inlet Condition (Left Wall)
        // Force a specific velocity at x=0
        for (int y = 1; y < height - 1; y++) {
            int idx = y * width + 0;
            if (!obstacle[idx]) {
                float[] eq = calculateEquilibrium(1.0f, currentInletUx, currentInletUy); 
                for (int i = 0; i < Q; i++) {
                    f[idx * Q + i] = eq[i]; 
                }
            }
        }
        
        // 4. Outlet Condition (Right Wall) - Open Boundary (Zero Gradient)
        // Copy f from x=width-2 to x=width-1
        for (int y = 1; y < height - 1; y++) {
            int idxOut = y * width + (width - 1);
            int idxIn = y * width + (width - 2);
            
            if (!obstacle[idxOut]) {
                for (int i = 0; i < Q; i++) {
                    f[idxOut * Q + i] = f[idxIn * Q + i];
                }
                // Also update macroscopic for viz
                rho[idxOut] = rho[idxIn];
                ux[idxOut] = ux[idxIn];
                uy[idxOut] = uy[idxIn];
            }
        }
        
        // 5. Update Particles (CPU)
        updateParticles();
    }

    /**
     * Calculates the equilibrium distribution function f_eq.
     */
    private float[] calculateEquilibrium(float rho, float ux, float uy) {
        float[] eq = new float[Q];
        float u2 = ux * ux + uy * uy; 

        for (int i = 0; i < Q; i++) {
            float eu = EX[i] * ux + EY[i] * uy; 
            eq[i] = WEIGHTS[i] * rho * (1.0f + 3.0f * eu + 4.5f * eu * eu - 1.5f * u2);
        }
        return eq;
    }

    public synchronized double getCurl(int x, int y) {
        if (x < 1 || x >= width - 1 || y < 1 || y >= height - 1) return 0;
        
        // Curl = d(uy)/dx - d(ux)/dy
        // Central difference
        double duy_dx = (getVelocityY(x + 1, y) - getVelocityY(x - 1, y)) / 2.0;
        double dux_dy = (getVelocityX(x, y + 1) - getVelocityX(x, y - 1)) / 2.0;
        
        return duy_dx - dux_dy;
    }

    public synchronized double getVelocityX(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        return ux[y * width + x];
    }

    public synchronized double getVelocityY(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        return uy[y * width + x];
    }
    
    public synchronized double getDensity(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        return rho[y * width + x];
    }

    public boolean isObstacle(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return true;
        return obstacle[y * width + x];
    }

    public double getDragForce() {
        return dragForce;
    }

    public double getLiftForce() {
        return liftForce;
    }

    public void setObstacle(int x, int y, boolean isSolid) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        
        int idx = y * width + x;
        obstacle[idx] = isSolid;

        if (!isSolid) {
            rho[idx] = 1.0f;
            ux[idx] = 0.0f;
            uy[idx] = 0.0f;
            
            float[] eq = calculateEquilibrium(1.0f, 0.0f, 0.0f);
            for (int i = 0; i < Q; i++) {
                f[idx * Q + i] = eq[i];
                fNew[idx * Q + i] = eq[i];
            }
        }
        
        // Update kernel with new obstacle array
        // (Actually obstacle array ref is same, but content changed. Aparapi handles this.)
    }

    public List<Particle> getParticles() {
        return particles;
    }

    private void updateParticles() {
        for (Particle p : particles) {
            int gridX = (int) p.x;
            int gridY = (int) p.y;

            if (gridX >= 0 && gridX < width && gridY >= 0 && gridY < height) {
                int idx = gridY * width + gridX;
                float velX = ux[idx];
                float velY = uy[idx];
                
                p.x += velX * 2.0; 
                p.y += velY * 2.0;
            }

            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height || 
               (gridX >= 0 && gridX < width && gridY >= 0 && gridY < height && obstacle[gridY * width + gridX])) {
                p.x = 0; 
                p.y = random.nextDouble() * height;
            }
        }
    }
}
