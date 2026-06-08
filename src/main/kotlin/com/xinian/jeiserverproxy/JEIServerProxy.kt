package com.xinian.jeiserverproxy

import com.xinian.jeiserverproxy.command.CommandManager
import com.xinian.jeiserverproxy.i18n.LocaleManager
import com.xinian.jeiserverproxy.listener.PlayerJoinListener
import com.xinian.jeiserverproxy.network.JEINetworkHandler
import com.xinian.jeiserverproxy.network.NeoForgeRecipeSyncBridge
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.CampfireRecipe
import org.bukkit.inventory.CraftingRecipe
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.SmithingRecipe
import org.bukkit.inventory.SmokingRecipe
import org.bukkit.inventory.StonecuttingRecipe
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.File

class JEIServerProxy : JavaPlugin() {

    var recipeKeys: List<NamespacedKey> = emptyList()
        private set

    lateinit var localeManager: LocaleManager
        private set
    lateinit var neoForgeRecipeSyncBridge: NeoForgeRecipeSyncBridge
        private set

    lateinit var legacyJeiNetworkKey: NamespacedKey
        private set
    lateinit var legacyReiNetworkKey: NamespacedKey
        private set

    lateinit var jeiRecipeTransferPacketKey: NamespacedKey
        private set
    lateinit var jeiDeletePacketKey: NamespacedKey
        private set
    lateinit var jeiGiveItemStackPacketKey: NamespacedKey
        private set
    lateinit var jeiSetHotbarItemStackPacketKey: NamespacedKey
        private set
    lateinit var jeiRequestCheatPermissionPacketKey: NamespacedKey
        private set
    lateinit var jeiCheatPermissionPacketKey: NamespacedKey
        private set

    lateinit var reiDeletePacketKey: NamespacedKey
        private set
    lateinit var reiCreateItemPacketKey: NamespacedKey
        private set
    lateinit var neoForgeRecipeContentKey: NamespacedKey
        private set

    var sendRecipesEnabled: Boolean = true
        private set

    var neoForgeRecipeSyncEnabled: Boolean = true
        private set

    var sendCompatibilityPacketsOnJoin: Boolean = true
        private set

    var recipeSyncDelayTicks: Long = 20
        private set

    var logRecipeSyncs: Boolean = false
        private set

    var recipeTransferEnabled: Boolean = true
        private set

    var maxTransferSets: Int = 64
        private set

    var cheatBridgeEnabled: Boolean = false
        private set

    var logDeniedCheatActions: Boolean = true
        private set

    var blacklistedRecipeCount: Int = 0
        private set

    var recipeTypeCounts: Map<String, Int> = emptyMap()
        private set

    private var recipeBlacklist: Set<String> = emptySet()

    override fun onEnable() {
        initializeChannelKeys()
        saveDefaultConfig()
        saveBundledLocale("lang/en.yml")
        saveBundledLocale("lang/zh_cn.yml")

        localeManager = LocaleManager(this)
        loadPluginSettings()

        val networkHandler = JEINetworkHandler(this)
        neoForgeRecipeSyncBridge = NeoForgeRecipeSyncBridge(this)
        registerChannels(networkHandler)
        server.pluginManager.registerEvents(PlayerJoinListener(this, networkHandler, neoForgeRecipeSyncBridge), this)
        getCommand("jeiproxy")?.setExecutor(CommandManager(this, networkHandler, neoForgeRecipeSyncBridge))

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
        loadPluginSettings()
        logger.info(
            localeManager.getMessage(
                "plugin.reloaded",
                sendRecipesEnabled,
                recipeTransferEnabled,
                cheatBridgeEnabled,
                recipeBlacklist.size
            )
        )
        logger.info(localeManager.getMessage("plugin.recaching-recipes"))
        cacheRecipes()
        neoForgeRecipeSyncBridge.invalidateRecipeContentCache()
    }

    fun hasCheatPermission(player: Player): Boolean {
        return cheatBridgeEnabled && player.hasPermission("jeiserverproxy.cheat")
    }

    fun cheatPermissionHints(): List<String> {
        return if (cheatBridgeEnabled) {
            listOf("jei.chat.error.no.cheat.permission.op")
        } else {
            emptyList()
        }
    }

    fun isRecipeAllowed(recipeKey: String): Boolean {
        return recipeKey.lowercase() !in recipeBlacklist
    }

