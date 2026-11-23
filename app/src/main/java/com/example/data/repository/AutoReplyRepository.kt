package com.example.data.repository

import com.example.data.local.dao.AutoReplyModeDao
import com.example.data.local.dao.CustomFeatureSettingsDao
import com.example.data.local.entity.AutoReplyMode
import com.example.data.local.entity.CustomFeatureSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object AutoReplyPresets {

    fun defaultModes(nowMillis: Long = System.currentTimeMillis()): List<AutoReplyMode> = listOf(
        AutoReplyMode(
            modeId = "driving",
            title = "Driving",
            emoji = "🚗",
            defaultMessage = "Hi, I'm currently driving and can't answer safely. I'll call you back as soon as I park.",
            customMessage = "Hi, I'm currently driving and can't answer safely. I'll call you back as soon as I park.",
            sortOrder = 0,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "sleeping",
            title = "Sleeping",
            emoji = "😴",
            defaultMessage = "Hi, I'm sleeping right now. Please leave a text if urgent and I'll reach back out when I wake up.",
            customMessage = "Hi, I'm sleeping right now. Please leave a text if urgent and I'll reach back out when I wake up.",
            sortOrder = 1,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "meeting",
            title = "Meeting",
            emoji = "📅",
            defaultMessage = "Hi, I'm in a meeting and can't pick up right now. I'll call you right after it ends.",
            customMessage = "Hi, I'm in a meeting and can't pick up right now. I'll call you right after it ends.",
            sortOrder = 2,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "in_class",
            title = "In Class",
            emoji = "🎓",
            defaultMessage = "Hi, I'm currently in class. Please send a message and I'll respond during the break.",
            customMessage = "Hi, I'm currently in class. Please send a message and I'll respond during the break.",
            sortOrder = 3,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "studying",
            title = "Studying",
            emoji = "📚",
            defaultMessage = "Hi, I'm focused on studying right now. I'll return your call shortly.",
            customMessage = "Hi, I'm focused on studying right now. I'll return your call shortly.",
            sortOrder = 4,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "workout_gym",
            title = "Workout / Gym",
            emoji = "🏋️",
            defaultMessage = "Hi, I'm at the gym right now. I'll give you a call back once my workout finishes.",
            customMessage = "Hi, I'm at the gym right now. I'll give you a call back once my workout finishes.",
            sortOrder = 5,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "cinema_movie",
            title = "Cinema / Movie",
            emoji = "🎬",
            defaultMessage = "Hi, I'm watching a movie at the cinema. I'll call you back once it's over.",
            customMessage = "Hi, I'm watching a movie at the cinema. I'll call you back once it's over.",
            sortOrder = 6,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "busy_at_work",
            title = "Busy at Work",
            emoji = "💼",
            defaultMessage = "Hi, I'm tied up with work at the moment. Please text if urgent and I'll get back to you soon.",
            customMessage = "Hi, I'm tied up with work at the moment. Please text if urgent and I'll get back to you soon.",
            sortOrder = 7,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "commuting",
            title = "Commuting",
            emoji = "🚆",
            defaultMessage = "Hi, I'm currently commuting and can't talk clearly. I'll call you once I arrive.",
            customMessage = "Hi, I'm currently commuting and can't talk clearly. I'll call you once I arrive.",
            sortOrder = 8,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "travelling",
            title = "Travelling",
            emoji = "🧳",
            defaultMessage = "Hi, I'm travelling right now and may have spotty network. I'll call you back soon.",
            customMessage = "Hi, I'm travelling right now and may have spotty network. I'll call you back soon.",
            sortOrder = 9,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "flight_transit",
            title = "Flight / Transit",
            emoji = "✈️",
            defaultMessage = "Hi, I'm in transit / boarding a flight. I'll contact you as soon as I land.",
            customMessage = "Hi, I'm in transit / boarding a flight. I'll contact you as soon as I land.",
            sortOrder = 10,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "dinner_family",
            title = "Dinner / Family Time",
            emoji = "🍽️",
            defaultMessage = "Hi, I'm having dinner with family right now. I'll give you a call later.",
            customMessage = "Hi, I'm having dinner with family right now. I'll give you a call later.",
            sortOrder = 11,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "medical_doctor",
            title = "Medical / Doctor",
            emoji = "🩺",
            defaultMessage = "Hi, I'm at a medical appointment right now. I'll return your call when I step out.",
            customMessage = "Hi, I'm at a medical appointment right now. I'll return your call when I step out.",
            sortOrder = 12,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "prayer_meditation",
            title = "Prayer / Meditation",
            emoji = "🧘",
            defaultMessage = "Hi, I'm taking a quiet moment for prayer/meditation. I'll reach back shortly.",
            customMessage = "Hi, I'm taking a quiet moment for prayer/meditation. I'll reach back shortly.",
            sortOrder = 13,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "deep_focus",
            title = "Deep Focus",
            emoji = "🎯",
            defaultMessage = "Hi, I'm in a deep focus session right now. Send a text and I'll reply during my next break.",
            customMessage = "Hi, I'm in a deep focus session right now. Send a text and I'll reply during my next break.",
            sortOrder = 14,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "on_another_call",
            title = "On Another Call",
            emoji = "📞",
            defaultMessage = "Hi, I'm currently on another call. I'll call you back as soon as I'm free.",
            customMessage = "Hi, I'm currently on another call. I'll call you back as soon as I'm free.",
            sortOrder = 15,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "gaming",
            title = "Gaming",
            emoji = "🎮",
            defaultMessage = "Hi, I'm in the middle of a match right now. I'll call you back once it finishes.",
            customMessage = "Hi, I'm in the middle of a match right now. I'll call you back once it finishes.",
            sortOrder = 16,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "recording_live",
            title = "Recording / Live",
            emoji = "🎙️",
            defaultMessage = "Hi, I'm currently live / recording audio. Please text and I'll call you back afterward.",
            customMessage = "Hi, I'm currently live / recording audio. Please text and I'll call you back afterward.",
            sortOrder = 17,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "presentation",
            title = "Presentation",
            emoji = "📊",
            defaultMessage = "Hi, I'm presenting right now and can't pick up. I'll call you once the session wraps up.",
            customMessage = "Hi, I'm presenting right now and can't pick up. I'll call you once the session wraps up.",
            sortOrder = 18,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "quiet_library",
            title = "Quiet Zone / Library",
            emoji = "🤫",
            defaultMessage = "Hi, I'm in a quiet zone / library and can't speak on the phone. Please send a text.",
            customMessage = "Hi, I'm in a quiet zone / library and can't speak on the phone. Please send a text.",
            sortOrder = 19,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "battery_critical",
            title = "Battery Critical",
            emoji = "🪫",
            defaultMessage = "Hi, my phone battery is critically low. I'll call you back as soon as I'm on a charger.",
            customMessage = "Hi, my phone battery is critically low. I'll call you back as soon as I'm on a charger.",
            sortOrder = 20,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "running_late",
            title = "Running Late",
            emoji = "⏱️",
            defaultMessage = "Hi, I'm running a little late and can't talk right now. On my way—will call shortly.",
            customMessage = "Hi, I'm running a little late and can't talk right now. On my way—will call shortly.",
            sortOrder = 21,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "on_vacation",
            title = "On Vacation",
            emoji = "🌴",
            defaultMessage = "Hi, I'm away on vacation right now with limited availability. Please drop an SMS if urgent.",
            customMessage = "Hi, I'm away on vacation right now with limited availability. Please drop an SMS if urgent.",
            sortOrder = 22,
            updatedAtMillis = nowMillis
        ),
        AutoReplyMode(
            modeId = "custom_mode",
            title = "Custom Mode",
            emoji = "✨",
            defaultMessage = "Hi, I'm unavailable right now. Please leave a message and I'll get back to you soon.",
            customMessage = "Hi, I'm unavailable right now. Please leave a message and I'll get back to you soon.",
            sortOrder = 23,
            updatedAtMillis = nowMillis
        )
    )
}

