package de.codevoid.usbcopy.service

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import de.codevoid.usbcopy.model.ErrorStrategy
import de.codevoid.usbcopy.model.FileEvent
import de.codevoid.usbcopy.model.OverwriteStrategy
import de.codevoid.usbcopy.model.TransferTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.IOException

private const val BUFFER_SIZE = 65_536
private const val SAMPLE_INTERVAL_NS = 500_000_000L

class TransferEngine(private val context: Context) {

    data class Progress(
        val bytesDelta: Long,
        val currentFile: String,
        val speedBytesPerSec: Long,
        val event: FileEvent? = null,
    )

    suspend fun execute(task: TransferTask, onProgress: suspend (Progress) -> Unit) {
        val sourceDir = DocumentFile.fromTreeUri(context, task.sourceUri)
            ?: throw IOException("Cannot open source URI")
        val destRoot = DocumentFile.fromTreeUri(context, task.destRootUri)
            ?: throw IOException("Cannot open destination URI")

        val destDir = destRoot.findOrCreate(task.deviceId)
        copyDir(sourceDir, destDir, task, onProgress)
    }

    private suspend fun copyDir(
        sourceDir: DocumentFile,
        destDir: DocumentFile,
        task: TransferTask,
        onProgress: suspend (Progress) -> Unit,
    ) {
        for (source in sourceDir.listFiles()) {
            if (!currentCoroutineContext().isActive) return

            if (source.isDirectory) {
                val subDest = destDir.findOrCreate(source.name ?: continue)
                copyDir(source, subDest, task, onProgress)
            } else {
                copyFile(source, destDir, task, onProgress)
            }
        }
    }

    private suspend fun copyFile(
        source: DocumentFile,
        destDir: DocumentFile,
        task: TransferTask,
        onProgress: suspend (Progress) -> Unit,
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
            onProgress(Progress(sourceSize, name, 0L, FileEvent.Skipped(name, sourceSize)))
            return
        }

        val destFile = existing ?: destDir.createFile("application/octet-stream", name)
        if (destFile == null) {
            onProgress(Progress(0, name, 0L, FileEvent.Error(name, "Cannot create destination")))
            if (task.errorStrategy == ErrorStrategy.STOP) throw IOException("Cannot create $name")
            return
        }

        val resolver = context.contentResolver
        try {
            (resolver.openInputStream(source.uri) ?: throw IOException("Cannot open $name")).use { src ->
                (resolver.openOutputStream(destFile.uri, "wt") ?: throw IOException("Cannot write $name")).use { dst ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var bytesSinceLastEmit = 0L
                    var lastEmitTs = System.nanoTime()

                    while (true) {
                        if (!currentCoroutineContext().isActive) return
                        val read = src.read(buf)
                        if (read == -1) break
                        dst.write(buf, 0, read)
                        bytesSinceLastEmit += read

                        val now = System.nanoTime()
                        val elapsedNs = now - lastEmitTs
                        if (elapsedNs >= SAMPLE_INTERVAL_NS) {
                            val speed = bytesSinceLastEmit * 1_000_000_000L / elapsedNs
                            onProgress(Progress(bytesSinceLastEmit, name, speed))
                            bytesSinceLastEmit = 0L
                            lastEmitTs = now
                        }
                    }

                    onProgress(Progress(bytesSinceLastEmit, name, 0L, FileEvent.Copied(name, sourceSize)))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress(Progress(0, name, 0L, FileEvent.Error(name, e.message ?: "Unknown error")))
            if (task.errorStrategy == ErrorStrategy.STOP) throw e
        }
    }

    private fun DocumentFile.findOrCreate(name: String): DocumentFile =
        findFile(name) ?: createDirectory(name) ?: throw IOException("Cannot create directory $name")
}

fun DocumentFile.countBytes(): Long {
    if (isFile) return length()
    return listFiles().sumOf { it.countBytes() }
}
