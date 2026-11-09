package dev.cluvex.strata.feature.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cluvex.strata.R

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

/** Characters that are hard to find on a phone keyboard but common in shell commands. */
private val SHELL_SYMBOLS = listOf(
    "|", "&&", "||", "&", ";", ">", ">>", "<", "2>&1", "$", "\"", "'", "/", "~", "\\", "*",
    "{", "}", "(", ")", "[", "]", "-", "_", "=", "#", "`",
)

/** Undo and redo; a button that has nothing to do is dimmed. Tapping keeps the focus in the command box. */
@Composable
internal fun UndoRedoButtons(editor: CommandEditorState) {
    val noFocus = Modifier.focusProperties { canFocus = false }
    IconButton(onClick = { editor.undo() }, enabled = editor.canUndo, modifier = noFocus) {
        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.shell_undo))
    }
    IconButton(onClick = { editor.redo() }, enabled = editor.canRedo, modifier = noFocus) {
        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.shell_redo))
    }
}

/**
 * Undo and redo, then a strip of those characters, shown above the keyboard while the command box has focus.
 * Always laid out left to right, like the command itself.
 */
@Composable
internal fun SymbolBar(editor: CommandEditorState, onInsert: (String) -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                UndoRedoButtons(editor)
                Spacer(Modifier.width(4.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SHELL_SYMBOLS.forEach { sym ->
                        Surface(
                            onClick = { onInsert(sym) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            // Tapping a key must not take focus from the command box, or the keyboard (and this bar) would go away.
                            modifier = Modifier.focusProperties { canFocus = false }.height(40.dp).widthIn(min = 40.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(sym, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
                            }
                        }
                    }
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

/** The command box at full screen, for long or multi-line commands. It edits the same text as the small box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpandedCommandEditor(
    editor: CommandEditorState,
    onInsert: (String) -> Unit,
    highlight: VisualTransformation,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
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
                        title = { Text(stringResource(R.string.shell_command)) },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Rounded.FullscreenExit, contentDescription = stringResource(R.string.shell_collapse))
                            }
                        },
                        actions = { TextButton(onClick = onClose) { Text(stringResource(R.string.common_done)) } },
                    )
                },
                bottomBar = { SymbolBar(editor, onInsert) },
            ) { padding ->
                OutlinedTextField(
                    value = editor.value,
                    onValueChange = { editor.onChange(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                    visualTransformation = highlight,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp).focusRequester(focus),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
