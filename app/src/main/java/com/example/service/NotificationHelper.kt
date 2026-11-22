package com.example.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.CallActivity
import com.example.LyraApplication
import com.example.MainActivity
import com.example.data.local.preferences.LyraPreferences
import com.example.domain.model.CallStateEnum
import com.example.domain.model.CallerInfo
import com.example.domain.model.UnreadMissedCall
import com.example.navigation.LyraDestinations
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class IncomingCallPresentationMode {
    NONE,
    FULL_SCREEN_LOCKED,
    TOP_BANNER_UNLOCKED
}

object NotificationHelper {

    const val CHANNEL_INCOMING = "lyra_incoming_channel"
    const val CHANNEL_INCOMING_SILENT = "lyra_incoming_background_status_v2"
    const val CHANNEL_ONGOING = "lyra_ongoing_call_channel"
    const val CHANNEL_ASSISTANT = "lyra_assistant_channel"
    const val CHANNEL_MISSED_CALL = "lyra_missed_call_channel"
    const val CHANNEL_SCHEDULED_ALERTS = "lyra_scheduled_alerts_v2"

    const val NOTIFICATION_ID_INCOMING = 1001
    const val NOTIFICATION_ID_ASSISTANT = 1002
    const val NOTIFICATION_ID_SCHEDULED_CALL = 1003
    const val NOTIFICATION_ID_SCHEDULED_SERVICE = 1004
    const val NOTIFICATION_ID_MISSED_CALL = 1005

    const val ACTION_OPEN_RECENTS = "com.ajay.lyra.action.OPEN_RECENTS"
    const val EXTRA_NAVIGATE_ROUTE = "lyra_navigate_route"

    private val avatarBitmapCache = LruCache<String, Bitmap>(8)

    fun isDeviceLockedOrScreenOff(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return keyguardManager?.isKeyguardLocked == true || powerManager?.isInteractive == false
    }

    fun resolveIncomingCallPresentationMode(
        isLockedOrScreenOff: Boolean,
        incomingScreenEnabled: Boolean = true,
        fullScreenUiVisible: Boolean = false
    ): IncomingCallPresentationMode {
        if (fullScreenUiVisible) return IncomingCallPresentationMode.NONE
        if (isLockedOrScreenOff) {
            return if (incomingScreenEnabled) {
                IncomingCallPresentationMode.FULL_SCREEN_LOCKED
            } else {
                IncomingCallPresentationMode.NONE
            }
        }
        return IncomingCallPresentationMode.TOP_BANNER_UNLOCKED
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING,
                "Lyra Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority persistent heads-up and lock-screen incoming call notifications"
                enableVibration(true)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val incomingSilentChannel = NotificationChannel(
                CHANNEL_INCOMING_SILENT,
                "Lyra Incoming Call Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing incoming call shade entry while Lyra banner or full-screen UI is active"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING,
                "Lyra Active Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Persistent ongoing call status notification with Mute, Speaker & Hang Up controls"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val assistantChannel = NotificationChannel(
                CHANNEL_ASSISTANT,
                "Lyra Scheduled & Assistant Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Scheduled call reminders and assistant alerts"
            }

            val missedCallChannel = NotificationChannel(
                CHANNEL_MISSED_CALL,
                "Lyra Missed Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Expandable missed call alerts with unread count and caller details"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }

            val scheduledAlertChannel = NotificationChannel(
                CHANNEL_SCHEDULED_ALERTS,
                "Lyra Scheduled Call Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority scheduled outgoing call alerts and lock-screen call handoff"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(incomingSilentChannel)
            notificationManager.createNotificationChannel(ongoingChannel)
            notificationManager.createNotificationChannel(assistantChannel)
            notificationManager.createNotificationChannel(missedCallChannel)
            notificationManager.createNotificationChannel(scheduledAlertChannel)
        }
    }

    fun launchCallActivity(context: Context, action: String) {
        val intent = Intent(context, CallActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Context-aware incoming call dispatcher:
     * - Locked / screen-off: launches full-screen CallActivity directly over the lock screen and attaches fullScreenIntent.
     * - Unlocked: shows ONLY a top/heads-up incoming call banner (Compose overlay/in-app or heads-up notification)
     *   and never launches full-screen CallActivity automatically.
     */
    fun dispatchIncomingCall(
        context: Context,
        caller: CallerInfo,
        fullscreenPreferenceEnabled: Boolean,
        incomingScreenEnabled: Boolean = true,
        backgroundCallingEnabled: Boolean = true,
        persistentNotificationEnabled: Boolean = true,
        showCallerPhoto: Boolean = true
    ) {
        createNotificationChannels(context)
        val isLockedOrScreenOff = isDeviceLockedOrScreenOff(context)
        val isLyraForeground = LyraApplication.isAppInForeground
        val isFullScreenAlreadyVisible = CallStateManager.isFullScreenIncomingUiVisible.value

        val presentationMode = resolveIncomingCallPresentationMode(
            isLockedOrScreenOff = isLockedOrScreenOff,
            incomingScreenEnabled = incomingScreenEnabled,
            fullScreenUiVisible = isFullScreenAlreadyVisible
        )

        val shouldAttachFullScreenIntent =
            presentationMode == IncomingCallPresentationMode.FULL_SCREEN_LOCKED &&
                fullscreenPreferenceEnabled

        val hasCustomComposeBannerSurface = presentationMode == IncomingCallPresentationMode.TOP_BANNER_UNLOCKED &&
            (isLyraForeground || PermissionHelper.hasOverlayPermission(context))

        val suppressOsHeadsUp = isFullScreenAlreadyVisible ||
            hasCustomComposeBannerSurface ||
            presentationMode == IncomingCallPresentationMode.FULL_SCREEN_LOCKED

        // Start FGS immediately to bind NOTIFICATION_ID_INCOMING as an un-dismissible foreground call
        if (persistentNotificationEnabled || CallStateManager.isRingingOrOngoing()) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LyraCallService::class.java)
                )
            }
        }

