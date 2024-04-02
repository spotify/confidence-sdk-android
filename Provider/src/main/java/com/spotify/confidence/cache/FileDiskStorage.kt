package com.spotify.confidence.cache

import android.content.Context
import com.spotify.confidence.FlagResolution
import com.spotify.confidence.apply.ApplyInstance
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal const val FLAGS_FILE_NAME = "confidence_flags_cache.json"
internal const val APPLY_FILE_NAME = "confidence_apply_cache.json"

internal class FileDiskStorage private constructor(
    private val flagsFile: File,
    private val applyFile: File
) : DiskStorage {

    override fun store(flagResolution: FlagResolution) {
        write(Json.encodeToString(flagResolution))
    }

    override fun clear() {
        flagsFile.delete()
    }

    override fun writeApplyData(applyData: Map<String, MutableMap<String, ApplyInstance>>) {
        applyFile.writeText(Json.encodeToString(applyData))
    }

    override fun readApplyData(): MutableMap<String, MutableMap<String, ApplyInstance>> {
        if (!applyFile.exists()) return mutableMapOf()
        val fileText: String = applyFile.bufferedReader().use { it.readText() }
        return if (fileText.isEmpty()) {
            mutableMapOf()
        } else {
            Json.decodeFromString(fileText)
        }
    }

    private fun write(data: String) {
        flagsFile.writeText(data)
    }

    override fun read(): FlagResolution {
        if (!flagsFile.exists()) return FlagResolution()
        val fileText: String = flagsFile.bufferedReader().use { it.readText() }
        return if (fileText.isEmpty()) {
            FlagResolution()
        } else {
            Json.decodeFromString(fileText)
        }
    }

    companion object {
        fun create(context: Context): DiskStorage {
            return FileDiskStorage(
                flagsFile = File(context.filesDir, FLAGS_FILE_NAME),
                applyFile = File(context.filesDir, APPLY_FILE_NAME)
            )
        }
    }
}