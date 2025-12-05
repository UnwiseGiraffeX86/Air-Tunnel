package com.simulation.ui;

import com.simulation.simulation.Engine;
import com.simulation.simulation.LatticeModel;

import javax.swing.*;
import java.awt.*;

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
        
        // Set preferred width for the sidebar
        this.setPreferredSize(new Dimension(280, 0));

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(60, 60, 60)), // Separator line
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        
        // Helper to add components with spacing
        JLabel title = createLabel("Simulation Controls");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title);
        add(Box.createVerticalStrut(20));
        
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
        
        // --- Simulation Speed ---
        JLabel speedControlLabel = createLabel("Sim Speed (Steps/Frame): 2");
        add(speedControlLabel);
        JSlider speedControlSlider = new JSlider(1, 20, 2);
        speedControlSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        speedControlSlider.addChangeListener(e -> {
            int val = speedControlSlider.getValue();
            engine.setStepsPerFrame(val);
            speedControlLabel.setText("Sim Speed (Steps/Frame): " + val);
        });
        add(speedControlSlider);
        add(Box.createVerticalStrut(15));

        // --- View Mode ---
        add(createLabel("View Mode:"));
        String[] viewModes = {"Velocity (Speed)", "Pressure (Density)", "Curl (Vorticity)"};
        JComboBox<String> viewModeCombo = new JComboBox<>(viewModes);
        viewModeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewModeCombo.addActionListener(e -> {
            view.setViewMode(viewModeCombo.getSelectedIndex());
        });
        add(viewModeCombo);
        add(Box.createVerticalStrut(15));

        // --- Presets ---
        add(createLabel("Fluid Presets:"));
        String[] presets = {"Custom", "Air (Low Viscosity)", "Water (Medium)", "Oil (High Viscosity)"};
        JComboBox<String> presetCombo = new JComboBox<>(presets);
        presetCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // We need references to sliders to update them when preset changes
        // Initialized here, added to UI later
        JSlider viscositySlider = new JSlider(52, 200, 60);
        
        presetCombo.addActionListener(e -> {
            int idx = presetCombo.getSelectedIndex();
            if (idx == 1) viscositySlider.setValue(52); // Air ~ 0.52
            if (idx == 2) viscositySlider.setValue(60); // Water ~ 0.60
            if (idx == 3) viscositySlider.setValue(120); // Oil ~ 1.20
        });
        add(presetCombo);
        add(Box.createVerticalStrut(15));

        // --- Viscosity (Tau) ---
        JLabel viscosityLabel = createLabel("Viscosity: 0.60");
        add(viscosityLabel);
        
        // Increased min viscosity to 0.52 to prevent instability (BGK limit is 0.5)
        viscositySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        viscositySlider.setToolTipText("Lower = Water-like, Higher = Honey-like");
        viscositySlider.addChangeListener(e -> {
            double tau = viscositySlider.getValue() / 100.0;
            model.setViscosity(tau);
            viscosityLabel.setText(String.format("Viscosity: %.2f", tau));
            if (presetCombo.getSelectedIndex() != 0) presetCombo.setSelectedIndex(0); // Switch to Custom
        });
        add(viscositySlider);
        add(Box.createVerticalStrut(10));

        // --- Inlet Velocity ---
        JLabel speedLabel = createLabel("Wind Speed: 0.10");
        add(speedLabel);
        
        JSlider velocitySlider = new JSlider(0, 25, 10);
        velocitySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        velocitySlider.addChangeListener(e -> {
            double u = velocitySlider.getValue() / 100.0;
            model.setInletVelocity(u);
            speedLabel.setText(String.format("Wind Speed: %.2f", u));
        });
        add(velocitySlider);
        add(Box.createVerticalStrut(10));

        // --- Wind Direction ---
        JLabel angleLabel = createLabel("Wind Angle: 0°");
        add(angleLabel);
        
        JSlider angleSlider = new JSlider(0, 360, 0);
        angleSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        angleSlider.setMajorTickSpacing(90);
        angleSlider.setPaintTicks(true);
        angleSlider.addChangeListener(e -> {
            int angle = angleSlider.getValue();
            model.setInletAngle(angle);
            angleLabel.setText(String.format("Wind Angle: %d°", angle));
        });
        add(angleSlider);
        add(Box.createVerticalStrut(15));

        // --- Resolution ---
        add(createLabel("Grid Resolution:"));
        String[] resolutions = {"128x64 (Fast)", "256x128 (Balanced)", "512x256 (High Detail)"};
        JComboBox<String> resCombo = new JComboBox<>(resolutions);
        resCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        resCombo.setSelectedIndex(1); // Default 256x128
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
            model.reset();
        });
        add(resetButton);
        add(Box.createVerticalStrut(10));
        
        // --- Debug Button ---
        JButton debugButton = new JButton("Print Debug Info");
        debugButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        debugButton.addActionListener(e -> {
            model.printDebugInfo();
        });
        add(debugButton);
        
        // Push everything to top
        add(Box.createVerticalGlue());
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
