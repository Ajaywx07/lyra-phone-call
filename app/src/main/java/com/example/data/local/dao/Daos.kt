package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CallNote
import com.example.data.local.entity.CallRecording
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
    @Query("SELECT * FROM call_recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<CallRecording>>

    @Query("SELECT * FROM call_recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): CallRecording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: CallRecording): Long

    @Delete
    suspend fun deleteRecording(recording: CallRecording)

    @Query("DELETE FROM call_recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}


