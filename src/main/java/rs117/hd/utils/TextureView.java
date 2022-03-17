package rs117.hd.utils;

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
		addModifiers(TextureViewer.TextureModifiers.FLIP_Z);
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
