package com.auroraai.chat.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "aurora_settings")

data class AppSettings(
    val temperature: Float,
    val maxOutputTokens: Int,
    val themeMode: ThemeMode,
    /** Which saved ProviderProfile to reopen on cold start. */
    val lastActiveProviderProfileId: String?
)

/**
 * Global settings that survive app restart — chat sessions live in ChatHistoryStore, and
 * provider credentials live in ProviderProfileStore (deliberately never here — a single
 * global "the" API key was the whole problem when someone has several keys for one provider).
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val temperature = stringPreferencesKey("temperature")
        val maxTokens = stringPreferencesKey("max_tokens")
        val themeMode = stringPreferencesKey("theme_mode") // "system" | "light" | "dark"
        val lastActiveProviderProfileId = stringPreferencesKey("last_active_provider_profile_id")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            temperature = prefs[Keys.temperature]?.toFloatOrNull() ?: 0.7f,
            maxOutputTokens = prefs[Keys.maxTokens]?.toIntOrNull() ?: 4096,
            themeMode = when (prefs[Keys.themeMode]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
            lastActiveProviderProfileId = prefs[Keys.lastActiveProviderProfileId]
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.themeMode] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }

    suspend fun saveGenerationParams(temperature: Float, maxTokens: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.temperature] = temperature.toString()
            prefs[Keys.maxTokens] = maxTokens.toString()
        }
    }

    suspend fun saveLastActiveProviderProfile(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.lastActiveProviderProfileId] = id }
    }

    companion object {
        /** Presets shown as tappable chips when adding a provider — a shortcut, never a requirement. */
        val PRESETS = listOf(
            Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            Triple("Gemini (OpenAI shim)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-flash"),
            Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            Triple("Ollama (local)", "http://10.0.2.2:11434/v1", "llama3.1")
        )

        fun providerLabel(url: String): String = when {
            url.contains("deepseek", true) -> "DeepSeek"
            url.contains("generativelanguage", true) || url.contains("gemini", true) -> "Gemini"
            url.contains("ollama", true) || url.contains("11434") -> "Ollama"
            url.contains("openai", true) -> "OpenAI"
            else -> "Custom"
        }

        /** Ollama and other local hosts don't need a key to be considered "ready". */
        fun isKeylessLocal(url: String): Boolean =
            url.contains("10.0.2.2") || url.contains("127.0.0.1") || url.contains("localhost") || url.contains("11434")
    }
}
