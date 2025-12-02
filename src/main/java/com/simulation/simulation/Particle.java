package com.simulation.simulation;

/**
 * Particle.java
 * 
 * Represents a massless tracer particle that follows the fluid flow.
 * Used for visualizing streamlines.
 */
public class Particle {
    public double x;
    public double y;

    public Particle(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
