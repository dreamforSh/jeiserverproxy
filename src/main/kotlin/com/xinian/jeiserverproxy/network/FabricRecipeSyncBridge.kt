package com.xinian.jeiserverproxy.network

import com.xinian.jeiserverproxy.JEIServerProxy
import io.netty.buffer.Unpooled
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FabricRecipeSyncBridge(private val plugin: JEIServerProxy) : PluginMessageListener {

    private val recipeSyncChannel = plugin.fabricRecipeSyncKey.toString()
    private val supportedSerializersChannel = plugin.fabricSupportedRecipeSerializersKey.toString()
    private val pendingRecipeSyncs = ConcurrentHashMap.newKeySet<UUID>()
    private val supportedSerializersByPlayer = ConcurrentHashMap<UUID, Set<Identifier>>()

    @Volatile
    private var cachedFullRecipeSyncPayload: EncodedRecipeSyncPayload? = null

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel == supportedSerializersChannel) {
            readSupportedSerializers(player, message)
            queueRecipeSync(player)
        }
    }

    fun isRecipeSyncChannel(channel: String): Boolean {
        return channel == recipeSyncChannel
    }

    fun queueRecipeSync(player: Player) {
        if (!plugin.fabricRecipeSyncEnabled) {
            return
        }

        val playerId = player.uniqueId
        if (!pendingRecipeSyncs.add(playerId)) {
            return
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            pendingRecipeSyncs.remove(playerId)
            sendRecipeSync(player)
        })
    }

    fun sendRecipeSync(player: Player): Boolean {
        if (!plugin.fabricRecipeSyncEnabled || !player.isOnline) {
            return false
        }

        val supportedSerializers = supportedSerializersByPlayer[player.uniqueId]
        val payload = try {
            if (supportedSerializers == null) {
                getOrCreateFullRecipeSyncPayload()
            } else {
                createRecipeSyncPayload(supportedSerializers)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to encode Fabric recipe sync for ${player.name}: ${e.message}")
            return false
        }

        if (payload.recipeCount == 0) {
            if (plugin.logRecipeSyncs) {
                plugin.logger.info("Skipped Fabric recipe sync for ${player.name}: no supported recipes to send.")
            }
            return false
        }

        if (!CustomPayloadSender.send(player, recipeSyncChannel, payload.bytes, requireListeningChannel = false)) {
            return false
        }

        if (plugin.logRecipeSyncs) {
            plugin.logger.info(
                "Sent Fabric recipe sync to ${player.name} (${payload.recipeCount} recipes, ${payload.bytes.size} bytes)."
            )
        }
        return true
    }

    fun onPlayerQuit(player: Player) {
        pendingRecipeSyncs.remove(player.uniqueId)
        supportedSerializersByPlayer.remove(player.uniqueId)
    }

    fun invalidateRecipeSyncCache() {
        cachedFullRecipeSyncPayload = null
    }

    private fun getOrCreateFullRecipeSyncPayload(): EncodedRecipeSyncPayload {
        cachedFullRecipeSyncPayload?.let { return it }
        return synchronized(this) {
            cachedFullRecipeSyncPayload ?: createRecipeSyncPayload(null).also {
                cachedFullRecipeSyncPayload = it
            }
        }
    }

    private fun readSupportedSerializers(player: Player, message: ByteArray) {
        val byteBuf = Unpooled.wrappedBuffer(message)
        val buffer = FriendlyByteBuf(byteBuf)
        try {
            val count = buffer.readVarInt()
            if (count < 0 || count > MAX_SUPPORTED_SERIALIZERS) {
                throw IllegalArgumentException("Invalid serializer count: $count")
            }

            val ids = LinkedHashSet<Identifier>()
            repeat(count) {
                val id = buffer.readIdentifier()
                if (id.namespace == MINECRAFT_NAMESPACE) {
                    ids.add(id)
                }
            }
            if (buffer.readableBytes() != 0) {
                throw IllegalArgumentException("Unexpected trailing bytes: ${buffer.readableBytes()}")
            }

            supportedSerializersByPlayer[player.uniqueId] = ids
        } catch (e: Exception) {
            plugin.logger.warning("Failed to read Fabric recipe serializers from ${player.name}: ${e.message}")
        } finally {
            byteBuf.release()
        }
    }

    private fun createRecipeSyncPayload(supportedSerializers: Set<Identifier>?): EncodedRecipeSyncPayload {
        val craftServer = plugin.server as org.bukkit.craftbukkit.CraftServer
        val minecraftServer = craftServer.server
        val recipesBySerializer = linkedMapOf<RecipeSerializer<*>, MutableList<RecipeHolder<*>>>()

        minecraftServer.recipeManager.recipes.values().forEach { holder ->
            if (!plugin.isRecipeAllowed(holder.id().identifier().toString())) {
                return@forEach
            }

            val serializer = holder.value().serializer
            val serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer) ?: return@forEach
            if (serializerId.namespace != MINECRAFT_NAMESPACE) {
                return@forEach
            }
            if (supportedSerializers != null && serializerId !in supportedSerializers) {
                return@forEach
            }

            recipesBySerializer.getOrPut(serializer) { mutableListOf() }.add(holder)
        }

        val sortedEntries = recipesBySerializer.entries
            .sortedBy { BuiltInRegistries.RECIPE_SERIALIZER.getKey(it.key).toString() }

        val byteBuf = Unpooled.buffer()
        val registryBuf = RegistryFriendlyByteBuf(byteBuf, minecraftServer.registryAccess())
        try {
            registryBuf.writeVarInt(sortedEntries.size)
            var recipeCount = 0
            sortedEntries.forEach { (serializer, recipes) ->
                val sortedRecipes = recipes.sortedBy { it.id().identifier().toString() }
                writeSerializerEntry(registryBuf, serializer, sortedRecipes)
                recipeCount += sortedRecipes.size
            }

            val payload = ByteArray(registryBuf.readableBytes())
            registryBuf.readBytes(payload)
            return EncodedRecipeSyncPayload(payload, recipeCount)
        } finally {
            byteBuf.release()
        }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun writeSerializerEntry(
        buffer: RegistryFriendlyByteBuf,
        serializer: RecipeSerializer<*>,
        recipes: List<RecipeHolder<*>>
    ) {
        val serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer)
            ?: throw IllegalStateException("Recipe serializer is not registered: $serializer")
        val codec = serializer.streamCodec() as StreamCodec<RegistryFriendlyByteBuf, Recipe<*>>

        buffer.writeIdentifier(serializerId)
        buffer.writeVarInt(recipes.size)
        recipes.forEach { holder ->
            buffer.writeResourceKey(holder.id())
            codec.encode(buffer, holder.value())
        }
    }

    private data class EncodedRecipeSyncPayload(
        val bytes: ByteArray,
        val recipeCount: Int
    )

    companion object {
        private const val MAX_SUPPORTED_SERIALIZERS = 256
        private const val MINECRAFT_NAMESPACE = "minecraft"
    }
}
