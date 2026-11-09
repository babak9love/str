package dev.cluvex.strata.feature.shell

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.cluvex.strata.R
import dev.cluvex.strata.core.data.SettingsRepository
import dev.cluvex.strata.core.data.StrataSettings
import dev.cluvex.strata.ui.theme.StrataTheme
import kotlinx.coroutines.flow.catch
import androidx.compose.runtime.remember
import dev.cluvex.strata.core.shell.ShellCommandStore
import dev.cluvex.strata.core.shell.ShellLogSource
import dev.cluvex.strata.core.shell.ShellParams
import dev.cluvex.strata.core.shell.ShellShortcuts
import dev.cluvex.strata.core.shell.SavedCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Target of the launcher shortcuts and of the tile when it has to ask something first. It has no screen of its own:
 * it looks the command up by id (so an intent can never carry its own command text), asks what has to be asked
 * (confirmation, {package}), hands the run to [ShellStatus] (which shows the status as an overlay banner or a plain
 * Toast and records the run in the log) and finishes at once, so nothing of this window is left to block touches.
 */
class ShellShortcutActivity : ComponentActivity() {
    private val confirmFor = MutableStateFlow<SavedCommand?>(null)
    private val pickFor = MutableStateFlow<SavedCommand?>(null)
    private val pickAnswer = CompletableDeferred<List<String>?>()
    private val answer = CompletableDeferred<Boolean>()

    /** Idle mode: never takes the keyboard focus, and touches go to what is behind. */
    private fun bannerOnly() {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }

    /** While a question dialog is shown the window takes focus and touches like any dialog host. */
    private fun dialogMode() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The window is as small as it can be while no question is on screen, and it never takes touches or focus then.
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bannerOnly()
        setContent {
            // The dialogs use the app's own theme (its light/dark choice and colors), not just the system's dark mode.
            val settingsFlow = remember { SettingsRepository(applicationContext).settings.catch { emit(StrataSettings()) } }
            val appearance = settingsFlow.collectAsState(initial = StrataSettings()).value.appearance
            val asking by confirmFor.collectAsState()
            val picking by pickFor.collectAsState()
            picking?.let { c ->
                StrataTheme(appearance = appearance) {
                    ShellAppPickerPlainDialog(
                        title = stringResource(R.string.shell_param_pick_title, c.name),
                        lastPackage = ShellParams.lastPackage(applicationContext, c.id),
                        onPick = { pickAnswer.complete(it) },
                        onDismiss = { pickAnswer.complete(null) },
                    )
                }
            }
            asking?.let { c ->
                StrataTheme(appearance = appearance) {
                    AlertDialog(
                        onDismissRequest = { answer.complete(false) },
                        title = { Text(stringResource(R.string.shell_confirm_title, c.name)) },
                        text = { Text(c.command, maxLines = 8) },
                        confirmButton = { TextButton(onClick = { answer.complete(true) }) { Text(stringResource(R.string.shell_run)) } },
                        dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text(stringResource(R.string.common_cancel)) } },
                    )
                }
            }
        }

        val id = intent.getStringExtra(ShellShortcuts.EXTRA_ID)
        if (id == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) {
                ShellCommandStore(applicationContext).load().firstOrNull { it.id == id }
            }
            if (stored == null) {
                ShellStatus.toast(applicationContext, getString(R.string.shell_not_found))
                finish()
                return@launch
            }
            var command: SavedCommand = stored
            if (ShellParams.needsPackage(command.command)) {
                // A command with {package} asks which app first; the shortcut cannot carry that choice itself.
                dialogMode()
                pickFor.value = command
                val pkgs = pickAnswer.await()
                pickFor.value = null
                if (pkgs == null) {
                    finish()
                    return@launch
                }
                ShellParams.rememberPackage(applicationContext, command.id, pkgs.last())
                command = ShellParams.resolveAll(command, pkgs)
                bannerOnly()
            }
            if (command.confirm) {
                dialogMode()
                confirmFor.value = command
                val ok = answer.await()
                confirmFor.value = null
                if (!ok) {
                    finish()
                    return@launch
                }
                bannerOnly()
            }
            // Only the two known values are accepted; anything else counts as a plain shortcut.
            val source = if (intent.getStringExtra(ShellShortcuts.EXTRA_SOURCE) == ShellLogSource.TILE) ShellLogSource.TILE else ShellLogSource.SHORTCUT
            // The run and its status belong to the process, so this window can close right away.
            ShellStatus.start(applicationContext, command, source)
            finish()
        }
    }
}
