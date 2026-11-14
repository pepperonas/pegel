package io.celox.pegel

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.celox.pegel.audio.AndroidEffectChainFactory
import io.celox.pegel.audio.EffectEngine
import io.celox.pegel.data.PegelDatabase
import io.celox.pegel.data.SettingsStore
import io.celox.pegel.data.SoundRepository
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Hand-wired dependencies — one instance per process, owned by [PegelApp]. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val database: PegelDatabase = Room.databaseBuilder(app, PegelDatabase::class.java, "pegel.db").build()

    // Read once, blocking: the stored state is a few hundred bytes, and both the service (which may
    // start from a broadcast) and the first frame need it before they can do anything sensible.
    val repository: SoundRepository =
        runBlocking { SoundRepository.create(database, SettingsStore(app.settingsDataStore)) }

    val engine = EffectEngine(AndroidEffectChainFactory { repository.settings.value.frameDurationMs.toFloat() })
}
