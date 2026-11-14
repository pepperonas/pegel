package io.celox.pegel.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.celox.pegel.AppContainer
import io.celox.pegel.data.NameError
import io.celox.pegel.data.NameException
import io.celox.pegel.data.Profile
import io.celox.pegel.device.OutputDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUi(
    val rows: List<ProfileRow> = emptyList(),
    val devices: List<DeviceRow> = buildDeviceRows(emptyList(), null),
    /** Set while the active profile differs from what is stored. */
    val changed: Profile? = null,
)

/** Glue only: the decisions live in [buildProfileRows] and [buildDeviceRows]. */
class ProfilesViewModel(container: AppContainer, connected: Flow<OutputDevice?>) : ViewModel() {
    private val repo = container.repository

    val ui: StateFlow<ProfilesUi> = combine(
        repo.profiles, repo.devices, repo.activeProfile, repo.modified, connected,
    ) { profiles, devices, active, modified, device ->
        ProfilesUi(
            rows = buildProfileRows(profiles, active?.id, modified, devices),
            devices = buildDeviceRows(devices, device),
            changed = active.takeIf { modified },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfilesUi())

    fun load(id: Long) { viewModelScope.launch { repo.loadProfile(id) } }
    fun save() { viewModelScope.launch { repo.saveActiveProfile() } }
    fun discard() { viewModelScope.launch { repo.discardChanges() } }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteProfile(id) } }
    fun bind(row: DeviceRow, profileId: Long?) {
        viewModelScope.launch {
            repo.seeDevice(row.key, row.label)
            repo.bindDevice(row.key, profileId)
        }
    }
    fun forget(key: String) { viewModelScope.launch { repo.forgetDevice(key) } }

    suspend fun saveAs(name: String): NameError? = error(repo.saveAsProfile(name))
    suspend fun rename(id: Long, name: String): NameError? = error(repo.renameProfile(id, name))

    private fun error(r: Result<*>) = (r.exceptionOrNull() as? NameException)?.error

    companion object {
        fun factory(container: AppContainer, connected: Flow<OutputDevice?>) = viewModelFactory {
            initializer { ProfilesViewModel(container, connected) }
        }
    }
}
