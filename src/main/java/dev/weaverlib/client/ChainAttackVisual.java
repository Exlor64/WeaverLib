package dev.weaverlib.client;

import dev.weaverlib.angel.Angels;
import dev.weaverlib.angel.RetributionCombat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Show the same physical lash in first person, where the player's body render layers are absent. */
final class ChainAttackVisual {
    private ChainAttackVisual() {}
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || !client.options.getCameraType().isFirstPerson() || !Angels.view(player).active()
                || !Angels.retribution(player) || !player.isAlive() || player.isInvisible()) return;
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double age = player.level().getGameTime() + partial - RetributionCombat.swing(player);
        if (age < 0 || age >= 12) return;
        var buffers = client.renderBuffers().bufferSource();
        ChainMesh.whip(e.getPoseStack(), buffers.getBuffer(ChainMesh.layer()),
            client.getEntityRenderDispatcher().getPackedLightCoords(player, partial),
            ChainMesh.wrist(player, partial).subtract(e.getCamera().getPosition()),
            Vec3.directionFromRotation(0, player.getViewYRot(partial)), age,
            player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1);
        buffers.endBatch(ChainMesh.layer());
    }
}
