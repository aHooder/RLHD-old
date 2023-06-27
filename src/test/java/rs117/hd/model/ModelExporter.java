package rs117.hd.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.api.model.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import org.lwjgl.assimp.AIExportFormatDesc;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AILogStream;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMaterialProperty;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import rs117.hd.data.materials.Material;
import rs117.hd.scene.SceneContext;
import rs117.hd.scene.TextureManager;
import rs117.hd.utils.ColorUtils;
import rs117.hd.utils.HDUtils;

import static net.runelite.api.Perspective.*;
import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAddress0;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memSlice;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static rs117.hd.model.ModelPusher.MAX_MATERIAL_COUNT;
import static rs117.hd.model.ModelPusher.interpolateHSL;
import static rs117.hd.utils.ResourcePath.path;

@Slf4j
public class ModelExporter extends Overlay implements MouseListener, MouseWheelListener, KeyListener, AutoCloseable {
	private static final Color OUTLINE_COLOR = new Color(0xFFFF00FF, true);
	private static final int OUTLINE_WIDTH = 4;
	private static final int OUTLINE_FEATHER = 4;
	private static final int EXPORTER_KEY = KeyCode.KC_CONTROL;
	private static final float[] ZEROS = new float[12];

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private TextureManager textureManager;

	@Inject
	private ModelPusher modelPusher;

	private int clientTickCount;
	private int selectedIndex;
	private Object selectedObject;
	private final HashSet<Object> hoverSet = new HashSet<>();

	static {
		try (var stack = MemoryStack.stackPush()) {
			aiEnableVerboseLogging(true);
			var logStream = AILogStream.malloc(stack);
			aiGetPredefinedLogStream(aiDefaultLogStream_STDERR, (ByteBuffer) null, logStream);
			aiAttachLogStream(logStream);
		}
	}

	@Inject
	public ModelExporter(
		EventBus eventBus,
		OverlayManager overlayManager,
		MouseManager mouseManager,
		KeyManager keyManager
	) {
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGH);

