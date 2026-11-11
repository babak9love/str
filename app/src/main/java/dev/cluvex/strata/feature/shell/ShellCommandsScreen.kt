package dev.cluvex.strata.feature.shell

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FloatingActionButton
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.CreateNewFolder
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Schedule
import dev.cluvex.strata.feature.settings.switch
import dev.cluvex.strata.feature.settings.link
import dev.cluvex.strata.feature.settings.custom
import dev.cluvex.strata.feature.settings.choice
import dev.cluvex.strata.feature.settings.Tint
import dev.cluvex.strata.feature.settings.ConnectedChoice
import dev.cluvex.strata.feature.settings.ScrollableConnectedChoice
import dev.cluvex.strata.feature.settings.SettingsGroup
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import dev.cluvex.strata.core.shell.ShellCategoryInfo
import dev.cluvex.strata.feature.color.ColorPickerDialog
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Code
import kotlinx.coroutines.CoroutineScope
import dev.cluvex.strata.core.shell.ShellResult
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cluvex.strata.R
import dev.cluvex.strata.core.model.MotionStyle
import dev.cluvex.strata.core.shell.SavedCommand
import androidx.compose.ui.platform.LocalContext
import dev.cluvex.strata.core.shell.ShellExecutor
import dev.cluvex.strata.core.shell.ShellLogStore
import dev.cluvex.strata.core.shell.ShellRunTracker
import dev.cluvex.strata.core.shell.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.cluvex.strata.core.shell.ShellBackend
import dev.cluvex.strata.core.shell.ShellMode
import dev.cluvex.strata.core.shell.ShellParams
import dev.cluvex.strata.core.shell.ShellTemplateStore
import dev.cluvex.strata.core.shell.ShellTemplate
import dev.cluvex.strata.feature.editor.EditorController
import dev.cluvex.strata.core.shell.ShellRunner
import dev.cluvex.strata.ui.expressive.ExpressiveDialog
import dev.cluvex.strata.ui.expressive.ExpressiveEmptyState
import dev.cluvex.strata.ui.expressive.MorphShape
import dev.cluvex.strata.ui.expressive.rememberPolygonMorph
import dev.cluvex.strata.ui.rememberTextClipboard
import dev.cluvex.strata.ui.theme.LocalAppearance

private val TIMEOUT_CHOICES = listOf(10, 30, 60, 300, 600)

private val WAIT_PRESETS = listOf(5, 10, 15, 20)

@Composable
private fun timeoutLabel(sec: Int): String =
    if (sec <= ShellRunner.NO_TIMEOUT) stringResource(R.string.shell_timeout_none)
    else if (sec < 60) stringResource(R.string.shell_timeout_seconds, sec) else stringResource(R.string.shell_timeout_minutes, sec / 60)

