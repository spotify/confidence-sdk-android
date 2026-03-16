package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.ResolvedFlag
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stress test for rapid evaluation context changes.
 *
 * Hammers putContext() many times in quick succession and verifies that
 * only the LAST context's resolved flags end up on disk and in cache.
 * Earlier in-flight resolves should either be cancelled or their stale
 * responses discarded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RapidContextChangeStressTest {
    private val flagApplierClient: com.spotify.confidence.client.FlagApplierClient = mock()
    private val mockContext: Context = mock()

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(
            Files.createTempDirectory("tmpTests").toFile()
        )
    }

    private fun resolvedFlagsForContext(
        contextValue: String,
        resolveCount: Int
    ) = listOf(
        ResolvedFlag(
            "test-flag",
            "flags/test-flag/variants/variant-$resolveCount",
            mutableMapOf(
                "mystring" to ConfidenceValue.String("value-for-$contextValue")
            ),
            ResolveReason.RESOLVE_REASON_MATCH,
            shouldApply = true
        )
    )

    /**
     * Rapidly fires 20 context changes. Each resolve has a small delay
     * to simulate network latency. Only the final context should survive.
     */
    @Test
    fun testOnlyLastContextSurvivesRapidChanges() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val totalChanges = 20
        val resolveStarted = AtomicInteger(0)
        val resolveCompleted = AtomicInteger(0)

        val flagResolver = object : FlagResolver {
            override suspend fun resolve(
                flags: List<String>,
                context: Map<String, ConfidenceValue>
            ): Result<FlagResolution> {
                val count = resolveStarted.incrementAndGet()
                val contextValue = (context["targeting_key"] as ConfidenceValue.String).string
                // Simulate network latency — enough to overlap with next context change
                delay(50)
                resolveCompleted.incrementAndGet()
                return Result.Success(
                    FlagResolution(
                        context,
                        resolvedFlagsForContext(contextValue, count),
                        "token-$count"
                    )
                )
            }
        }

        val diskStorage = FileDiskStorage.create(mockContext)
        val cache = InMemoryCache()
        val confidence = Confidence(
            clientSecret = "",
            dispatcher = testDispatcher,
            eventSenderEngine = mock(),
            initialContext = mapOf("targeting_key" to ConfidenceValue.String("user-0")),
            cache = cache,
            flagResolver = flagResolver,
            flagApplierClient = flagApplierClient,
            diskStorage = diskStorage,
            region = ConfidenceRegion.GLOBAL,
            debugLogger = null
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        // Initial fetch so cache is populated
        confidence.fetchAndActivate()
        advanceUntilIdle()

        // Hammer context changes
        for (i in 1..totalChanges) {
            confidence.putContext(
                mapOf("targeting_key" to ConfidenceValue.String("user-$i"))
            )
        }

        // Let all coroutines finish
        advanceUntilIdle()

        // The last context should be user-$totalChanges
        val expectedContextValue = "user-$totalChanges"
        val expectedContext = mapOf(
            "targeting_key" to ConfidenceValue.String(expectedContextValue)
        )

        // Verify current context is the last one set
        assertEquals(expectedContext, confidence.getContext())

        // Verify disk storage has flags for the LAST context (or is empty if
        // everything got discarded — then the activate from disk would be empty).
        // Either way, disk must NOT have flags for any intermediate context.
        val stored = diskStorage.read()
        if (stored != FlagResolution.EMPTY) {
            assertEquals(
                "Disk storage must contain flags for the last context only",
                expectedContext,
                stored.context
            )
        }

        // Verify cache evaluation returns the correct value
        confidence.activate()
        val eval = confidence.getFlag("test-flag.mystring", "default-fallback")

        // The value should either be for the last context (if its resolve completed)
        // or stale from initial fetch — but NEVER from an intermediate context
        val acceptableValues = setOf(
            "value-for-$expectedContextValue", // last resolve completed
            "value-for-user-0" // stale from initial fetch (if last resolve was also discarded)
        )
        assertTrue(
            "Flag value '${eval.value}' should be from last context or initial stale, " +
                "not from any intermediate context. Acceptable: $acceptableValues",
            eval.value in acceptableValues
        )

        // Most resolves should have been cancelled — far fewer completions than starts
        println("Resolves started: ${resolveStarted.get()}, completed: ${resolveCompleted.get()}")
        assertTrue(
            "Most intermediate resolves should be cancelled. " +
                "Started: ${resolveStarted.get()}, Completed: ${resolveCompleted.get()}",
            resolveCompleted.get() < resolveStarted.get()
        )
    }

    /**
     * Same barrage but with varying delays to simulate jittery network.
     * Verifies no intermediate context leaks through.
     */
    @Test
    fun testJitteryNetworkWithRapidContextChanges() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val totalChanges = 15
        val storedContexts = mutableListOf<Map<String, ConfidenceValue>>()

        val flagResolver = object : FlagResolver {
            override suspend fun resolve(
                flags: List<String>,
                context: Map<String, ConfidenceValue>
            ): Result<FlagResolution> {
                val contextValue = (context["targeting_key"] as ConfidenceValue.String).string
                val index = contextValue.removePrefix("user-").toInt()
                // Jittery delay — earlier requests take LONGER (simulating them being overtaken)
                delay((totalChanges - index + 1).toLong() * 30)
                return Result.Success(
                    FlagResolution(
                        context,
                        resolvedFlagsForContext(contextValue, index),
                        "token-$index"
                    )
                )
            }
        }

        // Spy on disk storage to track all store() calls
        val realDiskStorage = FileDiskStorage.create(mockContext)
        val spyDiskStorage = object : com.spotify.confidence.cache.DiskStorage {
            override fun store(flagResolution: FlagResolution) {
                storedContexts.add(flagResolution.context)
                realDiskStorage.store(flagResolution)
            }

            override fun read(): FlagResolution = realDiskStorage.read()
            override fun clear() = realDiskStorage.clear()
            override fun writeApplyData(
                applyData: Map<String, MutableMap<String, com.spotify.confidence.apply.ApplyInstance>>
            ) = realDiskStorage.writeApplyData(applyData)

            override fun readApplyData() = realDiskStorage.readApplyData()
        }

        val confidence = Confidence(
            clientSecret = "",
            dispatcher = testDispatcher,
            eventSenderEngine = mock(),
            initialContext = mapOf("targeting_key" to ConfidenceValue.String("user-0")),
            cache = InMemoryCache(),
            flagResolver = flagResolver,
            flagApplierClient = flagApplierClient,
            diskStorage = spyDiskStorage,
            region = ConfidenceRegion.GLOBAL,
            debugLogger = null
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        // Initial fetch
        confidence.fetchAndActivate()
        advanceUntilIdle()
        storedContexts.clear() // Reset — we only care about stores after the barrage

        // Fire rapid context changes
        for (i in 1..totalChanges) {
            confidence.putContext(
                mapOf("targeting_key" to ConfidenceValue.String("user-$i"))
            )
        }
        advanceUntilIdle()

        val expectedLastContext = mapOf(
            "targeting_key" to ConfidenceValue.String("user-$totalChanges")
        )

        // Every context that was stored must be the LAST context — no intermediate leaks
        println("Contexts stored to disk after barrage: ${storedContexts.size}")
        for ((index, storedCtx) in storedContexts.withIndex()) {
            assertEquals(
                "Store call #$index should only contain the last context",
                expectedLastContext,
                storedCtx
            )
        }

        // Final disk state must be the last context
        val finalStored = realDiskStorage.read()
        if (finalStored != FlagResolution.EMPTY) {
            assertEquals(expectedLastContext, finalStored.context)
        }
    }

    /**
     * Verifies that when context changes rapidly, evaluations during the
     * storm never return values from intermediate contexts — only stale
     * (from before the storm) or the final resolved values.
     */
    @Test
    fun testEvaluationsDuringRapidChangesNeverReturnIntermediateValues() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val totalChanges = 10

        val flagResolver = object : FlagResolver {
            override suspend fun resolve(
                flags: List<String>,
                context: Map<String, ConfidenceValue>
            ): Result<FlagResolution> {
                val contextValue = (context["targeting_key"] as ConfidenceValue.String).string
                delay(100)
                return Result.Success(
                    FlagResolution(
                        context,
                        resolvedFlagsForContext(contextValue, 0),
                        "token"
                    )
                )
            }
        }

        val confidence = Confidence(
            clientSecret = "",
            dispatcher = testDispatcher,
            eventSenderEngine = mock(),
            initialContext = mapOf("targeting_key" to ConfidenceValue.String("user-initial")),
            cache = InMemoryCache(),
            flagResolver = flagResolver,
            flagApplierClient = flagApplierClient,
            diskStorage = FileDiskStorage.create(mockContext),
            region = ConfidenceRegion.GLOBAL,
            debugLogger = null
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        // Populate cache with initial context
        confidence.fetchAndActivate()
        advanceUntilIdle()

        val evalBefore = confidence.getFlag("test-flag.mystring", "default-fallback")
        assertEquals("value-for-user-initial", evalBefore.value)

        // Rapidly change contexts
        for (i in 1..totalChanges) {
            confidence.putContext(
                mapOf("targeting_key" to ConfidenceValue.String("user-$i"))
            )

            // Evaluate during the storm
            val evalDuring = confidence.getFlag("test-flag.mystring", "default-fallback")

            // Value must be from initial context (stale) — never from an intermediate resolve
            val intermediateValues = (1 until i).map { "value-for-user-$it" }.toSet()
            assertTrue(
                "During storm, got '${evalDuring.value}' which is from an intermediate context",
                evalDuring.value !in intermediateValues
            )
        }

        // Let everything settle
        advanceUntilIdle()

        // After storm, activate and check final state
        confidence.activate()
        val evalFinal = confidence.getFlag("test-flag.mystring", "default-fallback")

        val acceptableFinalValues = setOf(
            "value-for-user-$totalChanges",
            "value-for-user-initial"
        )
        assertTrue(
            "Final value '${evalFinal.value}' should be last context or initial stale",
            evalFinal.value in acceptableFinalValues
        )
    }
}
