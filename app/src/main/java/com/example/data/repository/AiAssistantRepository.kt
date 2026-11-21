package com.example.data.repository

/**
 * Interface for AI assistant capabilities.
 * Designed so that future providers (e.g., Local on-device LLM, Gemini API, or OpenAI)
 * can be plugged in seamlessly without modifying the UI layer.
 */
interface AiAssistantRepository {
    suspend fun generateCallSummary(
        callerName: String,
        durationSeconds: Long,
        notes: String
    ): String

    suspend fun suggestFollowUps(notes: String): List<String>
}

/**
 * Default local-first rule-based implementation.
 * Operates 100% offline on-device with zero data transmission.
 */
class LocalRuleBasedAiRepository : AiAssistantRepository {

    override suspend fun generateCallSummary(
        callerName: String,
        durationSeconds: Long,
        notes: String
    ): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        val durationFormatted = String.format("%02d:%02d", minutes, seconds)

        val builder = StringBuilder()
        builder.append("• Call completed with $callerName (Duration: $durationFormatted)\n")

        if (notes.isNotBlank()) {
            val lines = notes.lines().filter { it.isNotBlank() }
            builder.append("• Key Discussion Points:\n")
            lines.forEach { line ->
                val cleanLine = line.trim().removePrefix("•").trim()
                builder.append("  - $cleanLine\n")
            }

            // Detect action items or follow-ups based on keywords
            val followUps = suggestFollowUps(notes)
            if (followUps.isNotEmpty()) {
                builder.append("• AI Detected Action Items:\n")
                followUps.forEach { item ->
                    builder.append("  * $item\n")
                }
            }
        } else {
            builder.append("• Call concluded with no manual notes captured.\n")
            builder.append("• Status: Regular conversation completed successfully.")
        }

        return builder.toString().trim()
    }

    override suspend fun suggestFollowUps(notes: String): List<String> {
        val followUps = mutableListOf<String>()
        val lower = notes.lowercase()

        if (lower.contains("call back") || lower.contains("call again") || lower.contains("tomorrow")) {
            followUps.add("Schedule follow-up call tomorrow")
        }
        if (lower.contains("send") || lower.contains("email") || lower.contains("share") || lower.contains("document")) {
            followUps.add("Send requested documents/information")
        }
        if (lower.contains("meeting") || lower.contains("schedule") || lower.contains("discuss")) {
            followUps.add("Confirm calendar appointment")
        }
        if (lower.contains("pay") || lower.contains("invoice") || lower.contains("bill") || lower.contains("price")) {
            followUps.add("Verify financial/billing details")
        }

        if (followUps.isEmpty() && notes.isNotBlank()) {
            followUps.add("Review discussion notes and log any pending tasks")
        }

        return followUps
    }
}
