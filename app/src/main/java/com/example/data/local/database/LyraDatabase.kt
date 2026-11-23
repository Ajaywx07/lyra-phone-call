package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AutoReplyModeDao
import com.example.data.local.dao.CallNoteDao
import com.example.data.local.dao.CallRecordingDao
import com.example.data.local.dao.CallSummaryDao
import com.example.data.local.dao.CustomFeatureSettingsDao
import com.example.data.local.dao.PriorityContactDao
import com.example.data.local.dao.ScheduledCallDao
import com.example.data.local.dao.VoicemailDao
import com.example.data.local.entity.AutoReplyMode
import com.example.data.local.entity.CallNote
import com.example.data.local.entity.CallRecording
import com.example.data.local.entity.CallSummary
import com.example.data.local.entity.CustomFeatureSettings
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
        CallRecording::class,
        AutoReplyMode::class,
        CustomFeatureSettings::class
    ],
    version = 9,
    exportSchema = false
)
abstract class LyraDatabase : RoomDatabase() {
    abstract fun callNoteDao(): CallNoteDao
    abstract fun callSummaryDao(): CallSummaryDao
    abstract fun priorityContactDao(): PriorityContactDao
    abstract fun voicemailDao(): VoicemailDao
    abstract fun scheduledCallDao(): ScheduledCallDao
    abstract fun callRecordingDao(): CallRecordingDao
    abstract fun autoReplyModeDao(): AutoReplyModeDao
    abstract fun customFeatureSettingsDao(): CustomFeatureSettingsDao

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
                // Retained for upgrade path continuity
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `call_recordings`")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `call_recordings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `callerName` TEXT NOT NULL,
                        `callerNumber` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mediaStoreUri` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `captureSource` TEXT NOT NULL,
                        `isTwoWayAudio` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `auto_reply_modes` (
                        `modeId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `defaultMessage` TEXT NOT NULL,
                        `customMessage` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `updatedAtMillis` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`modeId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_feature_settings` (
                        `id` INTEGER NOT NULL DEFAULT 1,
                        `showLyraLogoOnIncoming` INTEGER NOT NULL DEFAULT 1,
                        `accessibilityAutomationEnabled` INTEGER NOT NULL DEFAULT 0,
                        `ussdDirectHandlerEnabled` INTEGER NOT NULL DEFAULT 1,
                        `showMostFrequentContacts` INTEGER NOT NULL DEFAULT 1,
                        `autoReplyMasterEnabled` INTEGER NOT NULL DEFAULT 0,
                        `activeAutoReplyModeId` TEXT NOT NULL DEFAULT '',
                        `updatedAtMillis` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_feature_settings ADD COLUMN monthlyStatsAutoResetEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE custom_feature_settings ADD COLUMN lastStatsResetMonthKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE custom_feature_settings ADD COLUMN keypadToneEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE custom_feature_settings ADD COLUMN keypadVoiceAnnouncementEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): LyraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LyraDatabase::class.java,
                    "lyra_calling_assistant.db"
                )
                    .addMigrations(
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
