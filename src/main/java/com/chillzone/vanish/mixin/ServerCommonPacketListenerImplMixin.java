package com.chillzone.vanish.mixin;

import com.chillzone.vanish.ChillZoneVanish;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites only outgoing TAB header/footer packets. This lets the existing TAB
 * system keep ownership of its design while Chill Zone Vanish substitutes the
 * visible-player count whenever the text contains "Players: X/Y".
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @ModifyVariable(
        method = "send(Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Packet<?> chillzonevanish$rewriteTabCount(Packet<?> packet) {
        if (packet instanceof ClientboundTabListPacket tabPacket
            && (Object) this instanceof ServerGamePacketListenerImpl gameListener) {
            return ChillZoneVanish.rewriteTabListPacket(gameListener.player, tabPacket);
        }
        return packet;
    }
}
