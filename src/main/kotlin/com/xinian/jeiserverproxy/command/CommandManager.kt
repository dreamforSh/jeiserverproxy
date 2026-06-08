package com.xinian.jeiserverproxy.command

import com.xinian.jeiserverproxy.JEIServerProxy
import com.xinian.jeiserverproxy.network.JEINetworkHandler
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor

class CommandManager(
    private val plugin: JEIServerProxy,
    private val networkHandler: JEINetworkHandler
) : TabExecutor {

    private val localeManager get() = plugin.localeManager
    private val subcommands = listOf("reload", "sync", "handshake", "status")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("jeiserverproxy.admin")) {
            sender.sendMessage(localeManager.getMessage("command.no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                plugin.reloadPluginConfig()
                sender.sendMessage(localeManager.getMessage("command.reload-success"))
            }
            "sync", "handshake" -> {
                if (args.size < 2) {
                    sender.sendMessage(localeManager.getMessage("command.sync.usage"))
                    return true
                }
                val playerName = args[1]
                val player = Bukkit.getPlayer(playerName)
                if (player == null) {
                    sender.sendMessage(localeManager.getMessage("command.player-not-found", playerName))
                    return true
                }
                
                plugin.logger.info("Administrator ${sender.name} manually synced JEI compatibility packets for ${player.name}.")
                networkHandler.sendCompatibilityPackets(player)
                sender.sendMessage(localeManager.getMessage("command.sync.success", player.name))
            }
            "status" -> sendStatus(sender)
            else -> sendHelp(sender)
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("jeiserverproxy.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> subcommands.filter { it.startsWith(args[0].lowercase()) }
            2 -> if (args[0].equals("sync", true) || args[0].equals("handshake", true)) {
                Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            } else {
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(localeManager.getMessage("command.help-header"))
        sender.sendMessage(localeManager.getMessage("command.help-reload"))
        sender.sendMessage(localeManager.getMessage("command.help-sync"))
        sender.sendMessage(localeManager.getMessage("command.help-status"))
    }

    private fun sendStatus(sender: CommandSender) {
        sender.sendMessage(localeManager.getMessage("command.status.header"))
        sender.sendMessage(localeManager.getMessage("command.status.recipes", plugin.recipeKeys.size, plugin.blacklistedRecipeCount))
        sender.sendMessage(localeManager.getMessage("command.status.recipe-sync", plugin.sendRecipesEnabled, plugin.recipeSyncDelayTicks))
        sender.sendMessage(localeManager.getMessage("command.status.compatibility", plugin.sendCompatibilityPacketsOnJoin))
        sender.sendMessage(localeManager.getMessage("command.status.transfer", plugin.recipeTransferEnabled, plugin.maxTransferSets))
        sender.sendMessage(localeManager.getMessage("command.status.cheat", plugin.cheatBridgeEnabled))
    }
}
