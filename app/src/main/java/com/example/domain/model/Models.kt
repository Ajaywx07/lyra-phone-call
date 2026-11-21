package com.example.domain.model

enum class CallStateEnum {
    IDLE,
    RINGING,
    DIALING,
    ACTIVE,
    VOICEMAIL,
    DISCONNECTED
}

enum class CallTypeEnum {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

enum class VoicemailPhase(val label: String) {
    IDLE("Ready"),
    SPEAKING_GREETING("Lyra Speaking Greeting..."),
    PLAYING_BEEP("Playing Beep..."),
    RECORDING_MESSAGE("Recording Caller Message..."),
    PROCESSING_AI("AI Extracting Key Points..."),
    READY("Voicemail Ready"),
    ERROR("Needs Attention")
}

enum class AiProvider(val key: String, val displayName: String, val subtitle: String) {
    FIREBASE_AI("FIREBASE_AI", "Firebase AI", "Zero-key built-in Gemini via App Check"),
    GEMINI_REST("GEMINI_REST", "Gemini API", "Direct Google AI Studio / Gemini API key"),
    OPENAI_COMPATIBLE("OPENAI_COMPATIBLE", "OpenAI / Custom", "OpenAI, Groq, OpenRouter, or custom endpoint");

    companion object {
        fun fromKey(key: String?): AiProvider =
            entries.firstOrNull { it.key == key } ?: FIREBASE_AI
    }
}

enum class CallScreenStyle(val key: String, val displayName: String, val subtitle: String) {
    LYRA_AI("LYRA_AI", "Lyra AI", "Futuristic dark glass & glowing orb"),
    IPHONE("IPHONE", "iPhone Style", "Clean minimalist iOS call layout"),
    NORMAL("NORMAL", "Normal Classic", "Crisp standard Android card UI"),
    GOOGLE_PIXEL("GOOGLE_PIXEL", "Google Pixel", "Material You rounded pill layout");

    companion object {
        fun fromKey(key: String?): CallScreenStyle =
            entries.firstOrNull { it.key == key } ?: LYRA_AI
    }
}

enum class CallAnimationStyle(val key: String, val displayName: String) {
    ORB_PULSE("ORB_PULSE", "Orb Pulse"),
    RIPPLE_RINGS("RIPPLE_RINGS", "Ripple Waves"),
    GRADIENT_AURORA("GRADIENT_AURORA", "Aurora Glow"),
    SLIDE_GLOW("SLIDE_GLOW", "Neon Sweep");

    companion object {
        fun fromKey(key: String?): CallAnimationStyle =
            entries.firstOrNull { it.key == key } ?: ORB_PULSE
    }
}

data class AiEngineConfig(
    val provider: AiProvider = AiProvider.FIREBASE_AI,
    val modelName: String = "gemini-3.8-flash",
    val apiKey: String = "",
    val customBaseUrl: String = "https://api.openai.com/v1"
)

data class VoicemailAnalysisResult(
    val transcript: String,
    val keyPoints: List<String>,
    val summary: String,
    val actionPrompt: String
)

data class CallerInfo(
    val phoneNumber: String,
    val formattedNumber: String,
    val displayName: String,
    val photoUri: String? = null,
    val isKnown: Boolean = false,
    val isPriority: Boolean = false,
    val isOwner: Boolean = false,
    val contactLabel: String = "Mobile"
)

data class CallHistoryItemData(
    val id: String,
    val phoneNumber: String,
    val contactName: String?,
    val photoUri: String? = null,
    val callType: CallTypeEnum,
    val timestamp: Long,
    val durationSeconds: Long,
    val formattedDuration: String,
    val formattedDate: String,
    val simLabel: String? = null,
    val isPriority: Boolean = false,
    val isOwner: Boolean = false,
    val isDemoData: Boolean = false
)

data class UnreadMissedCall(
    val callerName: String,
    val phoneNumber: String,
    val timestamp: Long,
    val simLabel: String? = null
)

data class IncomingCallSettingsSnapshot(
    val isLyraEnabled: Boolean = true,
    val ownerName: String = "Ajay",
    val ownerPhone: String = "+91 98765 43210",
    val isFullscreenIncomingEnabled: Boolean = true,
    val isIncomingScreenEnabled: Boolean = true,
    val isPersistentNotificationEnabled: Boolean = true,
    val isBackgroundCallingEnabled: Boolean = true,
    val showContactPhotoOnCall: Boolean = true,
    val blockUnknownCalls: Boolean = false,
    val blockCommercialPrefixes: Boolean = false,
    val themeMode: String = "IOS_GLASS"
)

data class ContactItemData(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val email: String? = null,
    val label: String = "Mobile",
    val isPriority: Boolean = false,
    val isOwner: Boolean = false,
    val isDemoData: Boolean = false
)
