package com.spotify.confidence.cache

import android.content.Context
import com.spotify.confidence.client.ResolvedFlag
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.Value
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

const val FLAGS_FILE_NAME = "confidence_flags_cache.json"

class StorageFileCache private constructor(private val file: File) : DiskStorage {

    override fun store(
        resolvedFlags: List<ResolvedFlag>,
        resolveToken: String,
        evaluationContext: EvaluationContext
    ): CacheData {
        val data = toCacheData(
            resolvedFlags,
            resolveToken,
            evaluationContext
        )

        write(Json.encodeToString(data))
        return data
    }

    override fun clear() {
        file.delete()
    }

    private fun write(data: String) {
        file.writeText(data)
    }

    override fun read(): CacheData? {
        if (!file.exists()) return null
        val fileText: String = file.bufferedReader().use { it.readText() }
        if (fileText.isEmpty()) return null
        return Json.decodeFromString(fileText)
    }

    companion object {
        fun create(context: Context): DiskStorage {
            return StorageFileCache(File(context.filesDir, FLAGS_FILE_NAME))
        }

        /**
         * Testing purposes only!
         */
        fun forFile(file: File): DiskStorage {
            return StorageFileCache(file)
        }
    }
}

internal fun toCacheData(
    resolvedFlags: List<ResolvedFlag>,
    resolveToken: String,
    evaluationContext: EvaluationContext
) = CacheData(
    values = resolvedFlags.associate {
        it.flag to ProviderCache.CacheEntry(
            it.variant,
            Value.Structure(it.value.asMap()),
            it.reason
        )
    },
    evaluationContextHash = evaluationContext.hashCode(),
    resolveToken = resolveToken
)

@Serializable
data class CacheData(
    val resolveToken: String,
    val evaluationContextHash: Int,
    val values: Map<String, ProviderCache.CacheEntry>
)