package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.ScheduledCall
import com.example.utils.PermissionHelper

object ScheduledCallScheduler {

    const val EXTRA_SCHEDULED_CALL_ID = "extra_scheduled_call_id"
    const val ACTION_TRIGGER_SCHEDULED_CALL = "com.ajay.lyra.ACTION_TRIGGER_SCHEDULED_CALL"
    const val ACTION_OPEN_SCHEDULED_CALLS = "com.ajay.lyra.ACTION_OPEN_SCHEDULED_CALLS"

    fun scheduleAlarm(context: Context, call: ScheduledCall) {
        if (call.id <= 0L || call.status != "PENDING") return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, call.id) ?: return
        val triggerAt = call.scheduledAtMillis.coerceAtLeast(System.currentTimeMillis() + 1000L)

        runCatching {
            val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                PermissionHelper.canScheduleExactAlarms(context)

            if (canUseExact) {
                // setAlarmClock wakes the device out of Doze precisely on time and grants
                // a background-FGS launch exemption when ScheduledCallAlarmReceiver triggers.
                val showIntent = buildShowPendingIntent(context, call.id)
                val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
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
        val dao = LyraDatabase.getDatabase(context).scheduledCallDao()
        val now = System.currentTimeMillis()
        val pending = dao.getPendingScheduledCalls()
        for (call in pending) {
            if (call.scheduledAtMillis < now - 120_000L) {
                dao.updateScheduledCall(
                    call.copy(
                        status = "FAILED",
                        lastAttemptMillis = now,
                        statusMessage = "Scheduled time elapsed while device was off or backgrounded."
                    )
                )
            } else {
                scheduleAlarm(context, call)
            }
        }
    }

    private fun buildPendingIntent(context: Context, callId: Long): PendingIntent? {
        val intent = Intent(context, ScheduledCallAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_SCHEDULED_CALL
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
