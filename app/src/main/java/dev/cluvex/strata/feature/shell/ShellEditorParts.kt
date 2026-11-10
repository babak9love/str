package dev.cluvex.strata.feature.shell

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cluvex.strata.R
import kotlinx.coroutines.launch
import dev.cluvex.strata.core.shell.ShellParams
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import dev.cluvex.strata.core.shell.ShellResult
import kotlin.math.roundToInt

/**
 * The command text with an undo and redo history. Typing in quick succession counts as one step; inserting a
 * snippet, pasting or clearing are steps of their own. Moving the cursor alone is not recorded.
 */
@Stable
internal class CommandEditorState(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial))
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private val undoStack = ArrayList<TextFieldValue>()
    private val redoStack = ArrayList<TextFieldValue>()
    private var lastEditAt = 0L

    private fun sync() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun record(group: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!group || undoStack.isEmpty() || now - lastEditAt > GROUP_MS) {
            undoStack.add(value)
            if (undoStack.size > MAX_STEPS) undoStack.removeAt(0)
        }
        lastEditAt = if (group) now else 0L
        redoStack.clear()
    }

    /** A change coming from the text field (typing, deleting, moving the cursor). */
    fun onChange(new: TextFieldValue) {
        if (new.text != value.text) record(group = true)
        value = new
        sync()
    }

    /** A change made by a button; always its own undo step. */
    fun set(new: TextFieldValue) {
        if (new.text != value.text) record(group = false)
        value = new
        sync()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(value)
        value = undoStack.removeAt(undoStack.lastIndex)
        lastEditAt = 0L
        sync()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(value)
        value = redoStack.removeAt(redoStack.lastIndex)
        lastEditAt = 0L
        sync()
    }

    private companion object {
        const val GROUP_MS = 1000L
        const val MAX_STEPS = 100
    }
}

/** A ready-made command the editor can drop into the command box. */
internal class ShellSnippet(@StringRes val label: Int, val command: String)

internal val SHELL_SNIPPETS = listOf(
    ShellSnippet(R.string.shell_snip_force_stop, "am force-stop {package}"),
    ShellSnippet(R.string.shell_snip_open_app, "monkey -p {package} -c android.intent.category.LAUNCHER 1"),
    ShellSnippet(R.string.shell_snip_clear_data, "pm clear {package}"),
    ShellSnippet(R.string.shell_snip_disable, "pm disable-user --user 0 {package}"),
    ShellSnippet(R.string.shell_snip_enable, "pm enable {package}"),
    ShellSnippet(R.string.shell_snip_screenshot, "screencap -p /sdcard/Pictures/screenshot_\$(date +%Y%m%d_%H%M%S).png"),
    ShellSnippet(R.string.shell_snip_wifi_on, "svc wifi enable"),
    ShellSnippet(R.string.shell_snip_wifi_off, "svc wifi disable"),
    ShellSnippet(R.string.shell_snip_data_on, "svc data enable"),
    ShellSnippet(R.string.shell_snip_data_off, "svc data disable"),
    ShellSnippet(R.string.shell_snip_restart_ui, "killall com.android.systemui"),
)

/** Label shown on a key and the text it inserts. */
private class SymbolKey(val label: String, val text: String = label)

/** Characters that are hard to find on a phone keyboard but common in shell commands (Tab first, like the text editor). */
private val SHELL_SYMBOLS = listOf(
    SymbolKey("\u21E5", "\t"), SymbolKey("{"), SymbolKey("}"), SymbolKey("("), SymbolKey(")"), SymbolKey(";"),
    SymbolKey("|"), SymbolKey("&"), SymbolKey("$"), SymbolKey("="), SymbolKey("\""), SymbolKey("'"),
    SymbolKey("&&"), SymbolKey("||"), SymbolKey(">"), SymbolKey(">>"), SymbolKey("<"), SymbolKey("2>&1"),
    SymbolKey("/"), SymbolKey("~"), SymbolKey("\\"), SymbolKey("*"), SymbolKey("["), SymbolKey("]"),
    SymbolKey("-"), SymbolKey("_"), SymbolKey("#"), SymbolKey("`"),
)

