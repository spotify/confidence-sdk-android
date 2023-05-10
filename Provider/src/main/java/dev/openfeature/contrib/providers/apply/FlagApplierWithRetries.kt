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
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

const val APPLY_FILE_NAME = "confidence_apply_cache.json"

class FlagApplierWithRetries(
    private val client: ConfidenceClient,
    private val applyDispatcher: CoroutineDispatcher,
    context: Context) : FlagApplier {
    // <tokenString, <flagName, <uuid, applyTime>>>
    private var data : ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<UUID, Instant>>> = ConcurrentHashMap()
    private val file: File = File(context.filesDir, APPLY_FILE_NAME)
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    init {
        readFile()
    }

    // TODO Ensure this fun is not called on a main thread
    override fun apply(flagName: String, resolveToken: String) {
        data.putIfAbsent(resolveToken, ConcurrentHashMap())
        data[resolveToken]?.putIfAbsent(flagName, ConcurrentHashMap())
        // Never delete entries from the maps above, only add. This should prevent racy conditions
        // Empty entries are not added to the cache file, so empty entries are removed when restarting
        data[resolveToken]?.get(flagName)?.putIfAbsent(UUID.randomUUID(), Instant.now())
        writeToFile()
        try {
            triggerBatch()
        } catch (_: Throwable) {
            // "triggerBatch" should not introduce bad state in case of any failure, will retry later
        }
    }

    data class ApplyCacheData(
        var events: MutableMap<String, MutableMap<String, MutableList<Instant>>> = mutableMapOf()
    )

    // TODO Define the logic on when / how often to call this function
    // This function should never introduce bad state and any type of error is recoverable on the next try
    fun triggerBatch() {
        data.entries.forEach { (token, flagsForToken) ->
            val appliedFlagsKeyed = flagsForToken.entries.flatMap { (flagName, events) ->
                events.entries.map { (uuid, time) ->
                    Pair(uuid, AppliedFlag(flagName, time)) }
            }
            val handler = CoroutineExceptionHandler { _, _ ->
                // "triggerBatch" should not introduce bad state in case of any failure, will retry later
            }
            CoroutineScope(applyDispatcher).launch(handler) {
                // TODO Check size limit, cap max amount of flags per request
                client.apply(appliedFlagsKeyed.map { it.second }, token)
                appliedFlagsKeyed.forEach {
                    data[token]?.get(it.second.flag)?.remove(it.first)
                }
                writeToFile()
            }
        }
    }

    private fun writeToFile() {
        val fileData = gson.toJson(
            data.filter {
                // All flags entries are empty for this token, don't add this token to the file
                !it.value.values.stream().allMatch { events -> events.isEmpty() }
            }
        )
        file.writeText(fileData)
    }

    private fun readFile() {
        if (!file.exists()) return
        val fileText: String = file.bufferedReader().use { it.readText() }
        if (fileText.isEmpty()) return
        val type = object : TypeToken<ApplyCacheData>() {}.type
        data = gson.fromJson(fileText, type)
    }
}