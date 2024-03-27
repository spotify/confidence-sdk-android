package com.spotify.confidence.client

import com.spotify.confidence.ConfidenceValue
import dev.openfeature.sdk.Structure
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class AppliedFlag(
    val flag: String,
    @Contextual
    val applyTime: Date
)

@Serializable
data class ResolveFlagsRequest(
    val flags: List<String>,
    @Contextual
    val evaluationContext: Structure,
    val clientSecret: String,
    val apply: Boolean,
    val sdk: Sdk
)

@Serializable
data class Sdk(
    val id: String,
    val version: String
)

@Serializable
data class ResolveFlags(
    @Contextual
    val resolvedFlags: Flags,
    val resolveToken: String
)

data class Flags(
    val list: List<ResolvedFlag>
)

data class ResolvedFlag(
    val flag: String,
    val variant: String,
    val value: ConfidenceValue.Struct = ConfidenceValue.Struct(mapOf()),
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