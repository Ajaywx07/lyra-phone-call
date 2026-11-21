package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

data class SimSlotInfo(
    val slotIndex: Int,      // 1 or 2
    val simKey: String,      // "SIM_1" or "SIM_2"
    val label: String,       // "SIM 1" or carrier name
    val carrierName: String,
    val number: String? = null,
    val accountHandle: PhoneAccountHandle? = null,
    val isDetectedOnDevice: Boolean = false
)

object SimManager {

    const val SIM_ASK = "ASK"
    const val SIM_1 = "SIM_1"
    const val SIM_2 = "SIM_2"

    @SuppressLint("MissingPermission")
    fun getAvailableSims(context: Context): List<SimSlotInfo> {
        val result = mutableListOf<SimSlotInfo>()

        val subList: List<SubscriptionInfo> = if (PermissionHelper.hasPhonePermission(context)) {
            runCatching {
                val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                subMgr?.activeSubscriptionInfoList.orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val handles: List<PhoneAccountHandle> = if (PermissionHelper.hasPhonePermission(context)) {
            runCatching {
                val telecomMgr = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomMgr?.callCapablePhoneAccounts.orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val telecomMgr = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        if (subList.isNotEmpty()) {
            subList.sortedBy { it.simSlotIndex }.forEachIndexed { idx, info ->
                val slotNum = if (info.simSlotIndex >= 0) info.simSlotIndex + 1 else idx + 1
                val simKey = if (slotNum == 2) SIM_2 else SIM_1
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM $slotNum"
                val matchedHandle = handles.getOrNull(idx)
                val subNum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    PermissionHelper.hasPhoneNumbersPermission(context)
                ) {
                    runCatching {
                        val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                        subMgr?.getPhoneNumber(info.subscriptionId)
                    }.getOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    info.number
                }
                result.add(
                    SimSlotInfo(
                        slotIndex = slotNum,
                        simKey = simKey,
                        label = "SIM $slotNum",
                        carrierName = carrier,
                        number = subNum?.takeIf { it.isNotBlank() },
                        accountHandle = matchedHandle,
                        isDetectedOnDevice = true
                    )
                )
            }
        } else if (handles.isNotEmpty()) {
            handles.take(2).forEachIndexed { idx, handle ->
                val slotNum = idx + 1
                val simKey = if (slotNum == 2) SIM_2 else SIM_1
                val accountLabel = runCatching {
                    telecomMgr?.getPhoneAccount(handle)?.label?.toString()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "SIM $slotNum"
                result.add(
                    SimSlotInfo(
                        slotIndex = slotNum,
                        simKey = simKey,
                        label = "SIM $slotNum",
                        carrierName = accountLabel,
                        accountHandle = handle,
                        isDetectedOnDevice = true
                    )
                )
            }
        }

        // Always provide clear SIM 1 & SIM 2 selection options for Dual-SIM configuration UX
        if (result.none { it.simKey == SIM_1 }) {
            result.add(
                0,
                SimSlotInfo(
                    slotIndex = 1,
                    simKey = SIM_1,
                    label = "SIM 1",
                    carrierName = "Primary SIM",
                    accountHandle = handles.getOrNull(0),
                    isDetectedOnDevice = handles.isNotEmpty()
                )
            )
        }
        if (result.none { it.simKey == SIM_2 }) {
            result.add(
                SimSlotInfo(
                    slotIndex = 2,
                    simKey = SIM_2,
                    label = "SIM 2",
                    carrierName = "Secondary SIM",
                    accountHandle = handles.getOrNull(1),
                    isDetectedOnDevice = handles.size > 1
                )
            )
        }

        return result.sortedBy { it.slotIndex }
    }

    fun resolveHandleForSimKey(context: Context, simKey: String): PhoneAccountHandle? {
        if (simKey == SIM_ASK) return null
        return getAvailableSims(context).firstOrNull { it.simKey == simKey }?.accountHandle
    }

    fun displayLabelForSimKey(context: Context, simKey: String): String {
        if (simKey == SIM_ASK) return "Ask Every Time"
        val match = getAvailableSims(context).firstOrNull { it.simKey == simKey }
        return if (match != null) "${match.label} (${match.carrierName})" else simKey.replace("_", " ")
    }

    @SuppressLint("MissingPermission")
    fun buildAccountIdToSimLabelMap(context: Context): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        if (!PermissionHelper.hasPhonePermission(context)) return map

        val subList: List<SubscriptionInfo> = runCatching {
            val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            subMgr?.activeSubscriptionInfoList.orEmpty()
        }.getOrDefault(emptyList())

        val telecomMgr = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val handles: List<PhoneAccountHandle> = runCatching {
            telecomMgr?.callCapablePhoneAccounts.orEmpty()
        }.getOrDefault(emptyList())

        val sortedSubs = subList.sortedBy { it.simSlotIndex }
        sortedSubs.forEachIndexed { idx, info ->
            val slotNum = if (info.simSlotIndex >= 0) info.simSlotIndex + 1 else idx + 1
            val label = "SIM $slotNum"
            map[info.subscriptionId.toString()] = label
            @Suppress("DEPRECATION")
            val iccId = info.iccId
            if (!iccId.isNullOrBlank()) {
                map[iccId] = label
            }
            handles.getOrNull(idx)?.id?.takeIf { it.isNotBlank() }?.let { handleId ->
                map.putIfAbsent(handleId, label)
            }
        }

        handles.forEachIndexed { idx, handle ->
            val handleId = handle.id
            if (handleId.isNotBlank() && !map.containsKey(handleId)) {
                val slotNum = idx + 1
                map[handleId] = "SIM $slotNum"
            }
        }

        return map
    }

    fun isDualSimDevice(context: Context): Boolean {
        return getAvailableSims(context).count { it.isDetectedOnDevice } >= 2
    }

    fun resolveAccountLabel(context: Context, handle: PhoneAccountHandle?): String {
        if (handle == null) return "SIM 1"
        val map = buildAccountIdToSimLabelMap(context)
        resolveSimLabelFromAccountId(handle.id, map, includeSingleSimFallback = true)?.let { return it }
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val rawLabel = runCatching { telecom?.getPhoneAccount(handle)?.label?.toString() }.getOrNull()
        return rawLabel?.takeIf { it.isNotBlank() } ?: "SIM 1"
    }

    fun resolveSimLabelFromAccountId(
        phoneAccountId: String?,
        accountIdMap: Map<String, String>,
        includeSingleSimFallback: Boolean = false
    ): String? {
        val raw = phoneAccountId?.trim()?.takeIf { it.isNotBlank() } ?: return null

        // Direct lookup from SubscriptionInfo / Telecom PhoneAccountHandle mapping
        accountIdMap[raw]?.let { return it }
        accountIdMap.entries.firstOrNull { (key, _) ->
            key.equals(raw, ignoreCase = true) ||
                (key.length >= 6 && raw.contains(key, ignoreCase = true)) ||
                (raw.length >= 6 && key.contains(raw, ignoreCase = true))
        }?.let { return it.value }

        // Pattern fallback for OEM/Telecom PhoneAccount IDs like "sim_1", "SIM 2", "account_sim1", "slot_2", etc.
        val normalized = raw.lowercase()
        val looksLikeSim2 = normalized.contains("sim 2") ||
            normalized.contains("sim_2") ||
            normalized.endsWith("sim2") ||
            normalized.contains("slot_2") ||
            normalized.contains("slot 2") ||
            normalized.endsWith("slot2") ||
            raw == "2"
        val looksLikeSim1 = normalized.contains("sim 1") ||
            normalized.contains("sim_1") ||
            normalized.endsWith("sim1") ||
            normalized.contains("slot_1") ||
            normalized.contains("slot 1") ||
            normalized.endsWith("slot1") ||
            raw == "1" ||
            raw == "0"
        return when {
            looksLikeSim2 -> "SIM 2"
            looksLikeSim1 -> "SIM 1"
            else -> null
        }
    }
}
