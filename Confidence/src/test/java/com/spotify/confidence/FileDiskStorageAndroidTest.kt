package com.spotify.confidence

import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.apply.EventStatus
import com.spotify.confidence.cache.FileDiskStorage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 26], manifest = Config.NONE)
class FileDiskStorageAndroidTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replacesExistingFlagsFile() {
        val flagsFile = temporaryFolder.newFile("flags.json")
        val storage = FileDiskStorage(
            flagsFile,
            File(temporaryFolder.root, "apply.json"),
            File(temporaryFolder.root, "metadata.json")
        )
        val original = FlagResolution(mapOf("value" to ConfidenceValue.String("original")), emptyList(), "old-token")
        val replacement = FlagResolution(mapOf("value" to ConfidenceValue.String("replacement")), emptyList(), "new-token")
        storage.store(original)
        assertEquals(original, storage.read())
        assertTrue(flagsFile.exists())

        storage.store(replacement)

        assertEquals(replacement, Json.decodeFromString<FlagResolution>(flagsFile.readText()))
        assertEquals(replacement, storage.read())
        assertFalse(File(temporaryFolder.root, "flags.json.tmp").exists())
    }

    @Test
    fun replacesExistingApplyFile() {
        val applyFile = temporaryFolder.newFile("apply.json")
        val storage = FileDiskStorage(
            File(temporaryFolder.root, "flags.json"),
            applyFile,
            File(temporaryFolder.root, "metadata.json")
        )
        val original = mapOf("old-token" to mutableMapOf("flag" to ApplyInstance(Date(1000), EventStatus.CREATED)))
        val replacement = mapOf("new-token" to mutableMapOf("flag" to ApplyInstance(Date(2000), EventStatus.SENT)))
        storage.writeApplyData(original)
        assertEquals(original, storage.readApplyData())
        assertTrue(applyFile.exists())

        storage.writeApplyData(replacement)

        assertEquals(replacement, Json.decodeFromString<Map<String, MutableMap<String, ApplyInstance>>>(applyFile.readText()))
        assertEquals(replacement, storage.readApplyData())
        assertFalse(File(temporaryFolder.root, "apply.json.tmp").exists())
    }
}
