package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.example.domain.model.CallHistoryItemData
import com.example.domain.model.CallTypeEnum
import com.example.domain.model.ContactItemData
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import com.example.utils.SimManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogRepository(
    private val context: Context,
    private val contactsRepository: ContactsRepository
) {

    private val localDeletedIds = mutableSetOf<String>()

    private val demoCallLog = listOf(
        CallHistoryItemData(
            id = "demo_call_1",
            phoneNumber = "+91 98765 43210",
            contactName = "Ajay",
            callType = CallTypeEnum.INCOMING,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
            durationSeconds = 342,
            formattedDuration = "5m 42s",
            formattedDate = "Today, 10:42 AM",
            simLabel = "SIM 1",
            isPriority = true,
            isOwner = true,
            isDemoData = true
        ),
        CallHistoryItemData(
            id = "demo_call_2",
            phoneNumber = "+91 98111 22334",
            contactName = "Dr. Rohan Verma",
            callType = CallTypeEnum.MISSED,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 95,
            durationSeconds = 0,
            formattedDuration = "Missed",
            formattedDate = "Today, 09:15 AM",
            simLabel = "SIM 2",
            isPriority = true,
            isOwner = false,
            isDemoData = true
        ),
        CallHistoryItemData(
            id = "demo_call_3",
            phoneNumber = "+91 99887 76655",
            contactName = "Unknown Caller",
            callType = CallTypeEnum.INCOMING,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 240,
            durationSeconds = 48,
            formattedDuration = "48s",
            formattedDate = "Today, 07:05 AM",
            simLabel = "SIM 1",
            isPriority = false,
            isOwner = false,
            isDemoData = true
        ),
        CallHistoryItemData(
            id = "demo_call_4",
            phoneNumber = "+91 98222 33445",
            contactName = "Priya Sharma",
            callType = CallTypeEnum.OUTGOING,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24,
            durationSeconds = 195,
            formattedDuration = "3m 15s",
            formattedDate = "Yesterday, 04:30 PM",
            simLabel = "SIM 2",
            isPriority = true,
            isOwner = false,
            isDemoData = true
        ),
        CallHistoryItemData(
            id = "demo_call_5",
            phoneNumber = "+91 91234 56789",
            contactName = "Unknown Caller",
            callType = CallTypeEnum.REJECTED,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 28,
            durationSeconds = 0,
            formattedDuration = "Rejected",
            formattedDate = "Yesterday, 12:10 PM",
            simLabel = "SIM 1",
            isPriority = false,
            isOwner = false,
            isDemoData = true
        )
    )

    suspend fun getCallLog(
        preloadedContacts: List<ContactItemData>? = null,
        maxEntries: Int = 600
    ): List<CallHistoryItemData> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasCallLogPermission(context)) {
            return@withContext demoCallLog.filterNot { localDeletedIds.contains(it.id) }
        }

        // Reuse preloaded contacts when provided to avoid duplicate ContactsContract scans
        val resolvedContacts = preloadedContacts ?: contactsRepository.getContacts()
        val contactsByCore = HashMap<String, ContactItemData>(resolvedContacts.size)
        for (c in resolvedContacts) {
            if (c.isDemoData) continue
            val core = PhoneNumberUtils.extractCoreDigits(c.phoneNumber)
            if (core.isNotEmpty()) contactsByCore.putIfAbsent(core, c)
        }

        val accountIdMap = SimManager.buildAccountIdToSimLabelMap(context)
        val isDualSim = SimManager.isDualSimDevice(context) || accountIdMap.values.distinct().size >= 2

        val logList = mutableListOf<CallHistoryItemData>()
        val seenKeys = mutableSetOf<String>()
        val contentResolver = context.contentResolver
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val accountIdIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (it.moveToNext() && logList.size < maxEntries) {
                    val id = if (idIdx >= 0) it.getString(idIdx) else ""
                    if (id.isBlank() || localDeletedIds.contains(id)) continue

                    val rawNumber = if (numberIdx >= 0) it.getString(numberIdx) ?: "" else ""
                    val cachedName = if (nameIdx >= 0) it.getString(nameIdx)?.takeIf { n -> n.isNotBlank() } else null
                    val rawType = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val dateLong = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val durationSec = if (durationIdx >= 0) it.getLong(durationIdx).coerceAtLeast(0L) else 0L
                    val rawAccountId = if (accountIdIdx >= 0) it.getString(accountIdIdx) else null

                    val callType = mapCallType(rawType)
                    val coreNumber = PhoneNumberUtils.extractCoreDigits(rawNumber)
                    val dedupeKey = "${coreNumber}_${dateLong / 1000L}_${callType.name}"
                    if (!seenKeys.add(dedupeKey)) continue

                    val durationStr = formatCallDuration(callType, durationSec)
                    val simLabel = SimManager.resolveSimLabelFromAccountId(
                        phoneAccountId = rawAccountId,
                        accountIdMap = accountIdMap,
                        includeSingleSimFallback = isDualSim
                    )

                    val matchedContact = contactsByCore[coreNumber]
                    val resolvedName = matchedContact?.name
                        ?: cachedName
                        ?: if (rawNumber.isNotBlank()) PhoneNumberUtils.formatDisplay(rawNumber) else "Unknown Caller"

                    logList.add(
                        CallHistoryItemData(
                            id = id,
                            phoneNumber = PhoneNumberUtils.formatDisplay(rawNumber),
                            contactName = resolvedName,
                            photoUri = matchedContact?.photoUri,
                            callType = callType,
                            timestamp = dateLong,
                            durationSeconds = durationSec,
                            formattedDuration = durationStr,
                            formattedDate = dateFormat.format(Date(dateLong)),
                            simLabel = simLabel,
                            isPriority = matchedContact?.isPriority ?: false,
                            isOwner = matchedContact?.isOwner ?: false,
                            isDemoData = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        // When READ_CALL_LOG permission IS granted, return the true device log (even if empty)
        logList.sortedByDescending { it.timestamp }
    }

    suspend fun deleteCallLogEntry(id: String) = withContext(Dispatchers.IO) {
        localDeletedIds.add(id)
        if (PermissionHelper.hasPermission(context, android.Manifest.permission.WRITE_CALL_LOG)) {
            try {
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} = ?",
                    arrayOf(id)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun markSystemMissedCallsAsRead() = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasWriteCallLogPermission(context)) return@withContext
        runCatching {
            val values = ContentValues().apply {
                put(CallLog.Calls.IS_READ, 1)
                put(CallLog.Calls.NEW, 0)
            }
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                values,
                "${CallLog.Calls.TYPE} = ? AND (${CallLog.Calls.IS_READ} = 0 OR ${CallLog.Calls.NEW} = 1)",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            )
        }
    }

    companion object {
        fun mapCallType(rawType: Int): CallTypeEnum = when (rawType) {
            CallLog.Calls.INCOMING_TYPE -> CallTypeEnum.INCOMING
            CallLog.Calls.OUTGOING_TYPE -> CallTypeEnum.OUTGOING
            CallLog.Calls.MISSED_TYPE -> CallTypeEnum.MISSED
            CallLog.Calls.REJECTED_TYPE -> CallTypeEnum.REJECTED
            else -> CallTypeEnum.INCOMING
        }

        fun formatCallDuration(callType: CallTypeEnum, durationSec: Long): String {
            return when (callType) {
                CallTypeEnum.MISSED -> "Missed"
                CallTypeEnum.REJECTED -> "Rejected"
                CallTypeEnum.INCOMING, CallTypeEnum.OUTGOING -> {
                    if (durationSec <= 0L) {
                        "Not connected"
                    } else {
                        val mins = durationSec / 60
                        val secs = durationSec % 60
                        if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                    }
                }
            }
        }
    }
}
