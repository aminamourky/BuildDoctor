package com.builddoctor

import kotlinx.serialization.Serializable

@Serializable
data class BuildStep(
    val name: String,
    val duration: Long? = null,
    val status: String,
    val errorMessage: String? = null,
    val lineNumber: Int? = null
)

@Serializable
data class LogAnalysis(
    val totalSteps: Int,
    val failedSteps: Int,
    val totalDuration: Long? = null,
    val steps: List<BuildStep>,
    val errors: List<String>,
    val warnings: List<String>
)

@Serializable
data class AnalyzeRequest(
    val logContent: String,
    val logType: String = "generic"
)

@Serializable
data class AnalyzeResponse(
    val status: String,
    val analysis: LogAnalysis,
    val summary: String,
    val aiAnalysis: AIAnalysisData? = null
)

@Serializable
data class AIAnalysisData(
    val rootCause: String,
    val recommendations: String,
    val impact: String
)