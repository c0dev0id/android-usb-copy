package de.codevoid.usbcopy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.codevoid.usbcopy.R
import de.codevoid.usbcopy.model.ErrorStrategy
import de.codevoid.usbcopy.model.OverwriteStrategy
import de.codevoid.usbcopy.model.TaskState
import de.codevoid.usbcopy.model.TransferTask
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting…"))
        if (intent != null) {
            val (tasks, sequential) = buildTasks(intent)
            if (tasks.isNotEmpty()) start(tasks, sequential)
        }
        return START_NOT_STICKY
    }

    fun start(
        tasks: List<TransferTask>,
        sequential: Boolean,
    ) {
        _tasks.value = tasks.map { TaskState(it) }

        transferJob = scope.launch {
            precomputeTotals(tasks)

            if (sequential) {
                for (task in tasks) runTask(task)
            } else {
                val jobs = tasks.map { task -> launch { runTask(task) } }
                jobs.forEach { it.join() }
            }

            stopSelf()
        }
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

    private suspend fun precomputeTotals(tasks: List<TransferTask>) {
        for (task in tasks) {
            val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                applicationContext, task.sourceUri
            ) ?: continue
            val total = doc.countBytes()
            _tasks.update { list ->
                list.map { if (it.task.id == task.id) it.copy(totalBytes = total) else it }
            }
        }
    }

    private suspend fun runTask(task: TransferTask) {
        setStatus(task.id, TaskState.Status.RUNNING)

        try {
            val total = _tasks.value.first { it.task.id == task.id }.totalBytes

            engine.execute(task, total).collect { progress ->
                _tasks.update { list ->
                    list.map { state ->
                        if (state.task.id != task.id) return@map state
                        val newLog = when (val e = progress.event) {
                            is de.codevoid.usbcopy.model.FileEvent.InProgress ->
                                state.log.dropLastInProgress() + e
                            else -> state.log + progress.event
                        }
                        state.copy(
                            bytesCopied = progress.bytesCopied,
                            currentFile = (progress.event as? de.codevoid.usbcopy.model.FileEvent.InProgress)
                                ?.name ?: state.currentFile,
                            speedBytesPerSec = (progress.event as? de.codevoid.usbcopy.model.FileEvent.InProgress)
                                ?.speedBytesPerSec?.takeIf { it > 0 } ?: state.speedBytesPerSec,
                            log = newLog,
                        )
                    }
                }
                updateNotification()
            }
            setStatus(task.id, TaskState.Status.DONE)
        } catch (e: Exception) {
            setStatus(task.id, TaskState.Status.ERROR)
        }
    }

    private fun setStatus(taskId: String, status: TaskState.Status) {
        _tasks.update { list ->
            list.map { if (it.task.id == taskId) it.copy(status = status) else it }
        }
    }

    private fun updateNotification() {
        val states = _tasks.value
        val done = states.count { it.status == TaskState.Status.DONE }
        val total = states.size
        val avgProgress = if (states.isEmpty()) 0
        else (states.sumOf { it.progressFraction.toDouble() } / states.size * 100).toInt()
        val text = "$done/$total tasks · $avgProgress%"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
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

        fun buildTasks(intent: Intent): Pair<List<TransferTask>, Boolean> {
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
                val deviceId = uri.lastPathSegment?.substringBefore(':')
                    ?.takeIf { it.isNotBlank() } ?: "device_$i"
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

private fun List<de.codevoid.usbcopy.model.FileEvent>.dropLastInProgress(): List<de.codevoid.usbcopy.model.FileEvent> {
    if (lastOrNull() is de.codevoid.usbcopy.model.FileEvent.InProgress) return dropLast(1)
    return this
}
