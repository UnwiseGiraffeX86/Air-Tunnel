import sys
import tkinter as tk
from tkinter import ttk
from tkinter import filedialog
from OpenGL.GL import *
from OpenGL.GLUT import *
from OpenGL.GLU import *
from stl import mesh

class AirTunnelInterface:
    def __init__(self, root):
        self.root = root
        self.root.title("Air Tunnel Simulation")

        # Initialize OpenGL
        glutInit()
        self.create_widgets()
        self.init_gl()

    def create_widgets(self):
        # Frame for OpenGL canvas
        self.opengl_frame = ttk.Frame(self.root, width=600, height=600)
        self.opengl_frame.pack(side="left", expand=True, fill="both")

        # OpenGL Canvas
        self.opengl_canvas = tk.Canvas(self.opengl_frame)
        self.opengl_canvas.pack(fill="both", expand=True)
        self.opengl_canvas.bind("<Configure>", self.on_resize)

        # Control Panel for User Interaction
        self.control_panel = ttk.Frame(self.root)
        self.control_panel.pack(side="right", fill="tk.Y")

        # Buttons, Sliders, etc., for controlling the simulation
        # Example: Load Model Button
        self.load_model_button = ttk.Button(self.control_panel, text="Load Model", command=self.load_model)
        self.load_model_button.pack(pady=10)

    def init_gl(self):
        # Basic OpenGL initialization
        self.context = self.opengl_canvas.tk.call(self.opengl_canvas._w, 'context')
        glEnable(GL_DEPTH_TEST)

    def on_resize(self, event):
        # Handle window resizing, adjust the viewport
        width, height = event.width, event.height
        glViewport(0, 0, width, height)

    def load_model(self):
            # Open file dialog to choose an STL file
            #file_path = filedialog.askopenfilename(filetypes=[("STL files", "*.stl")])
            file_path = "C:/Users/stefa/Downloads/Super Flexi Gecko.stl"
            if not file_path:
                return  # No file was selected

            # Load the model using numpy-stl
            self.model = mesh.Mesh.from_file(file_path)
            self.model_list = self.create_model_list(self.model)

    def create_model_list(self, model):
        # Create a display list for the model
        model_list = glGenLists(1)
        glNewList(model_list, GL_COMPILE)
            
        glBegin(GL_TRIANGLES)
        for facet in model.vectors:
            for vertex in facet:
                glVertex3fv(vertex)
        glEnd()

        glEndList()
        return model_list
    
    
    def draw(self):
    # Clear the screen
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

        # Set up the camera (view matrix)
        glMatrixMode(GL_MODELVIEW)
        glLoadIdentity()
        gluLookAt(
            0.0, 0.0, 10.0,  # Camera position (x, y, z)
            0.0, 0.0, 0.0,   # Look at point (center of the scene)
            0.0, 1.0, 0.0    # Up vector (orientation of the camera)
        )

        # Apply transformations here (if needed)
        # Example: rotating the model or changing its position
        glRotatef(self.rotation_x, 1.0, 0.0, 0.0)  # Rotate around x-axis
        glRotatef(self.rotation_y, 0.0, 1.0, 0.0)  # Rotate around y-axis

        # Draw the loaded model (if available)
        if hasattr(self, 'model_list'):
            glCallList(self.model_list)

        # Swap buffers
        glutSwapBuffers()


    def update(self):
        # Update the scene
        self.draw()
        self.root.after(100, self.update)

    def run(self):
        # Main loop for the interface
        self.update()
        self.root.mainloop()

if __name__ == "__main__":
    root = tk.Tk()
    app = AirTunnelInterface(root)
    app.run()