package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Helpers for external actions (WhatsApp direct chat) and carrier USSD request handling.
 */
object ExternalIntentHelper {

    private const val TAG = "ExternalIntentHelper"

    /**
     * Strips formatting characters cleanly to produce WhatsApp-compatible phone digits.
     * Leaves country prefixes intact if present; drops leading zeros or `+`.
     */
    fun formatWhatsAppDigits(rawPhone: String, defaultCountryCode: String = "91"): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isBlank()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isBlank()) return ""
        return when {
            hasPlus -> digitsOnly
            digitsOnly.startsWith("00") && digitsOnly.length > 4 -> digitsOnly.drop(2)
            digitsOnly.length == 10 && defaultCountryCode.isNotBlank() -> "$defaultCountryCode$digitsOnly"
            digitsOnly.length == 11 && digitsOnly.startsWith("0") && defaultCountryCode.isNotBlank() ->
                "$defaultCountryCode${digitsOnly.drop(1)}"
            else -> digitsOnly
        }
    }

    fun buildWhatsAppUri(rawPhone: String): Uri? {
        val digits = formatWhatsAppDigits(rawPhone)
        if (digits.isBlank()) return null
        return Uri.parse("https://api.whatsapp.com/send?phone=$digits")
    }

    fun openWhatsAppChat(context: Context, rawPhone: String): Boolean {
        val digits = formatWhatsAppDigits(rawPhone)
        if (digits.isBlank()) return false

        val directSchemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedDirect = runCatching {
            context.startActivity(directSchemeIntent)
            true
        }.getOrDefault(false)
        if (launchedDirect) return true

        val webFallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(webFallbackIntent)
            true
        }.getOrDefault(false)
    }

    /**
     * Matches standard carrier USSD / MMI codes such as *121#, *#06#, *123*1#, or #31#.
     */
    fun isUssdCode(input: String?): Boolean {
        val clean = input?.trim().orEmpty()
        if (clean.length < 3 || !clean.endsWith("#")) return false
        return clean.startsWith("*") || clean.startsWith("#")
    }

    @SuppressLint("MissingPermission")
    fun executeUssdRequest(
        context: Context,
        ussdCode: String,
        simSlotPreference: String = "ASK",
        onResult: (Result<String>) -> Unit
    ) {
        val cleanCode = ussdCode.trim()
        if (!isUssdCode(cleanCode)) {
            onResult(Result.failure(IllegalArgumentException("Not a valid USSD code.")))
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !PermissionHelper.hasCallPhonePermission(context)) {
            val dialed = dialUssdFallback(context, cleanCode)
            if (dialed) {
                onResult(Result.success("Dispatched $cleanCode via system phone handler."))
            } else {
                onResult(Result.failure(IllegalStateException("CALL_PHONE permission is required to run USSD codes.")))
            }
            return
        }

        val baseTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (baseTelephonyManager == null) {
            onResult(Result.failure(IllegalStateException("Telephony service is unavailable.")))
            return
        }

        val telephonyForSim = resolveTelephonyManagerForSim(context, baseTelephonyManager, simSlotPreference)
        val callbackHandler = Handler(Looper.getMainLooper())

        try {
            telephonyForSim.sendUssdRequest(
                cleanCode,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        val message = response.toString().trim().ifBlank { "Carrier completed USSD request." }
                        onResult(Result.success(message))
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        Log.w(TAG, "USSD failed ($failureCode) for $request, attempting dial fallback")
                        val codeHint = when (failureCode) {
                            TelephonyManager.USSD_RETURN_FAILURE -> "Carrier returned an error or MMI code could not complete on this SIM."
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "Mobile network USSD service is currently unavailable."
                            else -> "USSD request failed (code $failureCode)."
                        }
                        onResult(Result.failure(IllegalStateException(codeHint)))
                    }
                },
                callbackHandler
            )
        } catch (t: Throwable) {
            Log.e(TAG, "sendUssdRequest threw exception", t)
            if (dialUssdFallback(context, cleanCode)) {
                onResult(Result.success("Sent $cleanCode to system dialer."))
            } else {
                onResult(Result.failure(t))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveTelephonyManagerForSim(
        context: Context,
        defaultTm: TelephonyManager,
        simSlotPreference: String
    ): TelephonyManager {
        val targetSlotIndex = when (simSlotPreference.uppercase()) {
            "SIM_1" -> 0
            "SIM_2" -> 1
            else -> return defaultTm
        }
        return runCatching {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val sub = sm?.activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == targetSlotIndex }
            if (sub != null) defaultTm.createForSubscriptionId(sub.subscriptionId) else defaultTm
        }.getOrDefault(defaultTm)
    }

    @SuppressLint("MissingPermission")
    fun dialUssdFallback(context: Context, ussdCode: String): Boolean {
        val encoded = Uri.encode(ussdCode.trim())
        val action = if (PermissionHelper.hasCallPhonePermission(context)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
