package dev.cluvex.strata.feature.explorer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.cluvex.strata.MainActivity
import dev.cluvex.strata.R
import dev.cluvex.strata.core.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps Strata's process alive while a file task (copy, move, extract, compress, ...) runs, and owns the scope the
 * tasks run in.
 *
 * Without a foreground service the process of an app that is no longer on screen is the first one Android kills when
 * memory runs low, and a task that was running in it just stops half way. [begin] / [end] bracket one task; while at
 * least one is running a [FileTaskService] shows an ongoing notification with its progress. The tasks themselves run in
 * [scope], not in a ViewModel's: a ViewModel is cleared when the screen is closed (for instance by swiping the app out
 * of the recents list), which would cancel a task that the service is keeping alive. Everything here runs on the main
 * thread (the explorer's tasks start, report and finish there).
 */
object FileTaskKeeper {

    /** The scope file tasks run in. It outlives the screen; the service keeps the process around for as long as a task runs. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _task = MutableStateFlow<TaskState?>(null)

    /** The progress of the running task, for whichever explorer screen is open (also one opened after the app was closed). */
    val task: StateFlow<TaskState?> = _task.asStateFlow()

    fun setTask(state: TaskState?) {
        _task.value = state
    }

    /** Emits the owner id (see [newOwner]) of a screen whose task has just finished, so another screen can refresh its listing. */
    val finished = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    private var nextOwner = 0

    fun newOwner(): Int = ++nextOwner

    /** Tasks shorter than this never start the service, so quick operations do not flash a notification. */
    private const val START_DELAY_MS = 500L
    private const val UPDATE_INTERVAL_MS = 700L
    private const val CHANNEL = "file_tasks"

    private class Job(val title: String, var fraction: Float, var indeterminate: Boolean)

    private val jobs = LinkedHashMap<Int, Job>()
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var nextId = 0
    private var serviceUp = false
    private var lastNotified = 0L

    private val starter = Runnable { startService() }

    /** A task has started; returns the id to hand to [update] and [end]. */
    fun begin(context: Context, title: String): Int {
        app = context.applicationContext
        val id = ++nextId
        jobs[id] = Job(title, 0f, true)
        if (serviceUp) {
            refresh(force = true)
        } else {
            handler.removeCallbacks(starter)
            handler.postDelayed(starter, START_DELAY_MS)
        }
        return id
    }

    fun update(id: Int, fraction: Float, indeterminate: Boolean) {
        val job = jobs[id] ?: return
        job.fraction = fraction
        job.indeterminate = indeterminate
        refresh(force = false)
    }

    /** The task is over, whatever the outcome. The service stops once no task is left. */
    fun end(id: Int) {
        jobs.remove(id) ?: return
        if (jobs.isEmpty()) {
            handler.removeCallbacks(starter)
            val context = app
            if (serviceUp && context != null) context.stopService(Intent(context, FileTaskService::class.java))
            serviceUp = false
        } else {
            refresh(force = true)
        }
    }

    internal fun serviceStopped() {
        serviceUp = false
    }

    private fun startService() {
        val context = app ?: return
        if (jobs.isEmpty() || serviceUp) return
        // Android may refuse (app already in the background, or a vendor restriction): the task then simply runs unprotected.
        serviceUp = try {
            ContextCompat.startForegroundService(context, Intent(context, FileTaskService::class.java))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun refresh(force: Boolean) {
        val context = app ?: return
        if (!serviceUp) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotified < UPDATE_INTERVAL_MS) return
        lastNotified = now
        context.getSystemService(NotificationManager::class.java).notify(FileTaskService.NOTIFICATION_ID, notification(context))
    }

    internal fun notification(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr(R.string.file_task_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val running = jobs.values.toList()
        val one = running.singleOrNull()
        val title = when {
            one != null -> one.title
            running.isEmpty() -> tr(R.string.file_task_channel)
            else -> tr(R.string.file_task_notif_many, running.size)
        }
        val percent = ((one?.fraction ?: 0f) * 100).toInt().coerceIn(0, 100)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setProgress(100, percent, one?.indeterminate ?: true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

/** Foreground service that only exists to keep the process alive while [FileTaskKeeper] has a task running. */
class FileTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    // Also in onCreate: a very short task may stop the service before onStartCommand ever runs, and a service
    // started with startForegroundService() that never calls startForeground() crashes the app.
    override fun onCreate() {
        super.onCreate()
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, FileTaskKeeper.notification(this), type)
    }

    // Android 15 limits a data-sync service to about six hours in total; it must stop itself when told so.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        FileTaskKeeper.serviceStopped()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 7312
    }
}