class AutoReplyRepository(
    private val autoReplyModeDao: AutoReplyModeDao,
    private val customFeatureSettingsDao: CustomFeatureSettingsDao
) {
    val allModes: Flow<List<AutoReplyMode>> = autoReplyModeDao.observeAllModes()

    val customFeatureSettings: Flow<CustomFeatureSettings> =
        customFeatureSettingsDao.observeSettings().map { it ?: CustomFeatureSettings() }

    suspend fun ensureSeeded() {
        autoReplyModeDao.insertMissingModes(AutoReplyPresets.defaultModes())
        customFeatureSettingsDao.insertDefaultIfMissing(CustomFeatureSettings())
    }

    suspend fun getSettingsOnce(): CustomFeatureSettings {
        ensureSeeded()
        return customFeatureSettingsDao.getSettingsOnce() ?: CustomFeatureSettings()
    }

    suspend fun getAllModesOnce(): List<AutoReplyMode> {
        ensureSeeded()
        return autoReplyModeDao.getAllModesOnce()
    }

    suspend fun getActiveAutoReplyModeIfEnabled(): AutoReplyMode? {
        ensureSeeded()
        val settings = customFeatureSettingsDao.getSettingsOnce() ?: CustomFeatureSettings()
        if (!settings.autoReplyMasterEnabled) return null
        val active = if (settings.activeAutoReplyModeId.isNotBlank()) {
            autoReplyModeDao.getModeById(settings.activeAutoReplyModeId)
        } else {
            autoReplyModeDao.getActiveMode()
        }
        return active
    }

    suspend fun setMasterEnabled(enabled: Boolean, selectedModeId: String? = null) {
        ensureSeeded()
        val now = System.currentTimeMillis()
        val current = customFeatureSettingsDao.getSettingsOnce() ?: CustomFeatureSettings()
        val resolvedModeId = when {
            !selectedModeId.isNullOrBlank() -> selectedModeId
            current.activeAutoReplyModeId.isNotBlank() -> current.activeAutoReplyModeId
            else -> "driving"
        }
        if (enabled) {
            autoReplyModeDao.setActiveMode(resolvedModeId, now)
        } else {
            autoReplyModeDao.deactivateAllModes(now)
        }
        customFeatureSettingsDao.upsertSettings(
            current.copy(
                autoReplyMasterEnabled = enabled,
                activeAutoReplyModeId = resolvedModeId,
                updatedAtMillis = now
            )
        )
    }

    suspend fun selectMode(modeId: String, activateIfMasterOn: Boolean = true) {
        ensureSeeded()
        val now = System.currentTimeMillis()
        val current = customFeatureSettingsDao.getSettingsOnce() ?: CustomFeatureSettings()
        if (current.autoReplyMasterEnabled || activateIfMasterOn) {
            if (current.autoReplyMasterEnabled) {
                autoReplyModeDao.setActiveMode(modeId, now)
            }
        }
        customFeatureSettingsDao.upsertSettings(
            current.copy(
                activeAutoReplyModeId = modeId,
                updatedAtMillis = now
            )
        )
    }

    suspend fun saveCustomMessage(modeId: String, newMessage: String) {
        ensureSeeded()
        val trimmed = newMessage.trim()
        val existing = autoReplyModeDao.getModeById(modeId) ?: return
        val resolved = trimmed.ifBlank { existing.defaultMessage }
        autoReplyModeDao.updateCustomMessage(modeId, resolved, System.currentTimeMillis())
    }

    suspend fun resetModeMessageToDefault(modeId: String) {
        ensureSeeded()
        val existing = autoReplyModeDao.getModeById(modeId) ?: return
        autoReplyModeDao.updateCustomMessage(modeId, existing.defaultMessage, System.currentTimeMillis())
    }

    suspend fun updateFeatureSettings(transform: (CustomFeatureSettings) -> CustomFeatureSettings): CustomFeatureSettings {
        ensureSeeded()
        val current = customFeatureSettingsDao.getSettingsOnce() ?: CustomFeatureSettings()
        val updated = transform(current).copy(updatedAtMillis = System.currentTimeMillis())
        customFeatureSettingsDao.upsertSettings(updated)
        return updated
    }
}
