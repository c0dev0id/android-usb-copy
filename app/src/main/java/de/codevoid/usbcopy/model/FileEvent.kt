package de.codevoid.usbcopy.model

sealed class FileEvent {
    data class Copied(val name: String, val sizeBytes: Long) : FileEvent()
    data class Skipped(val name: String, val sizeBytes: Long) : FileEvent()
    data class InProgress(
        val name: String,
        val bytesWritten: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
    ) : FileEvent()
    data class Error(val name: String, val message: String) : FileEvent()
}