/** The strip of those characters above the keyboard of the full-screen editor; scrolls sideways. */
@Composable
internal fun SymbolBar(onInsert: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SHELL_SYMBOLS.forEach { key ->
                Box(
                    // Tapping a key must not take focus from the editor, or the keyboard (and this bar) would go away.
                    Modifier.focusProperties { canFocus = false }
                        .height(40.dp).widthIn(min = 40.dp)
                        .clickable { onInsert(key.text) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(key.label, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
                }
            }
        }
    }
}

/**
 * Android's regex engine (ICU) is stricter than the JVM's: a bare "}" or "{" is a syntax error, so every brace
 * is escaped. A pattern that still fails to compile is skipped, so a colouring problem can never crash the editor.
 */
private fun safeRegex(pattern: String): Regex? = runCatching { Regex(pattern) }.getOrNull()

private val COMMENT = safeRegex("(?m)(?:^|(?<=\\s))#.*$")
private val STRING = safeRegex("\"(?:\\\\.|[^\"\\\\])*\"?|'[^']*'?")
private val VARIABLE = safeRegex("\\$(?:\\{[^\\}]*\\}?|\\([^\\)]*\\)?|[A-Za-z_][A-Za-z0-9_]*|[?#@*!\\$0-9])")
private val PLACEHOLDER = safeRegex("\\{package\\}")
private val OPERATOR = safeRegex("[|\\&;<>]")
private val FLAG = safeRegex("(?m)(?:^|(?<=\\s))--?[A-Za-z][A-Za-z0-9_\\-]*")

/** Light syntax colouring for a shell command. Later rules win over earlier ones where they overlap. */
internal fun highlightShell(text: String, c: ColorScheme): AnnotatedString = buildAnnotatedString {
    append(text)
    fun paint(regex: Regex?, style: SpanStyle) {
        if (regex == null) return
        runCatching {
            regex.findAll(text).forEach { m -> if (m.range.last >= m.range.first) addStyle(style, m.range.first, m.range.last + 1) }
        }
    }
    paint(FLAG, SpanStyle(color = c.secondary))
    paint(OPERATOR, SpanStyle(color = c.primary, fontWeight = FontWeight.Bold))
    paint(VARIABLE, SpanStyle(color = c.tertiary))
    paint(PLACEHOLDER, SpanStyle(color = c.primary, fontWeight = FontWeight.Bold))
    paint(STRING, SpanStyle(color = c.tertiary))
    paint(COMMENT, SpanStyle(color = c.outline))
}

@Composable
internal fun rememberShellHighlight(): VisualTransformation {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { VisualTransformation { t -> TransformedText(highlightShell(t.text, scheme), OffsetMapping.Identity) } }
}

