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
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.CallNote
import com.example.data.local.entity.CallSummary
import com.example.data.local.entity.ScheduledCall
import com.example.data.local.entity.VoicemailEntry
import com.example.data.local.preferences.LyraPreferences
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
import com.example.domain.model.UnreadMissedCall
import com.example.domain.model.VoicemailPhase
import com.example.service.CallStateManager
import com.example.service.NotificationHelper
import com.example.service.QuickSmsDispatcher
import com.example.service.QuickSmsService
import com.example.service.SmsDispatchHelper
import com.example.ui.theme.AppThemeStyle
import com.example.utils.AudioRecorderManager
import com.example.utils.GoogleAuthHelper
import com.example.utils.GoogleSignInOutcome
import com.example.utils.MediaCropTransform
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import com.example.utils.ProfileMediaStorage
import com.example.utils.SimManager
import com.example.utils.SimSlotInfo
import com.example.utils.VoicemailEngine
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
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
    val isCallScreener: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class LyraMainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val database = LyraDatabase.getDatabase(context)
    val preferences = LyraPreferences(context)
    private val aiRepository = LocalRuleBasedAiRepository()
    private val voicemailAiRepository = UniversalVoicemailAiRepository()
    private val voicemailRecorder = AudioRecorderManager(context)
    private val inCallRecorder = AudioRecorderManager(context)
    private val voicemailEngine = VoicemailEngine(context)
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

    private val isFirebaseInitialized: Boolean
        get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

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
    val callStatusBanner: StateFlow<String?> = CallStateManager.callStatusBanner
    val isCallRecording: StateFlow<Boolean> = inCallRecorder.isRecording
    val callRecordingSeconds: StateFlow<Long> = inCallRecorder.elapsedSeconds
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

    // Home Stats
    val homeStats: StateFlow<HomeStats> = _callLog.combine(_contacts) { logs, contactsList ->
        val missed = logs.count { it.callType == CallTypeEnum.MISSED }
        val known = contactsList.size
        val unknown = logs.count { it.contactName == "Unknown Caller" || it.contactName == null }
        HomeStats(
            callsToday = logs.size,
            missedCalls = missed,
            knownCallers = known,
            unknownCallers = unknown
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

    init {
        loadData()
        refreshAudioDeviceAvailability()
        runCatching {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        }
        viewModelScope.launch {
            runCatching { ScheduledCallScheduler.rescheduleAllPending(context) }
        }
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
            isCallScreener = PermissionHelper.isCallScreener(context)
        )
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
        stopCallRecordingIfActive(save = true)
        CallStateManager.declineOrEndActiveCall(callRepository)
    }

    fun endCall() {
        stopCallRecordingIfActive(save = true)
        CallStateManager.declineOrEndActiveCall(callRepository)
    }

    fun cancelDialingCall() {
        stopCallRecordingIfActive(save = false)
        CallStateManager.cancelDialingOrUnconnectedCall()
    }

    fun toggleMute() = CallStateManager.toggleMute()
    fun toggleSpeaker() = CallStateManager.toggleSpeaker()
    fun toggleHold() = CallStateManager.toggleHold()
    fun silenceRinger() = CallStateManager.silenceRinger()
    fun selectAudioRoute(route: Int) = CallStateManager.selectAudioRoute(route)
    fun mergeConferenceCalls() = CallStateManager.mergeConferenceCalls()
    fun clearCallStatusBanner() = CallStateManager.setCallStatusBanner(null)
    fun sendDtmf(digit: Char) = CallStateManager.sendDtmfTone(digit)
    fun updateInCallNote(note: String) = CallStateManager.updateNotes(note)

    fun toggleCallRecording() {
        if (inCallRecorder.isRecording.value) {
            stopCallRecordingIfActive(save = true)
        } else {
            runCatching {
                inCallRecorder.startRecording(viewModelScope) {
                    stopCallRecordingIfActive(save = true)
                }
                CallStateManager.setCallStatusBanner("Recording call locally")
            }.onFailure { err ->
                CallStateManager.setCallStatusBanner(
                    err.localizedMessage ?: "Microphone permission required to record call"
                )
            }
        }
    }

    private fun stopCallRecordingIfActive(save: Boolean) {
        if (!inCallRecorder.isRecording.value) return
        if (!save) {
            inCallRecorder.cancelRecording()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val wav = runCatching { inCallRecorder.stopAndBuildWav() }.getOrNull()
            if (wav != null && wav.size > 44) {
                val saved = runCatching { inCallRecorder.saveWavToInternalStorage(wav, "call_rec") }.getOrNull()
                if (saved != null) {
                    CallStateManager.setCallStatusBanner("Saved recording: ${saved.name}")
                }
            }
        }
    }

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
        stopCallRecordingIfActive(save = false)
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
        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber)
        if (cleanNumber.isBlank()) return

        val effectiveSim = when {
            fixDefaultSim.value && preferredSim.value != SimManager.SIM_ASK -> preferredSim.value
            simKeyOverride != null && simKeyOverride != SimManager.SIM_ASK -> simKeyOverride
            preferredSim.value != SimManager.SIM_ASK -> preferredSim.value
            else -> SimManager.SIM_ASK
        }

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
        viewModelScope.launch {
            val entity = ScheduledCall(
                contactName = contactName.ifBlank { cleanNumber },
                phoneNumber = cleanNumber,
                simKey = simKey,
                scheduledAtMillis = scheduledAtMillis,
                status = "PENDING",
                statusMessage = "Scheduled via AlarmManager"
            )
            val id = scheduledCallDao.insertScheduledCall(entity)
            ScheduledCallScheduler.scheduleAlarm(context, entity.copy(id = id))
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
        viewModelScope.launch {
            ScheduledCallScheduler.cancelAlarm(context, existing.id)
            val updated = existing.copy(
                contactName = contactName.ifBlank { cleanNumber },
                phoneNumber = cleanNumber,
                simKey = simKey,
                scheduledAtMillis = scheduledAtMillis,
                status = "PENDING",
                statusMessage = "Schedule updated"
            )
            scheduledCallDao.updateScheduledCall(updated)
            ScheduledCallScheduler.scheduleAlarm(context, updated)
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
                // Step 1: Play custom Lyra greeting
                _voicemailPhase.value = VoicemailPhase.SPEAKING_GREETING
                voicemailEngine.speakGreeting(greeting)

                // Step 2: Play voicemail beep
                _voicemailPhase.value = VoicemailPhase.PLAYING_BEEP
                voicemailEngine.playVoicemailBeep()

                // Step 3: Record caller audio locally
                _voicemailPhase.value = VoicemailPhase.RECORDING_MESSAGE
                voicemailRecorder.startRecording(
                    scope = viewModelScope,
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
                _voicemailPhase.value = VoicemailPhase.ERROR
                _voicemailError.value = e.localizedMessage
                    ?: "Microphone permission (RECORD_AUDIO) is required to record voicemail locally."
            } catch (e: Exception) {
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

    override fun onCleared() {
        super.onCleared()
        voicemailJob?.cancel()
        secondaryDialConnectJob?.cancel()
        runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
        voicemailRecorder.cancelRecording()
        inCallRecorder.cancelRecording()
        voicemailEngine.release()
    }
}
