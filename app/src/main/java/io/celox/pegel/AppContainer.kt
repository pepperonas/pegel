package io.celox.pegel

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.celox.pegel.audio.AndroidEffectChainFactory
import io.celox.pegel.audio.EffectEngine
import io.celox.pegel.data.PegelDatabase
import io.celox.pegel.data.SettingsStore
import io.celox.pegel.data.SoundRepository
import io.celox.pegel.device.HeadphoneCatalog
import io.celox.pegel.update.AndroidInstallGateway
import io.celox.pegel.update.ReleaseSource
import io.celox.pegel.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Hand-wired dependencies — one instance per process, owned by [PegelApp]. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val database: PegelDatabase = Room.databaseBuilder(app, PegelDatabase::class.java, "pegel.db")
        .addMigrations(*PegelDatabase.MIGRATIONS).build()

    // Read once, blocking: the stored state is a few hundred bytes, and both the service (which may
    // start from a broadcast) and the first frame need it before they can do anything sensible.
    val repository: SoundRepository =
        runBlocking { SoundRepository.create(database, SettingsStore(app.settingsDataStore)) }

    val engine = EffectEngine(AndroidEffectChainFactory { repository.settings.value.frameDurationMs.toFloat() })

    /** The shipped headphone table (see scripts/autoeq.py). Parsed on first use — off the main thread, please. */
    val headphones: HeadphoneCatalog by lazy {
        runCatching { HeadphoneCatalog.parse(app.assets.open("headphones.tsv").bufferedReader().use { it.readText() }) }
            .getOrDefault(HeadphoneCatalog(emptyList()))
    }

    /** The in-app update. Created on first use: most starts never touch it. */
    val updateInstaller: UpdateInstaller by lazy {
        UpdateInstaller(
            source = ReleaseSource(),
            gateway = AndroidInstallGateway(app),
            dir = File(app.cacheDir, "update"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            io = Dispatchers.IO,
        )
    }
}
