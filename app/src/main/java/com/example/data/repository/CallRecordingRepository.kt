package com.example.data.repository

import android.content.Context
import com.example.data.local.dao.CallRecordingDao
import com.example.data.local.entity.CallRecording
import com.example.utils.CallRecordingStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CallRecordingRepository(
    private val context: Context,
    private val callRecordingDao: CallRecordingDao
) {
    private val appContext = context.applicationContext ?: context

    fun getAllRecordings(): Flow<List<CallRecording>> = callRecordingDao.getAllRecordings()

    suspend fun insertRecording(recording: CallRecording): Long {
        return callRecordingDao.insertRecording(recording)
    }

    suspend fun getRecordingById(id: Long): CallRecording? {
        return callRecordingDao.getRecordingById(id)
    }

    suspend fun deleteRecording(recording: CallRecording) = withContext(Dispatchers.IO) {
        CallRecordingStorage.deleteRecordingMedia(appContext, recording.mediaStoreUri)
        callRecordingDao.deleteRecordingById(recording.id)
    }
}
