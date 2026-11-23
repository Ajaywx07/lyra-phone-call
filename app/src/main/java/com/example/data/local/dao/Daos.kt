package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CallNote
import com.example.data.local.entity.CallSummary
import com.example.data.local.entity.PriorityContact
import com.example.data.local.entity.ScheduledCall
import com.example.data.local.entity.VoicemailEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CallNoteDao {
    @Query("SELECT * FROM call_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<CallNote>>

    @Query("SELECT * FROM call_notes WHERE callerNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getNotesForCaller(phoneNumber: String): Flow<List<CallNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: CallNote): Long

    @Delete
    suspend fun deleteNote(note: CallNote)

    @Query("DELETE FROM call_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)
}

@Dao
interface CallSummaryDao {
    @Query("SELECT * FROM call_summaries ORDER BY timestamp DESC")
    fun getAllSummaries(): Flow<List<CallSummary>>

    @Query("SELECT * FROM call_summaries WHERE id = :id")
    suspend fun getSummaryById(id: Long): CallSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: CallSummary): Long

    @Delete
    suspend fun deleteSummary(summary: CallSummary)
}

@Dao
interface PriorityContactDao {
    @Query("SELECT * FROM priority_contacts ORDER BY isOwner DESC, contactName ASC")
    fun getAllPriorityContacts(): Flow<List<PriorityContact>>

    @Query("SELECT * FROM priority_contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getPriorityContact(phoneNumber: String): PriorityContact?

    @Query("SELECT * FROM priority_contacts WHERE id = :id LIMIT 1")
    suspend fun getPriorityContactById(id: Long): PriorityContact?

    @Query("SELECT * FROM priority_contacts WHERE sourceContactId = :sourceContactId LIMIT 1")
    suspend fun getPriorityContactBySourceId(sourceContactId: String): PriorityContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriorityContact(contact: PriorityContact): Long

    @Query("DELETE FROM priority_contacts WHERE phoneNumber = :phoneNumber")
    suspend fun removePriorityContact(phoneNumber: String)

    @Query("DELETE FROM priority_contacts WHERE id = :id")
    suspend fun deletePriorityContactById(id: Long)

    @Query("SELECT COUNT(*) FROM priority_contacts WHERE isOwner = 1")
    suspend fun getOwnerCount(): Int
}

@Dao
interface VoicemailDao {
    @Query("SELECT * FROM voicemails ORDER BY timestamp DESC")
    fun getAllVoicemails(): Flow<List<VoicemailEntry>>

    @Query("SELECT * FROM voicemails WHERE id = :id")
    suspend fun getVoicemailById(id: Long): VoicemailEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoicemail(entry: VoicemailEntry): Long

    @Update
    suspend fun updateVoicemail(entry: VoicemailEntry)

    @Query("DELETE FROM voicemails WHERE id = :id")
    suspend fun deleteVoicemailById(id: Long)
}

@Dao
interface ScheduledCallDao {
    @Query("SELECT * FROM scheduled_calls ORDER BY scheduledAtMillis ASC")
    fun getAllScheduledCalls(): Flow<List<ScheduledCall>>

    @Query("SELECT * FROM scheduled_calls WHERE status = 'PENDING' ORDER BY scheduledAtMillis ASC")
    suspend fun getPendingScheduledCalls(): List<ScheduledCall>

    @Query("SELECT * FROM scheduled_calls WHERE id = :id LIMIT 1")
    suspend fun getScheduledCallById(id: Long): ScheduledCall?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledCall(call: ScheduledCall): Long

    @Update
    suspend fun updateScheduledCall(call: ScheduledCall)

    @Query("DELETE FROM scheduled_calls WHERE id = :id")
    suspend fun deleteScheduledCallById(id: Long)
}

@Dao
interface CallRecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: com.example.data.local.entity.CallRecording): Long

    @Delete
    suspend fun deleteRecording(recording: com.example.data.local.entity.CallRecording)

    @Query("SELECT * FROM call_recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<com.example.data.local.entity.CallRecording>>

    @Query("SELECT * FROM call_recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): com.example.data.local.entity.CallRecording?

    @Query("DELETE FROM call_recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}

@Dao
interface AutoReplyModeDao {
    @Query("SELECT * FROM auto_reply_modes ORDER BY sortOrder ASC")
    fun observeAllModes(): Flow<List<com.example.data.local.entity.AutoReplyMode>>

    @Query("SELECT * FROM auto_reply_modes ORDER BY sortOrder ASC")
    suspend fun getAllModesOnce(): List<com.example.data.local.entity.AutoReplyMode>

    @Query("SELECT * FROM auto_reply_modes WHERE modeId = :modeId LIMIT 1")
    suspend fun getModeById(modeId: String): com.example.data.local.entity.AutoReplyMode?

    @Query("SELECT * FROM auto_reply_modes WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveMode(): com.example.data.local.entity.AutoReplyMode?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingModes(modes: List<com.example.data.local.entity.AutoReplyMode>)

    @Update
    suspend fun updateMode(mode: com.example.data.local.entity.AutoReplyMode)

    @Query("UPDATE auto_reply_modes SET isActive = 0, updatedAtMillis = :updatedAt")
    suspend fun deactivateAllModes(updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE auto_reply_modes SET isActive = CASE WHEN modeId = :modeId THEN 1 ELSE 0 END, updatedAtMillis = :updatedAt")
    suspend fun setActiveMode(modeId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE auto_reply_modes SET customMessage = :message, updatedAtMillis = :updatedAt WHERE modeId = :modeId")
    suspend fun updateCustomMessage(modeId: String, message: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface CustomFeatureSettingsDao {
    @Query("SELECT * FROM custom_feature_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<com.example.data.local.entity.CustomFeatureSettings?>

    @Query("SELECT * FROM custom_feature_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): com.example.data.local.entity.CustomFeatureSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: com.example.data.local.entity.CustomFeatureSettings)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultIfMissing(settings: com.example.data.local.entity.CustomFeatureSettings)
}

