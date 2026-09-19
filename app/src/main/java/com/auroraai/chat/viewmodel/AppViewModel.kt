package com.auroraai.chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auroraai.chat.data.AiClient
import com.auroraai.chat.data.AppSettings
import com.auroraai.chat.data.AppTab
import com.auroraai.chat.data.ChatMessage
import com.auroraai.chat.data.ChatRequest
import com.auroraai.chat.data.ChatResponseChunk
import com.auroraai.chat.data.ProviderProfile
import com.auroraai.chat.data.ProviderProfileStore
import com.auroraai.chat.data.SettingsStore
import com.auroraai.chat.data.ThemeMode
import com.auroraai.chat.data.UiChatMessage
import com.auroraai.chat.data.history.ChatHistoryStore
import com.auroraai.chat.data.history.ChatSession
import com.auroraai.chat.data.history.ChatSessionSummary
import com.auroraai.chat.data.history.PersistedMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

data class AppUiState(
    val settingsLoaded: Boolean = false,
    val settings: AppSettings? = null,
    val currentTab: AppTab = AppTab.CHAT,

    // provider profiles — fully-saved credentials, switch by name instead of re-pasting a key
    val activeProviderProfile: ProviderProfile? = null,
    val providerProfiles: List<ProviderProfile> = emptyList(),
    val showProviderPicker: Boolean = false,
    val providerFormEditing: ProviderProfile? = null,
    val showProviderForm: Boolean = false,

    // chat
    val sessionId: String = UUID.randomUUID().toString(),
    val messages: List<UiChatMessage> = emptyList(),
    val isAgentRunning: Boolean = false,
    val statusLabel: String = "Idle",
    val error: String? = null,

    // history
    val sessionSummaries: List<ChatSessionSummary> = emptyList(),
    val showHistorySidebar: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val historyStore = ChatHistoryStore(application)
    private val providerProfileStore = ProviderProfileStore(application)
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var apiMessages = mutableListOf<ChatMessage>()
    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                _state.update { it.copy(settingsLoaded = true, settings = settings) }
            }
        }
        viewModelScope.launch { initializeActiveProvider() }
        refreshSessions()
    }

    private suspend fun initializeActiveProvider() {
        val profiles = providerProfileStore.list()
        _state.update { it.copy(providerProfiles = profiles) }
        if (profiles.isEmpty()) return // no default auto-created — nothing to guess a key for
        val lastId = _state.value.settings?.lastActiveProviderProfileId
        val target = profiles.find { it.id == lastId } ?: profiles.first()
        _state.update { it.copy(activeProviderProfile = target) }
    }

    // ---------- Navigation ----------

    fun selectTab(tab: AppTab) = _state.update { it.copy(currentTab = tab) }

    fun openHistorySidebar() = _state.update { it.copy(showHistorySidebar = true) }
    fun dismissHistorySidebar() = _state.update { it.copy(showHistorySidebar = false) }

    // ---------- Provider profiles ----------

    fun openProviderPicker() {
        viewModelScope.launch { _state.update { it.copy(providerProfiles = providerProfileStore.list(), showProviderPicker = true) } }
    }

    fun dismissProviderPicker() = _state.update { it.copy(showProviderPicker = false) }

    fun selectProviderProfile(profile: ProviderProfile) {
        _state.update { it.copy(activeProviderProfile = profile, showProviderPicker = false) }
        viewModelScope.launch {
            settingsStore.saveLastActiveProviderProfile(profile.id)
            providerProfileStore.touch(profile.id)
            _state.update { it.copy(providerProfiles = providerProfileStore.list()) }
        }
    }

    fun requestAddProvider() = _state.update { it.copy(providerFormEditing = null, showProviderForm = true, showProviderPicker = false) }
    fun requestEditProvider(profile: ProviderProfile) = _state.update { it.copy(providerFormEditing = profile, showProviderForm = true, showProviderPicker = false) }
    fun dismissProviderForm() = _state.update { it.copy(showProviderForm = false) }

    fun saveProviderProfile(name: String, baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isBlank() || model.isBlank()) {
            viewModelScope.launch { _snackbar.emit("Base URL and model are required") }
            return
        }
        val editing = _state.value.providerFormEditing
        viewModelScope.launch {
            if (editing != null) {
                providerProfileStore.update(editing.id, name, baseUrl, apiKey, model)
            } else {
                val created = providerProfileStore.create(name, baseUrl, apiKey, model)
                selectProviderProfile(created)
            }
            val profiles = providerProfileStore.list()
            _state.update {
                it.copy(
                    providerProfiles = profiles, showProviderForm = false,
                    activeProviderProfile = if (editing != null && it.activeProviderProfile?.id == editing.id)
                        profiles.find { p -> p.id == editing.id } else it.activeProviderProfile
                )
            }
            _snackbar.emit(if (editing != null) "Provider updated" else "Provider saved and activated")
        }
    }

    fun deleteProviderProfile(profile: ProviderProfile) {
        viewModelScope.launch {
            providerProfileStore.delete(profile.id)
            val profiles = providerProfileStore.list()
            _state.update {
                it.copy(
                    providerProfiles = profiles,
                    activeProviderProfile = if (it.activeProviderProfile?.id == profile.id) profiles.firstOrNull() else it.activeProviderProfile
                )
            }
            _snackbar.emit("Deleted \"${profile.name}\"")
        }
    }

    // ---------- Chat ----------

    fun sendMessage(rawText: String) {
        val prompt = rawText.trim()
        if (prompt.isBlank() || _state.value.isAgentRunning) return
        val providerProfile = _state.value.activeProviderProfile
        if (providerProfile == null) {
            viewModelScope.launch { _snackbar.emit("Add or pick a provider first.") }
            openProviderPicker()
            return
        }

        apiMessages.add(ChatMessage(role = "user", content = prompt))
        _state.update { it.copy(messages = it.messages + UiChatMessage(role = "user", text = prompt), error = null) }

        streamJob = viewModelScope.launch { streamAssistantReply(providerProfile) }
    }

    fun stopAgent() {
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(isAgentRunning = false, statusLabel = "Stopped") }
    }

    fun newChat() {
        persistCurrentSessionIfNeeded()
        apiMessages = mutableListOf()
        _state.update {
            it.copy(sessionId = UUID.randomUUID().toString(), messages = emptyList(), statusLabel = "Idle", error = null)
        }
    }

    fun loadSession(id: String) {
        viewModelScope.launch {
            val session = historyStore.loadSession(id) ?: run {
                _snackbar.emit("That conversation is no longer available.")
                return@launch
            }
            apiMessages = session.apiMessages.toMutableList()
            val restored = session.renderedMessages.map { pm -> UiChatMessage(role = pm.role, text = pm.text, isError = pm.isError) }
            _state.update {
                it.copy(sessionId = session.id, messages = restored, currentTab = AppTab.CHAT, showHistorySidebar = false)
            }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            historyStore.deleteSession(id)
            refreshSessions()
            if (_state.value.sessionId == id) newChat()
        }
    }

    private fun refreshSessions() {
        viewModelScope.launch {
            _state.update { it.copy(sessionSummaries = historyStore.listSessions()) }
        }
    }

    private fun persistCurrentSessionIfNeeded() {
        val current = _state.value
        val firstUser = current.messages.firstOrNull { it.role == "user" }?.text
        if (firstUser == null) return // nothing to save
        val providerProfile = current.activeProviderProfile
        viewModelScope.launch {
            historyStore.saveSession(
                ChatSession(
                    id = current.sessionId,
                    title = ChatHistoryStore.titleFrom(firstUser),
                    providerLabel = providerProfile?.name ?: "—",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    apiMessages = apiMessages.toList(),
                    renderedMessages = current.messages.map { m -> PersistedMessage(role = m.role, text = m.text, isError = m.isError) }
                )
            )
            refreshSessions()
        }
    }

    private suspend fun streamAssistantReply(providerProfile: ProviderProfile) {
        val settings = _state.value.settings ?: return
        val client = AiClient(providerProfile.toConfig())
        _state.update { it.copy(isAgentRunning = true, statusLabel = "Thinking…") }

        val assistant = UiChatMessage(role = "assistant", isStreaming = true)
        _state.update { it.copy(messages = it.messages + assistant) }
        val text = StringBuilder()
        var streamError: String? = null

        val request = ChatRequest(
            model = providerProfile.model,
            messages = apiMessages.toList(),
            stream = true,
            temperature = settings.temperature.toDouble(),
            maxTokens = settings.maxOutputTokens
        )

        try {
            try {
                client.streamChatCompletion(request).collect { raw ->
                    val chunk = lenientJson.decodeFromString(ChatResponseChunk.serializer(), raw)
                    val token = chunk.choices.firstOrNull()?.delta?.content ?: return@collect
                    text.append(token)
                    updateMessage(assistant.id) { it.copy(text = text.toString()) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // stopAgent() cancelling this job must actually stop it, not surface as a stream error
            } catch (e: Exception) {
                streamError = e.message ?: "Connection error."
            }

            updateMessage(assistant.id) { it.copy(isStreaming = false) }

            if (streamError != null) {
                updateMessage(assistant.id) { it.copy(text = it.text.ifBlank { "Connection error." } + "\n\n⚠ $streamError", isError = true) }
                _state.update { it.copy(statusLabel = "Error", error = streamError) }
                _snackbar.emit(streamError!!)
            } else {
                apiMessages.add(ChatMessage(role = "assistant", content = text.toString()))
                _state.update { it.copy(statusLabel = "Idle") }
            }
        } finally {
            client.close()
            _state.update { it.copy(isAgentRunning = false) }
            if (_state.value.statusLabel == "Thinking…") _state.update { it.copy(statusLabel = "Idle") }
            streamJob = null
            persistCurrentSessionIfNeeded()
        }
    }

    private fun updateMessage(id: String, transform: (UiChatMessage) -> UiChatMessage) {
        _state.update { state -> state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it }) }
    }

    // ---------- Settings ----------

    fun saveGenerationParams(temperature: Float, maxTokens: Int) {
        viewModelScope.launch { settingsStore.saveGenerationParams(temperature, maxTokens) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.saveThemeMode(mode) }
    }
}
