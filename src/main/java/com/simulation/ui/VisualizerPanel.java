package com.simulation.ui;

import com.simulation.simulation.LatticeModel;
import com.simulation.simulation.Particle;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * VisualizerPanel.java
 * 
 * A custom Swing component that renders the fluid simulation state.
 * It maps the velocity magnitude of the fluid to a color gradient (Heatmap).
 */
public class VisualizerPanel extends JPanel {

    private final LatticeModel model;
    private final int scale = 4; // Scale factor for rendering (1 grid node = 4x4 pixels)
    private BufferedImage image;
    private int[] pixels;
    
    public enum ViewMode {
        HEATMAP,
        VECTORS,
        PARTICLES,
        DENSITY
    }
    
    private ViewMode currentViewMode = ViewMode.HEATMAP;

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
        int w = model.getWidth();
        int h = model.getHeight();
        this.setPreferredSize(new Dimension(w * scale, h * scale));
        
        // Create buffered image for fast rendering
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        
        this.revalidate();
    }

    private void handleMouseInput(MouseEvent e) {
        int gridX = e.getX() / scale;
        int gridY = e.getY() / scale;
        
        // Debug Inspection
        if (model.isDebugMode() && e.getID() == MouseEvent.MOUSE_PRESSED) {
            model.printNodeInfo(gridX, gridY);
        }
        
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

        // Render based on View Mode
        if (currentViewMode == ViewMode.HEATMAP || currentViewMode == ViewMode.DENSITY) {
            renderScalarField(g);
        } else if (currentViewMode == ViewMode.VECTORS) {
            renderVectors(g);
        } else {
            renderObstaclesOnly(g);
        }

        // Draw Particles (Streamlines) - Always draw for Heatmap/Particles modes, maybe optional for others
        // User asked for "Pure Particles", so we should draw them there.
        // Let's draw particles in all modes except maybe Vectors if it's too cluttered, 
        // but "Streamlines" are usually good.
        // Actually, "Pure Particles" implies we ONLY see particles (and obstacles).
        
        if (currentViewMode != ViewMode.VECTORS) {
             g.setColor(Color.WHITE);
             List<Particle> particles = model.getParticles();
             if (particles != null) {
                 for (Particle p : particles) {
                     int px = (int) (p.x * scale);
                     int py = (int) (p.y * scale);
                     g.fillOval(px, py, 2, 2);
                 }
             }
        }

        // Draw UI Overlay (Forces)
        g.setColor(new Color(0, 0, 0, 150)); // Semi-transparent black background
        g.fillRect(5, 5, 250, 85); // Increased height for mode info
        
        g.setColor(Color.WHITE);
        g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));
        g.drawString(String.format("Drag Force: %.5f", model.getDragForce()), 15, 25);
        g.drawString(String.format("Lift Force: %.5f", model.getLiftForce()), 15, 45);
        g.drawString("Mode: " + currentViewMode, 15, 65);
        g.drawString("Left Click: Draw | Right Click: Erase", 15, 85);
    }

    private void renderScalarField(Graphics g) {
        int w = model.getWidth();
        int h = model.getHeight();
        
        // Update pixel array directly
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int idx = y * w + x; // BufferedImage is row-major (y * width + x)
                
                if (model.isObstacle(x, y)) {
                    pixels[idx] = 0xFF808080; // Gray
                    continue;
                }

                int colorInt;
                if (currentViewMode == ViewMode.DENSITY) {
                    double rho = model.getDensity(x, y);
                    double normalized = (rho - 0.98) / 0.04; 
                    colorInt = getHeatMapColorInt(normalized * 0.15);
                } else {
                    double ux = model.getVelocityX(x, y);
                    double uy = model.getVelocityY(x, y);
                    double speed = Math.sqrt(ux * ux + uy * uy);
                    colorInt = getHeatMapColorInt(speed);
                }
                
                pixels[idx] = colorInt;
            }
        }
        
        // Draw the image scaled up
        g.drawImage(image, 0, 0, w * scale, h * scale, null);
    }
    
    private int getHeatMapColorInt(double value) {
        double maxSpeed = 0.15; 
        double normalized = value / maxSpeed;
        if (normalized > 1.0) normalized = 1.0;
        if (normalized < 0.0) normalized = 0.0;

        int r, g, b;

        if (normalized < 0.5) {
            double ratio = normalized * 2.0;
            r = 0;
            g = (int) (255 * ratio);
            b = (int) (255 * (1 - ratio));
        } else {
            double ratio = (normalized - 0.5) * 2.0;
            r = (int) (255 * ratio);
            g = (int) (255 * (1 - ratio));
            b = 0;
        }
        
        // Pack into int (ARGB)
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderObstaclesOnly(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        for (int x = 0; x < model.getWidth(); x++) {
            for (int y = 0; y < model.getHeight(); y++) {
                if (model.isObstacle(x, y)) {
                    g.setColor(Color.GRAY);
                    g.fillRect(x * scale, y * scale, scale, scale);
                }
            }
        }
    }

    private void renderVectors(Graphics g) {
        renderObstaclesOnly(g); // Draw background first
        
        g.setColor(Color.CYAN);
        int skip = 2; // Skip pixels to avoid clutter
        
        for (int x = 0; x < model.getWidth(); x += skip) {
            for (int y = 0; y < model.getHeight(); y += skip) {
                if (model.isObstacle(x, y)) continue;

                double ux = model.getVelocityX(x, y);
                double uy = model.getVelocityY(x, y);
                
                int x1 = x * scale + scale / 2;
                int y1 = y * scale + scale / 2;
                int x2 = x1 + (int)(ux * scale * 20); // Scale vector length
                int y2 = y1 + (int)(uy * scale * 20);
                
                g.drawLine(x1, y1, x2, y2);
            }
        }
    }

    /**
     * Maps a scalar value (speed) to a Color (Blue -> Green -> Red).
     */
    private Color getHeatMapColor(double value) {
        int colorInt = getHeatMapColorInt(value);
        return new Color(colorInt);
    }

    public void setViewMode(ViewMode mode) {
        this.currentViewMode = mode;
        repaint();
    }
}
