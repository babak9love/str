package dev.cluvex.strata.feature.shell

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
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
import dev.cluvex.strata.core.shell.SavedCommand
import dev.cluvex.strata.core.shell.ShellExecutor
import dev.cluvex.strata.core.shell.ShellLogSource
import dev.cluvex.strata.core.shell.ShellParams
import dev.cluvex.strata.core.shell.ShellRunTracker
import dev.cluvex.strata.core.shell.ShellShortcuts
import dev.cluvex.strata.core.shell.ShellTiles

/**
 * A Quick Settings tile that runs one saved command; the app itself is never opened.
 *
 * With "Show status message" on (or when the command has to ask something first: confirmation, {package}) the run
 * goes through [ShellShortcutActivity], the same screenless target the launcher shortcuts use, so the same banner
 * shows "running" and then the result. Android only lets a tile start a screen by closing the panel, so the panel
 * closes in that case. With the message off, the command runs right here, the panel stays open and the tile can be
 * seen switching on while it runs and off when it ends.
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
        if (command.showStatus || command.confirm || ShellParams.needsPackage(command.command)) openActivity(command) else runHere(command)
    }

    private fun runHere(command: SavedCommand) {
        // The same command is not started twice while it is still running.
        if (command.id in ShellRunTracker.runs.value) return
        val app = applicationContext
        // The run belongs to the process, not to this service: the service is released when the panel closes.
        runScope.launch { ShellExecutor.runLogged(app, command, ShellLogSource.TILE) }
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
