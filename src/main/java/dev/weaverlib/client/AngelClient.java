package dev.weaverlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.weaverlib.angel.AngelAction;
import dev.weaverlib.angel.AngelBurst;
import dev.weaverlib.angel.AngelEnergy;
import dev.weaverlib.angel.Angels;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

final class AngelClient {
    private final KeyMapping wheel = new KeyMapping("key.weaverlib.angel", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V, "key.categories.weaverlib");
    private final AngelVisuals visuals = new AngelVisuals();
    private boolean wasSprint;
    private boolean defending;
    private float guardPose;

    AngelClient(IEventBus bus) {
        bus.addListener(this::keys);
        bus.addListener(this::layers);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::click);
        NeoForge.EVENT_BUS.addListener(this::hud);
        NeoForge.EVENT_BUS.addListener(this::hands);
        NeoForge.EVENT_BUS.addListener(visuals::render);
        AngelBurst.receiver(visuals::receive);
    }

    private void keys(RegisterKeyMappingsEvent e) {
        e.register(wheel);
    }

    private void layers(EntityRenderersEvent.AddLayers e) {
        for (var skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new AngelWings(renderer));
        }
    }

    private void click(InputEvent.InteractionKeyMappingTriggered e) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (client.screen != null || player == null || !player.isAlive() || !Angels.view(player).active()
                || !player.getMainHandItem().isEmpty()) return;
        if (e.isAttack() || e.isUseItem()) {
            e.setCanceled(true);
            e.setSwingHand(false);
            if (e.isAttack()) send(AngelAction.ATTACK);
        }
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new AngelAction(action, 0));
    }

    private void tick(ClientTickEvent.Post e) {
        var client = Minecraft.getInstance();
        var player = client.player;
        boolean ready = player != null && player.isAlive() && !player.isSpectator()
            && client.getConnection() != null && client.isWindowActive();
        while (wheel.consumeClick()) {
            if (ready && client.screen == null && Angels.view(player).rank() > 0) {
                client.setScreen(new AngelWheel());
            } else if (client.screen instanceof AngelWheel) {
                client.setScreen(null);
            }
        }
        if (!ready) {
            defending = false;
            wasSprint = false;
            guardPose = 0;
            return;
        }
        boolean active = Angels.view(player).active();
        boolean guard = active && client.screen == null && player.getMainHandItem().isEmpty()
            && client.options.keyUse.isDown();
        if (guard && (!defending || player.tickCount % 4 == 0)) send(AngelAction.DEFEND);
        if (!guard && defending) send(AngelAction.RELEASE);
        defending = guard;
        float desiredGuard = Angels.view(player).defending() && !Angels.flying(player) ? 1 : 0;
        guardPose += Math.clamp(desiredGuard - guardPose, -.25F, .25F);
        boolean sprint = client.options.keySprint.isDown();
        if (active && client.screen == null && sprint && !wasSprint && !player.onGround()) {
            send(AngelAction.FLIGHT);
        }
        wasSprint = sprint;
        if (Angels.view(player).fast()) {
            player.getAbilities().flying = false;
            player.startFallFlying();
            Angels.accelerate(player, Angels.view(player).spin() > player.level().getGameTime());
        }
    }

    private void hands(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !Angels.view(player).active() || Angels.flying(player)
                || !player.getMainHandItem().isEmpty()) return;
        float attack = (float) ((player.level().getGameTime() + event.getPartialTick()
            - Angels.view(player).swing()) / 12.0);
        float sweep = attack >= 0 && attack < 1 ? (float) Math.sin(attack * Math.PI) : 0;
        if (guardPose <= 0 && sweep <= 0) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var poses = event.getPoseStack();
        for (int side = -1; side <= 1; side += 2) {
            poses.pushPose();
            boolean strikingWing = (Angels.view(player).wing() % 2 == 0 ? -1 : 1) == side;
            double strike = strikingWing && sweep > 0 ? attack : -1;
            // Use the same wrapped skeleton as third person; first-person coordinates have +Y up.
            poses.translate(0, -.04, -.08);
            poses.scale(side * .85F, -.85F, .85F);
            AngelWings.geometry(event.getMultiBufferSource().getBuffer(AngelMesh.layer()), poses,
                AngelWingMotion.sample(player.tickCount * .12, 0, 0, 0, guardPose, strike, 0, side));
            poses.popPose();
        }
    }

    private void hud(RenderGuiEvent.Post e) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || client.player.isSpectator()) return;
        var view = Angels.view(client.player);
        if (view.rank() == 0) return;
        var graphics = e.getGuiGraphics();
        int capacity = (int) AngelEnergy.capacity(view.rank());
        String mode = !view.active() ? "human" : view.fast() ? "fast" : "angel";
        var text = Component.translatable("weaverlib.angel.hud." + mode,
            String.format(java.util.Locale.ROOT, "%.1f", view.energy() / 100.0), capacity);
        int width = Math.max(90, client.font.width(text));
        int x = graphics.guiWidth() - width - 9;
        graphics.fill(x - 4, 5, x + width + 4, 24, 0xAC21170E);
        graphics.drawString(client.font, text, x, 8, 0xFFFFE4A5);
        graphics.fill(x, 19, x + width, 21, 0xFF645C55);
        graphics.fill(x, 19, x + width * view.energy() / (capacity * 100), 21, 0xFFFFCF71);
    }
}
