package com.example.service

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.content.ContextCompat
import com.example.LyraApplication
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.CallRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.LocalRuleBasedAiRepository
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.domain.model.IncomingCallSettingsSnapshot
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import com.example.utils.SimManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Telecom InCallService implementation.
 * Handles incoming and outgoing cellular calls across foreground, background, lock-screen,
 * and killed-process states, launching the dedicated CallActivity and keeping persistent notifications alive.
 */
class LyraInCallService : InCallService(), TelecomCallBridge {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTelecomCall: Call? = null
    private val trackedCalls = mutableListOf<Call>()

    private fun readCallState(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
    }

    private fun startCallForegroundService() {
        runCatching {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, LyraCallService::class.java)
            )
        }
    }

    private fun canBringCallUiToFront(): Boolean {
        return LyraApplication.isAppInForeground ||
            PermissionHelper.hasOverlayPermission(applicationContext) ||
            PermissionHelper.isDefaultDialer(applicationContext)
    }

    private fun launchCallUi(action: String) {
        NotificationHelper.launchCallActivity(applicationContext, action)
    }

    private fun createCallRepository(): CallRepository {
        val db = LyraDatabase.getDatabase(applicationContext)
        return CallRepository(
            db.callNoteDao(),
            db.callSummaryDao(),
            db.priorityContactDao(),
            LocalRuleBasedAiRepository()
        )
    }

    private fun resolveAccountLabel(call: Call): String {
        return SimManager.resolveAccountLabel(applicationContext, call.details?.accountHandle)
    }

    private fun extractTelecomDisplayName(call: Call): String? {
        val details = call.details ?: return null
        val contactName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details.contactDisplayName?.toString()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return contactName ?: details.callerDisplayName?.takeIf { it.isNotBlank() }
    }

    private fun handleOutgoingTransition(call: Call, isAlreadyActive: Boolean) {
        val handle = call.details?.handle?.schemeSpecificPart ?: ""
        val accountLabel = resolveAccountLabel(call)
        val formattedHandle = PhoneNumberUtils.formatDisplay(handle)
        val telecomName = extractTelecomDisplayName(call)
        val preliminaryCaller = CallerInfo(
            phoneNumber = handle,
            formattedNumber = formattedHandle,
            displayName = telecomName ?: formattedHandle.ifBlank { "Outgoing Call" },
            photoUri = null,
            isKnown = telecomName != null,
            contactLabel = if (telecomName != null) "Saved Contact" else "Outgoing Call"
        )

        if (CallStateManager.callState.value == CallStateEnum.IDLE ||
            CallStateManager.currentCaller.value == null
        ) {
            CallStateManager.onOutgoingCall(preliminaryCaller, simLabel = accountLabel)
        }
        if (isAlreadyActive) {
            CallStateManager.onCallAnswered()
        }
        startCallForegroundService()
        if (canBringCallUiToFront()) {
            launchCallUi("ACTION_OUTGOING_CALL")
        }

        serviceScope.launch {
            val db = LyraDatabase.getDatabase(applicationContext)
            val prefs = LyraPreferences(applicationContext)
            val repo = ContactsRepository(applicationContext, db.priorityContactDao(), prefs)
            val resolvedCaller = repo.resolveCaller(handle)
            if (CallStateManager.callState.value == CallStateEnum.DIALING ||
                CallStateManager.callState.value == CallStateEnum.ACTIVE
            ) {
                CallStateManager.updateCurrentCaller(resolvedCaller, simLabel = accountLabel)
            }
        }
    }

    private fun recordMissedCallIfUnanswered(
        disconnectedCall: Call?,
        prevState: CallStateEnum,
        prevCaller: CallerInfo?,
        prevSimLabel: String,
        wasOutgoing: Boolean
    ) {
        if (prevState != CallStateEnum.RINGING || wasOutgoing) return
        val disconnectCode = disconnectedCall?.details?.disconnectCause?.code
        if (disconnectCode == DisconnectCause.REJECTED || disconnectCode == DisconnectCause.LOCAL) {
            return
        }
        val phoneNumber = prevCaller?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: disconnectedCall?.details?.handle?.schemeSpecificPart.orEmpty()
        if (phoneNumber.isBlank() && prevCaller == null) return

        val callerName = prevCaller?.displayName?.takeIf { it.isNotBlank() }
            ?: extractTelecomDisplayName(disconnectedCall ?: return)
        val simLabelToRecord = prevSimLabel.takeIf { it.isNotBlank() }
            ?: disconnectedCall?.let { resolveAccountLabel(it) }
        val missedAt = System.currentTimeMillis()

        serviceScope.launch {
            val resolvedName = if (!callerName.isNullOrBlank()) {
                callerName
            } else {
                runCatching {
                    val db = LyraDatabase.getDatabase(applicationContext)
                    val prefs = LyraPreferences(applicationContext)
                    ContactsRepository(applicationContext, db.priorityContactDao(), prefs)
                        .resolveCaller(phoneNumber)
                        .displayName
                }.getOrNull()
            }
            val effectiveSimLabel = simLabelToRecord?.takeIf {
                SimManager.isDualSimDevice(applicationContext) || it != "SIM 1"
            }
            NotificationHelper.recordAndNotifyMissedCall(
                context = applicationContext,
                callerName = resolvedName,
                phoneNumber = phoneNumber,
                timestamp = missedAt,
                simLabel = effectiveSimLabel
            )
        }
    }

    private fun handleCallDisconnectedOrRemoved(disconnectedCall: Call? = null) {
        val prevState = CallStateManager.callState.value
        val prevCaller = CallStateManager.currentCaller.value
        val prevSimLabel = CallStateManager.activeSimLabel.value
        val wasOutgoing = CallStateManager.isOutgoingCall.value

        recordMissedCallIfUnanswered(
            disconnectedCall = disconnectedCall,
            prevState = prevState,
            prevCaller = prevCaller,
            prevSimLabel = prevSimLabel,
            wasOutgoing = wasOutgoing
        )

        NotificationHelper.cancelAllCallNotifications(applicationContext)
        val currentDuration = CallStateManager.callDurationSeconds.value
        when {
            prevState == CallStateEnum.IDLE -> Unit
            prevState == CallStateEnum.DIALING && currentDuration == 0L -> {
                CallStateManager.clearState()
            }
            else -> {
                CallStateManager.onCallEnded(createCallRepository())
            }
        }
        runCatching {
            applicationContext.stopService(Intent(applicationContext, LyraCallService::class.java))
        }
        activeTelecomCall = null
    }

    private fun refreshMultiCallState() {
        val snapshot = trackedCalls.toList()
        val activeCalls = snapshot.filter { readCallState(it) == Call.STATE_ACTIVE }
        val holdingCalls = snapshot.filter { readCallState(it) == Call.STATE_HOLDING }
        val hasConferenceChildren = snapshot.any { runCatching { it.children.isNotEmpty() }.getOrDefault(false) }
        val hasConferenceable = snapshot.any { runCatching { it.conferenceableCalls.isNotEmpty() }.getOrDefault(false) }
        val totalParticipants = maxOf(
            snapshot.size,
            snapshot.maxOfOrNull { runCatching { it.children.size }.getOrDefault(0) } ?: 1
        )
        val canMerge = !hasConferenceChildren &&
            ((activeCalls.isNotEmpty() && holdingCalls.isNotEmpty()) || hasConferenceable)

        if (activeCalls.isNotEmpty()) {
            activeTelecomCall = activeCalls.last()
        } else if (holdingCalls.isNotEmpty() && activeTelecomCall == null) {
            activeTelecomCall = holdingCalls.last()
        }

        CallStateManager.updateConferenceInfo(
            participantCount = totalParticipants,
            isMerged = hasConferenceChildren,
            canMerge = canMerge
        )
        CallStateManager.setHoldState(holdingCalls.isNotEmpty() && activeCalls.isEmpty())
    }

    private fun handleSingleCallDisconnected(call: Call?) {
        if (call != null) {
            runCatching { call.unregisterCallback(callCallback) }
            trackedCalls.remove(call)
        }
        val remainingCalls = trackedCalls.toList().filter { readCallState(it) != Call.STATE_DISCONNECTED }
        if (remainingCalls.isEmpty()) {
            handleCallDisconnectedOrRemoved(call)
        } else {
            activeTelecomCall = remainingCalls.lastOrNull()
            refreshMultiCallState()
            CallStateManager.setCallStatusBanner("Returned to remaining call")
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            val telecomCall = call ?: activeTelecomCall
            when (state) {
                Call.STATE_SELECT_PHONE_ACCOUNT -> Unit
                Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    if (trackedCalls.size <= 1 && telecomCall != null) {
                        handleOutgoingTransition(telecomCall, isAlreadyActive = false)
                    } else {
                        refreshMultiCallState()
                        startCallForegroundService()
                        if (canBringCallUiToFront()) {
                            launchCallUi("ACTION_OUTGOING_CALL")
                        }
                    }
                }
                Call.STATE_HOLDING -> {
                    refreshMultiCallState()
                }
                Call.STATE_ACTIVE -> {
                    if (CallStateManager.callState.value == CallStateEnum.IDLE && telecomCall != null) {
                        handleOutgoingTransition(telecomCall, isAlreadyActive = true)
                    } else {
                        CallStateManager.onCallAnswered()
                        refreshMultiCallState()
                        startCallForegroundService()
                        if (canBringCallUiToFront()) {
                            launchCallUi("ACTION_OUTGOING_CALL")
                        }
                    }
                }
                Call.STATE_DISCONNECTED -> {
                    handleSingleCallDisconnected(call)
                }
            }
        }

        override fun onChildrenChanged(call: Call?, children: MutableList<Call>?) {
            super.onChildrenChanged(call, children)
            refreshMultiCallState()
        }

        override fun onConferenceableCallsChanged(call: Call?, conferenceableCalls: MutableList<Call>?) {
            super.onConferenceableCallsChanged(call, conferenceableCalls)
            refreshMultiCallState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallStateManager.registerTelecomBridge(this)
    }

    override fun onDestroy() {
        CallStateManager.registerTelecomBridge(null)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        if (CallStateManager.callState.value == CallStateEnum.IDLE) {
            return
        }
        val action = if (CallStateManager.callState.value == CallStateEnum.RINGING) {
            "ACTION_INCOMING_CALL"
        } else {
            "ACTION_OUTGOING_CALL"
        }
        launchCallUi(action)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState ?: return
        val hasBtMask = (audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        val hasBtDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioState.supportedBluetoothDevices.isNotEmpty() ||
                (audioState.route and CallAudioState.ROUTE_BLUETOOTH) != 0
        } else {
            hasBtMask
        }
        val effectiveSupportedRoutes = if (hasBtMask && hasBtDevice) {
            audioState.supportedRouteMask
        } else {
            audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH.inv()
        }
        val speakerOn = (audioState.route and CallAudioState.ROUTE_SPEAKER) != 0
        CallStateManager.syncAudioState(
            muted = audioState.isMuted,
            speakerOn = speakerOn,
            route = audioState.route,
            supportedRoutes = effectiveSupportedRoutes,
            fromTelecom = true
        )
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (!trackedCalls.contains(call)) {
            trackedCalls.add(call)
        }
        val isPrimaryCall = activeTelecomCall == null || trackedCalls.size == 1
        activeTelecomCall = call
        call.registerCallback(callCallback)

        val currentState = readCallState(call)
        refreshMultiCallState()

        if (!isPrimaryCall) {
            return
        }

        val handle = call.details?.handle?.schemeSpecificPart ?: ""
        val accountLabel = resolveAccountLabel(call)

        if (currentState == Call.STATE_SELECT_PHONE_ACCOUNT) {
            return
        }

        // Fast optimistic incoming call presentation (<10ms): display Telecom metadata immediately
        // without waiting on DataStore/Room/Contacts disk I/O, then enrich asynchronously.
        if (currentState == Call.STATE_RINGING) {
            val formattedHandle = PhoneNumberUtils.formatDisplay(handle)
            val telecomName = extractTelecomDisplayName(call)
            val preliminaryCaller = CallerInfo(
                phoneNumber = handle,
                formattedNumber = formattedHandle,
                displayName = telecomName ?: formattedHandle.ifBlank { "Incoming Call" },
                photoUri = null,
                isKnown = telecomName != null,
                contactLabel = if (telecomName != null) "Saved Contact" else "Incoming Call"
            )
            val cachedSettings = LyraPreferences.getCachedIncomingSnapshotOrNull()
                ?: IncomingCallSettingsSnapshot()

            CallStateManager.onIncomingCall(preliminaryCaller, simLabel = accountLabel)
            NotificationHelper.dispatchIncomingCall(
                context = applicationContext,
                caller = preliminaryCaller,
                fullscreenPreferenceEnabled = cachedSettings.isFullscreenIncomingEnabled,
                incomingScreenEnabled = cachedSettings.isIncomingScreenEnabled,
                backgroundCallingEnabled = cachedSettings.isBackgroundCallingEnabled,
                persistentNotificationEnabled = cachedSettings.isPersistentNotificationEnabled,
                showCallerPhoto = false
            )

            serviceScope.launch {
                val db = LyraDatabase.getDatabase(applicationContext)
                val prefs = LyraPreferences(applicationContext)
                val settings = prefs.getIncomingCallSettingsSnapshot()
                val repo = ContactsRepository(applicationContext, db.priorityContactDao(), prefs)
                val resolvedCaller = repo.resolveCaller(handle)
                if (CallStateManager.callState.value == CallStateEnum.RINGING) {
                    CallStateManager.updateCurrentCaller(resolvedCaller, simLabel = accountLabel)
                    if (resolvedCaller != preliminaryCaller ||
                        (settings.showContactPhotoOnCall && !resolvedCaller.photoUri.isNullOrBlank())
                    ) {
                        NotificationHelper.dispatchIncomingCall(
                            context = applicationContext,
                            caller = resolvedCaller,
                            fullscreenPreferenceEnabled = settings.isFullscreenIncomingEnabled,
                            incomingScreenEnabled = settings.isIncomingScreenEnabled,
                            backgroundCallingEnabled = settings.isBackgroundCallingEnabled,
                            persistentNotificationEnabled = settings.isPersistentNotificationEnabled,
                            showCallerPhoto = settings.showContactPhotoOnCall
                        )
                    }
                }
            }
            return
        }

        when (currentState) {
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                handleOutgoingTransition(call, isAlreadyActive = false)
            }
            Call.STATE_ACTIVE -> {
                handleOutgoingTransition(call, isAlreadyActive = true)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        runCatching { call.unregisterCallback(callCallback) }
        trackedCalls.remove(call)
        if (trackedCalls.isEmpty()) {
            if (activeTelecomCall == call || activeTelecomCall == null) {
                handleCallDisconnectedOrRemoved(call)
            }
        } else {
            activeTelecomCall = trackedCalls.lastOrNull()
            refreshMultiCallState()
        }
    }

    override fun answerCall() {
        runCatching { activeTelecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    }

    override fun rejectOrDisconnectCall(): Boolean {
        val candidateCalls = (trackedCalls.toList() + listOfNotNull(activeTelecomCall)).distinct()
        var signalledAny = false
        for (call in candidateCalls) {
            val state = runCatching { readCallState(call) }.getOrDefault(-1)
            if (state == Call.STATE_DISCONNECTED) continue
            val ok = runCatching {
                if (state == Call.STATE_RINGING) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // SIP 603 Decline: tells carrier this is an explicit user decline (prevents 486 Busy -> forwarding tune delay)
                        call.reject(Call.REJECT_REASON_DECLINED)
                    } else {
                        @Suppress("DEPRECATION")
                        call.reject(false, null)
                    }
                } else {
                    call.disconnect()
                }
                true
            }.getOrDefault(false)
            if (ok) signalledAny = true
        }
        return signalledAny
    }

    override fun toggleHold(hold: Boolean) {
        runCatching {
            if (hold) activeTelecomCall?.hold() else activeTelecomCall?.unhold()
        }
    }

    override fun applyMute(muted: Boolean) {
        runCatching { setMuted(muted) }
    }

    override fun setSpeaker(speaker: Boolean) {
        @Suppress("DEPRECATION")
        val route = if (speaker) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        }
        @Suppress("DEPRECATION")
        runCatching { setAudioRoute(route) }
    }

    override fun setAudioRouteMode(route: Int) {
        @Suppress("DEPRECATION")
        runCatching { setAudioRoute(route) }
    }

    override fun mergeConference() {
        val primary = activeTelecomCall ?: return
        val conferenceTarget = runCatching { primary.conferenceableCalls.firstOrNull() }.getOrNull()
            ?: trackedCalls.toList().firstOrNull { it != primary }
        if (conferenceTarget != null) {
            runCatching { primary.conference(conferenceTarget) }
        }
    }

    override fun silenceRinger() {
        runCatching {
            val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                telecomManager?.silenceRinger()
            }
        }
    }

    override fun sendDtmf(digit: Char) {
        runCatching {
            activeTelecomCall?.playDtmfTone(digit)
            activeTelecomCall?.stopDtmfTone()
        }
    }
}
