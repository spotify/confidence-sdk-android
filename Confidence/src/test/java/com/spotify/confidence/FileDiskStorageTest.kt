package com.spotify.confidence

import com.spotify.confidence.apply.ApplyInstance
import com.spotify.confidence.apply.EventStatus
import com.spotify.confidence.cache.FileDiskStorage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Date

class FileDiskStorageTest {
    private lateinit var diskStorage: FileDiskStorage
    private lateinit var flagsFile: File
    private lateinit var applyFile: File

    val badFile = "{\"context\":{\"name\":\"emu64a\",\"version\":\"1.0\",\"density\":2.625,\"height\":1857.0,\"width\":1080,\"namespace\":\"com.example.confidencedemoapp\",\"build\":\"1\",\"manufacturer\":\"Google\",\"model\":\"sdk_gphone64_arm64\",\"type\":\"android\",\"targeting_key\":\"a98a4291-53b0-49d9-bae8-73d3f5da2070\"},\"flags\":[{\"flag\":\"hatten\",\"variant\":\"\",\"reason\":\"RESOLVE_REASON_NO_SEGMENT_MATCH\"}],\"resolveToken\":\"meh\"}"
    val badApply = "{\"apply\":{\"apply\":\"apply\"}"

    @Before
    fun setup() {
        flagsFile = Files.createTempFile("flags", ".txt").toFile()
        applyFile = Files.createTempFile("apply", ".txt").toFile()
        diskStorage = FileDiskStorage(flagsFile, applyFile)
    }

    @Test
    fun handleCrashingDeserializationForFlags() {
        flagsFile.writeText(badFile)
        val read = diskStorage.read()
        Assert.assertEquals(FlagResolution.EMPTY, read)
        Assert.assertFalse(flagsFile.exists())

        diskStorage.store(FlagResolution(mapOf("name" to ConfidenceValue.String("emu64a")), listOf(), "abcd"))
        Assert.assertTrue(flagsFile.exists())
    }

    @Test
    fun handleCrashingDeserializationForApplyData() {
        applyFile.writeText(badApply)
        val read = diskStorage.readApplyData()
        Assert.assertEquals(emptyMap<String, MutableMap<String, ApplyInstance>>(), read)

        diskStorage.writeApplyData(mutableMapOf("apply" to mutableMapOf("apply" to ApplyInstance(Date(), EventStatus.CREATED))))
        Assert.assertTrue(applyFile.exists())
    }

    @Test
    fun readEmptyFlagsFile() {
        val read = diskStorage.read()
        Assert.assertEquals(FlagResolution.EMPTY, read)
    }

    @Test
    fun readEmptyApplyFile() {
        val read = diskStorage.readApplyData()
        Assert.assertEquals(emptyMap<String, MutableMap<String, ApplyInstance>>(), read)
    }

    @Test
    fun testStoreFlagResolution() {
        diskStorage.store(FlagResolution.EMPTY)
        val read = diskStorage.read()
        Assert.assertEquals(FlagResolution.EMPTY, read)
    }
}