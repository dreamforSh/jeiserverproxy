package com.xinian.jeiserverproxy.i18n

import com.xinian.jeiserverproxy.JEIServerProxy
import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.text.MessageFormat

class LocaleManager(private val plugin: JEIServerProxy) {

    private var messages: FileConfiguration? = null
    private var defaultMessages: FileConfiguration? = null

    init {
        loadLocales()
    }

    fun loadLocales() {
        val lang = plugin.config.getString("language", "en")!!
        val langFile = File(plugin.dataFolder, "lang/$lang.yml")
        val defaultLangFile = File(plugin.dataFolder, "lang/en.yml")

        // 如果语言文件不存在，自动创建
        if (!langFile.exists()) {
            plugin.saveResource("lang/$lang.yml", false)
        }
        if (!defaultLangFile.exists()) {
            plugin.saveResource("lang/en.yml", false)
        }

        messages = YamlConfiguration.loadConfiguration(langFile)
        defaultMessages = YamlConfiguration.loadConfiguration(defaultLangFile)
    }

    fun getMessage(key: String, vararg args: Any): String {
        var message = messages?.getString(key)
        if (message == null) {
            message = defaultMessages?.getString(key) ?: key
        }

        val formattedMessage = MessageFormat.format(message, *args)
        return ChatColor.translateAlternateColorCodes('&', formattedMessage)
    }
}
