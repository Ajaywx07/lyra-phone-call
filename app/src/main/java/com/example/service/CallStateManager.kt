package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import com.example.data.local.entity.CallSummary
import com.example.data.repository.CallRepository
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.utils.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface TelecomCallBridge {
    fun answerCall()
    fun rejectOrDisconnectCall(): Boolean
    fun toggleHold(hold: Boolean)
    fun applyMute(muted: Boolean)
    fun setSpeaker(speaker: Boolean)
    fun setAudioRouteMode(route: Int) {}
    fun mergeConference() {}
    fun silenceRinger() {}
    fun sendDtmf(digit: Char)
}

object CallStateManager {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var telecomBridge: TelecomCallBridge? = null
    @Volatile
    private var appContext: Context? = null

    fun bindApplicationContext(context: Context) {
        appContext = context.applicationContext ?: context
    }

    private val _callState = MutableStateFlow(CallStateEnum.IDLE)
    val callState: StateFlow<CallStateEnum> = _callState.asStateFlow()

    private val _currentCaller = MutableStateFlow<CallerInfo?>(null)
    val currentCaller: StateFlow<CallerInfo?> = _currentCaller.asStateFlow()

    private val _isOutgoingCall = MutableStateFlow(false)
    val isOutgoingCall: StateFlow<Boolean> = _isOutgoingCall.asStateFlow()

    private val _activeSimLabel = MutableStateFlow("SIM 1")
    val activeSimLabel: StateFlow<String> = _activeSimLabel.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private val _callStartTimeMs = MutableStateFlow(0L)
    val callStartTimeMs: StateFlow<Long> = _callStartTimeMs.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isHold = MutableStateFlow(false)
    val isHold: StateFlow<Boolean> = _isHold.asStateFlow()

    private val _isRingerSilenced = MutableStateFlow(false)
    val isRingerSilenced: StateFlow<Boolean> = _isRingerSilenced.asStateFlow()

    private val _isSimulatedCall = MutableStateFlow(false)
    val isSimulatedCall: StateFlow<Boolean> = _isSimulatedCall.asStateFlow()

    private val _isFullScreenIncomingUiVisible = MutableStateFlow(false)
    val isFullScreenIncomingUiVisible: StateFlow<Boolean> = _isFullScreenIncomingUiVisible.asStateFlow()

    fun setFullScreenIncomingUiVisible(visible: Boolean) {
        _isFullScreenIncomingUiVisible.value = visible
    }

    @Suppress("DEPRECATION")
    private val _currentAudioRoute = MutableStateFlow(CallAudioState.ROUTE_EARPIECE)
    val currentAudioRoute: StateFlow<Int> = _currentAudioRoute.asStateFlow()

    @Suppress("DEPRECATION")
    private val _supportedAudioRoutes = MutableStateFlow(
        CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
    )
    val supportedAudioRoutes: StateFlow<Int> = _supportedAudioRoutes.asStateFlow()

    private var hasTelecomAudioState: Boolean = false
    private var systemBluetoothConnected: Boolean = false

    private val _isBluetoothAvailable = MutableStateFlow(false)
    val isBluetoothAvailable: StateFlow<Boolean> = _isBluetoothAvailable.asStateFlow()

    private val _conferenceParticipantCount = MutableStateFlow(1)
    val conferenceParticipantCount: StateFlow<Int> = _conferenceParticipantCount.asStateFlow()

    private val _canMergeConference = MutableStateFlow(false)
    val canMergeConference: StateFlow<Boolean> = _canMergeConference.asStateFlow()

    private val _isConferenceMerged = MutableStateFlow(false)
    val isConferenceMerged: StateFlow<Boolean> = _isConferenceMerged.asStateFlow()

    private val _callStatusBanner = MutableStateFlow<String?>(null)
    val callStatusBanner: StateFlow<String?> = _callStatusBanner.asStateFlow()

    private val _currentCallNotes = MutableStateFlow("")
    val currentCallNotes: StateFlow<String> = _currentCallNotes.asStateFlow()

    private val _lastSummary = MutableStateFlow<CallSummary?>(null)
    val lastSummary: StateFlow<CallSummary?> = _lastSummary.asStateFlow()

    fun registerTelecomBridge(bridge: TelecomCallBridge?) {
        telecomBridge = bridge
        if (bridge == null) {
            hasTelecomAudioState = false
            recomputeBluetoothAvailability()
        }
    }

    fun hasTelecomBridge(): Boolean = telecomBridge != null

