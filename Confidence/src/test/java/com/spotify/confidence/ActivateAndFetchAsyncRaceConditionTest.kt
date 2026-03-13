package com.spotify.confidence

import android.content.Context
import com.spotify.confidence.cache.FileDiskStorage
import com.spotify.confidence.client.ResolvedFlag
import junit.framework.TestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files

/**
 * Tests for context-aware flag resolution behaviour.
 *
 * Verifies that:
 * - Resolve responses for outdated contexts are discarded (not stored to disk)
 * - Flag evaluation returns stale values (not defaults) while a re-fetch is in flight
 * - FetchAndActivate correctly resolves and activates flags
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ActivateAndFetchAsyncRaceConditionTest {
    private val flagApplierClient: com.spotify.confidence.client.FlagApplierClient = mock()
    private val mockContext: Context = mock()

    private val resolvedValueAsMap = mutableMapOf(
        "mystring" to ConfidenceValue.String("resolved-server-value")
    )

    private val resolvedFlags = listOf(
        ResolvedFlag(
            "test-flag",
            "flags/test-flag/variants/variant-1",
            resolvedValueAsMap,
            ResolveReason.RESOLVE_REASON_MATCH,
            shouldApply = true
        )
    )

    @Before
    fun setup() {
        whenever(mockContext.filesDir).thenReturn(
            Files.createTempDirectory("tmpTests").toFile()
        )
    }

    private fun getConfidence(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        flagResolver: FlagResolver,
        cache: ProviderCache = InMemoryCache(),
        initialContext: Map<String, ConfidenceValue> = mapOf()
    ): Confidence = Confidence(
        clientSecret = "",
        dispatcher = dispatcher,
        eventSenderEngine = mock(),
        initialContext = initialContext,
        cache = cache,
        flagResolver = flagResolver,
        flagApplierClient = flagApplierClient,
        diskStorage = FileDiskStorage.create(mockContext),
        region = ConfidenceRegion.GLOBAL,
        debugLogger = null
    )

    @Test
    fun testFetchAndActivateDoesNotReturnDefaultValues() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context = mapOf("targeting_key" to ConfidenceValue.String("user123"))

        val flagResolver = object : FlagResolver {
            override suspend fun resolve(
                flags: List<String>,
                context: Map<String, ConfidenceValue>
            ): Result<FlagResolution> {
                return Result.Success(
                    FlagResolution(context, resolvedFlags, "token1")
                )
            }
        }

        val confidence = getConfidence(
            testDispatcher,
            flagResolver = flagResolver,
            initialContext = context
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        confidence.fetchAndActivate()
        advanceUntilIdle()

        val eval = confidence.getFlag("test-flag.mystring", "default-fallback")

        TestCase.assertEquals("resolved-server-value", eval.value)
        TestCase.assertEquals(ResolveReason.RESOLVE_REASON_MATCH, eval.reason)
        TestCase.assertNull(eval.errorCode)
    }

    /**
     * When the context changes while a fetch is in flight, evaluation should
     * return the previously cached value with a STALE reason rather than a
     * default value.
     */
    @Test
    fun testContextChangeTriggersRefetchAndEvaluationDuringFetchReturnsStale() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context1 = mapOf("targeting_key" to ConfidenceValue.String("user123"))
        val context2 = mapOf("targeting_key" to ConfidenceValue.String("user456"))

        var resolveCount = 0
        val secondResolveGate = CompletableDeferred<Unit>()

        val flagResolver = object : FlagResolver {
            override suspend fun resolve(
                flags: List<String>,
                context: Map<String, ConfidenceValue>
            ): Result<FlagResolution> {
                resolveCount++
                if (resolveCount >= 2) {
                    secondResolveGate.await()
                }
                return Result.Success(
                    FlagResolution(
                        context,
                        listOf(
                            ResolvedFlag(
                                "test-flag",
                                "flags/test-flag/variants/variant-$resolveCount",
                                mutableMapOf(
                                    "mystring" to ConfidenceValue.String("value-$resolveCount")
                                ),
                                ResolveReason.RESOLVE_REASON_MATCH,
                                shouldApply = true
                            )
                        ),
                        "token$resolveCount"
                    )
                )
            }
        }

        val confidence = getConfidence(
            testDispatcher,
            flagResolver = flagResolver,
            initialContext = context1
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        confidence.fetchAndActivate()
        advanceUntilIdle()

        val eval1 = confidence.getFlag("test-flag.mystring", "default")
        TestCase.assertEquals("value-1", eval1.value)

        // Context change triggers a background re-fetch; second resolve is blocked
        confidence.putContext(context2)

        val evalDuringFetch = confidence.getFlag("test-flag.mystring", "default")
        TestCase.assertEquals(
            "Should return stale value from previous resolve, not default",
            "value-1",
            evalDuringFetch.value
        )
        TestCase.assertEquals(
            "Reason should be STALE because context changed",
            ResolveReason.RESOLVE_REASON_STALE,
            evalDuringFetch.reason
        )

        secondResolveGate.complete(Unit)
        advanceUntilIdle()

        val evalAfterFetch = confidence.getFlag("test-flag.mystring", "default")
        TestCase.assertEquals("value-2", evalAfterFetch.value)
    }

    /**
     * When the evaluation context changes during an in-flight resolve request,
     * the response is stale and must NOT be stored to disk or activated into
     * cache. This prevents users from seeing flag values resolved for a
     * different context.
     */
    @Test
    fun testStaleResolveResponseDiscardedWhenContextChanged() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val context1 = mapOf("targeting_key" to ConfidenceValue.String("user-old"))
        val context2 = mapOf("targeting_key" to ConfidenceValue.String("user-new"))

        var shouldMutateContext: ((Map<String, ConfidenceValue>) -> Unit)? = null

        val flagResolver = object : FlagResolver {
            override suspend fun resolve(
                flags: List<String>,
                context: Map<String, ConfidenceValue>
            ): Result<FlagResolution> {
                val resolution = FlagResolution(
                    context,
                    listOf(
                        ResolvedFlag(
                            "test-flag",
                            "flags/test-flag/variants/variant-1",
                            mutableMapOf(
                                "mystring" to ConfidenceValue.String(
                                    "value-for-${context["targeting_key"]}"
                                )
                            ),
                            ResolveReason.RESOLVE_REASON_MATCH,
                            shouldApply = true
                        )
                    ),
                    "token1"
                )
                // Simulate context changing just as the response arrives
                shouldMutateContext?.invoke(context)
                shouldMutateContext = null
                return Result.Success(resolution)
            }
        }

        val diskStorage = FileDiskStorage.create(mockContext)
        val confidence = Confidence(
            clientSecret = "",
            dispatcher = testDispatcher,
            eventSenderEngine = mock(),
            initialContext = context1,
            cache = InMemoryCache(),
            flagResolver = flagResolver,
            flagApplierClient = flagApplierClient,
            diskStorage = diskStorage,
            region = ConfidenceRegion.GLOBAL,
            debugLogger = null
        )
        whenever(flagApplierClient.apply(any(), any())).thenReturn(Result.Success(Unit))

        // When the first resolve returns, change context before store()
        shouldMutateContext = {
            confidence.putContextLocal(context2)
        }

        confidence.fetchAndActivate()
        advanceUntilIdle()

        val storedResolution = diskStorage.read()

        TestCase.assertEquals(
            "Stale response for old context should not be stored on disk",
            FlagResolution.EMPTY,
            storedResolution
        )
    }
}
