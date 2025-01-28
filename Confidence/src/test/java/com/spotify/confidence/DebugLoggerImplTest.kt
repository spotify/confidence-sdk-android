package com.spotify.confidence

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Date

class DebugLoggerImplTest {

    private lateinit var verboseLogger: DebugLogger

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        verboseLogger = DebugLoggerImpl(LoggingLevel.VERBOSE, "key")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun logEventLogsOnDebugLevel() {
        verboseLogger.logEvent("action", EngineEvent("event", Date(0), mapOf()))
        verify { Log.d("Confidence", any()) }
    }

    @Test
    fun logMessageUsesDebugNormally() {
        verboseLogger.logMessage("Normal Debug")
        verify { Log.d("Confidence", "Normal Debug") }
    }

    @Test
    fun logMessageWithWarningTagLogsOnWarning() {
        verboseLogger.logMessage("Warning", isWarning = true)
        verify { Log.w("Confidence", "Warning") }
    }

    @Test
    fun logMessageWithThrowableLogsAsError() {
        val throwable = Throwable("ErrorErrorMessage")
        verboseLogger.logMessage("Error", throwable = throwable)
        verify { Log.e("Confidence", "Error: ErrorErrorMessage") }
    }

    @Test
    fun logMessageWithThrowableAndWarningTrueLogsAsWarning() {
        val throwable = Throwable("WarningErrorMessage")
        verboseLogger.logMessage("Warning", isWarning = true, throwable = throwable)
        verify { Log.w("Confidence", "Warning: WarningErrorMessage") }
    }

    @Test
    fun logFlagUsesVerboseLevel() {
        verboseLogger.logFlag("Resolve")
        verify { Log.v("Confidence", "[Resolve] null") }
        verboseLogger.logFlag("Apply", "myFlag")
        verify { Log.v("Confidence", "[Apply] myFlag") }
    }

    @Test
    fun logContextLogsOnVerboseLevel() {
        verboseLogger.logContext("action", mapOf("key" to ConfidenceValue.String("value")))
        verify { Log.v("Confidence", "[action] {key=value}") }
    }

    @Test
    fun filterOnDebugLevel() {
        val debugLogger = DebugLoggerImpl(LoggingLevel.DEBUG, clientKey = "key")
        debugLogger.logFlag("Resolve")
        verify(inverse = true) { Log.v("Confidence", "[Resolve] null") }
        debugLogger.logMessage("Normal Debug")
        verify { Log.d("Confidence", "Normal Debug") }
    }

    @Test
    fun filterOnWarnLevel() {
        val warnLogger = DebugLoggerImpl(LoggingLevel.WARN, clientKey = "key")
        warnLogger.logFlag("Resolve")
        verify(inverse = true) { Log.v("Confidence", "[Resolve] null") }
        warnLogger.logMessage("Normal Debug")
        verify(inverse = true) { Log.d("Confidence", "Normal Debug") }

        warnLogger.logMessage("Warning msg", isWarning = true)
        verify { Log.w("Confidence", "Warning msg") }
    }

    @Test
    fun filterOnErrorLevel() {
        val errorLogger = DebugLoggerImpl(LoggingLevel.ERROR, clientKey = "key")
        errorLogger.logFlag("Resolve")
        verify(inverse = true) { Log.v("Confidence", "[Resolve] null") }
        errorLogger.logMessage("Normal Debug")
        verify(inverse = true) { Log.d("Confidence", "Normal Debug") }

        errorLogger.logMessage("Warning msg", isWarning = true)
        verify(inverse = true) { Log.w("Confidence", "Warning msg") }

        errorLogger.logMessage("Error msg", throwable = Error("my error"))
        verify { Log.e("Confidence", "Error msg: my error") }
    }

    @Test
    fun filterNoneLevel() {
        val noneLogger = DebugLoggerImpl(LoggingLevel.NONE, clientKey = "key")
        noneLogger.logFlag("Resolve")
        verify(inverse = true) { Log.v("Confidence", "[Resolve] null") }
        noneLogger.logMessage("Normal Debug")
        verify(inverse = true) { Log.d("Confidence", "Normal Debug") }

        noneLogger.logMessage("Warning msg", isWarning = true)
        verify(inverse = true) { Log.w("Confidence", "Warning msg") }

        noneLogger.logMessage("Error msg", throwable = Error("my error"))
        verify(inverse = true) { Log.e("Confidence", "Error msg: my error") }
    }
}