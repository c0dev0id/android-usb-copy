package de.codevoid.usbcopy.service

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.codevoid.usbcopy.model.ErrorStrategy
import de.codevoid.usbcopy.model.FileEvent
import de.codevoid.usbcopy.model.OverwriteStrategy
import de.codevoid.usbcopy.model.TransferTask
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

private const val BUFFER_SIZE = 65_536

class TransferEngine(private val context: Context) {
    private val resolver get() = context.contentResolver

    data class Progress(
        val bytesCopied: Long,
        val totalBytes: Long,
        val event: FileEvent,
    )

    fun execute(task: TransferTask, totalBytes: Long): Flow<Progress> = flow {
        val sourceDir = DocumentFile.fromTreeUri(context, task.sourceUri)
            ?: error("Cannot open source URI")
        val destRoot = DocumentFile.fromTreeUri(context, task.destRootUri)
            ?: error("Cannot open destination URI")

        val destDir = destRoot.findOrCreate(task.deviceId)
        var bytesCopied = 0L

        copyDir(
            sourceDir = sourceDir,
            destDir = destDir,
            task = task,
            totalBytes = totalBytes,
            bytesCopied = { bytesCopied },
            onProgress = { delta, event ->
                bytesCopied += delta
                emit(Progress(bytesCopied, totalBytes, event))
            },
        )
    }

    private suspend fun copyDir(
        sourceDir: DocumentFile,
        destDir: DocumentFile,
        task: TransferTask,
        totalBytes: Long,
        bytesCopied: () -> Long,
        onProgress: suspend (delta: Long, event: FileEvent) -> Unit,
    ) {
        for (source in sourceDir.listFiles()) {
            if (!currentCoroutineContext().isActive) return

            if (source.isDirectory) {
                val subDest = destDir.findOrCreate(source.name ?: continue)
                copyDir(source, subDest, task, totalBytes, bytesCopied, onProgress)
            } else {
                copyFile(source, destDir, task, onProgress)
            }
        }
    }

    private suspend fun copyFile(
        source: DocumentFile,
        destDir: DocumentFile,
        task: TransferTask,
        onProgress: suspend (delta: Long, event: FileEvent) -> Unit,
    ) {
        val name = source.name ?: return
        val sourceSize = source.length()
        val existing = destDir.findFile(name)

        val skip = when (task.overwriteStrategy) {
            OverwriteStrategy.SKIP -> existing != null
            OverwriteStrategy.OVERWRITE_SMALLER -> existing != null && existing.length() >= sourceSize
            OverwriteStrategy.OVERWRITE -> false
        }

        if (skip) {
            onProgress(sourceSize, FileEvent.Skipped(name, sourceSize))
            return
        }

        val destFile = existing ?: destDir.createFile("application/octet-stream", name)
        if (destFile == null) {
            onProgress(0, FileEvent.Error(name, "Could not create destination file"))
            if (task.errorStrategy == ErrorStrategy.STOP) throw RuntimeException("Error on $name")
            return
        }

        try {
            resolver.openInputStream(source.uri)!!.use { input ->
                resolver.openOutputStream(destFile.uri, "wt")!!.use { output ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var written = 0L
                    var lastSpeedTs = System.nanoTime()
                    var bytesSinceLastTs = 0L

                    while (true) {
                        if (!currentCoroutineContext().isActive) return
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        written += read
                        bytesSinceLastTs += read

                        val now = System.nanoTime()
                        val elapsedNs = now - lastSpeedTs
                        if (elapsedNs >= 500_000_000L) {
                            val speed = bytesSinceLastTs * 1_000_000_000L / elapsedNs
                            onProgress(
                                read.toLong(),
                                FileEvent.InProgress(name, written, sourceSize, speed),
                            )
                            lastSpeedTs = now
                            bytesSinceLastTs = 0L
                        } else {
                            onProgress(read.toLong(), FileEvent.InProgress(name, written, sourceSize, 0L))
                        }
                    }
                }
            }
            onProgress(0, FileEvent.Copied(name, sourceSize))
        } catch (e: Exception) {
            onProgress(0, FileEvent.Error(name, e.message ?: "Unknown error"))
            if (task.errorStrategy == ErrorStrategy.STOP) throw e
        }
    }

    private fun DocumentFile.findOrCreate(name: String): DocumentFile =
        findFile(name) ?: createDirectory(name) ?: error("Cannot create directory $name")
}

fun DocumentFile.countBytes(): Long {
    if (isFile) return length()
    return listFiles().sumOf { it.countBytes() }
}
