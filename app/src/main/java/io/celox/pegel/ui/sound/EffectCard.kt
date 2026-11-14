package io.celox.pegel.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.celox.pegel.R
import io.celox.pegel.audio.ReverbRoom
import io.celox.pegel.ui.common.SectionCard
import io.celox.pegel.ui.common.tabular
import kotlin.math.roundToInt

/** A switch and one strength slider — bass, loudness, virtualizer. */
@Composable
fun StrengthCard(
    title: String,
    icon: ImageVector,
    index: Int,
    on: Boolean,
    strength: Float,
    available: Boolean = true,
    note: String? = null,
    onToggle: (Boolean) -> Unit,
    onStrength: (Float) -> Unit,
) {
    SectionCard(
        title = title, icon = icon, index = index,
        trailing = { Switch(checked = on && available, onCheckedChange = onToggle, enabled = available) },
    ) {
        if (!available) {
            Unavailable()
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = strength, onValueChange = onStrength, enabled = on,
                modifier = Modifier.weight(1f).semantics { contentDescription = title },
            )
            Text(
                stringResource(R.string.percent, (strength * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge.tabular, textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 52.dp),
            )
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Unavailable(text: String = stringResource(R.string.effect_unavailable)) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReverbCard(icon: ImageVector, index: Int, room: ReverbRoom, available: Boolean, onRoom: (ReverbRoom) -> Unit) {
    SectionCard(title = stringResource(R.string.card_reverb), icon = icon, index = index) {
        if (!available) {
            Unavailable(stringResource(R.string.reverb_global_hint))
            return@SectionCard
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReverbRoom.entries.forEach { r ->
                FilterChip(selected = r == room, onClick = { onRoom(r) }, label = { Text(reverbLabel(r)) })
            }
        }
    }
}

@Composable
fun reverbLabel(room: ReverbRoom): String = stringResource(
    when (room) {
        ReverbRoom.NONE -> R.string.reverb_none
        ReverbRoom.SMALL_ROOM -> R.string.reverb_small_room
        ReverbRoom.MEDIUM_ROOM -> R.string.reverb_medium_room
        ReverbRoom.LARGE_ROOM -> R.string.reverb_large_room
        ReverbRoom.MEDIUM_HALL -> R.string.reverb_medium_hall
        ReverbRoom.LARGE_HALL -> R.string.reverb_large_hall
        ReverbRoom.PLATE -> R.string.reverb_plate
    }
)

/** -1 … +1; each side shows how much of it is left. Snaps to the centre nearby. */
@Composable
fun BalanceCard(icon: ImageVector, index: Int, balance: Float, onBalance: (Float) -> Unit) {
    val title = stringResource(R.string.card_balance)
    SectionCard(
        title = title, icon = icon, index = index,
        trailing = {
            if (balance != 0f) TextButton(onClick = { onBalance(0f) }) { Text(stringResource(R.string.eq_reset)) }
        },
    ) {
        Slider(
            value = balance,
            onValueChange = { onBalance(if (kotlin.math.abs(it) < 0.04f) 0f else (it * 20).roundToInt() / 20f) },
            valueRange = -1f..1f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val left = ((1f - balance.coerceAtLeast(0f)) * 100).roundToInt()
            val right = ((1f + balance.coerceAtMost(0f)) * 100).roundToInt()
            Text(stringResource(R.string.balance_left, left), style = MaterialTheme.typography.labelLarge.tabular)
            if (balance == 0f) {
                Text(stringResource(R.string.balance_center), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.balance_right, right), style = MaterialTheme.typography.labelLarge.tabular)
        }
    }
}
