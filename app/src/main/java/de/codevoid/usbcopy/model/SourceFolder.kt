package de.codevoid.usbcopy.model

import android.net.Uri

data class SourceFolder(
    val uri: Uri,
    val displayName: String,
    val deviceId: String,
)
