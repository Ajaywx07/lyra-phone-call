package com.example

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.LyraDatabase
import com.example.data.local.entity.ScheduledCall
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.service.BootCompletedReceiver
import com.example.service.NotificationHelper
import com.example.service.ScheduledCallAlarmReceiver
import com.example.service.ScheduledCallScheduler
import com.example.service.ScheduledCallService
import com.example.utils.PermissionHelper
import com.example.utils.SimManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScheduledCallReliabilityTest {

    @Test
    fun normalizeToMinuteStart_snapsArbitrarySecondsAndMillisToZero() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 12, 33, 47)
            set(Calendar.MILLISECOND, 819)
        }
        val snapped = ScheduledCallScheduler.normalizeToMinuteStart(cal.timeInMillis)
        val verifyCal = Calendar.getInstance().apply { timeInMillis = snapped }

        assertEquals(12, verifyCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(33, verifyCal.get(Calendar.MINUTE))
        assertEquals(0, verifyCal.get(Calendar.SECOND))
        assertEquals(0, verifyCal.get(Calendar.MILLISECOND))
    }

    @Test
    @Config(sdk = [30])
    fun scheduleAlarm_armsExactAlarmClockWithoutOverwritingMultiDaySchedules() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = Shadows.shadowOf(alarmManager)

        val threeDaysLater = ScheduledCallScheduler.normalizeToMinuteStart(
            System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L
        )
        val callOne = ScheduledCall(
            id = 101L,
            contactName = "BOSS",
            phoneNumber = "+918795989222",
            simKey = SimManager.SIM_1,
            scheduledAtMillis = threeDaysLater,
            status = "PENDING"
        )
        val callTwo = callOne.copy(
            id = 102L,
            scheduledAtMillis = threeDaysLater + 120_000L
        )

        val resultOne = ScheduledCallScheduler.scheduleAlarm(context, callOne)
        assertTrue(resultOne.isExact)
        assertTrue(resultOne.diagnosticMessage.contains("AlarmClock", ignoreCase = true))

        val nextClock = alarmManager.nextAlarmClock
        assertNotNull(nextClock)
        assertEquals(threeDaysLater, nextClock?.triggerTime)

        val resultTwo = ScheduledCallScheduler.scheduleAlarm(context, callTwo)
        assertTrue(resultTwo.isExact)
        assertEquals(2, shadowAlarm.scheduledAlarms.size)

        ScheduledCallScheduler.cancelAlarm(context, 101L)
        ScheduledCallScheduler.cancelAlarm(context, 102L)
    }

    @Test
    fun scheduleAlarm_whenExactAlarmDeniedOnAndroid12Plus_reportsHonestInexactDiagnostic() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        if (PermissionHelper.canScheduleExactAlarms(context)) {
            return
        }
        val futureCall = ScheduledCall(
            id = 201L,
            contactName = "BOSS",
            phoneNumber = "+918795989222",
            simKey = SimManager.SIM_1,
            scheduledAtMillis = ScheduledCallScheduler.normalizeToMinuteStart(
                System.currentTimeMillis() + 300_000L
            ),
            status = "PENDING"
        )

        val result = ScheduledCallScheduler.scheduleAlarm(context, futureCall)
        assertFalse(result.isExact)
        assertTrue(result.diagnosticMessage.contains("Inexact fallback", ignoreCase = true))
        ScheduledCallScheduler.cancelAlarm(context, 201L)
    }

    @Test
    fun scheduledCallAlarmReceiver_forwardsCallIdAndUriFallbackToForegroundService() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(context)
        ScheduledCallService.releaseWakeLock()

        val receiver = ScheduledCallAlarmReceiver()
        val uriOnlyIntent = Intent(ScheduledCallScheduler.ACTION_TRIGGER_SCHEDULED_CALL).apply {
            data = Uri.parse("lyra://scheduled_call/42")
        }
        receiver.onReceive(context, uriOnlyIntent)

        val startedIntent = shadowApp.nextStartedService
        assertNotNull(startedIntent)
        assertEquals(ScheduledCallService::class.java.name, startedIntent?.component?.className)
        assertEquals(42L, startedIntent?.getLongExtra(ScheduledCallScheduler.EXTRA_SCHEDULED_CALL_ID, -1L))
        ScheduledCallService.releaseWakeLock()
    }

    @Test
    fun simManager_resolveFallbackHandle_picksCapableAccountEvenWhenSimKeyIsAsk() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val handle1 = PhoneAccountHandle(ComponentName(context, MainActivity::class.java), "sim_slot_1")
        val handle2 = PhoneAccountHandle(ComponentName(context, MainActivity::class.java), "sim_slot_2")
        val sims = listOf(
            com.example.utils.SimSlotInfo(
                slotIndex = 1,
                simKey = SimManager.SIM_1,
                label = "SIM 1",
                carrierName = "Jio",
                accountHandle = handle1,
                isDetectedOnDevice = true
            ),
            com.example.utils.SimSlotInfo(
                slotIndex = 2,
                simKey = SimManager.SIM_2,
                label = "SIM 2",
                carrierName = "Airtel",
                accountHandle = handle2,
                isDetectedOnDevice = true
            )
        )

        // Requested SIM 2 always honors SIM 2 even if SIM 1 is system default
        assertEquals(
            handle2,
            SimManager.resolveFallbackHandleFromAccounts(
                simKey = SimManager.SIM_2,
                availableSims = sims,
                defaultOutgoingHandle = handle1,
                callCapableHandles = listOf(handle1, handle2)
            )
        )

        // "ASK" with system default picks system default
        assertEquals(
            handle1,
            SimManager.resolveFallbackHandleFromAccounts(
                simKey = SimManager.SIM_ASK,
                availableSims = sims,
                defaultOutgoingHandle = handle1,
                callCapableHandles = listOf(handle1, handle2)
            )
        )

        // "ASK" without system default ("Ask Every Time" on Dual-SIM) falls back to first capable account
        assertEquals(
            handle2,
            SimManager.resolveFallbackHandleFromAccounts(
                simKey = SimManager.SIM_ASK,
                availableSims = sims,
                defaultOutgoingHandle = null,
                callCapableHandles = listOf(handle2)
            )
        )
    }

    @Test
    @Config(sdk = [33])
    fun notificationHelper_attachesFullScreenIntentOnlyForDialingWhenRequested() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val caller = CallerInfo(
            phoneNumber = "+918795989222",
            formattedNumber = "+91 87959 89222",
            displayName = "BOSS",
            isKnown = true
        )

        val dialingFsiNotification = NotificationHelper.buildOngoingCallNotification(
            context = context,
            caller = caller,
            callState = CallStateEnum.DIALING,
            callStartTimeMs = 0L,
            isMuted = false,
            isSpeakerOn = false,
            showCallerPhoto = false,
            useFullScreenIntent = true
        )
        assertNotNull(dialingFsiNotification.fullScreenIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(NotificationHelper.CHANNEL_SCHEDULED_ALERTS, dialingFsiNotification.channelId)
        }

        val normalOngoingNotification = NotificationHelper.buildOngoingCallNotification(
            context = context,
            caller = caller,
            callState = CallStateEnum.ACTIVE,
            callStartTimeMs = System.currentTimeMillis(),
            isMuted = false,
            isSpeakerOn = false,
            showCallerPhoto = false,
            useFullScreenIntent = false
        )
        assertNull(normalOngoingNotification.fullScreenIntent)
        assertTrue((normalOngoingNotification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test
    fun manifest_keepsMainActivityOffLockScreen_whileCallActivityHandlesLockedCalls() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pm = context.packageManager

        val mainInfo = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val callInfo = pm.getActivityInfo(ComponentName(context, CallActivity::class.java), 0)

        // android:showWhenLocked compiles into ActivityInfo private flag 0x00800000
        val flagShowWhenLockedInManifest = 0x00800000
        assertEquals(0, mainInfo.flags and flagShowWhenLockedInManifest)
        assertTrue((callInfo.flags and flagShowWhenLockedInManifest) != 0)

        val mainActivity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        @Suppress("DEPRECATION")
        assertEquals(0, mainActivity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }

    @Test
    @Config(sdk = [30])
    fun rescheduleAllPending_rearmsFutureCallsAcrossClockOrBootChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val dao = LyraDatabase.getDatabase(context).scheduledCallDao()
        val targetTime = ScheduledCallScheduler.normalizeToMinuteStart(System.currentTimeMillis() + 600_000L)
        val insertedId = dao.insertScheduledCall(
            ScheduledCall(
                contactName = "Dr. Rohan",
                phoneNumber = "+919811122334",
                simKey = SimManager.SIM_1,
                scheduledAtMillis = targetTime,
                status = "PENDING",
                statusMessage = "Initial"
            )
        )

        ScheduledCallScheduler.rescheduleAllPending(context)

        val updated = dao.getScheduledCallById(insertedId)
        assertNotNull(updated)
        assertEquals("PENDING", updated?.status)
        assertTrue(updated?.statusMessage?.contains("AlarmClock") == true)
        assertNotNull(alarmManager.nextAlarmClock)

        val receiver = BootCompletedReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
    }
}
