package com.spotify.confidence.cache

import android.content.Context
import com.spotify.confidence.FlagResolution
import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.client.serializers.ConfidenceValueSerializer
import com.spotify.confidence.client.serializers.UUIDSerializer
import dev.openfeature.sdk.DateSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.File

internal const val FLAGS_FILE_NAME = "confidence_flags_cache.json"
internal const val APPLY_FILE_NAME = "confidence_apply_cache.json"

internal class FileDiskStorage private constructor(
    private val flagsFile: File,
    private val applyFile: File
) : DiskStorage {

    override fun store(flagResolution: FlagResolution) {
        write(json.encodeToString(flagResolution))
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

    override fun read(): FlagResolution? {
        if (!flagsFile.exists()) return null
        val fileText: String = flagsFile.bufferedReader().use { it.readText() }
        return if (fileText.isEmpty()) {
            null
        } else {
            json.decodeFromString(fileText)
        }
    }

    companion object {
        fun create(context: Context): DiskStorage {
            return FileDiskStorage(
                flagsFile = File(context.filesDir, FLAGS_FILE_NAME),
                applyFile = File(context.filesDir, APPLY_FILE_NAME)
            )
        }

        /**
         * Testing purposes only!
         */
        fun forFiles(flagsFile: File, applyFile: File): DiskStorage {
            return FileDiskStorage(flagsFile, applyFile)
        }
    }
}

internal val json = Json {
    serializersModule = SerializersModule {
        contextual(UUIDSerializer)
        contextual(DateSerializer)
        contextual(ConfidenceValueSerializer)
    }
}