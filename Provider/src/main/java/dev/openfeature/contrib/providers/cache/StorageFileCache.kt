package dev.openfeature.contrib.providers.cache

import android.content.Context
import dev.openfeature.contrib.providers.client.ResolvedFlag
import dev.openfeature.sdk.EvaluationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
<<<<<<< HEAD
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val FLAGS_FILE_NAME = "confidence_flags_cache.json"

class StorageFileCache private constructor(context: Context) : InMemoryCache() {
    private val file: File = File(context.filesDir, FLAGS_FILE_NAME)

    override fun refresh(
        resolvedFlags: List<ResolvedFlag>,
        resolveToken: String,
        evaluationContext: EvaluationContext
    ) {
=======

const val CACHE_FILE_NAME = "confidence_cache.json"

class StorageFileCache(context: Context) : InMemoryCache() {
    private val file: File = File(context.filesDir, CACHE_FILE_NAME)

    init {
        readFile()
    }

    override fun refresh(resolvedFlags: List<ResolvedFlag>, resolveToken: String, evaluationContext: EvaluationContext) {
>>>>>>> 43375cb (Transfer codebase)
        super.refresh(resolvedFlags, resolveToken, evaluationContext)
        // TODO Should this happen before in-memory cache is changed?
        writeToFile()
    }

    override fun clear() {
        super.clear()
        // TODO Should this happen before in-memory cache is changed?
        file.delete()
    }

    private fun writeToFile() {
        val fileData = Json.encodeToString(data)
        file.writeText(fileData)
    }

    private fun readFile() {
        if (!file.exists()) return
        val fileText: String = file.bufferedReader().use { it.readText() }
        if (fileText.isEmpty()) return
        data = Json.decodeFromString(CacheData.serializer(), fileText)
    }
<<<<<<< HEAD

    companion object {
        suspend fun create(context: Context): StorageFileCache = suspendCoroutine {
            val storage = StorageFileCache(context)
            storage.readFile()
            it.resume(storage)
        }
    }
=======
>>>>>>> 43375cb (Transfer codebase)
}