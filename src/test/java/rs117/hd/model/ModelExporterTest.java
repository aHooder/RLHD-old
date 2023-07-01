package rs117.hd.model;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.util.Arrays;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.slf4j.LoggerFactory;
import rs117.hd.HdPlugin;
import rs117.hd.utils.DeveloperTools;
import rs117.hd.utils.ModelHash;

@SuppressWarnings("unchecked")
@Slf4j
public class ModelExporterTest {
	public static void main(String[] args) throws Exception {
		args = new String[] {
			"--developer-mode",
			"--disable-telemetry"
		};

		DeveloperTools.addTool(ModelExporter.class);
		DeveloperTools.addTool(ModelExporterTest.class);
		ExternalPluginManager.loadBuiltin(HdPlugin.class);
		RuneLite.main(args);
		((Logger) LoggerFactory.getLogger(ModelExporterTest.class)).setLevel(Level.DEBUG);
		((Logger) LoggerFactory.getLogger(ModelPusher.class)).setLevel(Level.DEBUG);
		((Logger) LoggerFactory.getLogger(ModelExporter.class)).setLevel(Level.DEBUG);
	}

	@Inject
	public ModelExporterTest(
		Client client,
		ClientThread clientThread,
		ModelExporter modelExporter
	) {
		clientThread.invoke(() -> {
			try {
				var composition = client.getObjectDefinition(9541);

				var renderable = new Renderable() {
					@Override
					public long getHash() {
						return ModelHash.packUuid(composition.getId(), ModelHash.TYPE_OBJECT);
					}

					@Override
					public Model getModel() {
						int objectId = ModelHash.getIdOrIndex(getHash());
						int[] modelIds = {};

						Class<?> clazz = composition.getClass();
						outer:
						while (clazz != null) {
							for (var field : clazz.getDeclaredFields()) {
								try {
									if (field.getType().equals(int[].class)) {
										field.setAccessible(true);
										var ids = (int[]) field.get(composition);
//										System.out.println("Values of int[]: " + Arrays.toString(ids));
										if (ids != null) {
											modelIds = ids;
											break outer;
										}
									}
								} catch (IllegalAccessException ignored) {
								}
							}

							clazz = clazz.getSuperclass();
						}

						log.debug("Exporting object {} (ID {}) with models IDs: {}", composition.getName(), objectId, modelIds);
						var merged = client.mergeModels(Arrays
							.stream(modelIds)
							.mapToObj(client::loadModelData)
							.toArray(ModelData[]::new));
						var model = merged.light();
						assert model != null;
						return model;
					}

					public Node getNext() {return null;}

					public Node getPrevious() {return null;}

					public int getModelHeight() {return 0;}

					public void setModelHeight(int i) {}

					public void draw(int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7, long l) {}
				};

				modelExporter.exportModel(Map.entry("model", composition.getName() + "_" + composition.getId()), renderable);

			} catch (Exception ex) {
				// delay until the client has been initialized
				return false;
			} finally {
				System.exit(0);
			}

			return true;
		});
	}
}
