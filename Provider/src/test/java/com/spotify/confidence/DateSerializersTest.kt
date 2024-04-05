package com.spotify.confidence

import com.spotify.confidence.client.serializers.DateSerializer
import com.spotify.confidence.client.serializers.DateTimeSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.regex.Pattern

class DateSerializersTest {
    @Test
    fun date_serializer_works_with_day_format() {
        val now = Date()
        val nowLocalDate = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val pattern = "^\\d{4}-\\d{2}-\\d{2}\$"
        val regex = Pattern.compile(pattern)
        val serializedDate = Json.encodeToString(DateSerializer, now)
        Assert.assertTrue(regex.matcher(serializedDate.removeSurrounding("\"")).matches())
        val deserializedDate = Json.decodeFromString(DateSerializer, serializedDate)
        val localDate = deserializedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        Assert.assertTrue(localDate == nowLocalDate)
    }

    @Test
    fun date_time_serializer_works_with_timestamp_format() {
        val pattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?(Z|[+-]\\d{2}:\\d{2})?\$"
        val regex = Pattern.compile(pattern)
        val now = Date()
        val serializedDate = Json.encodeToString(DateTimeSerializer, now)
        Assert.assertTrue(regex.matcher(serializedDate.removeSurrounding("\"")).matches())
        val deserializedDate = Json.decodeFromString(DateTimeSerializer, serializedDate)
        Assert.assertTrue(deserializedDate == now)
    }

    @Test
    fun date_serializer_works_with_day_format_specific_date_with_timezone() {
        val todayDate = LocalDate.of(2024, 4, 4)
        val now = Date.from(todayDate.atStartOfDay(ZoneId.of("UTC")).toInstant())
        val nowLocalDate = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val serializedDate = Json.encodeToString(DateSerializer, now)
        Assert.assertTrue(serializedDate.removeSurrounding("\"") == "2024-04-04")
        val deserializedDate = Json.decodeFromString(DateSerializer, serializedDate)
        val localDate = deserializedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        Assert.assertTrue(localDate == nowLocalDate)
    }

    @Test
    fun date_time_serializer_works_with_timestamp_format_specific_date_with_timezone() {
        val todayDate = LocalDate.of(2024, 4, 4)
        val now = Date.from(todayDate.atTime(20, 20, 20).toInstant(ZoneOffset.UTC))
        val serializedDate = Json.encodeToString(DateTimeSerializer, now)
        Assert.assertTrue(serializedDate.removeSurrounding("\"") == "2024-04-04T20:20:20.000Z")
        val deserializedDate = Json.decodeFromString(DateTimeSerializer, serializedDate)
        Assert.assertTrue(deserializedDate == now)
    }
}