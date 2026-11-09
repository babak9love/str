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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AssistChip
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import dev.cluvex.strata.core.shell.ShellRunner
import dev.cluvex.strata.ui.expressive.ExpressiveDialog
import dev.cluvex.strata.ui.expressive.ExpressiveEmptyState
import dev.cluvex.strata.ui.expressive.MorphShape
import dev.cluvex.strata.ui.expressive.rememberPolygonMorph
import dev.cluvex.strata.ui.rememberTextClipboard
import dev.cluvex.strata.ui.theme.LocalAppearance

private val TIMEOUT_CHOICES = listOf(10, 30, 60, 300, 600)

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
    // The + button opens a small menu: a new command or a new group.
    var fabOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var newGroupDialog by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf<String?>(null) }
    var liveId by remember { mutableStateOf<String?>(null) }
    // Swiping a row to the right asks before deleting it; swiping to the left opens its editor.
    var deleteCmd by remember { mutableStateOf<SavedCommand?>(null) }
    var deleteGroup by remember { mutableStateOf<String?>(null) }
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
    val listState = rememberLazyListState()
    // Display rows: each group (header + its commands unless folded), then the commands that are in no group.
    val allRows = remember(ui.commands, ui.groups, ui.collapsed) {
        buildList<ShellRow> {
            for (g in ui.groups) {
                val items = ui.commands.filter { it.group == g }
                val folded = g in ui.collapsed
                add(ShellRow.Group(g, items.size, folded))
                if (!folded) items.forEach { add(ShellRow.Cmd(it, nested = true)) }
            }
            val loose = ui.commands.filter { it.group.isEmpty() }
            if (ui.groups.isNotEmpty()) add(ShellRow.Label)
            loose.forEach { add(ShellRow.Cmd(it, nested = false)) }
        }
    }
    // While something is typed in the search, the list is flat: every command whose name, text or group matches.
    val q = query.trim()
    val rows: List<ShellRow> = remember(allRows, ui.commands, q) {
        if (q.isEmpty()) {
            allRows
        } else {
            ui.commands
                .filter { c -> c.name.contains(q, ignoreCase = true) || c.command.contains(q, ignoreCase = true) || c.group.contains(q, ignoreCase = true) }
                .map { ShellRow.Cmd(it, nested = false) }
        }
    }
    // The drag handlers live inside pointerInput blocks that are keyed only by the command id, so they keep the
    // lambdas (and the `rows` list) from the composition in which they were created. Without this, after a group is
    // folded/unfolded or a command moves, list indexes map to the wrong rows and the wrong (or no) group is targeted.
    val rowsNow by rememberUpdatedState(rows)
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The group the dragged card is currently over ("" = ungrouped); the card only joins it when it is dropped.
    var hoverGroup by remember { mutableStateOf<String?>(null) }
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
        hoverGroup = null
    }

    // What the dragged card is over. A group header (or the "Ungrouped" label) is small next to a card, so overlapping
    // it clearly is enough to target it; otherwise the row under the card's center is the target.
    fun findTarget(cur: LazyListItemInfo, dragged: SavedCommand): Pair<LazyListItemInfo, ShellRow>? {
        val items = listState.layoutInfo.visibleItemsInfo
        val top = cur.offset + dragOffset
        val bottom = top + cur.size
        val byOverlap = items.mapNotNull { info ->
            if (info.key == dragged.id) return@mapNotNull null
            val row = rowsNow.getOrNull(info.index) ?: return@mapNotNull null
            val targetable = (row is ShellRow.Group && row.name != dragged.group) || (row is ShellRow.Label && dragged.group.isNotEmpty())
            if (!targetable) return@mapNotNull null
            val overlap = minOf(bottom, (info.offset + info.size).toFloat()) - maxOf(top, info.offset.toFloat())
            if (overlap >= info.size * 0.3f) Triple(info, row, overlap) else null
        }.maxByOrNull { it.third }
        if (byOverlap != null) return byOverlap.first to byOverlap.second
        val center = top + cur.size / 2f
        val hit = items.firstOrNull { it.key != dragged.id && center >= it.offset && center <= it.offset + it.size } ?: return null
        return rowsNow.getOrNull(hit.index)?.let { hit to it }
    }

    // Called when the finger is released: if the card sits over another group, it moves there now.
    fun dropInto(id: String) {
        val cur = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val dragged = ui.commands.firstOrNull { it.id == id } ?: return
        val (hit, row) = findTarget(cur, dragged) ?: return
        when (row) {
            is ShellRow.Group -> if (row.name != dragged.group) {
                vm.moveToGroup(id, row.name, atEnd = false)
                // A folded group opens so the dropped command stays visible.
                if (row.collapsed) vm.expandGroup(row.name)
            }
            is ShellRow.Label -> if (dragged.group.isNotEmpty()) vm.moveToGroup(id, "", atEnd = false)
            is ShellRow.Cmd -> if (row.command.group != dragged.group) {
                val center = cur.offset + dragOffset + cur.size / 2f
                vm.moveNextTo(id, row.command.id, after = center > hit.offset + hit.size / 2f)
            }
        }
    }

    fun endDrag() {
        val dropped = dragId
        if (dropped != null) {
            dropInto(dropped)
            hoverGroup = null
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
        // Live reordering only happens inside the card's own group; joining another group is decided on release.
        fun sameGroupCmd(index: Int) = (rowsNow.getOrNull(index) as? ShellRow.Cmd)?.command?.group == dragged.group
        hoverGroup = when (val r = findTarget(cur, dragged)?.second) {
            is ShellRow.Group -> r.name
            is ShellRow.Cmd -> r.command.group
            is ShellRow.Label -> ""
            null -> null
        }?.takeIf { it != dragged.group }
        val next = visible.firstOrNull { it.index == cur.index + 1 && sameGroupCmd(it.index) }
        val prev = visible.firstOrNull { it.index == cur.index - 1 && sameGroupCmd(it.index) }
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
    // even when the finger is still; after each step dragBy(0f) refreshes the highlighted group and live swaps.
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
                            text = { Text(stringResource(R.string.shell_group_new)) },
                            icon = { FabMenuIcon(Icons.Rounded.CreateNewFolder) },
                            onClick = { fabOpen = false; newGroupDialog = true },
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
                ui.commands.isEmpty() && ui.groups.isEmpty() -> ExpressiveEmptyState(
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
                            is ShellRow.Group -> SwipeActionRow(
                                enabled = dragId == null,
                                shape = RoundedCornerShape(16.dp),
                                onSwipeRight = { deleteGroup = row.name },
                                onSwipeLeft = { groupMenu = row.name },
                                modifier = Modifier.animateItem(),
                            ) {
                                GroupHeader(
                                    name = row.name,
                                    count = row.count,
                                    collapsed = row.collapsed,
                                    highlight = hoverGroup == row.name,
                                    onToggle = { vm.toggleCollapsed(row.name) },
                                    onMenu = { groupMenu = row.name },
                                )
                            }
                            is ShellRow.Label -> Text(
                                stringResource(R.string.shell_group_ungrouped),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hoverGroup == "") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
            groups = ui.groups,
            // Tile slots that other commands already hold, with the name of the command that holds each.
            takenTiles = ui.commands.filter { it.tile != 0 && it.id != target.command?.id }.associate { it.tile to it.name },
            otherShortcuts = ui.commands.count { it.shortcut && it.id != target.command?.id },
            onNewGroup = vm::addGroup,
            onSave = { name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, group, tile ->
                vm.save(target.command?.id, name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, group, tile)
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

    deleteGroup?.let { g ->
        ExpressiveDialog(
            onDismissRequest = { deleteGroup = null },
            title = stringResource(R.string.shell_delete_confirm_title, g),
            icon = Icons.Rounded.Delete,
            confirmButton = {
                TextButton(onClick = { deleteGroup = null; vm.deleteGroup(g) }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteGroup = null }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            Text(stringResource(R.string.shell_group_delete_confirm_text))
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

    if (newGroupDialog) {
        GroupNameDialog(
            title = stringResource(R.string.shell_group_new),
            initial = "",
            confirmText = stringResource(R.string.common_create),
            onDismiss = { newGroupDialog = false },
            onConfirm = { vm.addGroup(it); newGroupDialog = false },
        )
    }
    groupMenu?.let { g ->
        val position = ui.groups.indexOf(g)
        GroupNameDialog(
            title = stringResource(R.string.shell_group_edit),
            initial = g,
            confirmText = stringResource(R.string.common_save),
            onDismiss = { groupMenu = null },
            onConfirm = { vm.renameGroup(g, it); groupMenu = null },
            onDelete = { vm.deleteGroup(g); groupMenu = null },
            onMove = { by -> vm.moveGroup(g, by) },
            canMoveUp = position > 0,
            canMoveDown = position >= 0 && position < ui.groups.lastIndex,
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCommandDialog(
    existing: SavedCommand?,
    groups: List<String>,
    takenTiles: Map<Int, String>,
    /** How many other commands already sit in the app icon menu. */
    otherShortcuts: Int,
    onNewGroup: (String) -> Unit,
    onSave: (name: String, command: String, shortcut: Boolean, mode: ShellMode, timeoutSec: Int, detached: Boolean, confirm: Boolean, showStatus: Boolean, group: String, tile: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDuplicate: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var command by remember { mutableStateOf(existing?.command.orEmpty()) }
    var shortcut by remember { mutableStateOf(existing?.shortcut ?: false) }
    var mode by remember { mutableStateOf(existing?.mode ?: ShellMode.AUTO) }
    var timeoutSec by remember { mutableStateOf(existing?.timeoutSec ?: ShellRunner.DEFAULT_TIMEOUT_SEC) }
    var detached by remember { mutableStateOf(existing?.detached ?: false) }
    var confirm by remember { mutableStateOf(existing?.confirm ?: false) }
    var showStatus by remember { mutableStateOf(existing?.showStatus ?: true) }
    var group by remember { mutableStateOf(existing?.group.orEmpty()) }
    var tile by remember { mutableStateOf(existing?.tile ?: 0) }
    var newGroupDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    // The status banner is drawn over other apps, which needs that permission; it is asked for when the switch is
    // turned on. Without it the status is shown as a plain Toast (the switch stays on either way).
    val overlayContext = LocalContext.current
    var askOverlay by remember { mutableStateOf(false) }
    val maxShortcuts = remember { runCatching { ShortcutManagerCompat.getMaxShortcutCountPerActivity(overlayContext) }.getOrDefault(0) }
    val shortcutsFull = maxShortcuts > 0 && otherShortcuts >= maxShortcuts
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
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
    if (newGroupDialog) {
        GroupNameDialog(
            title = stringResource(R.string.shell_group_new),
            initial = "",
            confirmText = stringResource(R.string.common_create),
            onDismiss = { newGroupDialog = false },
            onConfirm = { onNewGroup(it); group = it.trim(); newGroupDialog = false },
        )
    }
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (existing == null) R.string.shell_add else R.string.shell_edit),
        icon = Icons.Rounded.Terminal,
        confirmButton = {
            TextButton(onClick = { onSave(name, command, shortcut, mode, timeoutSec, detached, confirm, showStatus, group, tile) }, enabled = command.isNotBlank()) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        // The name and the command stay pinned; only the options below them scroll. The command field scrolls by
        // itself when its text is long, and it is not inside another scrolling column, so the two never fight.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Help for the command: the icon sits inside the command box (top right); more entries can be added as the
            // command grows features.
            if (showHelp) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HelpEntry("{package}", stringResource(R.string.shell_param_hint))
                }
            }
            Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text(stringResource(R.string.shell_command)) },
                minLines = 3,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                // Room at the end of the first line for the help icon, so text never runs under it.
                trailingIcon = { Spacer(Modifier.width(32.dp)) },
                modifier = Modifier.fillMaxWidth(),
            )
                IconButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 8.dp).size(32.dp),
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.shell_help), modifier = Modifier.size(20.dp))
                }
            }
            Column(
                modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text(stringResource(R.string.shell_group_title), style = MaterialTheme.typography.labelLarge)
            // One horizontally scrollable row: "No group" and "+ New group" stay first, then the groups.
            val chipsState = rememberLazyListState()
            LaunchedEffect(group, groups.size) {
                val at = if (group.isEmpty()) 0 else groups.indexOf(group).let { if (it < 0) 0 else it + 2 }
                chipsState.animateScrollToItem(at)
            }
            LazyRow(state = chipsState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = group.isEmpty(), onClick = { group = "" }, label = { Text(stringResource(R.string.shell_group_none)) })
                }
                item {
                    AssistChip(onClick = { newGroupDialog = true }, label = { Text("+ " + stringResource(R.string.shell_group_new)) })
                }
                items(groups, key = { it }) { g ->
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shell_shortcut), modifier = Modifier.weight(1f))
                // The launcher only shows a few app icon shortcuts; a command that already has one may always turn it off.
                Switch(checked = shortcut, onCheckedChange = { shortcut = it }, enabled = shortcut || !shortcutsFull)
            }
            if (!shortcut && shortcutsFull) {
                Text(
                    stringResource(R.string.shell_shortcut_full, maxShortcuts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.shell_tile_setting), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in 1..dev.cluvex.strata.core.shell.ShellTiles.SLOTS) {
                    val owner = takenTiles[n]
                    // Once this command picks the slot, the chip shows this command's name (what the list will show after
                    // saving) instead of the name of the command that held it.
                    val shown = if (tile == n) name.trim().ifEmpty { command.lineSequence().firstOrNull().orEmpty().trim().take(40) }.ifEmpty { null } else owner
                    FilterChip(
                        selected = tile == n,
                        // Tapping the chosen tile again clears the choice (0 = no tile).
                        onClick = { tile = if (tile == n) 0 else n },
                        // A slot another command already holds is highlighted and shows that command's name.
                        label = { Text(if (shown == null) n.toString() else "$n · ${shown.take(10)}${if (shown.length > 10) "…" else ""}") },
                        colors = if (owner == null) FilterChipDefaults.filterChipColors() else FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    )
                }
            }
            val takenBy = takenTiles[tile]
            if (takenBy != null) {
                Text(
                    stringResource(R.string.shell_tile_taken, takenBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (tile != 0) {
                Text(
                    stringResource(R.string.shell_tile_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shell_confirm_switch), modifier = Modifier.weight(1f))
                Switch(checked = confirm, onCheckedChange = { confirm = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shell_show_status), modifier = Modifier.weight(1f))
                Switch(
                    checked = showStatus,
                    onCheckedChange = { on ->
                        showStatus = on
                        if (on && !ShellStatus.canDrawOverlay(overlayContext)) askOverlay = true
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.shell_detached))
                    Text(
                        stringResource(R.string.shell_detached_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = detached, onCheckedChange = { detached = it })
            }
            Text(stringResource(R.string.shell_run_with), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShellMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = {
                            Text(
                                stringResource(
                                    when (m) {
                                        ShellMode.AUTO -> R.string.shell_mode_auto
                                        ShellMode.SHIZUKU -> R.string.shell_via_shizuku
                                        ShellMode.ROOT -> R.string.shell_via_root
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Text(stringResource(R.string.shell_timeout), style = MaterialTheme.typography.labelLarge)
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { menuOpen = true }) {
                    Text(timeoutLabel(timeoutSec))
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    (listOf(ShellRunner.NO_TIMEOUT) + TIMEOUT_CHOICES).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text(timeoutLabel(sec)) },
                            onClick = { timeoutSec = sec; menuOpen = false },
                        )
                    }
                }
            }
            if (onPin != null) {
                OutlinedButton(onClick = onPin) { Text(stringResource(R.string.shell_pin_home)) }
            }
            if (onDuplicate != null) {
                OutlinedButton(onClick = onDuplicate) { Text(stringResource(R.string.shell_duplicate)) }
            }
            }
        }
    }
}

private sealed interface ShellRow {
    val key: String

    data class Group(val name: String, val count: Int, val collapsed: Boolean) : ShellRow {
        override val key: String get() = "g:$name"
    }

    object Label : ShellRow {
        override val key: String = "ungrouped"
    }

    data class Cmd(val command: SavedCommand, val nested: Boolean) : ShellRow {
        override val key: String get() = command.id
    }
}

/** Header of a group: tap to fold or unfold it, long-press to rename or delete it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (highlight) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onToggle, onLongClick = onMenu),
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(10.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp))
            }
            Icon(
                if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = stringResource(if (collapsed) R.string.shell_group_expand else R.string.shell_group_collapse),
            )
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Moves the group one place up (-1) or down (1); null for a group that doesn't exist yet. */
    onMove: ((Int) -> Unit)? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Rounded.CreateNewFolder,
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) } },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.shell_group_delete)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.shell_group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onMove != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shell_group_move_up), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shell_group_move_down), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
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
