package io.celox.pegel.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.celox.pegel.R
import io.celox.pegel.container
import io.celox.pegel.data.BackupCodec
import io.celox.pegel.data.BackupData
import io.celox.pegel.data.BackupError
import io.celox.pegel.data.BackupException
import io.celox.pegel.data.backupFileName
import io.celox.pegel.data.readDocument
import io.celox.pegel.data.writeDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What went wrong with a picked file, in words. [expectingBackup]: which of the two kinds was expected. */
@Composable
fun backupErrorText(error: Throwable?, expectingBackup: Boolean): String = stringResource(
    when (val e = (error as? BackupException)?.error) {
        BackupError.Empty -> R.string.backup_error_empty
        BackupError.NotJson -> R.string.backup_error_not_json
        BackupError.NotPegel -> R.string.backup_error_not_pegel
        BackupError.WrongKind -> if (expectingBackup) R.string.backup_error_kind_backup else R.string.backup_error_kind_profile
        is BackupError.NewerFormat -> R.string.backup_error_newer
        null -> R.string.backup_error_read
    }
)

/**
 * Create and restore a backup through the system's file picker — no storage permission. A restore
 * is decoded first and only applied after the user confirmed what is about to be replaced.
 */
@Composable
fun BackupSection(notify: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.container.repository
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<BackupData?>(null) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var failed by remember { mutableStateOf(false) }

    val exported = stringResource(R.string.backup_exported)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val restored = stringResource(R.string.backup_restored)

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { writeDocument(context, uri, repo.backupText()) }
            notify(if (ok) exported else exportFailed)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readDocument(context, uri) }
            val result = text?.let(BackupCodec::decode)
            if (result?.isSuccess == true) pending = result.getOrNull()
            else { failure = result?.exceptionOrNull(); failed = true }
        }
    }

    Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { create.launch(backupFileName()) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.backup_export), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.backup_restore), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    pending?.let { data ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_text, data.profiles.size, data.presets.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch { repo.importAll(data); notify(restored) }
                }) { Text(stringResource(R.string.backup_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
    if (failed) {
        val message = backupErrorText(failure, expectingBackup = true)
        AlertDialog(
            onDismissRequest = { failed = false },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failed = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
