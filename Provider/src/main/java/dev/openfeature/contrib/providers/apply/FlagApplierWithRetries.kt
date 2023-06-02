package dev.openfeature.contrib.providers.apply

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.openfeature.contrib.providers.client.AppliedFlag
import dev.openfeature.contrib.providers.client.ConfidenceClient
import dev.openfeature.contrib.providers.client.InstantTypeAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.File
import java.time.Instant
import java.util.*

const val APPLY_FILE_NAME = "confidence_apply_cache.json"

data class WriteRequest(
    val flagName: String,
    val resolveToken: String
)

private typealias FlagsAppliedMap =
    MutableMap<String, MutableMap<String, MutableMap<UUID, Instant>>>

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val dispatcher: CoroutineDispatcher,
    context: Context
) : FlagApplier {
    private val coroutineScope: CoroutineScope by lazy {
        CoroutineScope(dispatcher)
    }
    private val writeRequestChannel: Channel<WriteRequest> = Channel()
    private val file: File = File(context.filesDir, APPLY_FILE_NAME)
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    init {
        coroutineScope.launch {
            // the data being in a coroutine like this
            // ensures we don't have any shared mutability
            val data: FlagsAppliedMap = mutableMapOf()
            readFile(data)

            // the select clause ensures only one at the time
            // of this events can come true,
            // either the write request or trigger signal,
            // makes sure we get them one by one and fairly distributed
            // the thread will suspended until we are done
            for(writeRequest in writeRequestChannel) {
                internalApply(writeRequest.flagName, writeRequest.resolveToken, data)
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
        data: FlagsAppliedMap
    ) = coroutineScope {
        data.putIfAbsent(resolveToken, hashMapOf())
        data[resolveToken]?.putIfAbsent(flagName, hashMapOf())
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

    private suspend fun internalTriggerBatch(
        data: FlagsAppliedMap
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
        data: FlagsAppliedMap
    ) = coroutineScope {
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
}