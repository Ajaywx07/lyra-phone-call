package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.navigation.LyraDestinations
import com.example.navigation.LyraNavGraph
import com.example.service.CallStateManager
import com.example.service.NotificationHelper
import com.example.service.ScheduledCallScheduler
import com.example.ui.components.TopIncomingCallBanner
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraMotion
import com.example.ui.theme.LyraTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.rememberAnimationsEnabled
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.PermissionHelper
import com.example.utils.configureAppCheck

class MainActivity : ComponentActivity() {

    private val viewModel: LyraMainViewModel by viewModels()
    private val latestIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure App Check override from launch intent is applied cleanly on top of LyraApplication init
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannels(this)
        latestIntentState.value = intent

        setContent {
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val quickSmsStatus by viewModel.quickSmsStatus.collectAsStateWithLifecycle()

            LaunchedEffect(quickSmsStatus) {
                val msg = quickSmsStatus
                if (!msg.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearQuickSmsStatus()
                }
            }

            MyApplicationTheme(appThemeStyle = appTheme) {
                val initialDestination = remember { resolveStartDestination(intent) }
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val activeIntent by latestIntentState
                val liveCallState by viewModel.callState.collectAsStateWithLifecycle()
                val currentCaller by viewModel.currentCaller.collectAsStateWithLifecycle()
                val activeSimLabel by viewModel.activeSimLabel.collectAsStateWithLifecycle()
                val isRingerSilenced by viewModel.isRingerSilenced.collectAsStateWithLifecycle()
                val showContactPhoto by viewModel.showContactPhotoOnCall.collectAsStateWithLifecycle()
                val isSimulatedCall by viewModel.isSimulatedCall.collectAsStateWithLifecycle()
                val isHold by viewModel.isHold.collectAsStateWithLifecycle()
                val durationSeconds by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
                val isFullScreenIncomingUiVisible by CallStateManager.isFullScreenIncomingUiVisible.collectAsStateWithLifecycle()
                val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
                val previousCallState = remember { mutableStateOf(liveCallState) }
                val animationsEnabled = rememberAnimationsEnabled()

                LaunchedEffect(Unit) {
                    viewModel.checkForAppUpdatesIfStartupPending()
                }

                LaunchedEffect(activeIntent) {
                    val pending = activeIntent ?: return@LaunchedEffect
                    handleIntent(pending, navController)
                    latestIntentState.value = null
                }

                // Keep in-app call screens synchronized cleanly on call termination without hijacking app navigation
                LaunchedEffect(liveCallState, isSimulatedCall) {
                    val prev = previousCallState.value
                    previousCallState.value = liveCallState
                    val routeNow = navController.currentBackStackEntry?.destination?.route
                    if (!isSimulatedCall &&
                        liveCallState != CallStateEnum.DISCONNECTED &&
                        liveCallState != CallStateEnum.IDLE
                    ) {
                        return@LaunchedEffect
                    }
                    when (liveCallState) {
                        CallStateEnum.RINGING -> {
                            // Open full-screen incoming UI automatically ONLY when locked/screen-off;
                            // when unlocked, render only TopIncomingCallBanner until tapped or answered.
                            if (NotificationHelper.isDeviceLockedOrScreenOff(this@MainActivity) &&
                                routeNow != LyraDestinations.INCOMING_CALL
                            ) {
                                runCatching {
                                    navController.navigate(LyraDestinations.INCOMING_CALL) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        CallStateEnum.DIALING,
                        CallStateEnum.ACTIVE,
                        CallStateEnum.VOICEMAIL -> {
                            // Never force-navigate away from normal app screens during an active call.
                        }
                        CallStateEnum.DISCONNECTED -> {
                            if (routeNow == LyraDestinations.INCOMING_CALL ||
                                routeNow == LyraDestinations.ACTIVE_CALL
                            ) {
                                runCatching {
                                    if (!navController.popBackStack()) {
                                        navController.navigate(LyraDestinations.DIALER) {
                                            popUpTo(routeNow) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                viewModel.resetCallState()
                            }
                        }
                        CallStateEnum.IDLE -> {
                            if (prev != CallStateEnum.IDLE &&
                                (routeNow == LyraDestinations.ACTIVE_CALL ||
                                    routeNow == LyraDestinations.INCOMING_CALL)
                            ) {
                                runCatching {
                                    if (!navController.popBackStack()) {
                                        navController.navigate(LyraDestinations.DIALER) {
                                            popUpTo(routeNow) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val isLockedOrScreenOff = NotificationHelper.isDeviceLockedOrScreenOff(this@MainActivity)
                val showTopIncomingBanner = liveCallState == CallStateEnum.RINGING &&
                    !isLockedOrScreenOff &&
                    !isFullScreenIncomingUiVisible &&
                    currentRoute != LyraDestinations.INCOMING_CALL &&
                    currentRoute != LyraDestinations.VOICEMAIL

                val isOngoingCallInApp = liveCallState == CallStateEnum.DIALING ||
                    liveCallState == CallStateEnum.ACTIVE ||
                    liveCallState == CallStateEnum.VOICEMAIL
                val isOnDedicatedCallRoute = currentRoute == LyraDestinations.ACTIVE_CALL ||
                    currentRoute == LyraDestinations.INCOMING_CALL ||
                    currentRoute == LyraDestinations.VOICEMAIL ||
                    currentRoute == LyraDestinations.CALL_SUMMARY
                val showReturnToCallBanner = isOngoingCallInApp &&
                    !isOnDedicatedCallRoute &&
                    !showTopIncomingBanner

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LyraTheme.colors.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                visible = showReturnToCallBanner,
                                enter = expandVertically(LyraMotion.calmTween(220, animationsEnabled)) +
                                    fadeIn(LyraMotion.calmTween(180, animationsEnabled)),
                                exit = shrinkVertically(LyraMotion.calmTween(180, animationsEnabled)) +
                                    fadeOut(LyraMotion.calmTween(140, animationsEnabled))
                            ) {
                                @OptIn(ExperimentalLayoutApi::class)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(LyraTheme.colors.background)
                                        .statusBarsPadding()
                                        .consumeWindowInsets(WindowInsets.statusBars)
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    ReturnToCallBar(
                                        callerName = currentCaller?.displayName
                                            ?.takeIf { it.isNotBlank() }
                                            ?: currentCaller?.formattedNumber
                                            ?.takeIf { it.isNotBlank() }
                                            ?: currentCaller?.phoneNumber
                                            ?.takeIf { it.isNotBlank() }
                                            ?: "Active Call",
                                        callState = liveCallState,
                                        isHold = isHold,
                                        durationSeconds = durationSeconds,
                                        activeSimLabel = activeSimLabel,
                                        onClick = {
                                            CallStateManager.setUserMinimizedCallUi(false)
                                            if (!isSimulatedCall && CallStateManager.hasTelecomBridge()) {
                                                val action = if (liveCallState == CallStateEnum.VOICEMAIL) {
                                                    "ACTION_VOICEMAIL"
                                                } else {
                                                    NotificationHelper.ACTION_RETURN_TO_CALL
                                                }
                                                NotificationHelper.launchCallActivity(this@MainActivity, action)
                                            } else {
                                                val targetRoute = if (liveCallState == CallStateEnum.VOICEMAIL) {
                                                    LyraDestinations.VOICEMAIL
                                                } else {
                                                    LyraDestinations.ACTIVE_CALL
                                                }
                                                runCatching {
                                                    navController.navigate(targetRoute) {
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            LyraNavGraph(
                                navController = navController,
                                viewModel = viewModel,
                                startDestination = initialDestination,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }

                        AnimatedVisibility(
                            visible = showTopIncomingBanner,
                            enter = slideInVertically(tween(240)) { -it } + fadeIn(tween(180)),
                            exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(140)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            val caller = currentCaller ?: CallerInfo(
                                phoneNumber = "",
                                formattedNumber = "Incoming Call",
                                displayName = "Incoming Call"
                            )
                            TopIncomingCallBanner(
                                caller = if (showContactPhoto) caller else caller.copy(photoUri = null),
                                activeSimLabel = activeSimLabel,
                                isSilenced = isRingerSilenced,
                                onBannerClick = {
                                    CallStateManager.setFullScreenIncomingUiVisible(true)
                                    if (!isSimulatedCall && CallStateManager.hasTelecomBridge()) {
                                        NotificationHelper.launchCallActivity(this@MainActivity, "ACTION_INCOMING_CALL")
                                    } else {
                                        runCatching {
                                            navController.navigate(LyraDestinations.INCOMING_CALL) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                },
                                onAcceptClick = {
                                    viewModel.acceptCall()
                                    if (!isSimulatedCall && CallStateManager.hasTelecomBridge()) {
                                        NotificationHelper.launchCallActivity(this@MainActivity, "ACTION_OUTGOING_CALL")
                                    } else {
                                        runCatching {
                                            navController.navigate(LyraDestinations.ACTIVE_CALL) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                },
                                onDeclineClick = {
                                    viewModel.declineCall()
                                    viewModel.resetCallState()
                                    NotificationHelper.cancelAllCallNotifications(this@MainActivity)
                                }
                            )
                        }

                        if (liveCallState == CallStateEnum.IDLE) {
                            availableUpdate?.let { release ->
                                UpdateAvailableDialog(
                                    release = release,
                                    onDownloadClick = { viewModel.openAvailableUpdate(release) },
                                    onSkipVersion = { viewModel.skipUpdateVersion(release.tagName) },
                                    onDismiss = { viewModel.dismissUpdatePrompt() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshIfStale()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntentState.value = intent
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN &&
            CallStateManager.callState.value == CallStateEnum.RINGING
        ) {
            viewModel.silenceRinger()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    internal fun resolveStartDestination(launchIntent: Intent?): String {
        val callState = CallStateManager.callState.value
        val action = launchIntent?.action
        if (callState == CallStateEnum.RINGING &&
            (action == "ACTION_INCOMING_CALL" ||
                NotificationHelper.isDeviceLockedOrScreenOff(this))
        ) {
            return LyraDestinations.INCOMING_CALL
        }
        if (action == NotificationHelper.ACTION_OPEN_RECENTS ||
            launchIntent?.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_ROUTE) == LyraDestinations.CALL_HISTORY
        ) {
            return LyraDestinations.CALL_HISTORY
        }
        return when (action) {
            "ACTION_INCOMING_CALL" -> LyraDestinations.INCOMING_CALL
            NotificationHelper.ACTION_RETURN_TO_CALL,
            "ACTION_OUTGOING_CALL",
            "ACTION_ACCEPT_CALL" -> when {
                callState == CallStateEnum.VOICEMAIL -> LyraDestinations.VOICEMAIL
                callState == CallStateEnum.DIALING ||
                    callState == CallStateEnum.ACTIVE ||
                    action == "ACTION_ACCEPT_CALL" -> LyraDestinations.ACTIVE_CALL
                else -> LyraDestinations.DIALER
            }
            "ACTION_VOICEMAIL" -> LyraDestinations.VOICEMAIL
            ScheduledCallScheduler.ACTION_OPEN_SCHEDULED_CALLS -> LyraDestinations.SCHEDULED_CALLS
            Intent.ACTION_DIAL, Intent.ACTION_CALL, Intent.ACTION_VIEW -> LyraDestinations.DIALER
            else -> LyraDestinations.DIALER
        }
    }

    private fun handleIntent(intent: Intent, navController: NavController) {
        runCatching {
            val targetExtraRoute = intent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_ROUTE)
            if (intent.action == NotificationHelper.ACTION_OPEN_RECENTS ||
                targetExtraRoute == LyraDestinations.CALL_HISTORY
            ) {
                viewModel.markMissedCallsAsRead()
                if (navController.currentBackStackEntry?.destination?.route != LyraDestinations.CALL_HISTORY) {
                    navController.navigate(LyraDestinations.CALL_HISTORY) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                return@runCatching
            }
            when (intent.action) {
                "ACTION_INCOMING_CALL" -> {
                    navController.navigate(LyraDestinations.INCOMING_CALL) {
                        launchSingleTop = true
                    }
                }
                NotificationHelper.ACTION_RETURN_TO_CALL,
                "ACTION_OUTGOING_CALL" -> {
                    CallStateManager.setUserMinimizedCallUi(false)
                    when (CallStateManager.callState.value) {
                        CallStateEnum.VOICEMAIL -> {
                            navController.navigate(LyraDestinations.VOICEMAIL) {
                                launchSingleTop = true
                            }
                        }
                        CallStateEnum.DIALING, CallStateEnum.ACTIVE -> {
                            navController.navigate(LyraDestinations.ACTIVE_CALL) {
                                launchSingleTop = true
                            }
                        }
                        else -> Unit
                    }
                }
                "ACTION_VOICEMAIL" -> {
                    CallStateManager.setUserMinimizedCallUi(false)
                    if (CallStateManager.callState.value != CallStateEnum.IDLE) {
                        navController.navigate(LyraDestinations.VOICEMAIL) {
                            launchSingleTop = true
                        }
                    }
                }
                "ACTION_ACCEPT_CALL" -> {
                    CallStateManager.setUserMinimizedCallUi(false)
                    viewModel.acceptCall()
                    navController.navigate(LyraDestinations.ACTIVE_CALL) {
                        launchSingleTop = true
                    }
                }
                "ACTION_DECLINE_CALL" -> {
                    viewModel.declineCall()
                }
                ScheduledCallScheduler.ACTION_OPEN_SCHEDULED_CALLS -> {
                    navController.navigate(LyraDestinations.SCHEDULED_CALLS) {
                        launchSingleTop = true
                    }
                }
                Intent.ACTION_CALL -> {
                    val number = intent.data?.schemeSpecificPart.orEmpty()
                    val canPlaceDirectly = PermissionHelper.hasCallPhonePermission(this) ||
                        PermissionHelper.isDefaultDialer(this)
                    if (intent.data?.scheme == "tel" && number.isNotBlank() && canPlaceDirectly) {
                        viewModel.placeCall(number)
                    } else if (intent.data?.scheme == "tel") {
                        viewModel.setPrefilledDialNumber(number)
                        navController.navigate(LyraDestinations.DIALER) {
                            launchSingleTop = true
                        }
                    }
                }
                Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                    if (intent.data?.scheme == "tel" || intent.action == Intent.ACTION_DIAL) {
                        val number = intent.data?.schemeSpecificPart.orEmpty()
                        if (number.isNotBlank()) {
                            viewModel.setPrefilledDialNumber(number)
                        }
                        navController.navigate(LyraDestinations.DIALER) {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReturnToCallBar(
    callerName: String,
    callState: CallStateEnum,
    isHold: Boolean,
    durationSeconds: Long,
    activeSimLabel: String,
    onClick: () -> Unit
) {
    val colors = LyraTheme.colors
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val formattedDuration = remember(minutes, seconds) {
        String.format("%02d:%02d", minutes, seconds)
    }
    val accent = when {
        isHold || callState == CallStateEnum.DIALING -> AmberPriority
        callState == CallStateEnum.VOICEMAIL -> colors.primary
        else -> EmeraldAccept
    }
    val statusText = when {
        isHold -> "${stringResource(R.string.ongoing_call_status_hold)} • $formattedDuration"
        callState == CallStateEnum.DIALING -> "${stringResource(R.string.ongoing_call_status_dialing)} • $activeSimLabel"
        callState == CallStateEnum.VOICEMAIL -> stringResource(R.string.ongoing_call_status_voicemail)
        else -> "${stringResource(R.string.ongoing_call_status_active)} • $formattedDuration"
    }
    val returnLabel = stringResource(R.string.return_to_call_action)

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("return_to_call_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.22f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isHold -> Icons.Default.Pause
                                callState == CallStateEnum.VOICEMAIL -> Icons.Default.Voicemail
                                else -> Icons.Default.Call
                            },
                            contentDescription = returnLabel,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = callerName,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = accent
            ) {
                Text(
                    text = returnLabel,
                    color = if (isHold || callState == CallStateEnum.DIALING) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

