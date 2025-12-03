package com.simulation.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LatticeModel.java
 * 
 * Implements the Lattice Boltzmann Method (LBM) using the D2Q9 model.
 * This class handles the core physics calculations for fluid dynamics.
 * 
 * D2Q9 Model:
 * - 2 Dimensions
 * - 9 Discrete velocity vectors per lattice node
 * 
 * The simulation proceeds in two main steps per time-step:
 * 1. Collision: Relaxation towards local equilibrium (BGK approximation).
 * 2. Streaming: Advection of particles to neighboring nodes.
 */
public class LatticeModel {

    // --- Simulation Constants ---
    private int width = 200;  // Grid width
    private int height = 100; // Grid height
    private static final int Q = 9;       // Number of discrete velocities (D2Q9)

    // LBM Weights for D2Q9
    // 4/9 for center, 1/9 for axis-aligned, 1/36 for diagonals
    private static final double[] WEIGHTS = {
        4.0/9.0, 
        1.0/9.0, 1.0/9.0, 1.0/9.0, 1.0/9.0, 
        1.0/36.0, 1.0/36.0, 1.0/36.0, 1.0/36.0
    };

    // LBM Velocity Vectors (e_i)
    // 0: (0,0)
    // 1-4: (1,0), (0,1), (-1,0), (0,-1)
    // 5-8: (1,1), (-1,1), (-1,-1), (1,-1)
    private static final int[] EX = {0, 1, 0, -1, 0, 1, -1, -1, 1};
    private static final int[] EY = {0, 0, 1, 0, -1, 1, 1, -1, -1};

    // Opposite directions for bounce-back (indices)
    private static final int[] OPPOSITE = {0, 3, 4, 1, 2, 7, 8, 5, 6};

    // --- Simulation State ---
    
    // Distribution functions: f[x][y][i]
    // We use two arrays to handle the streaming step (read from one, write to other)
    // Flattened arrays for performance: f[x * height * Q + y * Q + i]
    private double[] f;
    private double[] fNew;

    // Macroscopic variables
    private double[] rho; // Density
    private double[] ux;  // Velocity X
    private double[] uy;  // Velocity Y

    // Obstacle mask (true if solid)
    private boolean[] obstacle;
    
    // Tracer Particles
    private List<Particle> particles;
    private Random random = new Random();

    // Relaxation time (tau). Related to viscosity.
    // Kinematic viscosity nu = (tau - 0.5) / 3
    // Stability requires tau > 0.5
    private double tau = 0.6; 
    private double omega = 1.0 / tau; // Relaxation frequency
    
    // Smagorinsky Constant for LES
    private double smagorinskyConstant = 0.15;
    
    // Inlet Velocity
    private double inletVelocity = 0.1;
    
    // Debugging
    private boolean debugMode = false;

    public LatticeModel() {
        initializeSimulation();
    }
    
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        System.out.println("[LatticeModel] Debug Mode: " + debug);
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    public synchronized void setResolution(int w, int h) {
        this.width = w;
        this.height = h;
        initializeSimulation();
    }
    
    public void setViscosity(double tau) {
        this.tau = tau;
        this.omega = 1.0 / tau;
    }
    
    public void setInletVelocity(double u) {
        this.inletVelocity = u;
    }
    
    public void setSmagorinskyConstant(double c) {
        this.smagorinskyConstant = c;
    }
    
