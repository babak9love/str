package dev.cluvex.strata.feature.shell

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cluvex.strata.R
import dev.cluvex.strata.core.shell.ActiveRun
import dev.cluvex.strata.core.shell.ShellMode
import dev.cluvex.strata.core.shell.ShellRunTracker
import dev.cluvex.strata.core.shell.ShellRunner
import dev.cluvex.strata.ui.expressive.ExpressiveDialog
import dev.cluvex.strata.ui.rememberTextClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How close to the end (in pixels) still counts as "at the end". */
private const val TAIL_SLACK_PX = 24

/**
 * A scroll state for a log: it shows the end of the content as soon as it is laid out, and keeps following the end
 * while the content grows. Once the user scrolls up it stops following, until they scroll back down to the end.
 */
@Composable
internal fun rememberTailScrollState(enabled: Boolean = true): ScrollState {
    val scroll = rememberScrollState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(scroll, enabled) {
        if (!enabled) return@LaunchedEffect
        // A log that is opened again starts at its end, whatever the user did the last time.
        follow = true
        launch {
            snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
                if (!moving) follow = scroll.value >= scroll.maxValue - TAIL_SLACK_PX
            }
        }
        snapshotFlow { scroll.maxValue }.collect { end -> if (follow) scroll.scrollTo(end) }
    }
    return scroll
}

/** Selectable monospace log text in a scrolling box that follows its end. */
@Composable
internal fun LiveOutputText(text: String, modifier: Modifier = Modifier, maxHeight: Dp = 320.dp) {
    val scroll = rememberTailScrollState()
    Box(modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(scroll)) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
            )
        }
    }
}

/**
 * The output of a command that is running right now. Closing it does not stop the command; Stop does.
 * When the command ends (finished, timed out or stopped) the same dialog stays open and shows the final status
 * and output ([finished]), like a background command's log does, instead of closing and opening a result dialog.
 */
@Composable
internal fun LiveOutputDialog(commandId: String, finished: ShellRun?, onDismiss: () -> Unit) {
    val runs by ShellRunTracker.runs.collectAsState()
    val live = runs[commandId]
    // The tracker drops the run a moment before the finished result arrives; keep the last text so nothing flashes empty.
    var last by remember { mutableStateOf<ActiveRun?>(null) }
    if (live != null) last = live
    val shown = live ?: last
    val result = finished?.result
    val text = if (result != null) {
        result.output.ifEmpty { if (result.backend == null) "" else stringResource(R.string.shell_no_output) }
    } else {
        shown?.output.orEmpty()
    }
    val clipboard = rememberTextClipboard()
    val filter = rememberOutputFilter()
    val scope = rememberCoroutineScope()
    var stopping by remember { mutableStateOf(false) }
    // If the stop did not work, the button comes back after a few seconds.
    LaunchedEffect(stopping) {
        if (stopping) {
            delay(4_000)
            stopping = false
        }
    }
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = finished?.command?.name ?: shown?.name.orEmpty(),
        icon = Icons.Rounded.Terminal,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        // Stop sits with Copy and Close, in the same row of the dialog.
        dismissButton = {
            Row {
                if (finished == null) {
                    TextButton(
                        onClick = {
                            stopping = true
                            scope.launch { ShellRunner.stopRun(commandId, shown?.mode ?: ShellMode.AUTO) }
                        },
                        enabled = shown != null && !stopping,
                    ) { Text(stringResource(R.string.shell_log_stop), color = MaterialTheme.colorScheme.error) }
                }
                if (text.isNotEmpty()) {
                    OutputFilterButton(filter)
                    TextButton(onClick = { clipboard.setText(text) }) { Text(stringResource(R.string.common_copy)) }
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result != null) {
                // The same words and colors as the history: Success / Failed / Timed out / Stopped.
                val outcome = shellOutcome(false, result.timedOut, result.stopped, result.exitCode)
                val status = shellStatusLine(outcome, result.backend?.name, result.exitCode, result.durationMs)
                Text(
                    shellStatusAnnotated(outcome, status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shell_log_running), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            OutputFilterField(filter, text)
            if (result == null || text.isNotEmpty()) LiveOutputText(filter.apply(text).ifEmpty { "…" })
        }
    }
}

/** A line filter for a command's output (like grep): keeps only the lines that contain the typed text. */
internal class OutputFilter {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")

    fun toggle() {
        open = !open
        if (!open) query = ""
    }

    fun apply(text: String): String {
        val q = query.trim()
        if (!open || q.isEmpty()) return text
        return text.lineSequence().filter { it.contains(q, ignoreCase = true) }.joinToString("\n")
    }
}

@Composable
internal fun rememberOutputFilter(): OutputFilter = remember { OutputFilter() }

/** The search icon that opens or closes the filter field of [filter]. */
@Composable
internal fun OutputFilterButton(filter: OutputFilter) {
    IconButton(onClick = filter::toggle) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = stringResource(R.string.shell_output_filter),
            tint = if (filter.open) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

/** The filter field with how many of the output's lines match; it shows only while the filter is open. */
@Composable
internal fun OutputFilterField(filter: OutputFilter, text: String) {
    if (!filter.open) return
    // The field takes focus and the keyboard as soon as it opens, so the search icon needs only one tap.
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val q = filter.query.trim()
    val total = remember(text) { if (text.isEmpty()) 0 else text.lines().size }
    val shown = remember(text, q) { if (q.isEmpty()) total else text.lineSequence().count { it.contains(q, ignoreCase = true) } }
    OutlinedTextField(
        value = filter.query,
        onValueChange = { filter.query = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.shell_output_filter), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            IconButton(onClick = filter::toggle) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close)) }
        },
        supportingText = { if (q.isNotEmpty()) Text(stringResource(R.string.shell_output_count, shown, total)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}
