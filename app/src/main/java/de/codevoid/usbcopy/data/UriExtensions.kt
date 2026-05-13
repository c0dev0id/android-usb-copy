package de.codevoid.usbcopy.data

import android.net.Uri

fun Uri.extractDeviceId(fallback: String): String =
    lastPathSegment?.substringBefore(':')?.takeIf { it.isNotBlank() } ?: fallback