    fun isOngoingCall(): Boolean {
        val state = _callState.value
        return state == CallStateEnum.DIALING ||
            state == CallStateEnum.ACTIVE ||
            state == CallStateEnum.VOICEMAIL
    }

    fun isRingingOrOngoing(): Boolean {
        return _callState.value == CallStateEnum.RINGING || isOngoingCall()
    }

    fun onIncomingCall(
        caller: CallerInfo,
        simLabel: String = "SIM 1",
        isSimulated: Boolean = false
    ) {
        timerJob?.cancel()
        _currentCaller.value = caller
        _isOutgoingCall.value = false
        _activeSimLabel.value = simLabel
        _callState.value = CallStateEnum.RINGING
        _callDurationSeconds.value = 0L
        _callStartTimeMs.value = 0L
        _isHold.value = false
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isRingerSilenced.value = false
        _isSimulatedCall.value = isSimulated
        _isFullScreenIncomingUiVisible.value = false
        _conferenceParticipantCount.value = 1
        _canMergeConference.value = false
        _isConferenceMerged.value = false
        _callStatusBanner.value = null
        _currentCallNotes.value = ""
    }

    fun updateCurrentCaller(caller: CallerInfo, simLabel: String? = null) {
        val currentState = _callState.value
        if (currentState == CallStateEnum.IDLE || currentState == CallStateEnum.DISCONNECTED) {
            return
        }
        _currentCaller.value = caller
        if (!simLabel.isNullOrBlank()) {
            _activeSimLabel.value = simLabel
        }
    }

    fun onOutgoingCall(
        caller: CallerInfo,
        simLabel: String = "SIM 1",
        isSimulated: Boolean = false
    ) {
        timerJob?.cancel()
        _currentCaller.value = caller
        _isOutgoingCall.value = true
        _activeSimLabel.value = simLabel
        _callState.value = CallStateEnum.DIALING
        _callDurationSeconds.value = 0L
        _callStartTimeMs.value = System.currentTimeMillis()
        _isHold.value = false
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isRingerSilenced.value = false
        _isSimulatedCall.value = isSimulated
        _isFullScreenIncomingUiVisible.value = false
        _conferenceParticipantCount.value = 1
        _canMergeConference.value = false
        _isConferenceMerged.value = false
        _callStatusBanner.value = null
        _currentCallNotes.value = ""
    }

    fun onCallAnswered() {
        if (_callState.value == CallStateEnum.VOICEMAIL) {
            return
        }
        val wasAlreadyActive = _callState.value == CallStateEnum.ACTIVE
        _isRingerSilenced.value = false
        _isFullScreenIncomingUiVisible.value = false
        _callState.value = CallStateEnum.ACTIVE
        if (!wasAlreadyActive) {
            _callStartTimeMs.value = System.currentTimeMillis()
            startDurationTimer()
        }
    }

