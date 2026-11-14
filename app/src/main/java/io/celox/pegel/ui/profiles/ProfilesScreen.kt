package io.celox.pegel.ui.profiles

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.celox.pegel.R
import io.celox.pegel.container
import io.celox.pegel.data.Profile
import io.celox.pegel.data.readDocument
import io.celox.pegel.data.shareProfileIntent
import io.celox.pegel.device.DeviceKind
import io.celox.pegel.device.DeviceWatcher
import io.celox.pegel.ui.common.NameDialog
import io.celox.pegel.ui.common.SectionCard
import io.celox.pegel.ui.settings.backupErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen() {
    val context = LocalContext.current
    val watcher = remember { DeviceWatcher(context) }
    val vm: ProfilesViewModel = viewModel(factory = ProfilesViewModel.factory(context.container, watcher.active))
    val ui by vm.ui.collectAsStateWithLifecycle()

    var askNew by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    val repo = context.container.repository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val imported = stringResource(R.string.profiles_imported)
    val shareTitle = stringResource(R.string.profiles_share_title)
    val openFailed = stringResource(R.string.open_failed)
    var importFailure by remember { mutableStateOf<Throwable?>(null) }
    var importFailed by remember { mutableStateOf(false) }

    val pickProfile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readDocument(context, uri) }
            val result = text?.let { repo.importProfile(it) }
            if (result?.isSuccess == true) snackbar.showSnackbar(imported)
            else { importFailure = result?.exceptionOrNull(); importFailed = true }
        }
    }
    fun share(profile: Profile) {
        scope.launch {
            val text = repo.profileText(profile.id) ?: return@launch
            try {
                val send = withContext(Dispatchers.IO) { shareProfileIntent(context, profile.name, text) }
                context.startActivity(Intent.createChooser(send, shareTitle))
            } catch (_: Exception) {
                snackbar.showSnackbar(openFailed)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.changed?.let { ChangedBanner(it.name, onSave = vm::save, onDiscard = vm::discard) }

            SectionCard(title = stringResource(R.string.profiles_section), icon = Icons.Rounded.LibraryMusic, index = 0) {
                if (ui.rows.isEmpty()) {
                    Text(
                        stringResource(R.string.profiles_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProfileItem(
                        row, onLoad = { vm.load(row.profile.id) },
                        onShare = { share(row.profile) },
                        onRename = { renaming = row.profile }, onDelete = { deleting = row.profile },
                    )
                }
                Button(onClick = { askNew = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    // Wraps instead of cutting off: at large font sizes the label does not fit one line.
                    Text(stringResource(R.string.profiles_save_new), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { pickProfile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.profiles_import), textAlign = TextAlign.Center) }
            }

            SectionCard(title = stringResource(R.string.devices_section), icon = Icons.Rounded.Devices, index = 1) {
                Text(
                    stringResource(R.string.devices_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.devices.forEachIndexed { i, device ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DeviceItem(
                        device, profiles = ui.rows.map { it.profile },
                        onBind = { vm.bind(device, it) }, onForget = { vm.forget(device.key) },
                    )
                }
                if (ui.devices.size > 1) {
                    Text(
                        stringResource(R.string.devices_same_name_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (importFailed) {
        val message = backupErrorText(importFailure, expectingBackup = false)
        AlertDialog(
            onDismissRequest = { importFailed = false },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importFailed = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    if (askNew) {
        NameDialog(stringResource(R.string.profiles_new_title), onDismiss = { askNew = false }, onConfirm = { vm.saveAs(it) })
    }
    renaming?.let { p ->
        NameDialog(
            stringResource(R.string.profiles_rename_title), initial = p.name,
            onDismiss = { renaming = null }, onConfirm = { vm.rename(p.id, it) },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profiles_delete_title, p.name)) },
            text = { Text(stringResource(R.string.profiles_delete_text)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(p.id); deleting = null }) { Text(stringResource(R.string.profiles_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun ChangedBanner(name: String, onSave: () -> Unit, onDiscard: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profiles_changed_title, name), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(stringResource(R.string.profiles_save_changes)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.profiles_discard)) }
            }
        }
    }
}

@Composable
private fun ProfileItem(row: ProfileRow, onLoad: () -> Unit, onShare: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onLoad).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (row.modified) stringResource(R.string.profile_modified, row.profile.name) else row.profile.name,
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (row.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            val bound = row.boundTo.map { deviceLabel(it.label, io.celox.pegel.device.OutputDevice.kindOfKey(it.key)) }
            val sub = listOfNotNull(
                stringResource(R.string.profiles_active).takeIf { row.active },
                bound.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.profiles_bound_to, it.joinToString()) },
            )
            if (sub.isNotEmpty()) {
                Text(
                    sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.profiles_menu, row.profile.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.profiles_share)) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text(stringResource(R.string.profiles_rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.profiles_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun DeviceItem(device: DeviceRow, profiles: List<Profile>, onBind: (Long?) -> Unit, onForget: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val label = deviceLabel(device.label, device.kind)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(kindIcon(device.kind), contentDescription = null, modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (device.connected) {
                    Text(stringResource(R.string.devices_connected), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (device.forgettable && !device.connected) {
                IconButton(onClick = onForget) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.devices_forget, label))
                }
            }
        }
        Box {
            FilledTonalButton(onClick = { menu = true }, enabled = profiles.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        profiles.isEmpty() -> stringResource(R.string.devices_need_profile)
                        else -> profiles.firstOrNull { it.id == device.profileId }?.name ?: stringResource(R.string.devices_no_profile)
                    },
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (profiles.isNotEmpty()) Icon(Icons.Rounded.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.devices_no_profile)) }, onClick = { menu = false; onBind(null) })
                profiles.forEach { p ->
                    DropdownMenuItem(text = { Text(p.name) }, onClick = { menu = false; onBind(p.id) })
                }
            }
        }
    }
}

/** A device's own name, or what kind of output it is when it has none. */
@Composable
fun deviceLabel(name: String, kind: DeviceKind): String = when {
    kind == DeviceKind.SPEAKER -> stringResource(R.string.device_speaker)
    name.isNotBlank() -> name
    else -> stringResource(
        when (kind) {
            DeviceKind.WIRED -> R.string.device_kind_wired
            DeviceKind.USB -> R.string.device_kind_usb
            DeviceKind.BLUETOOTH -> R.string.device_kind_bluetooth
            else -> R.string.device_kind_other
        }
    )
}

private fun kindIcon(kind: DeviceKind): ImageVector = when (kind) {
    DeviceKind.SPEAKER -> Icons.Rounded.Speaker
    DeviceKind.WIRED -> Icons.Rounded.Headphones
    DeviceKind.USB -> Icons.Rounded.Usb
    DeviceKind.BLUETOOTH -> Icons.Rounded.Bluetooth
    DeviceKind.OTHER -> Icons.Rounded.SpeakerGroup
}
