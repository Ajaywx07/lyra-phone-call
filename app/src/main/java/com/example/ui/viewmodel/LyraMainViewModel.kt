package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.VoiceChangerEngine
import com.example.audio.VoiceMode
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.AutoReplyMode
import com.example.data.local.entity.CallNote
import com.example.data.local.entity.CallRecording
import com.example.data.local.entity.CallSummary
import com.example.data.local.entity.CustomFeatureSettings
import com.example.data.local.entity.ScheduledCall
import com.example.data.local.entity.VoicemailEntry
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.AutoReplyRepository
import com.example.data.repository.CallRecordingRepository
import com.example.domain.model.CallRecordingCapability
import com.example.domain.model.RecordingPlaybackState
import com.example.service.CallRecordingCoordinator
import com.example.service.LyraAccessibilityService
import com.example.utils.CallRecordingPlayer
import com.example.service.ScheduledCallScheduler
import com.example.data.remote.CloudContactBackup
import com.example.data.repository.CallLogRepository
import com.example.data.repository.CallRepository
import com.example.data.repository.CloudBackupRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.LocalRuleBasedAiRepository
import com.example.data.repository.UniversalVoicemailAiRepository
import com.example.domain.model.AiEngineConfig
import com.example.domain.model.AiProvider
import com.example.domain.model.CallAnimationStyle
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallScreenStyle
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.domain.model.FrequentContactItem
import com.example.domain.model.UnreadMissedCall
import com.example.domain.model.UssdUiState
import com.example.domain.model.VoicemailPhase
import com.example.service.CallStateManager
import com.example.service.CallParticipant
import com.example.service.NotificationHelper
import com.example.service.QuickSmsDispatcher
import com.example.service.QuickSmsService
import com.example.service.SmsDispatchHelper
import com.example.ui.theme.AppThemeStyle
import com.example.utils.AudioRecorderManager
import com.example.utils.DialpadAudioFeedbackManager
import com.example.utils.ExternalIntentHelper
import com.example.utils.GitHubReleaseInfo
import com.example.utils.GitHubUpdateManager
import com.example.utils.GoogleAuthHelper
import com.example.utils.GoogleSignInOutcome
import com.example.utils.MediaCropTransform
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import com.example.utils.ProfileMediaStorage
import com.example.utils.SimManager
import com.example.utils.SimSlotInfo
import com.example.utils.UpdateCheckResult
import com.example.utils.VoicemailEngine
import com.example.utils.ensureFirebaseInitialized
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeStats(
    val callsToday: Int = 0,
    val missedCalls: Int = 0,
    val knownCallers: Int = 0,
    val unknownCallers: Int = 0
)

data class PersistedAiSettings(
    val provider: AiProvider = AiProvider.FIREBASE_AI,
    val modelName: String = LyraPreferences.DEFAULT_AI_MODEL,
    val apiKey: String = "",
    val customBaseUrl: String = LyraPreferences.DEFAULT_AI_BASE_URL,
    val voicemailGreeting: String = LyraPreferences.DEFAULT_VOICEMAIL_GREETING,
    val isLoaded: Boolean = false
)

data class PermissionsState(
    val phoneAllowed: Boolean = false,
    val phoneNumbersAllowed: Boolean = false,
    val answerCallsAllowed: Boolean = false,
    val contactsAllowed: Boolean = false,
    val writeContactsAllowed: Boolean = false,
    val callLogAllowed: Boolean = false,
    val writeCallLogAllowed: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val microphoneAllowed: Boolean = false,
    val bluetoothAllowed: Boolean = false,
    val overlayAllowed: Boolean = false,
    val dndAllowed: Boolean = false,
    val fullScreenIntentAllowed: Boolean = false,
    val exactAlarmAllowed: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val sendSmsAllowed: Boolean = false,
    val isDefaultDialer: Boolean = false,
    val isCallScreener: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val deviceOwnerActive: Boolean = false,
    val accessibilityEnabled: Boolean = false
)

internal fun computeCurrentMonthStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    return Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun computeCurrentMonthKey(nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    return String.format(Locale.US, "%04d-%02d", year, month)
}

internal fun computeHomeStats(
    callLogs: List<CallHistoryItemData>,
    contacts: List<ContactItemData>,
    monthlyAutoResetEnabled: Boolean,
    nowMillis: Long = System.currentTimeMillis()
): HomeStats {
    val realContacts = contacts.filterNot { it.isDemoData }
    val realLogs = callLogs.filterNot { it.isDemoData }
    val cycleStartMillis = if (monthlyAutoResetEnabled) computeCurrentMonthStartMillis(nowMillis) else Long.MIN_VALUE
    val cycleLogs = realLogs.filter { it.timestamp >= cycleStartMillis }

    val missed = cycleLogs.count { it.callType == CallTypeEnum.MISSED }
    val unknown = cycleLogs.count { it.contactName.isNullOrBlank() || it.contactName == "Unknown Caller" }
    return HomeStats(
        callsToday = cycleLogs.size,
        missedCalls = missed,
        knownCallers = realContacts.size,
        unknownCallers = unknown
    )
}

