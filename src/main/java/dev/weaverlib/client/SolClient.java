package dev.weaverlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import dev.weaverlib.sol.Sol;
import dev.weaverlib.sol.SolAction;
import dev.weaverlib.sol.SolCombat;
import dev.weaverlib.sol.SolFlash;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

final class SolClient {
    private record Binding(KeyMapping key, SolAction action) {}

    private final List<Binding> bindings = List.of(
        bind("sol_combat", GLFW.GLFW_KEY_V, SolAction.CYCLE_MODE),
        bind("sol_sky", GLFW.GLFW_KEY_Z, SolAction.SKY_BEAM),
        bind("sol_hands", GLFW.GLFW_KEY_X, SolAction.HAND_BEAM),
        bind("sol_lance", GLFW.GLFW_KEY_B, SolAction.LANCE));
    private final SolVisuals visuals = new SolVisuals();

    SolClient(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::addLayers);
        NeoForge.EVENT_BUS.addListener(this::input);
        NeoForge.EVENT_BUS.addListener(this::hud);
        NeoForge.EVENT_BUS.addListener(this::hands);
        NeoForge.EVENT_BUS.addListener(visuals::render);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
        SolFlash.setClientReceiver(visuals::receive);
    }

    private static Binding bind(String key, int code, SolAction action) {
        return new Binding(new KeyMapping("key.weaverlib." + key, InputConstants.Type.KEYSYM, code,
            "key.categories.weaverlib"), action);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        for (var binding : bindings) {
            event.register(binding.key());
        }
    }

    private void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new SolWings(playerRenderer));
            }
        }
    }

    private void input(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        boolean ready = client.player != null && client.player.isAlive() && !client.player.isSpectator()
            && client.isWindowActive() && client.screen == null && client.getConnection() != null
            && SolCombat.view(client.player).unlocked();
        for (var binding : bindings) {
            while (binding.key().consumeClick()) {
                if (ready) {
                    PacketDistributor.sendToServer(binding.action());
                }
            }
        }
    }

    private void hud(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.isSpectator() || client.options.hideGui) {
            return;
        }
        var view = SolCombat.view(client.player);
        if (!view.unlocked()) {
            return;
        }

        var graphics = event.getGuiGraphics();
        Component label = switch (view.mode()) {
            case OFF -> Component.translatable("weaverlib.sol.hud", view.reserve());
            case RANGED -> Component.translatable("weaverlib.sol.hud.ranged", view.reserve());
            case MELEE -> Component.translatable("weaverlib.sol.hud.melee", view.reserve());
        };
        int width = Math.max(56, client.font.width(label));
        int x = graphics.guiWidth() - width - 9;
        graphics.fill(x - 4, 5, x + width + 4, 23, 0x9B21170E);
        graphics.drawString(client.font, label, x, 8, 0xFFFFE4A5);
        graphics.fill(x, 18, x + width, 20, 0xFF645C55);
        if (view.reserve() > 0) {
            int tint = SolMesh.mix(SolMesh.GOLD, 0xFFFFFF, view.reserve() / (float) Sol.MAX_RESERVE);
            graphics.fill(x, 18, x + width * view.reserve() / Sol.MAX_RESERVE, 20, 0xFF000000 | tint);
        }
    }

    private void hands(RenderHandEvent event) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || !SolCombat.view(player).handsTogether() || player.isInvisible()
                || !(client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }

        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        var poses = event.getPoseStack();
        for (int side : new int[] {-1, 1}) {
            poses.pushPose();
            poses.translate(side * -0.11F, -0.49F, -0.65F);
            poses.mulPose(Axis.ZP.rotationDegrees(-side * 24));
            poses.mulPose(Axis.XP.rotationDegrees(62));
            if (side < 0) {
                renderer.renderRightHand(poses, event.getMultiBufferSource(), event.getPackedLight(), player);
            } else {
                renderer.renderLeftHand(poses, event.getMultiBufferSource(), event.getPackedLight(), player);
            }
            poses.popPose();
        }
    }

    private void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        visuals.clear();
    }
}
