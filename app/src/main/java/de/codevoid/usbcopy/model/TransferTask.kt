package de.codevoid.usbcopy.model

import android.net.Uri

data class TransferTask(
    val id: String,
    val sourceUri: Uri,
    val destRootUri: Uri,
    val deviceId: String,
    val overwriteStrategy: OverwriteStrategy,
    val errorStrategy: ErrorStrategy,
)
