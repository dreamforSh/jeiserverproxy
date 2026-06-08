package com.xinian.jeiserverproxy.listener

import com.xinian.jeiserverproxy.JEIServerProxy
import com.xinian.jeiserverproxy.network.JEINetworkHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRegisterChannelEvent

class PlayerJoinListener(
    private val plugin: JEIServerProxy,
    private val networkHandler: JEINetworkHandler
) : Listener {

    private val localeManager get() = plugin.localeManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
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

            if (plugin.sendCompatibilityPacketsOnJoin) {
                networkHandler.sendCompatibilityPackets(player)
            }
        }, plugin.recipeSyncDelayTicks)
    }

    @EventHandler
    fun onPlayerRegisterChannel(event: PlayerRegisterChannelEvent) {
        if (!plugin.sendCompatibilityPacketsOnJoin || !networkHandler.isCompatibilityChannel(event.channel)) {
            return
        }

        val player = event.player
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) {
                networkHandler.sendCompatibilityPackets(player)
            }
        })
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        networkHandler.onPlayerQuit(event.player)
    }
}
