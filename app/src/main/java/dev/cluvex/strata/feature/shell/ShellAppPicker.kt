package dev.cluvex.strata.feature.shell

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.strata.R
import dev.cluvex.strata.core.apk.InstalledApp
import dev.cluvex.strata.core.apk.InstalledApps
import dev.cluvex.strata.core.shell.ShellParams
import dev.cluvex.strata.ui.expressive.ExpressiveDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks which apps go into the {package} of a command; [onPick] gets well-formed package names. Each app has a check
 * box, and nothing runs until the Run button next to Cancel is pressed; it shows how many apps are chosen.
 */
@Composable
internal fun ShellAppPickerDialog(title: String, lastPackage: String?, onPick: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(emptyList<String>()) }
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = { RunButton(selected, onPick) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        text = { ShellAppPickerContent(lastPackage, selected, { selected = it }, onPick) },
    )
}

/** The same picker in a plain dialog, for the launcher shortcut's window, which has no app theme of its own. */
@Composable
internal fun ShellAppPickerPlainDialog(title: String, lastPackage: String?, onPick: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(emptyList<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { ShellAppPickerContent(lastPackage, selected, { selected = it }, onPick) },
        confirmButton = { RunButton(selected, onPick) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Nothing runs until this is pressed; it shows how many apps are chosen. */
@Composable
private fun RunButton(selected: List<String>, onPick: (List<String>) -> Unit) {
    TextButton(onClick = { onPick(selected) }, enabled = selected.isNotEmpty()) {
        Text(if (selected.isEmpty()) stringResource(R.string.shell_run) else stringResource(R.string.shell_param_run_selected, selected.size))
    }
}

@Composable
private fun ShellAppPickerContent(
    lastPackage: String?,
    selected: List<String>,
    onSelected: (List<String>) -> Unit,
    onPick: (List<String>) -> Unit,
) {
    fun toggle(pkg: String) = onSelected(if (pkg in selected) selected - pkg else selected + pkg)
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { InstalledApps(context.packageManager).list(includeSystem = true) }.getOrDefault(emptyList())
        }
    }
    var query by remember { mutableStateOf("") }
    // User apps alone by default. Both on shows everything; either one alone shows only that kind. The last used
    // app is listed first, but only under the kind it belongs to.
    var showUser by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    val q = query.trim()
    val shown = remember(apps, q, showUser, showSystem, lastPackage) {
        apps.orEmpty()
            .filter { a ->
                val kindOk = showUser == showSystem || (if (showSystem) a.system else !a.system)
                val textOk = q.isEmpty() || a.label.contains(q, ignoreCase = true) || a.packageName.contains(q, ignoreCase = true)
                textOk && kindOk
            }
            .sortedByDescending { it.packageName == lastPackage }
    }
    // A package that is not in the list (hidden from this app, or not installed) can still be typed in.
    val typed = q.takeIf { ShellParams.isValidPackage(it) && shown.none { a -> a.packageName == it } }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.shell_param_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChip(showUser, stringResource(R.string.shell_param_user)) { showUser = !showUser }
            KindChip(showSystem, stringResource(R.string.shell_param_system)) { showSystem = !showSystem }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            if (apps == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    if (typed != null) {
                        item(key = "typed") {
                            val on = typed in selected
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { toggle(typed) })
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.shell_param_use, typed), style = mono, modifier = Modifier.weight(1f))
                                Checkbox(checked = on, onCheckedChange = null)
                            }
                        }
                    }
                    if (shown.isEmpty() && typed == null) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.shell_param_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            )
                        }
                    }
                    items(shown, key = { it.packageName }) { a ->
                        val last = a.packageName == lastPackage
                        val on = a.packageName in selected
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(if (on) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .toggleable(value = on, role = Role.Checkbox, onValueChange = { toggle(a.packageName) })
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    a.label.firstOrNull()?.uppercase() ?: "?",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (last) a.label + " · " + stringResource(R.string.shell_param_last) else a.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    a.packageName,
                                    style = mono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(checked = on, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

/** A filter chip with the app's usual chip colors; a check mark shows while it is on. */
@Composable
private fun KindChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
    )
}
