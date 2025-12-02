package com.simulation;

import com.simulation.simulation.Engine;
import com.simulation.simulation.LatticeModel;
import com.simulation.ui.ControlPanel;
import com.simulation.ui.VisualizerPanel;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/**
 * Main.java
 * 
 * Entry point for the Air Tunnel Simulation.
 * Sets up the main window and initializes the simulation components.
 */
public class Main {

    public static void main(String[] args) {
        // Apply Modern Look and Feel (Nimbus)
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // Fallback to default
        }

        // Ensure UI creation runs on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    private static void createAndShowGUI() {
        // 1. Create the Main Window
        JFrame frame = new JFrame("Air Tunnel Simulation (LBM D2Q9)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        // 2. Initialize Simulation Components
        LatticeModel model = new LatticeModel();
        VisualizerPanel view = new VisualizerPanel(model);
        Engine engine = new Engine(model, view);
        ControlPanel controls = new ControlPanel(model, engine, view, frame);

        // 3. Add UI to Window
        frame.add(view, BorderLayout.CENTER);
        frame.add(controls, BorderLayout.EAST);
        
        frame.pack(); // Size window to fit the preferred size of the view
        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);

        // 4. Start the Simulation Loop
        engine.start();
    }
}