/** [command] null means "new command". */
private class EditTarget(val command: SavedCommand?)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandsScreen(onBack: () -> Unit, vm: ShellCommandsViewModel = viewModel()) {
    val ui by vm.state.collectAsState()
    // Commands running right now, whoever started them: this screen, a launcher shortcut or a Quick Settings tile.
    // (A command started here is also in the view model's own list; the others are only in this one.)
    val tracked by ShellRunTracker.runs.collectAsState()
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var showLog by remember { mutableStateOf(false) }
    // Opened from a tile's long press while a command runs: go straight to the log, where the live output is.
    val logRequested by ShellOpenRequest.showLog.collectAsState()
    LaunchedEffect(logRequested) {
        if (logRequested) {
            showLog = true
            ShellOpenRequest.showLog.value = false
        }
    }
    var confirming by remember { mutableStateOf<SavedCommand?>(null) }
    // A command with {package} waits here until an app is picked for it.
    var pickFor by remember { mutableStateOf<SavedCommand?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    // The + button opens a small menu: a new command or a new category.
    var fabOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var newCategoryDialog by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf<String?>(null) }
    var liveId by remember { mutableStateOf<String?>(null) }
    // Swiping a row to the right asks before deleting it; swiping to the left opens its editor.
    var deleteCmd by remember { mutableStateOf<SavedCommand?>(null) }
    var deleteCategory by remember { mutableStateOf<String?>(null) }
    // A background command that is still running, whose log was opened again from its card.
    var backgroundCmd by remember { mutableStateOf<SavedCommand?>(null) }
    // A normal command opens its live output right away; a background one shows its log when it has started.
    val startCommand: (SavedCommand) -> Unit = { c ->
        vm.run(c)
        if (!c.detached) { vm.followRun(c.id); liveId = c.id }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) vm.exportTo(uri) { n ->
            val text = if (n < 0) context.getString(R.string.shell_import_failed) else context.getString(R.string.shell_exported, n)
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.importFrom(uri) { n ->
            val text = if (n < 0) context.getString(R.string.shell_import_failed) else context.getString(R.string.shell_imported, n)
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { vm.reload() }
    // Commands can also be started from a shortcut or the tile while the app is away: refresh when it comes back.
    LifecycleResumeEffect(Unit) { vm.refreshLastRun(); onPauseOrDispose { } }
    val listState = rememberLazyListState()
    // Display rows: each category (header + its commands unless folded), then the commands that are in no category.
    val allRows = remember(ui.commands, ui.categories, ui.collapsed) {
        buildList<ShellRow> {
            for (g in ui.categories) {
                val items = ui.commands.filter { it.category == g }
                val folded = g in ui.collapsed
                add(ShellRow.Category(g, items.size, folded))
                if (!folded) items.forEach { add(ShellRow.Cmd(it, nested = true)) }
            }
            val loose = ui.commands.filter { it.category.isEmpty() }
            if (ui.categories.isNotEmpty()) add(ShellRow.Label)
            loose.forEach { add(ShellRow.Cmd(it, nested = false)) }
        }
    }
    // While something is typed in the search, the list is flat: every command whose name, text or category matches.
    val q = query.trim()
    val rows: List<ShellRow> = remember(allRows, ui.commands, q) {
        if (q.isEmpty()) {
            allRows
        } else {
            ui.commands
                .filter { c -> c.name.contains(q, ignoreCase = true) || c.command.contains(q, ignoreCase = true) || c.category.contains(q, ignoreCase = true) }
                .map { ShellRow.Cmd(it, nested = false) }
        }
    }
    // The drag handlers live inside pointerInput blocks that are keyed only by the command id, so they keep the
    // lambdas (and the `rows` list) from the composition in which they were created. Without this, after a category is
    // folded/unfolded or a command moves, list indexes map to the wrong rows and the wrong (or no) category is targeted.
    val rowsNow by rememberUpdatedState(rows)
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The category the dragged card is currently over ("" = uncategorized); the card only joins it when it is dropped.
    var hoverCategory by remember { mutableStateOf<String?>(null) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }
    if (showLog) {
        ShellLogScreen(onBack = { showLog = false })
        return
    }
    BackHandler(onBack = {
        if (fabOpen) fabOpen = false else if (searching) { searching = false; query = "" } else onBack()
    })
    val density = LocalDensity.current

    fun startDrag(id: String) {
        // Reordering only makes sense in the full list, not in a search result.
        if (q.isNotEmpty()) return
        dragId = id
        dragOffset = 0f
        pendingIndex = null
        hoverCategory = null
    }

    // What the dragged card is over. A category header (or the "Uncategorized" label) is small next to a card, so overlapping
    // it clearly is enough to target it; otherwise the row under the card's center is the target.
    fun findTarget(cur: LazyListItemInfo, dragged: SavedCommand): Pair<LazyListItemInfo, ShellRow>? {
        val items = listState.layoutInfo.visibleItemsInfo
        val top = cur.offset + dragOffset
        val bottom = top + cur.size
        val byOverlap = items.mapNotNull { info ->
            if (info.key == dragged.id) return@mapNotNull null
            val row = rowsNow.getOrNull(info.index) ?: return@mapNotNull null
            val targetable = (row is ShellRow.Category && row.name != dragged.category) || (row is ShellRow.Label && dragged.category.isNotEmpty())
            if (!targetable) return@mapNotNull null
            val overlap = minOf(bottom, (info.offset + info.size).toFloat()) - maxOf(top, info.offset.toFloat())
            if (overlap >= info.size * 0.3f) Triple(info, row, overlap) else null
        }.maxByOrNull { it.third }
        if (byOverlap != null) return byOverlap.first to byOverlap.second
        val center = top + cur.size / 2f
        val hit = items.firstOrNull { it.key != dragged.id && center >= it.offset && center <= it.offset + it.size } ?: return null
        return rowsNow.getOrNull(hit.index)?.let { hit to it }
    }

    // Called when the finger is released: if the card sits over another category, it moves there now.
    fun dropInto(id: String) {
        val cur = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val dragged = ui.commands.firstOrNull { it.id == id } ?: return
        val (hit, row) = findTarget(cur, dragged) ?: return
        when (row) {
            is ShellRow.Category -> if (row.name != dragged.category) {
                vm.moveToCategory(id, row.name, atEnd = false)
                // A folded category opens so the dropped command stays visible.
                if (row.collapsed) vm.expandCategory(row.name)
            }
            is ShellRow.Label -> if (dragged.category.isNotEmpty()) vm.moveToCategory(id, "", atEnd = false)
            is ShellRow.Cmd -> if (row.command.category != dragged.category) {
                val center = cur.offset + dragOffset + cur.size / 2f
                vm.moveNextTo(id, row.command.id, after = center > hit.offset + hit.size / 2f)
            }
        }
    }

    fun endDrag() {
        val dropped = dragId
        if (dropped != null) {
            dropInto(dropped)
            hoverCategory = null
            dragId = null
            dragOffset = 0f
            pendingIndex = null
            vm.commitOrder()
        }
    }

    // The dragged card follows the finger through dragOffset; when its center passes the middle of a neighbour the
    // two swap, and dragOffset is corrected so the card stays under the finger.
    fun dragBy(dy: Float) {
        val id = dragId ?: return
        dragOffset += dy
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        val cur = visible.firstOrNull { it.key == id } ?: return
        val waiting = pendingIndex
        if (waiting != null) {
            if (cur.index != waiting) return
            pendingIndex = null
        }
        val center = cur.offset + dragOffset + cur.size / 2f
        val dragged = ui.commands.firstOrNull { it.id == id } ?: return
        // Live reordering only happens inside the card's own category; joining another category is decided on release.
        fun sameCategoryCmd(index: Int) = (rowsNow.getOrNull(index) as? ShellRow.Cmd)?.command?.category == dragged.category
        hoverCategory = when (val r = findTarget(cur, dragged)?.second) {
            is ShellRow.Category -> r.name
            is ShellRow.Cmd -> r.command.category
            is ShellRow.Label -> ""
            null -> null
        }?.takeIf { it != dragged.category }
        val next = visible.firstOrNull { it.index == cur.index + 1 && sameCategoryCmd(it.index) }
        val prev = visible.firstOrNull { it.index == cur.index - 1 && sameCategoryCmd(it.index) }
        val target = when {
            next != null && center > next.offset + next.size / 2f -> next
            prev != null && center < prev.offset + prev.size / 2f -> prev
            else -> null
        } ?: return
        val down = target.index > cur.index
        vm.swapCommands(id, (rowsNow[target.index] as ShellRow.Cmd).command.id)
        val newOffset = if (down) target.offset + target.size - cur.size else target.offset
        dragOffset += cur.offset - newOffset
        pendingIndex = target.index
    }

    // Auto-scroll while a card is held near the top or bottom edge. It runs every frame, so the list keeps moving
    // even when the finger is still; after each step dragBy(0f) refreshes the highlighted category and live swaps.
    LaunchedEffect(dragId) {
        val id = dragId ?: return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        val speed = with(density) { 6.dp.toPx() }
        while (true) {
            withFrameNanos { }
            if (dragId != id) break
            val info = listState.layoutInfo
            val cur = info.visibleItemsInfo.firstOrNull { it.key == id } ?: continue
            val top = cur.offset + dragOffset
            val bottom = top + cur.size
            val delta = when {
                top < info.viewportStartOffset + edge -> -speed
                bottom > info.viewportEndOffset - edge -> speed
                else -> 0f
            }
            if (delta != 0f) {
                val consumed = listState.dispatchRawDelta(delta)
                if (consumed != 0f) {
                    dragOffset += consumed
                    dragBy(0f)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag("shell-commands-screen"),
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        val focus = remember { FocusRequester() }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.shell_search_commands), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    } else {
                        Text(stringResource(R.string.shell_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { if (searching) { searching = false; query = "" } else searching = true }) {
                        Icon(
                            if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.shell_search_commands),
                        )
                    }
                    // While searching, the history button steps aside so the search box is wide.
                    if (!searching) {
                        IconButton(onClick = { showLog = true }) {
                            Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.shell_log_title))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shell_export)) },
                                enabled = ui.commands.isNotEmpty(),
                                onClick = { menuOpen = false; exportLauncher.launch("strata-shell-commands.json") },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shell_import)) },
                                onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // The same color a command card has at rest, so the menu items look like part of the list.
            val cardColor = lerp(MaterialTheme.colorScheme.surfaceBright, MaterialTheme.colorScheme.primaryContainer, 0.3f)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(
                    visible = fabOpen,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.shell_category_new)) },
                            icon = { FabMenuIcon(Icons.Rounded.CreateNewFolder) },
                            onClick = { fabOpen = false; newCategoryDialog = true },
                            containerColor = cardColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.shell_add)) },
                            icon = { FabMenuIcon(Icons.Rounded.Terminal) },
                            onClick = { fabOpen = false; editing = EditTarget(null) },
                            containerColor = cardColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                FloatingActionButton(onClick = { fabOpen = !fabOpen }) {
                    Icon(if (fabOpen) Icons.Rounded.Close else Icons.Rounded.Add, contentDescription = stringResource(R.string.shell_add))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // While the + menu is open, the list dims and a tap anywhere on it closes the menu.
            if (fabOpen) {
                Box(
                    Modifier.fillMaxSize().zIndex(1f)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fabOpen = false },
                )
            }
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                ui.commands.isEmpty() && ui.categories.isEmpty() -> ExpressiveEmptyState(
                    title = stringResource(R.string.shell_empty),
                    body = stringResource(R.string.shell_empty_body),
                    icon = Icons.Rounded.Terminal,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                )
                q.isNotEmpty() && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.shell_search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ShellRow.Category -> SwipeActionRow(
                                enabled = dragId == null,
                                shape = RoundedCornerShape(16.dp),
                                onSwipeRight = { deleteCategory = row.name },
                                onSwipeLeft = { categoryMenu = row.name },
                                modifier = Modifier.animateItem(),
                            ) {
                                val info = ui.categoryInfo[row.name]
                                CategoryHeader(
                                    name = row.name,
                                    description = info?.description.orEmpty(),
                                    color = Color(info?.color?.takeIf { it != 0 } ?: ShellCategoryInfo.fallbackColor(row.name)),
                                    count = row.count,
                                    collapsed = row.collapsed,
                                    highlight = hoverCategory == row.name,
                                    onToggle = { vm.toggleCollapsed(row.name) },
                                    onMenu = { categoryMenu = row.name },
                                )
                            }
                            is ShellRow.Label -> Text(
                                stringResource(R.string.shell_category_uncategorized),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hoverCategory == "") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, top = 8.dp).animateItem(),
                            )
                            is ShellRow.Cmd -> {
                                val c = row.command
                                val lifted = dragId == c.id
                                SwipeActionRow(
                                    enabled = dragId == null,
                                    shape = RoundedCornerShape(14.dp),
                                    onSwipeRight = { deleteCmd = c },
                                    onSwipeLeft = { editing = EditTarget(c) },
                                    // The lifted card has to draw above the rows after it, so the row carries the z-index.
                                    modifier = (if (lifted) Modifier else Modifier.animateItem()).zIndex(if (lifted) 1f else 0f),
                                ) {
                                    CommandCard(
                                        command = c,
                                        lastRun = ui.lastRun[c.id] ?: 0L,
                                        running = c.id in ui.runningIds || c.id in ui.backgroundIds || c.id in tracked,
                                        blocked = false,
                                        lifted = lifted,
                                        dragOffset = { dragOffset },
                                        // While a command runs, its button opens its output (or log) instead of starting it again.
                                        onRun = {
                                            when {
                                                c.id in ui.runningIds || c.id in tracked -> if (!c.detached) { vm.followRun(c.id); liveId = c.id }
                                                c.id in ui.backgroundIds -> backgroundCmd = c
                                                ShellParams.needsPackage(c.command) -> pickFor = c
                                                c.confirm -> confirming = c
                                                else -> startCommand(c)
                                            }
                                        },
                                        onEdit = { editing = EditTarget(c) },
                                        handle = Modifier.pointerInput(c.id) {
                                            detectDragGestures(
                                                onDragStart = { startDrag(c.id) },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragBy(amount.y)
                                                },
                                                onDragEnd = { endDrag() },
                                                onDragCancel = { endDrag() },
                                            )
                                        },
                                        modifier = Modifier
                                            .pointerInput(c.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { startDrag(c.id) },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragBy(amount.y)
                                                    },
                                                    onDragEnd = { endDrag() },
                                                    onDragCancel = { endDrag() },
                                                )
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        EditCommandDialog(
            existing = target.command,
            categories = ui.categories,
            // Tile slots that other commands already hold, with the name of the command that holds each.
            takenTiles = ui.commands.filter { it.tile != 0 && it.id != target.command?.id }.associate { it.tile to it.name },
            otherShortcuts = ui.commands.count { it.shortcut && it.id != target.command?.id },
            usedCategoryColors = ui.categories.map { ui.categoryInfo[it]?.color?.takeIf { c -> c != 0 } ?: ShellCategoryInfo.fallbackColor(it) },
            onNewCategory = { n, d, c -> vm.addCategory(n, d, c) },
            onSave = { name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, category, tile, statusWaitSec ->
                vm.save(target.command?.id, name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, category, tile, statusWaitSec)
                editing = null
            },
            onDelete = target.command?.let { c -> { vm.delete(c.id); editing = null } },
            onDuplicate = target.command?.let { c ->
                { vm.duplicate(c.id) { name -> context.getString(R.string.shell_copy_name, name) }; editing = null }
            },
            onPin = target.command?.let { c ->
                {
                    val ok = dev.cluvex.strata.core.shell.ShellShortcuts.pin(context, c)
                    if (!ok) android.widget.Toast.makeText(context, R.string.shell_pin_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { editing = null },
        )
    }

    deleteCmd?.let { c ->
        ExpressiveDialog(
            onDismissRequest = { deleteCmd = null },
            title = stringResource(R.string.shell_delete_confirm_title, c.name),
            icon = Icons.Rounded.Delete,
            confirmButton = {
                TextButton(onClick = { deleteCmd = null; vm.delete(c.id) }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteCmd = null }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(
                c.command,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
            )
        }
    }

    deleteCategory?.let { g ->
        ExpressiveDialog(
            onDismissRequest = { deleteCategory = null },
            title = stringResource(R.string.shell_delete_confirm_title, g),
            icon = Icons.Rounded.Delete,
            confirmButton = {
                TextButton(onClick = { deleteCategory = null; vm.deleteCategory(g) }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteCategory = null }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(stringResource(R.string.shell_category_delete_confirm_text))
        }
    }

    pickFor?.let { c ->
        ShellAppPickerDialog(
            title = stringResource(R.string.shell_param_pick_title, c.name),
            lastPackage = remember(c.id) { ShellParams.lastPackage(context, c.id) },
            onPick = { pkgs ->
                pickFor = null
                ShellParams.rememberPackage(context, c.id, pkgs.last())
                // The command that is confirmed, run and logged is the one with the app names written in.
                val resolved = ShellParams.resolveAll(c, pkgs)
                if (c.confirm) confirming = resolved else startCommand(resolved)
            },
            onDismiss = { pickFor = null },
        )
    }

    confirming?.let { c ->
        ExpressiveDialog(
            onDismissRequest = { confirming = null },
            title = stringResource(R.string.shell_confirm_title, c.name),
            icon = Icons.Rounded.Terminal,
            confirmButton = { TextButton(onClick = { confirming = null; startCommand(c) }) { Text(stringResource(R.string.shell_run)) } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(
                c.command,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
            )
        }
    }

    if (newCategoryDialog) {
        // The colour starts as a random one that no other category has; the user can pick another.
        val used = ui.categories.map { ui.categoryInfo[it]?.color?.takeIf { c -> c != 0 } ?: ShellCategoryInfo.fallbackColor(it) }
        CategoryDialog(
            title = stringResource(R.string.shell_category_new),
            initialName = "",
            initialDescription = "",
            initialColor = remember { ShellCategoryInfo.randomColor(used) },
            confirmText = stringResource(R.string.common_create),
            onDismiss = { newCategoryDialog = false },
            onConfirm = { n, d, c -> vm.addCategory(n, d, c); newCategoryDialog = false },
        )
    }
    categoryMenu?.let { g ->
        val position = ui.categories.indexOf(g)
        val info = ui.categoryInfo[g]
        CategoryDialog(
            title = stringResource(R.string.shell_category_edit),
            initialName = g,
            initialDescription = info?.description.orEmpty(),
            initialColor = info?.color?.takeIf { it != 0 } ?: ShellCategoryInfo.fallbackColor(g),
            confirmText = stringResource(R.string.common_save),
            onDismiss = { categoryMenu = null },
            onConfirm = { n, d, c -> vm.updateCategory(g, n, d, c); categoryMenu = null },
            onDelete = { vm.deleteCategory(g); categoryMenu = null },
            onMove = { by -> vm.moveCategory(g, by) },
            canMoveUp = position > 0,
            canMoveDown = position >= 0 && position < ui.categories.lastIndex,
        )
    }

    // The live view stays open when its command ends (finished, timed out or stopped by hand) and turns into the
    // result in the same dialog, so Stop does not close it and open a second one. Closing it clears that result.
    val followed = liveId
    val followedResult = followed?.let { id -> ui.runs.lastOrNull { it.command.id == id && !it.command.detached } }
    val liveActive = followed?.let { it in ui.runningIds || it in tracked } == true
    if (followed != null && (liveActive || followedResult != null)) {
        LiveOutputDialog(
            commandId = followed,
            finished = followedResult.takeIf { !liveActive },
            onDismiss = {
                liveId = null
                // Closed while still running: it goes on, but its end does not pop a dialog up later.
                if (followed in ui.runningIds) vm.silenceRun(followed)
                followedResult?.let { vm.dismissRun(it) }
            },
        )
    }

    backgroundCmd?.let { c ->
        RunResultDialog(vm.backgroundRun(c), onDismiss = { backgroundCmd = null })
    }

    // Results queue up when several commands finish; each is shown in turn.
    // (a result that the live dialog above is showing is left out here)
    val queued = ui.runs.firstOrNull { it !== followedResult || liveActive }
    queued?.let { run -> key(run) { RunResultDialog(run, onDismiss = { vm.dismissRun(run) }) } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommandCard(
    command: SavedCommand,
    /** Time in ms of the latest run; 0 = never run (or the history was cleared). */
    lastRun: Long,
    running: Boolean,
    blocked: Boolean,
    lifted: Boolean,
    dragOffset: () -> Float,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val reduced = LocalAppearance.current.motion == MotionStyle.REDUCED
    val lift by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "lift",
    )
    val morph = rememberPolygonMorph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie9Sided)
    val morphProgress by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.6f, stiffness = 260f),
        label = "morph",
    )
    val spin = if (lifted && !reduced) {
        val transition = rememberInfiniteTransition(label = "spin")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
            label = "spin",
        ).value
    } else {
        0f
    }
    Surface(
        shape = RoundedCornerShape((14 + 18 * lift).dp),
        color = lerp(lerp(colors.surfaceBright, colors.primaryContainer, 0.3f), colors.primaryContainer, lift * 0.85f),
        contentColor = colors.onSurface,
        shadowElevation = (10 * lift).dp,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationY = if (lifted) dragOffset() else 0f
                val scale = 1f + 0.035f * lift
                scaleX = scale
                scaleY = scale
                rotationZ = -1.2f * lift
            },
    ) {
        Column(
            Modifier
                .animateContentSize(if (reduced) snap() else spring(stiffness = 600f))
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = if (lifted) 8.dp else 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { rotationZ = spin }
                        .clip(MorphShape(morph, morphProgress))
                        .background(lerp(colors.primaryContainer, colors.primary, lift)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = lerp(colors.onPrimaryContainer, colors.onPrimary, lift),
                        modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -spin },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        command.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (lifted) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (command.shortcut && !lifted) {
                        Text(
                            stringResource(R.string.shell_in_menu),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                IconButton(onClick = onRun, enabled = !blocked && !lifted, modifier = Modifier.size(48.dp)) {
                    if (running) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.shell_run),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.common_edit))
                }
                Box(handle.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.DragIndicator,
                        contentDescription = stringResource(R.string.shell_drag, command.name),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            if (!lifted) Text(
                command.command,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 6.dp),
            )
            if (!lifted && lastRun > 0L) {
                Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.shell_last_run, lastRunText(lastRun)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** "Today, 10:24 AM", "Yesterday, 10:24 AM" or the date and time, in the device's own format. */
@Composable
private fun lastRunText(time: Long): String {
    val context = LocalContext.current
    val day = when {
        android.text.format.DateUtils.isToday(time) -> stringResource(R.string.shell_today)
        android.text.format.DateUtils.isToday(time + android.text.format.DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.shell_yesterday)
        else -> android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date(time))
    }
    return day + ", " + android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(time))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCommandDialog(
    existing: SavedCommand?,
    categories: List<String>,
    takenTiles: Map<Int, String>,
    /** How many other commands already sit in the app icon menu. */
    otherShortcuts: Int,
    /** Colours the existing categories have, so a new category's random colour is a different one. */
    usedCategoryColors: List<Int>,
    onNewCategory: (name: String, description: String, color: Int) -> Unit,
    onSave: (name: String, command: String, shortcut: Boolean, mode: ShellMode, timeoutSec: Int, detached: Boolean, confirm: Boolean, showStatus: Boolean, category: String, tile: Int, statusWaitSec: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDuplicate: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    val editor = remember { CommandEditorState(existing?.command.orEmpty()) }
    // Strata's own code editor, used by the full-screen command editor; it reports its text back into [editor].
    val controller = remember { EditorController().also { c -> c.onEdited = { editor.syncText(c.text()) } } }
    val commandField = editor.value
    val command = commandField.text
    var shortcut by remember { mutableStateOf(existing?.shortcut ?: false) }
    var mode by remember { mutableStateOf(existing?.mode ?: ShellMode.AUTO) }
    var timeoutSec by remember { mutableStateOf(existing?.timeoutSec ?: ShellRunner.DEFAULT_TIMEOUT_SEC) }
    var detached by remember { mutableStateOf(existing?.detached ?: false) }
    var confirm by remember { mutableStateOf(existing?.confirm ?: false) }
    var showStatus by remember { mutableStateOf(existing?.showStatus ?: true) }
    var waitText by remember { mutableStateOf(WAIT_PRESETS.minByOrNull { kotlin.math.abs(it - (existing?.statusWaitSec ?: ShellRunner.DEFAULT_STATUS_WAIT_SEC)) }.toString()) }
    val waitValid = waitText.toIntOrNull()?.let { it in ShellRunner.MIN_STATUS_WAIT_SEC..ShellRunner.MAX_STATUS_WAIT_SEC } == true
    var category by remember { mutableStateOf(existing?.category.orEmpty()) }
    var tile by remember { mutableStateOf(existing?.tile ?: 0) }
    var newCategoryDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    // The status banner is drawn over other apps, which needs that permission; it is asked for when the switch is
    // turned on. Without it the status is shown as a plain Toast (the switch stays on either way).
    val overlayContext = LocalContext.current
    var askOverlay by remember { mutableStateOf(false) }
    val maxShortcuts = remember { runCatching { ShortcutManagerCompat.getMaxShortcutCountPerActivity(overlayContext) }.getOrDefault(0) }
    val shortcutsFull = maxShortcuts > 0 && otherShortcuts >= maxShortcuts
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val highlight = rememberShellHighlight()
    val clipboard = rememberTextClipboard()
    var expanded by remember { mutableStateOf(false) }
    var pickInsert by remember { mutableStateOf(false) }
    var pickTest by remember { mutableStateOf(false) }
    var snippetsOpen by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    var askDeleteCommand by remember { mutableStateOf(false) }
    // The user's own templates of the command editor: stored on this device and included in a settings backup.
    val templateStore = remember { ShellTemplateStore(overlayContext.applicationContext) }
    var userTemplates by remember { mutableStateOf(templateStore.load()) }
    var askTemplateName by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var deleteTemplate by remember { mutableStateOf<ShellTemplate?>(null) }
    // When the name is left empty, the command's first line is used (the same rule the save uses).
    val autoName = remember(command) { command.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(40) }
    /** Writes [text] at the cursor (replacing any selection) and leaves the cursor after it. */
    fun insertText(text: String) {
        // While the full-screen editor is open it owns the text (and the cursor), so write there.
        if (expanded && controller.editor != null) {
            controller.insertText(text)
            return
        }
        val v = editor.value
        val start = v.selection.min.coerceIn(0, v.text.length)
        val end = v.selection.max.coerceIn(start, v.text.length)
        editor.set(TextFieldValue(v.text.replaceRange(start, end, text), TextRange(start + text.length)))
    }
    /** A ready-made command: replaces an empty box, otherwise goes on a new line at the end. */
    fun applySnippet(label: String, cmd: String) {
        val joined = if (command.isBlank()) cmd else command.trimEnd('\n') + "\n" + cmd
        if (expanded && controller.editor != null) controller.replaceWhole(joined) else editor.set(TextFieldValue(joined, TextRange(joined.length)))
        if (name.isBlank()) name = label
    }
    // A test run executes the command once, right here, without saving it or writing it to the history.
    var testOutput by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<ShellResult?>(null) }
    var testRunning by remember { mutableStateOf(false) }
    val testScope = rememberCoroutineScope()
    val runningNow by rememberUpdatedState(testRunning)
    val testId = "editortest"
    val testTimeout = if (timeoutSec <= ShellRunner.NO_TIMEOUT) ShellRunner.DEFAULT_TIMEOUT_SEC else timeoutSec
    fun runTest(cmd: String) {
        testOutput = ""
        testResult = null
        testRunning = true
        testScope.launch {
            val r = ShellRunner.run(cmd, mode, testTimeout, onOutput = { chunk -> testOutput = ((testOutput ?: "") + chunk).takeLast(20_000) }, runId = testId)
            testOutput = r.output.takeLast(20_000)
            testResult = r
            testRunning = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (runningNow) CoroutineScope(Dispatchers.IO).launch { ShellRunner.stopRun(testId, mode) } }
    }
    if (askOverlay) {
        ExpressiveDialog(
            onDismissRequest = { askOverlay = false },
            title = stringResource(R.string.shell_overlay_title),
            icon = Icons.Rounded.Terminal,
            confirmButton = {
                TextButton(onClick = {
                    askOverlay = false
                    runCatching { overlayLauncher.launch(ShellStatus.overlaySettingsIntent(overlayContext)) }
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { askOverlay = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(stringResource(R.string.shell_overlay_message))
        }
    }
    if (newCategoryDialog) {
        CategoryDialog(
            title = stringResource(R.string.shell_category_new),
            initialName = "",
            initialDescription = "",
            initialColor = remember { ShellCategoryInfo.randomColor(usedCategoryColors) },
            confirmText = stringResource(R.string.common_create),
            onDismiss = { newCategoryDialog = false },
            onConfirm = { n, d, c -> onNewCategory(n, d, c); category = n.trim(); newCategoryDialog = false },
        )
    }
    // Closing with unsaved changes asks first.
    val initialValues = remember { listOf<Any>(name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, waitText, category, tile) }
    val dirty = listOf<Any>(name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, waitText, category, tile) != initialValues
    val requestClose: () -> Unit = { if (dirty) { askDiscard = true } else { onDismiss() } }
    if (askDiscard) {
        ExpressiveDialog(
            onDismissRequest = { askDiscard = false },
            title = stringResource(R.string.shell_discard_title),
            icon = Icons.Rounded.Edit,
            confirmButton = { TextButton(onClick = { askDiscard = false; onDismiss() }) { Text(stringResource(R.string.common_discard)) } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text(stringResource(R.string.shell_keep_editing)) } },
        ) {
            Text(stringResource(R.string.shell_discard_message))
        }
    }
    if (pickInsert) {
        ShellAppPickerDialog(
            title = stringResource(R.string.shell_insert_pick_title),
            lastPackage = null,
            insert = true,
            onPick = { pkgs -> pickInsert = false; insertText(pkgs.filter { ShellParams.isValidPackage(it) }.joinToString(" ")) },
            onDismiss = { pickInsert = false },
        )
    }
    if (askDeleteCommand && onDelete != null) {
        ExpressiveDialog(
            onDismissRequest = { askDeleteCommand = false },
            title = stringResource(R.string.shell_delete_confirm_title, name.ifBlank { autoName }),
            icon = Icons.Rounded.Delete,
            confirmButton = { TextButton(onClick = { askDeleteCommand = false; onDelete() }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { askDeleteCommand = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(stringResource(R.string.shell_delete_command_text))
        }
    }
    if (askTemplateName) {
        ExpressiveDialog(
            onDismissRequest = { askTemplateName = false },
            title = stringResource(R.string.shell_template_add_title),
            icon = Icons.Rounded.Edit,
            confirmButton = {
                TextButton(
                    enabled = templateName.isNotBlank(),
                    onClick = {
                        userTemplates = templateStore.add(ShellTemplate(templateName, controller.text().ifBlank { command }))
                        askTemplateName = false
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { askTemplateName = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            OutlinedTextField(
                value = templateName,
                onValueChange = { templateName = it },
                label = { Text(stringResource(R.string.shell_template_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    deleteTemplate?.let { t ->
        ExpressiveDialog(
            onDismissRequest = { deleteTemplate = null },
            title = stringResource(R.string.shell_delete_confirm_title, t.name),
            icon = Icons.Rounded.Delete,
            confirmButton = { TextButton(onClick = { userTemplates = templateStore.remove(t.name); deleteTemplate = null }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTemplate = null }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(stringResource(R.string.shell_template_delete_text))
        }
    }
    if (pickTest) {
        ShellAppPickerDialog(
            title = stringResource(R.string.shell_param_pick_title, name.ifBlank { autoName }),
            lastPackage = null,
            onPick = { pkgs ->
                pickTest = false
                runTest(ShellParams.resolveAll(SavedCommand(id = testId, name = name, command = command.trim()), pkgs).command)
            },
            onDismiss = { pickTest = false },
        )
    }
    if (expanded) {
        ExpandedCommandEditor(
            controller = controller,
            initialText = editor.value.text,
            testStatus = { TestStatus(testResult, testRunning) },
            testOutput = testOutput,
            testRunning = testRunning,
            canRun = command.isNotBlank(),
            onRun = { if (ShellParams.needsPackage(command)) pickTest = true else runTest(command.trim()) },
            onStop = { testScope.launch { ShellRunner.stopRun(testId, mode) } },
            onInsert = { insertText(it) },
            onSnippet = { label, cmd -> applySnippet(label, cmd) },
            onPickApp = { pickInsert = true },
            onPaste = { clipboard.readText { t -> if (t.isNotEmpty()) insertText(t) } },
            onClear = { controller.replaceWhole("") },
            userTemplates = userTemplates,
            onAddTemplate = {
                if (controller.text().isBlank()) {
                    android.widget.Toast.makeText(overlayContext, R.string.shell_template_empty, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    templateName = name.ifBlank { autoName }
                    askTemplateName = true
                }
            },
            onDeleteTemplate = { deleteTemplate = it },
            onClose = { expanded = false },
        )
    }
    // A full-screen editor instead of a small dialog: the options are grouped into sections (run, result, quick
    // access) and the whole page scrolls, so nothing is squeezed into a short inner scroll area.
    var quickOpen by remember { mutableStateOf(category.isNotEmpty() || shortcut || tile != 0) }
    val canSave = command.isNotBlank() && (!showStatus || waitValid)
    val save = {
        onSave(
            name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, category, tile,
            waitText.toIntOrNull()?.coerceIn(ShellRunner.MIN_STATUS_WAIT_SEC, ShellRunner.MAX_STATUS_WAIT_SEC) ?: ShellRunner.DEFAULT_STATUS_WAIT_SEC,
        )
    }
    if (askSave) {
        ExpressiveDialog(
            onDismissRequest = { askSave = false },
            title = stringResource(R.string.shell_save_confirm_title),
            icon = Icons.Rounded.Edit,
            confirmButton = { TextButton(onClick = { askSave = false; save() }) { Text(stringResource(R.string.common_save)) } },
            dismissButton = { TextButton(onClick = { askSave = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(stringResource(R.string.shell_save_confirm_message))
        }
    }
    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                modifier = Modifier.systemBarsPadding().imePadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (existing == null) R.string.shell_add else R.string.shell_edit)) },
                        navigationIcon = {
                            IconButton(onClick = requestClose) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
                            }
                        },
                        actions = {
                            // A small filled button; it is dim until something changed (and while the command is empty).
                            Button(
                                onClick = { askSave = true },
                                enabled = canSave && dirty,
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                modifier = Modifier.padding(end = 8.dp).height(36.dp),
                            ) { Text(stringResource(R.string.common_save)) }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Name and command are cards like the rest of the page: an icon, a title and the value under it.
                    val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                    val tName = stringResource(R.string.common_name)
                    val tCommand = stringResource(R.string.shell_command)
                    val tHelp = stringResource(R.string.shell_help)
                    SettingsGroup(tCommand) {
                        custom(
                            title = tName,
                            icon = Icons.Rounded.Edit,
                        ) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
                                if (name.isEmpty() && autoName.isNotEmpty()) {
                                    Text(autoName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                                )
                            }
                        }
                        custom(
                            icon = Icons.Rounded.Terminal,
                            headline = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tCommand)
                                    Icon(
                                        Icons.Rounded.Info,
                                        contentDescription = tHelp,
                                        modifier = Modifier.padding(start = 6.dp).size(20.dp).clip(CircleShape).clickable { showHelp = !showHelp }.padding(1.dp),
                                    )
                                }
                            },
                            trailing = {
                                CommandRunButton(
                                    runEnabled = command.isNotBlank(),
                                    running = testRunning,
                                    onRun = { if (ShellParams.needsPackage(command)) pickTest = true else runTest(command.trim()) },
                                    onStop = { testScope.launch { ShellRunner.stopRun(testId, mode) } },
                                )
                            },
                        ) {
                            CommandPreview(command, onOpen = { expanded = true })
                        }
                    }
                    if (showHelp) {
                        Column(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HelpEntry("{package}", stringResource(R.string.shell_param_hint))
                            HelpEntry("\u25B6", stringResource(R.string.shell_test_hint))
                        }
                    }
                    TestStatus(testResult, testRunning)
                    if (testOutput != null) TestOutputPanel(testOutput, testRunning)

                    // The options use the same grouped rows as the settings pages: an icon, a title, a short note and the
                    // control on the row, in rounded categories with a heading.
                    val timeoutOptions = listOf(ShellRunner.NO_TIMEOUT) + TIMEOUT_CHOICES
                    val timeoutLabels = timeoutOptions.associateWith { timeoutLabel(it) }
                    val modeLabels = ShellMode.entries.associateWith { m ->
                        stringResource(
                            when (m) {
                                ShellMode.AUTO -> R.string.shell_mode_auto
                                ShellMode.SHIZUKU -> R.string.shell_via_shizuku
                                ShellMode.ROOT -> R.string.shell_via_root
                            },
                        )
                    }
                    val tRunWith = stringResource(R.string.shell_run_with)
                    val tDetached = stringResource(R.string.shell_detached)
                    val tDetachedHint = stringResource(R.string.shell_detached_hint)
                    val tTimeout = stringResource(R.string.shell_timeout)
                    val tConfirm = stringResource(R.string.shell_confirm_switch)
                    val tShowStatus = stringResource(R.string.shell_show_status)
                    val tStatusWait = stringResource(R.string.shell_status_wait)
                    val tCategory = stringResource(R.string.shell_category_title)
                    val tShortcut = stringResource(R.string.shell_shortcut)
                    val tTile = stringResource(R.string.shell_tile_setting)
                    val tPin = stringResource(R.string.shell_pin_home)
                    val tDuplicate = stringResource(R.string.shell_duplicate)
                    val tDelete = stringResource(R.string.common_delete)
                    // The status only waits this long for the result; a command whose limit is longer (or that has none)
                    // can still be running when the message closes, and then its result is only in the history.
                    val wait = waitText.toIntOrNull()
                    val waitHint = if (showStatus && waitValid && wait != null && (timeoutSec <= ShellRunner.NO_TIMEOUT || timeoutSec > wait)) {
                        stringResource(R.string.shell_status_wait_hint)
                    } else {
                        null
                    }
                    val shortcutFullNote = if (!shortcut && shortcutsFull) stringResource(R.string.shell_shortcut_full, maxShortcuts) else null
                    val takenBy = takenTiles[tile]
                    val tileNote = when {
                        takenBy != null -> stringResource(R.string.shell_tile_taken, takenBy)
                        tile != 0 -> stringResource(R.string.shell_tile_hint)
                        else -> null
                    }

                    // ── Run ──
                    SettingsGroup(stringResource(R.string.shell_section_run)) {
                        choice(tRunWith, ShellMode.entries, mode, { modeLabels.getValue(it) }, { mode = it }, icon = Icons.Rounded.Terminal)
                        switch(tDetached, detached, { detached = it }, summary = tDetachedHint, icon = Icons.Rounded.Schedule)
                        custom(title = tTimeout, icon = Icons.Rounded.Timer, wide = true) {
                            ScrollableConnectedChoice(
                                labels = timeoutOptions.map { timeoutLabels.getValue(it) },
                                selected = timeoutOptions.indexOf(timeoutSec),
                                onSelect = { timeoutSec = timeoutOptions[it] },
                            )
                        }
                        switch(tConfirm, confirm, { confirm = it }, icon = Icons.Rounded.VerifiedUser)
                    }

                    // ── Result display ──
                    SettingsGroup(stringResource(R.string.shell_section_result)) {
                        switch(
                            tShowStatus,
                            showStatus,
                            { on ->
                                showStatus = on
                                if (on && !ShellStatus.canDrawOverlay(overlayContext)) askOverlay = true
                            },
                            icon = Icons.Rounded.Visibility,
                        )
                        if (showStatus) {
                            custom(title = tStatusWait, icon = Icons.Rounded.AccessTime, wide = true) {
                                // Quick picks only (max 20 s, 10 is the default); the note sits inside the card, under the buttons.
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ConnectedChoice(
                                        options = WAIT_PRESETS,
                                        selected = waitText.toIntOrNull() ?: -1,
                                        label = { it.toString() },
                                        onSelect = { waitText = it.toString() },
                                    )
                                    if (waitHint != null) {
                                        // The wide content starts under the icon; the note lines up with the title again.
                                        Text(waitHint, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ── Quick access ──
                    SettingsGroup(stringResource(R.string.shell_section_quick)) {
                        custom(title = tCategory, icon = Icons.Rounded.Folder, wide = true) {
                            // Like "Run with": connected buttons under the title. One row that scrolls sideways: "No category" and
                            // "+ New category" stay first, then the categories.
                            val chipsState = rememberLazyListState()
                            val tNone = stringResource(R.string.shell_category_none)
                            val tNew = "+ " + stringResource(R.string.shell_category_new)
                            val selectedAt = if (category.isEmpty()) 0 else categories.indexOf(category).let { if (it < 0) 0 else it + 2 }
                            LaunchedEffect(category, categories.size) { chipsState.animateScrollToItem(selectedAt) }
                            ScrollableConnectedChoice(
                                labels = listOf(tNone, tNew) + categories,
                                selected = selectedAt,
                                onSelect = { i ->
                                    when (i) {
                                        0 -> category = ""
                                        1 -> newCategoryDialog = true
                                        else -> category = categories[i - 2]
                                    }
                                },
                                state = chipsState,
                            )
                        }
                        // The launcher only shows a few app icon shortcuts; a command that already has one may always turn it off.
                        switch(tShortcut, shortcut, { shortcut = it }, summary = shortcutFullNote, icon = Icons.Rounded.Shortcut, enabled = shortcut || !shortcutsFull)
                        custom(title = tTile, summary = tileNote, icon = Icons.Rounded.DashboardCustomize, wide = true) {
                            val slots = (1..dev.cluvex.strata.core.shell.ShellTiles.SLOTS).toList()
                            ScrollableConnectedChoice(
                                labels = slots.map { n ->
                                    val owner = takenTiles[n]
                                    // Once this command picks the slot, the button shows this command's name (what the list will show after
                                    // saving) instead of the name of the command that held it.
                                    val shown = if (tile == n) name.trim().ifEmpty { command.lineSequence().firstOrNull().orEmpty().trim().take(40) }.ifEmpty { null } else owner
                                    if (shown == null) n.toString() else "$n · ${shown.take(10)}${if (shown.length > 10) "…" else ""}"
                                },
                                // Tapping the chosen tile again clears the choice (0 = no tile).
                                selected = slots.indexOf(tile),
                                onSelect = { i -> tile = if (tile == slots[i]) 0 else slots[i] },
                                // A slot another command already holds is highlighted and shows that command's name.
                                highlighted = slots.mapIndexedNotNull { i, n -> if (takenTiles[n] != null) i else null }.toSet(),
                            )
                        }
                        // Pinning only exists for a saved command (a new one has no id yet).
                        if (onPin != null) link(tPin, icon = Icons.Rounded.Home, chevron = false) { onPin() }
                    }

                    // ── Actions on an existing command: the category is left out when there are none (a new command). Delete asks first. ──
                    SettingsGroup(stringResource(R.string.shell_section_actions)) {
                        if (onDuplicate != null) link(tDuplicate, icon = Icons.Rounded.ContentCopy, chevron = false) { onDuplicate() }
                        if (onDelete != null) link(tDelete, icon = Icons.Rounded.Delete, tint = Tint.ERROR, chevron = false) { askDeleteCommand = true }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Heading of a category of options in the command editor. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

private sealed interface ShellRow {
    val key: String

    data class Category(val name: String, val count: Int, val collapsed: Boolean) : ShellRow {
        override val key: String get() = "g:$name"
    }

    object Label : ShellRow {
        override val key: String = "uncategorized"
    }

    data class Cmd(val command: SavedCommand, val nested: Boolean) : ShellRow {
        override val key: String get() = command.id
    }
}

/**
 * Header of a category: a bar and a soft glow in its colour, its name, its description (if it has one), the number of
 * commands and the fold arrow. Tap to fold or unfold it, long-press to edit or delete it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryHeader(
    name: String,
    description: String,
    color: Color,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (highlight) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (highlight) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onToggle, onLongClick = onMenu),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Row(
                Modifier.weight(1f).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (description.isNotEmpty()) {
                        Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Surface(shape = RoundedCornerShape(10.dp), color = color, contentColor = if (color.luminance() > 0.5f) Color.Black else Color.White) {
                    Text(count.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp))
                }
                Icon(
                    if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = stringResource(if (collapsed) R.string.shell_category_expand else R.string.shell_category_collapse),
                    tint = color,
                )
            }
        }
    }
}

/** New or edit dialog of a category: name, an optional description and a colour (picked with the app's colour picker). */
@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    initialColor: Int,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, color: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Moves the category one place up (-1) or down (1); null for a category that doesn't exist yet. */
    onMove: ((Int) -> Unit)? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
) {
    var text by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var color by remember { mutableIntStateOf(initialColor) }
    var pickColor by remember { mutableStateOf(false) }
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Rounded.CreateNewFolder,
        confirmButton = { TextButton(onClick = { onConfirm(text, description, color) }, enabled = text.isNotBlank()) { Text(confirmText) } },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.shell_category_delete)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.shell_category_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.shell_category_description)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                onClick = { pickColor = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)))
                    Text(stringResource(R.string.shell_category_color), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            if (onMove != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shell_category_move_up), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shell_category_move_down), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (pickColor) {
        ColorPickerDialog(
            initial = color,
            onPick = { color = it or 0xFF000000.toInt(); pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
}

@Composable
private fun RunResultDialog(run: ShellRun, onDismiss: () -> Unit) {
    val clipboard = rememberTextClipboard()
    val result = run.result
    // A command run in the background only prints a "started" message; show its log file instead.
    val detachedLog = if (run.command.detached && result.backend != null) ShellRunner.detachedLogPath(run.command.id) else null
    val readMode = if (result.backend == ShellBackend.ROOT) ShellMode.ROOT else ShellMode.SHIZUKU
    var reload by remember { mutableIntStateOf(0) }
    var fileText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var stillRunning by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    // How a background command really ended (timed out, stopped, its exit code): read once it has stopped running.
    var endedEntry by remember { mutableStateOf<LogEntry?>(null) }
    val filter = rememberOutputFilter()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    if (detachedLog != null) {
        // Reads the log once, then keeps following it every few seconds for as long as the command runs.
        // `reload` only changes after Stop, to pick up the final state straight away.
        LaunchedEffect(reload) {
            loading = true
            if (reload == 0) delay(700)
            fileText = ShellRunner.readFile(detachedLog, readMode)
            stillRunning = ShellRunner.isDetachedRunning(run.command.id, readMode)
            loading = false
            while (stillRunning) {
                delay(LOG_FOLLOW_MS)
                // A failed read keeps the text that is already shown.
                ShellRunner.readFile(detachedLog, readMode)?.let { fileText = it }
                stillRunning = ShellRunner.isDetachedRunning(run.command.id, readMode)
            }
            if (!stillRunning) {
                val store = ShellLogStore(context)
                val entry = withContext(Dispatchers.IO) {
                    store.load().filter { it.commandId == run.command.id && it.logFile != null }.maxByOrNull { it.time }
                }
                if (entry != null) endedEntry = ShellExecutor.finalizeDetached(store, entry, readMode) ?: entry
            }
        }
    }
    // The same words as the history: Running / Success / Failed / Timed out / Stopped, with the same line under them.
    val outcome = when {
        detachedLog == null -> shellOutcome(false, result.timedOut, result.stopped, result.exitCode)
        endedEntry != null -> shellOutcome(false, endedEntry!!.timedOut, endedEntry!!.stopped, endedEntry!!.exitCode)
        !stillRunning && !loading -> shellOutcome(false, false, false, 0)
        else -> ShellOutcome.RUNNING
    }
    val status = shellStatusLine(
        outcome,
        result.backend?.name,
        endedEntry?.exitCode ?: result.exitCode,
        if (detachedLog != null) endedEntry?.durationMs else result.durationMs,
    )
    // A background log opens at its last line and follows it while the command runs.
    val scroll = rememberTailScrollState(enabled = detachedLog != null)
    val body = if (detachedLog != null) {
        when {
            loading && fileText == null -> "…"
            fileText == null -> stringResource(R.string.shell_log_file_missing)
            fileText!!.isEmpty() -> stringResource(R.string.shell_no_output)
            else -> fileText!!
        }
    } else {
        result.output.ifEmpty { if (result.backend == null) "" else stringResource(R.string.shell_no_output) }
    }
    val copyText = if (detachedLog != null) fileText.orEmpty() else result.output
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = run.command.name,
        icon = Icons.Rounded.Terminal,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        // Stop sits with Copy and Close, in the same row of the dialog.
        dismissButton = if (copyText.isNotEmpty() || (detachedLog != null && stillRunning)) {
            {
                Row {
                    if (detachedLog != null && stillRunning) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    stopping = true
                                    ShellRunner.stopDetached(run.command.id, readMode)
                                    stopping = false
                                    reload++
                                }
                            },
                            enabled = !stopping,
                        ) { Text(stringResource(R.string.shell_log_stop), color = MaterialTheme.colorScheme.error) }
                    }
                    if (copyText.isNotEmpty()) {
                        OutputFilterButton(filter)
                        TextButton(onClick = { clipboard.setText(copyText) }) { Text(stringResource(R.string.common_copy)) }
                    }
                }
            }
        } else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Same color and size as the status line in the history (a list item's supporting text).
            Text(shellStatusAnnotated(outcome, status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (detachedLog != null) {
                // Same label and path as the history, so it is clear this is a background command's log file.
                Text(stringResource(R.string.shell_log_file), style = MaterialTheme.typography.labelMedium)
                Text(
                    detachedLog,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutputFilterField(filter, body)
            if (body.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(scroll)) {
                    SelectionContainer {
                        Text(
                            filter.apply(body),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                textDirection = TextDirection.Ltr,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** How often the log of a background command is re-read while it runs. */
private const val LOG_FOLLOW_MS = 2_500L

@Composable
private fun HelpEntry(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
        )
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** The same cookie-shaped badge a command row puts behind its icon, reused for the + menu items. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FabMenuIcon(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    val morph = rememberPolygonMorph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie9Sided)
    Box(
        Modifier
            .size(40.dp)
            .clip(MorphShape(morph, 0f))
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
    }
}