    public double getSmagorinskyConstant() {
        return smagorinskyConstant;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    
    private int getIndex(int x, int y) {
        return x * height + y;
    }
    
    private int getFIndex(int x, int y, int i) {
        return (x * height + y) * Q + i;
    }

    /**
     * Sets up initial conditions and obstacles.
     */
    private void initializeSimulation() {
        // Initialize arrays
        int size = width * height;
        f = new double[size * Q];
        fNew = new double[size * Q];
        rho = new double[size];
        ux = new double[size];
        uy = new double[size];
        obstacle = new boolean[size];

        // 1. Define Obstacles (e.g., a circle in the center)
        int cx = width / 4;
        int cy = height / 2;
        int radius = height / 10; // Dynamic radius

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int idx = getIndex(x, y);
                
                // Top and Bottom Walls
                if (y == 0 || y == height - 1) {
                    obstacle[idx] = true;
                }
                // Circle obstacle
                else if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) {
                    obstacle[idx] = true;
                } else {
                    obstacle[idx] = false;
                }

                // Initial flow: slightly moving to the right
                rho[idx] = 1.0;
                ux[idx] = inletVelocity; // Initial velocity
                uy[idx] = 0.0;

                // Set equilibrium distribution
                double[] eq = calculateEquilibrium(rho[idx], ux[idx], uy[idx]);
                for (int i = 0; i < Q; i++) {
                    f[getFIndex(x, y, i)] = eq[i];
                }
            }
        }
        
        // Initialize Particles
        particles = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            particles.add(new Particle(random.nextDouble() * width, random.nextDouble() * height));
        }
    }

    // Forces acting on the obstacle
    private double dragForce = 0;
    private double liftForce = 0;

    /**
     * Performs one time step of the simulation.
     */
    public synchronized void step() {
        // Update Particles
        updateParticles();

        // Reset forces for this step
        double stepDrag = 0;
        double stepLift = 0;

        // 1. Collision Step (BGK)
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int idx = getIndex(x, y);
                if (obstacle[idx]) continue; // Skip collision inside solids

                // Calculate macroscopic variables (rho, u) from f
                double density = 0;
                double velX = 0;
                double velY = 0;

                for (int i = 0; i < Q; i++) {
                    int fIdx = getFIndex(x, y, i);
                    density += f[fIdx];
                    velX += f[fIdx] * EX[i];
                    velY += f[fIdx] * EY[i];
                }

                if (density > 0) {
                    velX /= density;
                    velY /= density;
                }

                // Store for visualization
                rho[idx] = density;
                ux[idx] = velX;
                uy[idx] = velY;

                // Compute Equilibrium
                double[] feq = calculateEquilibrium(density, velX, velY);

                // --- LES Implementation (Smagorinsky Model) ---
                double omegaEff = omega; // Default to base relaxation
                
                if (density > 0.000001) { // Avoid division by zero
                     // Calculate non-equilibrium stress tensor moments
                    double Qxx = 0, Qxy = 0, Qyy = 0;
                    for (int i = 0; i < Q; i++) {
                        int fIdx = getFIndex(x, y, i);
                        double fNeq = f[fIdx] - feq[i];
                        Qxx += EX[i] * EX[i] * fNeq;
                        Qyy += EY[i] * EY[i] * fNeq;
                        Qxy += EX[i] * EY[i] * fNeq;
                    }
                    
                    // Magnitude of the strain rate tensor (related to Q)
                    // Q_mag = sqrt(2 * sum(Q_ab * Q_ab))
                    double Q_mag = Math.sqrt(2.0 * (Qxx * Qxx + Qyy * Qyy + 2.0 * Qxy * Qxy));
                    
                    // Calculate effective relaxation time
                    // tau_eff = 0.5 * (tau + sqrt(tau^2 + 18 * (Cs * Delta)^2 * Q_mag / rho))
                    double tauEff = 0.5 * (tau + Math.sqrt(tau * tau + 18.0 * smagorinskyConstant * smagorinskyConstant * Q_mag / density));
                    omegaEff = 1.0 / tauEff;
                }

                // Relax towards equilibrium
                for (int i = 0; i < Q; i++) {
                    int fIdx = getFIndex(x, y, i);
                    f[fIdx] = (1.0 - omegaEff) * f[fIdx] + omegaEff * feq[i];
                }
            }
        }

        // 2. Streaming Step (with Bounce-Back)
        // We iterate over all nodes. If a node is fluid, we stream its particles to neighbors.
        // If a neighbor is an obstacle, the particle bounces back to the source.
        
        for(int x=0; x<width; x++) {
            for(int y=0; y<height; y++) {
                int idx = getIndex(x, y);
                if(obstacle[idx]) continue; // Don't stream *from* obstacles (they have no fluid)

                for(int i=0; i<Q; i++) {
                    int nextX = x + EX[i];
                    int nextY = y + EY[i];

                    // Periodic X (Inlet/Outlet handling)
                    // We want Open Boundary at Outlet (Right) and Fixed Inlet (Left)
                    // But the streaming loop handles movement.
                    
                    // If nextX >= width (Outlet), we extrapolate or absorb.
                    // Simple Zero-Gradient: Copy from neighbor (width-1)
                    if (nextX >= width) {
                        // Particle leaves the domain.
                        // We don't wrap around.
                        continue; 
                    }
                    
                    // If nextX < 0 (Inlet), we don't stream *from* outside.
                    // The inlet condition below will overwrite fNew[0][y].
                    if (nextX < 0) {
                        continue;
                    }

                    // Check Y bounds (Walls)
                    if (nextY < 0 || nextY >= height) {
                        // Wall bounce: particle stays at (x,y) but reverses direction
                        fNew[getFIndex(x, y, OPPOSITE[i])] = f[getFIndex(x, y, i)];
                    } 
                    // Check Internal Obstacle
                    else if (obstacle[getIndex(nextX, nextY)]) {
                        // Obstacle bounce: particle stays at (x,y) but reverses direction
                        fNew[getFIndex(x, y, OPPOSITE[i])] = f[getFIndex(x, y, i)];
                        
                        // Calculate Momentum Exchange (Force)
                        // Force = 2 * mass * velocity_component
                        stepDrag += 2.0 * f[getFIndex(x, y, i)] * EX[i];
                        stepLift += 2.0 * f[getFIndex(x, y, i)] * EY[i];
                    } 
                    else {
                        // Normal propagation
                        fNew[getFIndex(nextX, nextY, i)] = f[getFIndex(x, y, i)];
                    }
                }
            }
        }
        
        // Update public force variables (simple moving average could be added here for stability)
        this.dragForce = stepDrag;
        this.liftForce = stepLift;
        
        // Inlet Condition (Left side drive)
        // Use Parabolic Profile to avoid shear instability at walls
        // u(y) = 4 * U_max * (y/H) * (1 - y/H)
        for (int y = 1; y < height - 1; y++) {
            int idx = getIndex(0, y);
            if (!obstacle[idx]) {
                double normalizedY = (double) y / (height - 1);
                double profileFactor = 4.0 * normalizedY * (1.0 - normalizedY);
                double u = inletVelocity * profileFactor;
                
                double[] eq = calculateEquilibrium(1.0, u, 0.0); 
                for (int i = 0; i < Q; i++) {
                    fNew[getFIndex(0, y, i)] = eq[i];
                }
            }
        }
        
        // Outlet Condition (Right side open)
        // Zero-gradient: f[width-1] = f[width-2]
        for (int y = 1; y < height - 1; y++) {
            int idx = getIndex(width-1, y);
            if (!obstacle[idx]) {
                for (int i = 0; i < Q; i++) {
                    fNew[getFIndex(width-1, y, i)] = fNew[getFIndex(width-2, y, i)];
                }
            }
        }

        // Swap arrays
        double[] temp = f;
        f = fNew;
        fNew = temp;
    }

    /**
     * Calculates the equilibrium distribution function f_eq.
     * Formula: w_i * rho * [1 + 3(e_i . u) + 4.5(e_i . u)^2 - 1.5(u . u)]
     */
    private double[] calculateEquilibrium(double rho, double ux, double uy) {
        double[] eq = new double[Q];
        double u2 = ux * ux + uy * uy; // u dot u

        for (int i = 0; i < Q; i++) {
            double eu = EX[i] * ux + EY[i] * uy; // e_i dot u
            eq[i] = WEIGHTS[i] * rho * (1.0 + 3.0 * eu + 4.5 * eu * eu - 1.5 * u2);
        }
        return eq;
    }

    // --- Getters for Visualization ---

    public double getVelocityX(int x, int y) {
        return ux[getIndex(x, y)];
    }

    public double getVelocityY(int x, int y) {
        return uy[getIndex(x, y)];
    }
    
    public double getDensity(int x, int y) {
        return rho[getIndex(x, y)];
    }

    public boolean isObstacle(int x, int y) {
        return obstacle[getIndex(x, y)];
    }

    public double getDragForce() {
        return dragForce;
    }

    public double getLiftForce() {
        return liftForce;
    }

    /**
     * Dynamically updates the obstacle state of a node.
     * If removing an obstacle, we must re-initialize the fluid at that node.
     */
    public synchronized void setObstacle(int x, int y, boolean isSolid) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        
        int idx = getIndex(x, y);
        obstacle[idx] = isSolid;

        if (!isSolid) {
            // If turning a solid into fluid, initialize it to equilibrium
            // to prevent instability (zero density/velocity).
            rho[idx] = 1.0;
            ux[idx] = 0.0;
            uy[idx] = 0.0;
            
            double[] eq = calculateEquilibrium(1.0, 0.0, 0.0);
            for (int i = 0; i < Q; i++) {
                f[getFIndex(x, y, i)] = eq[i];
                fNew[getFIndex(x, y, i)] = eq[i];
            }
        }
    }

    public List<Particle> getParticles() {
        return particles;
    }

    private void updateParticles() {
        for (Particle p : particles) {
            int gridX = (int) p.x;
            int gridY = (int) p.y;

            // Check bounds
            if (gridX >= 0 && gridX < width && gridY >= 0 && gridY < height) {
                // Advect particle
                int idx = getIndex(gridX, gridY);
                double velX = ux[idx];
                double velY = uy[idx];
                
                p.x += velX * 2.0; // Scale velocity for visual effect
                p.y += velY * 2.0;
            }

            // Respawn if out of bounds or stuck in obstacle
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height || (gridX >= 0 && gridX < width && gridY >= 0 && gridY < height && obstacle[getIndex(gridX, gridY)])) {
                p.x = 0; // Respawn at inlet
                p.y = random.nextDouble() * height;
            }
        }
    }
    
    /**
     * Checks the simulation field for NaN or Infinite values.
     * Returns true if instability is detected.
     */
    public boolean checkStability() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int idx = getIndex(x, y);
                if (Double.isNaN(rho[idx]) || Double.isInfinite(rho[idx])) {
                    if (debugMode) {
                        System.err.printf("[Stability] Instability detected at (%d, %d). Density: %f%n", x, y, rho[idx]);
                    }
                    return true;
                }
                if (Double.isNaN(ux[idx]) || Double.isInfinite(ux[idx]) || 
                    Double.isNaN(uy[idx]) || Double.isInfinite(uy[idx])) {
                    if (debugMode) {
                        System.err.printf("[Stability] Velocity instability at (%d, %d). u: (%f, %f)%n", x, y, ux[idx], uy[idx]);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Prints detailed state of a specific node for debugging.
     */
    public void printNodeInfo(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            System.out.println("[Debug] Node out of bounds: " + x + ", " + y);
            return;
        }
        
        int idx = getIndex(x, y);
        System.out.println("--- Node Info (" + x + ", " + y + ") ---");
        System.out.printf("Density (rho): %.6f%n", rho[idx]);
        System.out.printf("Velocity (u): (%.6f, %.6f)%n", ux[idx], uy[idx]);
        System.out.printf("Obstacle: %b%n", obstacle[idx]);
        System.out.println("Distribution Functions (f):");
        for (int i = 0; i < Q; i++) {
            System.out.printf("  f[%d]: %.6f%n", i, f[getFIndex(x, y, i)]);
        }
        System.out.println("-----------------------------");
    }
}
