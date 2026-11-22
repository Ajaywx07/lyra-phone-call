package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.IncomingCallSettingsSnapshot
import com.example.domain.model.UnreadMissedCall
import com.example.utils.SecretCryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lyra_settings")

data class StoredAiSettings(
    val provider: String,
    val modelName: String,
    val apiKey: String,
    val customBaseUrl: String,
    val voicemailGreeting: String
)

class LyraPreferences(private val context: Context) {

    companion object {
        const val DEFAULT_AI_PROVIDER = "FIREBASE_AI"
        const val DEFAULT_AI_MODEL = "gemini-3.8-flash"
        const val DEFAULT_AI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_VOICEMAIL_GREETING =
            "Hi, I'm Lyra, Ajay's personal AI assistant. Ajay is busy. You can leave a message after the beep."
        const val DEFAULT_VOICEMAIL_MAX_SECONDS = 45

        val KEY_LYRA_ENABLED = booleanPreferencesKey("lyra_enabled")
        val KEY_OWNER_NAME = stringPreferencesKey("owner_name")
        val KEY_OWNER_PHONE = stringPreferencesKey("owner_phone")
        val KEY_CALLER_ID_ENABLED = booleanPreferencesKey("caller_id_enabled")
        val KEY_UNKNOWN_CALLER_DETECTION = booleanPreferencesKey("unknown_caller_detection")
        val KEY_CALL_SUMMARY_ENABLED = booleanPreferencesKey("call_summary_enabled")
        val KEY_CALL_NOTES_ENABLED = booleanPreferencesKey("call_notes_enabled")
        val KEY_VOICE_GREETING_ENABLED = booleanPreferencesKey("voice_greeting_enabled")
        val KEY_LOCAL_ONLY_MODE = booleanPreferencesKey("local_only_mode")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_FULLSCREEN_INCOMING = booleanPreferencesKey("fullscreen_incoming")
        val KEY_CALL_SCREEN_STYLE = stringPreferencesKey("call_screen_style")
        val KEY_CALL_ANIMATION_STYLE = stringPreferencesKey("call_animation_style")
        val KEY_PREFERRED_SIM = stringPreferencesKey("preferred_sim")
        val KEY_FIX_DEFAULT_SIM = booleanPreferencesKey("fix_default_sim")

        // Universal runtime AI engine & voicemail configuration
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_AI_MODEL_NAME = stringPreferencesKey("ai_model_name")
        val KEY_AI_API_KEY = stringPreferencesKey("ai_api_key")
        val KEY_AI_CUSTOM_BASE_URL = stringPreferencesKey("ai_custom_base_url")
        val KEY_VOICEMAIL_GREETING = stringPreferencesKey("voicemail_greeting")
        val KEY_VOICEMAIL_MAX_SECONDS = intPreferencesKey("voicemail_max_seconds")

        // Custom Dashboard & Settings Header Graphic (Photo or Video + crop transform)
        val KEY_DASHBOARD_MEDIA_URI = stringPreferencesKey("dashboard_media_uri")
        val KEY_DASHBOARD_MEDIA_TYPE = stringPreferencesKey("dashboard_media_type")
        val KEY_DASHBOARD_MEDIA_CROP = stringPreferencesKey("dashboard_media_crop")

        // Quick SMS auto-send & hidden/deleted contact tracking
        val KEY_AUTO_SEND_QUICK_SMS = booleanPreferencesKey("auto_send_quick_sms")
        val KEY_DELETED_CONTACT_KEYS = stringSetPreferencesKey("deleted_contact_keys")

        // Background calling, persistent notification, ringtone & vibration preferences
        val KEY_INCOMING_SCREEN_ENABLED = booleanPreferencesKey("incoming_screen_enabled")
        val KEY_PERSISTENT_NOTIFICATION_ENABLED = booleanPreferencesKey("persistent_notification_enabled")
        val KEY_BACKGROUND_CALLING_ENABLED = booleanPreferencesKey("background_calling_enabled")
        val KEY_CALL_VIBRATION_ENABLED = booleanPreferencesKey("call_vibration_enabled")
        val KEY_RINGTONE_URI = stringPreferencesKey("ringtone_uri")
        val KEY_RINGTONE_TITLE = stringPreferencesKey("ringtone_title")
        val KEY_SHOW_CONTACT_PHOTO_ON_CALL = booleanPreferencesKey("show_contact_photo_on_call")

        // Incoming call blocking & filtering switches
        val KEY_BLOCK_UNKNOWN_CALLS = booleanPreferencesKey("block_unknown_calls")
        val KEY_BLOCK_COMMERCIAL_PREFIXES = booleanPreferencesKey("block_commercial_prefixes")

        // Unread missed calls tracking
        val KEY_LAST_MISSED_READ_AT = longPreferencesKey("last_missed_calls_read_at")
        val KEY_UNREAD_MISSED_CALLS_JSON = stringPreferencesKey("unread_missed_calls_json")

        // GitHub Releases In-App Update Configuration
        const val DEFAULT_GITHUB_UPDATE_REPO = "ajay/lyra"
        val KEY_GITHUB_UPDATE_REPO = stringPreferencesKey("github_update_repo")
        val KEY_SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")

        @Volatile
        private var cachedIncomingSnapshot: IncomingCallSettingsSnapshot? = null

        fun getCachedIncomingSnapshotOrNull(): IncomingCallSettingsSnapshot? = cachedIncomingSnapshot

        private fun snapshotFromPreferences(preferences: Preferences): IncomingCallSettingsSnapshot {
            return IncomingCallSettingsSnapshot(
                isLyraEnabled = preferences[KEY_LYRA_ENABLED] ?: true,
                ownerName = preferences[KEY_OWNER_NAME] ?: "Ajay",
                ownerPhone = preferences[KEY_OWNER_PHONE] ?: "+91 98765 43210",
                isFullscreenIncomingEnabled = preferences[KEY_FULLSCREEN_INCOMING] ?: true,
                isIncomingScreenEnabled = preferences[KEY_INCOMING_SCREEN_ENABLED] ?: true,
                isPersistentNotificationEnabled = preferences[KEY_PERSISTENT_NOTIFICATION_ENABLED] ?: true,
                isBackgroundCallingEnabled = preferences[KEY_BACKGROUND_CALLING_ENABLED] ?: true,
                showContactPhotoOnCall = preferences[KEY_SHOW_CONTACT_PHOTO_ON_CALL] ?: true,
                blockUnknownCalls = preferences[KEY_BLOCK_UNKNOWN_CALLS] ?: false,
                blockCommercialPrefixes = preferences[KEY_BLOCK_COMMERCIAL_PREFIXES] ?: false,
                themeMode = preferences[KEY_THEME_MODE] ?: "IOS_GLASS"
            ).also { cachedIncomingSnapshot = it }
        }

        internal fun decodeUnreadMissedCalls(rawJson: String?): List<UnreadMissedCall> {
            if (rawJson.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(rawJson)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(
                            UnreadMissedCall(
                                callerName = obj.optString("callerName", ""),
                                phoneNumber = obj.optString("phoneNumber", ""),
                                timestamp = obj.optLong("timestamp", 0L),
                                simLabel = obj.optString("simLabel", "").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }.sortedByDescending { it.timestamp }
            }.getOrDefault(emptyList())
        }

        internal fun encodeUnreadMissedCalls(items: List<UnreadMissedCall>): String {
            val arr = JSONArray()
            items.sortedByDescending { it.timestamp }.take(50).forEach { item ->
                val obj = JSONObject()
                obj.put("callerName", item.callerName)
                obj.put("phoneNumber", item.phoneNumber)
                obj.put("timestamp", item.timestamp)
                if (!item.simLabel.isNullOrBlank()) {
                    obj.put("simLabel", item.simLabel)
                }
                arr.put(obj)
            }
            return arr.toString()
        }
    }

    val isLyraEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LYRA_ENABLED] ?: true
    }

    val ownerName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_OWNER_NAME] ?: "Ajay"
    }

    val ownerPhone: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_OWNER_PHONE] ?: "+91 98765 43210"
    }

    val isCallerIdEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_CALLER_ID_ENABLED] ?: true
    }

    val isUnknownCallerDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_UNKNOWN_CALLER_DETECTION] ?: true
    }

    val isCallSummaryEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_CALL_SUMMARY_ENABLED] ?: true
    }

    val isCallNotesEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_CALL_NOTES_ENABLED] ?: true
    }

    val isVoiceGreetingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_VOICE_GREETING_ENABLED] ?: false
    }

    val isLocalOnlyMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LOCAL_ONLY_MODE] ?: true
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME_MODE] ?: "IOS_GLASS"
    }

    val autoSendQuickSms: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SEND_QUICK_SMS] ?: false
    }

    val deletedContactKeys: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_DELETED_CONTACT_KEYS] ?: emptySet()
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val isFullscreenIncomingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FULLSCREEN_INCOMING] ?: true
    }

    val isIncomingScreenEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_INCOMING_SCREEN_ENABLED] ?: true
    }

    val isPersistentNotificationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PERSISTENT_NOTIFICATION_ENABLED] ?: true
    }

    val isBackgroundCallingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BACKGROUND_CALLING_ENABLED] ?: true
    }

    val isCallVibrationEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_CALL_VIBRATION_ENABLED] ?: true
    }

    val ringtoneUri: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_RINGTONE_URI] ?: ""
    }

    val ringtoneTitle: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_RINGTONE_TITLE] ?: "System Default"
    }

    val showContactPhotoOnCall: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHOW_CONTACT_PHOTO_ON_CALL] ?: true
    }

    val blockUnknownCalls: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BLOCK_UNKNOWN_CALLS] ?: false
    }

    val blockCommercialPrefixes: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BLOCK_COMMERCIAL_PREFIXES] ?: false
    }

    val callScreenStyle: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CALL_SCREEN_STYLE] ?: "LYRA_AI"
    }

    val callAnimationStyle: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CALL_ANIMATION_STYLE] ?: "ORB_PULSE"
    }

    val preferredSim: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PREFERRED_SIM] ?: "ASK"
    }

    val fixDefaultSim: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIX_DEFAULT_SIM] ?: false
    }

    val aiProvider: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AI_PROVIDER] ?: DEFAULT_AI_PROVIDER
    }

    val aiModelName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AI_MODEL_NAME] ?: DEFAULT_AI_MODEL
    }

    val aiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        SecretCryptoHelper.decrypt(preferences[KEY_AI_API_KEY])
    }

    val aiCustomBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AI_CUSTOM_BASE_URL] ?: DEFAULT_AI_BASE_URL
    }

    val voicemailGreeting: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_VOICEMAIL_GREETING] ?: DEFAULT_VOICEMAIL_GREETING
    }

    val storedAiSettings: Flow<StoredAiSettings> = context.dataStore.data.map { preferences ->
        StoredAiSettings(
            provider = preferences[KEY_AI_PROVIDER] ?: DEFAULT_AI_PROVIDER,
            modelName = preferences[KEY_AI_MODEL_NAME] ?: DEFAULT_AI_MODEL,
            apiKey = SecretCryptoHelper.decrypt(preferences[KEY_AI_API_KEY]),
            customBaseUrl = preferences[KEY_AI_CUSTOM_BASE_URL] ?: DEFAULT_AI_BASE_URL,
            voicemailGreeting = preferences[KEY_VOICEMAIL_GREETING] ?: DEFAULT_VOICEMAIL_GREETING
        )
    }

    val voicemailMaxSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_VOICEMAIL_MAX_SECONDS] ?: DEFAULT_VOICEMAIL_MAX_SECONDS
    }

    val dashboardMediaUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DASHBOARD_MEDIA_URI]?.takeIf { it.isNotBlank() }
    }

    val dashboardMediaType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DASHBOARD_MEDIA_TYPE] ?: "ORB"
    }

    val dashboardMediaCrop: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DASHBOARD_MEDIA_CROP] ?: "1.0,0.0,0.0"
    }

    val lastMissedCallsReadAt: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_MISSED_READ_AT] ?: 0L
    }

    val unreadMissedCalls: Flow<List<UnreadMissedCall>> = context.dataStore.data.map { preferences ->
        decodeUnreadMissedCalls(preferences[KEY_UNREAD_MISSED_CALLS_JSON])
    }

    val githubUpdateRepo: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GITHUB_UPDATE_REPO] ?: DEFAULT_GITHUB_UPDATE_REPO
    }

    val skippedUpdateVersion: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SKIPPED_UPDATE_VERSION] ?: ""
    }

    suspend fun getIncomingCallSettingsSnapshot(forceRefresh: Boolean = false): IncomingCallSettingsSnapshot {
        if (!forceRefresh) {
            cachedIncomingSnapshot?.let { return it }
        }
        val prefs = context.dataStore.data.first()
        return snapshotFromPreferences(prefs)
    }

    suspend fun warmUpCache() {
        runCatching { getIncomingCallSettingsSnapshot(forceRefresh = true) }
    }

    suspend fun setLyraEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_LYRA_ENABLED] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun setOwnerName(name: String) {
        context.dataStore.edit {
            it[KEY_OWNER_NAME] = name
            snapshotFromPreferences(it)
        }
    }

    suspend fun setOwnerPhone(phone: String) {
        context.dataStore.edit {
            it[KEY_OWNER_PHONE] = phone
            snapshotFromPreferences(it)
        }
    }

    suspend fun setCallerIdEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CALLER_ID_ENABLED] = enabled }
    }

    suspend fun setUnknownCallerDetection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_UNKNOWN_CALLER_DETECTION] = enabled }
    }

    suspend fun setCallSummaryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CALL_SUMMARY_ENABLED] = enabled }
    }

    suspend fun setCallNotesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CALL_NOTES_ENABLED] = enabled }
    }

    suspend fun setVoiceGreetingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VOICE_GREETING_ENABLED] = enabled }
    }

    suspend fun setLocalOnlyMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LOCAL_ONLY_MODE] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit {
            it[KEY_THEME_MODE] = mode
            snapshotFromPreferences(it)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setFullscreenIncoming(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_FULLSCREEN_INCOMING] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun setCallScreenStyle(style: String) {
        context.dataStore.edit { it[KEY_CALL_SCREEN_STYLE] = style }
    }

    suspend fun setCallAnimationStyle(animation: String) {
        context.dataStore.edit { it[KEY_CALL_ANIMATION_STYLE] = animation }
    }

    suspend fun setPreferredSim(simKey: String) {
        context.dataStore.edit { it[KEY_PREFERRED_SIM] = simKey }
    }

    suspend fun setFixDefaultSim(fixed: Boolean) {
        context.dataStore.edit { it[KEY_FIX_DEFAULT_SIM] = fixed }
    }

    suspend fun setAiEngineSettings(
        provider: String,
        modelName: String,
        apiKey: String,
        customBaseUrl: String
    ) {
        val encryptedKey = SecretCryptoHelper.encrypt(apiKey.trim())
        context.dataStore.edit { prefs ->
            prefs[KEY_AI_PROVIDER] = provider.ifBlank { DEFAULT_AI_PROVIDER }
            prefs[KEY_AI_MODEL_NAME] = modelName.trim().ifBlank { DEFAULT_AI_MODEL }
            if (encryptedKey.isEmpty()) {
                prefs.remove(KEY_AI_API_KEY)
            } else {
                prefs[KEY_AI_API_KEY] = encryptedKey
            }
            prefs[KEY_AI_CUSTOM_BASE_URL] = customBaseUrl.trim().ifBlank { DEFAULT_AI_BASE_URL }
        }
    }

    suspend fun clearAiApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_AI_API_KEY)
        }
    }

    suspend fun setVoicemailGreeting(greeting: String) {
        context.dataStore.edit {
            it[KEY_VOICEMAIL_GREETING] = greeting.trim().ifBlank { DEFAULT_VOICEMAIL_GREETING }
        }
    }

    suspend fun setVoicemailMaxSeconds(seconds: Int) {
        context.dataStore.edit {
            it[KEY_VOICEMAIL_MAX_SECONDS] = seconds.coerceIn(10, 120)
        }
    }

    suspend fun setDashboardMedia(uri: String?, type: String, cropTransform: String = "1.0,0.0,0.0") {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank() || type == "ORB") {
                prefs.remove(KEY_DASHBOARD_MEDIA_URI)
                prefs[KEY_DASHBOARD_MEDIA_TYPE] = "ORB"
                prefs[KEY_DASHBOARD_MEDIA_CROP] = "1.0,0.0,0.0"
            } else {
                prefs[KEY_DASHBOARD_MEDIA_URI] = uri
                prefs[KEY_DASHBOARD_MEDIA_TYPE] = type
                prefs[KEY_DASHBOARD_MEDIA_CROP] = cropTransform
            }
        }
    }

    suspend fun setAutoSendQuickSms(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_SEND_QUICK_SMS] = enabled
        }
    }

    suspend fun markContactDeleted(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DELETED_CONTACT_KEYS] ?: emptySet()
            prefs[KEY_DELETED_CONTACT_KEYS] = current + keys
        }
    }

    suspend fun unmarkContactDeleted(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DELETED_CONTACT_KEYS] ?: emptySet()
            prefs[KEY_DELETED_CONTACT_KEYS] = current - keys
        }
    }

    suspend fun setIncomingScreenEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_INCOMING_SCREEN_ENABLED] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun setPersistentNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_PERSISTENT_NOTIFICATION_ENABLED] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun setBackgroundCallingEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_BACKGROUND_CALLING_ENABLED] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun setCallVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CALL_VIBRATION_ENABLED] = enabled }
    }

    suspend fun setRingtone(uri: String, title: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RINGTONE_URI] = uri
            prefs[KEY_RINGTONE_TITLE] = title.ifBlank { "System Default" }
        }
    }

    suspend fun setShowContactPhotoOnCall(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_SHOW_CONTACT_PHOTO_ON_CALL] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun setBlockUnknownCalls(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_BLOCK_UNKNOWN_CALLS] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun setBlockCommercialPrefixes(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_BLOCK_COMMERCIAL_PREFIXES] = enabled
            snapshotFromPreferences(it)
        }
    }

    suspend fun addUnreadMissedCall(entry: UnreadMissedCall): List<UnreadMissedCall> {
        var updatedList: List<UnreadMissedCall> = emptyList()
        context.dataStore.edit { prefs ->
            val current = decodeUnreadMissedCalls(prefs[KEY_UNREAD_MISSED_CALLS_JSON])
            val isDuplicate = current.any { existing ->
                existing.phoneNumber == entry.phoneNumber &&
                    kotlin.math.abs(existing.timestamp - entry.timestamp) < 2_000L
            }
            updatedList = if (isDuplicate) current else (listOf(entry) + current).sortedByDescending { it.timestamp }.take(50)
            prefs[KEY_UNREAD_MISSED_CALLS_JSON] = encodeUnreadMissedCalls(updatedList)
        }
        return updatedList
    }

    suspend fun markAllMissedCallsAsRead(readAtMillis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_UNREAD_MISSED_CALLS_JSON)
            prefs[KEY_LAST_MISSED_READ_AT] = readAtMillis
        }
    }

    suspend fun setGithubUpdateRepo(repoSlug: String) {
        val cleaned = repoSlug.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
            .ifBlank { DEFAULT_GITHUB_UPDATE_REPO }
        context.dataStore.edit { prefs ->
            prefs[KEY_GITHUB_UPDATE_REPO] = cleaned
        }
    }

    suspend fun setSkippedUpdateVersion(versionTag: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SKIPPED_UPDATE_VERSION] = versionTag.trim()
        }
    }
}
