package dev.weaverlib.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.leclowndu93150.thaumaturge.content.aura.BlockRechargePedestal;
import dev.weaverlib.CastInscription;
import dev.weaverlib.WeaverLib;
import dev.weaverlib.spell.Inscriptions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = WeaverLib.ID, dist = Dist.CLIENT)
public final class WeaverClient {
    private final KeyMapping scribing =
        new KeyMapping("key.weaverlib.canvas", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.weaverlib");

    private RuneCanvas canvas;

    public WeaverClient(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::useInscription);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(scribing);
    }

    private void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        canvas = null;
    }

    private void useInscription(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        if (!event.isUseItem() || client.player == null || client.level == null || client.player.isSpectator()
                || client.player.isShiftKeyDown()) {
            return;
        }
        var inscription = Inscriptions.get(client.player.getMainHandItem());
        if (inscription == null || (client.hitResult instanceof BlockHitResult hit
                && client.level.getBlockState(hit.getBlockPos()).getBlock() instanceof BlockRechargePedestal)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            PacketDistributor.sendToServer(new CastInscription(inscription.id()));
        }
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        while (scribing.consumeClick()) {
            if (scribing.isDown() && client.isWindowActive() && client.player != null
                    && client.level != null && client.screen == null) {
                if (canvas == null) {
                    canvas = new RuneCanvas(client.gameDirectory.toPath().resolve("weaverlib")
                        .resolve("rune-guides").resolve(client.player.getUUID() + ".txt"));
                }
                canvas.setCanvasKey(scribing.getKey());
                client.setScreen(canvas);
            }
        }
    }
}
