package com.xinian.jeiserverproxy

import com.xinian.jeiserverproxy.command.CommandManager
import com.xinian.jeiserverproxy.i18n.LocaleManager
import com.xinian.jeiserverproxy.listener.PlayerJoinListener
import com.xinian.jeiserverproxy.network.JEINetworkHandler
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.inventory.*
import org.bukkit.plugin.java.JavaPlugin

class JEIServerProxy : JavaPlugin() {

    lateinit var recipeKeys: List<NamespacedKey>
        private set

    lateinit var localeManager: LocaleManager
        private set


    lateinit var jeiNetworkKey: NamespacedKey
        private set
    lateinit var reiNetworkKey: NamespacedKey
        private set


    lateinit var jeiDeletePacketKey: NamespacedKey
        private set

    var sendRecipesEnabled: Boolean = true
    private var recipeBlacklist: Set<String> = emptySet()

    override fun onEnable() {

        jeiNetworkKey = NamespacedKey("jei", "network")
        reiNetworkKey = NamespacedKey("rei", "networking")
        jeiDeletePacketKey = NamespacedKey("jei", "delete_player_item")

        saveDefaultConfig()

        saveResource("lang/en.yml", false)
        saveResource("lang/zh_cn.yml", false)

        localeManager = LocaleManager(this)
        reloadPluginConfig()

        val networkHandler = JEINetworkHandler(this)


        val jeiNetworkChannelName = jeiNetworkKey.toString()
        server.messenger.registerIncomingPluginChannel(this, jeiNetworkChannelName, networkHandler)
        server.messenger.registerOutgoingPluginChannel(this, jeiNetworkChannelName)
        logger.info("Registered JEI channel: $jeiNetworkChannelName")

        val reiNetworkChannelName = reiNetworkKey.toString()
        server.messenger.registerIncomingPluginChannel(this, reiNetworkChannelName, networkHandler)
        server.messenger.registerOutgoingPluginChannel(this, reiNetworkChannelName)
        logger.info("Registered REI channel: $reiNetworkChannelName")


        val deleteChannelName = jeiDeletePacketKey.toString()
        server.messenger.registerIncomingPluginChannel(this, deleteChannelName, networkHandler)

        server.messenger.registerOutgoingPluginChannel(this, deleteChannelName)

        logger.info("Registered JEI detection channel: $deleteChannelName")

        server.pluginManager.registerEvents(PlayerJoinListener(this, networkHandler), this)
        getCommand("jeiproxy")?.setExecutor(CommandManager(this, networkHandler))

        val version = pluginMeta.version
        logger.info(localeManager.getMessage("plugin.decor"))
        logger.info(localeManager.getMessage("plugin.enabled", version))

        logger.info(localeManager.getMessage("plugin.caching-recipes"))
        cacheRecipes()

        logger.info(localeManager.getMessage("plugin.ready"))
        logger.info(localeManager.getMessage("plugin.decor"))
    }

    fun reloadPluginConfig() {
        reloadConfig()
        localeManager.loadLocales()
        sendRecipesEnabled = config.getBoolean("send-recipes-on-join", true)
        recipeBlacklist = config.getStringList("recipe-blacklist").toSet()
        logger.info(localeManager.getMessage("plugin.reloaded", sendRecipesEnabled, recipeBlacklist.size))

        logger.info(localeManager.getMessage("plugin.recaching-recipes"))
        cacheRecipes()
    }

    private fun cacheRecipes() {
        val keys = mutableListOf<NamespacedKey>()
        val recipeCounts = mutableMapOf<String, Int>()
        var blacklistedCount = 0

        server.recipeIterator().forEachRemaining { recipe ->
            if (recipe is Keyed) {
                if (recipe.key.toString() in recipeBlacklist) {
                    blacklistedCount++
                    return@forEachRemaining
                }

                keys.add(recipe.key)
                val typeName = when (recipe) {
                    is CraftingRecipe -> "Crafting"
                    is SmithingRecipe -> "Smithing"
                    is SmokingRecipe -> "Smoking"
                    is FurnaceRecipe -> "Furnace"
                    is BlastingRecipe -> "Blasting"
                    is StonecuttingRecipe -> "Stonecutting"
                    is CampfireRecipe -> "Campfire"
                    else -> "Other"
                }
                recipeCounts[typeName] = recipeCounts.getOrDefault(typeName, 0) + 1
            }
        }
        this.recipeKeys = keys

        logger.info(localeManager.getMessage("plugin.cached-recipes", keys.size, blacklistedCount))
        recipeCounts.toSortedMap().forEach { (type, count) ->
            logger.info(localeManager.getMessage("plugin.found-recipes", count, type))
        }
    }

    override fun onDisable() {
        logger.info(localeManager.getMessage("plugin.disabled"))
    }
}
