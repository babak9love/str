package dev.cluvex.strata.feature.shell

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.cluvex.strata.R
import dev.cluvex.strata.core.data.SettingsRepository
import dev.cluvex.strata.core.data.StrataSettings
import dev.cluvex.strata.ui.theme.StrataTheme
import kotlinx.coroutines.flow.catch
import androidx.compose.runtime.remember
import dev.cluvex.strata.core.shell.ShellCommandStore
import dev.cluvex.strata.core.shell.ShellExecutor
import dev.cluvex.strata.core.shell.ShellLogSource
import dev.cluvex.strata.core.shell.ShellParams
import dev.cluvex.strata.core.shell.ShellResult
import dev.cluvex.strata.core.shell.ShellShortcuts
import dev.cluvex.strata.core.shell.SavedCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface Banner {
    data class Running(val name: String) : Banner
    data class Done(val name: String, val ok: Boolean, val detail: String?) : Banner
}

/**
 * Target of the launcher shortcuts. It has no screen of its own: it looks the command up by id (so an intent
 * can never carry its own command text), runs it, shows a small banner at the top of the screen, records the
 * run in the log and finishes. The window lets touches through to whatever is behind it.
 */
class ShellShortcutActivity : ComponentActivity() {
    private val banner = MutableStateFlow<Banner?>(null)
    private val confirmFor = MutableStateFlow<SavedCommand?>(null)
    private val pickFor = MutableStateFlow<SavedCommand?>(null)
    private val pickAnswer = CompletableDeferred<List<String>?>()
    private val answer = CompletableDeferred<Boolean>()

    /** Banner mode: never takes the keyboard focus, and touches outside the banner go to what is behind. */
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
        // The window is only as big as the banner itself, at the top, and takes touches only inside that area (a tap
        // on the banner dismisses it). Everything outside it is not covered by the window at all, so the app behind
        // keeps working. A full-screen window that "lets touches through" does not do that: from Android 12 the
        // system blocks touches that pass through a window of another app, and this one is another app's window
        // whenever the banner shows over something else.
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bannerOnly()
        setContent {
            // The dialogs use the app's own theme (its light/dark choice and colors), not just the system's dark mode.
            val settingsFlow = remember { SettingsRepository(applicationContext).settings.catch { emit(StrataSettings()) } }
            val appearance = settingsFlow.collectAsState(initial = StrataSettings()).value.appearance
            val current by banner.collectAsState()
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
            Box(
                Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp, end = 8.dp, top = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                current?.let { StrataTheme(appearance = appearance) { BannerPill(it, onTap = { finish() }) } }
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
                banner.value = Banner.Done(getString(R.string.shell_not_found), ok = false, detail = null)
                delay(2_500)
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
            if (command.showStatus) banner.value = Banner.Running(command.name)
            // Only the two known values are accepted; anything else counts as a plain shortcut.
            val source = if (intent.getStringExtra(ShellShortcuts.EXTRA_SOURCE) == ShellLogSource.TILE) ShellLogSource.TILE else ShellLogSource.SHORTCUT
            // The run belongs to the process, not to this screen, so it goes on if the banner is dismissed early.
            val run = runScope.async { ShellExecutor.runLogged(applicationContext, command, source) }
            var result = if (command.showStatus && !command.detached) {
                // A long command (a logcat, say) must not keep the banner up until it ends: after a few seconds the banner
                // goes away and the command carries on; its result is then only in the history.
                withTimeoutOrNull(BANNER_RUNNING_MAX_MS) { run.await() }
            } else {
                run.await()
            }
            if (result == null) {
                banner.value = null
                finish()
                return@launch
            }
            if (!command.showStatus) {
                finish()
                return@launch
            }
            var stillRunning = false
            if (command.detached && result.backend != null && !result.timedOut && result.exitCode == 0) {
                // For a background command "started" is all runLogged knows. A command that fails at once is the usual
                // case, so give it a few seconds: if it ends, the banner shows how it really ended; if not, it is running.
                val end = ShellExecutor.awaitDetachedEnd(applicationContext, command, DETACHED_BANNER_WAIT_MS)
                if (end == null) stillRunning = true
                else result = result.copy(exitCode = end.exitCode, timedOut = end.timedOut, stopped = end.stopped)
            }
            banner.value = if (stillRunning) Banner.Done(command.name, ok = true, detail = getString(R.string.shell_log_running)) else done(command.name, result)
            delay(if (result.backend != null && !result.timedOut && result.exitCode == 0) 1_800 else 3_000)
            finish()
        }
    }

    private companion object {
        /** How long the banner of a background command waits to see whether it ends right away; with the 1.8 s "running" notice after it, the banner stays about 6 s. */
        const val DETACHED_BANNER_WAIT_MS = 4_200L

        /** How long the "running" banner of a normal command stays before it goes away on its own. */
        const val BANNER_RUNNING_MAX_MS = 6_000L

        /** Runs the commands; it is not tied to the screen, which closes while a long command still runs. */
        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    private fun done(name: String, result: ShellResult): Banner.Done = when {
        result.stopped -> Banner.Done(name, ok = true, detail = getString(R.string.shell_log_stopped))
        result.backend == null -> Banner.Done(name, ok = false, detail = getString(R.string.shell_banner_no_backend))
        result.timedOut -> Banner.Done(name, ok = false, detail = getString(R.string.shell_log_timed_out))
        result.exitCode != 0 -> Banner.Done(
            name,
            ok = false,
            // Same words as the history: "Failed · exit code 1".
            detail = getString(R.string.shell_log_failed) + getString(R.string.common_separator) + getString(R.string.shell_exit_code, result.exitCode),
        )
        else -> Banner.Done(name, ok = true, detail = null)
    }
}

@Composable
private fun BannerPill(banner: Banner, onTap: () -> Unit) {
    // The pill has the same colors as the app's dialogs (their container color and the text color made for it), so it
    // is dark in a dark theme and light in a light one. The check and cross colors are picked so they show on either.
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val text = MaterialTheme.colorScheme.onSurface
    val onDark = surface.luminance() < 0.5f
    val okColor = if (onDark) Color(0xFF7BE08B) else Color(0xFF1B7F2E)
    val failColor = if (onDark) Color(0xFFFF7A70) else Color(0xFFB3261E)
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = onTap),
        shape = RoundedCornerShape(28.dp),
        color = surface.copy(alpha = 0.97f),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (banner) {
                is Banner.Running -> {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = text)
                    Text(banner.name, color = text)
                }
                is Banner.Done -> Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = if (banner.ok) okColor else failColor, fontWeight = FontWeight.Bold)) {
                            append(if (banner.ok) "✓" else "✗")
                        }
                        append(" ")
                        append(banner.name)
                        banner.detail?.let { append(" · "); append(it) }
                    },
                    color = text,
                )
            }
        }
    }
}