internal fun computeMostFrequentContacts(
    callLogs: List<CallHistoryItemData>,
    contacts: List<ContactItemData>,
    limit: Int = 20,
    cycleStartMillis: Long? = null
): List<FrequentContactItem> {
    if (callLogs.isEmpty() || limit <= 0) return emptyList()
    val realContacts = contacts.filterNot { it.isDemoData }
    val contactsByNormalized = realContacts.associateBy {
        PhoneNumberUtils.normalize(it.phoneNumber).ifBlank { it.phoneNumber.trim() }
    }
    data class Acc(
        val rawNumber: String,
        val normalizedNumber: String,
        var count: Int = 0,
        var totalDuration: Long = 0L,
        var lastTimestamp: Long = 0L,
        var fallbackName: String? = null,
        var photoUri: String? = null,
        var isPriority: Boolean = false,
        var isOwner: Boolean = false
    )
    val grouped = LinkedHashMap<String, Acc>()
    for (entry in callLogs) {
        if (entry.isDemoData) continue
        if (cycleStartMillis != null && entry.timestamp < cycleStartMillis) continue
        val isConnectedType = entry.callType == CallTypeEnum.INCOMING || entry.callType == CallTypeEnum.OUTGOING
        if (!isConnectedType || entry.durationSeconds <= 0L) continue

        val key = PhoneNumberUtils.normalize(entry.phoneNumber).ifBlank { entry.phoneNumber.trim() }
        if (key.isBlank()) continue

        val current = grouped[key] ?: Acc(
            rawNumber = entry.phoneNumber,
            normalizedNumber = key,
            fallbackName = entry.contactName?.takeIf { it.isNotBlank() && it != "Unknown Caller" },
            photoUri = entry.photoUri,
            isPriority = entry.isPriority,
            isOwner = entry.isOwner
        ).also { grouped[key] = it }

        current.count += 1
        current.totalDuration += entry.durationSeconds
        if (entry.timestamp > current.lastTimestamp) {
            current.lastTimestamp = entry.timestamp
        }
        if (current.fallbackName.isNullOrBlank() &&
            !entry.contactName.isNullOrBlank() &&
            entry.contactName != "Unknown Caller"
        ) {
            current.fallbackName = entry.contactName
        }
        if (current.photoUri.isNullOrBlank() && !entry.photoUri.isNullOrBlank()) {
            current.photoUri = entry.photoUri
        }
        if (entry.isPriority) current.isPriority = true
        if (entry.isOwner) current.isOwner = true
    }

    return grouped.values
        .sortedWith(
            compareByDescending<Acc> { it.count }
                .thenByDescending { it.totalDuration }
                .thenByDescending { it.lastTimestamp }
        )
        .take(limit)
        .map { acc ->
            val matched = contactsByNormalized[acc.normalizedNumber]
            val formattedFallback = PhoneNumberUtils.formatDisplay(acc.rawNumber).ifBlank { acc.rawNumber }
            FrequentContactItem(
                phoneNumber = matched?.phoneNumber ?: acc.rawNumber,
                normalizedNumber = acc.normalizedNumber,
                displayName = matched?.name?.takeIf { it.isNotBlank() }
                    ?: acc.fallbackName
                    ?: formattedFallback,
                photoUri = matched?.photoUri ?: acc.photoUri,
                connectedCallCount = acc.count,
                totalDurationSeconds = acc.totalDuration,
                lastConnectedTimestamp = acc.lastTimestamp,
                isPriority = matched?.isPriority ?: acc.isPriority,
                isOwner = matched?.isOwner ?: acc.isOwner
            )
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LyraMainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val database = LyraDatabase.getDatabase(context)
    val preferences = LyraPreferences(context)
    private val aiRepository = LocalRuleBasedAiRepository()
    private val voicemailAiRepository = UniversalVoicemailAiRepository(context)
    private val voicemailRecorder = AudioRecorderManager(context)
    private val voicemailEngine = VoicemailEngine(context)
    private val dialpadAudioFeedbackManager = DialpadAudioFeedbackManager(context)
    private val voiceChangerEngine = VoiceChangerEngine(context)
    private val voicemailDao = database.voicemailDao()
    private val scheduledCallDao = database.scheduledCallDao()
    private var voicemailJob: Job? = null
    private var secondaryDialConnectJob: Job? = null
    private var loadDataJob: Job? = null
    @Volatile
    private var lastLoadedAtMs: Long = 0L

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioDeviceAvailability()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioDeviceAvailability()
        }
    }

    val contactsRepository = ContactsRepository(context, database.priorityContactDao(), preferences)
    val callLogRepository = CallLogRepository(context, contactsRepository)
    val callRepository = CallRepository(
        database.callNoteDao(),
        database.callSummaryDao(),
        database.priorityContactDao(),
        aiRepository
    )
    val callRecordingRepository = CallRecordingRepository(database.callRecordingDao())
    val autoReplyRepository = AutoReplyRepository(
        database.autoReplyModeDao(),
        database.customFeatureSettingsDao()
    )
    private val callRecordingPlayer = CallRecordingPlayer(viewModelScope)

    val allCallRecordings: StateFlow<List<CallRecording>> = callRecordingRepository.allRecordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _callRecordingCapability = MutableStateFlow(
        callRecordingRepository.getCapability()
    )
    val callRecordingCapability: StateFlow<CallRecordingCapability> = _callRecordingCapability.asStateFlow()

    fun refreshRecordingCapability(sdkInt: Int = android.os.Build.VERSION.SDK_INT) {
        _callRecordingCapability.value = callRecordingRepository.getCapability(sdkInt)
    }

    val recordingPlaybackState: StateFlow<RecordingPlaybackState> = callRecordingPlayer.playbackState

    private val _recordingActionMessage = MutableStateFlow<String?>(null)
    val recordingActionMessage: StateFlow<String?> = _recordingActionMessage.asStateFlow()

    // Permanent SQLite-backed Auto-Reply Modes (24 pre-fed modes) & Custom Feature Settings
    val autoReplyModes: StateFlow<List<AutoReplyMode>> = autoReplyRepository.allModes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customFeatureSettings: StateFlow<CustomFeatureSettings> = autoReplyRepository.customFeatureSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomFeatureSettings())

    val showLyraLogoOnIncoming: StateFlow<Boolean> = customFeatureSettings
        .map { it.showLyraLogoOnIncoming }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val accessibilityAutomationEnabled: StateFlow<Boolean> = customFeatureSettings
        .map { it.accessibilityAutomationEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val ussdDirectHandlerEnabled: StateFlow<Boolean> = customFeatureSettings
        .map { it.ussdDirectHandlerEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showMostFrequentContacts: StateFlow<Boolean> = customFeatureSettings
        .map { it.showMostFrequentContacts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val monthlyStatsAutoResetEnabled: StateFlow<Boolean> = customFeatureSettings
        .map { it.monthlyStatsAutoResetEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val keypadToneEnabled: StateFlow<Boolean> = customFeatureSettings
        .map { it.keypadToneEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val keypadVoiceAnnouncementEnabled: StateFlow<Boolean> = customFeatureSettings
        .map { it.keypadVoiceAnnouncementEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isVoiceChangerEnabled: StateFlow<Boolean> = preferences.isVoiceChangerEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val voiceMode: StateFlow<VoiceMode> = voiceChangerEngine.currentMode
    val isVoiceChangerPipelineActive: StateFlow<Boolean> = voiceChangerEngine.isPipelineActive
    val voiceChangerStatus: StateFlow<String?> = voiceChangerEngine.statusMessage

    private val _cycleReferenceNowMillis = MutableStateFlow(System.currentTimeMillis())

    val currentStatsCycleMonthKey: StateFlow<String> = _cycleReferenceNowMillis
        .map { computeCurrentMonthKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeCurrentMonthKey())

    val autoReplyMasterEnabled: StateFlow<Boolean> = customFeatureSettings
        .map { it.autoReplyMasterEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeAutoReplyMode: StateFlow<AutoReplyMode?> = combine(
        autoReplyModes,
        customFeatureSettings
    ) { modes, settings ->
        if (!settings.autoReplyMasterEnabled) null
        else modes.firstOrNull { it.modeId == settings.activeAutoReplyModeId }
            ?: modes.firstOrNull { it.isActive }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val accessibilityServiceConnected: StateFlow<Boolean> = LyraAccessibilityService.isServiceConnected
    val accessibilityAutomationStatus: StateFlow<String?> = LyraAccessibilityService.lastAutomationStatus

    private val _ussdState = MutableStateFlow<UssdUiState>(UssdUiState.Idle)
    val ussdState: StateFlow<UssdUiState> = _ussdState.asStateFlow()

    private val isFirebaseInitialized: Boolean
        get() = ensureFirebaseInitialized(context)

    private val cloudBackupRepository: CloudBackupRepository? by lazy {
        if (isFirebaseInitialized) {
            runCatching { CloudBackupRepository(context) }.getOrNull()
        } else {
            null
        }
    }

    // Call State from Manager
    val callState: StateFlow<CallStateEnum> = CallStateManager.callState
    val currentCaller: StateFlow<CallerInfo?> = CallStateManager.currentCaller
    val isOutgoingCall: StateFlow<Boolean> = CallStateManager.isOutgoingCall
    val activeSimLabel: StateFlow<String> = CallStateManager.activeSimLabel
    val callDurationSeconds: StateFlow<Long> = CallStateManager.callDurationSeconds
    val isMuted: StateFlow<Boolean> = CallStateManager.isMuted
    val isSpeakerOn: StateFlow<Boolean> = CallStateManager.isSpeakerOn
    val isHold: StateFlow<Boolean> = CallStateManager.isHold
    val isRingerSilenced: StateFlow<Boolean> = CallStateManager.isRingerSilenced
    val isSimulatedCall: StateFlow<Boolean> = CallStateManager.isSimulatedCall
    val currentAudioRoute: StateFlow<Int> = CallStateManager.currentAudioRoute
    val supportedAudioRoutes: StateFlow<Int> = CallStateManager.supportedAudioRoutes
    val isBluetoothAvailable: StateFlow<Boolean> = CallStateManager.isBluetoothAvailable
    val conferenceParticipantCount: StateFlow<Int> = CallStateManager.conferenceParticipantCount
    val canMergeConference: StateFlow<Boolean> = CallStateManager.canMergeConference
    val isConferenceMerged: StateFlow<Boolean> = CallStateManager.isConferenceMerged
    val conferenceParticipants: StateFlow<List<CallParticipant>> = CallStateManager.conferenceParticipants
    val callStatusBanner: StateFlow<String?> = CallStateManager.callStatusBanner
    val currentCallNotes: StateFlow<String> = CallStateManager.currentCallNotes
    val lastSummary: StateFlow<CallSummary?> = CallStateManager.lastSummary

    // Permissions State (populated asynchronously off the main thread in loadData)
    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    // Dual-SIM State
    private val _availableSims = MutableStateFlow<List<SimSlotInfo>>(emptyList())
    val availableSims: StateFlow<List<SimSlotInfo>> = _availableSims.asStateFlow()

    // Contacts
    private val _contacts = MutableStateFlow<List<ContactItemData>>(emptyList())
    val contacts: StateFlow<List<ContactItemData>> = _contacts.asStateFlow()

    private val _contactsSearchQuery = MutableStateFlow("")
    val contactsSearchQuery: StateFlow<String> = _contactsSearchQuery.asStateFlow()

    val filteredContacts: StateFlow<List<ContactItemData>> = combine(
        _contacts,
        _contactsSearchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.phoneNumber.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedContact = MutableStateFlow<ContactItemData?>(null)
    val selectedContact: StateFlow<ContactItemData?> = _selectedContact.asStateFlow()

    private val _prefilledDialNumber = MutableStateFlow<String?>(null)
    val prefilledDialNumber: StateFlow<String?> = _prefilledDialNumber.asStateFlow()

    fun setPrefilledDialNumber(number: String?) {
        val normalized = number?.trim().orEmpty()
        if (normalized.isNotBlank()) {
            _prefilledDialNumber.value = normalized
        }
    }

    fun consumePrefilledDialNumber(): String? {
        val current = _prefilledDialNumber.value
        _prefilledDialNumber.value = null
        return current
    }

    // Call History
    private val _callLog = MutableStateFlow<List<CallHistoryItemData>>(emptyList())
    val callLog: StateFlow<List<CallHistoryItemData>> = _callLog.asStateFlow()

    val mostFrequentContacts: StateFlow<List<FrequentContactItem>> = combine(
        _callLog,
        _contacts,
        showMostFrequentContacts,
        monthlyStatsAutoResetEnabled,
        _cycleReferenceNowMillis
    ) { logs, contactsList, enabled, autoResetEnabled, nowMillis ->
        if (!enabled) {
            emptyList()
        } else {
            val cycleStart = if (autoResetEnabled) computeCurrentMonthStartMillis(nowMillis) else null
            computeMostFrequentContacts(
                callLogs = logs,
                contacts = contactsList,
                limit = 20,
                cycleStartMillis = cycleStart
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _callLogFilter = MutableStateFlow<CallTypeEnum?>(null)
    val callLogFilter: StateFlow<CallTypeEnum?> = _callLogFilter.asStateFlow()

    val filteredCallLog: StateFlow<List<CallHistoryItemData>> = combine(
        _callLog,
        _callLogFilter
    ) { list, filter ->
        when (filter) {
            null -> list
            CallTypeEnum.MISSED -> list.filter {
                it.callType == CallTypeEnum.MISSED || it.callType == CallTypeEnum.REJECTED
            }
            else -> list.filter { it.callType == filter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unread Missed Calls Tracking
    val unreadMissedCalls: StateFlow<List<UnreadMissedCall>> = preferences.unreadMissedCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadMissedCallCount: StateFlow<Int> = unreadMissedCalls
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markMissedCallsAsRead() {
        NotificationHelper.clearMissedCallNotification(context)
        viewModelScope.launch {
            preferences.markAllMissedCallsAsRead()
            callLogRepository.markSystemMissedCallsAsRead()
        }
    }

    // Home Stats (real calls & real contacts, with automatic 1st-of-month 00:00 cycle reset when enabled)
    val homeStats: StateFlow<HomeStats> = combine(
        _callLog,
        _contacts,
        monthlyStatsAutoResetEnabled,
        _cycleReferenceNowMillis
    ) { logs, contactsList, autoResetEnabled, nowMillis ->
        computeHomeStats(
            callLogs = logs,
            contacts = contactsList,
            monthlyAutoResetEnabled = autoResetEnabled,
            nowMillis = nowMillis
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStats())

    // Notes & Summaries
    val allNotes: StateFlow<List<CallNote>> = callRepository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSummaries: StateFlow<List<CallSummary>> = callRepository.allSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Preferences Flows
    val isLyraEnabled: StateFlow<Boolean> = preferences.isLyraEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val ownerName: StateFlow<String> = preferences.ownerName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ajay")

    val ownerPhone: StateFlow<String> = preferences.ownerPhone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "+91 98765 43210")

    val isCallerIdEnabled: StateFlow<Boolean> = preferences.isCallerIdEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isUnknownCallerDetectionEnabled: StateFlow<Boolean> = preferences.isUnknownCallerDetectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isCallSummaryEnabled: StateFlow<Boolean> = preferences.isCallSummaryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isCallNotesEnabled: StateFlow<Boolean> = preferences.isCallNotesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isVoiceGreetingEnabled: StateFlow<Boolean> = preferences.isVoiceGreetingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isLocalOnlyMode: StateFlow<Boolean> = preferences.isLocalOnlyMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isFullscreenIncomingEnabled: StateFlow<Boolean> = preferences.isFullscreenIncomingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isIncomingScreenEnabled: StateFlow<Boolean> = preferences.isIncomingScreenEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isPersistentNotificationEnabled: StateFlow<Boolean> = preferences.isPersistentNotificationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isBackgroundCallingEnabled: StateFlow<Boolean> = preferences.isBackgroundCallingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isCallVibrationEnabled: StateFlow<Boolean> = preferences.isCallVibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val ringtoneUri: StateFlow<String> = preferences.ringtoneUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val ringtoneTitle: StateFlow<String> = preferences.ringtoneTitle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System Default")

    val showContactPhotoOnCall: StateFlow<Boolean> = preferences.showContactPhotoOnCall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockUnknownCalls: StateFlow<Boolean> = preferences.blockUnknownCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val blockCommercialPrefixes: StateFlow<Boolean> = preferences.blockCommercialPrefixes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val callScreenStyle: StateFlow<CallScreenStyle> = preferences.callScreenStyle
        .map { CallScreenStyle.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CallScreenStyle.LYRA_AI)

    val callAnimationStyle: StateFlow<CallAnimationStyle> = preferences.callAnimationStyle
        .map { CallAnimationStyle.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CallAnimationStyle.ORB_PULSE)

    val preferredSim: StateFlow<String> = preferences.preferredSim
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SimManager.SIM_ASK)

    val fixDefaultSim: StateFlow<Boolean> = preferences.fixDefaultSim
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appTheme: StateFlow<AppThemeStyle> = preferences.themeMode
        .map { AppThemeStyle.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeStyle.IOS_GLASS)

    val autoSendQuickSms: StateFlow<Boolean> = preferences.autoSendQuickSms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val quickSmsStatus: StateFlow<String?> = QuickSmsDispatcher.statusMessage

    // Universal AI Settings & Voicemail Preferences
    val aiProvider: StateFlow<AiProvider> = preferences.aiProvider
        .map { AiProvider.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProvider.FIREBASE_AI)

    val aiModelName: StateFlow<String> = preferences.aiModelName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyraPreferences.DEFAULT_AI_MODEL)

    val aiApiKey: StateFlow<String> = preferences.aiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val aiCustomBaseUrl: StateFlow<String> = preferences.aiCustomBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyraPreferences.DEFAULT_AI_BASE_URL)

    val voicemailGreeting: StateFlow<String> = preferences.voicemailGreeting
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyraPreferences.DEFAULT_VOICEMAIL_GREETING)

    val voicemailMaxSeconds: StateFlow<Int> = preferences.voicemailMaxSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyraPreferences.DEFAULT_VOICEMAIL_MAX_SECONDS)

    val dashboardMediaUri: StateFlow<String?> = preferences.dashboardMediaUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dashboardMediaType: StateFlow<String> = preferences.dashboardMediaType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ORB")

    val dashboardMediaCrop: StateFlow<MediaCropTransform> = preferences.dashboardMediaCrop
        .map { MediaCropTransform.decode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaCropTransform())

    val persistedAiSettings: StateFlow<PersistedAiSettings> = preferences.storedAiSettings
        .map { stored ->
            PersistedAiSettings(
                provider = AiProvider.fromKey(stored.provider),
                modelName = stored.modelName,
                apiKey = stored.apiKey,
                customBaseUrl = stored.customBaseUrl,
                voicemailGreeting = stored.voicemailGreeting,
                isLoaded = true
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersistedAiSettings(isLoaded = false))

    private val _aiConnectionStatus = MutableStateFlow<String?>(null)
    val aiConnectionStatus: StateFlow<String?> = _aiConnectionStatus.asStateFlow()

    private val _isTestingAiConnection = MutableStateFlow(false)
    val isTestingAiConnection: StateFlow<Boolean> = _isTestingAiConnection.asStateFlow()

    // Voicemail State & Local Inbox
    val allVoicemails: StateFlow<List<VoicemailEntry>> = voicemailDao.getAllVoicemails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Scheduled Automatic Calls
    val allScheduledCalls: StateFlow<List<ScheduledCall>> = scheduledCallDao.getAllScheduledCalls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _voicemailPhase = MutableStateFlow(VoicemailPhase.IDLE)
    val voicemailPhase: StateFlow<VoicemailPhase> = _voicemailPhase.asStateFlow()

    val voicemailAmplitude: StateFlow<Float> = voicemailRecorder.amplitude
    val voicemailElapsedSeconds: StateFlow<Long> = voicemailRecorder.elapsedSeconds
    val playingVoicemailId: StateFlow<Long?> = voicemailEngine.playingVoicemailId

    private val _activeVoicemailEntry = MutableStateFlow<VoicemailEntry?>(null)
    val activeVoicemailEntry: StateFlow<VoicemailEntry?> = _activeVoicemailEntry.asStateFlow()

    private val _voicemailError = MutableStateFlow<String?>(null)
    val voicemailError: StateFlow<String?> = _voicemailError.asStateFlow()

    // Google Auth State (strictly auth-gated before touching Firestore)
    private val authUserFlow: Flow<FirebaseUser?> = if (isFirebaseInitialized) {
        callbackFlow {
            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                trySend(firebaseAuth.currentUser)
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }
    } else {
        flowOf(null)
    }

    val currentUser: StateFlow<FirebaseUser?> = authUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Auth-gated realtime Cloud Contacts count and list (strictly suppressed in Local-Only Operation Mode)
    val cloudContacts: StateFlow<List<CloudContactBackup>> = combine(currentUser, isLocalOnlyMode) { user, localOnly ->
        user to localOnly
    }
        .flatMapLatest { (user, localOnly) ->
            val repo = cloudBackupRepository
            if (localOnly || user == null || repo == null) {
                flowOf(emptyList())
            } else {
                repo.observeCloudContacts(user.uid)
                    .catch { emit(emptyList()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    // Backup & Restore status message
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    // GitHub Releases App Update State
    val githubUpdateRepo: StateFlow<String> = preferences.githubUpdateRepo
        .stateIn(viewModelScope, SharingStarted.Eagerly, LyraPreferences.DEFAULT_GITHUB_UPDATE_REPO)

    val skippedUpdateVersion: StateFlow<String> = preferences.skippedUpdateVersion
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _availableUpdate = MutableStateFlow<GitHubReleaseInfo?>(null)
    val availableUpdate: StateFlow<GitHubReleaseInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _updateCheckStatus = MutableStateFlow<String?>(null)
    val updateCheckStatus: StateFlow<String?> = _updateCheckStatus.asStateFlow()

    private var hasRunStartupUpdateCheck = false

    init {
        loadData()
        refreshAudioDeviceAvailability()
        runCatching {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        }
        viewModelScope.launch {
            runCatching { ScheduledCallScheduler.rescheduleAllPending(context) }
        }
        viewModelScope.launch {
            runCatching {
                autoReplyRepository.ensureSeeded()
                val persisted = autoReplyRepository.getSettingsOnce()
                preferences.setShowLyraLogoOnIncoming(persisted.showLyraLogoOnIncoming)
                preferences.setAccessibilityAutomationEnabled(persisted.accessibilityAutomationEnabled)
                preferences.setUssdDirectHandlerEnabled(persisted.ussdDirectHandlerEnabled)
                preferences.setShowMostFrequentContacts(persisted.showMostFrequentContacts)
                preferences.setAutoReplyState(persisted.autoReplyMasterEnabled, persisted.activeAutoReplyModeId)
                preferences.setMonthlyStatsAutoResetEnabled(persisted.monthlyStatsAutoResetEnabled)
                preferences.setKeypadToneEnabled(persisted.keypadToneEnabled)
                preferences.setKeypadVoiceAnnouncementEnabled(persisted.keypadVoiceAnnouncementEnabled)
                if (persisted.keypadVoiceAnnouncementEnabled) {
                    dialpadAudioFeedbackManager.warmUpVoice()
                }
                checkAndRenewMonthlyCycleIfNeeded()
            }
        }
        viewModelScope.launch {
            CallStateManager.callState.collect { state ->
                if (state == CallStateEnum.IDLE || state == CallStateEnum.DISCONNECTED) {
                    voiceChangerEngine.stopSession(resetMode = true)
                }
            }
        }
    }

    private suspend fun checkAndRenewMonthlyCycleIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        val currentMonthKey = computeCurrentMonthKey(nowMillis)
        _cycleReferenceNowMillis.value = nowMillis
        val settings = autoReplyRepository.getSettingsOnce()
        if (settings.monthlyStatsAutoResetEnabled && settings.lastStatsResetMonthKey != currentMonthKey) {
            autoReplyRepository.updateFeatureSettings { it.copy(lastStatsResetMonthKey = currentMonthKey) }
            preferences.setLastStatsResetMonthKey(currentMonthKey)
        }
    }

    fun checkForAppUpdatesIfStartupPending() {
        if (hasRunStartupUpdateCheck) return
        hasRunStartupUpdateCheck = true
        checkForAppUpdates(manual = false)
    }

    fun checkForAppUpdates(manual: Boolean = true) {
        if (_isCheckingUpdates.value) return
        val repoSlug = githubUpdateRepo.value.trim()
        if (repoSlug.isBlank()) {
            if (manual) _updateCheckStatus.value = "Enter a GitHub repository (owner/repo) first."
            return
        }
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            if (manual) _updateCheckStatus.value = "Checking GitHub Releases..."
            when (val result = GitHubUpdateManager.checkLatestRelease(repoSlug)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    val skipped = skippedUpdateVersion.value
                    val alreadySkipped = GitHubUpdateManager.isSameVersion(skipped, result.release.tagName)
                    if (manual || !alreadySkipped) {
                        _availableUpdate.value = result.release
                    }
                    _updateCheckStatus.value = "Update ${result.release.tagName} available!"
                }
                is UpdateCheckResult.UpToDate -> {
                    if (manual) {
                        _updateCheckStatus.value = "Lyra v${result.currentVersion} is up to date ✓"
                    }
                }
                is UpdateCheckResult.Error -> {
                    if (manual) {
                        _updateCheckStatus.value = result.message
                    }
                }
            }
            _isCheckingUpdates.value = false
        }
    }

    fun setGitHubUpdateRepo(repoSlug: String) {
        viewModelScope.launch {
            preferences.setGithubUpdateRepo(repoSlug)
            _updateCheckStatus.value = "Update repository saved ✓"
        }
    }

    fun skipUpdateVersion(tagName: String) {
        viewModelScope.launch {
            preferences.setSkippedUpdateVersion(tagName)
            _availableUpdate.value = null
        }
    }

    fun dismissUpdatePrompt() {
        _availableUpdate.value = null
    }

    fun openAvailableUpdate(release: GitHubReleaseInfo): Boolean {
        val opened = GitHubUpdateManager.openReleaseInBrowser(context, release)
        _availableUpdate.value = null
        if (!opened) {
            _updateCheckStatus.value = "Could not open browser for ${release.htmlUrl}"
        }
        return opened
    }

    fun refreshAudioDeviceAvailability() {
        val devices = runCatching {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        }.getOrDefault(emptyArray())
        val hasBluetoothAudio = devices.any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
                AudioDeviceInfo.TYPE_HEARING_AID -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                AudioDeviceInfo.TYPE_BLE_HEADSET -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                else -> false
            }
        }
        CallStateManager.setSystemBluetoothConnected(hasBluetoothAudio)
    }

    @JvmOverloads
    fun loadData(force: Boolean = true) {
        if (!force && loadDataJob?.isActive == true) return
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            runCatching { checkAndRenewMonthlyCycleIfNeeded() }
            val perms = withContext(Dispatchers.IO) { checkPermissions() }
            _permissionsState.value = perms
            val sims = withContext(Dispatchers.IO) { SimManager.getAvailableSims(context) }
            _availableSims.value = sims
            val loadedContacts = contactsRepository.getContacts()
            _contacts.value = loadedContacts
            _callLog.value = callLogRepository.getCallLog(preloadedContacts = loadedContacts)
            lastLoadedAtMs = android.os.SystemClock.elapsedRealtime()
        }
    }

    fun refreshIfStale(minIntervalMs: Long = 8_000L) {
        viewModelScope.launch { runCatching { checkAndRenewMonthlyCycleIfNeeded() } }
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastLoadedAtMs
        if (lastLoadedAtMs == 0L || elapsed >= minIntervalMs) {
            loadData(force = false)
        } else {
            viewModelScope.launch {
                _permissionsState.value = withContext(Dispatchers.IO) { checkPermissions() }
            }
        }
    }

    fun checkPermissions(): PermissionsState {
        return PermissionsState(
            phoneAllowed = PermissionHelper.hasPhonePermission(context) &&
                PermissionHelper.hasCallPhonePermission(context),
            phoneNumbersAllowed = PermissionHelper.hasPhoneNumbersPermission(context),
            answerCallsAllowed = PermissionHelper.hasAnswerCallsPermission(context),
            contactsAllowed = PermissionHelper.hasContactsPermission(context),
            writeContactsAllowed = PermissionHelper.hasWriteContactsPermission(context),
            callLogAllowed = PermissionHelper.hasCallLogPermission(context),
            writeCallLogAllowed = PermissionHelper.hasWriteCallLogPermission(context),
            notificationsAllowed = PermissionHelper.hasNotificationPermission(context),
            microphoneAllowed = PermissionHelper.hasMicrophonePermission(context),
            bluetoothAllowed = PermissionHelper.hasBluetoothPermission(context),
            overlayAllowed = PermissionHelper.hasOverlayPermission(context),
            dndAllowed = PermissionHelper.hasDndAccess(context),
            fullScreenIntentAllowed = PermissionHelper.canUseFullScreenIntent(context),
            exactAlarmAllowed = PermissionHelper.canScheduleExactAlarms(context),
            batteryOptimizationIgnored = PermissionHelper.isIgnoringBatteryOptimizations(context),
            sendSmsAllowed = PermissionHelper.hasSendSmsPermission(context),
            isDefaultDialer = PermissionHelper.isDefaultDialer(context),
            isCallScreener = PermissionHelper.isCallScreener(context),
            deviceAdminActive = PermissionHelper.isDeviceAdminActive(context),
            deviceOwnerActive = PermissionHelper.isDeviceOwner(context),
            accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
        )
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            _permissionsState.value = withContext(Dispatchers.IO) { checkPermissions() }
        }
    }

    fun deactivateDeviceAdmin(): Boolean {
        val removed = PermissionHelper.removeDeviceAdmin(context)
        refreshPermissions()
        return removed
    }

    fun setContactsSearchQuery(query: String) {
        _contactsSearchQuery.value = query
    }

    fun setCallLogFilter(filter: CallTypeEnum?) {
        _callLogFilter.value = filter
    }

    fun selectContact(contact: ContactItemData) {
        _selectedContact.value = contact
    }

    fun toggleContactPriority(contact: ContactItemData) {
        viewModelScope.launch {
            contactsRepository.togglePriorityContact(contact)
            val updatedList = contactsRepository.getContacts()
            _contacts.value = updatedList
            _selectedContact.value?.let { current ->
                if (current.id == contact.id ||
                    PhoneNumberUtils.areNumbersEqual(current.phoneNumber, contact.phoneNumber)
                ) {
                    _selectedContact.value = updatedList.firstOrNull {
                        it.id == contact.id || PhoneNumberUtils.areNumbersEqual(it.phoneNumber, contact.phoneNumber)
                    } ?: current.copy(isPriority = !current.isPriority)
                }
            }
        }
    }

    fun updateContact(
        original: ContactItemData,
        newName: String,
        newPhone: String,
        newPhotoUri: String?,
        isPriority: Boolean
    ) {
        if (newName.isBlank() || newPhone.isBlank()) return
        viewModelScope.launch {
            val updated = contactsRepository.updateContact(
                original = original,
                newName = newName,
                newPhoneNumber = newPhone,
                newPhotoUri = newPhotoUri,
                isPriority = isPriority
            )
            _selectedContact.value = updated
            val reloadedContacts = contactsRepository.getContacts()
            _contacts.value = reloadedContacts
            _callLog.value = callLogRepository.getCallLog(preloadedContacts = reloadedContacts)
        }
    }

    fun deleteCallLogEntry(id: String) {
        viewModelScope.launch {
            callLogRepository.deleteCallLogEntry(id)
            _callLog.value = callLogRepository.getCallLog(preloadedContacts = _contacts.value)
        }
    }

    fun deleteContact(contact: ContactItemData) {
        viewModelScope.launch {
            contactsRepository.deleteContact(contact)
            _contacts.value = contactsRepository.getContacts()
            _callLog.value = callLogRepository.getCallLog()
            if (_selectedContact.value?.id == contact.id ||
                (_selectedContact.value != null &&
                    PhoneNumberUtils.areNumbersEqual(_selectedContact.value!!.phoneNumber, contact.phoneNumber))
            ) {
                _selectedContact.value = null
            }
        }
    }

    fun saveCallNote(callerNumber: String, callerName: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            callRepository.saveCallNote(callerNumber, callerName, text)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            callRepository.deleteNoteById(id)
        }
    }

    // Call Control Actions
    fun acceptCall() {
        CallStateManager.acceptActiveCall()
    }

    fun declineCall() {
        CallStateManager.declineOrEndActiveCall(callRepository)
    }

    fun endCall() {
        CallStateManager.declineOrEndActiveCall(callRepository)
    }

    fun cancelDialingCall() {
        CallStateManager.cancelDialingOrUnconnectedCall()
    }

    fun toggleMute() = CallStateManager.toggleMute()
    fun toggleSpeaker() = CallStateManager.toggleSpeaker()
    fun toggleHold() = CallStateManager.toggleHold()
    fun silenceRinger() = CallStateManager.silenceRinger()
    fun selectAudioRoute(route: Int) = CallStateManager.selectAudioRoute(route)
    fun mergeConferenceCalls() = CallStateManager.mergeConferenceCalls()
    fun disconnectParticipant(participantId: String) = CallStateManager.disconnectParticipant(participantId)
    fun toggleHoldParticipant(participantId: String, hold: Boolean) = CallStateManager.toggleHoldParticipant(participantId, hold)
    fun clearCallStatusBanner() = CallStateManager.setCallStatusBanner(null)
    fun sendDtmf(digit: Char) = CallStateManager.sendDtmfTone(digit)
    fun updateInCallNote(note: String) = CallStateManager.updateNotes(note)

    @SuppressLint("MissingPermission")
    fun addCallParticipant(phoneNumber: String) {
        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber)
        if (cleanNumber.isBlank()) return

        secondaryDialConnectJob?.cancel()

        if (!isHold.value) {
            CallStateManager.toggleHold()
        }

        val matchedContact = _contacts.value.firstOrNull {
            PhoneNumberUtils.areNumbersEqual(it.phoneNumber, cleanNumber)
        }
        val displayLabel = matchedContact?.name?.takeIf { it.isNotBlank() }
            ?: PhoneNumberUtils.formatDisplay(cleanNumber)

        val canPlaceDirectly = !isSimulatedCall.value &&
            (PermissionHelper.hasCallPhonePermission(context) ||
                PermissionHelper.isDefaultDialer(context))
        if (canPlaceDirectly && CallStateManager.hasTelecomBridge()) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val uri = Uri.fromParts("tel", cleanNumber, null)
            val placed = runCatching {
                telecomManager?.placeCall(uri, Bundle())
                true
            }.getOrDefault(false)
            if (placed) {
                CallStateManager.registerSecondaryParticipant(displayLabel, connectedImmediately = false)
                return
            }
        }

        // Preview / simulated fallback: show dialing banner first, then mark 2nd call connected so 'Add Call' transitions to 'Merge'
        CallStateManager.registerSecondaryParticipant(displayLabel, connectedImmediately = false)
        secondaryDialConnectJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1400L)
            if (CallStateManager.isOngoingCall() && !CallStateManager.isConferenceMerged.value) {
                CallStateManager.markSecondaryCallConnected(displayLabel)
            }
        }
    }

    fun resetCallState() {
        secondaryDialConnectJob?.cancel()
        CallStateManager.clearState()
    }

    /**
     * Simulate an incoming phone call to preview call screen styles and animations.
     */
    fun simulateIncomingCall(
        name: String = "BOSS",
        number: String = "8795989222",
        isOwner: Boolean = true,
        isPriority: Boolean = true
    ) {
        val simLabel = if (preferredSim.value == SimManager.SIM_2) "SIM 2" else "SIM 1"
        val caller = CallerInfo(
            phoneNumber = number,
            formattedNumber = PhoneNumberUtils.formatDisplay(number),
            displayName = name,
            isKnown = true,
            isPriority = isPriority,
            isOwner = isOwner,
            contactLabel = if (isOwner) "Owner / Primary Device" else "Mobile"
        )
        CallStateManager.onIncomingCall(caller, simLabel = simLabel, isSimulated = true)
    }

    fun simulateUnknownCall(
        name: String = "Unknown \"Mine\"",
        number: String = "8073469203"
    ) {
        val simLabel = if (preferredSim.value == SimManager.SIM_2) "SIM 2" else "SIM 1"
        val caller = CallerInfo(
            phoneNumber = number,
            formattedNumber = PhoneNumberUtils.formatDisplay(number),
            displayName = name,
            isKnown = false,
            isPriority = false,
            isOwner = false,
            contactLabel = name
        )
        CallStateManager.onIncomingCall(caller, simLabel = simLabel, isSimulated = true)
    }

    fun simulateOutgoingCall(
        phoneNumber: String = "+91 98111 22334",
        simKeyOverride: String? = null
    ) {
        viewModelScope.launch {
            val effectiveSim = if (fixDefaultSim.value) preferredSim.value else (simKeyOverride ?: preferredSim.value)
            val simLabel = SimManager.displayLabelForSimKey(context, effectiveSim)
                .let { if (it == "Ask Every Time") "SIM 1" else it }
            val caller = contactsRepository.resolveCaller(phoneNumber)
            CallStateManager.onOutgoingCall(caller, simLabel = simLabel, isSimulated = true)
        }
    }

    /**
     * Places an outgoing call using Android TelecomManager (supporting SIM 1 / SIM 2 PhoneAccountHandle)
     * when permissions / default dialer role are granted, or opens Android Dialer fallback.
     */
    @SuppressLint("MissingPermission")
    fun placeCall(
        phoneNumber: String,
        simKeyOverride: String? = null,
        @Suppress("UNUSED_PARAMETER")
        onNavigateToOutgoingCall: () -> Unit = {}
    ) {
        val effectiveSim = when {
            fixDefaultSim.value && preferredSim.value != SimManager.SIM_ASK -> preferredSim.value
            simKeyOverride != null && simKeyOverride != SimManager.SIM_ASK -> simKeyOverride
            preferredSim.value != SimManager.SIM_ASK -> preferredSim.value
            else -> SimManager.SIM_ASK
        }

        val trimmedRaw = phoneNumber.trim()
        if (ExternalIntentHelper.isUssdCode(trimmedRaw)) {
            if (ussdDirectHandlerEnabled.value) {
                executeUssdCode(trimmedRaw, effectiveSim)
            } else {
                ExternalIntentHelper.dialUssdFallback(context, trimmedRaw)
            }
            return
        }

        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber)
        if (cleanNumber.isBlank()) return

        val handle = SimManager.resolveHandleForSimKey(context, effectiveSim)

        val canPlaceDirectly = PermissionHelper.hasCallPhonePermission(context) ||
            PermissionHelper.isDefaultDialer(context)

        if (canPlaceDirectly) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val uri = Uri.fromParts("tel", cleanNumber, null)
            val extras = Bundle().apply {
                if (handle != null) {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
            }
            val placed = runCatching {
                telecomManager?.placeCall(uri, extras)
                true
            }.getOrDefault(false)

            if (placed) {
                // Do not optimistically show ActiveCallScreen here:
                // LyraInCallService will enter CallStateEnum.DIALING & launch ActiveCallScreen
                // only once SIM selection finishes and Android Telecom transitions the call to DIALING/ACTIVE.
                return
            }
        }

        // Fallback to system dialer when CALL_PHONE / Default Dialer isn't granted yet
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(dialIntent) }
    }

    fun executeUssdCode(ussdCode: String, simKey: String = preferredSim.value) {
        val simLabel = SimManager.displayLabelForSimKey(context, simKey)
            .let { if (it == "Ask Every Time") "Default SIM" else it }
        _ussdState.value = UssdUiState.Loading(ussdCode = ussdCode, simLabel = simLabel)
        ExternalIntentHelper.executeUssdRequest(
            context = context,
            ussdCode = ussdCode,
            simSlotPreference = simKey
        ) { result ->
            result.onSuccess { msg ->
                _ussdState.value = UssdUiState.Success(
                    ussdCode = ussdCode,
                    responseMessage = msg,
                    simLabel = simLabel
                )
            }.onFailure { err ->
                _ussdState.value = UssdUiState.Error(
                    ussdCode = ussdCode,
                    errorMessage = err.localizedMessage ?: "Failed to execute USSD code.",
                    simLabel = simLabel
                )
            }
        }
    }

    fun clearUssdState() {
        _ussdState.value = UssdUiState.Idle
    }

    // Legacy compatibility method
    fun initiateCall(phoneNumber: String) = placeCall(phoneNumber)

    fun addContactFromDialer(name: String, phoneNumber: String, isPriority: Boolean = false) {
        viewModelScope.launch {
            contactsRepository.addCustomContact(name, phoneNumber, isPriority)
            loadData()
        }
    }

    fun startOutgoingCallWithLyra(
        phoneNumber: String,
        simKeyOverride: String? = null,
        onNavigateToActiveCall: () -> Unit
    ) {
        viewModelScope.launch {
            val effectiveSim = if (fixDefaultSim.value) preferredSim.value else (simKeyOverride ?: preferredSim.value)
            val simLabel = if (effectiveSim == SimManager.SIM_ASK) {
                "SIM 1"
            } else {
                SimManager.displayLabelForSimKey(context, effectiveSim)
            }
            val caller = contactsRepository.resolveCaller(phoneNumber)
            CallStateManager.onOutgoingCall(caller, simLabel = simLabel, isSimulated = true)
            onNavigateToActiveCall()
        }
    }

    fun initiateSms(phoneNumber: String, body: String = "") {
        SmsDispatchHelper.launchSmsComposer(context, phoneNumber, body)
    }

    fun sendQuickResponseSms(
        phoneNumber: String,
        message: String,
        autoSendOverride: Boolean? = null
    ) {
        val shouldAutoSend = autoSendOverride ?: autoSendQuickSms.value
        if (shouldAutoSend && PermissionHelper.hasSendSmsPermission(context)) {
            QuickSmsService.startSendSms(context, phoneNumber, message)
        } else {
            if (shouldAutoSend && !PermissionHelper.hasSendSmsPermission(context)) {
                QuickSmsDispatcher.emitStatus("SMS permission needed for auto-send; opened SMS composer.")
            }
            SmsDispatchHelper.launchSmsComposer(context, phoneNumber, message)
        }
    }

    fun clearQuickSmsStatus() = QuickSmsDispatcher.emitStatus(null)

    // Settings Updates
    fun setLyraEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setLyraEnabled(enabled) }
    fun setOwnerName(name: String) = viewModelScope.launch { preferences.setOwnerName(name); loadData() }
    fun setOwnerPhone(phone: String) = viewModelScope.launch { preferences.setOwnerPhone(phone); loadData() }
    fun setCallerIdEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setCallerIdEnabled(enabled) }
    fun setUnknownCallerDetection(enabled: Boolean) = viewModelScope.launch { preferences.setUnknownCallerDetection(enabled) }
    fun setCallSummaryEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setCallSummaryEnabled(enabled) }
    fun setCallNotesEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setCallNotesEnabled(enabled) }
    fun setVoiceGreetingEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setVoiceGreetingEnabled(enabled) }
    fun setLocalOnlyMode(enabled: Boolean) = viewModelScope.launch { preferences.setLocalOnlyMode(enabled) }
    fun setFullscreenIncoming(enabled: Boolean) = viewModelScope.launch { preferences.setFullscreenIncoming(enabled) }
    fun setIncomingScreenEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setIncomingScreenEnabled(enabled) }
    fun setPersistentNotificationEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setPersistentNotificationEnabled(enabled) }
    fun setBackgroundCallingEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setBackgroundCallingEnabled(enabled) }
    fun setCallVibrationEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setCallVibrationEnabled(enabled) }
    fun setRingtone(uri: String, title: String) = viewModelScope.launch { preferences.setRingtone(uri, title) }
    fun setShowContactPhotoOnCall(enabled: Boolean) = viewModelScope.launch { preferences.setShowContactPhotoOnCall(enabled) }
    fun setBlockUnknownCalls(enabled: Boolean) = viewModelScope.launch { preferences.setBlockUnknownCalls(enabled) }
    fun setBlockCommercialPrefixes(enabled: Boolean) = viewModelScope.launch { preferences.setBlockCommercialPrefixes(enabled) }
    fun setCallScreenStyle(style: CallScreenStyle) = viewModelScope.launch { preferences.setCallScreenStyle(style.key) }
    fun setCallAnimationStyle(animation: CallAnimationStyle) = viewModelScope.launch { preferences.setCallAnimationStyle(animation.key) }
    fun setPreferredSim(simKey: String) = viewModelScope.launch { preferences.setPreferredSim(simKey) }
    fun setFixDefaultSim(fixed: Boolean) = viewModelScope.launch { preferences.setFixDefaultSim(fixed) }
    fun setAppTheme(style: AppThemeStyle) = viewModelScope.launch { preferences.setThemeMode(style.key) }
    fun setAutoSendQuickSms(enabled: Boolean) = viewModelScope.launch { preferences.setAutoSendQuickSms(enabled) }

    fun setShowLyraLogoOnIncoming(enabled: Boolean) = viewModelScope.launch {
        autoReplyRepository.updateFeatureSettings { it.copy(showLyraLogoOnIncoming = enabled) }
        preferences.setShowLyraLogoOnIncoming(enabled)
    }

    fun setAccessibilityAutomationEnabled(enabled: Boolean) = viewModelScope.launch {
        autoReplyRepository.updateFeatureSettings { it.copy(accessibilityAutomationEnabled = enabled) }
        preferences.setAccessibilityAutomationEnabled(enabled)
        refreshPermissions()
    }

    fun setUssdDirectHandlerEnabled(enabled: Boolean) = viewModelScope.launch {
        autoReplyRepository.updateFeatureSettings { it.copy(ussdDirectHandlerEnabled = enabled) }
        preferences.setUssdDirectHandlerEnabled(enabled)
    }

    fun setShowMostFrequentContacts(enabled: Boolean) = viewModelScope.launch {
        autoReplyRepository.updateFeatureSettings { it.copy(showMostFrequentContacts = enabled) }
        preferences.setShowMostFrequentContacts(enabled)
    }

    fun setMonthlyStatsAutoResetEnabled(enabled: Boolean) = viewModelScope.launch {
        val nowMillis = System.currentTimeMillis()
        val monthKey = computeCurrentMonthKey(nowMillis)
        _cycleReferenceNowMillis.value = nowMillis
        autoReplyRepository.updateFeatureSettings {
            it.copy(
                monthlyStatsAutoResetEnabled = enabled,
                lastStatsResetMonthKey = if (enabled) monthKey else it.lastStatsResetMonthKey
            )
        }
        preferences.setMonthlyStatsAutoResetEnabled(enabled)
        if (enabled) {
            preferences.setLastStatsResetMonthKey(monthKey)
        }
    }

    fun setKeypadToneEnabled(enabled: Boolean) = viewModelScope.launch {
        autoReplyRepository.updateFeatureSettings { it.copy(keypadToneEnabled = enabled) }
        preferences.setKeypadToneEnabled(enabled)
    }

    fun setKeypadVoiceAnnouncementEnabled(enabled: Boolean) = viewModelScope.launch {
        autoReplyRepository.updateFeatureSettings { it.copy(keypadVoiceAnnouncementEnabled = enabled) }
        preferences.setKeypadVoiceAnnouncementEnabled(enabled)
        if (enabled) {
            dialpadAudioFeedbackManager.warmUpVoice()
        }
    }

    fun onDialpadKeyFeedback(digit: String) {
        dialpadAudioFeedbackManager.playDigitFeedback(
            digit = digit,
            playTone = keypadToneEnabled.value,
            speakDigit = keypadVoiceAnnouncementEnabled.value
        )
    }

    fun setVoiceChangerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setVoiceChangerEnabled(enabled)
        }
        if (!enabled) {
            voiceChangerEngine.stopSession(resetMode = true)
        }
    }

    fun selectVoiceMode(mode: VoiceMode) {
        if (!isVoiceChangerEnabled.value) {
            voiceChangerEngine.stopSession(resetMode = true)
            return
        }
        val nextMode = if (voiceMode.value == mode && mode != VoiceMode.NONE) {
            VoiceMode.NONE
        } else {
            mode
        }
        val callOngoing = callState.value == CallStateEnum.DIALING ||
            callState.value == CallStateEnum.ACTIVE
        voiceChangerEngine.setMode(nextMode, isCallOngoing = callOngoing)
    }

    fun resetVoiceChangerToNormal() {
        voiceChangerEngine.stopSession(resetMode = true)
    }

    fun setAutoReplyMasterEnabled(enabled: Boolean) = viewModelScope.launch {
        val selected = customFeatureSettings.value.activeAutoReplyModeId.ifBlank { "driving" }
        autoReplyRepository.setMasterEnabled(enabled = enabled, selectedModeId = selected)
        preferences.setAutoReplyState(masterEnabled = enabled, activeModeId = selected)
    }

    fun selectAutoReplyMode(modeId: String) = viewModelScope.launch {
        autoReplyRepository.selectMode(modeId)
        preferences.setAutoReplyState(
            masterEnabled = customFeatureSettings.value.autoReplyMasterEnabled,
            activeModeId = modeId
        )
    }

    fun saveAutoReplyMessage(modeId: String, message: String) = viewModelScope.launch {
        autoReplyRepository.saveCustomMessage(modeId, message)
    }

    fun resetAutoReplyMessage(modeId: String) = viewModelScope.launch {
        autoReplyRepository.resetModeMessageToDefault(modeId)
    }

    fun applyCroppedDashboardImage(
        sourceBitmap: android.graphics.Bitmap,
        crop: MediaCropTransform,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val savedUri = ProfileMediaStorage.cropAndSaveImage(context, sourceBitmap, crop)
            if (!savedUri.isNullOrBlank()) {
                preferences.setDashboardMedia(savedUri, "IMAGE", crop.encode())
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun applyCroppedDashboardVideo(
        sourceUri: Uri,
        crop: MediaCropTransform,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val savedUri = ProfileMediaStorage.saveVideoToInternalStorage(context, sourceUri)
            if (!savedUri.isNullOrBlank()) {
                preferences.setDashboardMedia(savedUri, "VIDEO", crop.encode())
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun resetDashboardMedia() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ProfileMediaStorage.clearSavedMedia(context)
            preferences.setDashboardMedia(null, "ORB")
        }
    }

    // Google Sign-In & Cloud Backup
    fun signInWithGoogle(activityContext: Context) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            _backupStatus.value = "Opening Google Sign-In..."
            when (val outcome = GoogleAuthHelper.signInWithGoogle(activityContext)) {
                is GoogleSignInOutcome.Success -> {
                    _backupStatus.value = "Signed in as ${outcome.user.email ?: outcome.user.displayName ?: "Google User"} ✓"
                }
                is GoogleSignInOutcome.Cancelled -> {
                    _backupStatus.value = outcome.message
                }
                is GoogleSignInOutcome.Failure -> {
                    _backupStatus.value = outcome.message
                }
            }
            _isSigningIn.value = false
        }
    }

    fun signOutFromGoogle() {
        viewModelScope.launch {
            GoogleAuthHelper.signOut(context)
            _backupStatus.value = "Signed out from Google account"
        }
    }

    fun backupContactsToCloud() {
        if (isLocalOnlyMode.value) {
            _backupStatus.value = "Local-Only Operation Mode is enabled. Turn it off in Privacy settings to upload contacts."
            return
        }
        val user = currentUser.value
        val repo = cloudBackupRepository
        if (user == null) {
            _backupStatus.value = "Sign in with Google first to back up contacts."
            return
        }
        if (repo == null) {
            _backupStatus.value = "Cloud backup service unavailable."
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _backupStatus.value = "Backing up contacts to Google Cloud..."
            val list = contactsRepository.getContacts()
            val result = repo.backupContacts(user.uid, list)
            result.onSuccess { count ->
                _backupStatus.value = "Backed up $count contacts to ${user.email ?: "your Google Cloud"} ✓"
            }.onFailure { err ->
                _backupStatus.value = "Cloud backup failed: ${err.localizedMessage ?: "Permission or network error"}"
            }
            _isCloudSyncing.value = false
        }
    }

    fun restoreContactsFromCloud() {
        if (isLocalOnlyMode.value) {
            _backupStatus.value = "Local-Only Operation Mode is enabled. Turn it off in Privacy settings to restore from cloud."
            return
        }
        val user = currentUser.value
        val repo = cloudBackupRepository
        if (user == null) {
            _backupStatus.value = "Sign in with Google first to restore cloud contacts."
            return
        }
        if (repo == null) {
            _backupStatus.value = "Cloud restore service unavailable."
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _backupStatus.value = "Restoring contacts from Google Cloud..."
            val result = repo.getCloudContacts(user.uid)
            result.onSuccess { items ->
                var imported = 0
                for (item in items) {
                    if (item.name.isNotBlank() && item.phoneNumber.isNotBlank()) {
                        contactsRepository.addCustomContact(
                            name = item.name,
                            phoneNumber = item.phoneNumber,
                            isPriority = item.priorityContact
                        )
                        imported++
                    }
                }
                loadData()
                _backupStatus.value = "Restored $imported contacts from Google Cloud ✓"
            }.onFailure { err ->
                _backupStatus.value = "Cloud restore failed: ${err.localizedMessage ?: "Permission or network error"}"
            }
            _isCloudSyncing.value = false
        }
    }

    // Local JSON Backup & Restore
    fun exportBackup() {
        viewModelScope.launch {
            try {
                val json = contactsRepository.exportContactsBackup()
                _backupStatus.value = "Local backup generated (${json.length} bytes)"
            } catch (e: Exception) {
                _backupStatus.value = "Failed to create backup: ${e.message}"
            }
        }
    }

    fun restoreBackup(json: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = contactsRepository.restoreContactsBackup(json)
            loadData()
            onResult(count)
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    // Universal AI Engine Configuration (Post-Build Hot-Swappable in Settings)
    fun currentAiEngineConfig(): AiEngineConfig = AiEngineConfig(
        provider = aiProvider.value,
        modelName = aiModelName.value,
        apiKey = aiApiKey.value,
        customBaseUrl = aiCustomBaseUrl.value
    )

    fun saveAiEngineSettings(
        provider: AiProvider,
        modelName: String,
        apiKey: String,
        customBaseUrl: String,
        greetingScript: String
    ) {
        viewModelScope.launch {
            preferences.setAiEngineSettings(
                provider = provider.key,
                modelName = modelName,
                apiKey = apiKey,
                customBaseUrl = customBaseUrl
            )
            preferences.setVoicemailGreeting(greetingScript)
            _aiConnectionStatus.value = "Saved ${provider.displayName} • ${modelName.trim().ifBlank { LyraPreferences.DEFAULT_AI_MODEL }} ✓"
        }
    }

    fun testAiConnection(
        provider: AiProvider,
        modelName: String,
        apiKey: String,
        customBaseUrl: String
    ) {
        if (_isTestingAiConnection.value) return
        viewModelScope.launch {
            _isTestingAiConnection.value = true
            _aiConnectionStatus.value = "Testing ${provider.displayName}..."
            val config = AiEngineConfig(
                provider = provider,
                modelName = modelName.trim().ifBlank { LyraPreferences.DEFAULT_AI_MODEL },
                apiKey = apiKey.trim(),
                customBaseUrl = customBaseUrl.trim().ifBlank { LyraPreferences.DEFAULT_AI_BASE_URL }
            )
            val result = voicemailAiRepository.testConnection(config)
            _aiConnectionStatus.value = result.fold(
                onSuccess = { it },
                onFailure = { err -> "Test failed: ${err.localizedMessage ?: "Check model or API key"}" }
            )
            _isTestingAiConnection.value = false
        }
    }

    fun deleteAiApiKey() {
        viewModelScope.launch {
            preferences.clearAiApiKey()
            _aiConnectionStatus.value = "API key deleted from secure storage"
        }
    }

    fun clearAiConnectionStatus() {
        _aiConnectionStatus.value = null
    }

    // Scheduled Automatic Calling (Phase 13)
    fun scheduleCall(
        contactName: String,
        phoneNumber: String,
        simKey: String,
        scheduledAtMillis: Long
    ) {
        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber)
        if (cleanNumber.isBlank()) return
        val normalizedTime = ScheduledCallScheduler.normalizeToMinuteStart(scheduledAtMillis)
            .let { if (it <= System.currentTimeMillis()) scheduledAtMillis else it }
        viewModelScope.launch {
            val entity = ScheduledCall(
                contactName = contactName.ifBlank { cleanNumber },
                phoneNumber = cleanNumber,
                simKey = simKey,
                scheduledAtMillis = normalizedTime,
                status = "PENDING",
                statusMessage = "Arming alarm..."
            )
            val id = scheduledCallDao.insertScheduledCall(entity)
            val saved = entity.copy(id = id)
            val result = ScheduledCallScheduler.scheduleAlarm(context, saved)
            scheduledCallDao.updateScheduledCall(saved.copy(statusMessage = result.diagnosticMessage))
        }
    }

    fun updateScheduledCall(
        existing: ScheduledCall,
        contactName: String,
        phoneNumber: String,
        simKey: String,
        scheduledAtMillis: Long
    ) {
        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber)
        if (cleanNumber.isBlank()) return
        val normalizedTime = ScheduledCallScheduler.normalizeToMinuteStart(scheduledAtMillis)
            .let { if (it <= System.currentTimeMillis()) scheduledAtMillis else it }
        viewModelScope.launch {
            ScheduledCallScheduler.cancelAlarm(context, existing.id)
            val updated = existing.copy(
                contactName = contactName.ifBlank { cleanNumber },
                phoneNumber = cleanNumber,
                simKey = simKey,
                scheduledAtMillis = normalizedTime,
                status = "PENDING",
                statusMessage = "Updating alarm..."
            )
            val result = ScheduledCallScheduler.scheduleAlarm(context, updated)
            scheduledCallDao.updateScheduledCall(updated.copy(statusMessage = result.diagnosticMessage))
        }
    }

    fun cancelScheduledCall(call: ScheduledCall) {
        viewModelScope.launch {
            ScheduledCallScheduler.cancelAlarm(context, call.id)
            scheduledCallDao.updateScheduledCall(
                call.copy(
                    status = "CANCELLED",
                    statusMessage = "Cancelled by user"
                )
            )
        }
    }

    fun deleteScheduledCall(callId: Long) {
        viewModelScope.launch {
            ScheduledCallScheduler.cancelAlarm(context, callId)
            scheduledCallDao.deleteScheduledCallById(callId)
        }
    }

    // Voicemail Screening & Local Recording + AI Summary Flow
    fun startVoicemailScreening() {
        voicemailJob?.cancel()
        voicemailRecorder.cancelRecording()
        voicemailEngine.stopGreeting()
        voicemailEngine.stopPlayback()

        CallStateManager.routeToVoicemail()
        _activeVoicemailEntry.value = null
        _voicemailError.value = null

        val caller = currentCaller.value ?: CallerInfo(
            phoneNumber = "+91 98989 12345",
            formattedNumber = "+91 98989 12345",
            displayName = "Caller"
        )
        val greeting = voicemailGreeting.value.ifBlank { LyraPreferences.DEFAULT_VOICEMAIL_GREETING }
        val maxDurationSec = voicemailMaxSeconds.value.coerceIn(10, 120)

        voicemailJob = viewModelScope.launch {
            try {
                // Step 1: Play custom Lyra greeting over unmuted Speaker Acoustic Bridge
                CallStateManager.setVoicemailAcousticBridge(speakingGreeting = true)
                _voicemailPhase.value = VoicemailPhase.SPEAKING_GREETING
                voicemailEngine.speakGreeting(greeting)

                // Step 2: Play voicemail beep while still unmuted on speaker bridge
                _voicemailPhase.value = VoicemailPhase.PLAYING_BEEP
                voicemailEngine.playVoicemailBeep()

                // Step 3: Mute owner room mic, keep speaker ON, and record caller via VOICE_RECOGNITION source
                CallStateManager.setVoicemailAcousticBridge(speakingGreeting = false)
                _voicemailPhase.value = VoicemailPhase.RECORDING_MESSAGE
                voicemailRecorder.startRecording(
                    scope = viewModelScope,
                    preferVoiceRecognition = true,
                    onMaxLimitReached = { stopVoicemailRecordingAndSummarize() }
                )

                // Enforce configured max voicemail length cleanly
                kotlinx.coroutines.delay(maxDurationSec * 1000L)
                if (_voicemailPhase.value == VoicemailPhase.RECORDING_MESSAGE) {
                    stopVoicemailRecordingAndSummarize()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                CallStateManager.restoreAfterVoicemail()
                _voicemailPhase.value = VoicemailPhase.ERROR
                _voicemailError.value = e.localizedMessage
                    ?: "Microphone permission (RECORD_AUDIO) is required to record voicemail locally."
            } catch (e: Exception) {
                CallStateManager.restoreAfterVoicemail()
                _voicemailPhase.value = VoicemailPhase.ERROR
                _voicemailError.value = e.localizedMessage
                    ?: "Unable to start voicemail recording for ${caller.displayName}."
            }
        }
    }

    fun stopVoicemailRecordingAndSummarize() {
        val currentPhase = _voicemailPhase.value
        if (currentPhase != VoicemailPhase.RECORDING_MESSAGE &&
            currentPhase != VoicemailPhase.SPEAKING_GREETING &&
            currentPhase != VoicemailPhase.PLAYING_BEEP
        ) {
            return
        }

        voicemailJob?.cancel()
        voicemailEngine.stopGreeting()

        val durationSec = voicemailRecorder.elapsedSeconds.value
        val wavBytes = runCatching { voicemailRecorder.stopAndBuildWav() }.getOrElse { ByteArray(0) }

        // End active internal Telecom call cleanly once message is captured
        if (CallStateManager.callState.value == CallStateEnum.VOICEMAIL) {
            CallStateManager.declineOrEndActiveCall(null)
        }

        if (wavBytes.size <= 44) {
            _voicemailPhase.value = VoicemailPhase.ERROR
            _voicemailError.value = "No caller speech audio was captured after the beep."
            return
        }

        val caller = currentCaller.value ?: CallerInfo(
            phoneNumber = "+91 98989 12345",
            formattedNumber = "+91 98989 12345",
            displayName = "Caller"
        )

        viewModelScope.launch {
            val savedFile = voicemailRecorder.saveWavToInternalStorage(wavBytes, "vm")
            val config = currentAiEngineConfig()

            val initialEntry = VoicemailEntry(
                callerNumber = caller.formattedNumber.ifBlank { caller.phoneNumber },
                callerName = caller.displayName.ifBlank { "Unknown Caller" },
                audioFilePath = savedFile.absolutePath,
                durationSeconds = durationSec.coerceAtLeast(1L),
                modelUsed = config.modelName,
                providerUsed = config.provider.displayName,
                status = "PROCESSING"
            )
            val insertedId = voicemailDao.insertVoicemail(initialEntry)
            val storedEntry = initialEntry.copy(id = insertedId)
            _activeVoicemailEntry.value = storedEntry
            _voicemailPhase.value = VoicemailPhase.PROCESSING_AI

            analyzeAndPersistVoicemail(storedEntry, wavBytes, config)
        }
    }

    fun reanalyzeVoicemail(entry: VoicemailEntry) {
        val file = java.io.File(entry.audioFilePath)
        if (!file.exists()) {
            _voicemailPhase.value = VoicemailPhase.ERROR
            _voicemailError.value = "Local voicemail audio file no longer exists on device."
            return
        }

        viewModelScope.launch {
            val wavBytes = runCatching { file.readBytes() }.getOrElse { ByteArray(0) }
            if (wavBytes.size <= 44) {
                _voicemailPhase.value = VoicemailPhase.ERROR
                _voicemailError.value = "Local voicemail audio file is empty."
                return@launch
            }
            val config = currentAiEngineConfig()
            val processingEntry = entry.copy(
                modelUsed = config.modelName,
                providerUsed = config.provider.displayName,
                status = "PROCESSING",
                errorMessage = ""
            )
            voicemailDao.updateVoicemail(processingEntry)
            _activeVoicemailEntry.value = processingEntry
            _voicemailPhase.value = VoicemailPhase.PROCESSING_AI
            _voicemailError.value = null

            analyzeAndPersistVoicemail(processingEntry, wavBytes, config)
        }
    }

    private suspend fun analyzeAndPersistVoicemail(
        entry: VoicemailEntry,
        wavBytes: ByteArray,
        config: AiEngineConfig
    ) {
        val result = voicemailAiRepository.analyzeVoicemailAudio(
            wavBytes = wavBytes,
            callerName = entry.callerName,
            config = config
        )

        result.onSuccess { analysis ->
            val updated = entry.copy(
                transcript = analysis.transcript,
                summary = analysis.summary,
                keyPoints = analysis.keyPoints.joinToString("\n"),
                actionPrompt = analysis.actionPrompt,
                modelUsed = config.modelName,
                providerUsed = config.provider.displayName,
                status = "READY",
                errorMessage = ""
            )
            voicemailDao.updateVoicemail(updated)
            _activeVoicemailEntry.value = updated
            _voicemailPhase.value = VoicemailPhase.READY
            _voicemailError.value = null
        }.onFailure { err ->
            val message = err.localizedMessage ?: "AI processing failed for ${config.modelName}."
            val failedEntry = entry.copy(
                modelUsed = config.modelName,
                providerUsed = config.provider.displayName,
                status = "ERROR",
                errorMessage = message
            )
            voicemailDao.updateVoicemail(failedEntry)
            _activeVoicemailEntry.value = failedEntry
            _voicemailPhase.value = VoicemailPhase.ERROR
            _voicemailError.value = message
        }
    }

    fun selectVoicemailForViewing(entry: VoicemailEntry) {
        _activeVoicemailEntry.value = entry
        _voicemailError.value = entry.errorMessage.takeIf { it.isNotBlank() }
        _voicemailPhase.value = when (entry.status) {
            "READY" -> VoicemailPhase.READY
            "ERROR" -> VoicemailPhase.ERROR
            "PROCESSING" -> VoicemailPhase.PROCESSING_AI
            else -> VoicemailPhase.READY
        }
    }

    fun toggleVoicemailPlayback(entry: VoicemailEntry) {
        voicemailEngine.togglePlayback(entry.id, entry.audioFilePath)
    }

    fun stopVoicemailPlayback() {
        voicemailEngine.stopPlayback()
    }

    fun deleteVoicemail(entry: VoicemailEntry) {
        viewModelScope.launch {
            if (playingVoicemailId.value == entry.id) {
                voicemailEngine.stopPlayback()
            }
            runCatching { java.io.File(entry.audioFilePath).delete() }
            voicemailDao.deleteVoicemailById(entry.id)
            if (_activeVoicemailEntry.value?.id == entry.id) {
                _activeVoicemailEntry.value = null
                _voicemailPhase.value = VoicemailPhase.IDLE
            }
        }
    }

    fun cancelVoicemailScreening() {
        voicemailJob?.cancel()
        voicemailRecorder.cancelRecording()
        voicemailEngine.stopGreeting()
        voicemailEngine.stopPlayback()
        if (CallStateManager.callState.value == CallStateEnum.VOICEMAIL ||
            CallStateManager.callState.value == CallStateEnum.RINGING
        ) {
            CallStateManager.declineOrEndActiveCall(null)
        }
        CallStateManager.clearState()
        _voicemailPhase.value = VoicemailPhase.IDLE
        _voicemailError.value = null
    }

    fun playRecording(recording: CallRecording) {
        callRecordingPlayer.play(context, recording)
    }

    fun pauseRecording() {
        callRecordingPlayer.pause()
    }

    fun resumeRecording() {
        callRecordingPlayer.resume()
    }

    fun stopRecordingPlayback() {
        callRecordingPlayer.stop()
    }

    fun seekRecordingPlayback(positionMs: Long) {
        callRecordingPlayer.seekTo(positionMs)
    }

    fun deleteRecording(recording: CallRecording) {
        viewModelScope.launch {
            try {
                if (recordingPlaybackState.value.recordingId == recording.id) {
                    callRecordingPlayer.stop()
                }
                callRecordingRepository.deleteRecording(context, recording)
                _recordingActionMessage.value = "Recording deleted"
            } catch (e: Exception) {
                _recordingActionMessage.value = "Failed to delete recording: ${e.message}"
            }
        }
    }

    fun shareRecording(ctx: Context, recording: CallRecording) {
        try {
            val shareIntent = callRecordingRepository.createShareIntent(ctx, recording).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(Intent.createChooser(shareIntent, "Share Call Recording").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            _recordingActionMessage.value = "Unable to share recording"
        }
    }

    fun clearRecordingActionMessage() {
        _recordingActionMessage.value = null
    }

    fun startManualCallRecording(ctx: Context, callerName: String, callerNumber: String): Boolean {
        return CallRecordingCoordinator.startRecording(ctx, callerName, callerNumber)
    }

    fun stopManualCallRecording(ctx: Context) {
        viewModelScope.launch {
            val rec = CallRecordingCoordinator.stopRecording(ctx)
            if (rec != null) {
                _recordingActionMessage.value = "Recording saved: ${rec.fileName}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voicemailJob?.cancel()
        secondaryDialConnectJob?.cancel()
        runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
        voicemailRecorder.cancelRecording()
        voicemailEngine.release()
        dialpadAudioFeedbackManager.release()
        voiceChangerEngine.release()
        callRecordingPlayer.release()
    }
}
