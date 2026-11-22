package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.UserManager
import com.example.MainActivity
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.ScheduledCall
import com.example.utils.PermissionHelper
import java.util.Calendar

data class ScheduleAlarmResult(
    val isExact: Boolean,
    val diagnosticMessage: String
)

object ScheduledCallScheduler {

    const val EXTRA_SCHEDULED_CALL_ID = "extra_scheduled_call_id"
    const val ACTION_TRIGGER_SCHEDULED_CALL = "com.ajay.lyra.ACTION_TRIGGER_SCHEDULED_CALL"
    const val ACTION_OPEN_SCHEDULED_CALLS = "com.ajay.lyra.ACTION_OPEN_SCHEDULED_CALLS"
    private const val OVERDUE_GRACE_WINDOW_MS = 120_000L

    /**
     * Snaps a timestamp down to :00.000 seconds so a call picked for 12:33 PM fires at
     * 12:33:00.000 rather than carrying over arbitrary :45s/:58s creation-time jitter.
     */
    fun normalizeToMinuteStart(epochMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun scheduleAlarm(context: Context, call: ScheduledCall): ScheduleAlarmResult {
        if (call.id <= 0L || call.status != "PENDING") {
            return ScheduleAlarmResult(
                isExact = false,
                diagnosticMessage = "Not scheduled (invalid ID or non-pending status)"
            )
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return ScheduleAlarmResult(
                isExact = false,
                diagnosticMessage = "AlarmManager unavailable"
            )
        val pendingIntent = buildPendingIntent(context, call.id)
            ?: return ScheduleAlarmResult(
                isExact = false,
                diagnosticMessage = "Failed to allocate PendingIntent"
            )

        val now = System.currentTimeMillis()
        val triggerAt = call.scheduledAtMillis.coerceAtLeast(now + 1000L)

        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            PermissionHelper.canScheduleExactAlarms(context)

        return runCatching {
            if (canUseExact) {
                // setAlarmClock wakes the CPU out of Deep Doze and Battery Saver right on time
                // and grants a background ForegroundService launch exemption on trigger.
                val showIntent = buildShowPendingIntent(context, call.id)
                val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
                ScheduleAlarmResult(
                    isExact = true,
                    diagnosticMessage = "Exact alarm armed (AlarmClock • Doze-exempt)"
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                ScheduleAlarmResult(
                    isExact = false,
                    diagnosticMessage = "Inexact fallback — grant 'Alarms & reminders' for exact-second precision"
                )
            }
        }.getOrElse { error ->
            ScheduleAlarmResult(
                isExact = false,
                diagnosticMessage = error.localizedMessage ?: "Failed to schedule alarm"
            )
        }
    }

    fun cancelAlarm(context: Context, callId: Long) {
        if (callId <= 0L) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, callId) ?: return
        runCatching { alarmManager.cancel(pendingIntent) }
        pendingIntent.cancel()
        buildShowPendingIntent(context, callId)?.cancel()
    }

    suspend fun rescheduleAllPending(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager != null && !userManager.isUserUnlocked) {
                return
            }
        }
        val dao = LyraDatabase.getDatabase(context).scheduledCallDao()
        val now = System.currentTimeMillis()
        val pending = dao.getPendingScheduledCalls()
        for (call in pending) {
            if (call.scheduledAtMillis < now - OVERDUE_GRACE_WINDOW_MS) {
                dao.updateScheduledCall(
                    call.copy(
                        status = "FAILED",
                        lastAttemptMillis = now,
                        statusMessage = "Scheduled time elapsed while device was off or backgrounded."
                    )
                )
            } else {
                val result = scheduleAlarm(context, call)
                dao.updateScheduledCall(
                    call.copy(statusMessage = result.diagnosticMessage)
                )
            }
        }
    }

    private fun buildPendingIntent(context: Context, callId: Long): PendingIntent? {
        val intent = Intent(context, ScheduledCallAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_SCHEDULED_CALL
            data = Uri.parse("lyra://scheduled_call/$callId")
            putExtra(EXTRA_SCHEDULED_CALL_ID, callId)
        }
        return PendingIntent.getBroadcast(
            context,
            (40_000 + (callId % 50_000)).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildShowPendingIntent(context: Context, callId: Long): PendingIntent? {
        val showIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SCHEDULED_CALLS
            data = Uri.parse("lyra://scheduled_call_ui/$callId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            (50_000 + (callId % 50_000)).toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
