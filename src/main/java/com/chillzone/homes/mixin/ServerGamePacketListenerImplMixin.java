package com.chillzone.homes.mixin;

import com.chillzone.homes.ui.SignInputManager;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observe completed vanilla sign edits AFTER Minecraft has processed them.
 *
 * Important: do not cancel handleSignUpdate at HEAD. Vanilla performs its
 * packet-thread handoff and sign validation inside this method. Cancelling it
 * before that point caused typed text to look like a cancelled input.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSignUpdate", at = @At("RETURN"))
    private void chillzonehomes$handleSignInputAfterVanilla(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        SignInputManager.handleUpdate(player, packet);
    }
}
