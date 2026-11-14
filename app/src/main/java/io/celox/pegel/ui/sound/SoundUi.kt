package io.celox.pegel.ui.sound

import io.celox.pegel.audio.BUILT_IN_PRESETS
import io.celox.pegel.audio.EngineState
import io.celox.pegel.audio.Preset
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.Target
import io.celox.pegel.data.Profile
import io.celox.pegel.data.Settings
import io.celox.pegel.data.SoundRepository
import java.util.Locale
import kotlin.math.roundToInt

sealed interface ProfileLabel {
    data object Unsaved : ProfileLabel
    data class Named(val name: String, val modified: Boolean) : ProfileLabel
}

/** The one thing worth telling the user above the cards, most serious first. */
enum class Notice { ENGINE_FAILED, EQ_UNAVAILABLE, NOTIFICATIONS_OFF }

data class SoundUi(
    val enabled: Boolean = false,
    val state: SoundState = SoundState(),
    val presets: List<Preset> = BUILT_IN_PRESETS,
    val profileLabel: ProfileLabel = ProfileLabel.Unsaved,
    val target: Target = Target.None,
    val notice: Notice? = null,
    val virtualizerAvailable: Boolean = true,
    val reverbAvailable: Boolean = true,
    val showReverb: Boolean = false,
    val showBalance: Boolean = false,
)

/**
 * What the sound screen shows. While Pegel is off nothing counts as broken: the engine is not
 * attached on purpose, so every "unavailable" would be a false alarm.
 */
fun buildSoundUi(
    settings: Settings,
    state: SoundState,
    customPresets: List<Preset>,
    active: Profile?,
    modified: Boolean,
    engine: EngineState,
    notificationsAllowed: Boolean,
): SoundUi {
    val status = engine.status.takeIf { settings.enabled }
    val notice = when {
        !settings.enabled -> null
        engine.failed -> Notice.ENGINE_FAILED
        status != null && !status.dynamics -> Notice.EQ_UNAVAILABLE
        !notificationsAllowed -> Notice.NOTIFICATIONS_OFF
        else -> null
    }
    return SoundUi(
        enabled = settings.enabled,
        state = state,
        presets = BUILT_IN_PRESETS + customPresets,
        profileLabel = active?.let { ProfileLabel.Named(it.name, modified) } ?: ProfileLabel.Unsaved,
        target = engine.target,
        notice = notice,
        virtualizerAvailable = status?.virtualizer ?: true,
        reverbAvailable = status?.reverb ?: true,
        showReverb = settings.showReverb,
        showBalance = settings.showBalance,
    )
}

/** Everything the sound screen can do to the sound — plain suspend functions, tested without a UI. */
class SoundActions(private val repo: SoundRepository) {

    suspend fun setGain(band: Int, db: Float) = repo.update { it.withGain(band, db) }

    suspend fun selectPreset(id: String) {
        val preset = BUILT_IN_PRESETS.firstOrNull { it.id == id }
            ?: repo.db.presets().all().map { with(SoundRepository) { it.toPreset() } }.firstOrNull { it.id == id }
            ?: return
        repo.update { it.withPreset(preset) }
    }

    /** Stores the current sliders under [name] and marks them as coming from that preset. */
    suspend fun saveAsPreset(name: String): Result<Long> {
        val result = repo.savePreset(name, repo.state.value.gainsDb)
        result.onSuccess { rowId -> repo.update { it.copy(presetId = "custom:$rowId") } }
        return result
    }

    suspend fun deletePreset(id: String) {
        repo.deletePreset(id)
        repo.update { if (it.presetId == id) it.copy(presetId = null) else it }
    }

    suspend fun resetEqualizer() = repo.update { it.withPreset(BUILT_IN_PRESETS.first()) }

    suspend fun mutate(transform: (SoundState) -> SoundState) = repo.update(transform)
}

/** 31.25 → "31", 1000 → "1k", 16000 → "16k". */
fun bandLabel(hz: Float): String = if (hz >= 1000f) "${(hz / 1000f).roundToInt()}k" else hz.toInt().toString()

/** "+3.5", "−2", "0" — a real minus sign, and no trailing ".0". */
fun gainLabel(db: Float, locale: Locale = Locale.getDefault()): String {
    if (db == 0f) return "0"
    val abs = kotlin.math.abs(db)
    val number = if (abs % 1f == 0f) abs.toInt().toString() else String.format(locale, "%.1f", abs)
    return (if (db > 0) "+" else "−") + number
}
