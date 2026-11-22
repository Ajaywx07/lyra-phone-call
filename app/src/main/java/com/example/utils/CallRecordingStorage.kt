package com.example.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.entity.CallRecording
import com.example.domain.model.CallerInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CallRecordingSupportMode {
    FULL_TWO_WAY,
    MIC_ONLY_RESTRICTED,
    PERMISSION_REQUIRED
}

data class CallRecordingSupportStatus(
    val mode: CallRecordingSupportMode,
    val headline: String,
    val explanation: String,
    val speakerTip: String,
    val deviceLabel: String,
    val apiLevel: Int,
    val hasMicPermission: Boolean,
    val hasStoragePermission: Boolean,
    val hasCaptureAudioOutput: Boolean
)

data class SavedRecordingMedia(
    val fileName: String,
    val mediaStoreUri: String,
    val relativePath: String,
    val fileSizeBytes: Long,
    val savedViaMediaStore: Boolean
)

object CallRecordingStorage {

    const val SUBDIRECTORY_PATH = "Lyra/Recordings"

    private val unavailableNamePlaceholders = setOf(
        "unknown",
        "unknown caller",
        "incoming call",
        "outgoing call",
        "private number",
        "no caller id",
        "blocked",
        "caller",
        "ended call"
    )

    fun detectRecordingCapability(context: Context): CallRecordingSupportStatus {
        val hasMic = PermissionHelper.hasMicrophonePermission(context)
        val hasStorage = PermissionHelper.hasCallRecordingStoragePermission(context)
        val hasPrivilegedCapture = PermissionHelper.hasCaptureAudioOutputPermission(context)
        val manufacturer = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty().trim()
        val deviceLabel = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android Device" }
        val apiLevel = Build.VERSION.SDK_INT

        return when {
            hasPrivilegedCapture && hasMic && hasStorage -> CallRecordingSupportStatus(
                mode = CallRecordingSupportMode.FULL_TWO_WAY,
                headline = "Two-Way Call Recording Supported",
                explanation = "Privileged CAPTURE_AUDIO_OUTPUT access is available on $deviceLabel (Android ${Build.VERSION.RELEASE}). Both local mic and remote call audio can be captured.",
                speakerTip = "Recordings are saved in shared storage under $SUBDIRECTORY_PATH.",
                deviceLabel = deviceLabel,
                apiLevel = apiLevel,
                hasMicPermission = true,
                hasStoragePermission = true,
                hasCaptureAudioOutput = true
            )

            !hasMic || !hasStorage -> {
                val missingText = buildList {
                    if (!hasMic) add("Microphone (RECORD_AUDIO)")
                    if (!hasStorage) add("Storage")
                }.joinToString(" & ")
                CallRecordingSupportStatus(
                    mode = CallRecordingSupportMode.PERMISSION_REQUIRED,
                    headline = "$missingText Permission Needed",
                    explanation = if (apiLevel >= Build.VERSION_CODES.P) {
                        "Grant $missingText permission to record calls. Note: Android 9+ ($deviceLabel) restricts two-way VOICE_CALL capture for non-system apps, so recordings use your microphone channel."
                    } else {
                        "Grant $missingText permission to capture and save WAV recordings into $SUBDIRECTORY_PATH."
                    },
                    speakerTip = "Android/OEM restriction: Non-privileged apps cannot directly tap cellular downlink audio on Android 9+.",
                    deviceLabel = deviceLabel,
                    apiLevel = apiLevel,
                    hasMicPermission = hasMic,
                    hasStoragePermission = hasStorage,
                    hasCaptureAudioOutput = hasPrivilegedCapture
                )
            }

            else -> CallRecordingSupportStatus(
                mode = CallRecordingSupportMode.MIC_ONLY_RESTRICTED,
                headline = if (apiLevel >= Build.VERSION_CODES.P) {
                    "Two-Way Recording Restricted (Android ${Build.VERSION.RELEASE} / OEM)"
                } else {
                    "Mic-Only Recording Fallback Active ($deviceLabel)"
                },
                explanation = "$deviceLabel (API $apiLevel) restricts privileged CAPTURE_AUDIO_OUTPUT / VOICE_CALL audio routing for third-party dialer apps. Lyra respects this platform rule and records cleanly via VOICE_COMMUNICATION / Microphone without root or hacks.",
                speakerTip = "Turn on Speaker during a call if you need the remote caller's voice captured acoustically through the microphone.",
                deviceLabel = deviceLabel,
                apiLevel = apiLevel,
                hasMicPermission = true,
                hasStoragePermission = true,
                hasCaptureAudioOutput = false
            )
        }
    }

