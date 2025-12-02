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
    private double[][][] f;
    private double[][][] fNew;

    // Macroscopic variables
    private double[][] rho; // Density
    private double[][] ux;  // Velocity X
    private double[][] uy;  // Velocity Y

    // Obstacle mask (true if solid)
    private boolean[][] obstacle;
    
    // Tracer Particles
    private List<Particle> particles;
    private Random random = new Random();

    // Relaxation time (tau). Related to viscosity.
    // Kinematic viscosity nu = (tau - 0.5) / 3
    // Stability requires tau > 0.5
    private double tau = 0.6; 
    private double omega = 1.0 / tau; // Relaxation frequency
    
    // Inlet Velocity
    private double inletVelocity = 0.1;

    public LatticeModel() {
        initializeSimulation();
    }
    
    public void setResolution(int w, int h) {
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
    
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /**
     * Sets up initial conditions and obstacles.
     */
    private void initializeSimulation() {
        // Initialize arrays
        f = new double[width][height][Q];
        fNew = new double[width][height][Q];
        rho = new double[width][height];
        ux = new double[width][height];
        uy = new double[width][height];
        obstacle = new boolean[width][height];

        // 1. Define Obstacles (e.g., a circle in the center)
        int cx = width / 4;
        int cy = height / 2;
        int radius = height / 10; // Dynamic radius

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Circle obstacle
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) {
                    obstacle[x][y] = true;
                } else {
                    obstacle[x][y] = false;
                }

                // Initial flow: slightly moving to the right
                rho[x][y] = 1.0;
                ux[x][y] = inletVelocity; // Initial velocity
                uy[x][y] = 0.0;

                // Set equilibrium distribution
                double[] eq = calculateEquilibrium(rho[x][y], ux[x][y], uy[x][y]);
                for (int i = 0; i < Q; i++) {
                    f[x][y][i] = eq[i];
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
    public void step() {
        // Update Particles
        updateParticles();

        // Reset forces for this step
        double stepDrag = 0;
        double stepLift = 0;

        // 1. Collision Step (BGK)
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (obstacle[x][y]) continue; // Skip collision inside solids

                // Calculate macroscopic variables (rho, u) from f
                double density = 0;
                double velX = 0;
                double velY = 0;

                for (int i = 0; i < Q; i++) {
                    density += f[x][y][i];
                    velX += f[x][y][i] * EX[i];
                    velY += f[x][y][i] * EY[i];
                }

                if (density > 0) {
                    velX /= density;
                    velY /= density;
                }

                // Store for visualization
                rho[x][y] = density;
                ux[x][y] = velX;
                uy[x][y] = velY;

                // Compute Equilibrium
                double[] feq = calculateEquilibrium(density, velX, velY);

                // Relax towards equilibrium
                for (int i = 0; i < Q; i++) {
                    f[x][y][i] = (1.0 - omega) * f[x][y][i] + omega * feq[i];
                }
            }
        }

        // 2. Streaming Step
        // Move particles to neighboring nodes
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int i = 0; i < Q; i++) {
                    // Target coordinates
                    int nextX = x + EX[i];
                    int nextY = y + EY[i];

                    // Periodic Boundary Conditions (Wrap around)
                    // Or simple bounce/outflow. Here we use periodic for simplicity or simple bounds.
                    // Let's use Periodic for X, Bounce for Y (Top/Bottom walls)
                    
                    if (nextX < 0) nextX = width - 1;
                    if (nextX >= width) nextX = 0;

                    if (nextY < 0 || nextY >= height) {
                        // Wall bounce-back logic handled implicitly or explicitly?
                        // For simple streaming, if out of bounds, we might just reflect.
                        // But standard streaming writes to fNew.
                        // If out of bounds Y, we treat it as a bounce back on the *next* step or here.
                        // Simplest: Bounce back immediately if hitting top/bottom wall
                        // But let's stick to standard streaming and handle boundaries after.
                        // Actually, for a tunnel, Top/Bottom are solid walls.
                        continue; 
                    }

                    fNew[nextX][nextY][i] = f[x][y][i];
                }
            }
        }

        // 3. Boundary Conditions & Obstacles
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Handle internal obstacles (Bounce-back)
                if (obstacle[x][y]) {
                    for (int i = 1; i < Q; i++) {
                        // Reflect incoming particles back to where they came from
                        // The particle that *would have* arrived here at direction i
                        // is bounced back to direction OPPOSITE[i] at the source node.
                        // Standard way: fNew at the boundary node takes the value of the opposite direction
                        // from the streaming step.
                        // Simplified: Just swap directions in place? No, we have fNew.
                        
                        // Better approach for obstacles in loop:
                        // If a node is an obstacle, it reflects particles.
                        // We can implement this by post-processing fNew or modifying streaming.
                        // Let's use a simple rule: 
                        // f_new(x, y, i) = f(x - ex[i], y - ey[i], i) usually.
                        // If (x,y) is solid, we don't update it.
                        // Instead, at the fluid neighbor, the particle coming from solid is bounced?
                        
                        // Let's use the standard "Wet Node" or simple bounce-back:
                        // If we streamed into an obstacle, we reverse.
                        // Actually, let's do it simply:
                        // Iterate all nodes. If solid, reflect all f's.
                        // But we already streamed.
                    }
                }
                
                // Top/Bottom Walls (Bounce back)
                if (y == 0 || y == height - 1) {
                     obstacle[x][y] = true; // Treat as obstacle
                }
            }
        }
        
        // Apply Bounce-Back for all solid nodes (including walls)
        // This overrides the streaming for solid nodes
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (obstacle[x][y]) {
                    for (int i = 1; i < Q; i++) {
                        // The particle that was streaming *into* this obstacle node 'i'
                        // should be bounced back to the neighbor 'opposite[i]'.
                        // However, standard simple bounce back:
                        // f_out(x_fluid, opposite) = f_in(x_fluid, i)
                        
                        // Let's use a robust method:
                        // During streaming, if target is obstacle, write to source with opposite direction.
                        // Since we already did full streaming into fNew, we need to correct it.
                        // Actually, let's rewrite streaming to handle obstacles directly.
                    }
                }
            }
        }
        
        // RE-IMPLEMENTING STREAMING WITH BOUNCE-BACK INTEGRATED
        // Reset fNew to 0 or handle carefully
        // It's cleaner to do it in one pass.
        
        for(int x=0; x<width; x++) {
            for(int y=0; y<height; y++) {
                if(obstacle[x][y]) continue; // Don't stream *from* obstacles (they have no fluid)

                for(int i=0; i<Q; i++) {
                    int nextX = x + EX[i];
                    int nextY = y + EY[i];

                    // Periodic X
                    if (nextX < 0) nextX = width - 1;
                    if (nextX >= width) nextX = 0;

                    // Check Y bounds (Walls)
                    if (nextY < 0 || nextY >= height) {
                        // Wall bounce: particle stays at (x,y) but reverses direction
                        fNew[x][y][OPPOSITE[i]] = f[x][y][i];
                    } 
                    // Check Internal Obstacle
                    else if (obstacle[nextX][nextY]) {
                        // Obstacle bounce: particle stays at (x,y) but reverses direction
                        fNew[x][y][OPPOSITE[i]] = f[x][y][i];
                        
                        // Calculate Momentum Exchange (Force)
                        // Force = 2 * mass * velocity_component
                        stepDrag += 2.0 * f[x][y][i] * EX[i];
                        stepLift += 2.0 * f[x][y][i] * EY[i];
                    } 
                    else {
                        // Normal propagation
                        fNew[nextX][nextY][i] = f[x][y][i];
                    }
                }
            }
        }
        
        // Update public force variables (simple moving average could be added here for stability)
        this.dragForce = stepDrag;
        this.liftForce = stepLift;
        
        // Inlet Condition (Left side drive)
        // Force a specific velocity at x=0
        for (int y = 1; y < height - 1; y++) {
            if (!obstacle[0][y]) {
                double[] eq = calculateEquilibrium(1.0, inletVelocity, 0.0); // Constant inflow u=0.1
                for (int i = 0; i < Q; i++) {
                    fNew[0][y][i] = eq[i];
                }
            }
        }

        // Swap arrays
        double[][][] temp = f;
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
        return ux[x][y];
    }

    public double getVelocityY(int x, int y) {
        return uy[x][y];
    }
    
    public double getDensity(int x, int y) {
        return rho[x][y];
    }

    public boolean isObstacle(int x, int y) {
        return obstacle[x][y];
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
    public void setObstacle(int x, int y, boolean isSolid) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        
        obstacle[x][y] = isSolid;

        if (!isSolid) {
            // If turning a solid into fluid, initialize it to equilibrium
            // to prevent instability (zero density/velocity).
            rho[x][y] = 1.0;
            ux[x][y] = 0.0;
            uy[x][y] = 0.0;
            
            double[] eq = calculateEquilibrium(1.0, 0.0, 0.0);
            for (int i = 0; i < Q; i++) {
                f[x][y][i] = eq[i];
                fNew[x][y][i] = eq[i];
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
                double velX = ux[gridX][gridY];
                double velY = uy[gridX][gridY];
                
                p.x += velX * 2.0; // Scale velocity for visual effect
                p.y += velY * 2.0;
            }

            // Respawn if out of bounds or stuck in obstacle
            if (p.x < 0 || p.x >= width || p.y < 0 || p.y >= height || (gridX >= 0 && gridX < width && gridY >= 0 && gridY < height && obstacle[gridX][gridY])) {
                p.x = 0; // Respawn at inlet
                p.y = random.nextDouble() * height;
            }
        }
    }
}
