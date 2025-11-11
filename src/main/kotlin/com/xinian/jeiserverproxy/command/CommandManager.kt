package com.xinian.jeiserverproxy.command

import com.xinian.jeiserverproxy.JEIServerProxy
import com.xinian.jeiserverproxy.network.JEINetworkHandler
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class CommandManager(
    private val plugin: JEIServerProxy,
    private val networkHandler: JEINetworkHandler
) : CommandExecutor {

    private val localeManager get() = plugin.localeManager

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
            "handshake" -> {
                if (args.size < 2) {
                    sender.sendMessage(localeManager.getMessage("command.handshake.usage"))
                    return true
                }
                val playerName = args[1]
                val player = Bukkit.getPlayer(playerName)
                if (player == null) {
                    sender.sendMessage(localeManager.getMessage("command.player-not-found", playerName))
                    return true
                }
                
                plugin.logger.info("Administrator ${sender.name} is manually triggering JEI handshake for ${player.name}...")
                networkHandler.sendHandshake(player)
                networkHandler.sendCheatPermissionPacket(player)
                sender.sendMessage(localeManager.getMessage("command.handshake.success", player.name))
            }
            else -> sendHelp(sender)
        }
        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(localeManager.getMessage("command.help-header"))
        sender.sendMessage(localeManager.getMessage("command.help-reload"))
        sender.sendMessage(localeManager.getMessage("command.help-handshake"))
    }
}
