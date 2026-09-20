package dev.yinghuang.legacyforgebridge.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** In-game access independent of Mod Menu; the key remains rebindable in Controls. */
public final class LegacyBlockingPoseControls {
    private static boolean initialized;

    private LegacyBlockingPoseControls() { }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        KeyMapping open = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.legacyforgebridge.open_blocking_pose_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyMapping.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                client.setScreen(LegacyBlockingPoseConfigScreen.create(client.screen));
            }
        });
    }
}
