package io.celox.pegel.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/** A real Room database (in memory) and a real DataStore (temp file) — no fakes of our own code. */
class RepoFixture {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: PegelDatabase = Room.inMemoryDatabaseBuilder(context, PegelDatabase::class.java)
        .allowMainThreadQueries().build()
    private val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()
    val store = SettingsStore(PreferenceDataStoreFactory.create(scope = newScope()) { file })

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }

    /** A fresh repository on the same database and settings file — what a process restart sees. */
    private val repos = mutableListOf<SoundRepository>()

    fun repository(): SoundRepository = runBlocking {
        repos.forEach { it.flush() } // what a clean stop does before the next start
        SoundRepository.create(db, store).also { repos += it }
    }

    fun close() {
        scopes.forEach { it.cancel() }
        db.close()
        file.delete()
    }
}
