package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CallNoteDao
import com.example.data.local.dao.CallRecordingDao
import com.example.data.local.dao.CallSummaryDao
import com.example.data.local.dao.PriorityContactDao
import com.example.data.local.dao.ScheduledCallDao
import com.example.data.local.dao.VoicemailDao
import com.example.data.local.entity.CallNote
import com.example.data.local.entity.CallRecording
import com.example.data.local.entity.CallSummary
import com.example.data.local.entity.PriorityContact
import com.example.data.local.entity.ScheduledCall
import com.example.data.local.entity.VoicemailEntry

@Database(
    entities = [
        CallNote::class,
        CallSummary::class,
        PriorityContact::class,
        VoicemailEntry::class,
        ScheduledCall::class,
        CallRecording::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LyraDatabase : RoomDatabase() {
    abstract fun callNoteDao(): CallNoteDao
    abstract fun callSummaryDao(): CallSummaryDao
    abstract fun priorityContactDao(): PriorityContactDao
    abstract fun voicemailDao(): VoicemailDao
    abstract fun scheduledCallDao(): ScheduledCallDao
    abstract fun callRecordingDao(): CallRecordingDao

    companion object {
        @Volatile
        private var INSTANCE: LyraDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE priority_contacts ADD COLUMN photoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE priority_contacts ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE priority_contacts ADD COLUMN sourceContactId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `call_recordings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `callerName` TEXT NOT NULL,
                        `callerNumber` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mediaStoreUri` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL DEFAULT 'Lyra/Recordings',
                        `durationSeconds` INTEGER NOT NULL DEFAULT 0,
                        `fileSizeBytes` INTEGER NOT NULL DEFAULT 0,
                        `timestamp` INTEGER NOT NULL DEFAULT 0,
                        `captureSource` TEXT NOT NULL DEFAULT 'VOICE_COMMUNICATION',
                        `isTwoWayAudio` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): LyraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LyraDatabase::class.java,
                    "lyra_calling_assistant.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
