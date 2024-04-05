package com.spotify.confidence.client

import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.client.serializers.ConfidenceValueSerializer
import com.spotify.confidence.client.serializers.DateTimeSerializer
import com.spotify.confidence.client.serializers.FlagsSerializer
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class AppliedFlag(
    val flag: String,
    @Serializable(DateTimeSerializer::class)
    val applyTime: Date
)

@Serializable
data class Sdk(
    val id: String,
    val version: String
)

@Serializable
data class ResolveFlags(
    val resolvedFlags: Flags,
    val resolveToken: String
)

@Serializable(FlagsSerializer::class)
data class Flags(
    val list: List<ResolvedFlag>
)

@Serializable
data class ResolvedFlag(
    val flag: String,
    val variant: String,
    val value: ConfidenceValueMap = mapOf(),
    val reason: ResolveReason
)

sealed interface SchemaType {
    data class SchemaStruct(
        var schema: Map<String, SchemaType>
    ) : SchemaType
    object IntSchema : SchemaType
    object DoubleSchema : SchemaType
    object StringSchema : SchemaType
    object BoolSchema : SchemaType
}

sealed class ResolveResponse {
    object NotModified : ResolveResponse()
    data class Resolved(val flags: ResolveFlags) : ResolveResponse()
}

data class SdkMetadata(val sdkId: String, val sdkVersion: String)

typealias ConfidenceValueMap =
    Map<String, @Serializable(ConfidenceValueSerializer::class) ConfidenceValue>