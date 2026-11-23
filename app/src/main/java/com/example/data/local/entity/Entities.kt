package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a local call note created during or after a call.
 * Stored 100% locally on the device with zero cloud transmission.
 */
@Entity(tableName = "call_notes")
data class CallNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerNumber: String,
    val callerName: String,
    val noteText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String = "General"
)

/**
 * Entity representing a post-call summary generated locally.
 */
@Entity(tableName = "call_summaries")
data class CallSummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerNumber: String,
    val callerName: String,
    val durationSeconds: Long,
    val callType: String, // INCOMING, OUTGOING, MISSED
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val summaryText: String = ""
)

/**
 * Entity for local priority contacts list, including the primary owner "Ajay".
 */
@Entity(tableName = "priority_contacts")
data class PriorityContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val contactName: String,
    val isOwner: Boolean = false,
    val notes: String = "",
    val photoUri: String = "",
    val isStarred: Boolean = true,
    val sourceContactId: String = "",
    val addedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Entity representing an internally routed voicemail recording saved locally on-device
 * along with its AI-extracted key points and concise 1-2 line actionable summary.
 */
@Entity(tableName = "voicemails")
data class VoicemailEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerNumber: String,
    val callerName: String,
    val audioFilePath: String,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val transcript: String = "",
    val summary: String = "",
    val keyPoints: String = "", // Newline-separated bullet items
    val actionPrompt: String = "",
    val modelUsed: String = "",
    val providerUsed: String = "",
    val status: String = "RECORDED", // RECORDED, PROCESSING, READY, ERROR
    val errorMessage: String = ""
)

/**
 * Entity representing a scheduled automatic outgoing call.
 */
@Entity(tableName = "scheduled_calls")
data class ScheduledCall(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String,
    val phoneNumber: String,
    val simKey: String = "ASK", // ASK, SIM_1, SIM_2
    val scheduledAtMillis: Long,
    val status: String = "PENDING", // PENDING, PLACED, FAILED, CANCELLED
    val statusMessage: String = "",
    val lastAttemptMillis: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Entity representing a recorded phone call saved locally in shared MediaStore storage.
 */
@Entity(tableName = "call_recordings")
data class CallRecording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerName: String,
    val callerNumber: String,
    val fileName: String,
    val mediaStoreUri: String,
    val relativePath: String,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val captureSource: String = "MIC",
    val isTwoWayAudio: Boolean = false
)

/**
 * Permanent Room entity representing an auto-reply status mode (e.g. Driving, Sleeping, Meeting).
 * Seeded with 24 pre-fed modes; user edits to customMessage and active status persist permanently across restarts.
 */
@Entity(tableName = "auto_reply_modes")
data class AutoReplyMode(
    @PrimaryKey
    val modeId: String,
    val title: String,
    val emoji: String,
    val defaultMessage: String,
    val customMessage: String,
    val isActive: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Permanent singleton Room entity backing custom UI, automation, and security feature settings.
 */
@Entity(tableName = "custom_feature_settings")
data class CustomFeatureSettings(
    @PrimaryKey
    val id: Int = 1,
    val showLyraLogoOnIncoming: Boolean = true,
    val accessibilityAutomationEnabled: Boolean = false,
    val ussdDirectHandlerEnabled: Boolean = true,
    val showMostFrequentContacts: Boolean = true,
    val autoReplyMasterEnabled: Boolean = false,
    val activeAutoReplyModeId: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

