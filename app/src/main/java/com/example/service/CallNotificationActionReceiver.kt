package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.CallRepository
import com.example.data.repository.LocalRuleBasedAiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles call actions directly from incoming heads-up, ongoing, and missed call notifications
 * without disrupting the user's current app until they explicitly interact.
 */
class CallNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ACCEPT_CALL -> {
                CallStateManager.acceptActiveCall()
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, LyraCallService::class.java)
                    )
                }
                NotificationHelper.launchCallActivity(context, "ACTION_OUTGOING_CALL")
            }

            ACTION_DECLINE_CALL -> {
                val db = LyraDatabase.getDatabase(context)
                val callRepo = CallRepository(
                    db.callNoteDao(),
                    db.callSummaryDao(),
                    db.priorityContactDao(),
                    LocalRuleBasedAiRepository()
                )
                CallStateManager.declineOrEndActiveCall(callRepo)
                NotificationHelper.cancelAllCallNotifications(context)
                runCatching { context.stopService(Intent(context, LyraCallService::class.java)) }
            }

            ACTION_TOGGLE_MUTE -> {
                CallStateManager.toggleMute()
            }

            ACTION_TOGGLE_SPEAKER -> {
                CallStateManager.toggleSpeaker()
            }

            ACTION_HANG_UP -> {
                val db = LyraDatabase.getDatabase(context)
                val callRepo = CallRepository(
                    db.callNoteDao(),
                    db.callSummaryDao(),
                    db.priorityContactDao(),
                    LocalRuleBasedAiRepository()
                )
                CallStateManager.declineOrEndActiveCall(callRepo)
                NotificationHelper.cancelAllCallNotifications(context)
                runCatching { context.stopService(Intent(context, LyraCallService::class.java)) }
            }

            ACTION_CLEAR_MISSED_CALLS -> {
                NotificationHelper.clearMissedCallNotification(context)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        LyraPreferences(context).markAllMissedCallsAsRead()
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT_CALL = "com.ajay.lyra.action.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.ajay.lyra.action.DECLINE_CALL"
        const val ACTION_TOGGLE_MUTE = "com.ajay.lyra.action.TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.ajay.lyra.action.TOGGLE_SPEAKER"
        const val ACTION_HANG_UP = "com.ajay.lyra.action.HANG_UP"
        const val ACTION_CLEAR_MISSED_CALLS = "com.ajay.lyra.action.CLEAR_MISSED_CALLS"
    }
}
