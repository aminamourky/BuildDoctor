package com.builddoctor

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSerialization()
        configureRouting()
    }.start(wait = true)
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.configureRouting() {
    val logParser = LogParser()

    // Get API key from environment variable
    val openAIKey = System.getenv("OPENAI_API_KEY")
    val openAIClient = if (openAIKey != null) {
        OpenAIClient(openAIKey)
    } else {
        null
    }

    routing {
        get("/") {
            call.respondText("BuildDoctor API v1.0 - CI/CD Log Analyzer", ContentType.Text.Plain)
        }

        get("/health") {
            call.respond(mapOf(
                "status" to "healthy",
                "aiEnabled" to (openAIClient != null)
            ))
        }

        post("/analyze") {
            try {
                val request = call.receive<AnalyzeRequest>()

                if (request.logContent.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Log content cannot be empty")
                    )
                    return@post
                }

                val analysis = logParser.parse(request.logContent, request.logType)
                val summary = generateSummary(analysis)

                // Get AI analysis if available and there are failures
                val aiAnalysis = if (openAIClient != null && analysis.failedSteps > 0) {
                    try {
                        val aiResult = openAIClient.analyzeLog(analysis)
                        AIAnalysisData(
                            rootCause = aiResult.rootCause,
                            recommendations = aiResult.recommendations,
                            impact = aiResult.impact
                        )
                    } catch (e: Exception) {
                        println("AI analysis failed: ${e.message}")
                        null
                    }
                } else {
                    null
                }

                val response = AnalyzeResponse(
                    status = if (analysis.failedSteps > 0) "failed" else "success",
                    analysis = analysis,
                    summary = summary,
                    aiAnalysis = aiAnalysis
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to analyze log: ${e.message}")
                )
            }
        }
    }
}

fun generateSummary(analysis: LogAnalysis): String {
    val parts = mutableListOf<String>()

    parts.add("Analyzed ${analysis.totalSteps} build steps.")

    if (analysis.failedSteps > 0) {
        parts.add("${analysis.failedSteps} step(s) failed.")
    } else {
        parts.add("All steps completed successfully.")
    }

    if (analysis.errors.isNotEmpty()) {
        parts.add("Found ${analysis.errors.size} error(s).")
    }

    if (analysis.warnings.isNotEmpty()) {
        parts.add("Found ${analysis.warnings.size} warning(s).")
    }

    analysis.totalDuration?.let { duration ->
        val seconds = duration / 1000
        parts.add("Total duration: ${seconds}s.")
    }

    return parts.joinToString(" ")
}