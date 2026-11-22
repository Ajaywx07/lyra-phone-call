package com.example.service

import android.content.Context
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.CallRecording
import com.example.domain.model.CallerInfo
import com.example.utils.AudioRecorderManager
import com.example.utils.CallRecordingStorage
import com.example.utils.CallRecordingSupportMode
import com.example.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide coordinator for in-call audio recording.
 * Ensures recordings started via ActiveCallScreen or More Sheet automatically stop and save
 * into shared storage (Lyra/Recordings via MediaStore) + Room DB whenever the call ends
 * from ANY trigger (local End Call, notification action, or remote disconnect in LyraInCallService).
 */
object CallRecordingCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stopSaveInFlight = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var recorder: AudioRecorderManager? = null

    @Volatile
    private var activeCallerSnapshot: CallerInfo? = null

    @Volatile
    private var recordingStartTimestampMs: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _activeCaptureSourceLabel = MutableStateFlow("VOICE_COMMUNICATION")
    val activeCaptureSourceLabel: StateFlow<String> = _activeCaptureSourceLabel.asStateFlow()

    private val _isTwoWayAudio = MutableStateFlow(false)
    val isTwoWayAudio: StateFlow<Boolean> = _isTwoWayAudio.asStateFlow()

    private val _lastSavedRecording = MutableStateFlow<CallRecording?>(null)
    val lastSavedRecording: StateFlow<CallRecording?> = _lastSavedRecording.asStateFlow()

    fun bindApplicationContext(context: Context) {
        val ctx = context.applicationContext ?: context
        appContext = ctx
        if (recorder == null) {
            synchronized(this) {
                if (recorder == null) {
                    val mgr = AudioRecorderManager(ctx)
                    recorder = mgr
                    scope.launch {
                        mgr.elapsedSeconds.collect { secs ->
                            if (_isRecording.value) {
                                _elapsedSeconds.value = secs
                            }
                        }
                    }
                    scope.launch {
                        mgr.activeCaptureSource.collect { source ->
                            _activeCaptureSourceLabel.value = source.label
                            _isTwoWayAudio.value = source.isTwoWay
                        }
                    }
                }
            }
        }
    }

    private fun ensureRecorder(context: Context): AudioRecorderManager {
        bindApplicationContext(context)
        return recorder!!
    }

    fun toggleRecording(
        context: Context,
        caller: CallerInfo?
    ): Boolean {
        val ctx = context.applicationContext ?: context
        bindApplicationContext(ctx)
        return if (_isRecording.value) {
            stopAndSaveIfActive(ctx, save = true)
            false
        } else {
            startRecording(ctx, caller)
        }
    }

    fun startRecording(
        context: Context,
        caller: CallerInfo?
    ): Boolean {
        val ctx = context.applicationContext ?: context
        val mgr = ensureRecorder(ctx)
        if (_isRecording.value) return true

        if (!PermissionHelper.hasCallRecordingRequiredPermissions(ctx)) {
            val support = CallRecordingStorage.detectRecordingCapability(ctx)
            CallStateManager.setCallStatusBanner(support.headline)
            return false
        }

        val resolvedCaller = caller ?: CallStateManager.currentCaller.value
        activeCallerSnapshot = resolvedCaller
        recordingStartTimestampMs = System.currentTimeMillis()
        stopSaveInFlight.set(false)

        return runCatching {
            mgr.startRecording(
                scope = scope,
                preferCallCapture = true,
                onMaxLimitReached = {
                    stopAndSaveIfActive(ctx, save = true)
                }
            )
            _isRecording.value = true
            _elapsedSeconds.value = 0L

            val source = mgr.activeCaptureSource.value
            _activeCaptureSourceLabel.value = source.label
            _isTwoWayAudio.value = source.isTwoWay

            val support = CallRecordingStorage.detectRecordingCapability(ctx)
            val bannerText = if (source.isTwoWay || support.mode == CallRecordingSupportMode.FULL_TWO_WAY) {
                "Recording call (Two-Way) • Saving to Lyra/Recordings"
            } else {
                "Recording mic audio (Android restricts 2-way capture)"
            }
            CallStateManager.setCallStatusBanner(bannerText)
            true
        }.getOrElse { err ->
            _isRecording.value = false
            _elapsedSeconds.value = 0L
            activeCallerSnapshot = null
            CallStateManager.setCallStatusBanner(
                err.localizedMessage ?: "Unable to start call recording on this device"
            )
            false
        }
    }

    fun stopAndSaveIfActive(
        context: Context? = appContext,
        save: Boolean = true
    ) {
        val mgr = recorder
        val ctx = context?.applicationContext ?: context ?: appContext
        if (!_isRecording.value && mgr?.isRecording?.value != true) {
            return
        }
        if (!stopSaveInFlight.compareAndSet(false, true)) {
            return
        }

        val capturedSeconds = _elapsedSeconds.value.coerceAtLeast(mgr?.elapsedSeconds?.value ?: 0L)
        val caller = activeCallerSnapshot ?: CallStateManager.currentCaller.value
        val startedAt = recordingStartTimestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val source = mgr?.activeCaptureSource?.value ?: AudioRecorderManager.CaptureSource.VOICE_COMMUNICATION

        _isRecording.value = false
        _elapsedSeconds.value = 0L
        activeCallerSnapshot = null
        recordingStartTimestampMs = 0L

        if (!save || ctx == null || mgr == null) {
            runCatching { mgr?.cancelRecording() }
            stopSaveInFlight.set(false)
            return
        }

        scope.launch {
            try {
                val wavBytes = withContext(Dispatchers.IO) {
                    runCatching { mgr.stopAndBuildWav() }.getOrNull()
                }
                if (wavBytes == null || wavBytes.size <= 44) {
                    CallStateManager.setCallStatusBanner("Recording cancelled (no audio captured)")
                    return@launch
                }

                val entity = persistWavBytesAsRecording(
                    context = ctx,
                    wavBytes = wavBytes,
                    caller = caller,
                    durationSeconds = capturedSeconds.coerceAtLeast(1L),
                    timestampMillis = startedAt,
                    captureSourceLabel = source.label,
                    isTwoWayAudio = source.isTwoWay
                )
                _lastSavedRecording.value = entity
                CallStateManager.setCallStatusBanner("Saved recording: ${entity.fileName}")
            } catch (e: Exception) {
                CallStateManager.setCallStatusBanner(
                    e.localizedMessage ?: "Failed to save call recording"
                )
            } finally {
                stopSaveInFlight.set(false)
            }
        }
    }

    /**
     * Pure suspend helper usable both at runtime and directly in unit/integration tests
     * to save a WAV byte payload into MediaStore (Lyra/Recordings) and Room DB.
     */
    suspend fun persistWavBytesAsRecording(
        context: Context,
        wavBytes: ByteArray,
        caller: CallerInfo?,
        durationSeconds: Long,
        timestampMillis: Long = System.currentTimeMillis(),
        captureSourceLabel: String = "VOICE_COMMUNICATION",
        isTwoWayAudio: Boolean = false
    ): CallRecording = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext ?: context
        val resolvedName = CallRecordingStorage.resolveCallerNameForRecording(caller)
        val rawNumber = caller?.phoneNumber?.trim().orEmpty()
            .ifBlank { caller?.formattedNumber?.trim().orEmpty() }
            .ifBlank { "Unknown" }

        val fileName = CallRecordingStorage.buildRecordingFileName(
            callerName = caller?.displayName,
            phoneNumber = caller?.phoneNumber,
            timestampMillis = timestampMillis,
            isKnown = caller?.isKnown ?: false
        )

        val savedMedia = CallRecordingStorage.saveWavToSharedStorage(
            context = ctx,
            wavBytes = wavBytes,
            fileName = fileName,
            timestampMillis = timestampMillis
        )

        val entity = CallRecording(
            callerName = resolvedName,
            callerNumber = rawNumber,
            fileName = savedMedia.fileName,
            mediaStoreUri = savedMedia.mediaStoreUri,
            relativePath = savedMedia.relativePath,
            durationSeconds = durationSeconds.coerceAtLeast(0L),
            fileSizeBytes = savedMedia.fileSizeBytes,
            timestamp = timestampMillis,
            captureSource = captureSourceLabel,
            isTwoWayAudio = isTwoWayAudio
        )

        val dao = LyraDatabase.getDatabase(ctx).callRecordingDao()
        val rowId = dao.insertRecording(entity)
        entity.copy(id = rowId)
    }
}
