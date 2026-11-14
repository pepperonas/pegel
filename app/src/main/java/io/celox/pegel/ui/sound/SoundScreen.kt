package io.celox.pegel.ui.sound

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.celox.pegel.R
import io.celox.pegel.audio.Preset
import io.celox.pegel.audio.Target
import io.celox.pegel.container
import io.celox.pegel.service.ServiceStarter
import io.celox.pegel.ui.common.NameDialog
import io.celox.pegel.ui.common.appLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(onOpenProfiles: () -> Unit) {
    val context = LocalContext.current
    val vm: SoundViewModel = viewModel(
        factory = SoundViewModel.factory(context.container) { ServiceStarter.sync(context.applicationContext, it) }
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    // Coming back from the system settings may have changed this.
    LifecycleResumeEffect(Unit) {
        vm.setNotificationsAllowed(NotificationManagerCompat.from(context).areNotificationsEnabled())
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotificationsAllowed(granted)
    }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var showPresets by rememberSaveable { mutableStateOf(false) }
    var askPresetName by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val masterLabel = stringResource(R.string.sound_master_switch)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = {
                    Text(
                        stringResource(if (ui.enabled) R.string.sound_master_on else R.string.sound_master_off),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    Switch(
                        checked = ui.enabled,
                        onCheckedChange = { on ->
                            vm.setEnabled(on)
                            if (on && !NotificationManagerCompat.from(context).areNotificationsEnabled()) requestNotifications()
                        },
                        modifier = Modifier.padding(end = 16.dp).semantics { contentDescription = masterLabel },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.enabled) StatusRow(ui, onOpenProfiles) else OffHint()
            ui.notice?.let { NoticeBanner(it, onAllowNotifications = ::requestNotifications) }

            val s = ui.state
            EqCard(
                state = s, presets = ui.presets, index = 0,
                onToggle = { on -> vm.mutate { it.copy(eqOn = on) } },
                onGain = vm::setGain,
                onOpenPresets = { showPresets = true },
                onSavePreset = { askPresetName = true },
                onReset = vm::resetEqualizer,
            )
            StrengthCard(
                title = stringResource(R.string.card_bass), icon = Icons.Rounded.Speaker, index = 1,
                on = s.bassOn, strength = s.bassStrength,
                onToggle = { on -> vm.mutate { it.copy(bassOn = on) } },
                onStrength = { v -> vm.mutate { it.copy(bassStrength = v) } },
            )
            StrengthCard(
                title = stringResource(R.string.card_loudness), icon = Icons.Rounded.VolumeUp, index = 2,
                on = s.loudnessOn, strength = s.loudnessStrength,
                note = stringResource(R.string.loudness_warning),
                onToggle = { on -> vm.mutate { it.copy(loudnessOn = on) } },
                onStrength = { v -> vm.mutate { it.copy(loudnessStrength = v) } },
            )
            StrengthCard(
                title = stringResource(R.string.card_virtualizer), icon = Icons.Rounded.SurroundSound, index = 3,
                on = s.virtualizerOn, strength = s.virtualizerStrength, available = ui.virtualizerAvailable,
                onToggle = { on -> vm.mutate { it.copy(virtualizerOn = on) } },
                onStrength = { v -> vm.mutate { it.copy(virtualizerStrength = v) } },
            )
            if (ui.showReverb) {
                ReverbCard(
                    icon = Icons.Rounded.Waves, index = 4, room = s.reverb, available = ui.reverbAvailable,
                    onRoom = { room -> vm.mutate { it.copy(reverb = room) } },
                )
            }
            if (ui.showBalance) {
                BalanceCard(
                    icon = Icons.Rounded.CompareArrows, index = 5, balance = s.balance,
                    onBalance = { b -> vm.mutate { it.copy(balance = b) } },
                )
            }
        }
    }

    if (showPresets) {
        PresetSheet(
            presets = ui.presets, selectedId = ui.state.presetId,
            onSelect = { vm.selectPreset(it); showPresets = false },
            onDelete = vm::deletePreset,
            onDismiss = { showPresets = false },
        )
    }
    if (askPresetName) {
        NameDialog(
            title = stringResource(R.string.eq_save_preset),
            onDismiss = { askPresetName = false },
            onConfirm = { vm.saveAsPreset(it) },
        )
    }
}

/** Where Pegel is attached and which profile is playing — tapping leads to the profiles. */
@Composable
private fun StatusRow(ui: SoundUi, onOpenProfiles: () -> Unit) {
    val context = LocalContext.current
    val target = when (val t = ui.target) {
        Target.GlobalMix -> stringResource(R.string.target_global)
        is Target.Players -> t.packages.joinToString { appLabel(context, it) }
        Target.None -> stringResource(if (ui.notice == Notice.ENGINE_FAILED) R.string.target_failed else R.string.target_waiting)
    }
    val profile = when (val p = ui.profileLabel) {
        ProfileLabel.Unsaved -> stringResource(R.string.profile_unsaved)
        is ProfileLabel.Named -> if (p.modified) stringResource(R.string.profile_modified, p.name) else p.name
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfiles),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(target, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(profile, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OffHint() {
    Text(
        stringResource(R.string.sound_off_hint),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun NoticeBanner(notice: Notice, onAllowNotifications: () -> Unit) {
    val serious = notice != Notice.NOTIFICATIONS_OFF
    Surface(
        color = if (serious) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (serious) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(
                    when (notice) {
                        Notice.ENGINE_FAILED -> R.string.notice_engine_failed
                        Notice.EQ_UNAVAILABLE -> R.string.notice_eq_unavailable
                        Notice.NOTIFICATIONS_OFF -> R.string.notice_notifications_off
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (notice == Notice.NOTIFICATIONS_OFF && Build.VERSION.SDK_INT >= 33) {
                TextButton(onClick = onAllowNotifications, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.notice_notifications_allow))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(
    presets: List<Preset>, selectedId: String?,
    onSelect: (String) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.presets_title), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(presets, key = { it.id }) { preset ->
                val label = presetLabel(preset)
                ListItem(
                    headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        if (preset.id == selectedId) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Transparent)
                    },
                    trailingContent = if (preset.builtIn) null else {
                        {
                            IconButton(onClick = { onDelete(preset.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.presets_delete, label))
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSelect(preset.id) },
                )
            }
        }
    }
}
