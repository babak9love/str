package dev.cluvex.strata.feature.shell

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.cluvex.strata.R
import dev.cluvex.strata.core.data.SettingsRepository
import dev.cluvex.strata.core.model.AppearancePrefs
import dev.cluvex.strata.core.shell.SavedCommand
import dev.cluvex.strata.core.shell.ShellExecutor
import dev.cluvex.strata.core.shell.ShellResult
import dev.cluvex.strata.ui.theme.StrataTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface Banner {
    data class Running(val name: String) : Banner
    data class Done(val name: String, val ok: Boolean, val detail: String?) : Banner
    /** A plain notice without a check or cross, e.g. "name · Running" for a command that is still going. */
    data class Notice(val name: String, val detail: String) : Banner
}

/**
 * Shows the status of a shell command run (launcher shortcut or Quick Settings tile).
 *
 *  - "Show status message" off: nothing is shown.
 *  - On and the "Display over other apps" permission granted: always a small banner drawn as an
 *    overlay window (like MacroDroid's customizable popup). The window is only as big as the banner, so touches
 *    outside it reach the app behind; tapping the banner dismisses it.
 *  - On but the permission is missing (or was revoked): a plain Toast, which needs no permission and never blocks
 *    touches.
 *
 * The run belongs to the process, not to a screen, so it goes on when the banner is dismissed or the caller ends.
 */
internal object ShellStatus {
    /** How long the spinner banner of a normal command stays before it turns into the "running" notice. */
    private const val RUNNING_MAX_MS = 4_200L

    /** How long the banner of a background command waits to see whether it ends right away. */
    private const val DETACHED_WAIT_MS = 4_200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun canDrawOverlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Opens this app's "Display over other apps" page in the system settings. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** A one-off message (for example "command not found"): the banner when allowed, else a Toast. */
    fun toast(context: Context, text: String) {
        val app = context.applicationContext
        scope.launch {
            val presenter: StatusPresenter = if (canDrawOverlay(app)) OverlayPresenter(app, loadAppearance(app)) else ToastPresenter(app)
            try {
                presenter.show(Banner.Done(text, ok = false, detail = null))
                delay(2_500)
            } finally {
                presenter.dismiss()
            }
        }
    }

    private suspend fun loadAppearance(app: Context): AppearancePrefs =
        runCatching { withTimeoutOrNull(1_500L) { SettingsRepository(app).settings.first().appearance } }.getOrNull()
            ?: AppearancePrefs()

    /** Runs [command] in the background of the process and shows its status the way [command] asks for. */
    fun start(context: Context, command: SavedCommand, source: String) {
        val app = context.applicationContext
        scope.launch { run(app, command, source) }
    }

    private suspend fun run(app: Context, command: SavedCommand, source: String) {
        if (!command.showStatus) {
            // Status message off: nothing is shown.
            ShellExecutor.runLogged(app, command, source)
            return
        }
        val presenter: StatusPresenter = if (canDrawOverlay(app)) {
            OverlayPresenter(app, loadAppearance(app))
        } else {
            ToastPresenter(app)
        }
        try {
            presenter.show(Banner.Running(command.name))
            val run = scope.async { ShellExecutor.runLogged(app, command, source) }
            var result = if (!command.detached) {
                // A long command (a logcat, say) must not keep the status up until it ends: after a few seconds it turns
                // into "running" and goes away; the command carries on and its result is then only in the history.
                withTimeoutOrNull(RUNNING_MAX_MS) { run.await() }
            } else {
                run.await()
            }
            if (result == null) {
                presenter.show(Banner.Notice(command.name, app.getString(R.string.shell_log_running)))
                delay(1_800)
                return
            }
            var stillRunning = false
            if (command.detached && result.backend != null && !result.timedOut && result.exitCode == 0) {
                // For a background command "started" is all runLogged knows. A command that fails at once is the usual
                // case, so give it a few seconds: if it ends, the status shows how it really ended; if not, it runs.
                val end = ShellExecutor.awaitDetachedEnd(app, command, DETACHED_WAIT_MS)
                if (end == null) stillRunning = true
                else result = result.copy(exitCode = end.exitCode, timedOut = end.timedOut, stopped = end.stopped)
            }
            presenter.show(
                if (stillRunning) Banner.Notice(command.name, app.getString(R.string.shell_log_running))
                else done(app, command.name, result),
            )
            delay(if (result.backend != null && !result.timedOut && result.exitCode == 0) 1_800 else 3_000)
        } finally {
            presenter.dismiss()
        }
    }

