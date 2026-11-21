package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object QuickSmsDispatcher {
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun emitStatus(message: String?) {
        _statusMessage.value = message
    }
}

object SmsDispatchHelper {
    const val ACTION_SMS_SENT = "com.ajay.lyra.ACTION_QUICK_SMS_SENT"
    const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    const val EXTRA_MESSAGE_BODY = "extra_message_body"

    fun buildSmsSendToIntent(phoneNumber: String, body: String = ""): Intent {
        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber).ifBlank { phoneNumber.trim() }
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(cleanNumber)}")
            if (body.isNotBlank()) {
                putExtra("sms_body", body)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun launchSmsComposer(context: Context, phoneNumber: String, body: String = ""): Boolean {
        val sendToIntent = buildSmsSendToIntent(phoneNumber, body)
        val launchedSendTo = runCatching {
            context.startActivity(sendToIntent)
            true
        }.getOrDefault(false)
        if (launchedSendTo) return true

        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber).ifBlank { phoneNumber.trim() }
        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:$cleanNumber")
            if (body.isNotBlank()) {
                putExtra("sms_body", body)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return runCatching {
            context.startActivity(fallbackIntent)
            true
        }.getOrDefault(false)
    }

    fun sendMultipartSmsDirectly(
        context: Context,
        phoneNumber: String,
        message: String
    ): Result<Unit> {
        val cleanNumber = PhoneNumberUtils.normalize(phoneNumber).ifBlank { phoneNumber.trim() }
        if (cleanNumber.isBlank() || message.isBlank()) {
            return Result.failure(IllegalArgumentException("Recipient number and message cannot be blank."))
        }
        if (!PermissionHelper.hasSendSmsPermission(context)) {
            return Result.failure(SecurityException("SEND_SMS permission is required to auto-send SMS."))
        }

        return runCatching {
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            requireNotNull(smsManager) { "SmsManager unavailable on this device." }

            val parts = smsManager.divideMessage(message)
            val sentIntent = PendingIntent.getBroadcast(
                context,
                cleanNumber.hashCode(),
                Intent(context, QuickSmsStatusReceiver::class.java).apply {
                    action = ACTION_SMS_SENT
                    putExtra(EXTRA_PHONE_NUMBER, cleanNumber)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>(parts.size).apply {
                    repeat(parts.size) { add(sentIntent) }
                }
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, message, sentIntent, null)
            }
        }
    }
}

class QuickSmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsDispatchHelper.ACTION_SMS_SENT) return
        val number = intent.getStringExtra(SmsDispatchHelper.EXTRA_PHONE_NUMBER).orEmpty()
        val label = PhoneNumberUtils.formatDisplay(number).ifBlank { number }
        if (resultCode == Activity.RESULT_OK) {
            QuickSmsDispatcher.emitStatus("Quick response SMS sent to $label ✓")
        } else {
            QuickSmsDispatcher.emitStatus("Quick SMS delivery failed (code $resultCode) for $label")
        }
    }
}

class QuickSmsService : Service() {

    companion object {
        private const val CHANNEL_ID = "lyra_quick_sms_channel"
        private const val NOTIFICATION_ID = 4109

        fun startSendSms(context: Context, phoneNumber: String, message: String) {
            val intent = Intent(context, QuickSmsService::class.java).apply {
                putExtra(SmsDispatchHelper.EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(SmsDispatchHelper.EXTRA_MESSAGE_BODY, message)
            }
            val startedService = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrDefault(false)

            if (!startedService) {
                val cleanDisplay = PhoneNumberUtils.formatDisplay(phoneNumber).ifBlank { phoneNumber }
                SmsDispatchHelper.sendMultipartSmsDirectly(context, phoneNumber, message)
                    .onSuccess {
                        QuickSmsDispatcher.emitStatus("Sending quick response to $cleanDisplay...")
                    }
                    .onFailure {
                        SmsDispatchHelper.launchSmsComposer(context, phoneNumber, message)
                    }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra(SmsDispatchHelper.EXTRA_PHONE_NUMBER).orEmpty()
        val message = intent?.getStringExtra(SmsDispatchHelper.EXTRA_MESSAGE_BODY).orEmpty()
        val displayTarget = PhoneNumberUtils.formatDisplay(number).ifBlank { number }

        val notification = buildNotification(displayTarget)
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }
        val promoted = runCatching {
            androidx.core.app.ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType
            )
            true
        }.getOrDefault(false)

        if (number.isNotBlank() && message.isNotBlank()) {
            val result = SmsDispatchHelper.sendMultipartSmsDirectly(this, number, message)
            result.onSuccess {
                QuickSmsDispatcher.emitStatus("Sending quick response to $displayTarget...")
            }.onFailure { err ->
                QuickSmsDispatcher.emitStatus(
                    err.localizedMessage ?: "Auto-SMS unavailable; opening SMS composer."
                )
                SmsDispatchHelper.launchSmsComposer(this, number, message)
            }
        }

        if (promoted) {
            runCatching {
                androidx.core.app.ServiceCompat.stopForeground(
                    this,
                    androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
                )
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun buildNotification(targetLabel: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                manager?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Quick Response SMS",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Sends automated quick SMS replies when declining calls"
                    }
                )
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lyra_logo)
            .setContentTitle("Sending Quick SMS")
            .setContentText(
                if (targetLabel.isNotBlank()) "Dispatching reply to $targetLabel" else "Dispatching reply"
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
