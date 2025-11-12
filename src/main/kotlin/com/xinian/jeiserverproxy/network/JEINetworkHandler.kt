package com.xinian.jeiserverproxy.network

import com.xinian.jeiserverproxy.JEIServerProxy
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

class JEINetworkHandler(private val plugin: JEIServerProxy) : PluginMessageListener {

    companion object {
        private const val HANDSHAKE_PACKET_ID = 0
        private const val RECIPE_TRANSFER_PACKET_ID = 1
        private const val CHEAT_PERMISSION_PACKET_ID = 8
        private const val LATEST_PROTOCOL_VERSION = 19
    }

    private val playerProtocolVersions = mutableMapOf<UUID, Int>()
    private val playerChannels = mutableMapOf<UUID, NamespacedKey>()

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        when (channel) {
            plugin.jeiNetworkKey.toString(), plugin.reiNetworkKey.toString() -> {
                val channelKey = if (channel == plugin.jeiNetworkKey.toString()) plugin.jeiNetworkKey else plugin.reiNetworkKey
                handleNetworkPacket(player, message, channelKey)
            }
            plugin.jeiDeletePacketKey.toString(), plugin.reiDeletePacketKey.toString() -> {
                handleDeleteItemPacket(player)
            }
        }
    }

    private fun handleNetworkPacket(player: Player, message: ByteArray, channelKey: NamespacedKey) {
        try {
            val data = DataInputStream(ByteArrayInputStream(message))
            when (data.readByte().toInt()) {
                HANDSHAKE_PACKET_ID -> handleClientHandshake(player, data, channelKey)
                RECIPE_TRANSFER_PACKET_ID -> handleRecipeTransfer(player, data)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to handle network packet for player ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleDeleteItemPacket(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOp) {
                player.setItemOnCursor(ItemStack(Material.AIR))
                plugin.logger.info("Player ${player.name} deleted item on cursor via cheat mode.")
            } else {
                plugin.logger.warning("Player ${player.name} tried to delete item via cheat without permission.")
            }
        })
    }

    private fun handleClientHandshake(player: Player, data: DataInputStream, channelKey: NamespacedKey) {
        val clientProtocolVersion = data.readInt()
        val modName = if (channelKey == plugin.jeiNetworkKey) "JEI" else "REI"
        plugin.logger.info("Received $modName handshake from ${player.name} (v$clientProtocolVersion). Responding to complete handshake.")
        playerProtocolVersions[player.uniqueId] = clientProtocolVersion
        playerChannels[player.uniqueId] = channelKey

        sendHandshake(player, channelKey)
        sendCheatPermissionPacket(player, channelKey)
    }

    fun sendHandshake(player: Player, channelKey: NamespacedKey? = null) {
        val key = channelKey ?: playerChannels[player.uniqueId] ?: plugin.jeiNetworkKey
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeByte(HANDSHAKE_PACKET_ID)
        dos.writeInt(LATEST_PROTOCOL_VERSION)

        if (player.isOnline) {
            player.sendPluginMessage(plugin, key.toString(), baos.toByteArray())
        }
    }

    fun sendCheatPermissionPacket(player: Player, channelKey: NamespacedKey? = null) {
        val key = channelKey ?: playerChannels[player.uniqueId] ?: plugin.jeiNetworkKey
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeByte(CHEAT_PERMISSION_PACKET_ID)
        dos.writeBoolean(true)

        if (player.isOnline) {
            player.sendPluginMessage(plugin, key.toString(), baos.toByteArray())
        }
    }

    fun onPlayerQuit(player: Player) {
        playerProtocolVersions.remove(player.uniqueId)
        playerChannels.remove(player.uniqueId)
    }

    private fun handleRecipeTransfer(player: Player, data: DataInputStream) {
        readString(data)
        val craftingSlots = readSlotMap(data)
        val inventorySlots = readSlotMap(data)
        data.readBoolean()
        moveItems(player, craftingSlots, inventorySlots)
    }

    private fun moveItems(player: Player, crafting: Map<Int, Int>, inventory: Map<Int, Int>) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val openInventory = player.openInventory
            val craftingInventory = openInventory.topInventory

            crafting.keys.forEach { slotId ->
                val item = craftingInventory.getItem(slotId)
                if (item != null) {
                    craftingInventory.setItem(slotId, null)
                    player.inventory.addItem(item)
                }
            }

            inventory.forEach { (craftingSlot, playerInventorySlot) ->
                val sourceItem = player.inventory.getItem(playerInventorySlot)
                if (sourceItem != null && sourceItem.amount > 0) {
                    val existingItem = craftingInventory.getItem(craftingSlot)
                    if (existingItem == null || existingItem.type.isAir) {
                        val toMove = sourceItem.clone()
                        toMove.amount = 1
                        craftingInventory.setItem(craftingSlot, toMove)
                        sourceItem.amount--
                    } else if (existingItem.isSimilar(sourceItem) && existingItem.amount < existingItem.maxStackSize) {
                        existingItem.amount++
                        sourceItem.amount--
                    }
                }
            }
            player.updateInventory()
        })
    }

    private fun readString(data: DataInputStream): String {
        val length = data.readInt()
        if (length < 0) throw IndexOutOfBoundsException("Invalid string length: $length")
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    
    private fun readSlotMap(data: DataInputStream): Map<Int, Int> {
        val size = data.readByte().toInt()
        val map = mutableMapOf<Int, Int>()
        repeat(size) {
            map[data.readByte().toInt()] = data.readByte().toInt()
        }
        return map
    }
}
