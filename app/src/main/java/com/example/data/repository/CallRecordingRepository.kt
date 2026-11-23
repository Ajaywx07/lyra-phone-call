package com.example.data.repository

import android.content.Context
import android.content.Intent
import com.example.data.local.dao.CallRecordingDao
import com.example.data.local.entity.CallRecording
import com.example.domain.model.CallRecordingCapability
import com.example.utils.CallRecordingStorage
import kotlinx.coroutines.flow.Flow

class CallRecordingRepository(
    private val callRecordingDao: CallRecordingDao
) {

    val allRecordings: Flow<List<CallRecording>> = callRecordingDao.getAllRecordings()

    suspend fun insertRecording(recording: CallRecording): Long {
        return callRecordingDao.insertRecording(recording)
    }

    suspend fun deleteRecording(context: Context, recording: CallRecording) {
        CallRecordingStorage.deleteRecording(context, recording)
        callRecordingDao.deleteRecording(recording)
    }

    suspend fun getRecordingById(id: Long): CallRecording? {
        return callRecordingDao.getRecordingById(id)
    }

    fun createShareIntent(context: Context, recording: CallRecording): Intent {
        return CallRecordingStorage.createShareIntent(context, recording)
    }

    fun getCapability(sdkInt: Int = android.os.Build.VERSION.SDK_INT): CallRecordingCapability {
        if (sdkInt >= 35) {
            return CallRecordingCapability(
                isSupported = false,
                isTwoWaySupported = false,
                notice = "Native call recording is disabled on Android 15+ (API 35+) to comply with platform privacy policies."
            )
        }
        return CallRecordingCapability(
            isSupported = true,
            isTwoWaySupported = false,
            notice = "Android restricts direct line-in call recording on standard ROMs. Audio is captured via microphone and loudspeaker."
        )
    }
}
