package com.example.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.ContactsRepository
import com.example.domain.model.CallerInfo
import com.example.utils.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal data class CallScreeningDecision(
    val shouldBlock: Boolean,
    val reason: String? = null
)

internal fun evaluateCallScreeningDecision(
    isLyraEnabled: Boolean,
    blockUnknownCalls: Boolean,
    blockCommercialPrefixes: Boolean,
    phoneNumber: String?,
    caller: CallerInfo
): CallScreeningDecision {
    if (!isLyraEnabled || phoneNumber.isNullOrBlank()) {
        return CallScreeningDecision(shouldBlock = false)
    }
    // Always allow owner, priority contacts, and saved contacts to prevent false-positive auto-rejections
    if (caller.isOwner || caller.isPriority || caller.isKnown) {
        return CallScreeningDecision(shouldBlock = false)
    }
    if (blockCommercialPrefixes && PhoneNumberUtils.isCommercialTelemarketingNumber(phoneNumber)) {
        return CallScreeningDecision(shouldBlock = true, reason = "Commercial / Telemarketing prefix")
    }
    if (blockUnknownCalls) {
        return CallScreeningDecision(shouldBlock = true, reason = "Unknown caller not in contacts")
    }
    return CallScreeningDecision(shouldBlock = false)
}

internal fun shouldBypassSlowScreeningLookup(
    isLyraEnabled: Boolean,
    blockUnknownCalls: Boolean,
    blockCommercialPrefixes: Boolean,
    phoneNumber: String?
): Boolean {
    if (!isLyraEnabled || phoneNumber.isNullOrBlank()) return true
    if (!blockUnknownCalls && !blockCommercialPrefixes) return true
    if (!blockUnknownCalls && blockCommercialPrefixes &&
        !PhoneNumberUtils.isCommercialTelemarketingNumber(phoneNumber)
    ) {
        return true
    }
    return false
}

/**
 * Android CallScreeningService (API 29+) allowing Lyra to hold the Call Screening Role
 * and inspect incoming call metadata locally, applying optional unknown & commercial prefix filters
 * with sub-millisecond fast-path bypass so Telecom call notifications suffer zero delay.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class LyraCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart

        // Fast-path synchronous allow from in-memory snapshot: 0 DB/Disk I/O delay
        val cachedSnapshot = LyraPreferences.getCachedIncomingSnapshotOrNull()
        if (cachedSnapshot != null && shouldBypassSlowScreeningLookup(
                isLyraEnabled = cachedSnapshot.isLyraEnabled,
                blockUnknownCalls = cachedSnapshot.blockUnknownCalls,
                blockCommercialPrefixes = cachedSnapshot.blockCommercialPrefixes,
                phoneNumber = number
            )
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        scope.launch {
            var shouldBlock = false
            runCatching {
                val prefs = LyraPreferences(applicationContext)
                val snapshot = prefs.getIncomingCallSettingsSnapshot()
                if (!shouldBypassSlowScreeningLookup(
                        isLyraEnabled = snapshot.isLyraEnabled,
                        blockUnknownCalls = snapshot.blockUnknownCalls,
                        blockCommercialPrefixes = snapshot.blockCommercialPrefixes,
                        phoneNumber = number
                    )
                ) {
                    val db = LyraDatabase.getDatabase(applicationContext)
                    val repo = ContactsRepository(applicationContext, db.priorityContactDao(), prefs)
                    val caller = repo.resolveCaller(number)
                    val decision = evaluateCallScreeningDecision(
                        isLyraEnabled = snapshot.isLyraEnabled,
                        blockUnknownCalls = snapshot.blockUnknownCalls,
                        blockCommercialPrefixes = snapshot.blockCommercialPrefixes,
                        phoneNumber = number,
                        caller = caller
                    )
                    shouldBlock = decision.shouldBlock
                }
            }
            val response = if (shouldBlock) {
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
            } else {
                CallResponse.Builder().build()
            }
            respondToCall(callDetails, response)
        }
    }
}
