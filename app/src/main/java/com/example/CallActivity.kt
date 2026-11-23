package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CallStateEnum
import com.example.service.CallStateManager
import com.example.service.NotificationHelper
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.screens.VoicemailScreen
import com.example.ui.theme.LyraTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LyraMainViewModel
import com.example.utils.configureAppCheck

/**
 * Dedicated independent Activity for Incoming, Active, and Voicemail calls.
 * Operates in its own task (.call) above lock screens and third-party apps so
 * answering or ending a call returns users cleanly to their previous app or Home.
 */
class CallActivity : ComponentActivity() {

    private val viewModel: LyraMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        configureLockScreenWindow()
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannels(this)
        handleCallActionIntent(intent)

        setContent {
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val liveCallState by viewModel.callState.collectAsStateWithLifecycle()
            val quickSmsStatus by viewModel.quickSmsStatus.collectAsStateWithLifecycle()
            var hasObservedActiveCall by remember {
                mutableStateOf(liveCallState != CallStateEnum.IDLE && liveCallState != CallStateEnum.DISCONNECTED)
            }

            LaunchedEffect(quickSmsStatus) {
                val msg = quickSmsStatus
                if (!msg.isNullOrBlank()) {
                    Toast.makeText(this@CallActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearQuickSmsStatus()
                }
            }

            LaunchedEffect(liveCallState) {
                when (liveCallState) {
                    CallStateEnum.RINGING,
                    CallStateEnum.DIALING,
                    CallStateEnum.ACTIVE,
                    CallStateEnum.VOICEMAIL -> {
                        hasObservedActiveCall = true
                    }
                    CallStateEnum.DISCONNECTED -> {
                        if (CallStateManager.hasLiveTelecomCall()) {
                            CallStateManager.syncWithLiveTelecomBridge()
                        } else {
                            viewModel.resetCallState()
                            finishCallActivity()
                        }
                    }
                    CallStateEnum.IDLE -> {
                        if (CallStateManager.hasLiveTelecomCall()) {
                            CallStateManager.syncWithLiveTelecomBridge()
                        } else if (hasObservedActiveCall) {
                            finishCallActivity()
                        }
                    }
                }
            }

            MyApplicationTheme(appThemeStyle = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LyraTheme.colors.background
                ) {
                    AnimatedContent(
                        targetState = when (liveCallState) {
                            CallStateEnum.DIALING, CallStateEnum.ACTIVE -> CallStateEnum.ACTIVE
                            else -> liveCallState
                        },
                        transitionSpec = {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(110))
                        },
                        label = "call_activity_state_transition"
                    ) { screenState ->
                        when (screenState) {
                            CallStateEnum.RINGING -> {
                                IncomingCallScreen(
                                    viewModel = viewModel,
                                    onAcceptCall = {},
                                    onDeclineCall = {
                                        viewModel.resetCallState()
                                        finishCallActivity()
                                    },
                                    onVoicemailCall = {}
                                )
                            }
                            CallStateEnum.VOICEMAIL -> {
                                VoicemailScreen(
                                    viewModel = viewModel,
                                    onBackClick = {
                                        viewModel.cancelVoicemailScreening()
                                        finishCallActivity()
                                    },
                                    onOpenSettings = {
                                        val settingsIntent = Intent(this@CallActivity, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                        startActivity(settingsIntent)
                                        finishCallActivity()
                                    },
                                    onDone = {
                                        viewModel.resetCallState()
                                        finishCallActivity()
                                    }
                                )
                            }
                            CallStateEnum.DIALING,
                            CallStateEnum.ACTIVE -> {
                                ActiveCallScreen(
                                    viewModel = viewModel,
                                    onEndCall = {
                                        viewModel.resetCallState()
                                        finishCallActivity()
                                    },
                                    onCancelCall = {
                                        viewModel.resetCallState()
                                        finishCallActivity()
                                    }
                                )
                            }
                            CallStateEnum.DISCONNECTED,
                            CallStateEnum.IDLE -> {
                                // Empty surface while closing cleanly
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CallStateManager.syncWithLiveTelecomBridge()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
        handleCallActionIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            CallStateManager.callState.value == CallStateEnum.RINGING
        ) {
            // Silence ringtone ONLY: do not reject call, close UI, or dismiss notification
            viewModel.silenceRinger()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleCallActionIntent(launchIntent: Intent?) {
        CallStateManager.syncWithLiveTelecomBridge()
        when (launchIntent?.action) {
            "ACTION_INCOMING_CALL" -> {
                CallStateManager.setFullScreenIncomingUiVisible(true)
            }
            "ACTION_OUTGOING_CALL" -> {
                // Outgoing or picked-up call displays inside CallActivity above lock screen without dismissing keyguard.
                CallStateManager.setFullScreenIncomingUiVisible(false)
            }
            "ACTION_ACCEPT_CALL" -> {
                CallStateManager.setFullScreenIncomingUiVisible(false)
                viewModel.acceptCall()
            }
            "ACTION_DECLINE_CALL" -> {
                CallStateManager.setFullScreenIncomingUiVisible(false)
                viewModel.declineCall()
                viewModel.resetCallState()
                finishCallActivity()
            }
        }
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun finishCallActivity() {
        CallStateManager.setFullScreenIncomingUiVisible(false)
        if (!isFinishing && !isDestroyed) {
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            CallStateManager.setFullScreenIncomingUiVisible(false)
        }
        super.onDestroy()
    }
}
