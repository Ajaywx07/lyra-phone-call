package com.example.data.repository

import com.example.data.local.dao.CallNoteDao
import com.example.data.local.dao.CallSummaryDao
import com.example.data.local.dao.PriorityContactDao
import com.example.data.local.entity.CallNote
import com.example.data.local.entity.CallSummary
import com.example.data.local.entity.PriorityContact
import kotlinx.coroutines.flow.Flow

class CallRepository(
    private val callNoteDao: CallNoteDao,
    private val callSummaryDao: CallSummaryDao,
    private val priorityContactDao: PriorityContactDao,
    private val aiAssistantRepository: AiAssistantRepository
) {

    val allNotes: Flow<List<CallNote>> = callNoteDao.getAllNotes()
    val allSummaries: Flow<List<CallSummary>> = callSummaryDao.getAllSummaries()
    val allPriorityContacts: Flow<List<PriorityContact>> = priorityContactDao.getAllPriorityContacts()

    fun getNotesForCaller(phoneNumber: String): Flow<List<CallNote>> {
        return callNoteDao.getNotesForCaller(phoneNumber)
    }

    suspend fun saveCallNote(
        callerNumber: String,
        callerName: String,
        noteText: String,
        tag: String = "General"
    ): Long {
        return callNoteDao.insertNote(
            CallNote(
                callerNumber = callerNumber,
                callerName = callerName,
                noteText = noteText,
                tag = tag
            )
        )
    }

    suspend fun deleteNoteById(id: Long) {
        callNoteDao.deleteNoteById(id)
    }

    suspend fun createAndSaveCallSummary(
        callerNumber: String,
        callerName: String,
        durationSeconds: Long,
        callType: String,
        notes: String
    ): CallSummary {
        val summaryText = aiAssistantRepository.generateCallSummary(
            callerName = callerName,
            durationSeconds = durationSeconds,
            notes = notes
        )

        val summary = CallSummary(
            callerNumber = callerNumber,
            callerName = callerName,
            durationSeconds = durationSeconds,
            callType = callType,
            notes = notes,
            summaryText = summaryText
        )

        val id = callSummaryDao.insertSummary(summary)
        return summary.copy(id = id)
    }

    suspend fun getSummaryById(id: Long): CallSummary? {
        return callSummaryDao.getSummaryById(id)
    }

    suspend fun deleteSummary(summary: CallSummary) {
        callSummaryDao.deleteSummary(summary)
    }
}
