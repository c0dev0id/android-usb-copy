package de.codevoid.usbcopy.model

data class TaskState(
    val task: TransferTask,
    val status: Status = Status.IDLE,
    val bytesCopied: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFile: String = "",
    val speedBytesPerSec: Long = 0L,
    val log: List<FileEvent> = emptyList(),
) {
    enum class Status { IDLE, RUNNING, DONE, ERROR, CANCELLED }

    val progressFraction: Float
        get() = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else 0f
}
