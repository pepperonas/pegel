package io.celox.pegel.ui.sound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.celox.pegel.AppContainer
import io.celox.pegel.audio.SoundState
import io.celox.pegel.data.NameError
import io.celox.pegel.data.NameException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Glue only: the decisions live in [buildSoundUi] and [SoundActions]. */
class SoundViewModel(container: AppContainer, private val syncService: (Boolean) -> Unit) : ViewModel() {
    private val repo = container.repository
    private val actions = SoundActions(repo)
    private val notificationsAllowed = MutableStateFlow(true)

    private data class Profiled(val active: io.celox.pegel.data.Profile?, val modified: Boolean)

    val ui: StateFlow<SoundUi> = combine(
        repo.settings, repo.state, repo.customPresets,
        combine(repo.activeProfile, repo.modified, ::Profiled),
        combine(container.engine.state, notificationsAllowed) { e, n -> e to n },
    ) { settings, state, presets, profiled, (engine, allowed) ->
        buildSoundUi(settings, state, presets, profiled.active, profiled.modified, engine, allowed)
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        buildSoundUi(repo.settings.value, repo.state.value, emptyList(), repo.activeProfile.value, repo.modified.value,
            container.engine.state.value, true),
    )

    fun setNotificationsAllowed(allowed: Boolean) { notificationsAllowed.value = allowed }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(enabled = enabled) }
            syncService(enabled)
        }
    }

    fun setGain(band: Int, db: Float) { viewModelScope.launch { actions.setGain(band, db) } }
    fun selectPreset(id: String) { viewModelScope.launch { actions.selectPreset(id) } }
    fun deletePreset(id: String) { viewModelScope.launch { actions.deletePreset(id) } }
    fun resetEqualizer() { viewModelScope.launch { actions.resetEqualizer() } }
    fun mutate(transform: (SoundState) -> SoundState) { viewModelScope.launch { actions.mutate(transform) } }

    /** The error to show, or null when the preset was saved. */
    suspend fun saveAsPreset(name: String): NameError? =
        (actions.saveAsPreset(name).exceptionOrNull() as? NameException)?.error

    companion object {
        fun factory(container: AppContainer, syncService: (Boolean) -> Unit) = viewModelFactory {
            initializer { SoundViewModel(container, syncService) }
        }
    }
}
