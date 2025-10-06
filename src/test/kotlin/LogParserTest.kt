package com.builddoctor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogParserTest {

    private val parser = LogParser()

    @Test
    fun `test parse generic log with error`() {
        val logContent = """
            Step 1: Build
            ERROR: Compilation failed
            Error: Missing dependency
        """.trimIndent()

        val result = parser.parse(logContent, "generic")

        assertEquals(1, result.totalSteps)
        assertEquals(1, result.failedSteps)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.contains("Compilation failed"))
    }

    @Test
    fun `test parse GitHub Actions log`() {
        val logContent = """
            ##[group]Build Step
            ##[error]Test failed
            ##[warning]Deprecated API
        """.trimIndent()

        val result = parser.parse(logContent, "github-actions")

        assertEquals(1, result.totalSteps)
        assertEquals(1, result.errors.size)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `test parse log with no failures`() {
        val logContent = """
            Step 1: Build
            Step 2: Test
            All tests passed
        """.trimIndent()

        val result = parser.parse(logContent, "generic")

        assertEquals(2, result.totalSteps)
        assertEquals(0, result.failedSteps)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun `test parse TeamCity log`() {
        val logContent = """
            ##teamcity[blockOpened name='Build']
            ##teamcity[message text='Build failed' status='ERROR']
        """.trimIndent()

        val result = parser.parse(logContent, "teamcity")

        assertEquals(1, result.totalSteps)
        assertEquals(1, result.errors.size)
    }
}