    private fun done(app: Context, name: String, result: ShellResult): Banner.Done = when {
        result.stopped -> Banner.Done(name, ok = true, detail = app.getString(R.string.shell_log_stopped))
        result.backend == null -> Banner.Done(name, ok = false, detail = app.getString(R.string.shell_banner_no_backend))
        result.timedOut -> Banner.Done(name, ok = false, detail = app.getString(R.string.shell_log_timed_out))
        result.exitCode != 0 -> Banner.Done(
            name,
            ok = false,
            // Same words as the history: "Failed · exit code 1".
            detail = app.getString(R.string.shell_log_failed) + app.getString(R.string.common_separator) +
                app.getString(R.string.shell_exit_code, result.exitCode),
        )
        else -> Banner.Done(name, ok = true, detail = null)
    }
}

private interface StatusPresenter {
    fun show(banner: Banner)
    fun dismiss()
}

private fun bannerText(banner: Banner): String = when (banner) {
    is Banner.Running -> banner.name
    is Banner.Notice -> "${banner.name} · ${banner.detail}"
    is Banner.Done -> buildString {
        append(if (banner.ok) "✓" else "✗")
        append(' ')
        append(banner.name)
        banner.detail?.let { append(" · "); append(it) }
    }
}

/** Plain Toast: no permission, never blocks touches. A newer message replaces the one still showing. */
private class ToastPresenter(private val app: Context) : StatusPresenter {
    private val handler = Handler(Looper.getMainLooper())
    private var toast: Toast? = null

    override fun show(banner: Banner) {
        handler.post {
            toast?.cancel()
            val text = if (banner is Banner.Running) "${banner.name} · ${app.getString(R.string.shell_log_running)}" else bannerText(banner)
            toast = Toast.makeText(app, text, Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    // The last toast is left to finish on its own.
    override fun dismiss() = Unit
}

/** Banner drawn as an overlay window; needs "Display over other apps". Falls back to a Toast if it cannot be added. */
private class OverlayPresenter(private val app: Context, private val appearance: AppearancePrefs) : StatusPresenter {
    private val handler = Handler(Looper.getMainLooper())
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val state = mutableStateOf<Banner?>(null)
    private val fallback by lazy { ToastPresenter(app) }
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var closed = false
    private var useToast = false

    override fun show(banner: Banner) {
        handler.post {
            if (closed) return@post
            if (useToast) {
                fallback.show(banner)
                return@post
            }
            state.value = banner
            if (view == null && !attach()) {
                useToast = true
                fallback.show(banner)
            }
        }
    }

    override fun dismiss() {
        handler.post { close() }
    }

    private fun attach(): Boolean {
        val density = app.resources.displayMetrics.density
        val statusBarId = app.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId > 0) app.resources.getDimensionPixelSize(statusBarId) else 0
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // Never takes focus, and touches outside the banner go to what is behind it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBar + (8 * density).toInt()
        }
        val newOwner = OverlayOwner()
        val composeView = ComposeView(app).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent {
                val current = state.value
                Box(Modifier.padding(horizontal = 8.dp)) {
                    if (current != null) {
                        StrataTheme(appearance = appearance) { BannerPill(current, onTap = { close() }) }
                    }
                }
            }
        }
        return try {
            wm.addView(composeView, params)
            view = composeView
            owner = newOwner
            true
        } catch (_: Exception) {
            // The permission was taken away in the meantime, or the system refused the window.
            composeView.disposeComposition()
            newOwner.destroy()
            false
        }
    }

    private fun close() {
        closed = true
        view?.let { v ->
            runCatching { wm.removeViewImmediate(v) }
            v.disposeComposition()
        }
        view = null
        owner?.destroy()
        owner = null
    }
}

/** The lifecycle and saved-state owner a ComposeView needs when it lives in a bare window instead of an activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
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
                is Banner.Notice -> Text("${banner.name} · ${banner.detail}", color = text)
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
