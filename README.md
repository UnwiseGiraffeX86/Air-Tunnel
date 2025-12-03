# Air Tunnel Simulation

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)

A 2D fluid dynamics simulation written in Java. I built this project to explore computational physics, specifically the **Lattice Boltzmann Method (LBM)**, and to create a visual way to understand how air flows around objects.

It's a virtual wind tunnel where you can draw walls, change the wind speed, and see how the air reacts in real-time.

---

## What it does

*   **Simulates Fluid Flow**: Uses the D2Q9 Lattice Boltzmann model with **Large Eddy Simulation (LES)** turbulence modeling to calculate fluid movement.
*   **Interactive Sandbox**: You can draw obstacles with your mouse while the simulation runs.
*   **Visualizes Data**:
    *   **Heatmap**: Shows velocity (Blue is slow, Red is fast).
    *   **Vectors**: Displays flow direction and magnitude.
    *   **Particles**: Pure particle view for clear streamline visualization.
    *   **Density**: Visualizes pressure/density variations.
*   **Calculates Forces**: Displays real-time **Drag** and **Lift** values acting on the obstacles.
*   **Adjustable Settings**:
    *   **Fluid Presets**: Quickly switch between Air, Water, and Oil.
    *   Change fluid viscosity manually.
    *   Adjust wind speed.
    *   Change grid resolution (up to 400x200).
    *   **Debug Mode**: Inspect node values and check stability.

## Built With

*   **Java (JDK 11+)**: Core logic and physics.
*   **Swing**: Used for the GUI and custom rendering. No external game engines or heavy libraries were used—just standard Java.

## Installation & Run

### Prerequisites
*   Java Development Kit (JDK) 11 or higher.

### Running via Command Line
1.  Clone the repository:
    ```bash
    git clone https://github.com/UnwiseGiraffeX86/air-tunnel-simulation.git
    ```
2.  Navigate to the source directory:
    ```bash
    cd air-tunnel-simulation/src/main/java
    ```
3.  Compile the source code:
    ```bash
    javac com/simulation/Main.java com/simulation/simulation/*.java com/simulation/ui/*.java
    ```
4.  Run the application:
    ```bash
    java com.simulation.Main
    ```

## Controls

| Action | Description |
| :--- | :--- |
| **Left Click + Drag** | Draw solid obstacles (walls). |
| **Right Click + Drag** | Erase obstacles. |
| **Sliders** | Adjust Viscosity and Inlet Velocity. |
| **Dropdowns** | Change Resolution, Visualization Mode, and Fluid Medium. |
| **Debug Checkbox** | Enable detailed logging and stability checks. |
| **Click (Debug)** | Print detailed physics state of the clicked node to console. |

## Future Improvements

Here are some things I plan to add next:

*   **Image Import**: Loading black-and-white images to use as custom obstacle shapes (like airfoils or car silhouettes).
*   **Save/Load**: Ability to save the current state of the grid to a file.
*   **3D Support**: Expanding the engine to use the D3Q19 model for 3D simulations.

## The Physics Behind It

This simulation solves the discrete Boltzmann equation:

$$ f_i(x + e_i \Delta t, t + \Delta t) - f_i(x, t) = -\frac{1}{\tau} [f_i(x, t) - f_i^{eq}(x, t)] $$

Where:
*   $f_i$: Particle distribution function.
*   $e_i$: Discrete velocity vectors.
*   $\tau$: Relaxation time (related to viscosity).
*   $f_i^{eq}$: Equilibrium distribution (Maxwell-Boltzmann).

The simulation also implements the **Smagorinsky Large Eddy Simulation (LES)** model to handle turbulence at higher Reynolds numbers by locally adjusting the relaxation time based on the strain rate tensor.

The **D2Q9** model discretizes space into a grid where each node has 9 possible particle velocities. The simulation proceeds in two steps:
1.  **Collision**: Particles at a node interact and relax towards equilibrium.
2.  **Streaming**: Particles move to neighboring nodes based on their velocity.

### Performance Optimizations
To ensure smooth performance (60 FPS) even at high resolutions, the engine uses:
*   **Flattened 1D Arrays**: Data is stored in 1D arrays instead of 3D/2D arrays to maximize CPU cache locality.
*   **Direct Pixel Manipulation**: Rendering is done by writing directly to a `BufferedImage` integer array, bypassing slower Java2D drawing calls.
*   **Thread Safety**: Critical sections are synchronized to prevent race conditions during UI interaction.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
 
