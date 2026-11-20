package dev.weaverlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.weaverlib.angel.AngelAction;
import dev.weaverlib.angel.AngelBurst;
import dev.weaverlib.angel.AngelEnergy;
import dev.weaverlib.angel.PyraCharge;
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
    private boolean dashing;
    private float guardPose;

    AngelClient(IEventBus bus) {
        bus.addListener(this::keys);
        bus.addListener(this::layers);
        bus.addListener(PyraFire::shaders);
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
            if (e.isAttack() && !Angels.view(player).fast()) send(AngelAction.ATTACK);
        }
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new AngelAction(action, 0));
    }

    private void tick(ClientTickEvent.Post e) {
        var client = Minecraft.getInstance();
        PyraTrails.tick(client.level);
        PyraSlashes.tick(client.level);
        PyraWingFire.tick(client.level);
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
            if (client.getConnection() != null) {
                if (dashing) send(AngelAction.STOP_DASH);
                if (defending) send(AngelAction.CANCEL);
            }
            dashing = false;
            defending = false;
            wasSprint = false;
            guardPose = 0;
            return;
        }
        boolean active = Angels.view(player).active();
        boolean guard = active && client.screen == null && player.getMainHandItem().isEmpty()
            && client.options.keyUse.isDown();
        if (guard && (!defending || player.tickCount % 4 == 0)) send(AngelAction.DEFEND);
        if (!guard && defending) {
            boolean released = active && client.screen == null && player.getMainHandItem().isEmpty()
                && !player.isUsingItem() && !client.options.keyUse.isDown();
            send(released ? AngelAction.RELEASE : AngelAction.CANCEL);
        }
        defending = guard;
        boolean dash = active && Angels.view(player).fast() && client.screen == null
            && player.getMainHandItem().isEmpty() && !player.isUsingItem() && client.options.keyAttack.isDown();
        if (dash && (!dashing || player.tickCount % 4 == 0)) send(AngelAction.DASH);
        if (!dash && dashing) send(AngelAction.STOP_DASH);
        dashing = dash;
        float desiredGuard = Angels.view(player).defending() && !Angels.flying(player) && !Angels.inferno(player) ? 1 : 0;
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
        if (player == null || !Angels.view(player).active()
                || !player.getMainHandItem().isEmpty()) return;
        boolean aerialPyra = Angels.inferno(player) && player.getAbilities().flying && !Angels.view(player).fast();
        if (Angels.flying(player) && !aerialPyra) return;
        float attack = (float) ((player.level().getGameTime() + event.getPartialTick()
            - Angels.view(player).swing()) / 12.0);
        float sweep = attack >= 0 && attack < 1 ? (float) Math.sin(attack * Math.PI) : 0;
        if (Angels.inferno(player) && Angels.charge(player).active()) {
            event.setCanceled(true);
            if (!aerialPyra && event.getHand() == InteractionHand.MAIN_HAND) chargeHands(event, player);
            return;
        }
        if (guardPose <= 0 && sweep <= 0) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var poses = event.getPoseStack();
        boolean fire = Angels.inferno(player);
        for (int pass = 0; pass < (fire ? 3 : 2); pass++) {
            var buffer = event.getMultiBufferSource().getBuffer(pass == 0 ? AngelMesh.wingLayer()
                : pass == 1 ? AngelMesh.layer() : PyraFire.layer());
            for (int side = -1; side <= 1; side += 2) {
                poses.pushPose();
                boolean strikingWing = (Angels.view(player).wing() % 2 == 0 ? -1 : 1) == side;
                double strike = strikingWing && sweep > 0 ? attack : -1;
                // Match the depth-writing vane, glow and fire passes used in third person.
                poses.translate(0, -.04, -.08);
                poses.scale(side * .85F, -.85F, .85F);
                var formation = Angels.formation(player);
                double time = player.level().getGameTime() + event.getPartialTick();
                int pair = fire && sweep > 0 ? Angels.view(player).wing() / 2 : 0;
                long pattern = AngelFeathers.seed(player.getUUID().getMostSignificantBits(),
                    player.getUUID().getLeastSignificantBits(), pair, side);
                double growth = formation.started() < 0 ? 1 : AngelWingMotion.birthProgress(time - formation.started(), pair);
                var joints = AngelWingMotion.sample(player.tickCount * .12, 0, 0, 0, guardPose, strike, pair, side);
                if (aerialPyra && strike >= 0) joints = PyraWingMotion.slash(joints, strike);
                joints = AngelWingMotion.growing(joints, growth);
                if (pass == 0) AngelWings.geometry(buffer, poses, joints, fire);
                else if (pass == 1) AngelWings.glowGeometry(buffer, poses, joints, fire);
                else {
                    PyraWings.geometry(buffer, poses, joints, time, pattern);
                    PyraWingFire.capture(player, poses, joints, pattern, pair, side, true);
                    if (strike >= 0) PyraSlashes.sample(player, poses, joints, time, strike);
                }
                poses.popPose();
            }
        }
    }

    private static void chargeHands(RenderHandEvent event, net.minecraft.client.player.AbstractClientPlayer player) {
        var client = Minecraft.getInstance();
        if (!(client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        var pose = event.getPoseStack();
        double radius = PyraCharge.orbRadius(Angels.charge(player).energy());
        double strain = radius / (.3 + radius);
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * (.24 + strain * .12), -.46 - strain * .08, -.38);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-80));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * -18));
            pose.scale(.85F,.85F,.85F);
            if (side < 0) renderer.renderLeftHand(pose,event.getMultiBufferSource(),event.getPackedLight(),player);
            else renderer.renderRightHand(pose,event.getMultiBufferSource(),event.getPackedLight(),player);
            pose.popPose();
        }
    }

    private void hud(RenderGuiEvent.Post e) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || client.player.isSpectator()) return;
        var view = Angels.view(client.player);
        if (view.rank() == 0) return;
        var graphics = e.getGuiGraphics();
        int capacity = (int) AngelEnergy.capacity(view.rank());
        String mode = !view.active() ? "human" : Angels.inferno(client.player)
            ? view.fast() ? "pyra_fast" : "pyra" : view.fast() ? "fast" : "angel";
        var text = Component.translatable("weaverlib.angel.hud." + mode,
            String.format(java.util.Locale.ROOT, "%.1f", view.energy() / 100.0), capacity);
        int width = Math.max(90, client.font.width(text));
        int x = graphics.guiWidth() - width - 9;
        graphics.fill(x - 4, 5, x + width + 4, 24, 0xAC21170E);
        graphics.drawString(client.font, text, x, 8, 0xFFFFE4A5);
        graphics.fill(x, 19, x + width, 21, 0xFF645C55);
        graphics.fill(x, 19, x + width * view.energy() / (capacity * 100), 21, 0xFFFFCF71);
        var charge = Angels.charge(client.player);
        if (Angels.inferno(client.player) && charge.active()) {
            var label = Component.translatable("weaverlib.angel.pyra_charge",
                String.format(java.util.Locale.ROOT, "%.1f", charge.energy()));
            int chargeWidth = Math.max(width, client.font.width(label));
            int cx = graphics.guiWidth() - chargeWidth - 9;
            graphics.fill(cx - 4, 26, cx + chargeWidth + 4, 46, 0xC4310C0C);
            graphics.drawString(client.font,label,cx,29,0xFFFFAA65);
            graphics.fill(cx,41,cx + chargeWidth,43,0xFF5D2323);
            graphics.fill(cx,41,cx + (int)(chargeWidth * (1 - 1 / (1 + charge.energy() / 100))),43,0xFFF86024);
        }
    }
}