    private fun initializeChannelKeys() {
        legacyJeiNetworkKey = NamespacedKey("jei", "network")
        legacyReiNetworkKey = NamespacedKey("rei", "networking")

        jeiRecipeTransferPacketKey = NamespacedKey("jei", "recipe_transfer")
        jeiDeletePacketKey = NamespacedKey("jei", "delete_player_item")
        jeiGiveItemStackPacketKey = NamespacedKey("jei", "give_item_stack")
        jeiSetHotbarItemStackPacketKey = NamespacedKey("jei", "set_hotbar_item_stack")
        jeiRequestCheatPermissionPacketKey = NamespacedKey("jei", "request_cheat_permission")
        jeiCheatPermissionPacketKey = NamespacedKey("jei", "cheat_permission")

        reiDeletePacketKey = NamespacedKey("roughlyenoughitems", "delete_item")
        reiCreateItemPacketKey = NamespacedKey("roughlyenoughitems", "request_create_item")
        neoForgeRecipeContentKey = NamespacedKey("neoforge", "recipe_content")
    }

    private fun registerChannels(networkHandler: PluginMessageListener) {
        registerIncoming(legacyJeiNetworkKey, networkHandler, "legacy JEI network")
        registerOutgoing(legacyJeiNetworkKey, "legacy JEI network")
        registerIncoming(legacyReiNetworkKey, networkHandler, "legacy REI network")
        registerOutgoing(legacyReiNetworkKey, "legacy REI network")

        registerIncoming(jeiRecipeTransferPacketKey, networkHandler, "JEI recipe transfer")
        registerIncoming(jeiDeletePacketKey, networkHandler, "JEI delete item")
        registerIncoming(jeiGiveItemStackPacketKey, networkHandler, "JEI give item")
        registerIncoming(jeiSetHotbarItemStackPacketKey, networkHandler, "JEI set hotbar item")
        registerIncoming(jeiRequestCheatPermissionPacketKey, networkHandler, "JEI cheat permission request")
        registerOutgoing(jeiCheatPermissionPacketKey, "JEI cheat permission")

        registerIncoming(reiDeletePacketKey, networkHandler, "REI delete item")
        registerOutgoing(reiDeletePacketKey, "REI delete item")
        registerIncoming(reiCreateItemPacketKey, networkHandler, "REI create item")

        registerOutgoing(neoForgeRecipeContentKey, "NeoForge recipe content")
    }

    private fun registerIncoming(key: NamespacedKey, networkHandler: PluginMessageListener, label: String) {
        val channelName = key.toString()
        server.messenger.registerIncomingPluginChannel(this, channelName, networkHandler)
        logger.info("Registered incoming $label channel: $channelName")
    }

    private fun registerOutgoing(key: NamespacedKey, label: String) {
        val channelName = key.toString()
        server.messenger.registerOutgoingPluginChannel(this, channelName)
        logger.info("Registered outgoing $label channel: $channelName")
    }

    private fun saveBundledLocale(path: String) {
        val target = File(dataFolder, path)
        if (!target.exists()) {
            saveResource(path, false)
        }
    }

    private fun loadPluginSettings() {
        localeManager.loadLocales()
        sendRecipesEnabled = config.getBoolean("send-recipes-on-join", true)
        neoForgeRecipeSyncEnabled = config.getBoolean("send-neoforge-recipe-content", true)
        sendCompatibilityPacketsOnJoin = config.getBoolean("send-compatibility-packets-on-join", true)
        recipeSyncDelayTicks = config.getLong("recipe-sync-delay-ticks", 20).coerceIn(0, 200)
        logRecipeSyncs = config.getBoolean("log-recipe-syncs", false)
        recipeTransferEnabled = config.getBoolean("recipe-transfer-enabled", true)
        maxTransferSets = config.getInt("max-transfer-sets", 64).coerceIn(1, 256)
        cheatBridgeEnabled = config.getBoolean("cheat-bridge-enabled", false)
        logDeniedCheatActions = config.getBoolean("log-denied-cheat-actions", true)
        recipeBlacklist = config.getStringList("recipe-blacklist")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun cacheRecipes() {
        val keys = mutableListOf<NamespacedKey>()
        val recipeCounts = mutableMapOf<String, Int>()
        var blacklistedCount = 0

        server.recipeIterator().forEachRemaining { recipe ->
            if (recipe is Keyed) {
                if (recipe.key.toString().lowercase() in recipeBlacklist) {
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
        recipeKeys = keys
        blacklistedRecipeCount = blacklistedCount
        recipeTypeCounts = recipeCounts.toSortedMap()

        logger.info(localeManager.getMessage("plugin.cached-recipes", keys.size, blacklistedCount))
        recipeTypeCounts.forEach { (type, count) ->
            logger.info(localeManager.getMessage("plugin.found-recipes", count, type))
        }
    }

    override fun onDisable() {
        server.messenger.unregisterIncomingPluginChannel(this)
        server.messenger.unregisterOutgoingPluginChannel(this)
        if (::localeManager.isInitialized) {
            logger.info(localeManager.getMessage("plugin.disabled"))
        }
    }
}
