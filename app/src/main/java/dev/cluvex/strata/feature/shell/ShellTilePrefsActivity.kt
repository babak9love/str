package dev.cluvex.strata.feature.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.cluvex.strata.MainActivity
import dev.cluvex.strata.core.shell.ShellRunTracker
import dev.cluvex.strata.core.shell.ShellTiles
import kotlinx.coroutines.flow.MutableStateFlow

/** A one-shot request for the shell screen to open its log when it appears (set when the app is opened from a tile). */
object ShellOpenRequest {
    val showLog = MutableStateFlow(false)
}

/**
 * Where a long press on one of the shell tiles goes. It has no screen: it opens the app, on the log while any command
 * is running (so the live output is there), and on the shell commands otherwise, then finishes.
 */
class ShellTilePrefsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A background command that has just started is not in the run list yet, but it is in the short "started" list.
        val running = ShellRunTracker.runs.value.isNotEmpty() || ShellRunTracker.pulses.value.isNotEmpty()
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ShellTiles.EXTRA_OPEN_SHELL, if (running) ShellTiles.OPEN_LOG else ShellTiles.OPEN_COMMANDS)
        startActivity(open)
        finish()
    }
}