    fun resolveCallerNameForRecording(caller: CallerInfo?): String {
        if (caller == null) return "Unknown"
        return resolveCallerNameForRecording(
            rawName = caller.displayName,
            phoneNumber = caller.phoneNumber,
            isKnown = caller.isKnown
        )
    }

    fun resolveCallerNameForRecording(
        rawName: String?,
        phoneNumber: String?,
        isKnown: Boolean = true
    ): String {
        val trimmed = rawName?.trim().orEmpty()
        if (trimmed.isBlank()) return "Unknown"
        val lower = trimmed.lowercase(Locale.US)
        if (lower in unavailableNamePlaceholders) return "Unknown"
        if (!isKnown && lower.startsWith("unknown")) return "Unknown"

        val numberDigits = phoneNumber.orEmpty().filter { it.isDigit() }
        val nameDigits = trimmed.filter { it.isDigit() }
        val looksLikeRawNumber = trimmed.all { it.isDigit() || it in "+-() ." }
        if (!isKnown && (looksLikeRawNumber || (numberDigits.isNotEmpty() && nameDigits == numberDigits))) {
            return "Unknown"
        }
        if (numberDigits.isNotEmpty() && looksLikeRawNumber && nameDigits == numberDigits) {
            return "Unknown"
        }
        return trimmed
    }

    fun sanitizeNameForFileName(resolvedName: String): String {
        val cleaned = resolvedName
            .trim()
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(40)
        return cleaned.ifBlank { "Unknown" }
    }

    fun sanitizeNumberForFileName(phoneNumber: String?): String {
        val digits = phoneNumber.orEmpty().filter { it.isDigit() }
        return digits.ifBlank { "Unknown" }
    }

