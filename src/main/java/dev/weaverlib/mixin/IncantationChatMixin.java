package dev.weaverlib.mixin;

import dev.weaverlib.WeaverLib;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class IncantationChatMixin {
    // Route the filtered message here so vanilla still handles validation and spam accounting.
    @Redirect(method = "broadcastChatMessage", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage("
            + "Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/network/chat/ChatType$Bound;)V"))
    private void weaverlib$routeIncantation(PlayerList players, PlayerChatMessage chat,
            ServerPlayer caster, ChatType.Bound type) {
        if (!WeaverLib.handleIncantation(caster, chat, type)) {
            players.broadcastChatMessage(chat, caster, type);
        }
    }
}
