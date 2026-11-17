package dev.weaverlib.client;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.weaverlib.WeaverLib;
import dev.weaverlib.compat.SoulIntegrity;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

final class SoulVision {
    private static final ResourceLocation EFFECT =
        ResourceLocation.fromNamespaceAndPath(WeaverLib.ID, "shaders/post/soul_vision.json");
    private static final System.Logger LOGGER = System.getLogger(SoulVision.class.getName());
    private PostChain chain;
    private boolean unavailable;
    private int width;
    private int height;

    void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> reset());
    }

    void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private void reset() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        unavailable = false;
    }

    void render(RenderGuiEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || unavailable) {
            return;
        }
        int soul = SoulIntegrity.percent(client.player);
        if (soul == 100) {
            return;
        }
        var target = client.getMainRenderTarget();
        if (chain == null) {
            try {
                chain = new PostChain(client.getTextureManager(), client.getResourceManager(), target, EFFECT);
            } catch (IOException | JsonSyntaxException error) {
                unavailable = true;
                LOGGER.log(System.Logger.Level.WARNING, "Could not load the soul vision shader", error);
                return;
            }
            width = 0;
            height = 0;
        }
        if (width != target.width || height != target.height) {
            width = target.width;
            height = target.height;
            chain.resize(width, height);
        }
        event.getGuiGraphics().flush();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.setUniform("Saturation", soul / 100F);
        try {
            chain.process(0);
        } finally {
            target.bindWrite(true);
            RenderSystem.enableDepthTest();
        }
    }
}
