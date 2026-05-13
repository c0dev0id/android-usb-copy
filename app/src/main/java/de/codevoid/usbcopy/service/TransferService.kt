package de.codevoid.usbcopy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import de.codevoid.usbcopy.R
import de.codevoid.usbcopy.data.extractDeviceId
import de.codevoid.usbcopy.model.ErrorStrategy
import de.codevoid.usbcopy.model.OverwriteStrategy
import de.codevoid.usbcopy.model.TaskState
import de.codevoid.usbcopy.model.TransferTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "transfer"
private const val NOTIFICATION_ID = 1
private const val NOTIFICATION_THROTTLE_NS = 1_000_000_000L
private const val LOG_MAX_ENTRIES = 10_000

class TransferService : Service() {

    inner class LocalBinder : Binder() {
        fun service() = this@TransferService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val engine by lazy { TransferEngine(this) }

    private val _tasks = MutableStateFlow<List<TaskState>>(emptyList())
    val tasks: StateFlow<List<TaskState>> = _tasks.asStateFlow()

    private var transferJob: Job? = null
    private var lastNotificationTs = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting…"))
        if (intent != null && transferJob == null) {
            val (tasks, sequential) = buildTasks(intent)
            if (tasks.isNotEmpty()) start(tasks, sequential)
        }
        return START_NOT_STICKY
    }

    fun cancel() {
        transferJob?.cancel()
        _tasks.update { list ->
            list.map { state ->
                if (state.status == TaskState.Status.RUNNING)
                    state.copy(status = TaskState.Status.CANCELLED)
                else state
            }
        }
    }

    private fun start(tasks: List<TransferTask>, sequential: Boolean) {
        _tasks.value = tasks.map { TaskState(it) }

        transferJob = scope.launch {
            if (sequential) {
                for (task in tasks) {
                    precomputeTotal(task)
                    runTask(task)
                }
            } else {
                tasks.map { task ->
                    launch {
                        precomputeTotal(task)
                        runTask(task)
                    }
                }.forEach { it.join() }
            }
            stopSelf()
        }
    }

    private fun precomputeTotal(task: TransferTask) {
        val doc = DocumentFile.fromTreeUri(applicationContext, task.sourceUri) ?: return
        val total = doc.countBytes()
        _tasks.update { list ->
            list.map { if (it.task.id == task.id) it.copy(totalBytes = total) else it }
        }
    }

    private suspend fun runTask(task: TransferTask) {
        setStatus(task.id, TaskState.Status.RUNNING)
        try {
            engine.execute(task) { progress -> applyProgress(task.id, progress) }
            setStatus(task.id, TaskState.Status.DONE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus(task.id, TaskState.Status.ERROR)
        }
    }

    private fun applyProgress(taskId: String, progress: TransferEngine.Progress) {
        _tasks.update { list ->
            list.map { state ->
                if (state.task.id != taskId) return@map state
                val log = if (progress.event != null) {
                    val appended = state.log + progress.event
                    if (appended.size > LOG_MAX_ENTRIES) appended.takeLast(LOG_MAX_ENTRIES) else appended
                } else state.log
                state.copy(
                    bytesCopied = state.bytesCopied + progress.bytesDelta,
                    currentFile = progress.currentFile,
                    speedBytesPerSec = progress.speedBytesPerSec,
                    log = log,
                )
            }
        }
        throttledNotificationUpdate()
    }

    private fun setStatus(taskId: String, status: TaskState.Status) {
        _tasks.update { list ->
            list.map { if (it.task.id == taskId) it.copy(status = status) else it }
        }
    }

    private fun throttledNotificationUpdate() {
        val now = System.nanoTime()
        if (now - lastNotificationTs < NOTIFICATION_THROTTLE_NS) return
        lastNotificationTs = now

        val states = _tasks.value
        val done = states.count { it.status == TaskState.Status.DONE }
        val total = states.size
        val avgPct = if (states.isEmpty()) 0
        else (states.sumOf { it.progressFraction.toDouble() } / states.size * 100).toInt()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("$done/$total tasks · $avgPct%"))
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notification_transfer_running))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_SOURCES = "sources"
        const val EXTRA_DEST = "dest"
        const val EXTRA_OVERWRITE = "overwrite"
        const val EXTRA_ERROR = "error"
        const val EXTRA_SEQUENTIAL = "sequential"

        private fun buildTasks(intent: Intent): Pair<List<TransferTask>, Boolean> {
            val sourceUris = intent.getStringArrayExtra(EXTRA_SOURCES)
                ?.map { Uri.parse(it) } ?: emptyList()
            val destUri = Uri.parse(intent.getStringExtra(EXTRA_DEST) ?: return Pair(emptyList(), false))
            val overwrite = OverwriteStrategy.valueOf(
                intent.getStringExtra(EXTRA_OVERWRITE) ?: OverwriteStrategy.SKIP.name
            )
            val error = ErrorStrategy.valueOf(
                intent.getStringExtra(EXTRA_ERROR) ?: ErrorStrategy.SKIP_AND_CONTINUE.name
            )
            val sequential = intent.getBooleanExtra(EXTRA_SEQUENTIAL, false)

            val tasks = sourceUris.mapIndexed { i, uri ->
                val deviceId = uri.extractDeviceId("device_$i")
                TransferTask(
                    id = deviceId,
                    sourceUri = uri,
                    destRootUri = destUri,
                    deviceId = deviceId,
                    overwriteStrategy = overwrite,
                    errorStrategy = error,
                )
            }
            return Pair(tasks, sequential)
        }
    }
}
