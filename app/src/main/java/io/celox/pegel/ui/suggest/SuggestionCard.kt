package io.celox.pegel.ui.suggest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.pegel.R
import io.celox.pegel.container
import io.celox.pegel.device.DeviceKind
import io.celox.pegel.device.DeviceType
import io.celox.pegel.device.DeviceWatcher
import io.celox.pegel.device.HeadphoneCatalog
import io.celox.pegel.device.HeadphoneModel
import io.celox.pegel.device.OutputDevice
import io.celox.pegel.device.startCurveDb
import io.celox.pegel.ui.profiles.deviceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The headphone table, parsed off the main thread; null until it is there. */
@Composable
private fun rememberCatalog(): HeadphoneCatalog? {
    val context = LocalContext.current
    val catalog by produceState<HeadphoneCatalog?>(null) {
        value = withContext(Dispatchers.Default) { context.container.headphones }
    }
    return catalog
}

@Composable
fun deviceTypeLabel(type: DeviceType): String = stringResource(
    when (type) {
        DeviceType.HEADPHONES -> R.string.device_type_headphones
        DeviceType.SPEAKER_SMALL -> R.string.device_type_speaker_small
        DeviceType.SPEAKER_LARGE -> R.string.device_type_speaker_large
        DeviceType.CAR -> R.string.device_type_car
        DeviceType.PHONE_SPEAKER -> R.string.device_type_phone
    }
)

/** Offers a sound for the output that just became active — once per device, until answered. */
@Composable
fun SuggestionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { context.container.repository }
    val watcher = remember { DeviceWatcher(context) }
    val connected by watcher.active.collectAsStateWithLifecycle(initialValue = watcher.current())
    val devices by repo.devices.collectAsStateWithLifecycle(initialValue = null)
    val catalog = rememberCatalog()

    val stored = devices
    val suggestion = if (stored == null) Suggestion.None
    else suggestionFor(connected, stored.firstOrNull { it.key == connected?.key }, catalog)

    AnimatedVisibility(
        visible = suggestion != Suggestion.None,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        // Keep the last card while it animates out.
        val shown = remember { arrayOfNulls<Suggestion>(1) }
        if (suggestion != Suggestion.None) shown[0] = suggestion
        val s = shown[0] ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) { SuggestionPanel(s, catalog, active = true, onDone = {}) }
        }
    }
}

/** The same offer on request, for any stored device — connected or not. */
@Composable
fun SuggestionDialog(device: OutputDevice, active: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { context.container.repository }
    val devices by repo.devices.collectAsStateWithLifecycle(initialValue = null)
    val catalog = rememberCatalog()
    val stored = devices
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.suggest_close)) } },
        text = {
            Column {
                if (stored != null) {
                    val s = suggestionFor(device, stored.firstOrNull { it.key == device.key }, catalog, askAgain = true)
                    SuggestionPanel(s, catalog, active = active, onDone = onClose, dismissable = false)
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionPanel(
    s: Suggestion,
    catalog: HeadphoneCatalog?,
    active: Boolean,
    onDone: () -> Unit,
    dismissable: Boolean = true,
) {
    val context = LocalContext.current
    val repo = remember { context.container.repository }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }

    val device = when (s) {
        is Suggestion.Headphones -> s.device
        is Suggestion.StartCurve -> s.device
        is Suggestion.AskType -> s.device
        is Suggestion.PickModel -> s.device
        Suggestion.None -> return
    }
    val label = deviceLabel(device.name, device.kind)
    val storedLabel = device.name.trim()

    fun apply(profileName: String, gains: List<Float>) {
        scope.launch {
            repo.applySuggestion(device.key, storedLabel, profileName, gains, load = active)
            onDone()
        }
    }
    fun applyModel(m: HeadphoneModel) = apply(storedLabel.ifBlank { m.name }, m.correctionDb())
    fun setType(type: DeviceType?) { scope.launch { repo.setDeviceType(device.key, storedLabel, type?.name) } }
    fun dismiss() { scope.launch { repo.dismissSuggestion(device.key, storedLabel); onDone() } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (device.kind == DeviceKind.SPEAKER) label else stringResource(R.string.suggest_title_device, label),
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            when (s) {
                is Suggestion.Headphones -> stringResource(R.string.suggest_headphones_text, s.model.name, s.model.source)
                is Suggestion.StartCurve -> stringResource(R.string.suggest_start_text, deviceTypeLabel(s.type))
                is Suggestion.AskType -> stringResource(R.string.suggest_ask_text)
                is Suggestion.PickModel -> stringResource(R.string.suggest_pick_text)
                Suggestion.None -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (s) {
                is Suggestion.Headphones -> {
                    Button(onClick = { applyModel(s.model) }) { Text(stringResource(R.string.suggest_apply)) }
                    FilledTonalButton(onClick = { searching = true }) { Text(stringResource(R.string.suggest_other_model)) }
                }
                is Suggestion.StartCurve -> {
                    val typeName = deviceTypeLabel(s.type)
                    Button(onClick = { apply(storedLabel.ifBlank { typeName }, startCurveDb(s.type).orEmpty()) }) {
                        Text(stringResource(R.string.suggest_apply))
                    }
                    if (s.type != DeviceType.PHONE_SPEAKER) {
                        FilledTonalButton(onClick = { setType(null) }) { Text(stringResource(R.string.suggest_other_type)) }
                    }
                }
                is Suggestion.AskType -> {
                    for (type in listOf(DeviceType.HEADPHONES, DeviceType.SPEAKER_SMALL, DeviceType.SPEAKER_LARGE, DeviceType.CAR)) {
                        FilledTonalButton(onClick = { setType(type) }) { Text(deviceTypeLabel(type)) }
                    }
                }
                is Suggestion.PickModel -> {
                    Button(onClick = { searching = true }) { Text(stringResource(R.string.suggest_search_model)) }
                    FilledTonalButton(onClick = { setType(null) }) { Text(stringResource(R.string.suggest_other_type)) }
                }
                Suggestion.None -> {}
            }
            if (dismissable) TextButton(onClick = ::dismiss) { Text(stringResource(R.string.suggest_not_now)) }
        }
    }

    if (searching && catalog != null) {
        ModelSearchDialog(
            catalog = catalog,
            first = (s as? Suggestion.Headphones)?.let { listOf(it.model) + it.alternatives }.orEmpty(),
            onPick = { searching = false; applyModel(it) },
            onClose = { searching = false },
        )
    }
}

@Composable
private fun ModelSearchDialog(
    catalog: HeadphoneCatalog,
    first: List<HeadphoneModel>,
    onPick: (HeadphoneModel) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { if (query.isBlank()) first else catalog.search(query) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.suggest_search_title)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.suggest_close)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.suggest_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (results.isEmpty()) {
                    Text(
                        stringResource(if (query.isBlank()) R.string.suggest_search_start else R.string.suggest_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.name }) { m ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 10.dp)) {
                            Text(m.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.suggest_measured_by, m.source),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}
