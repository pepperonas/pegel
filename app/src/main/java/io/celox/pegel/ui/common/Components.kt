package io.celox.pegel.ui.common

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.celox.pegel.R
import io.celox.pegel.data.NAME_MAX
import io.celox.pegel.data.NameError
import io.celox.pegel.ui.theme.springEntrance

/** Numbers that change in place keep their width (tabular figures). */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

/** A titled card; [trailing] sits at the end of the title row (a switch, usually). */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    index: Int,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().springEntrance(index),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    title, style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/** A whole row that toggles: label (and an optional hint) on the left, the switch on the right. */
@Composable
fun SwitchRow(title: String, hint: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun nameErrorText(error: NameError?): String? = when (error) {
    NameError.EMPTY -> stringResource(R.string.error_name_empty)
    NameError.DUPLICATE -> stringResource(R.string.error_name_duplicate)
    null -> null
}

/**
 * Asks for a name. [onConfirm] returns the error to show, or null when the name was accepted —
 * then the dialog closes.
 */
@Composable
fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onConfirm: suspend (String) -> NameError?) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf<NameError?>(null) }
    val scope = rememberCoroutineScope()
    // The dialog exists to type a name: the field has the cursor and the keyboard is up at once.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() { scope.launch { error = onConfirm(text); if (error == null) onDismiss() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(NAME_MAX); error = null },
                label = { Text(stringResource(R.string.dialog_name_label)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
                // "Done" on the keyboard (or Enter) saves — no reaching for the button.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                isError = error != null,
                supportingText = nameErrorText(error)?.let { { Text(it) } },
            )
        },
        confirmButton = {
            TextButton(onClick = ::submit) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

/** A player's name as the launcher shows it; its package name if Android will not tell. */
fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: Exception) {
    packageName
}
