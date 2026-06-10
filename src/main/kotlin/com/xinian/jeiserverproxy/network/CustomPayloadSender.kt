package com.xinian.jeiserverproxy.network

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.DiscardedPayload
import net.minecraft.resources.Identifier
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player

object CustomPayloadSender {
    fun send(player: Player, channel: String, payload: ByteArray, requireListeningChannel: Boolean = true): Boolean {
        if (!player.isOnline) {
            return false
        }
        if (requireListeningChannel && channel !in player.listeningPluginChannels) {
            return false
        }

        val craftPlayer = player as? CraftPlayer ?: return false
        val connection = craftPlayer.handle.connection
        connection.send(
            ClientboundCustomPayloadPacket(
                DiscardedPayload(Identifier.parse(channel), payload)
            )
        )
        return true
    }
}
