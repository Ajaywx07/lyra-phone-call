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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.example.ui.theme.LyraTheme
import com.example.ui.theme.MyApplicationTheme
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
                val isFullScreenIncomingUiVisible by CallStateManager.isFullScreenIncomingUiVisible.collectAsStateWithLifecycle()
                val previousCallState = remember { mutableStateOf(liveCallState) }

                LaunchedEffect(activeIntent) {
                    val pending = activeIntent ?: return@LaunchedEffect
                    handleIntent(pending, navController)
                    latestIntentState.value = null
                }

                // Keep in-app call screens synchronized without auto-opening full-screen UI when unlocked
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
                        CallStateEnum.DIALING, CallStateEnum.ACTIVE -> {
                            if (routeNow != LyraDestinations.ACTIVE_CALL &&
                                routeNow != LyraDestinations.VOICEMAIL
                            ) {
                                runCatching {
                                    navController.navigate(LyraDestinations.ACTIVE_CALL) {
                                        if (routeNow == LyraDestinations.INCOMING_CALL) {
                                            popUpTo(LyraDestinations.INCOMING_CALL) { inclusive = true }
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                        CallStateEnum.VOICEMAIL -> {
                            if (routeNow != LyraDestinations.VOICEMAIL) {
                                runCatching {
                                    navController.navigate(LyraDestinations.VOICEMAIL) {
                                        if (routeNow == LyraDestinations.INCOMING_CALL) {
                                            popUpTo(LyraDestinations.INCOMING_CALL) { inclusive = true }
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            }
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
                    currentRoute != LyraDestinations.ACTIVE_CALL &&
                    currentRoute != LyraDestinations.VOICEMAIL

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LyraTheme.colors.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LyraNavGraph(
                            navController = navController,
                            viewModel = viewModel,
                            startDestination = initialDestination
                        )

                        AnimatedVisibility(
                            visible = showTopIncomingBanner,
                            enter = slideInVertically(tween(240)) { -it } + fadeIn(tween(180)),
                            exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(140)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
        if (callState == CallStateEnum.RINGING &&
            (launchIntent?.action == "ACTION_INCOMING_CALL" ||
                NotificationHelper.isDeviceLockedOrScreenOff(this))
        ) {
            return LyraDestinations.INCOMING_CALL
        }
        if (callState == CallStateEnum.VOICEMAIL) return LyraDestinations.VOICEMAIL
        if (callState == CallStateEnum.DIALING || callState == CallStateEnum.ACTIVE) {
            return LyraDestinations.ACTIVE_CALL
        }
        if (launchIntent?.action == NotificationHelper.ACTION_OPEN_RECENTS ||
            launchIntent?.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_ROUTE) == LyraDestinations.CALL_HISTORY
        ) {
            return LyraDestinations.CALL_HISTORY
        }
        return when (launchIntent?.action) {
            "ACTION_INCOMING_CALL" -> LyraDestinations.INCOMING_CALL
            "ACTION_OUTGOING_CALL", "ACTION_ACCEPT_CALL" -> LyraDestinations.ACTIVE_CALL
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
                "ACTION_OUTGOING_CALL" -> {
                    if (CallStateManager.callState.value != CallStateEnum.IDLE) {
                        navController.navigate(LyraDestinations.ACTIVE_CALL) {
                            launchSingleTop = true
                        }
                    }
                }
                "ACTION_ACCEPT_CALL" -> {
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
