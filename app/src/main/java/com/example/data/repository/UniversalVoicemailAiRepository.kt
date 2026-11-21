package com.example.data.repository

import android.util.Base64
import android.util.Log
import com.example.domain.model.AiEngineConfig
import com.example.domain.model.AiProvider
import com.example.domain.model.VoicemailAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "VoicemailAiRepo"

/**
 * Universal AI processor for caller voicemails.
 *
 * Supports hot-swapping AI Provider, Model Name, API Key, and Custom Base URL
 * directly from Settings at runtime without recompiling:
 * - FIREBASE_AI: Built-in zero-key Gemini via Firebase AI Logic + App Check (default)
 * - GEMINI_REST: Direct Google Gemini REST API using a runtime user-supplied API key
 * - OPENAI_COMPATIBLE: OpenAI / Groq / OpenRouter / custom REST endpoint with user API key
 */
class UniversalVoicemailAiRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeVoicemailAudio(
        wavBytes: ByteArray,
        callerName: String,
        config: AiEngineConfig
    ): Result<VoicemailAnalysisResult> {
        if (wavBytes.size <= 44) {
            return Result.failure(IllegalArgumentException("Recorded voicemail audio is empty."))
        }

        val resolvedModel = config.modelName.trim().ifBlank { "gemini-3.8-flash" }
        return when (config.provider) {
            AiProvider.FIREBASE_AI -> analyzeWithFirebaseAi(wavBytes, callerName, resolvedModel)
            AiProvider.GEMINI_REST -> analyzeWithGeminiRest(wavBytes, callerName, resolvedModel, config.apiKey)
            AiProvider.OPENAI_COMPATIBLE -> analyzeWithOpenAiCompatible(
                wavBytes = wavBytes,
                callerName = callerName,
                modelName = resolvedModel,
                apiKey = config.apiKey,
                baseUrl = config.customBaseUrl
            )
        }
    }

    suspend fun testConnection(config: AiEngineConfig): Result<String> {
        val modelName = config.modelName.trim().ifBlank { "gemini-3.8-flash" }
        return try {
            withTimeout(25_000L) {
                when (config.provider) {
                    AiProvider.FIREBASE_AI -> {
                        val model = Firebase.ai.generativeModel(modelName = modelName)
                        val response = model.generateContent("Reply with OK in 2 words.")
                        val reply = response.text?.trim().orEmpty()
                        if (reply.isNotBlank()) {
                            Result.success("Firebase AI ($modelName) connected ✓")
                        } else {
                            Result.failure(IllegalStateException("Empty test response from $modelName"))
                        }
                    }

                    AiProvider.GEMINI_REST -> {
                        val key = config.apiKey.trim()
                        if (key.isBlank()) {
                            return@withTimeout Result.failure(
                                IllegalArgumentException("Enter a Gemini API Key first.")
                            )
                        }
                        val endpoint =
                            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$key"
                        val bodyJson = JSONObject().apply {
                            put(
                                "contents",
                                JSONArray().put(
                                    JSONObject().put(
                                        "parts",
                                        JSONArray().put(JSONObject().put("text", "Reply OK."))
                                    )
                                )
                            )
                        }
                        val responseText = executeJsonPost(endpoint, bodyJson.toString())
                        val parsed = extractGeminiRestText(responseText)
                        if (parsed.isNotBlank()) {
                            Result.success("Gemini API ($modelName) connected ✓")
                        } else {
                            Result.failure(IllegalStateException("Gemini API returned empty response."))
                        }
                    }

                    AiProvider.OPENAI_COMPATIBLE -> {
                        val key = config.apiKey.trim()
                        if (key.isBlank()) {
                            return@withTimeout Result.failure(
                                IllegalArgumentException("Enter an API Key for the OpenAI-compatible provider.")
                            )
                        }
                        val normalizedBase = config.customBaseUrl.trim().trimEnd('/').ifBlank {
                            "https://api.openai.com/v1"
                        }
                        val bodyJson = JSONObject().apply {
                            put("model", modelName)
                            put(
                                "messages",
                                JSONArray().put(
                                    JSONObject()
                                        .put("role", "user")
                                        .put("content", "Reply OK.")
                                )
                            )
                            put("max_tokens", 12)
                        }
                        val responseText = executeJsonPost(
                            url = "$normalizedBase/chat/completions",
                            jsonBody = bodyJson.toString(),
                            bearerToken = key
                        )
                        val parsed = extractOpenAiChatContent(responseText)
                        if (parsed.isNotBlank()) {
                            Result.success("Custom AI ($modelName) connected ✓")
                        } else {
                            Result.failure(IllegalStateException("Custom AI returned empty response."))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI connection test failed", e)
            Result.failure(e)
        }
    }

    private suspend fun analyzeWithFirebaseAi(
        wavBytes: ByteArray,
        callerName: String,
        modelName: String
    ): Result<VoicemailAnalysisResult> {
        return try {
            val schema = Schema.obj(
                properties = mapOf(
                    "transcript" to Schema.string("Accurate verbatim transcript of the voicemail audio."),
                    "keyPoints" to Schema.array(
                        Schema.string("Short actionable bullet point"),
                        description = "2 to 4 concise key points extracted from the voicemail."
                    ),
                    "summary" to Schema.string(
                        "Concise 1-2 line summary highlighting actionable prompts for Ajay."
                    ),
                    "actionPrompt" to Schema.string(
                        "Single clear next action for Ajay (e.g., 'Call back before 5 PM about invoice')."
                    )
                )
            )
            val model = Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = schema
                }
            )
            val promptContent = content {
                inlineData(bytes = wavBytes, mimeType = "audio/wav")
                text(buildAnalysisPrompt(callerName))
            }
            val response = withTimeout(60_000L) {
                model.generateContent(promptContent)
            }
            val rawJson = response.text?.trim().orEmpty()
            if (rawJson.isBlank()) {
                Result.failure(IllegalStateException("Firebase AI returned an empty voicemail summary."))
            } else {
                Result.success(parseStructuredAnalysisJson(rawJson))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI voicemail analysis failed", e)
            Result.failure(e)
        }
    }

    private suspend fun analyzeWithGeminiRest(
        wavBytes: ByteArray,
        callerName: String,
        modelName: String,
        apiKey: String
    ): Result<VoicemailAnalysisResult> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return Result.failure(
                IllegalArgumentException("API Key is required for Gemini API mode. Add it in Settings or switch to Firebase AI.")
            )
        }
        return try {
            val audioBase64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            val parts = JSONArray().apply {
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "audio/wav")
                            .put("data", audioBase64)
                    )
                )
                put(JSONObject().put("text", buildJsonAnalysisPrompt(callerName)))
            }
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json")
                )
            }
            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$cleanKey"
            val rawResponse = withTimeout(60_000L) {
                executeJsonPost(endpoint, payload.toString())
            }
            val text = extractGeminiRestText(rawResponse)
            Result.success(parseStructuredAnalysisJson(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini REST voicemail analysis failed", e)
            Result.failure(e)
        }
    }

    private suspend fun analyzeWithOpenAiCompatible(
        wavBytes: ByteArray,
        callerName: String,
        modelName: String,
        apiKey: String,
        baseUrl: String
    ): Result<VoicemailAnalysisResult> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return Result.failure(
                IllegalArgumentException("API Key is required for OpenAI / Custom provider. Enter it in Settings.")
            )
        }
        val normalizedBase = baseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }

        return try {
            withTimeout(60_000L) {
                // Step 1: If user picked an audio-native chat model (e.g. gpt-4o-audio-preview), send audio inline
                if (modelName.contains("audio", ignoreCase = true)) {
                    val audioBase64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
                    val contentParts = JSONArray().apply {
                        put(JSONObject().put("type", "text").put("text", buildJsonAnalysisPrompt(callerName)))
                        put(
                            JSONObject()
                                .put("type", "input_audio")
                                .put(
                                    "input_audio",
                                    JSONObject()
                                        .put("data", audioBase64)
                                        .put("format", "wav")
                                )
                        )
                    }
                    val chatBody = JSONObject().apply {
                        put("model", modelName)
                        put(
                            "messages",
                            JSONArray().put(
                                JSONObject()
                                    .put("role", "user")
                                    .put("content", contentParts)
                            )
                        )
                    }
                    val resp = executeJsonPost("$normalizedBase/chat/completions", chatBody.toString(), cleanKey)
                    val contentText = extractOpenAiChatContent(resp)
                    return@withTimeout Result.success(parseStructuredAnalysisJson(contentText))
                }

                // Step 2: Otherwise transcribe audio via /audio/transcriptions then summarize with user's chosen model
                val sttModel = if (modelName.contains("whisper", ignoreCase = true) ||
                    modelName.contains("transcribe", ignoreCase = true)
                ) {
                    modelName
                } else {
                    "whisper-1"
                }

                val transcript = executeWhisperTranscription(
                    url = "$normalizedBase/audio/transcriptions",
                    wavBytes = wavBytes,
                    model = sttModel,
                    bearerToken = cleanKey
                )

                if (transcript.isBlank()) {
                    return@withTimeout Result.failure(
                        IllegalStateException("Speech transcription returned empty text.")
                    )
                }

                // Summarize transcript into 1-2 line actionable summary + key points
                val summaryModel = if (modelName.contains("whisper", ignoreCase = true)) "gpt-4o-mini" else modelName
                val chatPayload = JSONObject().apply {
                    put("model", summaryModel)
                    put(
                        "messages",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("role", "system")
                                    .put("content", "Return ONLY valid JSON with keys: transcript, keyPoints, summary, actionPrompt.")
                            )
                            .put(
                                JSONObject()
                                    .put("role", "user")
                                    .put(
                                        "content",
                                        "Caller: $callerName\nTranscript:\n\"$transcript\"\n\n${buildJsonAnalysisPrompt(callerName)}"
                                    )
                            )
                    )
                }
                val chatResponse = executeJsonPost(
                    url = "$normalizedBase/chat/completions",
                    jsonBody = chatPayload.toString(),
                    bearerToken = cleanKey
                )
                val parsed = parseStructuredAnalysisJson(extractOpenAiChatContent(chatResponse))
                val merged = if (parsed.transcript.isBlank()) parsed.copy(transcript = transcript) else parsed
                Result.success(merged)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI-compatible voicemail analysis failed", e)
            Result.failure(e)
        }
    }

    private suspend fun executeJsonPost(
        url: String,
        jsonBody: String,
        bearerToken: String? = null
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")
        if (!bearerToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        httpClient.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("AI API HTTP ${response.code}: ${extractErrorMessage(body)}")
            }
            body
        }
    }

    private suspend fun executeWhisperTranscription(
        url: String,
        wavBytes: ByteArray,
        model: String,
        bearerToken: String
    ): String = withContext(Dispatchers.IO) {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "voicemail.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", model)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearerToken")
            .post(multipart)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Audio transcription HTTP ${response.code}: ${extractErrorMessage(body)}")
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
            json?.optString("text", "")?.trim().orEmpty().ifBlank { body.trim() }
        }
    }

    private fun extractGeminiRestText(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: return ""
        val first = candidates.optJSONObject(0) ?: return ""
        val content = first.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val partText = parts.optJSONObject(i)?.optString("text", "").orEmpty()
            sb.append(partText)
        }
        return sb.toString().trim()
    }

    private fun extractOpenAiChatContent(responseBody: String): String {
        val root = JSONObject(responseBody)
        val choices = root.optJSONArray("choices") ?: return ""
        val first = choices.optJSONObject(0) ?: return ""
        val message = first.optJSONObject("message") ?: return ""
        return message.optString("content", "").trim()
    }

    private fun extractErrorMessage(body: String): String {
        val parsed = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return parsed ?: body.take(180).ifBlank { "Unknown API error" }
    }

    private fun buildAnalysisPrompt(callerName: String): String = """
        You are Lyra, Ajay's personal AI calling assistant.
        Listen carefully to this voicemail message left by "$callerName".
        1. Transcribe the message accurately.
        2. Extract 2-4 crisp key points.
        3. Write a concise 1-2 line summary focused on actionable prompts for Ajay (who called, why, and what needs to be done).
        4. Provide one direct actionPrompt (e.g., "Reply with invoice PDF before 4 PM").
    """.trimIndent()

    private fun buildJsonAnalysisPrompt(callerName: String): String = """
        ${buildAnalysisPrompt(callerName)}
        Respond strictly with raw JSON matching:
        {
          "transcript": "full verbatim text",
          "keyPoints": ["point 1", "point 2"],
          "summary": "1-2 concise actionable lines for Ajay",
          "actionPrompt": "single immediate action item"
        }
    """.trimIndent()

    companion object {
        fun parseStructuredAnalysisJson(rawText: String): VoicemailAnalysisResult {
            val cleaned = rawText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonObj = runCatching {
                val start = cleaned.indexOf('{')
                val end = cleaned.lastIndexOf('}')
                val jsonSlice = if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
                JSONObject(jsonSlice)
            }.getOrNull()

            if (jsonObj != null) {
                val transcript = jsonObj.optString("transcript", "").trim()
                val summary = jsonObj.optString("summary", "").trim()
                val actionPrompt = jsonObj.optString("actionPrompt", "").trim()
                val pointsArray = jsonObj.optJSONArray("keyPoints")
                val points = buildList {
                    if (pointsArray != null) {
                        for (i in 0 until pointsArray.length()) {
                            val item = pointsArray.optString(i, "").trim()
                            if (item.isNotBlank()) add(item)
                        }
                    }
                }
                val finalSummary = summary.ifBlank {
                    points.take(2).joinToString(" • ").ifBlank { transcript.take(160) }
                }
                return VoicemailAnalysisResult(
                    transcript = transcript.ifBlank { finalSummary },
                    keyPoints = points.ifEmpty { listOf(finalSummary).filter { it.isNotBlank() } },
                    summary = finalSummary,
                    actionPrompt = actionPrompt.ifBlank { "Follow up with caller" }
                )
            }

            // Graceful fallback when a custom model replies in plain text instead of JSON
            val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }
            val twoLines = lines.take(2).joinToString(" ").ifBlank { cleaned.take(180) }
            return VoicemailAnalysisResult(
                transcript = cleaned,
                keyPoints = lines.take(3),
                summary = twoLines,
                actionPrompt = "Review message & follow up"
            )
        }
    }
}
