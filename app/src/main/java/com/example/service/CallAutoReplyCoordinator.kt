package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.AutoReplyRepository
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import java.util.concurrent.ConcurrentHashMap

data class AutoReplyExecutionDecision(
    val shouldCutCall: Boolean,
    val modeTitle: String? = null,
    val smsDispatched: Boolean = false,
    val smsText: String? = null
)

/**
 * Coordinates Auto-Reply Modes across CallScreeningService (API 29+) and InCallService.
 * When an auto-reply mode is active:
 * 1) Cuts/rejects the incoming call immediately.
 * 2) Dispatches the user's saved custom SMS once (deduped across a 15-second window).
 */
object CallAutoReplyCoordinator {

    private const val TAG = "CallAutoReplyCoordinator"
    private const val DEDUP_WINDOW_MS = 15_000L

    private val recentReplies = ConcurrentHashMap<String, Long>()

    internal fun clearDedupForTesting() {
        recentReplies.clear()
    }

    suspend fun evaluateAndExecuteIfActive(
        context: Context,
        incomingPhoneNumber: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): AutoReplyExecutionDecision {
        val appContext = context.applicationContext
        val prefs = LyraPreferences(appContext)
        val snapshot = prefs.getIncomingCallSettingsSnapshot()
        if (!snapshot.isLyraEnabled) {
            return AutoReplyExecutionDecision(shouldCutCall = false)
        }

        val db = LyraDatabase.getDatabase(appContext)
        val repo = AutoReplyRepository(db.autoReplyModeDao(), db.customFeatureSettingsDao())
        val activeMode = repo.getActiveAutoReplyModeIfEnabled()
            ?: return AutoReplyExecutionDecision(shouldCutCall = false)

        val messageToSend = activeMode.customMessage.ifBlank { activeMode.defaultMessage }
        val cleanNumber = incomingPhoneNumber?.trim().orEmpty()
        val dedupKey = PhoneNumberUtils.normalize(cleanNumber).ifBlank { cleanNumber }

        var smsSent = false
        if (cleanNumber.isNotBlank() && dedupKey.isNotBlank()) {
            val lastSentAt = recentReplies[dedupKey]
            val alreadySentRecently = lastSentAt != null && (nowMillis - lastSentAt) < DEDUP_WINDOW_MS
            if (!alreadySentRecently) {
                recentReplies[dedupKey] = nowMillis
                if (PermissionHelper.hasSendSmsPermission(appContext)) {
                    val result = SmsDispatchHelper.sendMultipartSmsDirectly(
                        context = appContext,
                        phoneNumber = cleanNumber,
                        message = messageToSend
                    )
                    smsSent = result.isSuccess
                    if (smsSent) {
                        val display = PhoneNumberUtils.formatDisplay(cleanNumber).ifBlank { cleanNumber }
                        QuickSmsDispatcher.emitStatus(
                            "${activeMode.emoji} Auto-reply (${activeMode.title}) sent to $display"
                        )
                    } else {
                        Log.w(TAG, "Auto-reply SMS failed: ${result.exceptionOrNull()?.message}")
                        QuickSmsDispatcher.emitStatus(
                            "${activeMode.emoji} ${activeMode.title} rejected call (SMS delivery unavailable)"
                        )
                    }
                } else {
                    QuickSmsDispatcher.emitStatus(
                        "${activeMode.emoji} ${activeMode.title} rejected call (grant SEND_SMS to auto-text callers)"
                    )
                }
            }
        }

        return AutoReplyExecutionDecision(
            shouldCutCall = true,
            modeTitle = "${activeMode.emoji} ${activeMode.title}",
            smsDispatched = smsSent,
            smsText = messageToSend
        )
    }
}
