package com.builddoctor

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenAIClient(private val apiKey: String) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun analyzeLog(analysis: LogAnalysis): AIAnalysisResult {
        val prompt = buildPrompt(analysis)

        val response = client.post("https://api.openai.com/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(OpenAIRequest(
                model = "gpt-3.5-turbo",
                messages = listOf(
                    Message(role = "system", content = SYSTEM_PROMPT),
                    Message(role = "user", content = prompt)
                ),
                temperature = 0.7,
                maxTokens = 500
            ))
        }

        val openAIResponse: OpenAIResponse = response.body()
        val aiContent = openAIResponse.choices.firstOrNull()?.message?.content
            ?: "Unable to generate analysis"

        return parseAIResponse(aiContent)
    }

    private fun buildPrompt(analysis: LogAnalysis): String {
        return buildString {
            appendLine("Analyze this CI/CD build failure:")
            appendLine()
            appendLine("Build Status: ${if (analysis.failedSteps > 0) "FAILED" else "SUCCESS"}")
            appendLine("Total Steps: ${analysis.totalSteps}")
            appendLine("Failed Steps: ${analysis.failedSteps}")
            appendLine()

            if (analysis.errors.isNotEmpty()) {
                appendLine("Errors Found:")
                analysis.errors.take(5).forEach { error ->
                    appendLine("- $error")
                }
                if (analysis.errors.size > 5) {
                    appendLine("... and ${analysis.errors.size - 5} more errors")
                }
                appendLine()
            }

            if (analysis.warnings.isNotEmpty()) {
                appendLine("Warnings Found:")
                analysis.warnings.take(3).forEach { warning ->
                    appendLine("- $warning")
                }
                appendLine()
            }

            if (analysis.steps.any { it.status == "failed" }) {
                appendLine("Failed Steps:")
                analysis.steps.filter { it.status == "failed" }.forEach { step ->
                    appendLine("- ${step.name}")
                    step.errorMessage?.let { appendLine("  Error: $it") }
                }
            }
        }
    }

    private fun parseAIResponse(content: String): AIAnalysisResult {
        val lines = content.lines()
        val rootCause = lines.find { it.contains("Root Cause", ignoreCase = true) }
            ?.substringAfter(":", "").takeIf { it.isNotBlank() }
            ?: extractSection(content, "root cause", "recommendation")

        val recommendations = extractSection(content, "recommendation", "impact")
            .ifBlank { extractBulletPoints(content) }

        val impact = extractSection(content, "impact", null)
            .ifBlank { "Build failure preventing deployment" }

        return AIAnalysisResult(
            rootCause = rootCause.trim(),
            recommendations = recommendations.trim(),
            impact = impact.trim()
        )
    }

    private fun extractSection(content: String, startMarker: String, endMarker: String?): String {
        val lower = content.lowercase()
        val startIdx = lower.indexOf(startMarker)
        if (startIdx == -1) return ""

        val start = content.indexOf('\n', startIdx) + 1
        if (start == 0) return ""

        val end = if (endMarker != null) {
            val endIdx = lower.indexOf(endMarker, start)
            if (endIdx != -1) endIdx else content.length
        } else {
            content.length
        }

        return content.substring(start, end).trim()
    }

    private fun extractBulletPoints(content: String): String {
        return content.lines()
            .filter { it.trim().startsWith("-") || it.trim().startsWith("•") }
            .joinToString("\n")
            .trim()
    }

    fun close() {
        client.close()
    }

    companion object {
        private const val SYSTEM_PROMPT = """You are an expert CI/CD engineer analyzing build failures. 
Provide concise, actionable analysis in this format:

Root Cause: [1-2 sentence explanation of what caused the failure]

Recommendations:
- [Specific action 1]
- [Specific action 2]
- [Specific action 3]

Impact: [Brief description of how this affects the pipeline]

Keep responses focused and practical."""
    }
}

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 500
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: Message
)

data class AIAnalysisResult(
    val rootCause: String,
    val recommendations: String,
    val impact: String
)