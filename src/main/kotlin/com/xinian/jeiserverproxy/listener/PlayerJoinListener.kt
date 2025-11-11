package com.xinian.jeiserverproxy.listener

import com.xinian.jeiserverproxy.JEIServerProxy
import com.xinian.jeiserverproxy.network.JEINetworkHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerJoinListener(
    private val plugin: JEIServerProxy,
    private val networkHandler: JEINetworkHandler
) : Listener {

    private val localeManager get() = plugin.localeManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (plugin.sendRecipesEnabled) {
            val player = event.player
            player.discoverRecipes(plugin.recipeKeys)
            plugin.logger.info(localeManager.getMessage("listener.sent-recipes", plugin.recipeKeys.size, player.name))
        }
    }


    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        networkHandler.onPlayerQuit(event.player)
    }
}
