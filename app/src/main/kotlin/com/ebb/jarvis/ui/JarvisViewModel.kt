package com.ebb.jarvis.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebb.jarvis.core.ai.BrainEvent
import com.ebb.jarvis.core.ai.JarvisBrain
import com.ebb.jarvis.core.ai.tools.DeviceTools
import com.ebb.jarvis.core.apps.AppEntry
import com.ebb.jarvis.core.apps.AppRepository
import com.ebb.jarvis.core.data.SecureStore
import com.ebb.jarvis.core.speech.VoiceEngine
import com.ebb.jarvis.core.speech.VoiceEvent
import com.ebb.jarvis.core.system.SystemMonitor
import com.ebb.jarvis.core.system.SystemStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { BOOT, IDLE, LISTENING, WORKING, SPEAKING }

enum class Surface { HUD, DRAWER, SETTINGS }

data class SettingsSnapshot(
    val apiKeySet: Boolean = false,
    val model: String = SecureStore.DEFAULT_MODEL,
    val effort: String = SecureStore.DEFAULT_EFFORT,
    val operatorName: String = "Sir",
    val speakReplies: Boolean = true,
    val bootSequence: Boolean = true,
    val webSearch: Boolean = true,
)

data class JarvisUiState(
    val phase: Phase = Phase.BOOT,
    val surface: Surface = Surface.HUD,
    val status: SystemStatus = SystemStatus(),
    val apps: List<AppEntry> = emptyList(),
    val heard: String = "",
    val reply: String = "",
    val actions: List<String> = emptyList(),
    val error: String? = null,
    val amplitude: Float = 0f,
    val settings: SettingsSnapshot = SettingsSnapshot(),
)

/**
 * Single owner of launcher state. Everything the HUD renders is derived from
 * [state]; the activity only forwards intent (tap, type, swipe, permission result).
 */
