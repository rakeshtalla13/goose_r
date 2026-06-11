package com.gram.api

import com.google.gson.Gson
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

class ClaudeApiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mediaType = "application/json".toMediaType()

    suspend fun validateKey(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = gson.toJson(
                mapOf(
                    "model" to "claude-haiku-4-5-20251001",
                    "max_tokens" to 1,
                    "messages" to listOf(mapOf("role" to "user", "content" to "Hi"))
                )
            )
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toRequestBody(mediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.code == 401) error("Invalid API key")
            if (!response.isSuccessful && response.code != 400) {
                error("Error ${response.code}")
            }
        }
    }

    suspend fun analyzeText(text: String, platform: String): Result<GrammarResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val systemPrompt = buildSystemPrompt(platform)
                val userPrompt = "Analyze and improve this $platform post:\n\n\"$text\""
                val requestBody = buildRequestBody(systemPrompt, userPrompt)

                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(requestBody.toRequestBody(mediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    error("API error ${response.code}: ${response.body?.string()}")
                }

                parseResponse(response.body?.string() ?: error("Empty response"), text)
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

    private fun buildRequestBody(systemPrompt: String, userPrompt: String): String =
        gson.toJson(
            mapOf(
                "model" to "claude-sonnet-4-6",
                "max_tokens" to 1024,
                "system" to systemPrompt,
                "messages" to listOf(mapOf("role" to "user", "content" to userPrompt))
            )
        )

    private fun parseResponse(responseJson: String, originalText: String): GrammarResult {
        val apiResponse = gson.fromJson(responseJson, ApiResponse::class.java)
        val content = apiResponse.content.firstOrNull()?.text ?: error("No content in response")

        val jsonStart = content.indexOf('{')
        val jsonEnd = content.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1) error("No JSON in response: $content")

        val result = gson.fromJson(content.substring(jsonStart, jsonEnd + 1), ParsedResult::class.java)
        return GrammarResult(
            correctedText = result.corrected.ifBlank { originalText },
            rewrittenText = result.rewritten.ifBlank { originalText },
            hasErrors = result.has_errors,
            summary = result.summary.ifBlank { "No changes needed" }
        )
    }

    private data class ApiResponse(val content: List<ContentBlock>)
    private data class ContentBlock(val type: String, val text: String)
    private data class ParsedResult(
        val corrected: String = "",
        val rewritten: String = "",
        val has_errors: Boolean = false,
        val summary: String = ""
    )
}
