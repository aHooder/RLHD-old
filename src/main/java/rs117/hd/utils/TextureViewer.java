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
import static com.jogamp.opengl.GL.GL_TEXTURE1;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLE_FAN;
import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;
import com.jogamp.opengl.GL3;
import static com.jogamp.opengl.GL3ES3.GL_GEOMETRY_SHADER;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.OSType;
import static rs117.hd.GLUtil.glDeleteBuffer;
import static rs117.hd.GLUtil.glDeleteVertexArrays;
import static rs117.hd.GLUtil.glGenBuffers;
import static rs117.hd.GLUtil.glGenVertexArrays;
import rs117.hd.HdPlugin;
import rs117.hd.Shader;
import rs117.hd.ShaderException;
import rs117.hd.template.Template;

@Slf4j
public class TextureViewer implements KeyListener, MouseListener
{
	public static class RenderPass extends ShaderEnum
	{
		// FIXME: apparently Java won't let you write switch statements with these...
		private static int n = 0;
		// TOOD: replace colorsr with SOLID_COLOR & uniform vec4
		public static final int SOLID_AUTO = n++;
		public static final int SOLID_BLACK = n++;
		public static final int SOLID_OSRS_SEA = n++;
		public static final int PATTERN_CHECKER = n++;
		public static final int PATTERN_MISSING = n++;
		public static final int TEXTURE = n++;
	}

	@RequiredArgsConstructor
	public static class TextureModifiers extends ShaderEnum
	{
		private static int n = 0;
		public static final int HAS_COLOR = 1 << n++;
		public static final int HAS_DEPTH = 1 << n++;

		public static final int FLIP_X = 1 << n++;
		public static final int FLIP_Y = 1 << n++;
		public static final int FLIP_Z = 1 << n++;
		public static final int UINT_DEPTH = 1 << n++;
		public static final int ALPHA_DEPTH = 1 << n++;
		public static final int GREYSCALE = 1 << n++;
		public static final int RGB_TO_GREYSCALE = 1 << n++;
		public static final int RGB_TO_BGR = 1 << n++;
		public static final int DISABLE_ALPHA = 1 << n++;
		public static final int PARALLAX = 1 << n++;
		public static final int RAINBOW = 1 << n++;
		public static final int TERRAIN = 1 << n++;
		public static final int INTERPOLATE_DEPTH = 1 << n++;
		public static final int SHADOWS = 1 << n++;
	}

	private static final float[] IDENTITY_MAT4 = new Matrix4().getMatrix();

	private static final Shader TEXTURE_VIEWER_PROGRAM = new Shader()
		.add(GL_VERTEX_SHADER, "utils/texture_viewer_vert.glsl")
		.add(GL_GEOMETRY_SHADER, "utils/texture_viewer_geom.glsl")
		.add(GL_FRAGMENT_SHADER, "utils/texture_viewer_frag.glsl");

	private static int glProgram = -1;

	private static int uniRenderPass;
	private static int uniTextureModifiers;
	private static int uniTextureDepthMin;
	private static int uniTextureDepthMax;
	private static int uniTextureDepthScale;
	private static int uniTextureAspectRatio;

	private static int uniColorTexture;
	private static int uniDepthTexture;
	private static int uniUDepthTexture;

	private static int uniModel;
	private static int uniView;
	private static int uniProjection;

	private static int uniTime;

