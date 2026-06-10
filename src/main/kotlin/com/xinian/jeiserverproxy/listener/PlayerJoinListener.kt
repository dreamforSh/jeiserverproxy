package com.xinian.jeiserverproxy.listener

import com.xinian.jeiserverproxy.JEIServerProxy
import com.xinian.jeiserverproxy.network.FabricRecipeSyncBridge
import com.xinian.jeiserverproxy.network.JEINetworkHandler
import com.xinian.jeiserverproxy.network.NeoForgeRecipeSyncBridge
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRegisterChannelEvent

class PlayerJoinListener(
    private val plugin: JEIServerProxy,
    private val networkHandler: JEINetworkHandler,
    private val neoForgeRecipeSyncBridge: NeoForgeRecipeSyncBridge,
    private val fabricRecipeSyncBridge: FabricRecipeSyncBridge
) : Listener {

    private val localeManager get() = plugin.localeManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        fabricRecipeSyncBridge.queueRecipeSync(player)

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }

            if (plugin.sendRecipesEnabled && plugin.recipeKeys.isNotEmpty()) {
                player.discoverRecipes(plugin.recipeKeys)
                if (plugin.logRecipeSyncs) {
                    plugin.logger.info(localeManager.getMessage("listener.sent-recipes", plugin.recipeKeys.size, player.name))
                }
            }
            neoForgeRecipeSyncBridge.queueRecipeContentSync(player)
            fabricRecipeSyncBridge.queueRecipeSync(player)

            if (plugin.sendCompatibilityPacketsOnJoin) {
                networkHandler.sendCompatibilityPackets(player)
            }
        }, plugin.recipeSyncDelayTicks)
    }

    @EventHandler
    fun onPlayerRegisterChannel(event: PlayerRegisterChannelEvent) {
        if (plugin.sendCompatibilityPacketsOnJoin && networkHandler.isCompatibilityChannel(event.channel)) {
            networkHandler.queueCompatibilityPackets(event.player)
        }

        if (neoForgeRecipeSyncBridge.isRecipeContentChannel(event.channel)) {
            neoForgeRecipeSyncBridge.queueRecipeContentSync(event.player)
        }

        if (fabricRecipeSyncBridge.isRecipeSyncChannel(event.channel)) {
            fabricRecipeSyncBridge.queueRecipeSync(event.player)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        networkHandler.onPlayerQuit(event.player)
        neoForgeRecipeSyncBridge.onPlayerQuit(event.player)
        fabricRecipeSyncBridge.onPlayerQuit(event.player)
    }
}
