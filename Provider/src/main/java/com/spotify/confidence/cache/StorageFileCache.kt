package com.spotify.confidence.cache

import android.content.Context
import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.client.ResolvedFlag
import com.spotify.confidence.client.serializers.UUIDSerializer
import dev.openfeature.sdk.DateSerializer
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.Value
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.File

internal const val FLAGS_FILE_NAME = "confidence_flags_cache.json"
internal const val APPLY_FILE_NAME = "confidence_apply_cache.json"

internal class StorageFileCache private constructor(
    private val flagsFile: File,
    private val applyFile: File
) : DiskStorage {

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
        flagsFile.delete()
    }

    override fun writeApplyData(applyData: Map<String, MutableMap<String, ApplyInstance>>) {
        applyFile.writeText(json.encodeToString(applyData))
    }

    override fun readApplyData(): MutableMap<String, MutableMap<String, ApplyInstance>> {
        if (!applyFile.exists()) return mutableMapOf()
        val fileText: String = applyFile.bufferedReader().use { it.readText() }
        return if (fileText.isEmpty()) {
            mutableMapOf()
        } else {
            json.decodeFromString(fileText)
        }
    }

    private fun write(data: String) {
        flagsFile.writeText(data)
    }

    override fun read(): CacheData? {
        if (!flagsFile.exists()) return null
        val fileText: String = flagsFile.bufferedReader().use { it.readText() }
        return if (fileText.isEmpty()) {
            null
        } else {
            Json.decodeFromString(fileText)
        }
    }

    companion object {
        fun create(context: Context): DiskStorage {
            return StorageFileCache(
                flagsFile = File(context.filesDir, FLAGS_FILE_NAME),
                applyFile = File(context.filesDir, APPLY_FILE_NAME)
            )
        }

        /**
         * Testing purposes only!
         */
        fun forFiles(flagsFile: File, applyFile: File): DiskStorage {
            return StorageFileCache(flagsFile, applyFile)
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

internal val json = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(DateSerializer)
    }
}