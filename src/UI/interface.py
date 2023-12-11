import tkinter as tk
import os
import pygame
from pygame.locals import *
from OpenGL.GL import *
from OpenGL.GLU import *
from stl import mesh
import ctypes
import sys

class AirTunnelInterface:
    def __init__(self, root):
        self.root = root
        self.init_pygame_frame()
        self.root.update()

    def init_pygame_frame(self):
        # Embed Pygame into Tkinter
        os.environ['SDL_WINDOWID'] = str(self.embed.winfo_id())
        if sys.platform == "win32":
            os.environ['SDL_VIDEODRIVER'] = 'windib'

        self.screen = pygame.display.set_mode((800, 600), DOUBLEBUF | OPENGL)
        pygame.display.set_caption("Air Tunnel Simulation")
        self.init_gl()

        # Drag and Drop Setup
        self.root.update()
        self.embed.drop_target_register(DND_FILES)
        self.embed.dnd_bind('<<Drop>>', self.on_drop)

    def init_gl(self):
        glClearColor(0.0, 0.0, 0.0, 1.0)  # Set the background color
        glEnable(GL_DEPTH_TEST)            # Enable depth testing for 3D rendering
        glShadeModel(GL_SMOOTH)            # Enable smooth shading
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()                   # Reset the projection matrix
        gluPerspective(45, (800 / 600), 0.1, 50.0)  # Define the perspective
        glMatrixMode(GL_MODELVIEW)

    def on_drop(self, event):
        # Handle the drop event
        # Load the model file dropped into the frame
        file_path = event.data
        if file_path:
            self.load_model(file_path)

    def load_model(self, file_path):
        # Load the model using numpy-stl
        your_model = mesh.Mesh.from_file(file_path)
        
        # Create a display list
        self.model_list = glGenLists(1)
        glNewList(self.model_list, GL_COMPILE)

        glBegin(GL_TRIANGLES)
        for facet in your_model.vectors:
            for vertex in facet:
                glVertex3fv(vertex)
        glEnd()

        glEndList()


    def draw(self):
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)  # Clear the screen
        glLoadIdentity()  # Reset the view

        # Camera settings, adjust as needed
        gluLookAt(0, 0, -8, 0, 0, 0, 0, 1, 0)

        # Render the model
        if self.model_list:
            glCallList(self.model_list)

        pygame.display.flip()  # Update the display


    def run(self):
        running = True
        while running:
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False

            self.draw()
            pygame.time.wait(10)  # Small delay for CPU relief

    pygame.quit()


if __name__ == "__main__":
    root = tk.Tk()
    embed = tk.Frame(root, width=800, height=600)
    embed.pack(side="left", expand=True, fill="both")
    app = AirTunnelInterface(embed)
    app.run()
