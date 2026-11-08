package dev.cluvex.strata.feature.shell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.cluvex.strata.MainActivity
import dev.cluvex.strata.R
import dev.cluvex.strata.core.shell.ShellTiles

/**
 * The status of a shell command run from a shortcut or tile, shown as a heads-up notification at the top of the
 * screen. Unlike a window of its own it never takes the touches of the app underneath. Each command has one
 * notification, so its "running" card is replaced by the result. Without the notification permission it falls back
 * to a Toast.
 */
internal object ShellStatusNotifier {
    private const val CHANNEL = "shell_status"
    private const val TIMEOUT_MS = 6_000L
    private const val OK_COLOR = 0xFF1B7F2E.toInt()
    private const val FAIL_COLOR = 0xFFB3261E.toInt()

    enum class Kind { RUNNING, OK, FAIL }

    fun show(context: Context, commandId: String, title: String, detail: String?, kind: Kind) {
        val app = context.applicationContext
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) {
            toast(app, listOfNotNull(mark(kind), title, detail).joinToString(" · ").trim())
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, app.getString(R.string.shell_notif_channel), NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.enableVibration(false)
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            app,
            commandId.hashCode(),
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ShellTiles.EXTRA_OPEN_SHELL, ShellTiles.OPEN_LOG),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_shell)
            .setContentTitle(listOf(mark(kind), title).filter { it.isNotEmpty() }.joinToString(" "))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(open)
            .addAction(0, app.getString(R.string.shell_notif_open_log), open)
        if (!detail.isNullOrEmpty()) builder.setContentText(detail)
        when (kind) {
            Kind.RUNNING -> builder.setProgress(0, 0, true)
            Kind.OK -> builder.setColor(OK_COLOR)
            Kind.FAIL -> builder.setColor(FAIL_COLOR)
        }
        runCatching { manager.notify(commandId.hashCode(), builder.build()) }
    }

    private fun mark(kind: Kind) = when (kind) {
        Kind.RUNNING -> ""
        Kind.OK -> "✓"
        Kind.FAIL -> "✗"
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
