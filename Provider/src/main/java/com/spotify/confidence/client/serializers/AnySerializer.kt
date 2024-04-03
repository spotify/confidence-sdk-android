package com.spotify.confidence.client.serializers

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.BooleanArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.CharArraySerializer
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.LongArraySerializer
import kotlinx.serialization.builtins.ShortArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object AnySerializer : KSerializer<Any> {
    override fun deserialize(decoder: Decoder): Any {
        // Stub function; should not be called.
        return "not-implemented"
    }

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        ContextualSerializer(Any::class, null, emptyArray()).descriptor

    override fun serialize(encoder: Encoder, value: Any) {
        val toJsonElement = value.toJsonElement()
        encoder.encodeSerializableValue(Json.serializersModule.serializer(), toJsonElement)
    }
}

/**
 * A pre-configured Json Implementation with an Any type serializer.
 */
val JsonAnySerializer = Json {
    serializersModule = SerializersModule {
        contextual(Any::class) { AnySerializer }
    }
}

fun Any.toJsonElement(): JsonElement {
    when (this) {
        is Map<*, *> -> {
            val value = this as Map<String, Any>
            return value.toJsonElement()
        }
        is Array<*> -> {
            val value = this as Array<Any>
            return value.toJsonElement()
        }
        is Collection<*> -> {
            val value = this as Collection<Any>
            return value.toJsonElement()
        }
        is Pair<*, *> -> {
            val value = this as Pair<Any, Any>
            return value.toJsonElement()
        }
        is Triple<*, *, *> -> {
            val value = this as Triple<Any, Any, Any>
            return value.toJsonElement()
        }
        is Map.Entry<*, *> -> {
            val value = this as Map.Entry<Any, Any>
            return value.toJsonElement()
        }
        else -> {
            serializerFor(this::class)?.let {
                return Json.encodeToJsonElement(it, this)
            }
        }
    }
    return JsonNull
}

inline fun <reified T : Any> serializerFor(value: KClass<out T>): KSerializer<T>? {
    val serializer = primitiveSerializers[value] ?: return null
    return serializer as KSerializer<T>
}

val primitiveSerializers = mapOf(
    String::class to String.serializer(),
    Char::class to Char.serializer(),
    CharArray::class to CharArraySerializer(),
    Double::class to Double.serializer(),
    DoubleArray::class to DoubleArraySerializer(),
    Float::class to Float.serializer(),
    FloatArray::class to FloatArraySerializer(),
    Long::class to Long.serializer(),
    LongArray::class to LongArraySerializer(),
    Int::class to Int.serializer(),
    IntArray::class to IntArraySerializer(),
    Short::class to Short.serializer(),
    ShortArray::class to ShortArraySerializer(),
    Byte::class to Byte.serializer(),
    ByteArray::class to ByteArraySerializer(),
    Boolean::class to Boolean.serializer(),
    BooleanArray::class to BooleanArraySerializer(),
    Unit::class to Unit.serializer(),
    UInt::class to UInt.serializer(),
    ULong::class to ULong.serializer(),
    UByte::class to UByte.serializer(),
    UShort::class to UShort.serializer()
)