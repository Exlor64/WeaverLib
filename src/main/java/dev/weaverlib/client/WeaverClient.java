package dev.weaverlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.weaverlib.WeaverLib;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = WeaverLib.ID, dist = Dist.CLIENT)
public final class WeaverClient {
    private final KeyMapping scribing =
        new KeyMapping("key.weaverlib.canvas", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.weaverlib");

    public WeaverClient(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(scribing);
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        while (scribing.consumeClick()) {
            if (scribing.isDown() && client.isWindowActive() && client.player != null
                    && client.level != null && client.screen == null) {
                client.setScreen(new RuneCanvas(scribing.getKey()));
            }
        }
    }
}
