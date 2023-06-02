package dev.openfeature.contrib.providers.apply

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.InstantTypeAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

const val APPLY_FILE_NAME = "confidence_apply_cache.json"

data class WriteRequest(
    val flagName: String,
    val resolveToken: String
)

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val dispatcher: CoroutineDispatcher,
    context: Context
) : FlagApplier {
    private val coroutineScope: CoroutineScope by lazy {
        CoroutineScope(dispatcher)
    }
    private val writeRequestChannel: Channel<WriteRequest> = Channel()
    private val triggerBatchChannel: Channel<Unit> = Channel()
    private val file: File = File(context.filesDir, APPLY_FILE_NAME)
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    init {
        coroutineScope.launch {
            // the data being in a coroutine like this
            // ensures we don't have any shared mutability
            val data : ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>> =
                ConcurrentHashMap()
            readFile(data)
            select {
                writeRequestChannel.onReceive {writeRequest ->
                    internalApply(writeRequest.flagName, writeRequest.resolveToken, data)
                }

                triggerBatchChannel.onReceive {
                    internalTriggerBatch(data)
                }
            }
        }
    }

    override fun apply(flagName: String, resolveToken: String) {
        coroutineScope.launch {
            writeRequestChannel.send(WriteRequest(flagName, resolveToken))
        }
    }

    private suspend fun internalApply(
        flagName: String,
        resolveToken: String,
        data: ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>>
    ) = coroutineScope {
        data.putIfAbsent(resolveToken, ConcurrentHashMap())
        data[resolveToken]?.putIfAbsent(flagName, ConcurrentHashMap())
        // Never delete entries from the maps above, only add. This should prevent racy conditions
        // Empty entries are not added to the cache file, so empty entries are removed when restarting
        data[resolveToken]?.get(flagName)?.putIfAbsent(UUID.randomUUID(), Instant.now())
        writeToFile(data)
        try {
            internalTriggerBatch(data)
        } catch (_: Throwable) {
            // "triggerBatch" should not introduce bad state in case of any failure, will retry later
        }
    }

    // TODO Define the logic on when / how often to call this function
    // This function should never introduce bad state and any type of error is recoverable on the next try
    fun triggerBatch() {
        coroutineScope.launch {
            triggerBatchChannel.send(Unit)
        }
    }

    private suspend fun internalTriggerBatch(
        data: ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>>
    ) = coroutineScope {
        data.entries.forEach { (token, flagsForToken) ->
            val appliedFlagsKeyed = flagsForToken.entries.flatMap { (flagName, events) ->
                events.entries.map { (uuid, time) ->
                    Pair(uuid, AppliedFlag(flagName, time))
                }
            }
            // TODO chunk size 20 is an arbitrary value, replace with appropriate size
            appliedFlagsKeyed.chunked(20).forEach { appliedFlagsKeyedChunk ->
                    client.apply(appliedFlagsKeyedChunk.map { it.second }, token)
                    appliedFlagsKeyedChunk.forEach {
                        data[token]?.get(it.second.flag)?.remove(it.first)
                    }
                    writeToFile(data)
            }
        }
    }

    private suspend fun writeToFile(
        data: ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>>
    )  = coroutineScope {
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
        data: ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>>
    ) = coroutineScope {
            if (!file.exists()) return@coroutineScope
            val fileText: String = file.bufferedReader().use { it.readText() }
            if (fileText.isEmpty()) return@coroutineScope
            val type = object :
                TypeToken<ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>>>() {}.type
            val newData: ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>> =
                gson.fromJson(fileText, type)
            newData.entries.forEach { (resolveToken, eventsByFlagName) ->
                eventsByFlagName.entries.forEach { (flagName, eventTimeEntries) ->
                    data.putIfAbsent(resolveToken, ConcurrentHashMap())
                    data[resolveToken]?.putIfAbsent(flagName, ConcurrentHashMap())
                    eventTimeEntries.forEach { (id, time) ->
                        data[resolveToken]?.get(flagName)?.putIfAbsent(id, time)
                    }
                }
            }
        }
    }