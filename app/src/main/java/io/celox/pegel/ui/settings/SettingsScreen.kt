package io.celox.pegel.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.pegel.R
import io.celox.pegel.audio.SessionMode
import io.celox.pegel.audio.Tuning
import io.celox.pegel.container
import io.celox.pegel.data.Settings
import io.celox.pegel.ui.about.AboutSection
import io.celox.pegel.ui.about.AppearanceSection
import io.celox.pegel.ui.about.UpdateSection
import io.celox.pegel.ui.common.SectionCard
import io.celox.pegel.ui.common.SwitchRow
import io.celox.pegel.ui.theme.ThemeMode
import io.celox.pegel.ui.theme.ThemeSettings
import kotlinx.coroutines.launch

/** Block sizes offered; the stored value may be anything in 10..1000 (a restored backup). */
val FRAME_CHOICES_MS = listOf(10, 20, 50, 100, 250, 500, 1000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenHelp: () -> Unit, backup: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val repo = context.container.repository
    val settings by repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun change(transform: (Settings) -> Settings) { scope.launch { repo.updateSettings(transform) } }

    val themeMode by ThemeSettings.mode(context).collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val dynamicColor by ThemeSettings.dynamicColor(context).collectAsStateWithLifecycle(false)

    val snackbar = remember { SnackbarHostState() }
    val openFailedText = stringResource(R.string.open_failed)
    fun openFailed() { scope.launch { snackbar.showSnackbar(openFailedText) } }
    fun start(intent: Intent) {
        try { context.startActivity(intent) } catch (_: Exception) { openFailed() }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.settings_title)) }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.settings_sound), Icons.Rounded.Tune, index = 0) {
                val t = settings.tuning
                ChoiceRow(
                    title = stringResource(R.string.settings_bass_cutoff), hint = stringResource(R.string.settings_bass_cutoff_hint),
                    value = t.bassCutoffHz, choices = Tuning.BASS_CUTOFFS_HZ, label = { stringResource(R.string.unit_hz, it) },
                ) { v -> change { it.copy(tuning = it.tuning.copy(bassCutoffHz = v)) } }
                ChoiceRow(
                    title = stringResource(R.string.settings_bass_max), hint = null,
                    value = t.bassMaxDb, choices = Tuning.MAX_GAINS_DB, label = { stringResource(R.string.unit_db, it) },
                ) { v -> change { it.copy(tuning = it.tuning.copy(bassMaxDb = v)) } }
                ChoiceRow(
                    title = stringResource(R.string.settings_loudness_max), hint = null,
                    value = t.loudnessMaxDb, choices = Tuning.MAX_GAINS_DB, label = { stringResource(R.string.unit_db, it) },
                ) { v -> change { it.copy(tuning = it.tuning.copy(loudnessMaxDb = v)) } }
                SwitchRow(stringResource(R.string.settings_show_reverb), stringResource(R.string.settings_show_reverb_hint), settings.showReverb) { on ->
                    change { it.copy(showReverb = on) }
                }
                SwitchRow(stringResource(R.string.settings_show_balance), stringResource(R.string.settings_show_balance_hint), settings.showBalance) { on ->
                    change { it.copy(showBalance = on) }
                }
            }

            SectionCard(stringResource(R.string.settings_connection), Icons.Rounded.Cable, index = 1) {
                ModeRow(SessionMode.AUTO, R.string.settings_mode_auto, R.string.settings_mode_auto_hint, settings.mode) { m -> change { it.copy(mode = m) } }
                ModeRow(SessionMode.GLOBAL_ONLY, R.string.settings_mode_global, R.string.settings_mode_global_hint, settings.mode) { m -> change { it.copy(mode = m) } }
                ModeRow(SessionMode.PLAYERS_ONLY, R.string.settings_mode_players, R.string.settings_mode_players_hint, settings.mode) { m -> change { it.copy(mode = m) } }
                ChoiceRow(
                    title = stringResource(R.string.settings_frame), hint = stringResource(R.string.settings_frame_hint),
                    value = settings.frameDurationMs, choices = (FRAME_CHOICES_MS + settings.frameDurationMs).distinct().sorted(),
                    label = { stringResource(R.string.unit_ms, it) },
                ) { v -> change { it.copy(frameDurationMs = v) } }
            }

            SectionCard(stringResource(R.string.settings_operation), Icons.Rounded.PowerSettingsNew, index = 2) {
                SwitchRow(stringResource(R.string.settings_boot), stringResource(R.string.settings_boot_hint), settings.startOnBoot) { on ->
                    change { it.copy(startOnBoot = on) }
                }
                var exempt by remember { mutableStateOf(isBatteryExempt(context)) }
                LifecycleResumeEffect(Unit) { exempt = isBatteryExempt(context); onPauseOrDispose { } }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.settings_battery), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (exempt) R.string.settings_battery_exempt else R.string.settings_battery_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exempt) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    if (!exempt) {
                        TextButton(onClick = {
                            start(Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                        }) { Text(stringResource(R.string.settings_battery_action)) }
                    }
                }
                LinkRow(stringResource(R.string.settings_notifications)) {
                    start(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }

            SectionCard(stringResource(R.string.settings_appearance), Icons.Rounded.Palette, index = 3) {
                AppearanceSection(
                    mode = themeMode, dynamicColor = dynamicColor,
                    onMode = { scope.launch { ThemeSettings.setMode(context, it) } },
                    onDynamicColor = { scope.launch { ThemeSettings.setDynamicColor(context, it) } },
                )
                if (Build.VERSION.SDK_INT >= 33) {
                    LinkRow(stringResource(R.string.settings_language)) {
                        start(Intent(SystemSettings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }
            }

            backup()

            SectionCard(stringResource(R.string.settings_updates), Icons.Rounded.SystemUpdate, index = 5) {
                UpdateSection(onOpenFailed = ::openFailed)
            }

            SectionCard(stringResource(R.string.settings_help), Icons.AutoMirrored.Rounded.HelpOutline, index = 6) {
                LinkRow(stringResource(R.string.settings_help_open), onClick = onOpenHelp)
            }

            SectionCard(stringResource(R.string.settings_about), Icons.Rounded.Info, index = 7) {
                AboutSection(onOpenFailed = ::openFailed)
            }
        }
    }
}

private fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** A setting with a handful of fixed values: the value on the right opens the list. */
@Composable
private fun ChoiceRow(
    title: String, hint: String?, value: Int, choices: List<Int>,
    label: @Composable (Int) -> String, onChoose: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.DropdownList) { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Box {
            Text(label(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { c ->
                    DropdownMenuItem(text = { Text(label(c)) }, onClick = { open = false; onChoose(c) })
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mode: SessionMode, title: Int, hint: Int, current: SessionMode, onSelect: (SessionMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = mode == current, role = Role.RadioButton) { onSelect(mode) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == current, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
