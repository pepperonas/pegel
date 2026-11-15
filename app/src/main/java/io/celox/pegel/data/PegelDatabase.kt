package io.celox.pegel.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A saved sound: [stateJson] is a SoundState (see SoundStateJson). Names are unique, ignoring case. */
@Entity(tableName = "profiles", indices = [Index(value = ["name"], unique = true)])
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val stateJson: String,
    val updatedAt: Long,
)

/** A custom equalizer preset: [gainsJson] is a JSON array of ten dB values. */
@Entity(tableName = "presets", indices = [Index(value = ["name"], unique = true)])
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val gainsJson: String,
)

/**
 * An output device Pegel has seen, and the profile to apply when it becomes active. Deleting the
 * profile clears the binding (SET NULL) rather than the device.
 */
@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("profileId")],
)
data class DeviceEntity(
    @PrimaryKey val key: String,
    val label: String,
    val profileId: Long?,
    val lastSeen: Long,
    /** A [io.celox.pegel.device.DeviceType] name once the user said what the device is. */
    val type: String? = null,
    /** "done" or "dismissed" once the user answered Pegel's suggestion for this device. */
    val suggestion: String? = null,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE") suspend fun all(): List<ProfileEntity>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun byId(id: Long): ProfileEntity?
    @Query("SELECT * FROM profiles WHERE name = :name COLLATE NOCASE") suspend fun byName(name: String): ProfileEntity?
    @Insert suspend fun insert(p: ProfileEntity): Long
    @Query("UPDATE profiles SET stateJson = :stateJson, updatedAt = :now WHERE id = :id")
    suspend fun setState(id: Long, stateJson: String, now: Long)
    @Query("UPDATE profiles SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun setName(id: Long, name: String, now: Long)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM profiles") suspend fun clear()
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<PresetEntity>>
    @Query("SELECT * FROM presets ORDER BY name COLLATE NOCASE") suspend fun all(): List<PresetEntity>
    @Query("SELECT * FROM presets WHERE name = :name COLLATE NOCASE") suspend fun byName(name: String): PresetEntity?
    @Insert suspend fun insert(p: PresetEntity): Long
    @Query("DELETE FROM presets WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM presets") suspend fun clear()
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY label COLLATE NOCASE") fun observeAll(): Flow<List<DeviceEntity>>
    @Query("SELECT * FROM devices ORDER BY label COLLATE NOCASE") suspend fun all(): List<DeviceEntity>
    @Query("SELECT * FROM devices WHERE `key` = :key") suspend fun byKey(key: String): DeviceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: DeviceEntity)
    @Query("DELETE FROM devices WHERE `key` = :key") suspend fun delete(key: String)
    @Query("DELETE FROM devices") suspend fun clear()
}

@Database(
    entities = [ProfileEntity::class, PresetEntity::class, DeviceEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PegelDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun presets(): PresetDao
    abstract fun devices(): DeviceDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN type TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN suggestion TEXT")
            }
        }

        /** Every step from the first shipped version on — a user's profiles must survive an update. */
        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
    }
}
