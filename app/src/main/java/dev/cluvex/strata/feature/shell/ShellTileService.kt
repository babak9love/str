package dev.cluvex.strata.feature.shell

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.cluvex.strata.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.cluvex.strata.core.shell.SavedCommand
import dev.cluvex.strata.core.shell.ShellExecutor
import dev.cluvex.strata.core.shell.ShellLogSource
import dev.cluvex.strata.core.shell.ShellParams
import dev.cluvex.strata.core.shell.ShellResult
import dev.cluvex.strata.core.shell.ShellRunTracker
import dev.cluvex.strata.core.shell.ShellShortcuts
import dev.cluvex.strata.core.shell.ShellTiles

/**
 * A Quick Settings tile that runs one saved command; the app itself is never opened.
 *
 * A command that needs no questions (no confirmation, no {package}) runs right here and the panel stays open, so the
 * tile can be seen switching on while it runs and off when it ends, and the result is shown as a short message.
 * A command that has to ask something first is handed to [ShellShortcutActivity], the same screenless target the
 * launcher shortcuts use; that closes the panel, because the question has to be shown on screen.
 */
abstract class ShellTileService(private val slot: Int) : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onStartListening() {
        refresh()
        // While the panel is open the tile follows the run: on while the command runs, off when it ends.
        watcher?.cancel()
        watcher = scope.launch {
            combine(ShellRunTracker.runs, ShellRunTracker.pulses) { runs, pulses -> runs.keys + pulses }
                .collect { refresh() }
        }
    }

    override fun onStopListening() {
        watcher?.cancel()
        watcher = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        val command = ShellTiles.command(applicationContext, slot)
        if (command == null) {
            refresh()
            return
        }
        // A shell command must not run from a locked screen without the person unlocking first.
        if (isLocked) unlockAndRun { start(command) } else start(command)
    }

    private fun start(command: SavedCommand) {
        if (command.confirm || ShellParams.needsPackage(command.command)) openActivity(command) else runHere(command)
    }

    private fun runHere(command: SavedCommand) {
        // The same command is not started twice while it is still running.
        if (command.id in ShellRunTracker.runs.value) return
        val app = applicationContext
        // The run belongs to the process, not to this service: the service is released when the panel closes.
        runScope.launch {
            val result = ShellExecutor.runLogged(app, command, ShellLogSource.TILE)
            if (command.showStatus) withContext(Dispatchers.Main) { showResult(app, command, result) }
        }
    }

    private fun openActivity(command: SavedCommand) {
        val intent = Intent(this, ShellShortcutActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(ShellShortcuts.EXTRA_ID, command.id)
            putExtra(ShellShortcuts.EXTRA_SOURCE, ShellLogSource.TILE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(this, slot, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun showResult(context: android.content.Context, command: SavedCommand, result: ShellResult) {
        val sep = context.getString(R.string.common_separator)
        val ok: Boolean
        val detail: String?
        when {
            result.stopped -> { ok = true; detail = context.getString(R.string.shell_log_stopped) }
            result.backend == null -> { ok = false; detail = context.getString(R.string.shell_banner_no_backend) }
            result.timedOut -> { ok = false; detail = context.getString(R.string.shell_log_timed_out) }
            result.exitCode != 0 -> {
                ok = false
                detail = context.getString(R.string.shell_log_failed) + sep + context.getString(R.string.shell_exit_code, result.exitCode)
            }
            else -> { ok = true; detail = null }
        }
        // A background command only reports that it started; its tile just lights up for a moment, so no message.
        if (ok && detail == null && command.detached) return
        val text = (if (ok) "✓ " else "✗ ") + command.name + (detail?.let { sep + it } ?: "")
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val command = ShellTiles.command(applicationContext, slot)
        tile.label = command?.name ?: getString(R.string.shell_tile_label, slot)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_shell)
        // On while the command runs (or, for a background command, for the short moment after it started); off otherwise.
        val busy = command != null && (command.id in ShellRunTracker.runs.value || command.id in ShellRunTracker.pulses.value)
        tile.state = if (busy) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (command == null) getString(R.string.shell_tile_not_set) else null
        tile.updateTile()
    }
}

class ShellTile1 : ShellTileService(1)
class ShellTile2 : ShellTileService(2)
class ShellTile3 : ShellTileService(3)
class ShellTile4 : ShellTileService(4)
