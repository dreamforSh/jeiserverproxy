package com.xinian.jeiserverproxy.i18n

import com.xinian.jeiserverproxy.JEIServerProxy
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
        var lang = plugin.config.getString("language", "en")?.trim().orEmpty()
        if (lang.isEmpty()) {
            lang = "en"
        }

        var langFile = File(plugin.dataFolder, "lang/$lang.yml")
        val defaultLangFile = File(plugin.dataFolder, "lang/en.yml")

        if (!langFile.exists()) {
            if (bundledResourceExists("lang/$lang.yml")) {
                plugin.saveResource("lang/$lang.yml", false)
            } else {
                plugin.logger.warning("Language file lang/$lang.yml was not found; falling back to English.")
                langFile = defaultLangFile
            }
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

        return translateLegacyColorCodes(MessageFormat.format(message, *args))
    }

    private fun bundledResourceExists(path: String): Boolean {
        return plugin.getResource(path)?.use { true } ?: false
    }

    private fun translateLegacyColorCodes(message: String): String {
        val chars = message.toCharArray()
        for (i in 0 until chars.lastIndex) {
            if (chars[i] == '&' && chars[i + 1] in LEGACY_COLOR_CODES) {
                chars[i] = '\u00A7'
                chars[i + 1] = chars[i + 1].lowercaseChar()
            }
        }
        return String(chars)
    }

    companion object {
        private const val LEGACY_COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx"
    }
}
