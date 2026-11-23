package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.ContactsRepository
import com.example.domain.model.CallStateEnum
import com.example.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LyraCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // When Lyra is the Default Dialer or InCallService bridge is bound, LyraInCallService handles Telecom states directly.
                if (PermissionHelper.isDefaultDialer(context) || CallStateManager.hasTelecomBridge()) {
                    return@launch
                }

                val prefs = LyraPreferences(context)
                val settings = prefs.getIncomingCallSettingsSnapshot()
                if (!settings.isLyraEnabled) return@launch

                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        val db = LyraDatabase.getDatabase(context)
                        val contactsRepo = ContactsRepository(context, db.priorityContactDao(), prefs)
                        val caller = contactsRepo.resolveCaller(incomingNumber)
                        val decision = evaluateCallScreeningDecision(
                            isLyraEnabled = settings.isLyraEnabled,
                            blockUnknownCalls = settings.blockUnknownCalls,
                            blockCommercialPrefixes = settings.blockCommercialPrefixes,
                            phoneNumber = incomingNumber,
                            caller = caller
                        )
                        if (decision.shouldBlock) {
                            return@launch
                        }

                        CallStateManager.onIncomingCall(caller)

                        NotificationHelper.dispatchIncomingCall(
                            context = context,
                            caller = caller,
                            fullscreenPreferenceEnabled = settings.isFullscreenIncomingEnabled,
                            incomingScreenEnabled = settings.isIncomingScreenEnabled,
                            backgroundCallingEnabled = settings.isBackgroundCallingEnabled,
                            persistentNotificationEnabled = settings.isPersistentNotificationEnabled,
                            showCallerPhoto = settings.showContactPhotoOnCall
                        )
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        CallStateManager.onCallAnswered()
                        runCatching {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, LyraCallService::class.java)
                            )
                        }
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        val prevState = CallStateManager.callState.value
                        val prevCaller = CallStateManager.currentCaller.value
                        val prevSimLabel = CallStateManager.activeSimLabel.value
                        val wasOutgoing = CallStateManager.isOutgoingCall.value

                        if (prevState == CallStateEnum.RINGING && !wasOutgoing) {
                            val number = prevCaller?.phoneNumber?.takeIf { it.isNotBlank() }
                                ?: incomingNumber.orEmpty()
                            if (number.isNotBlank() || prevCaller != null) {
                                NotificationHelper.recordAndNotifyMissedCall(
                                    context = context,
                                    callerName = prevCaller?.displayName,
                                    phoneNumber = number,
                                    timestamp = System.currentTimeMillis(),
                                    simLabel = prevSimLabel.takeIf { it.isNotBlank() }
                                )
                            }
                        }

                        if (prevState != CallStateEnum.IDLE) {
                            CallStateManager.onCallEnded()
                        }
                        NotificationHelper.cancelAllCallNotifications(context)
                        runCatching {
                            context.stopService(Intent(context, LyraCallService::class.java))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
