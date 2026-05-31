package com.gram.api

import com.gram.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class GrammarResult(
    val correctedText: String,
    val rewrittenText: String,
    val hasErrors: Boolean,
    val summary: String
)

class ClaudeApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mediaType = "application/json".toMediaType()

    suspend fun analyzeText(text: String, platform: String): Result<GrammarResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val systemPrompt = buildSystemPrompt(platform)
                val userPrompt = buildUserPrompt(text, platform)

                val requestBody = buildRequestBody(systemPrompt, userPrompt)
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", BuildConfig.CLAUDE_API_KEY)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(requestBody.toRequestBody(mediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    error("API error ${response.code}: ${response.body?.string()}")
                }

                val responseText = response.body?.string() ?: error("Empty response")
                parseResponse(responseText, text)
            }
        }

    private fun buildSystemPrompt(platform: String): String = """
        You are an expert writing assistant helping users craft polished content for $platform.
        When given text, you will:
        1. Correct grammar, spelling, and punctuation errors
        2. Provide a rewritten version optimized for $platform's tone and audience
        3. Keep the user's core message and intent intact

        Always respond in valid JSON with this exact structure:
        {
          "corrected": "the grammar-corrected text (minimal changes)",
          "rewritten": "a polished rewrite optimized for $platform",
          "has_errors": true/false,
          "summary": "one-line description of changes made"
        }
    """.trimIndent()

    private fun buildUserPrompt(text: String, platform: String): String =
        "Analyze and improve this $platform post:\n\n\"$text\""

    private fun buildRequestBody(systemPrompt: String, userPrompt: String): String {
        val body = mapOf(
            "model" to "claude-sonnet-4-6",
            "max_tokens" to 1024,
            "system" to systemPrompt,
            "messages" to listOf(
                mapOf("role" to "user", "content" to userPrompt)
            )
        )
        return gson.toJson(body)
    }

    private fun parseResponse(responseJson: String, originalText: String): GrammarResult {
        val apiResponse = gson.fromJson(responseJson, ApiResponse::class.java)
        val content = apiResponse.content.firstOrNull()?.text ?: error("No content in response")

        // Extract JSON block from the response text
        val jsonStart = content.indexOf('{')
        val jsonEnd = content.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1) error("No JSON in response: $content")

        val jsonText = content.substring(jsonStart, jsonEnd + 1)
        val result = gson.fromJson(jsonText, ParsedResult::class.java)

        return GrammarResult(
            correctedText = result.corrected.ifBlank { originalText },
            rewrittenText = result.rewritten.ifBlank { originalText },
            hasErrors = result.has_errors,
            summary = result.summary.ifBlank { "No changes needed" }
        )
    }

    private data class ApiResponse(
        val content: List<ContentBlock>
    )

    private data class ContentBlock(
        val type: String,
        val text: String
    )

    private data class ParsedResult(
        val corrected: String = "",
        val rewritten: String = "",
        val has_errors: Boolean = false,
        val summary: String = ""
    )
}
