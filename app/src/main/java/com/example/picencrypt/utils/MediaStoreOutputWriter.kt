package com.example.picencrypt.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.picencrypt.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object MediaStoreOutputWriter {

    sealed class OutputResult {
        data class Success(val uri: Uri, val displayName: String) : OutputResult()
        data class Failure(val message: String, val throwable: Throwable? = null) : OutputResult()
    }

    /**
     * Transactional write to MediaStore Images.
     * On API 29+, sets IS_PENDING = 1, writes data, and clears IS_PENDING on success.
     * Deletes the uncommitted record on failure or exception.
     */
    fun writeImage(
        context: Context,
        fileName: String,
        mimeType: String = "image/png",
        subDir: String? = null,
        writer: (OutputStream) -> Boolean
    ): OutputResult {
        val appName = context.getString(R.string.app_name)
        val relativeDir = subDir ?: "Pictures/$appName"
        return writeInternal(
            context = context,
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            legacyPublicDir = Environment.DIRECTORY_PICTURES,
            fileName = fileName,
            mimeType = mimeType,
            relativeDir = relativeDir,
            appName = appName,
            isImage = true,
            writer = writer
        )
    }

    /**
     * Transactional write to MediaStore Downloads.
     * On API 29+, sets IS_PENDING = 1, writes data, and clears IS_PENDING on success.
     * Deletes the uncommitted record on failure or exception.
     */
    fun writeDownload(
        context: Context,
        fileName: String,
        mimeType: String = "application/octet-stream",
        subDir: String? = null,
        writer: (OutputStream) -> Boolean
    ): OutputResult {
        val appName = context.getString(R.string.app_name)
        val relativeDir = subDir ?: "Download/$appName"
        return writeInternal(
            context = context,
            collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                null
            },
            legacyPublicDir = Environment.DIRECTORY_DOWNLOADS,
            fileName = fileName,
            mimeType = mimeType,
            relativeDir = relativeDir,
            appName = appName,
            isImage = false,
            writer = writer
        )
    }

    private fun writeInternal(
        context: Context,
        collectionUri: Uri?,
        legacyPublicDir: String,
        fileName: String,
        mimeType: String,
        relativeDir: String,
        appName: String,
        isImage: Boolean,
        writer: (OutputStream) -> Boolean
    ): OutputResult {
        var insertedUri: Uri? = null
        var legacyFile: File? = null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && collectionUri != null) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collectionUri, values)
                    ?: return OutputResult.Failure("Failed to insert MediaStore record")
                insertedUri = uri

                val writeSuccess = context.contentResolver.openOutputStream(uri)?.use { out ->
                    writer(out)
                } ?: false

                if (!writeSuccess) {
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    return OutputResult.Failure("Write operation returned false")
                }

                // Commit transaction: Clear IS_PENDING
                val commitValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, commitValues, null, null)
                OutputResult.Success(uri, fileName)
            } else {
                val saveDir = File(Environment.getExternalStoragePublicDirectory(legacyPublicDir), appName)
                if (!saveDir.exists() && !saveDir.mkdirs()) {
                    return OutputResult.Failure("Failed to create directory: ${saveDir.absolutePath}")
                }
                val targetFile = File(saveDir, fileName)
                legacyFile = targetFile

                val writeSuccess = FileOutputStream(targetFile).use { out ->
                    writer(out)
                }

                if (!writeSuccess) {
                    if (targetFile.exists()) {
                        try { targetFile.delete() } catch (_: Exception) {}
                    }
                    return OutputResult.Failure("Write operation returned false")
                }

                FileUtils.notifyMediaScanner(context, targetFile, mimeType)
                OutputResult.Success(Uri.fromFile(targetFile), fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && insertedUri != null) {
                try { context.contentResolver.delete(insertedUri, null, null) } catch (_: Exception) {}
            } else if (legacyFile != null && legacyFile.exists()) {
                try { legacyFile.delete() } catch (_: Exception) {}
            }
            OutputResult.Failure(e.localizedMessage ?: "Unknown error during MediaStore output", e)
        }
    }
}
