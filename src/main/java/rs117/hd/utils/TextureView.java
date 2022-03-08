package rs117.hd.utils;

import lombok.RequiredArgsConstructor;

public class TextureView
{
	public Type type;
	public float aspectRatio;
	public int modifiers;
	public float depthMin = 0;
	public float depthMax = 1;
	public float depthScale = .05f;
	public int backgroundPass = TextureViewer.RenderPass.PATTERN_CHECKER;

	public int colorTexture = -1;
	public int depthTexture = -1;

	public enum Type
	{
		GREYSCALE,
		COLOR,
		DEPTH,
		COLOR_AND_DEPTH,
		HEIGHT,
		COLOR_AND_HEIGHT
	}

	@RequiredArgsConstructor
	public static class Modifier extends ShaderEnum
	{
		private static int n = 0;
		public static final int COLOR = 1 << n++;
		public static final int DEPTH = 1 << n++;

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
	}

	public TextureView(Type type, int width, int height)
	{
		this(type, (float) width / height);
	}

	public TextureView(Type type, float aspectRatio)
	{
		this.type = type;
		this.aspectRatio = aspectRatio;
	}

	public TextureView setColorTexture(int texture)
	{
		colorTexture = texture;
		return this;
	}

	public TextureView setDepthMap(int texture)
	{
		depthTexture = texture;
		return this;
	}

	public TextureView setHeightMap(int texture)
	{
		depthTexture = texture;
		addModifiers(Modifier.FLIP_Z);
		return this;
	}

	public TextureView setDepthBounds(float min, float max)
	{
		depthMin = min;
		depthMax = max;
		return this;
	}

	public TextureView setDepthScale(float scale)
	{
		depthScale = scale;
		return this;
	}

	public TextureView setBackground(int renderPass)
	{
		backgroundPass = renderPass;
		return this;
	}

	public TextureView addModifiers(int modifiers)
	{
		this.modifiers |= modifiers;
		return this;
	}

	public int getRenderPass()
	{
		switch (type)
		{
			case GREYSCALE:
			case COLOR:
				if (colorTexture == -1)
				{
					break;
				}
				return TextureViewer.RenderPass.TEXTURE;
			case DEPTH:
			case HEIGHT:
				if (depthTexture == -1)
				{
					break;
				}
				return TextureViewer.RenderPass.TEXTURE;
			case COLOR_AND_DEPTH:
			case COLOR_AND_HEIGHT:
				if (colorTexture == -1 || depthTexture == -1)
				{
					break;
				}
				return TextureViewer.RenderPass.TEXTURE;
		}
		return TextureViewer.RenderPass.PATTERN_MISSING;
	}

	public boolean hasModifiers(int modifiers)
	{
		return (this.modifiers & modifiers) == modifiers;
	}
}