    /**
     * Exact filename specification:
     * 2026-11-22_143015_Name_9876543210.wav
     */
    fun buildRecordingFileName(
        callerName: String?,
        phoneNumber: String?,
        timestampMillis: Long = System.currentTimeMillis(),
        isKnown: Boolean = true
    ): String {
        val timestampPart = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(timestampMillis))
        val resolvedName = resolveCallerNameForRecording(callerName, phoneNumber, isKnown)
        val safeName = sanitizeNameForFileName(resolvedName)
        val safeNumber = sanitizeNumberForFileName(phoneNumber)
        return "${timestampPart}_${safeName}_${safeNumber}.wav"
    }

    fun resolvePreferredRelativePath(sdkInt: Int = Build.VERSION.SDK_INT): String {
        val rootDir = if (sdkInt >= Build.VERSION_CODES.S) {
            Environment.DIRECTORY_RECORDINGS
        } else {
            Environment.DIRECTORY_MUSIC
        }
        return "$rootDir/$SUBDIRECTORY_PATH"
    }

    /**
     * Saves the WAV recording into shared storage under Lyra/Recordings using Android MediaStore APIs
     * (Recordings/Lyra/Recordings on API 31+, Music/Lyra/Recordings on API 29-30 & 26-28),
     * falling back gracefully to app-external/internal storage only if MediaStore insert fails.
     */
    fun saveWavToSharedStorage(
        context: Context,
        wavBytes: ByteArray,
        fileName: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): SavedRecordingMedia {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val candidates = buildList {
                add(resolvePreferredRelativePath(Build.VERSION.SDK_INT))
                val musicPath = "${Environment.DIRECTORY_MUSIC}/$SUBDIRECTORY_PATH"
                if (!contains(musicPath)) add(musicPath)
            }
            for (relPath in candidates) {
                val inserted = runCatching {
                    insertViaScopedMediaStore(
                        context = context,
                        wavBytes = wavBytes,
                        fileName = fileName,
                        relativePath = relPath,
                        timestampMillis = timestampMillis
                    )
                }.getOrNull()
                if (inserted != null) {
                    return inserted
                }
            }
        } else {
            val legacySaved = runCatching {
                insertViaLegacySharedStorage(
                    context = context,
                    wavBytes = wavBytes,
                    fileName = fileName,
                    timestampMillis = timestampMillis
                )
            }.getOrNull()
            if (legacySaved != null) {
                return legacySaved
            }
        }

        // Resilient fallback if external shared storage is unavailable (e.g., restricted ROM / test sandbox)
        val fallbackBase = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val fallbackDir = File(fallbackBase, SUBDIRECTORY_PATH).apply { mkdirs() }
        val fallbackFile = File(fallbackDir, fileName)
        fallbackFile.writeBytes(wavBytes)
        return SavedRecordingMedia(
            fileName = fileName,
            mediaStoreUri = Uri.fromFile(fallbackFile).toString(),
            relativePath = SUBDIRECTORY_PATH,
            fileSizeBytes = fallbackFile.length(),
            savedViaMediaStore = false
        )
    }

    private fun insertViaScopedMediaStore(
        context: Context,
        wavBytes: ByteArray,
        fileName: String,
        relativePath: String,
        timestampMillis: Long
    ): SavedRecordingMedia? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.DATE_ADDED, timestampMillis / 1000L)
            put(MediaStore.Audio.Media.DATE_MODIFIED, timestampMillis / 1000L)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open MediaStore output stream for $uri")
            stream.use { out ->
                out.write(wavBytes)
                out.flush()
            }
            val commitValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
                put(MediaStore.Audio.Media.SIZE, wavBytes.size.toLong())
            }
            resolver.update(uri, commitValues, null, null)
            SavedRecordingMedia(
                fileName = fileName,
                mediaStoreUri = uri.toString(),
                relativePath = relativePath,
                fileSizeBytes = wavBytes.size.toLong(),
                savedViaMediaStore = true
            )
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun insertViaLegacySharedStorage(
        context: Context,
        wavBytes: ByteArray,
        fileName: String,
        timestampMillis: Long
    ): SavedRecordingMedia? {
        if (!PermissionHelper.hasCallRecordingStoragePermission(context)) return null
        val publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            ?: return null
        val targetDir = File(publicRoot, SUBDIRECTORY_PATH)
        if (!targetDir.exists() && !targetDir.mkdirs()) return null
        val targetFile = File(targetDir, fileName)
        targetFile.writeBytes(wavBytes)

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.DATA, targetFile.absolutePath)
            put(MediaStore.Audio.Media.SIZE, targetFile.length())
            put(MediaStore.Audio.Media.DATE_ADDED, timestampMillis / 1000L)
            put(MediaStore.Audio.Media.DATE_MODIFIED, timestampMillis / 1000L)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()

        runCatching {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf("audio/wav"),
                null
            )
        }

        val resolvedUri = uri?.toString() ?: Uri.fromFile(targetFile).toString()
        return SavedRecordingMedia(
            fileName = fileName,
            mediaStoreUri = resolvedUri,
            relativePath = "${Environment.DIRECTORY_MUSIC}/$SUBDIRECTORY_PATH",
            fileSizeBytes = targetFile.length(),
            savedViaMediaStore = uri != null
        )
    }

    fun deleteRecordingMedia(context: Context, mediaStoreUri: String): Boolean {
        if (mediaStoreUri.isBlank()) return false
        val uri = runCatching { Uri.parse(mediaStoreUri) }.getOrNull() ?: return false
        return if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file != null && file.exists()) file.delete() else true
        } else {
            val deletedRows = runCatching {
                context.contentResolver.delete(uri, null, null)
            }.getOrDefault(0)
            deletedRows > 0 || !isMediaUriReadable(context, uri)
        }
    }

    fun isMediaUriReadable(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).exists() } == true
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun resolveShareUri(context: Context, recording: CallRecording): Uri? {
        val parsed = runCatching { Uri.parse(recording.mediaStoreUri) }.getOrNull() ?: return null
        return if (parsed.scheme == "file") {
            val file = parsed.path?.let { File(it) } ?: return null
            if (!file.exists()) return null
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrElse { parsed }
        } else {
            parsed
        }
    }

    fun createShareIntent(context: Context, recording: CallRecording): Intent? {
        val shareUri = resolveShareUri(context, recording) ?: return null
        val displayCaller = if (recording.callerNumber.isNotBlank()) {
            "${recording.callerName} (${recording.callerNumber})"
        } else {
            recording.callerName
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, "Call Recording - $displayCaller")
            putExtra(Intent.EXTRA_TEXT, "Lyra call recording: ${recording.fileName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share Call Recording").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun formatDuration(seconds: Long): String {
        val mins = (seconds / 60).coerceAtLeast(0)
        val secs = (seconds % 60).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.US, "%.1f KB", kb)
        } else {
            String.format(Locale.US, "%.2f MB", kb / 1024.0)
        }
    }

    fun mediaStoreIdFromUri(uriString: String): Long? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.scheme != "content") return null
        return runCatching { ContentUris.parseId(uri) }.getOrNull()
    }
}
