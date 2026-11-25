package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.LyraApplication
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.CallRepository
import com.example.data.repository.LocalRuleBasedAiRepository
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.ui.components.TopIncomingCallBanner
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.CallProximityManager
import com.example.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground Service keeping BOTH incoming ringing notifications and active ongoing call
 * notifications strictly non-dismissible across app switching, screen lock, and Recent Apps swipe.
 * Also manages the unlocked-screen floating TopIncomingCallBanner overlay when Lyra is in the background.
 */
class LyraCallService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var activeForegroundNotificationId: Int? = null
    private val preferences by lazy { LyraPreferences(applicationContext) }

    private var windowManager: WindowManager? = null
    private var bannerOverlayView: ComposeView? = null
    private var bannerLifecycleOwner: OverlayLifecycleOwner? = null
    private var screenReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val snap = currentSnapshot()
            if (snap.state == CallStateEnum.RINGING && intent.action == Intent.ACTION_SCREEN_OFF) {
                hideTopBannerOverlay()
                serviceScope.launch {
                    val settings = runCatching { preferences.getIncomingCallSettingsSnapshot() }.getOrNull()
                    val incomingEnabled = settings?.isIncomingScreenEnabled ?: true
                    val bgEnabled = settings?.isBackgroundCallingEnabled ?: true
                    if (incomingEnabled && (bgEnabled || snap.isAppInForeground) && !snap.isFullScreenUiVisible) {
                        NotificationHelper.launchCallActivity(applicationContext, "ACTION_INCOMING_CALL")
                    }
                    publishForSnapshot(currentSnapshot())
                }
            } else if (snap.state == CallStateEnum.RINGING) {
                serviceScope.launch {
                    publishForSnapshot(currentSnapshot())
                }
            }
        }
    }

    private data class CallForegroundSnapshot(
        val state: CallStateEnum,
        val caller: CallerInfo?,
        val simLabel: String,
        val startTimeMs: Long,
        val isMuted: Boolean,
        val isSpeakerOn: Boolean,
        val isHold: Boolean,
        val isRingerSilenced: Boolean,
        val isFullScreenUiVisible: Boolean,
        val isAppInForeground: Boolean
    )

    private fun resolveForegroundType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun handleCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun handleDestroy() {
            runCatching {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            screenReceiverRegistered = true
        }
        startObservingCallState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val snapshot = currentSnapshot()
        // Android 12+ requires calling startForeground() synchronously after startForegroundService(),
        // even if the call already transitioned to IDLE/DISCONNECTED before onStartCommand runs.
        startForegroundImmediately(snapshot)
        if (snapshot.state == CallStateEnum.IDLE || snapshot.state == CallStateEnum.DISCONNECTED) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }
        serviceScope.launch {
            publishForSnapshot(snapshot)
        }
        return START_STICKY
    }

    private fun startForegroundImmediately(snapshot: CallForegroundSnapshot) {
        val foregroundType = resolveForegroundType()
        val isLockedOrOff = NotificationHelper.isDeviceLockedOrScreenOff(this)
        val hasCustomBanner = !isLockedOrOff &&
            !snapshot.isFullScreenUiVisible &&
            (snapshot.isAppInForeground || PermissionHelper.hasOverlayPermission(this))

        val (notificationId, notification) = when (snapshot.state) {
            CallStateEnum.RINGING -> {
                val caller = snapshot.caller ?: CallerInfo(
                    phoneNumber = "",
                    formattedNumber = "Incoming Call",
                    displayName = "Incoming Call"
                )
                NotificationHelper.NOTIFICATION_ID_INCOMING to NotificationHelper.buildIncomingCallNotification(
                    context = this,
                    caller = caller,
                    useFullScreenIntent = isLockedOrOff && !snapshot.isFullScreenUiVisible,
                    showCallerPhoto = false,
                    isSilenced = snapshot.isRingerSilenced,
                    suppressHeadsUpPopup = snapshot.isFullScreenUiVisible || hasCustomBanner || isLockedOrOff
                )
            }
            CallStateEnum.DIALING,
            CallStateEnum.ACTIVE,
            CallStateEnum.VOICEMAIL -> {
                NotificationHelper.NOTIFICATION_ID_ASSISTANT to NotificationHelper.buildOngoingCallNotification(
                    context = this,
                    caller = snapshot.caller,
                    callState = snapshot.state,
                    callStartTimeMs = snapshot.startTimeMs,
                    isMuted = snapshot.isMuted,
                    isSpeakerOn = snapshot.isSpeakerOn,
                    showCallerPhoto = false,
                    isHold = snapshot.isHold
                )
            }
            CallStateEnum.IDLE,
            CallStateEnum.DISCONNECTED -> {
                NotificationHelper.NOTIFICATION_ID_ASSISTANT to NotificationHelper.buildOngoingCallNotification(
                    context = this,
                    caller = CallerInfo(
                        phoneNumber = "",
                        formattedNumber = "",
                        displayName = "Ending Call"
                    ),
                    callState = CallStateEnum.ACTIVE,
                    callStartTimeMs = 0L,
                    isMuted = false,
                    isSpeakerOn = false,
                    showCallerPhoto = false,
                    isHold = false
                )
            }
        }
        val promoted = runCatching {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                foregroundType
            )
            true
        }.getOrDefault(false)
        if (promoted) {
            activeForegroundNotificationId = notificationId
        }
    }

    private fun currentSnapshot(): CallForegroundSnapshot {
        return CallForegroundSnapshot(
            state = CallStateManager.callState.value,
            caller = CallStateManager.currentCaller.value,
            simLabel = CallStateManager.activeSimLabel.value,
            startTimeMs = CallStateManager.callStartTimeMs.value,
            isMuted = CallStateManager.isMuted.value,
            isSpeakerOn = CallStateManager.isSpeakerOn.value,
            isHold = CallStateManager.isHold.value,
            isRingerSilenced = CallStateManager.isRingerSilenced.value,
            isFullScreenUiVisible = CallStateManager.isFullScreenIncomingUiVisible.value,
            isAppInForeground = LyraApplication.isAppInForeground
        )
    }

    private fun startObservingCallState() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            val baseFlow = combine(
                CallStateManager.callState,
                CallStateManager.currentCaller,
                CallStateManager.callStartTimeMs,
                CallStateManager.isMuted,
                CallStateManager.isSpeakerOn
            ) { state, caller, startTimeMs, muted, speaker ->
                CallForegroundSnapshot(
                    state = state,
                    caller = caller,
                    simLabel = CallStateManager.activeSimLabel.value,
                    startTimeMs = startTimeMs,
                    isMuted = muted,
                    isSpeakerOn = speaker,
                    isHold = CallStateManager.isHold.value,
                    isRingerSilenced = CallStateManager.isRingerSilenced.value,
                    isFullScreenUiVisible = CallStateManager.isFullScreenIncomingUiVisible.value,
                    isAppInForeground = LyraApplication.isAppInForeground
                )
            }
            val metaFlow = combine(
                CallStateManager.isRingerSilenced,
                CallStateManager.isFullScreenIncomingUiVisible,
                LyraApplication.isAppInForegroundFlow,
                CallStateManager.activeSimLabel,
                CallStateManager.isHold
            ) { silenced, fullScreenVisible, inForeground, simLabel, isHold ->
                arrayOf<Any>(silenced, fullScreenVisible, inForeground, simLabel, isHold)
            }
            combine(baseFlow, metaFlow) { snap, meta ->
                snap.copy(
                    isRingerSilenced = meta[0] as Boolean,
                    isFullScreenUiVisible = meta[1] as Boolean,
                    isAppInForeground = meta[2] as Boolean,
                    simLabel = meta[3] as String,
                    isHold = meta[4] as Boolean
                )
            }.collect { snapshot ->
                val enableProximity = snapshot.state == CallStateEnum.DIALING ||
                    snapshot.state == CallStateEnum.ACTIVE
                CallProximityManager.updateCallAudioState(
                    context = this@LyraCallService,
                    isCallOngoing = enableProximity,
                    isSpeakerOn = snapshot.isSpeakerOn
                )
                when (snapshot.state) {
                    CallStateEnum.RINGING,
                    CallStateEnum.DIALING,
                    CallStateEnum.ACTIVE,
                    CallStateEnum.VOICEMAIL -> {
                        publishForSnapshot(snapshot)
                    }

                    CallStateEnum.IDLE,
                    CallStateEnum.DISCONNECTED -> {
                        stopForegroundAndSelf()
                    }
                }
            }
        }
    }

    private suspend fun publishForSnapshot(snapshot: CallForegroundSnapshot) {
        val foregroundType = resolveForegroundType()
        val settings = runCatching { preferences.getIncomingCallSettingsSnapshot() }.getOrNull()
        val showPhoto = settings?.showContactPhotoOnCall ?: true
        val fullscreenEnabled = settings?.isFullscreenIncomingEnabled ?: true
        val incomingScreenEnabled = settings?.isIncomingScreenEnabled ?: true
        val appTheme = AppThemeStyle.fromKey(settings?.themeMode ?: AppThemeStyle.IOS_GLASS.key)

        if (snapshot.state == CallStateEnum.RINGING) {
            val caller = snapshot.caller ?: CallerInfo(
                phoneNumber = "",
                formattedNumber = "Incoming Call",
                displayName = "Incoming Call"
            )
            val isLockedOrOff = NotificationHelper.isDeviceLockedOrScreenOff(this)
            val presentationMode = NotificationHelper.resolveIncomingCallPresentationMode(
                isLockedOrScreenOff = isLockedOrOff,
                incomingScreenEnabled = incomingScreenEnabled,
                fullScreenUiVisible = snapshot.isFullScreenUiVisible
            )

            // Manage floating Compose top banner ONLY for unlocked background calls with overlay permission
            val shouldShowFloatingTopBanner = presentationMode == IncomingCallPresentationMode.TOP_BANNER_UNLOCKED &&
                !snapshot.isAppInForeground &&
                !snapshot.isFullScreenUiVisible &&
                PermissionHelper.hasOverlayPermission(this)

            if (shouldShowFloatingTopBanner) {
                showOrUpdateTopBannerOverlay(
                    caller = if (showPhoto) caller else caller.copy(photoUri = null),
                    simLabel = snapshot.simLabel,
                    isSilenced = snapshot.isRingerSilenced,
                    appTheme = appTheme
                )
            } else {
                hideTopBannerOverlay()
            }

            val useFullScreenIntent =
                presentationMode == IncomingCallPresentationMode.FULL_SCREEN_LOCKED &&
                    fullscreenEnabled

            val hasCustomComposeBanner = presentationMode == IncomingCallPresentationMode.TOP_BANNER_UNLOCKED &&
                (snapshot.isAppInForeground || PermissionHelper.hasOverlayPermission(this))

            val suppressHeadsUpPopup = snapshot.isFullScreenUiVisible ||
                hasCustomComposeBanner ||
                presentationMode == IncomingCallPresentationMode.FULL_SCREEN_LOCKED

            val incomingNotification = kotlinx.coroutines.withContext(Dispatchers.IO) {
                NotificationHelper.buildIncomingCallNotification(
                    context = this@LyraCallService,
                    caller = caller,
                    useFullScreenIntent = useFullScreenIntent,
                    showCallerPhoto = showPhoto,
                    isSilenced = snapshot.isRingerSilenced,
                    suppressHeadsUpPopup = suppressHeadsUpPopup
                )
            }
            runCatching {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_ID_INCOMING,
                    incomingNotification,
                    foregroundType
                )
                if (activeForegroundNotificationId == NotificationHelper.NOTIFICATION_ID_ASSISTANT) {
                    NotificationManagerCompat.from(this).cancel(NotificationHelper.NOTIFICATION_ID_ASSISTANT)
                }
                activeForegroundNotificationId = NotificationHelper.NOTIFICATION_ID_INCOMING
            }
            return
        }

        // Not ringing anymore -> always tear down any unlocked top incoming banner immediately
        hideTopBannerOverlay()

        if (snapshot.state == CallStateEnum.DIALING ||
            snapshot.state == CallStateEnum.ACTIVE ||
            snapshot.state == CallStateEnum.VOICEMAIL
        ) {
            val isLockedOrOff = NotificationHelper.isDeviceLockedOrScreenOff(this)
            val attachOutgoingFsi = snapshot.state == CallStateEnum.DIALING &&
                isLockedOrOff &&
                !snapshot.isFullScreenUiVisible &&
                PermissionHelper.canUseFullScreenIntent(this)

            val ongoingNotification = kotlinx.coroutines.withContext(Dispatchers.IO) {
                NotificationHelper.buildOngoingCallNotification(
                    context = this@LyraCallService,
                    caller = snapshot.caller,
                    callState = snapshot.state,
                    callStartTimeMs = snapshot.startTimeMs,
                    isMuted = snapshot.isMuted,
                    isSpeakerOn = snapshot.isSpeakerOn,
                    showCallerPhoto = showPhoto,
                    useFullScreenIntent = attachOutgoingFsi,
                    isHold = snapshot.isHold
                )
            }
            runCatching {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_ID_ASSISTANT,
                    ongoingNotification,
                    foregroundType
                )
                activeForegroundNotificationId = NotificationHelper.NOTIFICATION_ID_ASSISTANT
                NotificationManagerCompat.from(this).cancel(NotificationHelper.NOTIFICATION_ID_INCOMING)
            }
        }
    }

    private fun showOrUpdateTopBannerOverlay(
        caller: CallerInfo,
        simLabel: String,
        isSilenced: Boolean,
        appTheme: AppThemeStyle
    ) {
        val wm = windowManager ?: return
        if (!PermissionHelper.hasOverlayPermission(this)) return

        val existingView = bannerOverlayView
        if (existingView != null) {
            existingView.setContent {
                OverlayBannerContent(
                    caller = caller,
                    simLabel = simLabel,
                    isSilenced = isSilenced,
                    appTheme = appTheme
                )
            }
            return
        }

        runCatching {
            val lifecycleOwner = OverlayLifecycleOwner().apply { handleCreate() }
            val composeView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    OverlayBannerContent(
                        caller = caller,
                        simLabel = simLabel,
                        isSilenced = isSilenced,
                        appTheme = appTheme
                    )
                }
            }

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val topMarginPx = (8f * resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = topMarginPx
            }

            wm.addView(composeView, params)
            bannerOverlayView = composeView
            bannerLifecycleOwner = lifecycleOwner
        }
    }

    @androidx.compose.runtime.Composable
    private fun OverlayBannerContent(
        caller: CallerInfo,
        simLabel: String,
        isSilenced: Boolean,
        appTheme: AppThemeStyle
    ) {
        MyApplicationTheme(appThemeStyle = appTheme) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                TopIncomingCallBanner(
                    caller = caller,
                    activeSimLabel = simLabel,
                    isSilenced = isSilenced,
                    onBannerClick = {
                        hideTopBannerOverlay()
                        CallStateManager.setFullScreenIncomingUiVisible(true)
                        NotificationHelper.launchCallActivity(applicationContext, "ACTION_INCOMING_CALL")
                    },
                    onAcceptClick = {
                        hideTopBannerOverlay()
                        CallStateManager.acceptActiveCall()
                        NotificationHelper.launchCallActivity(applicationContext, "ACTION_OUTGOING_CALL")
                    },
                    onDeclineClick = {
                        hideTopBannerOverlay()
                        val db = LyraDatabase.getDatabase(applicationContext)
                        val callRepo = CallRepository(
                            db.callNoteDao(),
                            db.callSummaryDao(),
                            db.priorityContactDao(),
                            LocalRuleBasedAiRepository()
                        )
                        CallStateManager.declineOrEndActiveCall(callRepo)
                        stopForegroundAndSelf()
                    }
                )
            }
        }
    }

    private fun hideTopBannerOverlay() {
        val view = bannerOverlayView ?: return
        bannerOverlayView = null
        val owner = bannerLifecycleOwner
        bannerLifecycleOwner = null
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { runCatching { windowManager?.removeView(view) } }
        owner?.handleDestroy()
    }

    private fun stopForegroundAndSelf() {
        hideTopBannerOverlay()
        CallProximityManager.release()
        if (activeForegroundNotificationId != null) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            activeForegroundNotificationId = null
        }
        NotificationHelper.cancelAllCallNotifications(this)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep persistent incoming or ongoing call notification alive even if user swipes Lyra from Recents
        if (CallStateManager.isRingingOrOngoing()) {
            val snap = currentSnapshot()
            serviceScope.launch { publishForSnapshot(snap) }
        } else {
            stopForegroundAndSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        hideTopBannerOverlay()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenReceiverRegistered = false
        }
        CallProximityManager.release()
        observerJob?.cancel()
        serviceScope.cancel()
        if (activeForegroundNotificationId != null) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            activeForegroundNotificationId = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