class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureStore(application)
    private val appRepository = AppRepository(application)
    private val systemMonitor = SystemMonitor(application)
    private val voice = VoiceEngine(application)
    private val brain = JarvisBrain(
        store = store,
        tools = DeviceTools(application, appRepository, systemMonitor),
        system = systemMonitor,
    )

    private val _state = MutableStateFlow(
        JarvisUiState(
            phase = if (store.bootSequence) Phase.BOOT else Phase.IDLE,
            status = systemMonitor.snapshot(),
            settings = snapshotSettings(),
        ),
    )
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private var askJob: Job? = null

    init {
        viewModelScope.launch {
            systemMonitor.statusFlow().collect { status ->
                _state.update { it.copy(status = status) }
            }
        }
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val loaded = appRepository.refresh()
            _state.update { it.copy(apps = loaded) }
        }
    }

    fun onBootFinished() {
        _state.update { if (it.phase == Phase.BOOT) it.copy(phase = Phase.IDLE) else it }
    }

    // --- surfaces -----------------------------------------------------------

    fun showDrawer() = _state.update { it.copy(surface = Surface.DRAWER) }

    fun showSettings() = _state.update { it.copy(surface = Surface.SETTINGS) }

    /** Called for back presses and for HOME pressed while already home. */
    fun showHud() {
        _state.update { it.copy(surface = Surface.HUD) }
    }

    fun launch(entry: AppEntry) {
        if (appRepository.launch(entry)) {
            _state.update { it.copy(surface = Surface.HUD) }
        } else {
            _state.update { it.copy(error = "${entry.label} refused to start") }
        }
    }

    // --- voice --------------------------------------------------------------

    fun toggleListening() {
        if (_state.value.phase == Phase.LISTENING) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (_state.value.phase == Phase.WORKING) return
        voice.stopSpeaking()
        askJob?.cancel()
        listenJob?.cancel()

        _state.update {
            it.copy(
                phase = Phase.LISTENING,
                surface = Surface.HUD,
                heard = "",
                reply = "",
                error = null,
                actions = emptyList(),
                amplitude = 0f,
            )
        }

        listenJob = viewModelScope.launch {
            voice.listen().collect { event ->
                when (event) {
                    is VoiceEvent.Listening -> Unit
                    is VoiceEvent.Amplitude ->
                        _state.update { it.copy(amplitude = event.level) }
                    is VoiceEvent.Partial ->
                        _state.update { it.copy(heard = event.text) }
                    is VoiceEvent.Final -> {
                        _state.update { it.copy(heard = event.text, amplitude = 0f) }
                        submit(event.text)
                    }
                    is VoiceEvent.Failed ->
                        _state.update {
                            it.copy(
                                phase = Phase.IDLE,
                                amplitude = 0f,
                                error = event.reason,
                            )
                        }
                }
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        _state.update {
            if (it.phase == Phase.LISTENING) {
                it.copy(phase = Phase.IDLE, amplitude = 0f)
            } else {
                it
            }
        }
    }

    fun onMicrophoneDenied() {
        _state.update {
            it.copy(
                phase = Phase.IDLE,
                error = "Microphone denied. Type instead, or grant it in settings.",
            )
        }
    }

    // --- reasoning ----------------------------------------------------------

    fun submit(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return

        listenJob?.cancel()
        askJob?.cancel()
        _state.update {
            it.copy(
                phase = Phase.WORKING,
                surface = Surface.HUD,
                heard = prompt,
                reply = "",
                error = null,
                actions = emptyList(),
                amplitude = 0f,
            )
        }

        askJob = viewModelScope.launch {
            brain.ask(prompt).collect { event ->
                when (event) {
                    is BrainEvent.Action ->
                        _state.update { it.copy(actions = it.actions + event.note) }

                    is BrainEvent.Reply -> {
                        _state.update { it.copy(reply = event.text, phase = Phase.SPEAKING) }
                        if (store.speakReplies) {
                            voice.speak(event.text)
                        }
                        _state.update {
                            if (it.phase == Phase.SPEAKING) it.copy(phase = Phase.IDLE) else it
                        }
                    }

                    is BrainEvent.Failure ->
                        _state.update {
                            it.copy(phase = Phase.IDLE, error = event.message)
                        }
                }
            }
            _state.update {
                if (it.phase == Phase.WORKING) it.copy(phase = Phase.IDLE) else it
            }
        }
    }

    fun clearConversation() {
        askJob?.cancel()
        voice.stopSpeaking()
        brain.resetConversation()
        _state.update {
            it.copy(
                phase = Phase.IDLE,
                heard = "",
                reply = "",
                actions = emptyList(),
                error = null,
            )
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    // --- settings -----------------------------------------------------------

    fun setApiKey(value: String) {
        store.apiKey = value
        brain.resetConversation()
        pushSettings()
    }

    fun setModel(value: String) {
        store.model = value
        pushSettings()
    }

    fun setEffort(value: String) {
        store.effort = value
        pushSettings()
    }

    fun setOperatorName(value: String) {
        store.operatorName = value.ifBlank { "Sir" }
        pushSettings()
    }

    fun setSpeakReplies(value: Boolean) {
        store.speakReplies = value
        if (!value) voice.stopSpeaking()
        pushSettings()
    }

    fun setBootSequence(value: Boolean) {
        store.bootSequence = value
        pushSettings()
    }

    fun setWebSearch(value: Boolean) {
        store.webSearch = value
        pushSettings()
    }

    private fun pushSettings() = _state.update { it.copy(settings = snapshotSettings()) }

    private fun snapshotSettings() = SettingsSnapshot(
        apiKeySet = store.hasApiKey,
        model = store.model,
        effort = store.effort,
        operatorName = store.operatorName,
        speakReplies = store.speakReplies,
        bootSequence = store.bootSequence,
        webSearch = store.webSearch,
    )

    override fun onCleared() {
        listenJob?.cancel()
        askJob?.cancel()
        voice.shutdown()
        super.onCleared()
    }
}