		eventBus.register(this);
		mouseManager.registerMouseListener(this);
		mouseManager.registerMouseWheelListener(this);
		keyManager.registerKeyListener(this);
		SwingUtilities.invokeLater(() -> overlayManager.add(this));
	}

	@Override
	public void close() {
		eventBus.unregister(this);
		overlayManager.remove(this);
		mouseManager.unregisterMouseListener(this);
		mouseManager.unregisterMouseWheelListener(this);
		keyManager.unregisterKeyListener(this);
	}

	private void exportModel(String type, String name, Object object) {
		long formatCount = aiGetExportFormatCount();
		log.debug("Supported formats: {}", formatCount);
		for (int i = 0; i < formatCount; i++) {
			AIExportFormatDesc desc = null;
			try {
				desc = aiGetExportFormatDescription(i);
				log.debug(
					"    Format id={} extension={} description={}",
					memUTF8(desc.id()),
					desc.fileExtensionString(),
					memUTF8(desc.description())
				);
			} finally {
				if (desc != null)
					aiReleaseExportFormatDescription(desc);
			}
		}

		String formatId = "gltf2";
		String extension = "gltf";
//		String formatId = "glb2";
//		String extension = "glb";
		var filename = (type + " " + name)
			.replace(' ', '_')
			.replaceAll("[^0-9a-zA-Z_ -]", "")
			.toLowerCase();
		var exportPath = path("model-exports", filename + "." + extension);
		exportPath.mkdirs();
		int counter = 2;
		while (exportPath.exists())
			exportPath = path("model-exports", filename + "_" + (counter++) + "." + extension);

		long hash = getHash(object);
		var models = resolveModels(object);
		if (models.isEmpty()) {
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(type.substring(0, 1).toUpperCase() + type.substring(1) + " ")
					.append(Color.CYAN, name)
					.append(" has no model data.")
					.build())
				.build());
			return;
		}

		var cleanup = new ArrayList<Long>();
		try (var stack = MemoryStack.stackPush()) {
			var scene = AIScene.calloc(stack);

			float scale = 1f / 128;
			var transform = AIMatrix4x4.calloc(stack)
				.a1(-scale).b2(-scale).c3(scale).d4(1);

			var rootNode = AINode.calloc(stack)
				.mTransformation(transform);
			scene.mRootNode(rootNode);

			Material firstMaterial = Material.NONE;
			int vanillaTextureId = -1;

			var meshes = AIMesh.calloc(models.size(), stack);
			var meshPointers = stack.mallocPointer(models.size());
			var meshIndices = stack.mallocInt(models.size());
			for (int modelIndex = 0; modelIndex < models.size(); modelIndex++) {
				var model = (Model) models.get(modelIndex);

				var sceneContext = new SceneContext(client.getScene(), null);
				modelPusher.pushModel(sceneContext, null, hash, model, null, 0, false);

				int numVertices = sceneContext.getVertexOffset();
				int numUvs = sceneContext.getUvOffset();

				var vertices = memAllocFloat(numVertices * 3);
				cleanup.add(memAddress0(vertices));
				var normals = memAllocFloat(numVertices * 3);
				cleanup.add(memAddress0(normals));
				var colors = memAllocFloat(numVertices * 4);
				cleanup.add(memAddress0(colors));
				var faces = AIFace.malloc(numVertices / 3);
				cleanup.add(faces.address());
				var indices = memAllocInt(numVertices);
				cleanup.add((memAddress(indices)));

				var mesh = meshes.get(modelIndex)
					.mPrimitiveTypes(aiPrimitiveType_TRIANGLE)
					.mVertices(AIVector3D.create(memAddress0(vertices), numVertices))
					.mNumVertices(numVertices)
					.mNormals(AIVector3D.create(memAddress0(normals), numVertices))
					.mColors(stack.pointers(colors))
					.mFaces(faces);

				FloatBuffer uvs = null;
				if (numUvs > 0) {
					uvs = memAllocFloat(numVertices * 3);
					cleanup.add(memAddress(uvs));
					mesh.mNumUVComponents(stack.ints(2))
						.mTextureCoords(stack.pointers(uvs));
				}

				for (int face = 0; face < numVertices / 3; face++) {
					faces.get().mIndices(memSlice(indices, 0, 3));
					for (int i = 0; i < 3; i++) {
						int vertex = face * 3 + i;
						indices.put((faces.position() - 1) * 3 + i);

						int color = sceneContext.stagingBufferVertices.getBuffer().get(vertex * 4 + 3);
						colors
							.put(ColorUtils.packedHslToSrgb(color))
							.put(1 - (color >>> 24 & 0xFF) / (float) 0xFF);

						for (int c = 0; c < 3; c++) {
							vertices.put(sceneContext.stagingBufferVertices.getBuffer().get(vertex * 4 + c));
							normals.put(sceneContext.stagingBufferNormals.getBuffer().get(vertex * 4 + c));
						}
					}

					if (uvs != null) {
						int materialData = (int) sceneContext.stagingBufferUvs.getBuffer().get(face * 3 * 4 + 3);

						if (firstMaterial == Material.NONE) {
							firstMaterial = Material.values()[materialData >> 12 & MAX_MATERIAL_COUNT];
							firstMaterial = textureManager.getEffectiveMaterial(firstMaterial);
							if (firstMaterial != Material.NONE)
								vanillaTextureId = firstMaterial.vanillaTextureIndex;

							if ((materialData >> 2 & 1) == 1)
								throw new IllegalStateException("Not sure what to do about world UVs");
						}

						if ((materialData >> 1 & 1) == 1) {
							fillVanillaUvs(uvs, model, face);
						} else {
							for (int i = 0; i < 3; i++) {
								int vertex = face * 3 + i;
								for (int c = 0; c < 3; c++)
									uvs.put(sceneContext.stagingBufferUvs.getBuffer().get(vertex * 4 + c));
							}
						}
					}
				}

				// Assimp only supports one material per mesh, so we need to split things up, or utilize zero-UVs for base color
				mesh.mMaterialIndex(0);

				// Model#getHash() = ObjectDefinitionId << 10 | 7-bit model type << 3 | 1-bit mirroring? | 2-bit orientation
				int objectCompositionId = (int) (model.getHash() >> 10);
				mesh.mName(s -> s.data(stack.UTF8(objectCompositionId == 0 ? name : "def_" + objectCompositionId)));

				meshPointers.put(modelIndex, mesh);
				meshIndices.put(modelIndex, modelIndex);
			}
			scene.mMeshes(meshPointers);
			rootNode.mMeshes(meshIndices);

			// TODO: split mesh into one mesh per material, but retain shared vertex data, only changing indices
			// TODO: split player mesh into one mesh per equipment
			// TODO: if turning this into a general purpose exporter, consider exporting a material with vertex colors UV mapped
//			Player p;
//			p.getPlayerComposition().getEquipmentIds() // 12 slots, convert IDs to model IDs
//			client.loadModelData(id)

			var material = AIMaterial.calloc(stack);
			scene.mMaterials(stack.pointers(material));

			// Writing properties is unfortunately quite cumbersome
			var materialProperties = stack.mallocPointer(3);
			material.mProperties(materialProperties);
			material.mNumAllocated(3);

			AIString propertyKey;
			ByteBuffer propertyBuffer;

			propertyKey = AIString.malloc(stack).data(stack.UTF8(AI_MATKEY_NAME));
			var materialName = AIString.malloc(stack).data(stack.UTF8(firstMaterial.name()));
			propertyBuffer = stack.malloc(AIMaterialProperty.SIZEOF)
				// property name aiString
				.put(memByteBuffer(propertyKey))
				// texture type
				.putInt(aiTextureType_NONE)
				// texture index
				.putInt(0)
				// data size
				.putInt(materialName.sizeof())
				// data type
				.putInt(aiPTI_String)
				// pointer to the data
				.position(AIMaterialProperty.MDATA) // Alignment
				.put(memByteBuffer(stack.pointers(materialName)));
			materialProperties.put(memAddress0(propertyBuffer));

			propertyKey = AIString.malloc(stack).data(stack.UTF8(_AI_MATKEY_TEXTURE_BASE));
//			var materialTextureUri = AIString.malloc(stack).data(stack.UTF8("hd_infernal_cape.png"));
			var materialTextureUri = AIString.malloc(stack).data(stack.UTF8("*0")); // first texture in scene->mTextures
			propertyBuffer = stack.malloc(AIMaterialProperty.SIZEOF)
				// property name aiString
				.put(memByteBuffer(propertyKey))
				// texture type
				.putInt(aiTextureType_BASE_COLOR)
				// texture index
				.putInt(0)
				// data size
				.putInt(materialTextureUri.sizeof())
				// data type
				.putInt(aiPTI_String)
				// pointer to the data
				.position(AIMaterialProperty.MDATA) // Alignment
				.put(memByteBuffer(stack.pointers(materialTextureUri)));
			materialProperties.put(memAddress0(propertyBuffer));

			propertyKey = AIString.malloc(stack).data(stack.UTF8(_AI_MATKEY_UVWSRC_BASE));
			var materialUvMap = stack.ints(0); // UV map index
			propertyBuffer = stack.malloc(AIMaterialProperty.SIZEOF)
				// property name aiString
				.put(memByteBuffer(propertyKey))
				// texture type
				.putInt(aiTextureType_BASE_COLOR)
				// texture index
				.putInt(0)
				// data size
				.putInt(Integer.BYTES)
				// data type
				.putInt(aiPTI_Integer)
				// pointer to the data
				.position(AIMaterialProperty.MDATA) // Alignment
				.put(memByteBuffer(stack.pointers(materialUvMap)));
			materialProperties.put(memAddress0(propertyBuffer));

//			propertyKey = AIString.malloc(stack).data(stack.UTF8(AI_MATKEY_COLOR_DIFFUSE));
//			var diffuseColor = memByteBuffer(stack.floats(.25f, 0, 1));
//			propertyBuffer = stack.malloc(AIMaterialProperty.SIZEOF)
//				// property name aiString
//				.put(memByteBuffer(propertyKey))
//				// texture type
//				.position(AIMaterialProperty.MSEMANTIC)
//				.putInt(aiTextureType_NONE)
//				// texture index
//				.putInt(0)
//				// data size
//				.putInt(diffuseColor.limit())
//				// data type
//				.putInt(aiPTI_Float)
//				// pointer to the data
//				.position(AIMaterialProperty.MDATA) // Alignment
//				.put(memByteBuffer(stack.pointers(diffuseColor)));
//			materialProperties.put(memAddress0(propertyBuffer));

//			propertyKey = AIString.malloc(stack).data(stack.UTF8(AI_MATKEY_ROUGHNESS_FACTOR));
//			var roughness = memByteBuffer(stack.floats(0));
//			propertyBuffer = stack.malloc(AIMaterialProperty.SIZEOF)
//				// property name aiString
//				.put(memByteBuffer(propertyKey))
//				// texture type
//				.position(AIMaterialProperty.MSEMANTIC)
//				.putInt(aiTextureType_NONE)
//				// texture index
//				.putInt(0)
//				// data size
//				.putInt(roughness.limit())
//				// data type
//				.putInt(aiPTI_Float)
//				// pointer to the data
//				.position(AIMaterialProperty.MDATA) // Alignment
//				.put(memByteBuffer(stack.pointers(roughness)));
//			materialProperties.put(memAddress0(propertyBuffer));

//			propertyKey = AIString.malloc(stack).data(stack.UTF8(AI_MATKEY_METALLIC_FACTOR));
//			var metallic = memByteBuffer(stack.floats(1));
//			propertyBuffer = stack.malloc(AIMaterialProperty.SIZEOF)
//				// property name aiString
//				.put(memByteBuffer(propertyKey))
//				// texture type
//				.position(AIMaterialProperty.MSEMANTIC)
//				.putInt(aiTextureType_NONE)
//				// texture index
//				.putInt(0)
//				// data size
//				.putInt(metallic.limit())
//				// data type
//				.putInt(aiPTI_Float)
//				// pointer to the data
//				.position(AIMaterialProperty.MDATA) // Alignment
//				.put(memByteBuffer(stack.pointers(metallic)));
//			materialProperties.put(memAddress0(propertyBuffer));

			if (firstMaterial != Material.NONE) {
				String textureName = firstMaterial == Material.NONE ? String.valueOf(vanillaTextureId) : firstMaterial.name().toLowerCase();
				BufferedImage textureImage = textureManager.loadTexture(firstMaterial, vanillaTextureId, client.getTextureProvider());
				if (textureImage == null) {
					log.error("Failed to load texture: {} ({})", firstMaterial, vanillaTextureId);
				} else {
					if (!textureManager.isVanillaTexture(textureImage)) {
						AffineTransform t = new AffineTransform();
						t.translate(textureImage.getWidth(), 0);
						t.scale(-1, 1);
						AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
						var orientedImage = new BufferedImage(
							textureImage.getWidth(),
							textureImage.getHeight(),
							BufferedImage.TYPE_INT_ARGB
						);
						op.filter(textureImage, orientedImage);
						textureImage = orientedImage;
					}

					var pixelData = memAllocInt(textureImage.getWidth() * textureImage.getHeight())
						.put(((DataBufferInt) textureImage.getRaster().getDataBuffer()).getData());
					cleanup.add(memAddress0(pixelData));
					log.debug("Loaded texture {} ({}) of size {}x{}",
						firstMaterial, vanillaTextureId, textureImage.getWidth(), textureImage.getHeight()
					);
					ByteBuffer textureEncoded;
					try (var os = new ByteArrayOutputStream()) {
						ImageIO.write(textureImage, "png", os);
						byte[] encoded = os.toByteArray();
						textureEncoded = memAlloc(encoded.length)
							.put(encoded)
							.flip();
						cleanup.add(memAddress0(textureEncoded));
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}

					var aiTexture = stack.calloc(AITexture.ALIGNOF, AITexture.SIZEOF)
						.putInt(textureEncoded.capacity())
						.putInt(0)
						.put(stack.ASCII("png"))
						.position(AITexture.PCDATA)
						.put(memByteBuffer(stack.pointers(memAddress0(textureEncoded))))
						.put(memByteBuffer(AIString.malloc(stack).data(stack.UTF8(textureName + ".png"))));
					var tex = AITexture.create(memAddress0(aiTexture));
					scene.mTextures(stack.pointers(memAddress0(aiTexture)));

					tex = AITexture.create(scene.mTextures().get());
					log.debug("aiTexture width: {}", tex.mWidth());
					log.debug("aiTexture height: {}", tex.mHeight());
					log.debug("aiTexture hint: {}", tex.achFormatHintString());
					log.debug("aiTexture filename: {}", tex.mFilename().dataString());
					log.debug("aiTexture data: {}", memAddress0(tex.pcDataCompressed()));
				}
			}

			int statusCode = aiExportScene(scene, formatId, exportPath.toPath().toString(),
				aiProcess_EmbedTextures
				| aiProcess_PreTransformVertices
				| aiProcess_DropNormals
//				| aiProcess_GenNormals
				| aiProcess_GenSmoothNormals
//				| aiProcess_FixInfacingNormals
//				| aiProcess_FlipWindingOrder
				| aiProcess_ValidateDataStructure
				| aiProcess_FindInvalidData
			);

			if (statusCode != 0) {
				System.err.println(aiGetErrorString());
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append("Error while exporting " + type + " model for ")
						.append(Color.CYAN, name)
						.append(Color.LIGHT_GRAY, " (status code " + statusCode + ")")
						.build())
					.build());
			} else {
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append("Exported model as ")
						.append(Color.CYAN, exportPath.getFilename())
						.build())
					.build());
			}
		} finally {
			cleanup.forEach(MemoryUtil::nmemFree);
		}
	}

	private void pushVanillaModel(
		Model model,
		AIFace.Buffer faces,
		IntBuffer indices,
		FloatBuffer vertices,
		FloatBuffer uvs,
		FloatBuffer normals,
		FloatBuffer colors
	) {
		final int triangleCount = model.getFaceCount();

		final int[] verticesX = model.getVerticesX();
		final int[] verticesY = model.getVerticesY();
		final int[] verticesZ = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final int[] color1s = model.getFaceColors1();
		final int[] color2s = model.getFaceColors2();
		final int[] color3s = model.getFaceColors3();

		final short[] faceTextures = model.getFaceTextures();

		final byte[] transparencies = model.getFaceTransparencies();

		final byte overrideAmount = model.getOverrideAmount();
		final byte overrideHue = model.getOverrideHue();
		final byte overrideSat = model.getOverrideSaturation();
		final byte overrideLum = model.getOverrideLuminance();

		for (int face = 0; face < triangleCount; ++face) {
			faces.get(face).mIndices(memSlice(indices, 0, 3));
			for (int i = 0; i < 3; i++)
				indices.put(face * 3 + i);

			int color1 = color1s[face];
			int color2 = color2s[face];
			int color3 = color3s[face];

			if (color3 == -1) {
				color2 = color3 = color1;
			} else if (color3 == -2) {
				vertices.put(ZEROS, 0, 9);
				colors.put(ZEROS, 0, 12);
				normals.put(ZEROS, 0, 9);
				if (faceTextures != null)
					uvs.put(ZEROS, 0, 9);
				continue;
			}

			// HSL override is not applied to textured faces
			if (faceTextures == null || faceTextures[face] == -1) {
				if (overrideAmount > 0) {
					color1 = interpolateHSL(color1, overrideHue, overrideSat, overrideLum, overrideAmount);
					color2 = interpolateHSL(color2, overrideHue, overrideSat, overrideLum, overrideAmount);
					color3 = interpolateHSL(color3, overrideHue, overrideSat, overrideLum, overrideAmount);
				}
			}

			int triA = indices1[face];
			int triB = indices2[face];
			int triC = indices3[face];

			vertices.put(verticesX[triA]).put(verticesY[triA]).put(verticesZ[triA]);
			vertices.put(verticesX[triB]).put(verticesY[triB]).put(verticesZ[triB]);
			vertices.put(verticesX[triC]).put(verticesY[triC]).put(verticesZ[triC]);

			float alpha = 1;
			if (transparencies != null)
				alpha -= (float) (transparencies[face] & 0xFF) / 0xFF;
			colors
				.put(ColorUtils.packedHslToSrgb(color1)).put(alpha)
				.put(ColorUtils.packedHslToSrgb(color2)).put(alpha)
				.put(ColorUtils.packedHslToSrgb(color3)).put(alpha);

			final int[] vertexNormalsX = model.getVertexNormalsX();
			final int[] vertexNormalsY = model.getVertexNormalsY();
			final int[] vertexNormalsZ = model.getVertexNormalsZ();
			normals
				.put(vertexNormalsX[triA]).put(vertexNormalsY[triA]).put(vertexNormalsZ[triA])
				.put(vertexNormalsX[triB]).put(vertexNormalsY[triB]).put(vertexNormalsZ[triB])
				.put(vertexNormalsX[triC]).put(vertexNormalsY[triC]).put(vertexNormalsZ[triC]);

			fillVanillaUvs(uvs, model, face);
		}
	}

	private void fillVanillaUvs(FloatBuffer uvs, Model model, int face) {
		final short[] faceTextures = model.getFaceTextures();
		final byte[] textureFaces = model.getTextureFaces();
		if (faceTextures == null || faceTextures[face] == -1) {
			uvs.put(ZEROS, 0, 9);
			return;
		}

		final int[] verticesX = model.getVerticesX();
		final int[] verticesY = model.getVerticesY();
		final int[] verticesZ = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final int[] texIndices1 = model.getTexIndices1();
		final int[] texIndices2 = model.getTexIndices2();
		final int[] texIndices3 = model.getTexIndices3();

		int triA = indices1[face];
		int triB = indices2[face];
		int triC = indices3[face];

		float u0, u1, u2, v0, v1, v2;

		if (textureFaces != null && textureFaces[face] != -1) {
			int tfaceIdx = textureFaces[face] & 0xff;
			int texA = texIndices1[tfaceIdx];
			int texB = texIndices2[tfaceIdx];
			int texC = texIndices3[tfaceIdx];

			// v1 = vertex[texA]
			float v1x = (float) verticesX[texA];
			float v1y = (float) verticesY[texA];
			float v1z = (float) verticesZ[texA];
			// v2 = vertex[texB] - v1
			float v2x = (float) verticesX[texB] - v1x;
			float v2y = (float) verticesY[texB] - v1y;
			float v2z = (float) verticesZ[texB] - v1z;
			// v3 = vertex[texC] - v1
			float v3x = (float) verticesX[texC] - v1x;
			float v3y = (float) verticesY[texC] - v1y;
			float v3z = (float) verticesZ[texC] - v1z;

			// v4 = vertex[triangleA] - v1
			float v4x = (float) verticesX[triA] - v1x;
			float v4y = (float) verticesY[triA] - v1y;
			float v4z = (float) verticesZ[triA] - v1z;
			// v5 = vertex[triangleB] - v1
			float v5x = (float) verticesX[triB] - v1x;
			float v5y = (float) verticesY[triB] - v1y;
			float v5z = (float) verticesZ[triB] - v1z;
			// v6 = vertex[triangleC] - v1
			float v6x = (float) verticesX[triC] - v1x;
			float v6y = (float) verticesY[triC] - v1y;
			float v6z = (float) verticesZ[triC] - v1z;

			// v7 = v2 x v3
			float v7x = v2y * v3z - v2z * v3y;
			float v7y = v2z * v3x - v2x * v3z;
			float v7z = v2x * v3y - v2y * v3x;

			// v8 = v3 x v7
			float v8x = v3y * v7z - v3z * v7y;
			float v8y = v3z * v7x - v3x * v7z;
			float v8z = v3x * v7y - v3y * v7x;

			// f = 1 / (v8 ⋅ v2)
			float f = 1.0F / (v8x * v2x + v8y * v2y + v8z * v2z);

			// u0 = (v8 ⋅ v4) * f
			u0 = (v8x * v4x + v8y * v4y + v8z * v4z) * f;
			// u1 = (v8 ⋅ v5) * f
			u1 = (v8x * v5x + v8y * v5y + v8z * v5z) * f;
			// u2 = (v8 ⋅ v6) * f
			u2 = (v8x * v6x + v8y * v6y + v8z * v6z) * f;

			// v8 = v2 x v7
			v8x = v2y * v7z - v2z * v7y;
			v8y = v2z * v7x - v2x * v7z;
			v8z = v2x * v7y - v2y * v7x;

			// f = 1 / (v8 ⋅ v3)
			f = 1.0F / (v8x * v3x + v8y * v3y + v8z * v3z);

			// v0 = (v8 ⋅ v4) * f
			v0 = (v8x * v4x + v8y * v4y + v8z * v4z) * f;
			// v1 = (v8 ⋅ v5) * f
			v1 = (v8x * v5x + v8y * v5y + v8z * v5z) * f;
			// v2 = (v8 ⋅ v6) * f
			v2 = (v8x * v6x + v8y * v6y + v8z * v6z) * f;
		} else {
			// Without a texture face, the client assigns tex = triangle, but the resulting
			// calculations can be reduced:
			//
			// v1 = vertex[texA]
			// v2 = vertex[texB] - v1
			// v3 = vertex[texC] - v1
			//
			// v4 = 0
			// v5 = v2
			// v6 = v3
			//
			// v7 = v2 x v3
			//
			// v8 = v3 x v7
			// u0 = (v8 . v4) / (v8 . v2) // 0 because v4 is 0
			// u1 = (v8 . v5) / (v8 . v2) // 1 because v5=v2
			// u2 = (v8 . v6) / (v8 . v2) // 0 because v8 is perpendicular to v3/v6
			//
			// v8 = v2 x v7
			// v0 = (v8 . v4) / (v8 ⋅ v3) // 0 because v4 is 0
			// v1 = (v8 . v5) / (v8 ⋅ v3) // 0 because v8 is perpendicular to v5/v2
			// v2 = (v8 . v6) / (v8 ⋅ v3) // 1 because v6=v3

			u0 = 0f;
			v0 = 0f;

			u1 = 1f;
			v1 = 0f;

			u2 = 0f;
			v2 = 1f;
		}

		uvs
			.put(u0).put(v0).put(0)
			.put(u1).put(v1).put(0)
			.put(u2).put(v2).put(0);
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort) {
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.isMenuOpen() || selectedObject == null)
			return;

		var renderable = selectedObject;
		String type = "model";
		String name = "Unknown";
		if (renderable instanceof TileObject) {
			var object = (TileObject) renderable;
			var def = client.getObjectDefinition(object.getId());
			type = "tile object";
			name = "ID " + def.getId();
			if (!def.getName().equals("null")) {
				name = def.getName() + " (" + name + ")";
			} else if (def.getImpostorIds() != null) {
				def = def.getImpostor();
				if (def != null && !def.getName().equals("null"))
					name = def.getName() + " (" + name + ")";
			}
		} else if (renderable instanceof NPC) {
			var npc = (NPC) renderable;
			var def = client.getNpcDefinition(npc.getId());
			type = "NPC";
			name = "ID " + def.getId();
			if (!def.getName().equals("null"))
				name = def.getName() + " (" + name + ")";
		} else if (renderable instanceof Player) {
			var player = (Player) renderable;
			type = "player";
			if (player.getName() != null)
				name = player.getName();
		} else if (renderable instanceof GraphicsObject) {
			var object = (GraphicsObject) renderable;
			type = "graphic";
			name = "ID " + object.getId();
		} else if (renderable instanceof Projectile) {
			var projectile = (Projectile) renderable;
			type = "projectile";
			name = "ID " + projectile.getId();
		}

		String finalType = type;
		String finalName = name;
		client.createMenuEntry(client.getMenuEntries().length)
			.setOption("Export " + type)
			.setTarget("<col=00ffff>" + name + "</col>")
			.setIdentifier(MenuAction.RUNELITE_HIGH_PRIORITY.getId())
			.onClick(e -> clientThread.invoke(() -> exportModel(finalType, finalName, renderable)));
	}

	@Subscribe
	public void onClientTick(ClientTick tick) {
		if (clientTickCount++ % 5 == 0)
			updateHoverSet();

		if (hoverSet.isEmpty()) {
			selectedObject = null;
			return;
		}

		int camX = client.getCameraX();
		int camY = client.getCameraY();
		int camZ = client.getCameraZ(); // up

		selectedIndex = HDUtils.clamp(selectedIndex, 0, Math.max(0, hoverSet.size() - 1));
		selectedObject = hoverSet.stream()
			.map(e -> {
				int x = 0, y = 0, z = 0;
				if (e instanceof Actor) {
					var a = (Actor) e;
					var lp = a.getLocalLocation();
					x = lp.getX();
					y = lp.getY();

					if (a instanceof NPC) {
						int size = client.getNpcDefinition(((NPC) a).getId()).getSize();
						var tileHeightPoint = new LocalPoint(
							size * LOCAL_HALF_TILE_SIZE - LOCAL_HALF_TILE_SIZE + x,
							size * LOCAL_HALF_TILE_SIZE - LOCAL_HALF_TILE_SIZE + z
						);
						z = getTileHeight(client, tileHeightPoint, client.getPlane());
					} else if (a instanceof Player) {
						z = getTileHeight(client, lp, client.getPlane());
					}
				} else if (e instanceof TileObject) {
					var o = (TileObject) e;
					x = o.getX();
					y = o.getY();
					z = o.getZ();
				} else if (e instanceof GraphicsObject) {
					var g = (GraphicsObject) e;
					LocalPoint lp = g.getLocation();
					x = lp.getX();
					y = lp.getY();
					z = g.getZ();
				} else if (e instanceof Projectile) {
					var p = (Projectile) e;
					x = (int) p.getX();
					y = (int) p.getY();
					z = (int) p.getZ();
				}
				x -= camX;
				y -= camY;
				z -= camZ;
				int dist = x * x + y * y + z * z;
				return Map.entry(dist, e);
			})
			.sorted(Comparator.comparingInt(Map.Entry::getKey))
			.skip(selectedIndex)
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
	}

	@Override
	public Dimension render(Graphics2D g) {
		if (selectedObject != null) {
			if (selectedObject instanceof NPC) {
				modelOutlineRenderer.drawOutline((NPC) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof Player) {
				modelOutlineRenderer.drawOutline((Player) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof TileObject) {
				modelOutlineRenderer.drawOutline((TileObject) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof GraphicsObject) {
				modelOutlineRenderer.drawOutline((GraphicsObject) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof Projectile) {
				Shape shape = getConvexHull((Projectile) selectedObject);
				if (shape != null) {
					g.setColor(OUTLINE_COLOR);
					g.setStroke(new BasicStroke(OUTLINE_WIDTH));
					g.draw(shape);
				}
			}
		}
		return null;
	}

	private void updateHoverSet() {
		hoverSet.clear();

		if (!client.isKeyPressed(EXPORTER_KEY))
			return;

		Point canvasPoint = client.getMouseCanvasPosition();
		if (canvasPoint.getX() == -1 && canvasPoint.getY() == -1)
			return;
		Point2D hoverPoint = new Point2D.Float(canvasPoint.getX(), canvasPoint.getY());

		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		for (int plane = tiles.length - 1; plane >= 0; plane--) {
			Tile[][] tilePlane = tiles[plane];
			for (Tile[] tileColumn : tilePlane) {
				for (Tile tile : tileColumn) {
					if (tile == null)
						continue;

					if (localToCanvas(client, tile.getLocalLocation(), tile.getRenderLevel()) == null)
						continue;

					processTile(hoverPoint, tile);
				}
			}
		}

		for (var npc : client.getNpcs())
			addIfHovered(hoverPoint, npc);
		for (var player : client.getPlayers())
			addIfHovered(hoverPoint, player);
		for (var object : client.getGraphicsObjects())
			addIfHovered(hoverPoint, object);
		for (var projectile : client.getProjectiles())
			addIfHovered(hoverPoint, projectile);
	}

	private void processTile(Point2D hoverPoint, Tile tile) {
		var bridge = tile.getBridge();
		if (bridge != null)
			processTile(hoverPoint, bridge);

		if (Perspective.getCanvasTilePoly(client, tile.getLocalLocation()) == null)
			return;

		for (GameObject gameObject : tile.getGameObjects())
			if (gameObject != null)
				addIfHovered(hoverPoint, gameObject);

		WallObject wallObject = tile.getWallObject();
		if (wallObject != null)
			addIfHovered(hoverPoint, wallObject);

		DecorativeObject decorativeObject = tile.getDecorativeObject();
		if (decorativeObject != null)
			addIfHovered(hoverPoint, decorativeObject);

		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
			addIfHovered(hoverPoint, groundObject);
	}

	private void addIfHovered(Point2D hoverPoint, Object object) {
		Shape shape = null;
		if (object instanceof Actor) {
			shape = ((Actor) object).getConvexHull();
		} else if (object instanceof TileObject) {
			shape = ((TileObject) object).getClickbox();
		} else if (object instanceof GraphicsObject) {
			shape = getConvexHull((GraphicsObject) object);
		} else if (object instanceof Projectile) {
			shape = getConvexHull((Projectile) object);
		}

		if (shape != null && shape.contains(hoverPoint))
			hoverSet.add(object);
	}

	private Shape getConvexHull(GraphicsObject o) {
		var model = o.getModel();
		int[] x2d = new int[model.getVerticesCount()];
		int[] y2d = new int[model.getVerticesCount()];

		Perspective.modelToCanvas(
			client,
			model.getVerticesCount(),
			o.getLocation().getX(),
			o.getLocation().getY(),
			o.getZ(),
			0,
			model.getVerticesX(),
			model.getVerticesZ(),
			model.getVerticesY(),
			x2d,
			y2d
		);

		return Jarvis.convexHull(x2d, y2d);
	}

	private Shape getConvexHull(Projectile p) {
		var model = p.getModel();
		int[] x2d = new int[model.getVerticesCount()];
		int[] y2d = new int[model.getVerticesCount()];

		Perspective.modelToCanvas(
			client,
			model.getVerticesCount(),
			(int) p.getX(),
			(int) p.getY(),
			(int) p.getZ(),
			0,
			model.getVerticesX(),
			model.getVerticesZ(),
			model.getVerticesY(),
			x2d,
			y2d
		);

		return Jarvis.convexHull(x2d, y2d);
	}

	private long getHash(Object object) {
		if (object instanceof Actor)
			return ((Actor) object).getHash();
		if (object instanceof GraphicsObject)
			return ((GraphicsObject) object).getHash();
		if (object instanceof Projectile)
			return ((Projectile) object).getHash();
		if (object instanceof GameObject)
			return ((GameObject) object).getHash();
		if (object instanceof WallObject)
			return ((WallObject) object).getHash();
		if (object instanceof DecorativeObject)
			return ((DecorativeObject) object).getHash();
		if (object instanceof GroundObject)
			return ((GroundObject) object).getHash();
		return 0;
	}

	private List<Renderable> resolveRenderables(Object object) {
		if (object instanceof Actor)
			return List.of(((Actor) object).getModel());
		if (object instanceof GraphicsObject)
			return List.of(((GraphicsObject) object).getModel());
		if (object instanceof Projectile)
			return List.of(((Projectile) object).getModel());
		if (object instanceof GameObject)
			return List.of(((GameObject) object).getRenderable());
		if (object instanceof WallObject) {
			var wallObject = (WallObject) object;
			return List.of(wallObject.getRenderable1(), wallObject.getRenderable2());
		}
		if (object instanceof DecorativeObject) {
			var decorativeObject = (DecorativeObject) object;
			return List.of(decorativeObject.getRenderable(), decorativeObject.getRenderable2());
		}
		if (object instanceof GroundObject)
			return List.of(((GroundObject) object).getRenderable());
		return List.of();
	}

	private List<Model> resolveModels(Object object) {
		return resolveRenderables(object).stream()
			.filter(Objects::nonNull)
			.map(r -> r instanceof Model ? (Model) r : r.getModel())
			.collect(Collectors.toList());
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event) {
		return event;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) {
		if (client.isKeyPressed(EXPORTER_KEY)) {
			selectedIndex -= Math.signum(event.getWheelRotation());
			event.consume();
		}
		return event;
	}

	@Override
	public void keyTyped(KeyEvent event) {}

	@Override
	public void keyPressed(KeyEvent event) {
		if (!client.isKeyPressed(EXPORTER_KEY))
			return;

		switch (event.getKeyCode()) {
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_DOWN:
				selectedIndex--;
				event.consume();
				break;
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
				selectedIndex++;
				event.consume();
				break;
		}
	}

	@Override
	public void keyReleased(KeyEvent event) {}
}
