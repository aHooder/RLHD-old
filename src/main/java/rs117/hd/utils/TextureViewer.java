/*
 * Copyright (c) 2022, Hooder <https://github.com/ahooder>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package rs117.hd.utils;

import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.opengl.GLWindow;
import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_CULL_FACE;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLE_FAN;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.math.FloatUtil;
import static com.jogamp.opengl.math.FloatUtil.HALF_PI;
import com.jogamp.opengl.math.Matrix4;
import com.jogamp.opengl.math.VectorUtil;
import java.awt.Component;
import java.awt.Point;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.util.OSType;
import static rs117.hd.GLUtil.glDeleteBuffer;
import static rs117.hd.GLUtil.glDeleteVertexArrays;
import static rs117.hd.GLUtil.glGenBuffers;
import static rs117.hd.GLUtil.glGenVertexArrays;
import rs117.hd.HdPlugin;
import rs117.hd.Shader;
import rs117.hd.ShaderException;
import rs117.hd.template.Template;

public class TextureViewer implements KeyListener, MouseListener
{
	private static final int BACKGROUND_PATTERN_PASS = 0;
	private static final int MISSING_TEXTURE_PASS = 1;
	private static final int COLOR_TEXTURE_PASS = 2;
	private static final int GREYSCALE_TEXTURE_PASS = 3;
	private static final int DEPTH_TEXTURE_PASS = 4;

	private static final Shader TEXTURE_VIEWER_PROGRAM = new Shader()
		.add(GL4.GL_VERTEX_SHADER, "utils/texture_viewer_vert.glsl")
		.add(GL4.GL_FRAGMENT_SHADER, "utils/texture_viewer_frag.glsl");

	private static int glProgram = -1;
	private static int uniProjection;
	private static int uniTexture;
	private static int uniRenderPass;

	private static final AtomicInteger activeShaderUsers = new AtomicInteger(0);
	private static final HdPlugin.ShaderHook shaderHook = new HdPlugin.ShaderHook()
	{
		@Override
		public void compile(GL4 gl, Template template) throws ShaderException
		{
			int v = activeShaderUsers.getAndIncrement();
			if (v == 0 && glProgram == -1)
			{
				try
				{
					glProgram = TEXTURE_VIEWER_PROGRAM.compile(gl, template);

					uniProjection = gl.glGetUniformLocation(glProgram, "projection");
					uniRenderPass = gl.glGetUniformLocation(glProgram, "renderPass");
					uniTexture = gl.glGetUniformLocation(glProgram, "tex");
				}
				catch (ShaderException ex)
				{
					activeShaderUsers.decrementAndGet();
					throw ex;
				}
			}
		}

		@Override
		public void destroy(GL4 gl)
		{
			int users = activeShaderUsers.updateAndGet(i -> Math.max(0, i - 1));
			if (users == 0 && glProgram != -1)
			{
				gl.glDeleteProgram(glProgram);
				glProgram = -1;
			}
		}
	};

	private final HdPlugin hdPlugin;
	private GLWindow window;

	private int vao, vbo;
	private int textureHandle = -1;
	private boolean alwaysOnTop = false;
	private int textureRenderPass = MISSING_TEXTURE_PASS;
	private Matrix4 modelMatrix = new Matrix4();
	private Matrix4 viewMatrix = new Matrix4();

	private final int[] prevMousePos = { 0, 0 };
	private short mouseButton = 0;

	private final ArrayList<Runnable> shutdownHooks = new ArrayList<>();
	private final Runnable shutdownHook = this::shutdown;
	private final Runnable renderHook;

	private boolean initialized = false;
	private boolean renderHookAdded = false;

	public TextureViewer(HdPlugin hdPlugin, String title)
	{
		this.hdPlugin = hdPlugin;

		hdPlugin.invokeOnMainThreadIfMacOS(() ->
			window = GLWindow.create(hdPlugin.glCaps));
		window.setTitle(title);
		window.addKeyListener(this);
		window.addMouseListener(this);
		window.setWindowDestroyNotifyAction(shutdownHook);

		hdPlugin.addShutdownHook(shutdownHook);
		hdPlugin.addShaderHook(shaderHook);

		if (hdPlugin.useSharedContexts)
		{
			window.setSharedContext(hdPlugin.glContext);
			renderHook = () ->
			{
				GLContext context = window.getContext();
				context.makeCurrent();
				render(context);
			};
		}
		else
		{
			renderHook = () ->
			{
				GLContext context = hdPlugin.glContext;
				context.setGLDrawable(window.getDelegatedDrawable(), true);
				render(context);
			};
		}
	}

	public void shutdown()
	{
		hdPlugin.removeShutdownHook(shutdownHook);
		hdPlugin.removeShaderHook(shaderHook);
		if (renderHookAdded)
		{
			hdPlugin.removeRenderHook(renderHook);
		}

		shutdownHooks.forEach(Runnable::run);
		shutdownHooks.clear();

		hdPlugin.invokeOnGLThread(this::destroy);
	}

	private void destroy()
	{
		hdPlugin.invokeWithWindowLocked(() ->
		{
			GLContext context = window.getContext();
			if (context != null && context.getGL() != null)
			{
				destroyQuadVao(context.getGL().getGL3());
			}
			window.destroy();
		});
	}

	public void addShutdownHook(Runnable hook)
	{
		shutdownHooks.add(hook);
	}

	private void initQuadVao(GL3 gl)
	{
		// Create quad VAO
		vao = glGenVertexArrays(gl);
		gl.glBindVertexArray(vao);

		// Create quad buffer
		vbo = glGenBuffers(gl);
		gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);

		FloatBuffer quadVertexData = ByteBuffer
			.allocateDirect(20 * Float.BYTES)
			.order(ByteOrder.nativeOrder())
			.asFloatBuffer()
			.put(new float[] {
				// positions     // texture coords
				1f, 1f, 0.0f, 1.0f, 1f, // top right
				1f, -1f, 0.0f, 1.0f, 0f, // bottom right
				-1f, -1f, 0.0f, 0.0f, 0f, // bottom left
				-1f, 1f, 0.0f, 0.0f, 1f  // top left
			})
			.flip();
		gl.glBufferData(GL_ARRAY_BUFFER, (long) quadVertexData.limit() * Float.BYTES, quadVertexData, GL_STATIC_DRAW);

		// Position attribute
		gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
		gl.glEnableVertexAttribArray(0);

		// UV attribute
		gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
		gl.glEnableVertexAttribArray(1);

		// Unbind VAO & VBO
		gl.glBindVertexArray(0);
		gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	private void destroyQuadVao(GL3 gl)
	{
		if (vao != -1)
		{
			glDeleteVertexArrays(gl, vao);
			vao = -1;
		}
		if (vbo != -1)
		{
			glDeleteBuffer(gl, vbo);
			vbo = -1;
		}
	}

	private TextureViewer setTexture(int textureHandle, int initialTextureRenderPass)
	{
		this.textureHandle = textureHandle;
		textureRenderPass = initialTextureRenderPass;
		return this;
	}

	public TextureViewer unsetTexture()
	{
		return setTexture(-1, MISSING_TEXTURE_PASS);
	}

	public TextureViewer setColorTexture(int textureHandle)
	{
		return setTexture(textureHandle, COLOR_TEXTURE_PASS);
	}

	public TextureViewer setGreyscaleTexture(int textureHandle)
	{
		return setTexture(textureHandle, GREYSCALE_TEXTURE_PASS);
	}

	public TextureViewer setDepthTexture(int textureHandle)
	{
		return setTexture(textureHandle, DEPTH_TEXTURE_PASS);
	}

	public TextureViewer setSize(int width, int height)
	{
		window.setSize(width, height);
		return this;
	}

	public TextureViewer setAlwaysOnTop(boolean alwaysOnTop)
	{
		window.setAlwaysOnTop(alwaysOnTop);
		return this;
	}

	public TextureViewer setVisible(boolean visible)
	{
		hdPlugin.invokeOnGLThread(() ->
		{
			if (OSType.getOSType() == OSType.MacOS)
			{
				hdPlugin.invokeWithWindowLocked(() ->
					window.setVisible(visible));
			}
			else
			{
				window.setVisible(visible);
			}

			if (visible)
			{
				hdPlugin.addRenderHook(renderHook);
				renderHookAdded = true;
			}
			else
			{
				hdPlugin.removeRenderHook(renderHook);
				renderHookAdded = false;
			}
		});
		return this;
	}

	public TextureViewer setPositionLeftOf(Component component)
	{
		Point point = component.getLocationOnScreen();
		window.setPosition(Math.max(0, point.x - window.getWidth()), Math.max(0, point.y));
		return this;
	}

	public void render(GLContext context)
	{
		context.setSwapInterval(0);

		GL3 gl = context.getGL().getGL3();

		if (!initialized)
		{
			initQuadVao(gl);
			initialized = false;
		}

		if (glProgram != -1)
		{
			gl.glUseProgram(glProgram);

			gl.glDisable(GL_DEPTH_TEST);
			gl.glDisable(GL_CULL_FACE);
			gl.glEnable(GL_BLEND);
			gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

			gl.glViewport(0, 0, window.getWidth(), window.getHeight());
			gl.glClearColor(0, 0, 0, 1);
			gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			gl.glBindVertexArray(vao);

			// Render background pattern with no projection
			gl.glUniform1i(uniRenderPass, BACKGROUND_PATTERN_PASS);
			gl.glUniformMatrix4fv(uniProjection, 1, false, new Matrix4().getMatrix(), 0);
			gl.glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

			// Render the texture with the current render mode, or missing if invalid texture
			int renderPass = MISSING_TEXTURE_PASS;
			if (gl.glIsTexture(textureHandle))
			{
				gl.glActiveTexture(GL_TEXTURE0);
				gl.glBindTexture(GL_TEXTURE_2D, textureHandle);
				gl.glUniform1i(uniTexture, 0);
				renderPass = textureRenderPass;
			}
			gl.glUniform1i(uniRenderPass, renderPass);
			gl.glUniformMatrix4fv(uniProjection, 1, false, getMVPMatrix().getMatrix(), 0);
			gl.glDrawArrays(gl.GL_TRIANGLE_FAN, 0, 4);

			gl.glBindVertexArray(0);

			window.swapBuffers();
		}
	}

	private Matrix4 getProjectionMatrix()
	{
		Matrix4 m = new Matrix4();
		float aspectRatio = (float) window.getWidth() / window.getHeight();
		m.makePerspective(HALF_PI, 1, .001f, 1000);
		if (aspectRatio > 1)
		{
			m.scale(1 / aspectRatio, 1, 1);
		}
		else
		{
			m.scale(1, aspectRatio, 1);
		}
		m.translate(0, 0, -1);
		return m;
	}

	private Matrix4 getMVPMatrix()
	{
		Matrix4 m = getProjectionMatrix();
		m.multMatrix(viewMatrix);
		m.multMatrix(modelMatrix);
		return m;
	}

	private void projectVector(Matrix4 matrix, float[] srcVector, float[] dstVector)
	{
		matrix.multVec(srcVector, dstVector);
		dstVector[0] /= dstVector[3];
		dstVector[1] /= dstVector[3];
		dstVector[2] /= dstVector[3];
		dstVector[3] = 1;
	}

	private float[] mouseToClipSpace(float x, float y)
	{
		Matrix4 proj = getMVPMatrix();
		float[] result = new float[4];
		projectVector(proj, new float[] { 0, 0, 0, 1 }, result);

		return new float[]
		{
			(x / window.getWidth()) * 2 - 1,
			(1 - y / window.getHeight()) * 2 - 1,
			result[2],
			1
		};
	}

	private float[] mouseToWorldSpace(float x, float y)
	{
		float[] clipSpace = mouseToClipSpace(x, y);
		Matrix4 inverse = getMVPMatrix();
		inverse.invert();
		float[] worldSpace = new float[4];
		projectVector(inverse, clipSpace, worldSpace);
		return worldSpace;
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		switch (e.getKeyChar())
		{
			case 'c': // Enable color mode
				textureRenderPass = COLOR_TEXTURE_PASS;
				break;
			case 'g': // Enable greyscale mode
				textureRenderPass = GREYSCALE_TEXTURE_PASS;
				break;
			case 'd': // Enable depth mode
				textureRenderPass = DEPTH_TEXTURE_PASS;
				break;
			case 'r': // Reset model & view matrices
				modelMatrix = new Matrix4();
				viewMatrix = new Matrix4();
				break;
			case 'a': // Toggle always on top
				alwaysOnTop = !alwaysOnTop;
				window.setAlwaysOnTop(alwaysOnTop);
				break;
			case '+': // Duplicate window
				hdPlugin.openShadowMapViewer();
				break;
		}

		switch (e.getKeyCode())
		{
			case KeyEvent.VK_ESCAPE:
				shutdown();
				break;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}

	@Override
	public void mousePressed(MouseEvent e)
	{
		mouseButton = e.getButton();
		prevMousePos[0] = e.getX();
		prevMousePos[1] = e.getY();

		if (mouseButton == MouseEvent.BUTTON2)
		{
			modelMatrix = new Matrix4();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseMoved(MouseEvent e) {}

	@Override
	public void mouseDragged(MouseEvent e)
	{
		float[] from = mouseToWorldSpace(prevMousePos[0], prevMousePos[1]);
		float[] to = mouseToWorldSpace(e.getX(), e.getY());
		float dx = to[0] - from[0];
		float dy = to[1] - from[1];

		switch (mouseButton)
		{
			case MouseEvent.BUTTON1: // left click to pan
				viewMatrix.translate(dx, dy, 0);
				break;
			case MouseEvent.BUTTON3: // right click to rotate
				float length = FloatUtil.sqrt(dx * dx + dy * dy);
				float[] perpVec = VectorUtil.normalizeVec2(new float[] { -dy, dx });
				modelMatrix.rotate(length, perpVec[0], perpVec[1], 0);
				break;
		}

		prevMousePos[0] = e.getX();
		prevMousePos[1] = e.getY();
	}

	@Override
	public void mouseWheelMoved(MouseEvent e)
	{
		float[] pos = mouseToWorldSpace(e.getX(), e.getY());
		float x = pos[0];
		float y = pos[1];

		float[] xyz = e.getRotation();
		float zoom = 1 + xyz[1] / 10;
		viewMatrix.translate(x, y, 0);
		viewMatrix.scale(zoom, zoom, 1);
		viewMatrix.translate(-x, -y, 0);
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}
}
