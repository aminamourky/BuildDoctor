package com.builddoctor

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class LogParser {

    private val githubActionStepPattern = Regex("""##\[group](.+?)$""")
    private val githubActionErrorPattern = Regex("""##\[error](.+?)$""")
    private val githubActionWarningPattern = Regex("""##\[warning](.+?)$""")

    private val jenkinsStepPattern = Regex("""\[Pipeline] (?:stage|step)\s*\{?\s*(.+)""")
    private val jenkinsErrorPattern = Regex("""ERROR:\s*(.+)""")

    private val teamcityStepPattern = Regex("""##teamcity\[blockOpened name='(.+?)']""")
    private val teamcityErrorPattern = Regex("""##teamcity\[message text='(.+?)' status='ERROR']""")

    private val genericErrorPattern = Regex("""(?i)(error|exception|failed|failure):\s*(.+)""")
    private val genericWarningPattern = Regex("""(?i)(warning|warn):\s*(.+)""")

    private val timestampPatterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ISO_DATE_TIME
    )

    fun parse(logContent: String, logType: String = "generic"): LogAnalysis {
        val lines = logContent.lines()
        val steps = mutableListOf<BuildStep>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        when (logType.lowercase()) {
            "github-actions" -> parseGitHubActions(lines, steps, errors, warnings)
            "jenkins" -> parseJenkins(lines, steps, errors, warnings)
            "teamcity" -> parseTeamCity(lines, steps, errors, warnings)
            else -> parseGeneric(lines, steps, errors, warnings)
        }

        val totalDuration = calculateTotalDuration(logContent)
        val failedSteps = steps.count { it.status == "failed" }

        return LogAnalysis(
            totalSteps = steps.size,
            failedSteps = failedSteps,
            totalDuration = totalDuration,
            steps = steps,
            errors = errors,
            warnings = warnings
        )
    }

    private fun parseGitHubActions(
        lines: List<String>,
        steps: MutableList<BuildStep>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        lines.forEachIndexed { index, line ->
            githubActionStepPattern.find(line)?.let { match ->
                val stepName = match.groupValues[1].trim()
                val hasError = lines.drop(index).take(10).any {
                    githubActionErrorPattern.containsMatchIn(it)
                }
                steps.add(BuildStep(
                    name = stepName,
                    status = if (hasError) "failed" else "success",
                    lineNumber = index + 1
                ))
            }

            githubActionErrorPattern.find(line)?.let { match ->
                errors.add(match.groupValues[1].trim())
            }

            githubActionWarningPattern.find(line)?.let { match ->
                warnings.add(match.groupValues[1].trim())
            }
        }
    }

    private fun parseJenkins(
        lines: List<String>,
        steps: MutableList<BuildStep>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        lines.forEachIndexed { index, line ->
            jenkinsStepPattern.find(line)?.let { match ->
                val stepName = match.groupValues[1].trim()
                steps.add(BuildStep(
                    name = stepName,
                    status = "success",
                    lineNumber = index + 1
                ))
            }

            jenkinsErrorPattern.find(line)?.let { match ->
                val errorMsg = match.groupValues[1].trim()
                errors.add(errorMsg)
                if (steps.isNotEmpty()) {
                    steps[steps.lastIndex] = steps.last().copy(
                        status = "failed",
                        errorMessage = errorMsg
                    )
                }
            }
        }
    }

    private fun parseTeamCity(
        lines: List<String>,
        steps: MutableList<BuildStep>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        lines.forEachIndexed { index, line ->
            teamcityStepPattern.find(line)?.let { match ->
                val stepName = match.groupValues[1]
                steps.add(BuildStep(
                    name = stepName,
                    status = "success",
                    lineNumber = index + 1
                ))
            }

            teamcityErrorPattern.find(line)?.let { match ->
                val errorMsg = match.groupValues[1]
                errors.add(errorMsg)
            }
        }
    }

    private fun parseGeneric(
        lines: List<String>,
        steps: MutableList<BuildStep>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        var currentStep: String? = null

        lines.forEachIndexed { index, line ->
            if (line.contains("Step", ignoreCase = true) ||
                line.contains("Stage", ignoreCase = true) ||
                line.startsWith("===") ||
                line.startsWith("---")) {

                currentStep = line.trim()
                steps.add(BuildStep(
                    name = currentStep!!,
                    status = "success",
                    lineNumber = index + 1
                ))
            }

            genericErrorPattern.find(line)?.let { match ->
                val errorMsg = match.groupValues[2].trim()
                errors.add(errorMsg)

                if (steps.isNotEmpty()) {
                    steps[steps.lastIndex] = steps.last().copy(
                        status = "failed",
                        errorMessage = errorMsg
                    )
                }
            }

            genericWarningPattern.find(line)?.let { match ->
                val warningMsg = match.groupValues[2].trim()
                warnings.add(warningMsg)
            }
        }

        if (steps.isEmpty() && (errors.isNotEmpty() || warnings.isNotEmpty())) {
            steps.add(BuildStep(
                name = "Build Process",
                status = if (errors.isNotEmpty()) "failed" else "success",
                errorMessage = errors.firstOrNull()
            ))
        }
    }

    private fun calculateTotalDuration(logContent: String): Long? {
        val timestamps = mutableListOf<LocalDateTime>()

        logContent.lines().forEach { line ->
            for (formatter in timestampPatterns) {
                try {
                    val timestampMatch = Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}""")
                        .find(line)

                    timestampMatch?.let {
                        val timestamp = LocalDateTime.parse(it.value, formatter)
                        timestamps.add(timestamp)
                    }
                    break
                } catch (e: DateTimeParseException) {
                    continue
                }
            }
        }

        return if (timestamps.size >= 2) {
            Duration.between(timestamps.first(), timestamps.last()).toMillis()
        } else null
    }
}