/** Result of the last test run: a spinner while it runs, then how it ended. */
@Composable
internal fun TestStatus(result: ShellResult?, running: Boolean) {
    if (running) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.shell_test_running), style = MaterialTheme.typography.labelLarge)
        }
    } else if (result != null) {
        val ok = result.backend != null && !result.timedOut && !result.stopped && result.exitCode == 0
        Text(
            when {
                result.backend == null -> stringResource(R.string.shell_banner_no_backend)
                result.timedOut -> stringResource(R.string.shell_log_timed_out)
                result.stopped -> stringResource(R.string.shell_log_stopped)
                result.exitCode != 0 -> stringResource(R.string.shell_log_failed) + " \u00B7 " + stringResource(R.string.shell_exit_code, result.exitCode)
                else -> stringResource(R.string.shell_log_success) + " \u00B7 " + stringResource(R.string.shell_exit_code, 0)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The output of the last test run; follows the end of the output while the command is still printing. */
@Composable
internal fun TestOutputPanel(output: String?, running: Boolean, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 220.dp) {
    if (output == null) return
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        val scroll = rememberScrollState()
        LaunchedEffect(output.length) { if (running) scroll.scrollTo(scroll.maxValue) }
        SelectionContainer {
            Text(
                output.ifEmpty { if (running) "" else stringResource(R.string.shell_no_output) },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(scroll).padding(12.dp),
            )
        }
    }
}

/**
 * The command as it sits on the edit page: a framed, read-only preview with Run on the top edge of the frame.
 * Tapping the frame opens the editor; while a test run is going, the same button stops it.
 */
@Composable
internal fun CommandBox(
    text: String,
    onOpen: () -> Unit,
    runEnabled: Boolean,
    running: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val preview = remember(text, scheme) { highlightShell(text, scheme) }
    // The frame starts 22.dp down so the Run button, centred on its top edge, stays inside this box and keeps its touch area.
    Box(modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = scheme.surface,
            border = BorderStroke(1.5.dp, if (text.isEmpty()) scheme.outline else scheme.primary),
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        ) {
            Text(
                if (text.isEmpty()) AnnotatedString(stringResource(R.string.shell_command_placeholder)) else preview,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                    textAlign = TextAlign.Left,
                    color = if (text.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                ),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 112.dp).padding(start = 14.dp, top = 28.dp, end = 72.dp, bottom = 14.dp),
            )
        }
        Surface(
            onClick = if (running) onStop else onRun,
            enabled = running || runEnabled,
            shape = CircleShape,
            color = scheme.secondaryContainer,
            border = BorderStroke(1.5.dp, if (running || runEnabled) scheme.primary else scheme.outlineVariant),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp).size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (running) R.string.common_stop else R.string.shell_test_run),
                    tint = if (running || runEnabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

/** Number of digits of the highest line number in [text] (at least 1). */
private fun lineCountDigits(text: String): Int = (text.count { it == '\n' } + 1).toString().length

/** Line and column (both from 1) of [offset] in [text]. */
private fun lineAndColumn(text: String, offset: Int): Pair<Int, Int> {
    val at = offset.coerceIn(0, text.length)
    val line = text.substring(0, at).count { it == '\n' } + 1
    val col = at - (text.lastIndexOf('\n', at - 1) + 1) + 1
    return line to col
}

/**
 * The command editor at full screen. Top row: back, title with the cursor position, undo, redo, search, test run and
 * done. Under it the command helpers (templates, app, package, paste, clear), then the text with a narrow line-number
 * column, the result of the last test run and the symbol strip above the keyboard. Search works like the text
 * editor's: regex / match case / whole word / replace, with every match highlighted. Always laid out left to right,
 * like the command itself. It edits the same text as the preview on the edit page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpandedCommandEditor(
    editor: CommandEditorState,
    highlight: VisualTransformation,
    testStatus: @Composable () -> Unit,
    testOutput: String?,
    testRunning: Boolean,
    canRun: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onInsert: (String) -> Unit,
    onSnippet: (label: String, command: String) -> Unit,
    onPickApp: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fieldFocus = remember { FocusRequester() }
    val queryFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var snippetsOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var replaceOpen by remember { mutableStateOf(false) }
    var replaceText by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    val value = editor.value
    val text = value.text

    val finder: Regex? = remember(query, useRegex, matchCase, wholeWord) {
        if (query.isEmpty()) null else runCatching {
            val body = if (useRegex) query else Regex.escape(query)
            Regex(if (wholeWord) "\\b(?:$body)\\b" else body, if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        }.getOrNull()
    }
    val matches: List<IntRange> = remember(finder, text) {
        if (finder == null) emptyList() else runCatching { finder.findAll(text).map { it.range }.filter { !it.isEmpty() }.toList() }.getOrDefault(emptyList())
    }
    val current = if (matches.isEmpty()) 0 else matchIndex.coerceIn(0, matches.size - 1)
    val findTransformation = remember(matches, current, scheme) {
        VisualTransformation { t ->
            TransformedText(
                buildAnnotatedString {
                    append(highlightShell(t.text, scheme))
                    matches.forEachIndexed { i, r ->
                        if (r.last < t.text.length) addStyle(SpanStyle(background = scheme.primary.copy(alpha = if (i == current) 0.55f else 0.25f)), r.first, r.last + 1)
                    }
                },
                OffsetMapping.Identity,
            )
        }
    }
    fun select(i: Int) {
        if (matches.isEmpty()) return
        val idx = ((i % matches.size) + matches.size) % matches.size
        matchIndex = idx
        val r = matches[idx]
        editor.onChange(editor.value.copy(selection = TextRange(r.first, r.last + 1)))
        layout?.let { l -> scope.launch { scroll.animateScrollTo(l.getLineTop(l.getLineForOffset(r.first.coerceAtMost(text.length))).roundToInt()) } }
    }
    fun replaceCurrent() {
        val f = finder ?: return
        val r = matches.getOrNull(current) ?: return
        val rep = runCatching { if (useRegex) f.replace(text.substring(r.first, r.last + 1), replaceText) else replaceText }.getOrNull() ?: return
        editor.set(TextFieldValue(text.replaceRange(r.first, r.last + 1, rep), TextRange(r.first + rep.length)))
    }
    fun replaceAll() {
        val f = finder ?: return
        if (matches.isEmpty()) return
        val out = runCatching { f.replace(text, if (useRegex) replaceText else Regex.escapeReplacement(replaceText)) }.getOrNull() ?: return
        editor.set(TextFieldValue(out, TextRange(out.length)))
    }
    val (line, col) = lineAndColumn(text, value.selection.start)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = scheme.surface) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    // ── Top row ──
                        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_close))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                Text(stringResource(R.string.shell_command), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Ln $line, Col $col", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                            }
                            val noFocus = Modifier.focusProperties { canFocus = false }.size(40.dp)
                            IconButton(onClick = { editor.undo() }, enabled = editor.canUndo, modifier = noFocus) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.shell_undo))
                            }
                            IconButton(onClick = { editor.redo() }, enabled = editor.canRedo, modifier = noFocus) {
                                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.shell_redo))
                            }
                            IconButton(onClick = { findOpen = !findOpen }, modifier = noFocus) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.shell_find), tint = if (findOpen) scheme.primary else scheme.onSurfaceVariant)
                            }
                            if (testRunning) {
                                IconButton(onClick = onStop, modifier = noFocus) { Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.common_stop), tint = scheme.primary) }
                            } else {
                                IconButton(onClick = onRun, enabled = canRun, modifier = noFocus) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.shell_test_run), tint = if (canRun) scheme.primary else scheme.onSurface.copy(alpha = 0.38f))
                                }
                            }
                            IconButton(onClick = onClose, modifier = noFocus) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.common_done)) }
                        }
                        // ── Search (like the text editor's) ──
                        if (findOpen) {
                            LaunchedEffect(Unit) { runCatching { queryFocus.requestFocus() } }
                            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it; matchIndex = 0 },
                                        singleLine = true,
                                        isError = query.isNotEmpty() && finder == null,
                                        trailingIcon = { Text(if (matches.isEmpty()) "0" else "${current + 1}/${matches.size}", style = MaterialTheme.typography.labelMedium) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                        modifier = Modifier.weight(1f).focusRequester(queryFocus),
                                    )
                                    IconButton(onClick = { select(current - 1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.shell_find_prev)) }
                                    IconButton(onClick = { select(current + 1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.shell_find_next)) }
                                    IconButton(onClick = { findOpen = false; query = ""; replaceOpen = false }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close)) }
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = useRegex, onClick = { useRegex = !useRegex }, label = { Text(stringResource(R.string.shell_find_regex)) })
                                    FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text(stringResource(R.string.shell_find_case)) })
                                    FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord }, label = { Text(stringResource(R.string.shell_find_word)) })
                                    FilterChip(selected = replaceOpen, onClick = { replaceOpen = !replaceOpen }, label = { Text(stringResource(R.string.shell_find_replace)) })
                                }
                                if (replaceOpen) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = replaceText,
                                            onValueChange = { replaceText = it },
                                            singleLine = true,
                                            placeholder = { Text(stringResource(R.string.shell_find_replace_with)) },
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = { replaceCurrent() }, enabled = matches.isNotEmpty()) { Text(stringResource(R.string.shell_find_replace)) }
                                        TextButton(onClick = { replaceAll() }, enabled = matches.isNotEmpty()) { Text(stringResource(R.string.shell_find_replace_all)) }
                                    }
                                }
                            }
                        }
                        // ── Helpers ──
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box {
                                AssistChip(
                                    onClick = { snippetsOpen = true },
                                    label = { Text(stringResource(R.string.shell_tool_snippets)) },
                                    leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                )
                                DropdownMenu(expanded = snippetsOpen, onDismissRequest = { snippetsOpen = false }) {
                                    SHELL_SNIPPETS.forEach { sn ->
                                        val label = stringResource(sn.label)
                                        DropdownMenuItem(text = { Text(label) }, onClick = { snippetsOpen = false; onSnippet(label, sn.command) })
                                    }
                                }
                            }
                            AssistChip(
                                onClick = onPickApp,
                                label = { Text(stringResource(R.string.shell_tool_app)) },
                                leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                            AssistChip(onClick = { onInsert(ShellParams.PACKAGE) }, label = { Text(ShellParams.PACKAGE) })
                            AssistChip(
                                onClick = onPaste,
                                label = { Text(stringResource(R.string.shell_tool_paste)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                            AssistChip(
                                onClick = onClear,
                                enabled = text.isNotEmpty(),
                                label = { Text(stringResource(R.string.common_clear)) },
                                leadingIcon = { Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                        // ── Text with line numbers; as tall as it needs (at least a few lines), then it scrolls ──
                        val density = LocalDensity.current
                        val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr, color = scheme.onSurface)
                        // The line-number column is only as wide as its digits need (1 digit ~ 24.dp, 2 digits ~ 33.dp).
                        val starts = remember(text) { buildList { add(0); text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } } }
                        val gutterWidth = (lineCountDigits(text) * 9 + 15).dp
                        val textHeight = with(density) { (layout?.size?.height ?: 0).toDp() }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val minBody = maxHeight
                            Row(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                                Box(Modifier.width(gutterWidth).height(maxOf(textHeight, minBody)).background(scheme.surfaceContainerLow)) {
                                    val l = layout
                                    if (l != null) {
                                        starts.forEachIndexed { i, start ->
                                            val top = l.getLineTop(l.getLineForOffset(start.coerceAtMost(text.length)))
                                            Text(
                                                "${i + 1}",
                                                style = mono.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize, color = if (i + 1 == line) scheme.primary else scheme.outline),
                                                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, top.roundToInt()) }.padding(end = 5.dp),
                                            )
                                        }
                                    }
                                }
                                BasicTextField(
                                    value = value,
                                    onValueChange = { editor.onChange(it) },
                                    textStyle = mono,
                                    cursorBrush = SolidColor(scheme.primary),
                                    visualTransformation = if (matches.isEmpty()) highlight else findTransformation,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    onTextLayout = { layout = it },
                                    modifier = Modifier.weight(1f).heightIn(min = minBody).focusRequester(fieldFocus)
                                        .drawBehind {
                                            // Light bar behind the line the cursor is on.
                                            val l = layout ?: return@drawBehind
                                            val cur = l.getLineForOffset(value.selection.start.coerceIn(0, text.length))
                                            val top = l.getLineTop(cur)
                                            drawRect(scheme.surfaceContainerHighest.copy(alpha = 0.5f), Offset(0f, top), Size(size.width, l.getLineBottom(cur) - top))
                                        }
                                        .padding(start = 6.dp, end = 8.dp),
                                )
                            }
                        }
                        // ── Result of the last test run ──
                        if (testRunning || testOutput != null) {
                            Surface(color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    testStatus()
                                    if (!testOutput.isNullOrEmpty()) TestOutputPanel(testOutput, testRunning, maxHeight = 96.dp)
                                }
                            }
                        }
                        SymbolBar(onInsert)
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    }
}
