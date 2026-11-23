package com.example.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.entity.CallRecording
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingTarget(
    val uri: Uri,
    val relativePath: String,
    val outputStream: OutputStream?,
    val isPendingSupported: Boolean = false
)

object CallRecordingStorage {

    private const val DIRECTORY_NAME = "Lyra/Recordings"
    private const val RELATIVE_PATH = "Recordings/Lyra"

    /**
     * Generates a filename adhering strictly to:
     * yyyy-MM-dd_HHmmss_Name_PhoneNumber.wav
     * Using "Unknown" when caller name is unavailable.
     */
    fun generateFileName(callerName: String?, callerNumber: String?): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())

        val sanitizedName = if (callerName.isNullOrBlank()) {
            "Unknown"
        } else {
            callerName.trim()
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .trim('_')
                .ifEmpty { "Unknown" }
        }

        val sanitizedNumber = if (callerNumber.isNullOrBlank()) {
            "Unknown"
        } else {
            callerNumber.trim()
                .replace(Regex("[^0-9+]"), "")
                .ifEmpty { "Unknown" }
        }

        return "${timestamp}_${sanitizedName}_${sanitizedNumber}.wav"
    }

    /**
     * Creates an output destination in shared MediaStore storage (or fallback app files dir).
     */
    fun createRecordingTarget(
        context: Context,
        fileName: String,
        callerName: String,
        callerNumber: String
    ): RecordingTarget {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Audio.Media.ARTIST, if (callerName.isNotBlank()) callerName else "Unknown")
                put(MediaStore.Audio.Media.COMPOSER, callerNumber)
                put(MediaStore.Audio.Media.ALBUM, "Lyra Recordings")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val uri = runCatching {
                resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
            }.getOrNull() ?: runCatching {
                resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            }.getOrNull()

            if (uri != null) {
                val stream = runCatching { resolver.openOutputStream(uri) }.getOrNull()
                if (stream != null) {
                    return RecordingTarget(
                        uri = uri,
                        relativePath = RELATIVE_PATH,
                        outputStream = stream,
                        isPendingSupported = true
                    )
                }
            }
        }

        // Fallback for older APIs or environments where MediaStore insert failed (e.g. robolectric/sandboxes)
        val baseDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
            DIRECTORY_NAME
        ).apply { mkdirs() }
        val targetFile = File(baseDir, fileName)
        val stream = FileOutputStream(targetFile)
        val fileUri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
        }.getOrDefault(Uri.fromFile(targetFile))

        return RecordingTarget(
            uri = fileUri,
            relativePath = targetFile.absolutePath,
            outputStream = stream,
            isPendingSupported = false
        )
    }

    fun finishPendingRecording(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            runCatching {
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
    }

    /**
     * Deletes a call recording. Accesses recording.callerName and recording.fileName.
     */
    fun deleteRecording(context: Context, recording: CallRecording): Boolean {
        var deleted = false
        val uriStr = recording.mediaStoreUri
        if (uriStr.isNotBlank()) {
            val uri = Uri.parse(uriStr)
            deleted = runCatching {
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
        }

        // If MediaStore delete didn't remove local file path, delete directly
        if (recording.relativePath.isNotBlank()) {
            val file = File(recording.relativePath)
            if (file.exists()) {
                val fileDeleted = runCatching { file.delete() }.getOrDefault(false)
                deleted = deleted || fileDeleted
            }
        }

        // Ensure we check by filename in the standard directory as fallback
        if (!deleted && recording.fileName.isNotBlank()) {
            val fallbackFile = File(
                File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, DIRECTORY_NAME),
                recording.fileName
            )
            if (fallbackFile.exists()) {
                deleted = runCatching { fallbackFile.delete() }.getOrDefault(false)
            }
        }

        return deleted
    }

    /**
     * Creates an Android share Intent for the recording.
     * Accesses recording.callerName, recording.fileName, and recording.mediaStoreUri.
     */
    fun createShareIntent(context: Context, recording: CallRecording): Intent {
        val uri: Uri = if (recording.mediaStoreUri.isNotBlank()) {
            Uri.parse(recording.mediaStoreUri)
        } else {
            val file = File(recording.relativePath)
            if (file.exists()) {
                runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrDefault(Uri.fromFile(file))
            } else {
                Uri.EMPTY
            }
        }

        val shareTitle = "Recording: ${recording.callerName} (${recording.fileName})"

        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            putExtra(Intent.EXTRA_TEXT, "Lyra Call Recording with ${recording.callerName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Writes a standard 44-byte RIFF/WAVE header to an OutputStream for PCM 16-bit audio.
     */
    fun writeWavHeader(
        out: OutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long = 44100L,
        channels: Int = 1,
        byteRate: Long = 44100L * 2 * 1
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
        header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
        header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xffL).toByte()
        header[25] = ((longSampleRate shr 8) and 0xffL).toByte()
        header[26] = ((longSampleRate shr 16) and 0xffL).toByte()
        header[27] = ((longSampleRate shr 24) and 0xffL).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = ((byteRate shr 8) and 0xffL).toByte()
        header[30] = ((byteRate shr 16) and 0xffL).toByte()
        header[31] = ((byteRate shr 24) and 0xffL).toByte()
        header[32] = (channels * 2).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()
        out.write(header, 0, 44)
    }
}
