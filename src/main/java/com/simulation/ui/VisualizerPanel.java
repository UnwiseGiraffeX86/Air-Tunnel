package com.simulation.ui;

import com.simulation.simulation.LatticeModel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

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
    private int viewMode = 0; // 0=Velocity, 1=Pressure, 2=Curl
    private BufferedImage canvas;
    private int[] canvasPixels;

    public VisualizerPanel(LatticeModel model) {
        this.model = model;
        this.setBackground(Color.BLACK);
        this.setPreferredSize(new Dimension(800, 400));
        
        // Initialize buffer
        initBuffer();

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
    
    private void initBuffer() {
        int w = model.getWidth();
        int h = model.getHeight();
        if (canvas == null || canvas.getWidth() != w || canvas.getHeight() != h) {
            canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            canvasPixels = ((java.awt.image.DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
        }
    }
    
    public void setViewMode(int mode) {
        this.viewMode = mode;
    }
    
    public void updateSize() {
        initBuffer();
        this.revalidate();
    }

    private void handleMouseInput(MouseEvent e) {
        int mw = model.getWidth();
        int mh = model.getHeight();
        
        // Calculate scale to preserve aspect ratio
        double scale = Math.min((double) getWidth() / mw, (double) getHeight() / mh);
        
        // Calculate offsets to center the image
        int drawW = (int) (mw * scale);
        int drawH = (int) (mh * scale);
        int offX = (getWidth() - drawW) / 2;
        int offY = (getHeight() - drawH) / 2;
        
        // Map mouse to grid
        int gridX = (int) ((e.getX() - offX) / scale);
        int gridY = (int) ((e.getY() - offY) / scale);
        
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
        
        if (canvas == null) initBuffer();
        
        renderToBuffer();

        int mw = model.getWidth();
        int mh = model.getHeight();

        // Calculate scale to preserve aspect ratio
        double scale = Math.min((double) getWidth() / mw, (double) getHeight() / mh);
        int drawW = (int) (mw * scale);
        int drawH = (int) (mh * scale);
        int offX = (getWidth() - drawW) / 2;
        int offY = (getHeight() - drawH) / 2;

        // Draw the image scaled and centered
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(canvas, offX, offY, drawW, drawH, null);

        // Draw Particles (Streamlines)
        g.setColor(Color.WHITE);
        List<Particle> particles = model.getParticles();
        if (particles != null) {
            for (Particle p : particles) {
                int px = offX + (int) (p.x * scale);
                int py = offY + (int) (p.y * scale);
                // Clip to drawing area
                if (px >= offX && px < offX + drawW && py >= offY && py < offY + drawH) {
                    g.fillOval(px, py, 2, 2);
                }
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

    private void renderToBuffer() {
        int mw = model.getWidth();
        int mh = model.getHeight();
        
        // Update the pixel buffer directly
        // Optimized: Direct array access to avoid method call overhead
        float[] ux = model.getUxArray();
        float[] uy = model.getUyArray();
        float[] rho = model.getRhoArray();
        boolean[] obstacle = model.getObstacleArray();
        
        for (int y = 0; y < mh; y++) {
            int yOffset = y * mw;
            for (int x = 0; x < mw; x++) {
                int idx = yOffset + x;
                int colorVal = 0;

                if (obstacle[idx]) {
                    colorVal = 0xFF808080; // Gray
                } else {
                    if (viewMode == 0) { // Velocity
                        float vx = ux[idx];
                        float vy = uy[idx];
                        // Avoid sqrt if possible, but needed for magnitude
                        // We can use a faster approximation or just standard sqrt (it's intrinsic usually)
                        double speed = Math.sqrt(vx * vx + vy * vy);
                        if (Double.isNaN(speed)) speed = 0; 
                        colorVal = getHeatMapColorInt(speed, 0.15);
                    } else if (viewMode == 1) { // Pressure (Density)
                        float r = rho[idx];
                        if (Float.isNaN(r)) r = 1.0f; 
                        colorVal = getHeatMapColorInt(r - 1.0, 0.05); 
                    } else if (viewMode == 2) { // Curl
                        // Inline Curl Calculation
                        double curl = 0;
                        if (x > 0 && x < mw - 1 && y > 0 && y < mh - 1) {
                            float duy_dx = (uy[yOffset + x + 1] - uy[yOffset + x - 1]) * 0.5f;
                            float dux_dy = (ux[(y + 1) * mw + x] - ux[(y - 1) * mw + x]) * 0.5f;
                            curl = duy_dx - dux_dy;
                        }
                        if (Double.isNaN(curl)) curl = 0;
                        colorVal = getCurlColorInt(curl);
                    }
                }
                canvasPixels[idx] = colorVal;
            }
        }
    }

    public BufferedImage getSnapshot(int modeToCheck) {
        int originalMode = this.viewMode;
        this.viewMode = modeToCheck;
        
        if (canvas == null) initBuffer();
        renderToBuffer();
        
        // Create a copy
        BufferedImage snapshot = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = snapshot.getGraphics();
        g.drawImage(canvas, 0, 0, null);
        g.dispose();
        
        this.viewMode = originalMode;
        // Re-render original mode to restore state
        renderToBuffer(); 
        
        return snapshot;
    }

    /**
     * Maps a scalar value to an RGB integer.
     * Gradient: Black -> Blue -> Cyan -> Green -> Yellow -> Red
     */
    private int getHeatMapColorInt(double value, double max) {
        double normalized = Math.abs(value) / max;
        if (normalized > 1.0) normalized = 1.0;
        
        int r = 0, g = 0, b = 0;

        // 5-stop gradient for better visualization
        // 0.00 - 0.20: Black -> Blue
        // 0.20 - 0.40: Blue -> Cyan
        // 0.40 - 0.60: Cyan -> Green
        // 0.60 - 0.80: Green -> Yellow
        // 0.80 - 1.00: Yellow -> Red
        
        if (normalized < 0.2) {
            // Black -> Blue
            double ratio = normalized / 0.2;
            b = (int) (255 * ratio);
        } else if (normalized < 0.4) {
            // Blue -> Cyan
            double ratio = (normalized - 0.2) / 0.2;
            b = 255;
            g = (int) (255 * ratio);
        } else if (normalized < 0.6) {
            // Cyan -> Green
            double ratio = (normalized - 0.4) / 0.2;
            g = 255;
            b = (int) (255 * (1 - ratio));
        } else if (normalized < 0.8) {
            // Green -> Yellow
            double ratio = (normalized - 0.6) / 0.2;
            g = 255;
            r = (int) (255 * ratio);
        } else {
            // Yellow -> Red
            double ratio = (normalized - 0.8) / 0.2;
            r = 255;
            g = (int) (255 * (1 - ratio));
        }

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
    
    private int getCurlColorInt(double val) {
        double max = 0.02;
        double norm = val / max;
        if (norm > 1) norm = 1;
        if (norm < -1) norm = -1;
        
        int r = 0, g = 0, b = 0;
        if (norm > 0) {
            r = (int)(255 * norm);
        } else {
            b = (int)(255 * -norm);
        }
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
