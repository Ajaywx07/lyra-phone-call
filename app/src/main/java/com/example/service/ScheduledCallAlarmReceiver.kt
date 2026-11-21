package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Receives exact [android.app.AlarmManager] triggers for scheduled calls and immediately
 * hands execution to [ScheduledCallService] (a Foreground Service with a bounded WakeLock)
 * so calls are processed reliably on time even if the app was closed or killed.
 */
class ScheduledCallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val callId = intent?.getLongExtra(ScheduledCallScheduler.EXTRA_SCHEDULED_CALL_ID, -1L) ?: -1L
        if (callId <= 0L) return

        ScheduledCallService.acquireWakeLock(context)
        val serviceIntent = Intent(context, ScheduledCallService::class.java).apply {
            action = ScheduledCallScheduler.ACTION_TRIGGER_SCHEDULED_CALL
            putExtra(ScheduledCallScheduler.EXTRA_SCHEDULED_CALL_ID, callId)
        }
        val started = runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
            true
        }.getOrDefault(false)

        if (!started) {
            ScheduledCallService.releaseWakeLock()
        }
    }
}
