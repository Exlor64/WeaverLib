package dev.weaverlib.client;

import dev.weaverlib.angel.Angels;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/** One visual frame shared by the body, wings and surrounding light during mode changes. */
public final class AngelFlightPose {
    public record Pose(double blend, float yaw, float tilt, double roll) {
        public AngelFlightFrame frame() {
            return AngelFlightFrame.of(yaw, -90 - tilt, roll);
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static Object world;

    private static final class State {
        AngelFlightTransition transition;
        double lastTime, roll;
        State(boolean fast, double now) {
            transition = AngelFlightTransition.steady(fast, now);
            lastTime = now;
        }
    }

    private AngelFlightPose() {}

    public static Pose sample(AbstractClientPlayer player, float partial) {
        if (world != player.level()) {
            STATES.clear();
            world = player.level();
        }
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        var view = Angels.view(player);
        if (!view.active() || !player.isAlive()) {
            STATES.remove(player.getUUID());
            return new Pose(0, bodyYaw, 0, 0);
        }
        double now = player.level().getGameTime() + partial;
        if (STATES.size() > 128) STATES.clear();
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State(view.fast(), now));
        state.transition = state.transition.toward(view.fast(), now);
        double blend = state.transition.value(now);
        long started = Angels.dashStarted(player);
        state.roll = view.fast() && view.spin() > now && started >= 0
            ? AngelFlightFrame.channelRoll(now - started)
            : AngelFlightMath.settleRoll(state.roll, Math.clamp(now - state.lastTime, 0, 5));
        if (Math.abs(state.roll) < .001) state.roll = 0;
        state.lastTime = Math.max(state.lastTime, now);
        float yaw = Mth.rotLerp((float) blend, bodyYaw, player.getViewYRot(partial));
        return new Pose(blend, yaw, (float) (blend * (-90 - player.getViewXRot(partial))), state.roll);
    }
}
