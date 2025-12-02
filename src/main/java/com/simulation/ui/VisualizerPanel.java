package com.simulation.ui;

import com.simulation.simulation.LatticeModel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.simulation.simulation.Particle;
import java.util.List;

/**
 * VisualizerPanel.java
 * 
 * A custom Swing component that renders the fluid simulation state.
 * It maps the velocity magnitude of the fluid to a color gradient (Heatmap).
 */
public class VisualizerPanel extends JPanel {

    private final LatticeModel model;
    private final int scale = 4; // Scale factor for rendering (1 grid node = 4x4 pixels)

    public VisualizerPanel(LatticeModel model) {
        this.model = model;
        updateSize();
        this.setBackground(Color.BLACK);

        // Mouse Interaction for drawing obstacles
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseInput(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseInput(e);
            }
        };
        
        this.addMouseListener(mouseHandler);
        this.addMouseMotionListener(mouseHandler);
    }
    
    public void updateSize() {
        this.setPreferredSize(new Dimension(model.getWidth() * scale, model.getHeight() * scale));
        this.revalidate();
    }

    private void handleMouseInput(MouseEvent e) {
        int gridX = e.getX() / scale;
        int gridY = e.getY() / scale;
        
        // Left click to draw, Right click to erase
        boolean isSolid = !SwingUtilities.isRightMouseButton(e);
        
        // Draw a small brush (3x3) for better usability
        for(int dx = -1; dx <= 1; dx++) {
            for(int dy = -1; dy <= 1; dy++) {
                model.setObstacle(gridX + dx, gridY + dy, isSolid);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Render the grid
        for (int x = 0; x < model.getWidth(); x++) {
            for (int y = 0; y < model.getHeight(); y++) {
                
                // Draw Obstacles
                if (model.isObstacle(x, y)) {
                    g.setColor(Color.GRAY);
                    g.fillRect(x * scale, y * scale, scale, scale);
                    continue;
                }

                // Calculate Velocity Magnitude
                double ux = model.getVelocityX(x, y);
                double uy = model.getVelocityY(x, y);
                double speed = Math.sqrt(ux * ux + uy * uy);

                // Map speed to color (Heatmap)
                // Assuming max speed is around 0.2 - 0.3 for stability
                Color color = getHeatMapColor(speed);
                g.setColor(color);
                g.fillRect(x * scale, y * scale, scale, scale);
            }
        }

        // Draw Particles (Streamlines)
        g.setColor(Color.WHITE);
        List<Particle> particles = model.getParticles();
        if (particles != null) {
            for (Particle p : particles) {
                int px = (int) (p.x * scale);
                int py = (int) (p.y * scale);
                g.fillOval(px, py, 2, 2);
            }
        }

        // Draw UI Overlay (Forces)
        g.setColor(new Color(0, 0, 0, 150)); // Semi-transparent black background
        g.fillRect(5, 5, 250, 65);
        
        g.setColor(Color.WHITE);
        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));
        g.drawString(String.format("Drag Force: %.5f", model.getDragForce()), 15, 25);
        g.drawString(String.format("Lift Force: %.5f", model.getLiftForce()), 15, 45);
        g.drawString("Left Click: Draw | Right Click: Erase", 15, 65);
    }

    /**
     * Maps a scalar value (speed) to a Color (Blue -> Green -> Red).
     */
    private Color getHeatMapColor(double value) {
        // Normalize value (clamp between 0 and max expected speed)
        double maxSpeed = 0.15; 
        double normalized = value / maxSpeed;
        if (normalized > 1.0) normalized = 1.0;
        if (normalized < 0.0) normalized = 0.0;

        // Simple Blue to Red gradient
        // 0.0 -> Blue (0, 0, 255)
        // 0.5 -> Green (0, 255, 0)
        // 1.0 -> Red (255, 0, 0)
        
        int r, g, b;

        if (normalized < 0.5) {
            // Blue to Green
            double ratio = normalized * 2.0;
            r = 0;
            g = (int) (255 * ratio);
            b = (int) (255 * (1 - ratio));
        } else {
            // Green to Red
            double ratio = (normalized - 0.5) * 2.0;
            r = (int) (255 * ratio);
            g = (int) (255 * (1 - ratio));
            b = 0;
        }

        return new Color(r, g, b);
    }
}
