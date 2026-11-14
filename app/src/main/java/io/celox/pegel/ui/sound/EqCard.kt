package io.celox.pegel.ui.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.celox.pegel.R
import io.celox.pegel.audio.BAND_CENTERS_HZ
import io.celox.pegel.audio.BAND_COUNT
import io.celox.pegel.audio.GAIN_LIMIT_DB
import io.celox.pegel.audio.GAIN_STEP_DB
import io.celox.pegel.audio.Preset
import io.celox.pegel.audio.SoundState
import io.celox.pegel.ui.common.SectionCard
import io.celox.pegel.ui.common.tabular

/** Narrower than this a slider is hard to hit; the row scrolls sideways instead of squeezing. */
private val MIN_SLOT = 28.dp
private val SLIDER_HEIGHT = 176.dp

@Composable
fun EqCard(
    state: SoundState,
    presets: List<Preset>,
    index: Int,
    onToggle: (Boolean) -> Unit,
    onGain: (band: Int, db: Float) -> Unit,
    onOpenPresets: () -> Unit,
    onSavePreset: () -> Unit,
    onReset: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.card_equalizer),
        icon = Icons.Rounded.Equalizer,
        index = index,
        trailing = { Switch(checked = state.eqOn, onCheckedChange = onToggle) },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slot = maxOf(maxWidth / BAND_COUNT, MIN_SLOT)
            val scrolls = slot * BAND_COUNT > maxWidth
            val content = @Composable {
                Column(Modifier.width(slot * BAND_COUNT).alpha(if (state.eqOn) 1f else 0.5f)) {
                    // The curve bends above the slider centres: half a slot of inset on each side.
                    CurveView(
                        gainsDb = state.gainsDb, active = state.eqOn,
                        sideInsetFraction = 0.5f / BAND_COUNT,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        for (band in 0 until BAND_COUNT) {
                            BandColumn(
                                width = slot, hz = BAND_CENTERS_HZ[band], db = state.gainsDb[band],
                                enabled = state.eqOn, onChange = { onGain(band, it) },
                            )
                        }
                    }
                }
            }
            if (scrolls) Row(Modifier.horizontalScroll(rememberScrollState())) { content() } else content()
        }

        val current = presets.firstOrNull { it.id == state.presetId }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onOpenPresets, modifier = Modifier.weight(1f)) {
                Text(
                    current?.let { presetLabel(it) } ?: stringResource(R.string.eq_preset_custom),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.ExpandMore, contentDescription = null)
            }
            if (current == null) {
                TextButton(onClick = onSavePreset) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.eq_save_preset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else if (current.id != "flat") {
                TextButton(onClick = onReset) { Text(stringResource(R.string.eq_reset)) }
            }
        }
    }
}

@Composable
private fun BandColumn(width: Dp, hz: Float, db: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            gainLabel(db), style = MaterialTheme.typography.labelSmall.tabular, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        VerticalGainSlider(
            db = db, enabled = enabled, onChange = onChange, label = bandLabel(hz),
            modifier = Modifier.width(width).height(SLIDER_HEIGHT),
        )
        Text(bandLabel(hz), style = MaterialTheme.typography.labelSmall.tabular, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * One band: a vertical track filled from the zero line to the thumb. Drag or tap anywhere in the
 * column; for TalkBack it is an adjustable value from −12 to +12 dB in half-decibel steps.
 */
@Composable
private fun VerticalGainSlider(db: Float, enabled: Boolean, label: String, onChange: (Float) -> Unit, modifier: Modifier) {
    val onChangeNow by rememberUpdatedState(onChange)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val thumb = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.eq_band_description, label)
    val stateText = stringResource(R.string.eq_gain_state, gainLabel(db))
    val steps = (2 * GAIN_LIMIT_DB / GAIN_STEP_DB).toInt() - 1

    Canvas(
        modifier
            .semantics {
                contentDescription = description
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(db, -GAIN_LIMIT_DB..GAIN_LIMIT_DB, steps)
                if (enabled) setProgress { target -> onChangeNow(target); true } else disabled()
            }
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        val pad = THUMB_HEIGHT.toPx() / 2
                        fun at(y: Float): Float {
                            val usable = size.height - 2 * pad
                            val t = 1f - ((y - pad) / usable).coerceIn(0f, 1f)
                            return SoundState.snapGain(-GAIN_LIMIT_DB + t * 2 * GAIN_LIMIT_DB)
                        }
                        detectVerticalDragGestures(
                            onDragStart = { onChangeNow(at(it.y)) },
                        ) { change, _ -> change.consume(); onChangeNow(at(change.position.y)) }
                    }
                    .pointerInput(Unit) {
                        val pad = THUMB_HEIGHT.toPx() / 2
                        detectTapGestures { o ->
                            val usable = size.height - 2 * pad
                            val t = 1f - ((o.y - pad) / usable).coerceIn(0f, 1f)
                            onChangeNow(SoundState.snapGain(-GAIN_LIMIT_DB + t * 2 * GAIN_LIMIT_DB))
                        }
                    }
            )
    ) {
        val pad = THUMB_HEIGHT.toPx() / 2
        val usable = size.height - 2 * pad
        val cx = size.width / 2
        val trackW = 8.dp.toPx()
        val zeroY = pad + usable / 2
        val thumbY = pad + usable * (1f - (db + GAIN_LIMIT_DB) / (2 * GAIN_LIMIT_DB))
        drawRoundRect(track, Offset(cx - trackW / 2, pad), Size(trackW, usable), CornerRadius(trackW / 2))
        val top = minOf(zeroY, thumbY)
        drawRoundRect(fill, Offset(cx - trackW / 2, top), Size(trackW, kotlin.math.abs(thumbY - zeroY)), CornerRadius(trackW / 2))
        // zero tick
        drawLine(track, Offset(cx - trackW * 1.5f, zeroY), Offset(cx + trackW * 1.5f, zeroY), strokeWidth = 2.dp.toPx())
        val tw = minOf(size.width - 8.dp.toPx(), 26.dp.toPx())
        val th = THUMB_HEIGHT.toPx()
        drawRoundRect(thumb, Offset(cx - tw / 2, thumbY - th / 2), Size(tw, th), CornerRadius(th / 2))
    }
}

private val THUMB_HEIGHT = 14.dp

@Composable
fun presetLabel(preset: Preset): String {
    if (!preset.builtIn) return preset.name
    val res = when (preset.id) {
        "flat" -> R.string.preset_flat
        "bass" -> R.string.preset_bass
        "treble" -> R.string.preset_treble
        "vocal" -> R.string.preset_vocal
        "rock" -> R.string.preset_rock
        "pop" -> R.string.preset_pop
        "jazz" -> R.string.preset_jazz
        "classical" -> R.string.preset_classical
        "electronic" -> R.string.preset_electronic
        "hiphop" -> R.string.preset_hiphop
        "acoustic" -> R.string.preset_acoustic
        "vshape" -> R.string.preset_vshape
        "speech" -> R.string.preset_speech
        else -> return preset.name
    }
    return stringResource(res)
}