    fun silenceRinger() {
        if (_callState.value != CallStateEnum.RINGING) return
        _isRingerSilenced.value = true
        _callStatusBanner.value = "Ringtone silenced"
        runCatching { telecomBridge?.silenceRinger() }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            _callDurationSeconds.value = 0L
            while (isActive && _callState.value == CallStateEnum.ACTIVE) {
                delay(1000)
                _callDurationSeconds.value += 1
            }
        }
    }

    fun acceptActiveCall() {
        telecomBridge?.answerCall()
        onCallAnswered()
    }

    /**
     * Routes an incoming call internally into Voicemail screening:
     * answers the Telecom call if ringing, prepares acoustic speaker/mic bridge so the greeting
     * is audible on speaker (instead of muted), and enters VOICEMAIL state.
     */
    @Suppress("DEPRECATION")
    fun routeToVoicemail() {
        timerJob?.cancel()
        if (_callState.value == CallStateEnum.RINGING) {
            telecomBridge?.answerCall()
        }
        _isRingerSilenced.value = false
        _callState.value = CallStateEnum.VOICEMAIL
        _callStartTimeMs.value = System.currentTimeMillis()
        _callDurationSeconds.value = 0L
        setVoicemailAcousticBridge(speakingGreeting = true)
    }

    /**
     * Controls native acoustic bridging during Voicemail screening:
     * - speakingGreeting = true: speaker ON + mic UNMUTED so TTS greeting & beep reach uplink TX
     * - speakingGreeting = false: speaker ON + mic MUTED so owner room audio stays silent while caller is recorded
     */
    @Suppress("DEPRECATION")
    fun setVoicemailAcousticBridge(speakingGreeting: Boolean) {
        val shouldMute = !speakingGreeting
        _isMuted.value = shouldMute
        _isSpeakerOn.value = true
        _currentAudioRoute.value = CallAudioState.ROUTE_SPEAKER

        val bridge = telecomBridge
        if (bridge != null) {
            runCatching { bridge.applyMute(shouldMute) }
            runCatching { bridge.setSpeaker(true) }
        }
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            runCatching {
                am.isMicrophoneMute = shouldMute
                am.isSpeakerphoneOn = true
            }
        }
    }

    @Suppress("DEPRECATION")
    fun restoreAfterVoicemail() {
        val bridge = telecomBridge
        if (bridge != null) {
            runCatching { bridge.applyMute(false) }
            runCatching { bridge.setSpeaker(false) }
        }
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            runCatching {
                am.isMicrophoneMute = false
                am.isSpeakerphoneOn = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun terminateLiveTelecomCall() {
        if (_isSimulatedCall.value) return
        val handledByBridge = runCatching { telecomBridge?.rejectOrDisconnectCall() == true }.getOrDefault(false)
        if (!handledByBridge && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ctx = appContext
            if (ctx != null && PermissionHelper.hasAnswerCallsPermission(ctx)) {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                runCatching { tm?.endCall() }
            }
        }
    }

    fun cancelDialingOrUnconnectedCall() {
        terminateLiveTelecomCall()
        clearState()
    }

    fun declineOrEndActiveCall(callRepository: CallRepository? = null, onSummaryReady: ((CallSummary) -> Unit)? = null) {
        if (_callState.value == CallStateEnum.DIALING && _callDurationSeconds.value == 0L) {
            cancelDialingOrUnconnectedCall()
            return
        }
        if (_callState.value == CallStateEnum.VOICEMAIL) {
            restoreAfterVoicemail()
        }
        terminateLiveTelecomCall()
        onCallEnded(callRepository, onSummaryReady)
    }

    fun onCallEnded(callRepository: CallRepository? = null, onSummaryReady: ((CallSummary) -> Unit)? = null) {
        if (_callState.value == CallStateEnum.DISCONNECTED || _callState.value == CallStateEnum.IDLE) {
            return
        }
        timerJob?.cancel()
        val caller = _currentCaller.value
        val duration = _callDurationSeconds.value
        val notes = _currentCallNotes.value
        val wasOutgoing = _isOutgoingCall.value

        _isRingerSilenced.value = false
        _isFullScreenIncomingUiVisible.value = false
        _callState.value = CallStateEnum.DISCONNECTED

        if (caller != null && callRepository != null) {
            scope.launch {
                try {
                    val typeStr = when {
                        wasOutgoing -> "OUTGOING"
                        duration > 0 -> "INCOMING"
                        else -> "MISSED"
                    }
                    val summary = callRepository.createAndSaveCallSummary(
                        callerNumber = caller.phoneNumber,
                        callerName = caller.displayName,
                        durationSeconds = duration,
                        callType = typeStr,
                        notes = notes
                    )
                    _lastSummary.value = summary
                    onSummaryReady?.invoke(summary)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun toggleMute() {
        val newValue = !_isMuted.value
        _isMuted.value = newValue
        telecomBridge?.applyMute(newValue)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val newValue = !_isSpeakerOn.value
        _isSpeakerOn.value = newValue
        _currentAudioRoute.value = if (newValue) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        telecomBridge?.setSpeaker(newValue)
    }

    @Suppress("DEPRECATION")
    fun selectAudioRoute(route: Int) {
        _currentAudioRoute.value = route
        _isSpeakerOn.value = route == CallAudioState.ROUTE_SPEAKER
        telecomBridge?.setAudioRouteMode(route)
    }

    fun toggleHold() {
        val newValue = !_isHold.value
        _isHold.value = newValue
        telecomBridge?.toggleHold(newValue)
        _callStatusBanner.value = if (newValue) "Call placed on hold" else "Call resumed"
    }

    fun setHoldState(hold: Boolean) {
        _isHold.value = hold
    }

    @Suppress("DEPRECATION")
    private fun recomputeBluetoothAvailability() {
        val telecomHasBt = (_supportedAudioRoutes.value and CallAudioState.ROUTE_BLUETOOTH) != 0
        _isBluetoothAvailable.value = if (hasTelecomAudioState) {
            telecomHasBt
        } else {
            telecomHasBt || systemBluetoothConnected
        }
    }

    @Suppress("DEPRECATION")
    fun setSystemBluetoothConnected(connected: Boolean) {
        systemBluetoothConnected = connected
        recomputeBluetoothAvailability()
        if (!hasTelecomAudioState && !connected && (_currentAudioRoute.value and CallAudioState.ROUTE_BLUETOOTH) != 0) {
            _currentAudioRoute.value = CallAudioState.ROUTE_EARPIECE
            _isSpeakerOn.value = false
        }
    }

    @Suppress("DEPRECATION")
    fun syncAudioState(
        muted: Boolean,
        speakerOn: Boolean,
        route: Int = _currentAudioRoute.value,
        supportedRoutes: Int = _supportedAudioRoutes.value,
        fromTelecom: Boolean = false
    ) {
        if (fromTelecom) {
            hasTelecomAudioState = true
        }
        if (_callState.value != CallStateEnum.VOICEMAIL) {
            _isMuted.value = muted
            _isSpeakerOn.value = speakerOn
            _currentAudioRoute.value = route
        }
        _supportedAudioRoutes.value = supportedRoutes
        recomputeBluetoothAvailability()
    }

    fun updateConferenceInfo(
        participantCount: Int,
        isMerged: Boolean,
        canMerge: Boolean = _canMergeConference.value
    ) {
        _conferenceParticipantCount.value = participantCount.coerceAtLeast(1)
        _isConferenceMerged.value = isMerged
        _canMergeConference.value = !isMerged && canMerge
    }

    fun mergeConferenceCalls() {
        val bridge = telecomBridge
        if (bridge != null) {
            bridge.mergeConference()
            if (_canMergeConference.value || _conferenceParticipantCount.value >= 2) {
                _isConferenceMerged.value = true
                _canMergeConference.value = false
                _isHold.value = false
                _callStatusBanner.value = "Conference call merged (${_conferenceParticipantCount.value} callers)"
            } else {
                _callStatusBanner.value = "Add a 2nd call first to merge a conference"
            }
        } else {
            val nextCount = _conferenceParticipantCount.value.coerceAtLeast(2)
            _conferenceParticipantCount.value = nextCount
            _isConferenceMerged.value = true
            _canMergeConference.value = false
            _isHold.value = false
            _callStatusBanner.value = "Conference active ($nextCount participants)"
        }
    }

    fun registerSecondaryParticipant(label: String, connectedImmediately: Boolean = false) {
        _conferenceParticipantCount.value = (_conferenceParticipantCount.value + 1).coerceAtLeast(2)
        _isConferenceMerged.value = false
        _canMergeConference.value = connectedImmediately
        _callStatusBanner.value = if (connectedImmediately) {
            "Connected with $label • Tap Merge to start conference"
        } else {
            "Dialing $label • Call 1 on hold"
        }
    }

    fun markSecondaryCallConnected(label: String? = null) {
        if (_callState.value == CallStateEnum.IDLE || _callState.value == CallStateEnum.DISCONNECTED) {
            return
        }
        _conferenceParticipantCount.value = _conferenceParticipantCount.value.coerceAtLeast(2)
        _isConferenceMerged.value = false
        _canMergeConference.value = true
        val target = label?.takeIf { it.isNotBlank() } ?: "2nd call"
        _callStatusBanner.value = "$target connected • Tap Merge to conference"
    }

    fun setCallStatusBanner(message: String?) {
        _callStatusBanner.value = message
    }

    fun sendDtmfTone(digit: Char) {
        telecomBridge?.sendDtmf(digit)
    }

    fun updateNotes(notes: String) {
        _currentCallNotes.value = notes
    }

    @Suppress("DEPRECATION")
    fun clearState() {
        timerJob?.cancel()
        if (_callState.value == CallStateEnum.VOICEMAIL) {
            restoreAfterVoicemail()
        }
        _callState.value = CallStateEnum.IDLE
        _currentCaller.value = null
        _isOutgoingCall.value = false
        _activeSimLabel.value = "SIM 1"
        _callDurationSeconds.value = 0L
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isHold.value = false
        _isRingerSilenced.value = false
        _isSimulatedCall.value = false
        _isFullScreenIncomingUiVisible.value = false
        hasTelecomAudioState = false
        _currentAudioRoute.value = CallAudioState.ROUTE_EARPIECE
        _supportedAudioRoutes.value = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
        recomputeBluetoothAvailability()
        _conferenceParticipantCount.value = 1
        _canMergeConference.value = false
        _isConferenceMerged.value = false
        _callStatusBanner.value = null
        _currentCallNotes.value = ""
    }
}
