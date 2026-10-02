package io.github.radiqalo.picaris.download

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.IOException

private const val FOLDER_URI_PARTS = 3

internal enum class DownloadFileState { AVAILABLE, MISSING, ACCESS_REQUIRED }

internal fun downloadFileState(
    context: Context,
    value: String,
): DownloadFileState =
    try {
        when {
            value.isBlank() -> DownloadFileState.MISSING
            value.startsWith("saf-folder|") -> documentFolderState(context, value)
            value.startsWith("media-folder|") -> mediaFolderState(context, value)
            else -> {
                val exists =
                    context.contentResolver
                        .openAssetFileDescriptor(
                            Uri.parse(value),
                            "r",
                        )?.use { true } ==
                        true
                if (exists) DownloadFileState.AVAILABLE else DownloadFileState.MISSING
            }
        }
    } catch (_: SecurityException) {
        DownloadFileState.ACCESS_REQUIRED
    } catch (_: FileNotFoundException) {
        if (Uri.parse(value).authority == MediaStore.AUTHORITY) {
            DownloadFileState.ACCESS_REQUIRED
        } else {
            DownloadFileState.MISSING
        }
    } catch (_: IOException) {
        DownloadFileState.ACCESS_REQUIRED
    } catch (_: IllegalArgumentException) {
        DownloadFileState.MISSING
    }

private fun documentFolderState(
    context: Context,
    value: String,
): DownloadFileState {
    val parts = value.split('|', limit = FOLDER_URI_PARTS)
    if (parts.size != FOLDER_URI_PARTS) return DownloadFileState.MISSING
    val tree = Uri.parse(parts[1])
    val authorized =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == tree &&
                it.isReadPermission
        }
    return if (!authorized) {
        DownloadFileState.ACCESS_REQUIRED
    } else {
        val exists =
            DocumentFile
                .fromTreeUri(context, tree)
                ?.let { resolveDownloadFolder(it, parts[2]) }
                ?.isDirectory == true
        if (exists) DownloadFileState.AVAILABLE else DownloadFileState.MISSING
    }
}

private fun mediaFolderState(
    context: Context,
    value: String,
): DownloadFileState {
    val exists =
        context.contentResolver
            .query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(value.substringAfter('|')),
                null,
            )?.use { it.moveToFirst() } == true
    // MediaStore filters out inaccessible rows. An empty result doesn't prove the files are gone.
    return if (exists) DownloadFileState.AVAILABLE else DownloadFileState.ACCESS_REQUIRED
}
