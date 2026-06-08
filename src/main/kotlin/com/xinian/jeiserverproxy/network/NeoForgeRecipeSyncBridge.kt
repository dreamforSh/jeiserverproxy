package com.xinian.jeiserverproxy.network

import com.xinian.jeiserverproxy.JEIServerProxy
import io.netty.buffer.Unpooled
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeType
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NeoForgeRecipeSyncBridge(private val plugin: JEIServerProxy) {

    private val recipeContentChannel = plugin.neoForgeRecipeContentKey.toString()
    private val pendingRecipeSyncs = ConcurrentHashMap.newKeySet<UUID>()
    @Volatile
    private var cachedRecipeContentPayload: ByteArray? = null
    private val vanillaRecipeTypes = linkedSetOf(
        RecipeType.CRAFTING,
        RecipeType.SMELTING,
        RecipeType.BLASTING,
        RecipeType.SMOKING,
        RecipeType.CAMPFIRE_COOKING,
        RecipeType.STONECUTTING,
        RecipeType.SMITHING
    )

    fun isRecipeContentChannel(channel: String): Boolean {
        return channel == recipeContentChannel
    }

    fun queueRecipeContentSync(player: Player) {
        if (!plugin.neoForgeRecipeSyncEnabled) {
            return
        }

        val playerId = player.uniqueId
        if (!pendingRecipeSyncs.add(playerId)) {
            return
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            pendingRecipeSyncs.remove(playerId)
            sendRecipeContent(player)
        })
    }

    fun sendRecipeContent(player: Player): Boolean {
        if (!plugin.neoForgeRecipeSyncEnabled || !player.isOnline || recipeContentChannel !in player.listeningPluginChannels) {
            return false
        }

        val payload = try {
            getOrCreateRecipeContentPayload()
        } catch (e: Exception) {
            plugin.logger.warning("Failed to encode NeoForge recipe content for ${player.name}: ${e.message}")
            return false
        }

        if (!CustomPayloadSender.send(player, recipeContentChannel, payload)) {
            return false
        }

        if (plugin.logRecipeSyncs) {
            plugin.logger.info("Sent NeoForge recipe content to ${player.name} (${payload.size} bytes).")
        }
        return true
    }

    fun onPlayerQuit(player: Player) {
        pendingRecipeSyncs.remove(player.uniqueId)
    }

    fun invalidateRecipeContentCache() {
        cachedRecipeContentPayload = null
    }

    private fun getOrCreateRecipeContentPayload(): ByteArray {
        cachedRecipeContentPayload?.let { return it }
        return synchronized(this) {
            cachedRecipeContentPayload ?: createRecipeContentPayload().also {
                cachedRecipeContentPayload = it
            }
        }
    }

    private fun createRecipeContentPayload(): ByteArray {
        val craftServer = plugin.server as org.bukkit.craftbukkit.CraftServer
        val minecraftServer = craftServer.server
        val recipeManager = minecraftServer.recipeManager
        val recipes = recipeManager.recipes.values()
            .filter { holder ->
                holder.value().type in vanillaRecipeTypes &&
                    plugin.isRecipeAllowed(holder.id().identifier().toString())
            }

        val byteBuf = Unpooled.buffer()
        val registryBuf = RegistryFriendlyByteBuf(byteBuf, minecraftServer.registryAccess())
        try {
            writeRecipeTypes(registryBuf)
            registryBuf.writeVarInt(recipes.size)
            recipes.forEach { holder ->
                RecipeHolder.STREAM_CODEC.encode(registryBuf, holder)
            }

            val payload = ByteArray(registryBuf.readableBytes())
            registryBuf.readBytes(payload)
            return payload
        } finally {
            byteBuf.release()
        }
    }

    private fun writeRecipeTypes(buffer: RegistryFriendlyByteBuf) {
        val recipeTypeRegistry = buffer.registryAccess().lookupOrThrow(Registries.RECIPE_TYPE)
        buffer.writeVarInt(vanillaRecipeTypes.size)
        vanillaRecipeTypes.forEach { recipeType ->
            val id = recipeTypeRegistry.getId(recipeType)
            if (id < 0) {
                throw IllegalStateException("Recipe type is not registered: $recipeType")
            }
            buffer.writeVarInt(id)
        }
    }
}
