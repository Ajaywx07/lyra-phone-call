package com.example.audio

enum class VoiceMode(
    val nativeOrdinal: Int,
    val displayName: String,
    val subtitle: String
) {
    NONE(
        nativeOrdinal = 0,
        displayName = "Normal (Off)",
        subtitle = "Unmodified natural voice passthrough"
    ),
    MALE(
        nativeOrdinal = 1,
        displayName = "Male",
        subtitle = "Deeper pitch & warm chest formant"
    ),
    FEMALE(
        nativeOrdinal = 2,
        displayName = "Female",
        subtitle = "Higher pitch & bright vocal presence"
    ),
    ROBOT(
        nativeOrdinal = 3,
        displayName = "Robot",
        subtitle = "Metallic ring-modulation & synth timbre"
    );

    companion object {
        val selectableEffects: List<VoiceMode> = listOf(MALE, FEMALE, ROBOT)

        fun fromStorageKey(key: String?): VoiceMode =
            entries.firstOrNull { it.name.equals(key?.trim(), ignoreCase = true) } ?: NONE
    }
}