        // Publish incoming call notification cleanly without causing dual banner + full-screen popups
        if (PermissionHelper.hasNotificationPermission(context)) {
            val notification = buildIncomingCallNotification(
                context = context,
                caller = caller,
                useFullScreenIntent = shouldAttachFullScreenIntent,
                showCallerPhoto = showCallerPhoto,
                isSilenced = CallStateManager.isRingerSilenced.value,
                suppressHeadsUpPopup = suppressOsHeadsUp
            )
            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_INCOMING, notification)
            }
        }

        // Only launch full-screen incoming UI automatically when screen is locked or off
        if (presentationMode == IncomingCallPresentationMode.FULL_SCREEN_LOCKED &&
            (backgroundCallingEnabled || isLyraForeground)
        ) {
            launchCallActivity(context, "ACTION_INCOMING_CALL")
        }
    }

    fun buildIncomingCallNotification(
        context: Context,
        caller: CallerInfo,
        useFullScreenIntent: Boolean = false,
        showCallerPhoto: Boolean = true,
        isSilenced: Boolean = false,
        suppressHeadsUpPopup: Boolean = false
    ): Notification {
        createNotificationChannels(context)

        val contentIntent = Intent(context, CallActivity::class.java).apply {
            action = "ACTION_INCOMING_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            10,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptBroadcast = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = CallNotificationActionReceiver.ACTION_ACCEPT_CALL
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            11,
            acceptBroadcast,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineBroadcast = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = CallNotificationActionReceiver.ACTION_DECLINE_CALL
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            12,
            declineBroadcast,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = buildPerson(context, caller, includePhoto = showCallerPhoto)
        val statusLabel = buildString {
            when {
                caller.isOwner -> append("Owner Priority Call • ${caller.formattedNumber}")
                caller.isPriority -> append("Priority Contact • ${caller.formattedNumber}")
                caller.isKnown -> append("${caller.contactLabel} • ${caller.formattedNumber}")
                else -> append("Incoming Call • ${caller.formattedNumber}")
            }
            if (isSilenced) append(" • Silenced")
        }

        val useSilentChannel = suppressHeadsUpPopup && !useFullScreenIntent
        val targetChannelId = if (useSilentChannel) CHANNEL_INCOMING_SILENT else CHANNEL_INCOMING
        val notificationPriority = if (useSilentChannel) {
            NotificationCompat.PRIORITY_LOW
        } else {
            NotificationCompat.PRIORITY_MAX
        }

        val builder = NotificationCompat.Builder(context, targetChannelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(caller.displayName)
            .setContentText(statusLabel)
            .setPriority(notificationPriority)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    callerPerson,
                    declinePendingIntent,
                    acceptPendingIntent
                )
            )
            .addPerson(callerPerson)

        if (useFullScreenIntent) {
            builder.setFullScreenIntent(contentPendingIntent, true)
        }

        val built = builder.build()
        built.flags = built.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        return built
    }

    fun buildOngoingCallNotification(
        context: Context,
        caller: CallerInfo?,
        callState: CallStateEnum,
        callStartTimeMs: Long,
        isMuted: Boolean,
        isSpeakerOn: Boolean,
        showCallerPhoto: Boolean = true,
        useFullScreenIntent: Boolean = false
    ): Notification {
        createNotificationChannels(context)

        val displayCaller = caller ?: CallerInfo(
            phoneNumber = "",
            formattedNumber = "",
            displayName = "Active Call"
        )
        val callerPerson = buildPerson(context, displayCaller, includePhoto = showCallerPhoto)

        val contentIntent = Intent(context, CallActivity::class.java).apply {
            action = "ACTION_OUTGOING_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            20,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangUpIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = CallNotificationActionReceiver.ACTION_HANG_UP
        }
        val hangUpPendingIntent = PendingIntent.getBroadcast(
            context,
            21,
            hangUpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = CallNotificationActionReceiver.ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            22,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speakerIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = CallNotificationActionReceiver.ACTION_TOGGLE_SPEAKER
        }
        val speakerPendingIntent = PendingIntent.getBroadcast(
            context,
            23,
            speakerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = when (callState) {
            CallStateEnum.DIALING -> "Dialing ${displayCaller.formattedNumber}..."
            CallStateEnum.VOICEMAIL -> "Lyra Voicemail Screening • Active"
            else -> buildString {
                append(displayCaller.formattedNumber.ifBlank { "Ongoing call" })
                if (isMuted) append(" • Muted")
                if (isSpeakerOn) append(" • Speaker")
            }
        }

        val targetChannel = if (useFullScreenIntent && callState == CallStateEnum.DIALING) {
            CHANNEL_SCHEDULED_ALERTS
        } else {
            CHANNEL_ONGOING
        }
        val builder = NotificationCompat.Builder(context, targetChannel)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(displayCaller.displayName)
            .setContentText(subtitle)
            .setPriority(
                if (useFullScreenIntent && callState == CallStateEnum.DIALING) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    callerPerson,
                    hangUpPendingIntent
                )
            )
            .addPerson(callerPerson)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                if (isMuted) "Unmute" else "Mute",
                mutePendingIntent
            )
            .addAction(
                android.R.drawable.stat_sys_speakerphone,
                if (isSpeakerOn) "Speaker Off" else "Speaker",
                speakerPendingIntent
            )

        if (useFullScreenIntent &&
            callState == CallStateEnum.DIALING &&
            PermissionHelper.canUseFullScreenIntent(context)
        ) {
            builder.setFullScreenIntent(contentPendingIntent, true)
        }

        if (callState == CallStateEnum.ACTIVE && callStartTimeMs > 0L) {
            builder.setWhen(callStartTimeMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
        }

        val built = builder.build()
        built.flags = built.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        return built
    }

    fun buildScheduledCallProcessingNotification(
        context: Context,
        targetName: String?,
        phoneNumber: String?
    ): Notification {
        createNotificationChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ScheduledCallScheduler.ACTION_OPEN_SCHEDULED_CALLS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            30,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = targetName?.takeIf { it.isNotBlank() }
            ?: phoneNumber?.takeIf { it.isNotBlank() }
            ?: "Scheduled Call"

        return NotificationCompat.Builder(context, CHANNEL_ASSISTANT)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Placing Scheduled Call")
            .setContentText("Connecting $label via Lyra Telecom...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPendingIntent)
            .build()
    }

    fun buildScheduledCallFallbackNotification(
        context: Context,
        targetName: String,
        phoneNumber: String,
        reason: String
    ): Notification {
        createNotificationChannels(context)
        val dialIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dialPendingIntent = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode(),
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SCHEDULED_ALERTS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Scheduled Call: $targetName")
            .setContentText("$phoneNumber • $reason")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(dialPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call Now", dialPendingIntent)
            .build()
    }

    fun buildMissedCallNotification(
        context: Context,
        missedCalls: List<UnreadMissedCall>
    ): Notification {
        createNotificationChannels(context)
        val ordered = missedCalls.sortedByDescending { it.timestamp }
        val count = ordered.size
        val latest = ordered.firstOrNull()
        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun formatCallerPart(entry: UnreadMissedCall): String {
            val cleanName = entry.callerName.trim()
            val cleanNum = entry.phoneNumber.trim()
            return when {
                cleanName.isNotBlank() && cleanNum.isNotBlank() && !cleanName.equals(cleanNum, ignoreCase = true) ->
                    "$cleanName ($cleanNum)"
                cleanName.isNotBlank() -> cleanName
                cleanNum.isNotBlank() -> cleanNum
                else -> "Unknown Caller"
            }
        }

        val title = if (count <= 1) "Missed call" else "$count missed calls"
        val summaryText = if (count <= 1) "1 new missed call" else "$count new missed calls"
        val collapsedText = when {
            count == 0 -> "Missed call"
            count == 1 && latest != null -> buildString {
                append(formatCallerPart(latest))
                append(" • ")
                append(timeFormatter.format(Date(latest.timestamp)))
                if (!latest.simLabel.isNullOrBlank()) {
                    append(" • ")
                    append(latest.simLabel)
                }
            }
            else -> {
                val previewNames = ordered.take(2).joinToString(", ") { entry ->
                    entry.callerName.trim().ifBlank { entry.phoneNumber.ifBlank { "Unknown" } }
                }
                if (count > 2) "$previewNames +${count - 2} more" else previewNames
            }
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(summaryText)

        ordered.forEach { entry ->
            val line = buildString {
                append(formatCallerPart(entry))
                append(" • ")
                append(timeFormatter.format(Date(entry.timestamp)))
                if (!entry.simLabel.isNullOrBlank()) {
                    append(" • ")
                    append(entry.simLabel)
                }
            }
            inboxStyle.addLine(line)
        }

        val openRecentsIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_RECENTS
            putExtra(EXTRA_NAVIGATE_ROUTE, LyraDestinations.CALL_HISTORY)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            40,
            openRecentsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clearBroadcast = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = CallNotificationActionReceiver.ACTION_CLEAR_MISSED_CALLS
        }
        val clearPendingIntent = PendingIntent.getBroadcast(
            context,
            41,
            clearBroadcast,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Notification.CATEGORY_MISSED_CALL
        } else {
            NotificationCompat.CATEGORY_CALL
        }

        return NotificationCompat.Builder(context, CHANNEL_MISSED_CALL)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle(title)
            .setContentText(collapsedText)
            .setStyle(inboxStyle)
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(clearPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setWhen(latest?.timestamp ?: System.currentTimeMillis())
            .build()
    }

    fun postMissedCallNotification(context: Context, missedCalls: List<UnreadMissedCall>) {
        if (missedCalls.isEmpty()) {
            clearMissedCallNotification(context)
            return
        }
        if (!PermissionHelper.hasNotificationPermission(context)) return
        val notification = buildMissedCallNotification(context, missedCalls)
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MISSED_CALL, notification)
        }
    }

    suspend fun recordAndNotifyMissedCall(
        context: Context,
        callerName: String?,
        phoneNumber: String,
        timestamp: Long = System.currentTimeMillis(),
        simLabel: String? = null
    ): List<UnreadMissedCall> {
        val formattedNumber = PhoneNumberUtils.formatDisplay(phoneNumber).ifBlank { phoneNumber }
        val resolvedName = callerName?.trim()?.takeIf { it.isNotBlank() }
            ?: formattedNumber.ifBlank { "Unknown Caller" }
        val entry = UnreadMissedCall(
            callerName = resolvedName,
            phoneNumber = formattedNumber,
            timestamp = timestamp,
            simLabel = simLabel?.takeIf { it.isNotBlank() }
        )
        val updatedList = LyraPreferences(context).addUnreadMissedCall(entry)
        postMissedCallNotification(context, updatedList)
        return updatedList
    }

    fun clearMissedCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_MISSED_CALL)
    }

    fun cancelAllCallNotifications(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(NOTIFICATION_ID_INCOMING)
        nm.cancel(NOTIFICATION_ID_ASSISTANT)
    }

    private fun buildPerson(
        context: Context,
        caller: CallerInfo,
        includePhoto: Boolean = true
    ): Person {
        val builder = Person.Builder()
            .setName(caller.displayName.ifBlank { caller.formattedNumber.ifBlank { "Caller" } })
            .setImportant(caller.isOwner || caller.isPriority)

        if (includePhoto) {
            loadScaledAvatarBitmap(context, caller.photoUri)?.let { bmp ->
                builder.setIcon(IconCompat.createWithBitmap(bmp))
            }
        }
        return builder.build()
    }

    private fun loadScaledAvatarBitmap(context: Context, photoUri: String?): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        avatarBitmapCache.get(photoUri)?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        return runCatching {
            val uri = Uri.parse(photoUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > 320 || (bounds.outHeight / sampleSize) > 320) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            val finalBitmap = if (raw.width == 160 && raw.height == 160) {
                raw
            } else {
                Bitmap.createScaledBitmap(raw, 160, 160, true).also {
                    if (it !== raw) raw.recycle()
                }
            }
            avatarBitmapCache.put(photoUri, finalBitmap)
            finalBitmap
        }.getOrNull()
    }
}
