package dev.openfeature.contrib.providers.apply

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openfeature.contrib.providers.EventProcessor
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.InstantTypeAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

const val APPLY_FILE_NAME = "confidence_apply_cache.json"
data class FlagApplierInput(
    val resolveToken: String,
    val flagName: String
)

data class FlagApplierBatchProcessedInput(
    val resolveToken: String,
    val flags: List<Pair<UUID, AppliedFlag>>
)

private typealias FlagsAppliedMap =
    MutableMap<String, MutableMap<String, MutableMap<UUID, Instant>>>

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val dispatcher: CoroutineDispatcher,
    context: Context
) : FlagApplier {
    private val file: File = File(context.filesDir, APPLY_FILE_NAME)
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    private val eventProcessor by lazy {
        EventProcessor<
            FlagApplierInput,
            FlagApplierBatchProcessedInput,
            FlagsAppliedMap
            >(
            onInitialised = {
                val data: FlagsAppliedMap = mutableMapOf()
                readFile(data)
                data
            },
            onApply = { flagApplierInput, mutableData ->
                internalApply(
                    flagApplierInput.flagName,
                    flagApplierInput.resolveToken,
                    mutableData
                )
            },
            processBatchAction = { event, mutableData ->
                event.flags.forEach {
                    mutableData[event.resolveToken]?.get(it.second.flag)?.remove(it.first)
                }
                writeToFile(mutableData)
            },
            onProcessBatch = { mutableData, sendChannel, coroutineScope, coroutineExceptionHandler ->
                processBatch(
                    mutableData,
                    coroutineScope,
                    sendChannel,
                    coroutineExceptionHandler
                )
            },
            dispatcher = dispatcher
        )
    }

    init {
        eventProcessor.start()
    }

    override fun apply(flagName: String, resolveToken: String) {
        eventProcessor.apply(FlagApplierInput(resolveToken, flagName))
    }

    private fun internalApply(
        flagName: String,
        resolveToken: String,
        data: FlagsAppliedMap
    ) {
        data.putIfAbsent(resolveToken, hashMapOf())
        data[resolveToken]?.putIfAbsent(flagName, hashMapOf())
        // Never delete entries from the maps above, only add. This should prevent racy conditions
        // Empty entries are not added to the cache file, so empty entries are removed when restarting
        data[resolveToken]?.get(flagName)?.putIfAbsent(UUID.randomUUID(), Instant.now())
        writeToFile(data)
    }

    private fun processBatch(
        data: FlagsAppliedMap,
        coroutineScope: CoroutineScope,
        sendChannel: SendChannel<FlagApplierBatchProcessedInput>,
        coroutineExceptionHandler: CoroutineExceptionHandler
    ) {
        data.entries.forEach { (token, flagsForToken) ->
            val appliedFlagsKeyed = flagsForToken.entries.flatMap { (flagName, events) ->
                events.entries.map { (uuid, time) ->
                    Pair(uuid, AppliedFlag(flagName, time))
                }
            }
            // TODO chunk size 20 is an arbitrary value, replace with appropriate size
            appliedFlagsKeyed.chunked(CHUNK_SIZE).forEach { appliedFlagsKeyedChunk ->
                coroutineScope.launch(coroutineExceptionHandler) {
                    client.apply(appliedFlagsKeyedChunk.map { it.second }, token)
                    sendChannel.send(FlagApplierBatchProcessedInput(token, appliedFlagsKeyedChunk))
                }
            }
        }
    }

    private fun writeToFile(
        data: FlagsAppliedMap
    ) {
        val fileData = gson.toJson(
            data.filter {
                // All flags entries are empty for this token, don't add this token to the file
                !it.value.values.stream().allMatch { events -> events.isEmpty() }
            }
        )
        // TODO Add a limit for the file size?
        file.writeText(fileData)
    }

    private suspend fun readFile(
        data: FlagsAppliedMap
    ) = coroutineScope {
        if (!file.exists()) return@coroutineScope
        val fileText: String = file.bufferedReader().use { it.readText() }
        if (fileText.isEmpty()) return@coroutineScope
        val type = object :
            TypeToken<Map<String, Map<String, Map<UUID, Instant>>>>() {}.type
        val newData: Map<String, Map<String, Map<UUID, Instant>>> =
            gson.fromJson(fileText, type)
        newData.entries.forEach { (resolveToken, eventsByFlagName) ->
            eventsByFlagName.entries.forEach { (flagName, eventTimeEntries) ->
                data.putIfAbsent(resolveToken, hashMapOf())
                data[resolveToken]?.putIfAbsent(flagName, hashMapOf())
                eventTimeEntries.forEach { (id, time) ->
                    data[resolveToken]?.get(flagName)?.putIfAbsent(id, time)
                }
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 20
    }
}