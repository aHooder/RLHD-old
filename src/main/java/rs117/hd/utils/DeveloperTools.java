package rs117.hd.utils;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.overlay.OverlayManager;
import rs117.hd.HdPlugin;
import rs117.hd.data.environments.Area;
import rs117.hd.overlays.TileInfoOverlay;

@Slf4j
public class DeveloperTools implements KeyListener {
	// This could be part of the config if we had developer mode config sections
	private static final Keybind KEY_TOGGLE_TILE_INFO = new Keybind(KeyEvent.VK_F3, InputEvent.CTRL_DOWN_MASK);
	private static final ArrayList<Class<?>> externalDeveloperToolClasses = new ArrayList<>();
	private static final ArrayList<Object> externalDeveloperToolInstances = new ArrayList<>();

	public static void addTool(Class<?> clazz) {
		externalDeveloperToolClasses.add(clazz);
	}

	@Inject
	private HdPlugin plugin;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TileInfoOverlay tileInfoOverlay;

	private boolean tileInfoOverlayEnabled = false;

	public void activate() {
		keyManager.registerKeyListener(this);
		if (tileInfoOverlayEnabled) {
			overlayManager.add(tileInfoOverlay);
		}

		// Check for any out of bounds areas
		for (Area area : Area.values()) {
			if (area == Area.ALL || area == Area.NONE) {
				continue;
			}

			for (AABB aabb : area.aabbs) {
				if (aabb.minX < -128 || aabb.minY < 1000 || aabb.maxX > 5000 || aabb.maxY > 13000) {
					throw new IllegalArgumentException(
						"Your definition for the area " + area + " has an incorrect AABB: " + aabb);
				}
			}
		}

		for (var clazz : externalDeveloperToolClasses) {
			try {
				externalDeveloperToolInstances.add(plugin.getInjector().getInstance(clazz));
			} catch (Throwable ex) {
				log.error("Error while activating external dev tool:", ex);
			}
		}
	}

	public void deactivate() {
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(tileInfoOverlay);

		for (var instance : externalDeveloperToolInstances) {
			if (instance instanceof AutoCloseable) {
				try {
					((AutoCloseable) instance).close();
				} catch (Exception ex) {
					log.error("Error while deactivating external dev tool:", ex);
				}
			}
		}
		externalDeveloperToolInstances.clear();
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (KEY_TOGGLE_TILE_INFO.matches(event))
		{
			event.consume();
			tileInfoOverlayEnabled = !tileInfoOverlayEnabled;
			if (tileInfoOverlayEnabled)
			{
				overlayManager.add(tileInfoOverlay);
			}
			else
			{
				overlayManager.remove(tileInfoOverlay);
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent event)
	{

	}

	@Override
	public void keyTyped(KeyEvent event)
	{

	}
}
