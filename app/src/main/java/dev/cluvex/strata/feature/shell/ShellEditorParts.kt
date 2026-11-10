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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.lang.EmptyLanguage
import dev.cluvex.strata.feature.editor.isDarkBackground
import dev.cluvex.strata.feature.editor.autoThemeId
import dev.cluvex.strata.feature.editor.TextMateEngine
import dev.cluvex.strata.feature.editor.StrataColorScheme
import dev.cluvex.strata.feature.editor.SearchQuery
import dev.cluvex.strata.feature.editor.EditorController
import dev.cluvex.strata.feature.editor.EditorChrome
import dev.cluvex.strata.feature.editor.CodeEditorView
import androidx.compose.ui.platform.LocalContext
import android.view.inputmethod.InputMethodManager
import android.content.Context
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

    /** Takes over text that the embedded editor changed; its own history does the undo, so nothing is recorded here. */
    fun syncText(text: String) {
        if (text == value.text) return
        value = TextFieldValue(text, TextRange(text.length))
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
    header: @Composable RowScope.() -> Unit,
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
        // The title sits on the top border of the frame, like the label of the Name field: it is centred on the border line
        // and has the page colour behind it, which cuts a gap into the line.
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 11.dp).height(22.dp).background(scheme.surface).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = header,
        )
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

/** Replaces the whole text of the embedded editor in one undoable step. */
internal fun EditorController.replaceWhole(text: String) {
    val e = editor ?: return
    if (!e.isEditable) return
    val content = e.text
    val last = content.lineCount - 1
    content.replace(0, 0, last, content.getColumnCount(last), text)
}

