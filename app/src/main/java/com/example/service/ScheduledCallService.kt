package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.data.local.database.LyraDatabase
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import com.example.utils.SimManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Dedicated Foreground Service triggered by [ScheduledCallAlarmReceiver] when an exact
 * [android.app.AlarmManager] alarm fires. Wakes the device cleanly with a bounded WakeLock
 * and processes scheduled outgoing Telecom calls even if Lyra was closed or killed by the system.
 */
class ScheduledCallService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var isForegroundStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getLongExtra(ScheduledCallScheduler.EXTRA_SCHEDULED_CALL_ID, -1L) ?: -1L

        promoteToForeground(targetName = null, phoneNumber = null)

        if (callId <= 0L) {
            stopForegroundAndRelease(startId)
            return START_NOT_STICKY
        }

        acquireWakeLock(applicationContext)
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            try {
                processScheduledCall(callId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopForegroundAndRelease(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        activeJob?.cancel()
        stopForegroundAndRelease(startId)
        super.onTimeout(startId, fgsType)
    }

    private fun promoteToForeground(targetName: String?, phoneNumber: String?) {
        val notification = NotificationHelper.buildScheduledCallProcessingNotification(
            context = applicationContext,
            targetName = targetName,
            phoneNumber = phoneNumber
        )
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }

        if (!isForegroundStarted) {
            runCatching {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_ID_SCHEDULED_SERVICE,
                    notification,
                    foregroundType
                )
                isForegroundStarted = true
            }
        } else if (PermissionHelper.hasNotificationPermission(this)) {
            runCatching {
                NotificationManagerCompat.from(this).notify(
                    NotificationHelper.NOTIFICATION_ID_SCHEDULED_SERVICE,
                    notification
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun processScheduledCall(callId: Long) {
        val dao = LyraDatabase.getDatabase(applicationContext).scheduledCallDao()
        val scheduled = dao.getScheduledCallById(callId) ?: return
        if (scheduled.status != "PENDING") return

        promoteToForeground(
            targetName = scheduled.contactName.takeIf { it.isNotBlank() },
            phoneNumber = scheduled.phoneNumber
        )

        val now = System.currentTimeMillis()
        val cleanNumber = PhoneNumberUtils.normalize(scheduled.phoneNumber)
        if (cleanNumber.isBlank()) {
            dao.updateScheduledCall(
                scheduled.copy(
                    status = "FAILED",
                    lastAttemptMillis = now,
                    statusMessage = "Invalid phone number."
                )
            )
            return
        }

        val hasCallPerm = PermissionHelper.hasCallPhonePermission(applicationContext) ||
            PermissionHelper.isDefaultDialer(applicationContext)

        if (!hasCallPerm) {
            val reason = "CALL_PHONE permission or Default Phone role required for auto-dial."
            dao.updateScheduledCall(
                scheduled.copy(
                    status = "FAILED",
                    lastAttemptMillis = now,
                    statusMessage = reason
                )
            )
            postFallbackNotification(scheduled.contactName, cleanNumber, reason, callId)
            return
        }

        val telecomManager = applicationContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecomManager == null) {
            val reason = "Telecom service unavailable on this device."
            dao.updateScheduledCall(
                scheduled.copy(
                    status = "FAILED",
                    lastAttemptMillis = now,
                    statusMessage = reason
                )
            )
            postFallbackNotification(scheduled.contactName, cleanNumber, reason, callId)
            return
        }

        val handle = SimManager.resolveHandleForSimKey(applicationContext, scheduled.simKey)
        val extras = Bundle().apply {
            if (handle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
        }
        val uri = Uri.fromParts("tel", cleanNumber, null)

        val outcome = runCatching {
            telecomManager.placeCall(uri, extras)
        }

        if (outcome.isSuccess) {
            dao.updateScheduledCall(
                scheduled.copy(
                    status = "PLACED",
                    lastAttemptMillis = now,
                    statusMessage = "Call placed on time via Foreground Service (${scheduled.simKey.replace("_", " ")})"
                )
            )
        } else {
            val err = outcome.exceptionOrNull()
            val reason = err?.localizedMessage
                ?: "Blocked by Android lock-screen or background call policy. Tap Call Now."
            dao.updateScheduledCall(
                scheduled.copy(
                    status = "FAILED",
                    lastAttemptMillis = now,
                    statusMessage = reason
                )
            )
            postFallbackNotification(scheduled.contactName, cleanNumber, reason, callId)
        }
    }

    @SuppressLint("MissingPermission")
    private fun postFallbackNotification(
        targetName: String,
        phoneNumber: String,
        reason: String,
        callId: Long
    ) {
        if (!PermissionHelper.hasNotificationPermission(applicationContext)) return
        val notification = NotificationHelper.buildScheduledCallFallbackNotification(
            context = applicationContext,
            targetName = targetName.ifBlank { phoneNumber },
            phoneNumber = phoneNumber,
            reason = reason
        )
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NotificationHelper.NOTIFICATION_ID_SCHEDULED_CALL + callId.toInt(), notification)
        }
    }

    private fun stopForegroundAndRelease(startId: Int) {
        releaseWakeLock()
        if (isForegroundStarted) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            isForegroundStarted = false
        }
        NotificationManagerCompat.from(applicationContext)
            .cancel(NotificationHelper.NOTIFICATION_ID_SCHEDULED_SERVICE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        if (isForegroundStarted) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            isForegroundStarted = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val WAKE_LOCK_TAG = "Lyra:ScheduledCallWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 20_000L

        @Volatile
        private var activeWakeLock: PowerManager.WakeLock? = null
        private val wakeLockMonitor = Any()

        fun acquireWakeLock(context: Context) {
            synchronized(wakeLockMonitor) {
                val existing = activeWakeLock
                if (existing != null && existing.isHeld) {
                    return
                }
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    ?: return
                runCatching {
                    val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                        setReferenceCounted(false)
                        acquire(WAKE_LOCK_TIMEOUT_MS)
                    }
                    activeWakeLock = wl
                }
            }
        }

        fun releaseWakeLock() {
            synchronized(wakeLockMonitor) {
                runCatching {
                    activeWakeLock?.let { wl ->
                        if (wl.isHeld) wl.release()
                    }
                }
                activeWakeLock = null
            }
        }
    }
}
