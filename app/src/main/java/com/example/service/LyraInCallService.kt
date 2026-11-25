package com.example.service

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Android Telecom InCallService implementation.
 * Handles incoming and outgoing cellular calls across foreground, background, lock-screen,
 * and killed-process states, keeping live call state, CallActivity, and API 34+ / legacy audio routing synchronized.
 */
class LyraInCallService : InCallService(), TelecomCallBridge {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackedCalls = CopyOnWriteArrayList<Call>()

    @Volatile
    private var activeTelecomCall: Call? = null

    @Volatile
    private var currentCallEndpoint: CallEndpoint? = null

    @Volatile
    private var availableCallEndpoints: List<CallEndpoint> = emptyList()

    @Volatile
    private var lastTelecomMutedState: Boolean = false

    private fun readCallState(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
    }

    private fun liveCallsSnapshot(): List<Call> {
        return trackedCalls.filter { readCallState(it) != Call.STATE_DISCONNECTED }
    }

    private fun resolvePrimaryCall(): Call? {
        val live = liveCallsSnapshot()
        if (live.isEmpty()) return null
        return live.lastOrNull { readCallState(it) == Call.STATE_ACTIVE }
            ?: live.lastOrNull {
                val st = readCallState(it)
                st == Call.STATE_DIALING || st == Call.STATE_CONNECTING || st == Call.STATE_PULLING_CALL
            }
            ?: live.lastOrNull { readCallState(it) == Call.STATE_RINGING }
            ?: live.lastOrNull { readCallState(it) == Call.STATE_HOLDING }
            ?: live.lastOrNull()
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

    private fun buildPreliminaryCaller(call: Call, isIncoming: Boolean): CallerInfo {
        val handle = call.details?.handle?.schemeSpecificPart ?: ""
        val formattedHandle = PhoneNumberUtils.formatDisplay(handle)
        val telecomName = extractTelecomDisplayName(call)
        val fallbackTitle = if (isIncoming) "Incoming Call" else "Outgoing Call"
        return CallerInfo(
            phoneNumber = handle,
            formattedNumber = formattedHandle,
            displayName = telecomName ?: formattedHandle.ifBlank { fallbackTitle },
            photoUri = null,
            isKnown = telecomName != null,
            contactLabel = if (telecomName != null) "Saved Contact" else fallbackTitle
        )
    }

    private fun resolveCallerAsync(call: Call, expectedStates: Set<CallStateEnum>) {
        val handle = call.details?.handle?.schemeSpecificPart ?: ""
        val accountLabel = resolveAccountLabel(call)
        serviceScope.launch {
            val db = LyraDatabase.getDatabase(applicationContext)
            val prefs = LyraPreferences(applicationContext)
            val repo = ContactsRepository(applicationContext, db.priorityContactDao(), prefs)
            val resolvedCaller = repo.resolveCaller(handle)
            if (CallStateManager.callState.value in expectedStates) {
                CallStateManager.updateCurrentCaller(resolvedCaller, simLabel = accountLabel)
            }
        }
    }

    private fun handleOutgoingOrConnectedTransition(
        call: Call,
        isAlreadyActive: Boolean,
        launchUiIfPermitted: Boolean = true
    ) {
        activeTelecomCall = call
        val accountLabel = resolveAccountLabel(call)
        val preliminaryCaller = buildPreliminaryCaller(call, isIncoming = false)
        val currentUiState = CallStateManager.callState.value

        if (currentUiState == CallStateEnum.IDLE ||
            currentUiState == CallStateEnum.DISCONNECTED ||
            CallStateManager.currentCaller.value == null
        ) {
            CallStateManager.onOutgoingCall(preliminaryCaller, simLabel = accountLabel)
        }
        if (isAlreadyActive) {
            CallStateManager.onCallAnswered()
        }
        syncCurrentAudioStateToManager()
        startCallForegroundService()
        if (launchUiIfPermitted &&
            !CallStateManager.isUserMinimizedCallUi.value &&
            (canBringCallUiToFront() || isAlreadyActive)
        ) {
            launchCallUi("ACTION_OUTGOING_CALL")
        }

        resolveCallerAsync(
            call = call,
            expectedStates = setOf(CallStateEnum.DIALING, CallStateEnum.ACTIVE, CallStateEnum.VOICEMAIL)
        )
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
            ?: disconnectedCall?.let { extractTelecomDisplayName(it) }
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
        currentCallEndpoint = null
        availableCallEndpoints = emptyList()
    }

    private fun refreshMultiCallState() {
        val snapshot = liveCallsSnapshot()
        val allParticipantCalls = mutableListOf<Call>()
        snapshot.forEach { call ->
            val children = runCatching { call.children }.getOrNull()
            if (!children.isNullOrEmpty()) {
                allParticipantCalls.addAll(children)
            } else {
                allParticipantCalls.add(call)
            }
        }
        val distinctParticipants = allParticipantCalls.distinct()

        val participantList = distinctParticipants.map { call ->
            val stateInt = readCallState(call)
            val stName = when (stateInt) {
                Call.STATE_ACTIVE -> "Active"
                Call.STATE_HOLDING -> "On Hold"
                Call.STATE_RINGING -> "Ringing"
                Call.STATE_DIALING -> "Dialing"
                else -> "Connected"
            }
            val num = call.details?.handle?.schemeSpecificPart.orEmpty()
            val name = extractTelecomDisplayName(call) ?: num.ifBlank { "Caller" }
            CallParticipant(
                id = call.hashCode().toString(),
                name = name,
                number = num,
                state = stName,
                isHolding = (stateInt == Call.STATE_HOLDING)
            )
        }

        CallStateManager.updateConferenceParticipants(participantList)

        val activeCalls = snapshot.filter { readCallState(it) == Call.STATE_ACTIVE }
        val holdingCalls = snapshot.filter { readCallState(it) == Call.STATE_HOLDING }
        val hasConferenceChildren = snapshot.any { runCatching { it.children.isNotEmpty() }.getOrDefault(false) }
        val hasConferenceable = snapshot.any { runCatching { it.conferenceableCalls.isNotEmpty() }.getOrDefault(false) }
        val canMerge = !hasConferenceChildren &&
            ((activeCalls.isNotEmpty() && holdingCalls.isNotEmpty()) || hasConferenceable)

        activeTelecomCall = resolvePrimaryCall()
        CallStateManager.updateConferenceInfo(
            participantCount = distinctParticipants.size,
            isMerged = hasConferenceChildren || distinctParticipants.size > 1,
            canMerge = canMerge
        )
        CallStateManager.setHoldState(holdingCalls.isNotEmpty() && activeCalls.isEmpty())
    }

    private fun handleSingleCallDisconnected(call: Call?) {
        if (call != null) {
            runCatching { call.unregisterCallback(callCallback) }
            trackedCalls.remove(call)
        }
        val remainingCalls = liveCallsSnapshot()
        if (remainingCalls.isEmpty()) {
            handleCallDisconnectedOrRemoved(call)
        } else {
            val primary = resolvePrimaryCall()
            activeTelecomCall = primary
            refreshMultiCallState()
            if (primary != null) {
                when (readCallState(primary)) {
                    Call.STATE_ACTIVE -> CallStateManager.onCallAnswered()
                    Call.STATE_HOLDING -> CallStateManager.setHoldState(true)
                }
            }
            CallStateManager.setCallStatusBanner("Returned to remaining call")
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            CallStateManager.registerTelecomBridge(this@LyraInCallService)
            val telecomCall = call ?: resolvePrimaryCall() ?: activeTelecomCall
            when (state) {
                Call.STATE_SELECT_PHONE_ACCOUNT -> Unit
                Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    activeTelecomCall = resolvePrimaryCall() ?: telecomCall
                    if (liveCallsSnapshot().size <= 1 && telecomCall != null) {
                        handleOutgoingOrConnectedTransition(telecomCall, isAlreadyActive = false)
                    } else {
                        refreshMultiCallState()
                        startCallForegroundService()
                        if (canBringCallUiToFront() && !CallStateManager.isUserMinimizedCallUi.value) {
                            launchCallUi("ACTION_OUTGOING_CALL")
                        }
                    }
                }
                Call.STATE_HOLDING -> {
                    refreshMultiCallState()
                }
                Call.STATE_ACTIVE -> {
                    activeTelecomCall = call ?: resolvePrimaryCall() ?: telecomCall
                    val uiState = CallStateManager.callState.value
                    val wasRingingOrDialing = uiState == CallStateEnum.RINGING || uiState == CallStateEnum.DIALING
                    if ((uiState == CallStateEnum.IDLE || uiState == CallStateEnum.DISCONNECTED) &&
                        telecomCall != null
                    ) {
                        handleOutgoingOrConnectedTransition(telecomCall, isAlreadyActive = true)
                    } else {
                        CallStateManager.onCallAnswered()
                        if (CallStateManager.currentCaller.value == null && telecomCall != null) {
                            CallStateManager.updateCurrentCaller(
                                buildPreliminaryCaller(telecomCall, isIncoming = false),
                                simLabel = resolveAccountLabel(telecomCall)
                            )
                            resolveCallerAsync(
                                telecomCall,
                                setOf(CallStateEnum.ACTIVE, CallStateEnum.VOICEMAIL)
                            )
                        }
                        refreshMultiCallState()
                        syncCurrentAudioStateToManager()
                        startCallForegroundService()
                        // Open CallActivity on pickup/transition unless user explicitly minimized back to app
                        if (wasRingingOrDialing && !CallStateManager.isUserMinimizedCallUi.value) {
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

        override fun onParentChanged(call: Call?, parent: Call?) {
            super.onParentChanged(call, parent)
            refreshMultiCallState()
        }

        override fun onCallDestroyed(call: Call?) {
            super.onCallDestroyed(call)
            if (call != null) {
                trackedCalls.remove(call)
            }
            refreshMultiCallState()
        }

        override fun onConferenceableCallsChanged(call: Call?, conferenceableCalls: MutableList<Call>?) {
            super.onConferenceableCallsChanged(call, conferenceableCalls)
            refreshMultiCallState()
        }

        override fun onDetailsChanged(call: Call?, details: Call.Details?) {
            super.onDetailsChanged(call, details)
            val target = call ?: return
            if (target == resolvePrimaryCall()) {
                val state = readCallState(target)
                if (state == Call.STATE_RINGING || state == Call.STATE_DIALING || state == Call.STATE_ACTIVE) {
                    val updatedName = extractTelecomDisplayName(target)
                    if (!updatedName.isNullOrBlank()) {
                        val existing = CallStateManager.currentCaller.value
                        if (existing == null || existing.displayName != updatedName) {
                            resolveCallerAsync(
                                target,
                                setOf(CallStateEnum.RINGING, CallStateEnum.DIALING, CallStateEnum.ACTIVE, CallStateEnum.VOICEMAIL)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallStateManager.bindApplicationContext(applicationContext)
        CallStateManager.registerTelecomBridge(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        CallStateManager.unregisterTelecomBridgeIfMatches(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        CallStateManager.unregisterTelecomBridgeIfMatches(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        syncCallsState()
        if (CallStateManager.callState.value == CallStateEnum.IDLE && !hasLiveCalls()) {
            return
        }
        CallStateManager.setUserMinimizedCallUi(false)
        val action = if (CallStateManager.callState.value == CallStateEnum.RINGING) {
            "ACTION_INCOMING_CALL"
        } else {
            NotificationHelper.ACTION_RETURN_TO_CALL
        }
        launchCallUi(action)
    }

    @Suppress("DEPRECATION")
    private fun endpointTypeToRoute(endpointType: Int): Int {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when (endpointType) {
                CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
                CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
                else -> CallAudioState.ROUTE_EARPIECE
            }
            else -> CallAudioState.ROUTE_EARPIECE
        }
    }

    @Suppress("DEPRECATION")
    private fun syncCurrentAudioStateToManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && currentCallEndpoint != null) {
            val endpoint = currentCallEndpoint ?: return
            val route = endpointTypeToRoute(endpoint.endpointType)
            var supportedMask = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
            availableCallEndpoints.forEach { ep ->
                supportedMask = supportedMask or endpointTypeToRoute(ep.endpointType)
            }
            CallStateManager.syncAudioState(
                muted = lastTelecomMutedState,
                speakerOn = (route and CallAudioState.ROUTE_SPEAKER) != 0,
                route = route,
                supportedRoutes = supportedMask,
                fromTelecom = true
            )
            return
        }

        @Suppress("DEPRECATION")
        val legacyAudioState = runCatching { callAudioState }.getOrNull()
        if (legacyAudioState != null) {
            syncLegacyAudioState(legacyAudioState)
        }
    }

    @Suppress("DEPRECATION")
    private fun syncLegacyAudioState(audioState: CallAudioState) {
        lastTelecomMutedState = audioState.isMuted
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

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState ?: return
        syncLegacyAudioState(audioState)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        currentCallEndpoint = callEndpoint
        syncCurrentAudioStateToManager()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        availableCallEndpoints = availableEndpoints.toList()
        syncCurrentAudioStateToManager()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        lastTelecomMutedState = isMuted
        syncCurrentAudioStateToManager()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallStateManager.bindApplicationContext(applicationContext)
        CallStateManager.registerTelecomBridge(this)

        if (!trackedCalls.contains(call)) {
            trackedCalls.add(call)
        }
        call.registerCallback(callCallback, mainHandler)

        val currentState = readCallState(call)
        val liveCount = liveCallsSnapshot().size
        val isPrimaryCall = activeTelecomCall == null || liveCount <= 1 ||
            currentState == Call.STATE_ACTIVE || currentState == Call.STATE_DIALING
        activeTelecomCall = resolvePrimaryCall() ?: call
        refreshMultiCallState()
        syncCurrentAudioStateToManager()

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
            val preliminaryCaller = buildPreliminaryCaller(call, isIncoming = true)
            val cachedSettings = LyraPreferences.getCachedIncomingSnapshotOrNull()
                ?: IncomingCallSettingsSnapshot()

            if (!cachedSettings.hasActiveAutoReplyMode) {
                CallStateManager.onIncomingCall(preliminaryCaller, simLabel = accountLabel)
                startCallForegroundService()
                NotificationHelper.dispatchIncomingCall(
                    context = applicationContext,
                    caller = preliminaryCaller,
                    fullscreenPreferenceEnabled = cachedSettings.isFullscreenIncomingEnabled,
                    incomingScreenEnabled = cachedSettings.isIncomingScreenEnabled,
                    backgroundCallingEnabled = cachedSettings.isBackgroundCallingEnabled,
                    persistentNotificationEnabled = cachedSettings.isPersistentNotificationEnabled,
                    showCallerPhoto = false
                )
            }

            serviceScope.launch {
                val autoReplyDecision = CallAutoReplyCoordinator.evaluateAndExecuteIfActive(
                    context = applicationContext,
                    incomingPhoneNumber = handle
                )
                if (autoReplyDecision.shouldCutCall) {
                    mainHandler.post {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                call.reject(Call.REJECT_REASON_DECLINED)
                            } else {
                                @Suppress("DEPRECATION")
                                call.reject(false, null)
                            }
                        }
                        if (liveCallsSnapshot().size <= 1) {
                            NotificationHelper.cancelAllCallNotifications(applicationContext)
                            CallStateManager.onCallEnded()
                        }
                    }
                    return@launch
                }

                val db = LyraDatabase.getDatabase(applicationContext)
                val prefs = LyraPreferences(applicationContext)
                val settings = prefs.getIncomingCallSettingsSnapshot()
                val repo = ContactsRepository(applicationContext, db.priorityContactDao(), prefs)
                val resolvedCaller = repo.resolveCaller(handle)
                if (cachedSettings.hasActiveAutoReplyMode && CallStateManager.callState.value != CallStateEnum.RINGING) {
                    CallStateManager.onIncomingCall(resolvedCaller, simLabel = accountLabel)
                    startCallForegroundService()
                }
                if (CallStateManager.callState.value == CallStateEnum.RINGING) {
                    CallStateManager.updateCurrentCaller(resolvedCaller, simLabel = accountLabel)
                    if (cachedSettings.hasActiveAutoReplyMode ||
                        resolvedCaller != preliminaryCaller ||
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
                handleOutgoingOrConnectedTransition(call, isAlreadyActive = false)
            }
            Call.STATE_ACTIVE -> {
                handleOutgoingOrConnectedTransition(call, isAlreadyActive = true)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        runCatching { call.unregisterCallback(callCallback) }
        trackedCalls.remove(call)
        val remaining = liveCallsSnapshot()
        if (remaining.isEmpty()) {
            handleCallDisconnectedOrRemoved(call)
        } else {
            activeTelecomCall = resolvePrimaryCall()
            refreshMultiCallState()
        }
    }

    override fun hasLiveCalls(): Boolean {
        return liveCallsSnapshot().isNotEmpty()
    }

    override fun syncCallsState() {
        CallStateManager.registerTelecomBridge(this)
        val primary = resolvePrimaryCall() ?: return
        activeTelecomCall = primary
        val telecomState = readCallState(primary)
        when (telecomState) {
            Call.STATE_RINGING -> {
                if (CallStateManager.callState.value != CallStateEnum.RINGING) {
                    val caller = CallStateManager.currentCaller.value
                        ?: buildPreliminaryCaller(primary, isIncoming = true)
                    CallStateManager.onIncomingCall(caller, simLabel = resolveAccountLabel(primary))
                }
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                handleOutgoingOrConnectedTransition(
                    call = primary,
                    isAlreadyActive = false,
                    launchUiIfPermitted = false
                )
            }
            Call.STATE_ACTIVE -> {
                handleOutgoingOrConnectedTransition(
                    call = primary,
                    isAlreadyActive = true,
                    launchUiIfPermitted = false
                )
            }
            Call.STATE_HOLDING -> {
                if (CallStateManager.callState.value == CallStateEnum.IDLE ||
                    CallStateManager.callState.value == CallStateEnum.DISCONNECTED
                ) {
                    handleOutgoingOrConnectedTransition(
                        call = primary,
                        isAlreadyActive = true,
                        launchUiIfPermitted = false
                    )
                }
                CallStateManager.setHoldState(true)
            }
        }
        refreshMultiCallState()
        syncCurrentAudioStateToManager()
    }

    override fun answerCall() {
        val ringingCall = liveCallsSnapshot().lastOrNull { readCallState(it) == Call.STATE_RINGING }
            ?: resolvePrimaryCall()
            ?: activeTelecomCall
            ?: return
        activeTelecomCall = ringingCall
        val videoState = runCatching { ringingCall.details?.videoState }.getOrNull()
            ?: VideoProfile.STATE_AUDIO_ONLY
        runCatching { ringingCall.answer(videoState) }
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
                        // SIP 603 Decline: tells carrier this is an explicit user decline
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
        val target = resolvePrimaryCall() ?: activeTelecomCall ?: return
        runCatching {
            if (hold) target.hold() else target.unhold()
        }
    }

    override fun applyMute(muted: Boolean) {
        lastTelecomMutedState = muted
        runCatching { setMuted(muted) }
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager
        runCatching { am?.isMicrophoneMute = muted }
    }

    override fun setSpeaker(speaker: Boolean) {
        @Suppress("DEPRECATION")
        val route = if (speaker) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        }
        setAudioRouteMode(route)
    }

    @Suppress("DEPRECATION")
    override fun setAudioRouteMode(route: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val desiredType = when {
                (route and CallAudioState.ROUTE_SPEAKER) != 0 -> CallEndpoint.TYPE_SPEAKER
                (route and CallAudioState.ROUTE_BLUETOOTH) != 0 -> CallEndpoint.TYPE_BLUETOOTH
                (route and CallAudioState.ROUTE_WIRED_HEADSET) != 0 -> CallEndpoint.TYPE_WIRED_HEADSET
                else -> CallEndpoint.TYPE_EARPIECE
            }
            val matchingEndpoint = availableCallEndpoints.firstOrNull { it.endpointType == desiredType }
                ?: if (desiredType == CallEndpoint.TYPE_EARPIECE) {
                    availableCallEndpoints.firstOrNull {
                        it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET ||
                            it.endpointType == CallEndpoint.TYPE_EARPIECE
                    }
                } else {
                    null
                }
            if (matchingEndpoint != null) {
                requestCallEndpointChange(
                    matchingEndpoint,
                    ContextCompat.getMainExecutor(applicationContext),
                    object : OutcomeReceiver<Void, CallEndpointException> {
                        override fun onResult(result: Void?) {
                            currentCallEndpoint = matchingEndpoint
                            syncCurrentAudioStateToManager()
                        }

                        override fun onError(error: CallEndpointException) {
                            applyLegacyAndAudioManagerRoute(route)
                        }
                    }
                )
                return
            }
        }
        applyLegacyAndAudioManagerRoute(route)
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyAndAudioManagerRoute(route: Int) {
        runCatching { setAudioRoute(route) }
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        val wantSpeaker = (route and CallAudioState.ROUTE_SPEAKER) != 0
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetTypes = when {
                    wantSpeaker -> listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    (route and CallAudioState.ROUTE_BLUETOOTH) != 0 -> listOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET
                    )
                    (route and CallAudioState.ROUTE_WIRED_HEADSET) != 0 -> listOf(
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    )
                    else -> listOf(
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    )
                }
                val device = am.availableCommunicationDevices.firstOrNull { it.type in targetTypes }
                if (device != null) {
                    am.setCommunicationDevice(device)
                } else {
                    am.clearCommunicationDevice()
                }
            }
            am.isSpeakerphoneOn = wantSpeaker
        }
    }

    override fun mergeConference() {
        val primary = resolvePrimaryCall() ?: activeTelecomCall ?: return
        val conferenceTarget = runCatching { primary.conferenceableCalls.firstOrNull() }.getOrNull()
            ?: liveCallsSnapshot().firstOrNull { it != primary }
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
        val target = resolvePrimaryCall() ?: activeTelecomCall ?: return
        runCatching {
            target.playDtmfTone(digit)
            target.stopDtmfTone()
        }
    }

    override fun disconnectParticipant(participantId: String) {
        val allCalls = trackedCalls.toList() + listOfNotNull(activeTelecomCall)
        val target = allCalls.firstOrNull { it.hashCode().toString() == participantId }
            ?: liveCallsSnapshot().firstOrNull { call ->
                call.hashCode().toString() == participantId ||
                    runCatching { call.children.any { c -> c.hashCode().toString() == participantId } }.getOrDefault(false)
            }
        if (target != null) {
            val child = runCatching { target.children.firstOrNull { it.hashCode().toString() == participantId } }.getOrNull()
            val callToDisconnect = child ?: target
            runCatching { callToDisconnect.disconnect() }
        }
    }

    override fun holdParticipant(participantId: String, hold: Boolean) {
        val allCalls = trackedCalls.toList() + listOfNotNull(activeTelecomCall)
        val target = allCalls.firstOrNull { it.hashCode().toString() == participantId }
            ?: liveCallsSnapshot().firstOrNull { it.hashCode().toString() == participantId }
        if (target != null) {
            runCatching {
                if (hold) target.hold() else target.unhold()
            }
        }
    }
}