/**
 * The command editor at full screen. It embeds the same editor as Strata's text editor, so selecting text brings up
 * the same icon menu (select all, copy, paste, cut), with undo, line numbers and shell colouring from the app's
 * grammar. Top row: back, title with the cursor position, undo, redo, search, test run and done. Under it the command
 * helpers (templates, app, package, paste, clear), then the editor, the result of the last test run and the symbol
 * strip above the keyboard. Search works like the text editor's: regex / match case / whole word / replace, with the
 * two fields side by side while replacing. Always laid out left to right, like the command itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpandedCommandEditor(
    controller: EditorController,
    initialText: String,
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
    val context = LocalContext.current
    val queryFocus = remember { FocusRequester() }
    var snippetsOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(SearchQuery("")) }
    var replacement by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(false) }
    var findError by remember { mutableStateOf<String?>(null) }

    // Once the editor view exists: load the engine, colours, the text and the shell grammar, then focus it.
    val dark = isDarkBackground(scheme.surface)
    LaunchedEffect(controller.editor) {
        val view = controller.editor ?: return@LaunchedEffect
        withContext(Dispatchers.Default) { runCatching { TextMateEngine.init(context) } }
        withContext(Dispatchers.Main.immediate) {
            runCatching { TextMateEngine.activateTheme(autoThemeId(dark)) }.getOrNull()?.let { model ->
                controller.setColorScheme(StrataColorScheme(ThemeRegistry.getInstance(), model, EditorChrome.from(scheme)))
            }
            controller.setText(initialText)
            controller.setWordWrap(true)
            controller.setTextSize(14f)
        }
        val language = withContext(Dispatchers.Default) { runCatching { TextMateEngine.language("source.shell") }.getOrNull() } ?: EmptyLanguage()
        withContext(Dispatchers.Main.immediate) {
            controller.setLanguage(language)
            view.requestFocus()
            runCatching { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, 0) }
        }
    }
    LaunchedEffect(scheme, controller.editor) {
        if (controller.editor == null) return@LaunchedEffect
        val model = runCatching { TextMateEngine.activateTheme(autoThemeId(dark)) }.getOrNull() ?: return@LaunchedEffect
        controller.setColorScheme(StrataColorScheme(ThemeRegistry.getInstance(), model, EditorChrome.from(scheme)))
    }
    LaunchedEffect(findOpen, query, controller.editor) { findError = if (findOpen) controller.search(query) else null }
    fun closeFind() {
        findOpen = false
        replacing = false
        controller.stopSearch()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = scheme.surface) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    // ── Top row ──
                    // 6.dp between every button (each is 40.dp wide with a 24.dp icon), so the icons no longer touch.
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_close))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.shell_command), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Ln ${controller.line}, Col ${controller.column}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        val noFocus = Modifier.focusProperties { canFocus = false }.size(40.dp)
                        IconButton(onClick = { controller.undo() }, enabled = controller.canUndo, modifier = noFocus) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.shell_undo))
                        }
                        IconButton(onClick = { controller.redo() }, enabled = controller.canRedo, modifier = noFocus) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.shell_redo))
                        }
                        IconButton(onClick = { if (findOpen) closeFind() else findOpen = true }, modifier = noFocus) {
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
                        // Focus goes back to the search field when the search opens and whenever Replace is switched on or off
                        // (the field is rebuilt in a new place then, which drops its focus).
                        LaunchedEffect(replacing) { runCatching { queryFocus.requestFocus() } }
                        val findField: @Composable (Modifier) -> Unit = { m ->
                            OutlinedTextField(
                                value = query.text,
                                onValueChange = { query = query.copy(text = it) },
                                singleLine = true,
                                isError = findError != null,
                                placeholder = { Text(stringResource(R.string.shell_find)) },
                                trailingIcon = {
                                    if (query.text.isNotEmpty()) {
                                        val index = controller.matchIndex
                                        Text(if (index >= 0) "${index + 1}/${controller.matchCount}" else "${controller.matchCount}", style = MaterialTheme.typography.labelMedium)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                modifier = m.focusRequester(queryFocus),
                            )
                        }
                        val navButtons: @Composable () -> Unit = {
                            IconButton(onClick = { controller.previous() }) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.shell_find_prev)) }
                            IconButton(onClick = { controller.next() }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.shell_find_next)) }
                            IconButton(onClick = { closeFind() }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close)) }
                        }
                        val optionChips: @Composable () -> Unit = {
                            FilterChip(selected = query.regex, onClick = { query = query.copy(regex = !query.regex) }, label = { Text(stringResource(R.string.shell_find_regex)) })
                            FilterChip(selected = query.matchCase, onClick = { query = query.copy(matchCase = !query.matchCase) }, label = { Text(stringResource(R.string.shell_find_case)) })
                            FilterChip(selected = query.wholeWord, onClick = { query = query.copy(wholeWord = !query.wholeWord) }, label = { Text(stringResource(R.string.shell_find_word)) })
                            FilterChip(selected = replacing, onClick = { replacing = !replacing }, label = { Text(stringResource(R.string.shell_find_replace)) })
                        }
                        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!replacing) {
                                // Search only: the field with the match navigation beside it, the options below.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    findField(Modifier.weight(1f))
                                    navButtons()
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { optionChips() }
                            } else {
                                // Replace: the two fields side by side, the options (and the replace actions) below.
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    findField(Modifier.weight(1f))
                                    OutlinedTextField(
                                        value = replacement,
                                        onValueChange = { replacement = it },
                                        singleLine = true,
                                        placeholder = { Text(stringResource(R.string.shell_find_replace_with)) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        optionChips()
                                        TextButton(onClick = { controller.replace(replacement) }, enabled = controller.matchCount > 0) { Text(stringResource(R.string.shell_find_replace)) }
                                        TextButton(onClick = { controller.replaceAll(replacement) }, enabled = controller.matchCount > 0) { Text(stringResource(R.string.shell_find_replace_all)) }
                                    }
                                    navButtons()
                                }
                            }
                            findError?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
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
                            label = { Text(stringResource(R.string.common_clear)) },
                            leadingIcon = { Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                    // ── The editor ──
                    CodeEditorView(controller, Modifier.weight(1f).fillMaxWidth())
                    // ── Result of the last test run ──
                    if (testRunning || testOutput != null) {
                        Surface(color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                testStatus()
                                if (!testOutput.isNullOrEmpty()) TestOutputPanel(testOutput, testRunning, maxHeight = 96.dp)
                            }
                        }
                    }
                    SymbolBar(onInsert = { controller.insertSymbol(it) })
                }
            }
        }
    }
}
