package io.github.radiqalo.picaris.ui

import java.util.Locale

private const val BYTES_PER_KIB = 1024L
private const val BYTES_PER_MIB = BYTES_PER_KIB * BYTES_PER_KIB

internal fun formatByteSize(bytes: Long): String =
    when {
        bytes >= BYTES_PER_MIB ->
            String.format(Locale.ROOT, "%.1f MB", bytes / BYTES_PER_MIB.toDouble())
        bytes >= BYTES_PER_KIB ->
            String.format(Locale.ROOT, "%.1f KB", bytes / BYTES_PER_KIB.toDouble())
        else -> "$bytes B"
    }
