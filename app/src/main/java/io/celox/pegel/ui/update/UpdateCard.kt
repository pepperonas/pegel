package io.celox.pegel.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.pegel.BuildConfig
import io.celox.pegel.R
import io.celox.pegel.container
import io.celox.pegel.update.InstallState
import io.celox.pegel.update.UpdateCheckStore
import io.celox.pegel.update.UpdateChecker
import io.celox.pegel.update.UpdateFailure

/** What the update card shows — decided here, free of Compose, so it can be tested. */
data class UpdateCard(
    /** The version on offer, or null when only a failure is left to show. */
    val version: String?,
    val phase: Phase,
    /** Whole percent while downloading with a known size. */
    val percent: Int?,
    val action: Action,
    val failure: UpdateFailure? = null,
) {
    enum class Phase { OFFER, DOWNLOADING, VERIFYING, PERMISSION, INSTALLING, FAILED }
    enum class Action { INSTALL, ALLOW, RETRY, NONE }
}

/** Null means: show nothing. [newer] is the version the last check found, if it is newer than this build. */
fun updateCard(newer: String?, state: InstallState): UpdateCard? = when (state) {
    InstallState.Idle -> newer?.let { UpdateCard(it, UpdateCard.Phase.OFFER, null, UpdateCard.Action.INSTALL) }
    is InstallState.Downloading -> UpdateCard(
        newer, UpdateCard.Phase.DOWNLOADING,
        state.progress?.let { (it * 100).toInt().coerceIn(0, 100) }, UpdateCard.Action.NONE,
    )
    InstallState.Verifying -> UpdateCard(newer, UpdateCard.Phase.VERIFYING, null, UpdateCard.Action.NONE)
    InstallState.NeedsPermission -> UpdateCard(newer, UpdateCard.Phase.PERMISSION, null, UpdateCard.Action.ALLOW)
    InstallState.Installing -> UpdateCard(newer, UpdateCard.Phase.INSTALLING, null, UpdateCard.Action.NONE)
    is InstallState.Failed -> UpdateCard(newer, UpdateCard.Phase.FAILED, null, UpdateCard.Action.RETRY, state.reason)
}

/**
 * The update offer: appears when a check found a newer version, installs it from inside the app.
 * Self-contained — it reads the finding and the installer itself, so any screen can place it.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier, onOpenFailed: () -> Unit = {}) {
    val context = LocalContext.current
    val installer = remember { context.container.updateInstaller }
    val tick by UpdateCheckStore.changes(context).collectAsStateWithLifecycle(initialValue = -1)
    val newer = remember(tick) { UpdateChecker.bannerVersion(context) }
    val state by installer.state.collectAsStateWithLifecycle()
    val card = updateCard(newer, state)

    // Back from Android's "Install unknown apps" screen: the update goes on by itself.
    LifecycleResumeEffect(Unit) {
        installer.onPermissionMaybeGranted()
        onPauseOrDispose { }
    }

    AnimatedVisibility(
        visible = card != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        // Keep the last card while it animates out.
        val shown = remember { arrayOfNulls<UpdateCard>(1) }
        if (card != null) shown[0] = card
        val c = shown[0] ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (c.version != null) stringResource(R.string.update_card_title, c.version)
                        else stringResource(R.string.update_card_title_plain),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    when (c.phase) {
                        UpdateCard.Phase.OFFER -> stringResource(R.string.update_card_body)
                        UpdateCard.Phase.DOWNLOADING ->
                            if (c.percent != null) stringResource(R.string.update_state_downloading, c.percent)
                            else stringResource(R.string.update_state_downloading_unknown)
                        UpdateCard.Phase.VERIFYING -> stringResource(R.string.update_state_verifying)
                        UpdateCard.Phase.PERMISSION -> stringResource(R.string.update_state_permission)
                        UpdateCard.Phase.INSTALLING -> stringResource(R.string.update_state_installing)
                        UpdateCard.Phase.FAILED -> stringResource(
                            when (c.failure) {
                                UpdateFailure.CHECKSUM -> R.string.update_failed_checksum
                                UpdateFailure.INSTALL -> R.string.update_failed_install
                                UpdateFailure.NO_RELEASE -> R.string.update_failed_no_release
                                else -> R.string.update_failed_network
                            }
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (c.phase) {
                    UpdateCard.Phase.DOWNLOADING ->
                        if (c.percent != null) {
                            LinearProgressIndicator(progress = { c.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    UpdateCard.Phase.VERIFYING, UpdateCard.Phase.INSTALLING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Unit
                }
                if (c.action != UpdateCard.Action.NONE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                when (c.action) {
                                    UpdateCard.Action.ALLOW -> {
                                        val settings = Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                        runCatching { context.startActivity(settings) }.onFailure { onOpenFailed() }
                                    }
                                    else -> installer.start(BuildConfig.VERSION_NAME)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                stringResource(
                                    when (c.action) {
                                        UpdateCard.Action.ALLOW -> R.string.update_action_allow
                                        UpdateCard.Action.RETRY -> R.string.update_action_retry
                                        else -> R.string.update_action_install
                                    }
                                )
                            )
                        }
                        if (c.phase == UpdateCard.Phase.FAILED) {
                            // The way out when the in-app path does not work on this phone.
                            TextButton(onClick = { if (!UpdateChecker.openDownload(context)) onOpenFailed() }) {
                                Text(stringResource(R.string.update_action_browser))
                            }
                            TextButton(onClick = installer::dismissFailure) {
                                Text(stringResource(R.string.update_action_dismiss))
                            }
                        }
                    }
                }
            }
        }
    }
}
