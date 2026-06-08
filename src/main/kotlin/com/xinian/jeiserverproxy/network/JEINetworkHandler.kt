package com.xinian.jeiserverproxy.network

import com.xinian.jeiserverproxy.JEIServerProxy
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

class JEINetworkHandler(private val plugin: JEIServerProxy) : PluginMessageListener {

    companion object {
        private const val LEGACY_HANDSHAKE_PACKET_ID = 0
        private const val LEGACY_RECIPE_TRANSFER_PACKET_ID = 1
        private const val LEGACY_CREATE_ITEM_PACKET_ID = 2
        private const val LEGACY_CHEAT_PERMISSION_PACKET_ID = 8
        private const val LEGACY_PROTOCOL_VERSION = 19

        private const val MAX_LEGACY_STRING_BYTES = 32_767
        private const val MAX_TRANSFER_OPERATIONS = 256
        private const val MAX_SLOT_COUNT = 256
        private const val MAX_VAR_INT_BYTES = 5

        private val CHEAT_PERMISSION_HINTS = listOf("jei.chat.error.no.cheat.permission.op")
    }

    private val playerProtocolVersions = mutableMapOf<UUID, Int>()
    private val playerChannels = mutableMapOf<UUID, NamespacedKey>()
    private val unsupportedModernItemWarnings = mutableSetOf<String>()

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        when (channel) {
            plugin.legacyJeiNetworkKey.toString(), plugin.legacyReiNetworkKey.toString() -> {
                val channelKey = if (channel == plugin.legacyJeiNetworkKey.toString()) {
                    plugin.legacyJeiNetworkKey
                } else {
                    plugin.legacyReiNetworkKey
                }
                handleLegacyMainNetworkPacket(player, message, channelKey)
            }

            plugin.jeiRecipeTransferPacketKey.toString() -> handleModernRecipeTransfer(player, message)
            plugin.jeiRequestCheatPermissionPacketKey.toString() -> {
                sendCheatPermissionPacket(player, plugin.jeiCheatPermissionPacketKey)
            }

            plugin.jeiDeletePacketKey.toString(), plugin.reiDeletePacketKey.toString() -> {
                handleDeleteItemPacket(player)
            }

            plugin.jeiGiveItemStackPacketKey.toString(), plugin.jeiSetHotbarItemStackPacketKey.toString() -> {
                handleUnsupportedModernItemPacket(player, channel)
            }

            plugin.reiCreateItemPacketKey.toString() -> {
                handleSerializedCreateItemPacket(player, message)
            }
        }
    }

    private fun handleLegacyMainNetworkPacket(player: Player, message: ByteArray, channelKey: NamespacedKey) {
        try {
            val data = DataInputStream(ByteArrayInputStream(message))
            val packetId = data.readUnsignedByte()
            when (packetId) {
                LEGACY_HANDSHAKE_PACKET_ID -> handleClientHandshake(player, data, channelKey)
                LEGACY_RECIPE_TRANSFER_PACKET_ID -> handleLegacyRecipeTransfer(player, data)
                LEGACY_CREATE_ITEM_PACKET_ID -> {
                    val itemData = message.sliceArray(1 until message.size)
                    handleSerializedCreateItemPacket(player, itemData)
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to handle legacy JEI packet for ${player.name}: ${e.message}")
        }
    }

    private fun handleModernRecipeTransfer(player: Player, message: ByteArray) {
        val payload = try {
            ModernPayloadReader(message).readRecipeTransferPayload()
        } catch (e: Exception) {
            plugin.logger.warning("Failed to decode JEI recipe transfer from ${player.name}: ${e.message}")
            return
        }

        moveModernItems(player, payload)
    }

    private fun handleSerializedCreateItemPacket(player: Player, itemData: ByteArray) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!plugin.hasCheatPermission(player)) {
                plugin.logger.warning("Player ${player.name} tried to create an item via cheat mode without permission.")
                sendCheatPermissionPacket(player, plugin.jeiCheatPermissionPacketKey)
                return@Runnable
            }

            try {
                val itemStack = ItemStack.deserializeBytes(itemData)
                player.setItemOnCursor(itemStack)
                player.updateInventory()
                plugin.logger.info("Player ${player.name} created item on cursor via legacy cheat mode: ${itemStack.type}")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to create item for player ${player.name}: ${e.message}")
            }
        })
    }

    private fun handleUnsupportedModernItemPacket(player: Player, channel: String) {
        if (!plugin.hasCheatPermission(player)) {
            sendCheatPermissionPacket(player, plugin.jeiCheatPermissionPacketKey)
            return
        }

        if (unsupportedModernItemWarnings.add(channel)) {
            plugin.logger.warning(
                "Ignoring $channel cheat item packets. JEI 26.1.2 sends Minecraft ItemStack payloads that Bukkit plugins cannot safely decode without server internals."
            )
        }
    }

    private fun handleDeleteItemPacket(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (plugin.hasCheatPermission(player)) {
                player.setItemOnCursor(ItemStack(Material.AIR))
                player.updateInventory()
                plugin.logger.info("Player ${player.name} deleted item on cursor via cheat mode.")
            } else {
                plugin.logger.warning("Player ${player.name} tried to delete an item via cheat mode without permission.")
                sendCheatPermissionPacket(player, plugin.jeiCheatPermissionPacketKey)
            }
        })
    }

    private fun handleClientHandshake(player: Player, data: DataInputStream, channelKey: NamespacedKey) {
        val clientProtocolVersion = data.readInt()
        val modName = if (channelKey == plugin.legacyJeiNetworkKey) "JEI" else "REI"
        plugin.logger.info("Received legacy $modName handshake from ${player.name} (v$clientProtocolVersion).")
        playerProtocolVersions[player.uniqueId] = clientProtocolVersion
        playerChannels[player.uniqueId] = channelKey

        sendHandshake(player, channelKey)
        sendCheatPermissionPacket(player, channelKey)
    }

    fun sendHandshake(player: Player, channelKey: NamespacedKey? = null) {
        val key = channelKey ?: playerChannels[player.uniqueId] ?: plugin.legacyJeiNetworkKey
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeByte(LEGACY_HANDSHAKE_PACKET_ID)
        dos.writeInt(LEGACY_PROTOCOL_VERSION)

        if (player.isOnline) {
            player.sendPluginMessage(plugin, key.toString(), baos.toByteArray())
        }
    }

    fun sendCheatPermissionPacket(player: Player, channelKey: NamespacedKey = plugin.jeiCheatPermissionPacketKey) {
        if (!player.isOnline) {
            return
        }

        val payload = if (channelKey == plugin.jeiCheatPermissionPacketKey) {
            ModernPayloadWriter().apply {
                writeBoolean(plugin.hasCheatPermission(player))
                writeStringList(CHEAT_PERMISSION_HINTS)
            }.toByteArray()
        } else {
            ByteArrayOutputStream().use { baos ->
                DataOutputStream(baos).use { dos ->
                    dos.writeByte(LEGACY_CHEAT_PERMISSION_PACKET_ID)
                    dos.writeBoolean(plugin.hasCheatPermission(player))
                }
                baos.toByteArray()
            }
        }

        player.sendPluginMessage(plugin, channelKey.toString(), payload)
    }

    fun onPlayerQuit(player: Player) {
        playerProtocolVersions.remove(player.uniqueId)
        playerChannels.remove(player.uniqueId)
    }

    private fun handleLegacyRecipeTransfer(player: Player, data: DataInputStream) {
        readLegacyString(data)
        val craftingSlots = readLegacySlotMap(data)
        val inventorySlots = readLegacySlotMap(data)
        data.readBoolean()
        moveLegacyItems(player, craftingSlots, inventorySlots)
    }

    private fun moveLegacyItems(player: Player, crafting: Map<Int, Int>, inventory: Map<Int, Int>) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val openInventory = player.openInventory
            val craftingInventory = openInventory.topInventory

            crafting.keys.forEach { slotId ->
                if (slotId !in 0 until craftingInventory.size) {
                    return@forEach
                }

                val item = craftingInventory.getItem(slotId)
                if (item != null && !item.type.isAir) {
                    craftingInventory.setItem(slotId, null)
                    stowItems(openInventory, player, playerInventoryRawSlots(openInventory), listOf(item))
                }
            }

            inventory.forEach { (craftingSlot, playerInventorySlot) ->
                if (craftingSlot !in 0 until craftingInventory.size || playerInventorySlot !in 0 until player.inventory.size) {
                    return@forEach
                }

                val sourceItem = player.inventory.getItem(playerInventorySlot)
                if (sourceItem != null && !sourceItem.type.isAir && sourceItem.amount > 0) {
                    val existingItem = craftingInventory.getItem(craftingSlot)
                    if (existingItem == null || existingItem.type.isAir) {
                        val toMove = sourceItem.clone()
                        toMove.amount = 1
                        craftingInventory.setItem(craftingSlot, toMove)
                        decrementStack(player.inventory, playerInventorySlot, sourceItem)
                    } else if (existingItem.isSimilar(sourceItem) && existingItem.amount < existingItem.maxStackSize) {
                        existingItem.amount++
                        decrementStack(player.inventory, playerInventorySlot, sourceItem)
                    }
                }
            }
            player.updateInventory()
        })
    }

    private fun moveModernItems(player: Player, payload: RecipeTransferPayload) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val view = player.openInventory
            val slotCount = view.countSlots()
            val craftingSlots = payload.craftingSlots.distinct()
            val inventorySlots = payload.inventorySlots.distinct()
            val allowedSourceSlots = (craftingSlots + inventorySlots).toSet()

            if (!allSlotsValid(craftingSlots, slotCount) || !allSlotsValid(inventorySlots, slotCount)) {
                plugin.logger.warning("Ignoring JEI recipe transfer from ${player.name}: payload contains invalid slots.")
                return@Runnable
            }

            val requirements = payload.transferOperations.mapNotNull { operation ->
                if (operation.craftingSlot !in craftingSlots || operation.inventorySlot !in allowedSourceSlots) {
                    plugin.logger.warning("Ignoring JEI recipe transfer from ${player.name}: operation references unexpected slots.")
                    return@Runnable
                }

                val sourceItem = view.getItem(operation.inventorySlot)
                if (sourceItem == null || sourceItem.type.isAir) {
                    null
                } else {
                    val requiredItem = sourceItem.clone()
                    requiredItem.amount = 1
                    TransferRequirement(operation.inventorySlot, operation.craftingSlot, requiredItem)
                }
            }

            if (requirements.isEmpty()) {
                return@Runnable
            }

            clearCraftingSlots(view, player, craftingSlots, inventorySlots)

            var movedAny = false
            var setsMoved = 0
            do {
                val plannedMoves = planOneTransferSet(view, requirements, inventorySlots) ?: break
                executePlannedMoves(view, plannedMoves)
                movedAny = true
                setsMoved++
            } while (payload.maxTransfer && setsMoved < 64)

            if (!movedAny) {
                plugin.logger.warning("JEI recipe transfer from ${player.name} could not find matching source items.")
            }

            player.updateInventory()
        })
    }

    private fun clearCraftingSlots(
        view: InventoryView,
        player: Player,
        craftingSlots: List<Int>,
        inventorySlots: List<Int>
    ) {
        val clearedItems = craftingSlots.mapNotNull { slot ->
            val item = view.getItem(slot)
            if (item == null || item.type.isAir) {
                null
            } else {
                view.setItem(slot, null)
                item
            }
        }

        stowItems(view, player, inventorySlots, clearedItems)
    }

    private fun planOneTransferSet(
        view: InventoryView,
        requirements: List<TransferRequirement>,
        inventorySlots: List<Int>
    ): List<PlannedMove>? {
        val remainingBySlot = mutableMapOf<Int, Int>()
        val plannedDestinationAmounts = mutableMapOf<Int, Int>()
        val plannedMoves = mutableListOf<PlannedMove>()

        for (requirement in requirements) {
            val destinationItem = view.getItem(requirement.destinationSlot)
            val destinationAmount = plannedDestinationAmounts.getOrPut(requirement.destinationSlot) {
                if (destinationItem == null || destinationItem.type.isAir) 0 else destinationItem.amount
            }

            if (destinationItem != null &&
                !destinationItem.type.isAir &&
                !destinationItem.isSimilar(requirement.item)
            ) {
                return null
            }

            val destinationLimit = destinationItem?.maxStackSize ?: requirement.item.maxStackSize
            if (destinationAmount >= destinationLimit) {
                return null
            }

            val sourceSlot = findMatchingSourceSlot(
                view,
                requirement.preferredSourceSlot,
                inventorySlots,
                requirement.item,
                remainingBySlot
            ) ?: return null

            remainingBySlot[sourceSlot] = remainingBySlot.getValue(sourceSlot) - 1
            plannedDestinationAmounts[requirement.destinationSlot] = destinationAmount + 1
            plannedMoves.add(PlannedMove(sourceSlot, requirement.destinationSlot, requirement.item))
        }

        return plannedMoves
    }

    private fun findMatchingSourceSlot(
        view: InventoryView,
        preferredSlot: Int,
        inventorySlots: List<Int>,
        requiredItem: ItemStack,
        remainingBySlot: MutableMap<Int, Int>
    ): Int? {
        val candidateSlots = sequenceOf(preferredSlot)
            .plus(inventorySlots.asSequence())
            .distinct()

        for (slot in candidateSlots) {
            val sourceItem = view.getItem(slot) ?: continue
            if (sourceItem.type.isAir || !sourceItem.isSimilar(requiredItem)) {
                continue
            }

            val remaining = remainingBySlot.getOrPut(slot) { sourceItem.amount }
            if (remaining > 0) {
                return slot
            }
        }

        return null
    }

    private fun executePlannedMoves(view: InventoryView, moves: List<PlannedMove>) {
        val takenItems = moves.mapNotNull { move ->
            takeOneItem(view, move.sourceSlot)?.let { move.destinationSlot to it }
        }

        takenItems.forEach { (destinationSlot, item) ->
            putOneItem(view, destinationSlot, item)
        }
    }

    private fun takeOneItem(view: InventoryView, slot: Int): ItemStack? {
        val sourceItem = view.getItem(slot) ?: return null
        if (sourceItem.type.isAir || sourceItem.amount <= 0) {
            return null
        }

        val taken = sourceItem.clone()
        taken.amount = 1
        if (sourceItem.amount <= 1) {
            view.setItem(slot, null)
        } else {
            sourceItem.amount = sourceItem.amount - 1
        }
        return taken
    }

    private fun putOneItem(view: InventoryView, slot: Int, item: ItemStack): Boolean {
        val existingItem = view.getItem(slot)
        if (existingItem == null || existingItem.type.isAir) {
            view.setItem(slot, item)
            return true
        }

        if (existingItem.isSimilar(item) && existingItem.amount < existingItem.maxStackSize) {
            existingItem.amount = existingItem.amount + 1
            return true
        }

        return false
    }

    private fun stowItems(view: InventoryView, player: Player, inventorySlots: List<Int>, items: List<ItemStack>) {
        items.forEach { item ->
            var remainder = item.clone()
            remainder = stowIntoExistingStacks(view, inventorySlots, remainder)
            remainder = stowIntoEmptySlots(view, inventorySlots, remainder)
            if (!remainder.type.isAir && remainder.amount > 0) {
                val leftovers = player.inventory.addItem(remainder)
                leftovers.values.forEach { leftover ->
                    player.world.dropItemNaturally(player.location, leftover)
                }
            }
        }
    }

    private fun stowIntoExistingStacks(view: InventoryView, inventorySlots: List<Int>, item: ItemStack): ItemStack {
        var remainder = item
        for (slot in inventorySlots) {
            if (remainder.type.isAir || remainder.amount <= 0) {
                break
            }

            val existingItem = view.getItem(slot) ?: continue
            if (existingItem.type.isAir || !existingItem.isSimilar(remainder)) {
                continue
            }

            val available = existingItem.maxStackSize - existingItem.amount
            if (available <= 0) {
                continue
            }

            val moved = minOf(available, remainder.amount)
            existingItem.amount = existingItem.amount + moved
            remainder.amount = remainder.amount - moved
        }
        return remainder
    }

    private fun stowIntoEmptySlots(view: InventoryView, inventorySlots: List<Int>, item: ItemStack): ItemStack {
        var remainder = item
        for (slot in inventorySlots) {
            if (remainder.type.isAir || remainder.amount <= 0) {
                break
            }

            val existingItem = view.getItem(slot)
            if (existingItem != null && !existingItem.type.isAir) {
                continue
            }

            val moved = remainder.clone()
            moved.amount = minOf(remainder.amount, moved.maxStackSize)
            view.setItem(slot, moved)
            remainder.amount = remainder.amount - moved.amount
        }
        return remainder
    }

    private fun playerInventoryRawSlots(view: InventoryView): List<Int> {
        val firstPlayerSlot = view.topInventory.size
        return (firstPlayerSlot until view.countSlots()).toList()
    }

    private fun decrementStack(inventory: org.bukkit.inventory.Inventory, slot: Int, item: ItemStack) {
        if (item.amount <= 1) {
            inventory.setItem(slot, null)
        } else {
            item.amount = item.amount - 1
        }
    }

    private fun allSlotsValid(slots: List<Int>, slotCount: Int): Boolean {
        return slots.all { it in 0 until slotCount }
    }

    private fun readLegacyString(data: DataInputStream): String {
        val length = data.readInt()
        if (length < 0 || length > MAX_LEGACY_STRING_BYTES || length > data.available()) {
            throw IndexOutOfBoundsException("Invalid string length: $length")
        }
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readLegacySlotMap(data: DataInputStream): Map<Int, Int> {
        val size = data.readUnsignedByte()
        val map = mutableMapOf<Int, Int>()
        repeat(size) {
            map[data.readUnsignedByte()] = data.readUnsignedByte()
        }
        return map
    }

    private data class RecipeTransferPayload(
        val transferOperations: List<TransferOperation>,
        val craftingSlots: List<Int>,
        val inventorySlots: List<Int>,
        val maxTransfer: Boolean,
        val requireCompleteSets: Boolean
    )

    private data class TransferOperation(val inventorySlot: Int, val craftingSlot: Int)

    private data class TransferRequirement(
        val preferredSourceSlot: Int,
        val destinationSlot: Int,
        val item: ItemStack
    )

    private data class PlannedMove(
        val sourceSlot: Int,
        val destinationSlot: Int,
        val item: ItemStack
    )

    private class ModernPayloadReader(message: ByteArray) {
        private val data = DataInputStream(ByteArrayInputStream(message))

        fun readRecipeTransferPayload(): RecipeTransferPayload {
            val operations = readList(MAX_TRANSFER_OPERATIONS) {
                TransferOperation(readVarInt(), readVarInt())
            }
            val craftingSlots = readIntList()
            val inventorySlots = readIntList()
            val maxTransfer = readBoolean()
            val requireCompleteSets = readBoolean()
            return RecipeTransferPayload(operations, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets)
        }

        private fun readIntList(): List<Int> {
            return readList(MAX_SLOT_COUNT) { readVarInt() }
        }

        private fun <T> readList(maxSize: Int, readElement: () -> T): List<T> {
            val size = readVarInt()
            if (size < 0 || size > maxSize) {
                throw IndexOutOfBoundsException("Invalid list size: $size")
            }
            return List(size) { readElement() }
        }

        private fun readBoolean(): Boolean {
            return data.readUnsignedByte() != 0
        }

        private fun readVarInt(): Int {
            var value = 0
            var position = 0

            repeat(MAX_VAR_INT_BYTES) {
                val currentByte = data.readUnsignedByte()
                value = value or ((currentByte and 0x7F) shl position)
                if ((currentByte and 0x80) == 0) {
                    return value
                }
                position += 7
            }

            throw IllegalArgumentException("VarInt is too big")
        }
    }

    private class ModernPayloadWriter {
        private val output = ByteArrayOutputStream()

        fun writeBoolean(value: Boolean) {
            output.write(if (value) 1 else 0)
        }

        fun writeStringList(values: List<String>) {
            writeVarInt(values.size)
            values.forEach { writeString(it) }
        }

        fun toByteArray(): ByteArray {
            return output.toByteArray()
        }

        private fun writeString(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeVarInt(bytes.size)
            output.write(bytes)
        }

        private fun writeVarInt(value: Int) {
            var remaining = value
            while ((remaining and -0x80) != 0) {
                output.write((remaining and 0x7F) or 0x80)
                remaining = remaining ushr 7
            }
            output.write(remaining)
        }
    }
}