	private static final AtomicInteger activeShaderUsers = new AtomicInteger(0);
	private static final HdPlugin.ShaderHook shaderHook = new HdPlugin.ShaderHook()
	{
		@Override
		public void compile(GL4 gl, Template parentTemplate) throws ShaderException
		{
			Template template = parentTemplate.copy()
				.prepend(key ->
				{
					Pattern regex = Pattern.compile("^\\s*<(\\w+)>\\s*$");
					Matcher m = regex.matcher(key);
					if (m.find())
					{
						key = m.group(1);
						switch (key)
						{
							case "RENDER_PASS":
								return ShaderEnum.generateDefines(key, RenderPass.class);
							case "TEXTURE_MODIFIER":
								return ShaderEnum.generateDefines(key, TextureModifiers.class);
						}
					}
					return null;
				});

			int v = activeShaderUsers.getAndIncrement();
			if (v == 0 && glProgram == -1)
			{
				try
				{
					glProgram = TEXTURE_VIEWER_PROGRAM.compile(gl, template);

					uniRenderPass = gl.glGetUniformLocation(glProgram, "renderPass");
					uniTextureModifiers = gl.glGetUniformLocation(glProgram, "textureModifiers");
					uniTextureDepthMin = gl.glGetUniformLocation(glProgram, "texDepthMin");
					uniTextureDepthMax = gl.glGetUniformLocation(glProgram, "texDepthMax");
					uniTextureDepthScale = gl.glGetUniformLocation(glProgram, "texDepthScale");
					uniTextureAspectRatio = gl.glGetUniformLocation(glProgram, "texAspectRatio");

					uniColorTexture = gl.glGetUniformLocation(glProgram, "texColor");
					uniDepthTexture = gl.glGetUniformLocation(glProgram, "texDepth");
					uniUDepthTexture = gl.glGetUniformLocation(glProgram, "texUDepth");

					uniModel = gl.glGetUniformLocation(glProgram, "model");
					uniView = gl.glGetUniformLocation(glProgram, "view");
					uniProjection = gl.glGetUniformLocation(glProgram, "projection");

					uniTime = gl.glGetUniformLocation(glProgram, "time");
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

	private final ArrayList<TextureView> textures = new ArrayList<>();
	private int textureIndex = 0;
	private boolean alwaysOnTop = false;
	private int backgroundPass = RenderPass.PATTERN_CHECKER;
	private int renderPass = RenderPass.PATTERN_MISSING;
	private int textureModifiers = 0;

	private final Matrix4 modelMatrix = new Matrix4();
	private final Matrix4 viewMatrix = new Matrix4();
	private final Matrix4 projectionMatrix = new Matrix4();
	private final float[] viewPos = { 0, 0, 1 };
	private final int[] prevMousePos = { 0, 0 };
	private short mouseButton = 0;

	private final ArrayList<Runnable> shutdownHooks = new ArrayList<>();
	private final Runnable shutdownHook = this::shutdown;
	private final Runnable renderHook;

	private boolean initialized = false;
	private boolean renderHookAdded = false;

	public TextureViewer(HdPlugin hdPlugin)
	{
		this.hdPlugin = hdPlugin;

		hdPlugin.invokeOnMainThreadIfMacOS(() ->
			window = GLWindow.create(hdPlugin.glCaps));
		window.setTitle("Texture Viewer");
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

		resetMatrices();
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
			if (context != null)
			{
				GL4 gl = context.getGL().getGL4();
				if (gl != null)
				{
					destroyQuadVao(gl);
					shaderHook.destroy(gl);
				}
			}
			window.destroy();
		});
	}

	public void addShutdownHook(Runnable hook)
	{
		shutdownHooks.add(hook);
	}

	private void initialize(GL3 gl)
	{
		initQuadVao(gl);
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
				// positions, OpenGL texture coords (bottom left to top right)
				1f, 1f, 0.0f, 1.0f, 1f, // top right
				1f, -1f, 0.0f, 1.0f, 0f, // bottom right
				-1f, -1f, 0.0f, 0.0f, 0f, // bottom left
				-1f, 1f, 0.0f, 0.0f, 1f,  // top left
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

	public TextureViewer addTexture(TextureView texture)
	{
		textures.add(texture);
		if (textures.size() == 1)
		{
			cycleTextures(0);
		}
		return this;
	}

	public TextureViewer removeTexture(TextureView texture)
	{
		int i = textures.indexOf(texture);
		if (i != -1)
		{
			if (textureIndex > i)
			{
				cycleTextures(-1);
			}
			textures.remove(i);
		}
		return this;
	}

	public TextureViewer clearTextures()
	{
		textureIndex = 0;
		textures.clear();
		return this;
	}

	public TextureViewer cycleTextures(int step)
	{
		if (textures.size() > 0)
		{
			textureIndex = Math.floorMod(textureIndex + step, textures.size());
			TextureView texture = textures.get(textureIndex);
			textureModifiers = texture.modifiers;
			renderPass = texture.getRenderPass();
			backgroundPass = texture.backgroundPass;
		}
		return this;
	}

	public TextureViewer setTitle(String title)
	{
		window.setTitle(title);
		return this;
	}

	public TextureViewer setSize(int width, int height)
	{
		hdPlugin.invokeOnMainThreadIfMacOS(() ->
			window.setSize(width, height));
		return this;
	}

	public TextureViewer setAlwaysOnTop(boolean alwaysOnTop)
	{
		hdPlugin.invokeOnMainThreadIfMacOS(() ->
			window.setAlwaysOnTop(alwaysOnTop));
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
		hdPlugin.invokeOnMainThreadIfMacOS(() ->
			window.setPosition(Math.max(0, point.x - window.getWidth()), Math.max(0, point.y)));
		return this;
	}

	public void render(GLContext context)
	{
		context.setSwapInterval(0);

		GL3 gl = context.getGL().getGL3();

		if (!initialized)
		{
			initialize(gl);
			initialized = true;
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

			try
			{
				TextureView texture = textures.get(textureIndex);

				int color = texture.colorTexture;
				int depth = texture.depthTexture;

				gl.glUniform1f(uniTextureDepthMin, texture.depthMin);
				gl.glUniform1f(uniTextureDepthMax, texture.depthMax);
				gl.glUniform1f(uniTextureDepthScale, texture.depthScale);
				gl.glUniform1f(uniTextureAspectRatio, texture.aspectRatio);

				if (color != -1 && gl.glIsTexture(color))
				{
					gl.glActiveTexture(GL_TEXTURE0);
					gl.glBindTexture(GL_TEXTURE_2D, color);
					gl.glUniform1i(uniColorTexture, 0);
				}

				if (depth != -1 && gl.glIsTexture(depth))
				{
					gl.glActiveTexture(GL_TEXTURE1);
					gl.glBindTexture(GL_TEXTURE_2D, depth);
					gl.glUniform1i(
						texture.hasModifiers(TextureModifiers.UINT_DEPTH) ?
						uniUDepthTexture : uniDepthTexture, 1);
				}

				gl.glActiveTexture(GL_TEXTURE0);
			}
			catch (IndexOutOfBoundsException ex)
			{
				log.error("Shouldn't be here...", ex);
			}

			// Render background pattern with no projection
			gl.glUniform1i(uniRenderPass, backgroundPass);
			gl.glUniformMatrix4fv(uniModel, 1, false, IDENTITY_MAT4, 0);
			gl.glUniformMatrix4fv(uniView, 1, false, IDENTITY_MAT4, 0);
			gl.glUniformMatrix4fv(uniProjection, 1, false, IDENTITY_MAT4, 0);
			gl.glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

			// Render textures
			gl.glUniform1i(uniRenderPass, renderPass);
			gl.glUniform1i(uniTextureModifiers, textureModifiers);
			gl.glUniformMatrix4fv(uniModel, 1, false, modelMatrix.getMatrix(), 0);
			gl.glUniformMatrix4fv(uniView, 1, false, viewMatrix.getMatrix(), 0);
			gl.glUniformMatrix4fv(uniProjection, 1, false, getProjectionMatrix().getMatrix(), 0);
			gl.glUniform1f(uniTime, (System.currentTimeMillis() % 86_400_000L) / 1000f);

			gl.glDrawArrays(gl.GL_TRIANGLE_FAN, 0, 4);

			gl.glBindVertexArray(0);

			window.swapBuffers();
		}
	}

	private TextureView getCurrentTexture()
	{
		try
		{
			return textures.get(textureIndex);
		}
		catch (IndexOutOfBoundsException ex)
		{
			return new TextureView(TextureView.Type.COLOR, 1, 1);
		}
	}

	private void resetMatrices()
	{
		modelMatrix.loadIdentity();
		resetViewMatrix();
	}

	private void resetViewMatrix()
	{
		viewMatrix.loadIdentity();
		viewMatrix.translate(-viewPos[0], -viewPos[1], -viewPos[2]);
	}

	private Matrix4 getProjectionMatrix()
	{
		projectionMatrix.loadIdentity();
		float windowAspectRatio = (float) window.getWidth() / window.getHeight();
		float aspectRatio = windowAspectRatio / getCurrentTexture().aspectRatio;
		projectionMatrix.makePerspective(HALF_PI, 1, .001f, 1000);
		if (aspectRatio > 1)
		{
			projectionMatrix.scale(1 / aspectRatio, 1, 1);
		}
		else
		{
			projectionMatrix.scale(1, aspectRatio, 1);
		}
		return projectionMatrix;
	}

	private Matrix4 getMVPMatrix()
	{
		Matrix4 m = new Matrix4();
		m.multMatrix(getProjectionMatrix());
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

	private float[] mouseToModelSpace(float x, float y)
	{
		float[] clipSpace = mouseToClipSpace(x, y);
		Matrix4 inverseVP = new Matrix4();
		inverseVP.multMatrix(getProjectionMatrix());
		inverseVP.multMatrix(viewMatrix);
		inverseVP.invert();
		float[] viewSpace = new float[4];
		projectVector(inverseVP, clipSpace, viewSpace);
		return viewSpace;
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

	private void withTranslation(Matrix4 matrix, float[] translation, Runnable runnable)
	{
		matrix.translate(translation[0], translation[1], translation[2]);
		runnable.run();
		matrix.translate(-translation[0], -translation[1], -translation[2]);
	}

	private void withTranslationNegated(Matrix4 matrix, float[] translation, Runnable runnable)
	{
		matrix.translate(-translation[0], -translation[1], -translation[2]);
		runnable.run();
		matrix.translate(translation[0], translation[1], translation[2]);
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		switch (e.getKeySymbol())
		{
			case 'C': // Enable color mode
				textureModifiers ^= TextureModifiers.HAS_COLOR;
				break;
			case 'D': // Enable depth mode
				if (e.isControlDown()) {
					if ((textureModifiers & TextureModifiers.RAINBOW) != 0) {
						textureModifiers ^= TextureModifiers.RAINBOW;
						textureModifiers ^= TextureModifiers.TERRAIN;
					} else if ((textureModifiers & TextureModifiers.TERRAIN) != 0) {
						textureModifiers ^= TextureModifiers.TERRAIN;
					} else {
						textureModifiers ^= TextureModifiers.RAINBOW;
					}
				} else {
					textureModifiers ^= TextureModifiers.HAS_DEPTH;
				}
				break;
			case 'G': // Toggle greyscale
				textureModifiers ^= TextureModifiers.GREYSCALE;
				break;
			case 'P': // Toggle height map parallax
				textureModifiers ^= TextureModifiers.PARALLAX;
				break;
			case 'T': // Toggle transparency
				textureModifiers ^= TextureModifiers.DISABLE_ALPHA;
				break;
			case 'B': // Cycle between different backgrounds
				backgroundPass = Math.floorMod(backgroundPass + 1, RenderPass.PATTERN_CHECKER + 1);
				if (backgroundPass < RenderPass.SOLID_AUTO)
				{
					backgroundPass = RenderPass.SOLID_AUTO;
				}
				break;
			case 'I':
				textureModifiers ^= TextureModifiers.INTERPOLATE_DEPTH;
				break;
			case 'A': // Toggle always on top
				alwaysOnTop = !alwaysOnTop;
				window.setAlwaysOnTop(alwaysOnTop);
				break;
			case 'R': // Reset model, view & projection matrices
				resetMatrices();
				break;
			case '+': // Duplicate window
				hdPlugin.openTextureViewer();
				break;
			case KeyEvent.VK_LEFT:
				cycleTextures(-1);
				break;
			case KeyEvent.VK_RIGHT:
				cycleTextures(1);
				break;
		}

		TextureView texture = textures.get(textureIndex);
		log.trace("### Texture {} ###\nrender pass: {}\ntexture modifiers: {}\nmin depth: {}\nmax depth: {}",
			textureIndex, renderPass, textureModifiers, texture.depthMin, texture.depthMax);

		if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			shutdown();
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
			modelMatrix.loadIdentity();
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
		float[] d = mouseToModelSpace(prevMousePos[0], prevMousePos[1]);
		VectorUtil.subVec3(d, mouseToModelSpace(e.getX(), e.getY()), d);

		switch (mouseButton)
		{
			case MouseEvent.BUTTON1: // left click to pan
				withTranslationNegated(viewMatrix, viewPos, () ->
					viewMatrix.translate(d[0], d[1], 0));
				break;
			case MouseEvent.BUTTON3: // right click to rotate
				float length = FloatUtil.sqrt(d[0] * d[0] + d[1] * d[1]);
				float[] perpVec = VectorUtil.normalizeVec2(new float[] { -d[1], d[0] });
				modelMatrix.rotate(length, perpVec[0], perpVec[1], 0);
				break;
		}

		prevMousePos[0] = e.getX();
		prevMousePos[1] = e.getY();
	}

	@Override
	public void mouseWheelMoved(MouseEvent e)
	{
		float[] pos = mouseToModelSpace(e.getX(), e.getY());
		float x = pos[0];
		float y = pos[1];

		float[] xyz = e.getRotation();
		float zoom = 1 + xyz[1] / 10;

		withTranslationNegated(viewMatrix, viewPos, () ->
		{
			viewMatrix.translate(x, y, 0);
			viewMatrix.scale(zoom, zoom, 1);
			viewMatrix.translate(-x, -y, 0);
		});
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}
}
