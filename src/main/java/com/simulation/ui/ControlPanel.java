package com.simulation.ui;

import com.simulation.simulation.Engine;
import com.simulation.simulation.LatticeModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ControlPanel.java
 * 
 * Provides UI controls for simulation parameters.
 */
public class ControlPanel extends JPanel {

    private final LatticeModel model;
    private final Engine engine;
    private final VisualizerPanel view;
    private final JFrame parentFrame;

    public ControlPanel(LatticeModel model, Engine engine, VisualizerPanel view, JFrame parentFrame) {
        this.model = model;
        this.engine = engine;
        this.view = view;
        this.parentFrame = parentFrame;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Controls"),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        // Helper to add components with spacing
        add(createLabel("Simulation State:"));
        
        // --- Pause / Resume ---
        JToggleButton pauseButton = new JToggleButton("Pause Simulation");
        pauseButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        pauseButton.addActionListener(e -> {
            boolean paused = pauseButton.isSelected();
            engine.setPaused(paused);
            pauseButton.setText(paused ? "Resume Simulation" : "Pause Simulation");
        });
        add(pauseButton);
        add(Box.createVerticalStrut(15));

        // --- Viscosity (Tau) ---
        add(createLabel("Viscosity (Fluid Thickness):"));
        JSlider viscositySlider = new JSlider(51, 120, 60);
        viscositySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        viscositySlider.setToolTipText("Lower = Water-like, Higher = Honey-like");
        viscositySlider.addChangeListener(e -> {
            double tau = viscositySlider.getValue() / 100.0;
            model.setViscosity(tau);
        });
        add(viscositySlider);
        add(Box.createVerticalStrut(10));

        // --- Inlet Velocity ---
        add(createLabel("Wind Speed:"));
        JSlider velocitySlider = new JSlider(0, 20, 10);
        velocitySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        velocitySlider.addChangeListener(e -> {
            double u = velocitySlider.getValue() / 100.0;
            model.setInletVelocity(u);
        });
        add(velocitySlider);
        add(Box.createVerticalStrut(15));

        // --- Resolution ---
        add(createLabel("Grid Resolution:"));
        String[] resolutions = {"100x50 (Fast)", "200x100 (Balanced)", "400x200 (High Detail)"};
        JComboBox<String> resCombo = new JComboBox<>(resolutions);
        resCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        resCombo.setSelectedIndex(1); // Default 200x100
        resCombo.addActionListener(e -> {
            String selected = (String) resCombo.getSelectedItem();
            // Extract numbers
            String[] parts = selected.split(" ")[0].split("x");
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            
            // Apply resolution
            model.setResolution(w, h);
            view.updateSize();
            parentFrame.pack(); // Resize window
            parentFrame.setLocationRelativeTo(null); // Re-center
        });
        add(resCombo);
        add(Box.createVerticalStrut(20));
        
        // --- Reset Button ---
        JButton resetButton = new JButton("Reset Simulation");
        resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetButton.addActionListener(e -> {
             // Re-trigger resolution set to reset arrays
             String selected = (String) resCombo.getSelectedItem();
             String[] parts = selected.split(" ")[0].split("x");
             int w = Integer.parseInt(parts[0]);
             int h = Integer.parseInt(parts[1]);
             model.setResolution(w, h);
        });
        add(resetButton);
        
        // Push everything to top
        add(Box.createVerticalGlue());
    }
    
    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
