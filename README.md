# Air Tunnel Simulation

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)
![Release](https://img.shields.io/badge/Release-Alpha%200.1-green?style=for-the-badge)

A 2D fluid dynamics simulation written in Java. I built this project to explore computational physics, specifically the **Lattice Boltzmann Method (LBM)**, and to create a visual way to understand how air flows around objects.

It's a virtual wind tunnel where you can draw walls, change the wind speed, and see how the air reacts in real-time.

---

## Release Notes: Alpha 0.1
**"High-Performance Update"**

This release focuses on stability and performance for high-resolution simulations.

*   **GPU Acceleration**: Now uses **Aparapi** to run the physics kernel on your GPU (OpenCL), significantly improving performance on large grids.
*   **Stability Fixes**: Implemented density clamping and velocity limiting to prevent "Black Screen" crashes at high Reynolds numbers.
*   **Optimized Rendering**: Direct memory access for visualization, reducing CPU overhead by ~40%.
*   **Debug Tools**: Added a "Print Debug Info" button to the control panel to monitor Reynolds Number, Density Range, and Stability metrics in real-time.
*   **New Controls**:
    *   **Wind Angle**: Adjust the direction of the airflow.
    *   **View Modes**: Switch between Velocity, Pressure (Density), and Curl (Vorticity).
    *   **Resolution**: Support for High-Res (512x256) grids.

---

## What it does

*   **Simulates Fluid Flow**: Uses the D2Q9 Lattice Boltzmann model to calculate fluid movement.
*   **Interactive Sandbox**: You can draw obstacles with your mouse while the simulation runs.
*   **Visualizes Data**:
    *   **Heatmap**: Shows velocity (Blue is slow, Red is fast).
    *   **Streamlines**: White particles show the path of the air.
    *   **Vorticity**: Visualize the curl/spin of the fluid.
*   **Calculates Forces**: Displays real-time **Drag** and **Lift** values acting on the obstacles.
*   **Adjustable Settings**:
    *   Change fluid viscosity (make it flow like water or honey).
    *   Adjust wind speed and angle.
    *   Change grid resolution (up to 512x256).

## Built With

*   **Java (JDK 11+)**: Core logic and physics.
*   **Maven**: Dependency management and build system.
*   **Aparapi**: GPU computing library (experimental support).
*   **Swing**: Used for the GUI and custom rendering.

## Installation & Run

### Prerequisites
*   Java Development Kit (JDK) 11 or higher.
*   Maven (optional, wrapper included in future updates).

### Running with Maven (Recommended)
1.  Navigate to the project root (where `pom.xml` is located).
2.  Build the project:
    ```bash
    mvn clean package
    ```
3.  Run the generated JAR:
    ```bash
    java -jar target/air-tunnel-1.0-SNAPSHOT.jar
    ```

### Running via Command Line (Legacy)
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
| **Sim Speed Slider** | Control simulation steps per frame. |
| **Viscosity Slider** | Adjust fluid thickness (Tau). |
| **Wind Speed/Angle** | Control inlet velocity vector. |
| **Print Debug Info** | Output current physics state to console. |

## Future Improvements

Here are some things I plan to add next:

*   **Image Import**: Loading black-and-white images to use as custom obstacle shapes (like airfoils or car silhouettes).
*   **Save/Load**: Ability to save the current state of the grid to a file.
*   **Advanced Turbulence**: Implementing LES (Large Eddy Simulation) for more accurate high-speed flow.
*   **3D Support**: Expanding the engine to use the D3Q19 model for 3D simulations.

## The Physics Behind It

This simulation solves the discrete Boltzmann equation:

$$ f_i(x + e_i \Delta t, t + \Delta t) - f_i(x, t) = -\frac{1}{\tau} [f_i(x, t) - f_i^{eq}(x, t)] $$

Where:
*   $f_i$: Particle distribution function.
*   $e_i$: Discrete velocity vectors.
*   $\tau$: Relaxation time (related to viscosity).
*   $f_i^{eq}$: Equilibrium distribution (Maxwell-Boltzmann).

The **D2Q9** model discretizes space into a grid where each node has 9 possible particle velocities. The simulation proceeds in two steps:
1.  **Collision**: Particles at a node interact and relax towards equilibrium.
2.  **Streaming**: Particles move to neighboring nodes based on their velocity.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
 
