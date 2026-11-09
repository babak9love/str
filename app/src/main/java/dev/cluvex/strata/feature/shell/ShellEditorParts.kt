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
 * The command as it sits on the edit page: a framed, read-only preview with its helpers inside the frame. Tapping
 * it opens the full-screen editor; the helpers (templates, app, paste, clear) work without opening it.
 */
@Composable
internal fun CommandBox(
    text: String,
    onOpen: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    tools: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val preview = remember(text, scheme) { highlightShell(text, scheme) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.5.dp, if (text.isEmpty()) scheme.outline else scheme.primary),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(start = 14.dp, top = 16.dp, end = 88.dp, bottom = 12.dp),
                )
                Row(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)) {
                    IconButton(onClick = onHelp, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.shell_help), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.shell_tool_expand), modifier = Modifier.size(22.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
            Row(
                Modifier.fillMaxWidth().background(scheme.surfaceContainerLow).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tools,
            )
        }
    }
}

/** Line and column (both from 1) of [offset] in [text]. */
private fun lineAndColumn(text: String, offset: Int): Pair<Int, Int> {
    val at = offset.coerceIn(0, text.length)
    val line = text.substring(0, at).count { it == '\n' } + 1
    val col = at - (text.lastIndexOf('\n', at - 1) + 1) + 1
    return line to col
}

/**
 * The command editor at full screen, laid out like the text editor: back, title with the cursor position, undo,
 * redo, test run, done and a menu in the top bar; line numbers beside the text; the symbol strip above the keyboard.
 * It edits the same text as the preview on the edit page. Always laid out left to right, like the command itself.
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
    val fieldFocus = remember { FocusRequester() }
    var menuOpen by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    val value = editor.value
    val text = value.text
    val matches = remember(query, text) {
        if (query.isEmpty()) emptyList() else buildList {
            var from = text.indexOf(query, 0, ignoreCase = true)
            while (from >= 0) { add(from); from = text.indexOf(query, from + query.length, ignoreCase = true) }
        }
    }
    fun goTo(i: Int) {
        if (matches.isEmpty()) return
        val idx = ((i % matches.size) + matches.size) % matches.size
        matchIndex = idx
        val start = matches[idx]
        editor.onChange(editor.value.copy(selection = TextRange(start, start + query.length)))
        runCatching { fieldFocus.requestFocus() }
    }
    val (line, col) = lineAndColumn(text, value.selection.start)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Scaffold(
                    modifier = Modifier.systemBarsPadding().imePadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(stringResource(R.string.shell_command), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text("Ln $line, Col $col", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_close)) }
                            },
                            actions = {
                                val noFocus = Modifier.focusProperties { canFocus = false }
                                IconButton(onClick = { editor.undo() }, enabled = editor.canUndo, modifier = noFocus) {
                                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.shell_undo))
                                }
                                IconButton(onClick = { editor.redo() }, enabled = editor.canRedo, modifier = noFocus) {
                                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.shell_redo))
                                }
                                if (testRunning) {
                                    IconButton(onClick = onStop, modifier = noFocus) { Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.common_stop), tint = MaterialTheme.colorScheme.primary) }
                                } else {
                                    IconButton(onClick = onRun, enabled = canRun, modifier = noFocus) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.shell_test_run), tint = if (canRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                    }
                                }
                                IconButton(onClick = onClose, modifier = noFocus) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.common_done)) }
                                Box {
                                    IconButton(onClick = { showSnippets = false; menuOpen = true }, modifier = noFocus) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.shell_more))
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        if (showSnippets) {
                                            SHELL_SNIPPETS.forEach { sn ->
                                                val label = stringResource(sn.label)
                                                DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; onSnippet(label, sn.command) })
                                            }
                                        } else {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.shell_find)) }, onClick = { menuOpen = false; findOpen = true })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.shell_tool_snippets)) }, onClick = { showSnippets = true })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.shell_tool_app)) }, onClick = { menuOpen = false; onPickApp() })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.shell_tool_paste)) }, onClick = { menuOpen = false; onPaste() })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.common_clear)) }, enabled = text.isNotEmpty(), onClick = { menuOpen = false; onClear() })
                                        }
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        Column {
                            if (testRunning || testOutput != null) {
                                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        testStatus()
                                        if (!testOutput.isNullOrEmpty()) TestOutputPanel(testOutput, testRunning, maxHeight = 120.dp)
                                    }
                                }
                            }
                            SymbolBar(onInsert)
                        }
                    },
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        if (findOpen) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it; matchIndex = 0 },
                                    placeholder = { Text(stringResource(R.string.shell_find)) },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(if (matches.isEmpty()) "0" else "${matchIndex + 1}/${matches.size}", style = MaterialTheme.typography.labelMedium)
                                IconButton(onClick = { goTo(matchIndex - 1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.shell_find_prev)) }
                                IconButton(onClick = { goTo(matchIndex + 1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.shell_find_next)) }
                                IconButton(onClick = { findOpen = false; query = "" }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close)) }
                            }
                        }
                        // Line numbers sit beside the text and follow its wrapping: each number is placed at the top of
                        // the first visual line of its own line.
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val minHeight = maxHeight
                            val density = LocalDensity.current
                            val scheme = MaterialTheme.colorScheme
                            val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr, color = scheme.onSurface)
                            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                            val scroll = rememberScrollState()
                            val starts = remember(text) { buildList { add(0); text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } } }
                            val textHeight = with(density) { (layout?.size?.height ?: 0).toDp() }
                            Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
                                Box(Modifier.width(44.dp).height(maxOf(textHeight, minHeight)).background(scheme.surfaceContainerLow)) {
                                    val l = layout
                                    if (l != null) {
                                        starts.forEachIndexed { i, start ->
                                            val top = l.getLineTop(l.getLineForOffset(start.coerceAtMost(text.length)))
                                            Text(
                                                "${i + 1}",
                                                style = mono.copy(color = if (i + 1 == line) scheme.primary else scheme.outline),
                                                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, top.roundToInt()) }.padding(end = 6.dp),
                                            )
                                        }
                                    }
                                }
                                BasicTextField(
                                    value = value,
                                    onValueChange = { editor.onChange(it) },
                                    textStyle = mono,
                                    cursorBrush = SolidColor(scheme.primary),
                                    visualTransformation = highlight,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    onTextLayout = { layout = it },
                                    modifier = Modifier.weight(1f).heightIn(min = minHeight).focusRequester(fieldFocus)
                                        .drawBehind {
                                            // Light bar behind the line the cursor is on.
                                            val l = layout ?: return@drawBehind
                                            val cur = l.getLineForOffset(value.selection.start.coerceIn(0, text.length))
                                            val top = l.getLineTop(cur)
                                            drawRect(scheme.surfaceContainerHighest.copy(alpha = 0.5f), Offset(0f, top), Size(size.width, l.getLineBottom(cur) - top))
                                        }
                                        .padding(start = 8.dp, end = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    }
}
