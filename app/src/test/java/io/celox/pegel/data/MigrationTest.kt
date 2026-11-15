package io.celox.pegel.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Builds the database exactly as version 1 shipped it (from the checked-in schema) and opens it with today's code. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun schema(version: Int): JSONObject {
        val dir = listOf(File("schemas"), File("app/schemas")).first { it.exists() }
        return JSONObject(File(dir, "io.celox.pegel.data.PegelDatabase/$version.json").readText()).getJSONObject("database")
    }

    @Test
    fun `a version 1 database keeps its profiles and bindings`() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val v1 = schema(1)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).also { it.parentFile?.mkdirs() }, null).use { db ->
            val entities = v1.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val idx = e.optJSONArray("indices")
                if (idx != null) for (j in 0 until idx.length()) {
                    db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                }
            }
            val setup = v1.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO profiles (id, name, stateJson, updatedAt) VALUES (7, 'Loud', '{}', 1)")
            db.execSQL("INSERT INTO devices (`key`, label, profileId, lastSeen) VALUES ('BLUETOOTH:Buds', 'Buds', 7, 5)")
            db.execSQL("INSERT INTO devices (`key`, label, profileId, lastSeen) VALUES ('SPEAKER', '', NULL, 6)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, PegelDatabase::class.java, name)
            .addMigrations(*PegelDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val devices = room.devices().all().associateBy { it.key }
            assertEquals(DeviceEntity("BLUETOOTH:Buds", "Buds", 7, 5, null, null), devices["BLUETOOTH:Buds"])
            assertNull(devices.getValue("SPEAKER").profileId)
            assertEquals("Loud", room.profiles().byId(7)?.name)
            room.devices().upsert(devices.getValue("SPEAKER").copy(type = "PHONE_SPEAKER", suggestion = "done"))
            assertEquals("done", room.devices().byKey("SPEAKER")?.suggestion)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
