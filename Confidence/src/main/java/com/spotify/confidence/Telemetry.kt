package com.spotify.confidence

import android.util.Base64
import com.spotify.confidence.client.Sdk
import java.io.ByteArrayOutputStream

internal class Telemetry(
    private val sdkId: String,
    @Volatile var library: Library,
    private val sdkVersion: String
) {
    private val lock = Any()
    private val evaluationTraces = mutableListOf<EvaluationTrace>()
    private val resolveLatencyTraces = mutableListOf<ResolveLatencyTrace>()

    val sdk: Sdk = Sdk(sdkId, sdkVersion)

    fun trackEvaluation(reason: EvaluationReason, errorCode: EvaluationErrorCode) {
        synchronized(lock) {
            if (evaluationTraces.size < MAX_TRACES) {
                evaluationTraces.add(EvaluationTrace(reason, errorCode))
            }
        }
    }

    fun trackResolveLatency(durationMs: Long, status: RequestStatus) {
        synchronized(lock) {
            if (resolveLatencyTraces.size < MAX_TRACES) {
                resolveLatencyTraces.add(ResolveLatencyTrace(durationMs, status))
            }
        }
    }

    fun encodedHeaderValue(): String {
        val snapshot = synchronized(lock) {
            val s = Snapshot(
                evaluations = evaluationTraces.toList(),
                resolveTraces = resolveLatencyTraces.toList(),
                library = library
            )
            evaluationTraces.clear()
            resolveLatencyTraces.clear()
            s
        }

        val bytes = encodeMonitoring(snapshot)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private data class Snapshot(
        val evaluations: List<EvaluationTrace>,
        val resolveTraces: List<ResolveLatencyTrace>,
        val library: Library
    )

    private fun encodeMonitoring(snapshot: Snapshot): ByteArray {
        val out = ByteArrayOutputStream()

        val libraryTracesBytes = encodeLibraryTraces(snapshot)
        out.writeTag(1, WIRE_TYPE_LENGTH_DELIMITED)
        out.writeVarint(libraryTracesBytes.size.toLong())
        out.write(libraryTracesBytes)

        if (Platform.KOTLIN.value != 0) {
            out.writeTag(2, WIRE_TYPE_VARINT)
            out.writeVarint(Platform.KOTLIN.value.toLong())
        }

        return out.toByteArray()
    }

    private fun encodeLibraryTraces(snapshot: Snapshot): ByteArray {
        val out = ByteArrayOutputStream()

        if (snapshot.library.value != 0) {
            out.writeTag(1, WIRE_TYPE_VARINT)
            out.writeVarint(snapshot.library.value.toLong())
        }

        val versionBytes = sdkVersion.toByteArray(Charsets.UTF_8)
        out.writeTag(2, WIRE_TYPE_LENGTH_DELIMITED)
        out.writeVarint(versionBytes.size.toLong())
        out.write(versionBytes)

        for (resolve in snapshot.resolveTraces) {
            val traceBytes = encodeResolveLatencyTrace(resolve)
            out.writeTag(3, WIRE_TYPE_LENGTH_DELIMITED)
            out.writeVarint(traceBytes.size.toLong())
            out.write(traceBytes)
        }
        for (eval in snapshot.evaluations) {
            val traceBytes = encodeEvaluationTrace(eval)
            out.writeTag(3, WIRE_TYPE_LENGTH_DELIMITED)
            out.writeVarint(traceBytes.size.toLong())
            out.write(traceBytes)
        }

        return out.toByteArray()
    }

    private fun encodeResolveLatencyTrace(trace: ResolveLatencyTrace): ByteArray {
        val out = ByteArrayOutputStream()

        out.writeTag(1, WIRE_TYPE_VARINT)
        out.writeVarint(TraceId.RESOLVE_LATENCY.value.toLong())

        val requestTraceBytes = encodeRequestTrace(trace)
        out.writeTag(3, WIRE_TYPE_LENGTH_DELIMITED)
        out.writeVarint(requestTraceBytes.size.toLong())
        out.write(requestTraceBytes)

        return out.toByteArray()
    }

    private fun encodeRequestTrace(trace: ResolveLatencyTrace): ByteArray {
        val out = ByteArrayOutputStream()

        if (trace.durationMs != 0L) {
            out.writeTag(1, WIRE_TYPE_VARINT)
            out.writeVarint(trace.durationMs)
        }

        if (trace.status.value != 0) {
            out.writeTag(2, WIRE_TYPE_VARINT)
            out.writeVarint(trace.status.value.toLong())
        }

        return out.toByteArray()
    }

    private fun encodeEvaluationTrace(trace: EvaluationTrace): ByteArray {
        val out = ByteArrayOutputStream()

        out.writeTag(1, WIRE_TYPE_VARINT)
        out.writeVarint(TraceId.FLAG_EVALUATION.value.toLong())

        val evalTraceBytes = encodeEvalTraceBody(trace)
        if (evalTraceBytes.isNotEmpty()) {
            out.writeTag(5, WIRE_TYPE_LENGTH_DELIMITED)
            out.writeVarint(evalTraceBytes.size.toLong())
            out.write(evalTraceBytes)
        }

        return out.toByteArray()
    }

    private fun encodeEvalTraceBody(trace: EvaluationTrace): ByteArray {
        val out = ByteArrayOutputStream()

        if (trace.reason.value != 0) {
            out.writeTag(1, WIRE_TYPE_VARINT)
            out.writeVarint(trace.reason.value.toLong())
        }

        if (trace.errorCode.value != 0) {
            out.writeTag(2, WIRE_TYPE_VARINT)
            out.writeVarint(trace.errorCode.value.toLong())
        }

        return out.toByteArray()
    }

    companion object {
        const val HEADER_NAME = "X-CONFIDENCE-TELEMETRY"
        private const val MAX_TRACES = 100

        private const val WIRE_TYPE_VARINT = 0
        private const val WIRE_TYPE_LENGTH_DELIMITED = 2

        fun mapEvaluationReason(
            reason: ResolveReason,
            errorCode: ConfidenceError.ErrorCode?
        ): Pair<EvaluationReason, EvaluationErrorCode> {
            if (errorCode != null) {
                return when (errorCode) {
                    ConfidenceError.ErrorCode.FLAG_NOT_FOUND ->
                        Pair(EvaluationReason.ERROR, EvaluationErrorCode.FLAG_NOT_FOUND)
                    ConfidenceError.ErrorCode.PARSE_ERROR ->
                        Pair(EvaluationReason.ERROR, EvaluationErrorCode.PARSE_ERROR)
                    ConfidenceError.ErrorCode.INVALID_CONTEXT ->
                        Pair(EvaluationReason.ERROR, EvaluationErrorCode.INVALID_CONTEXT)
                    ConfidenceError.ErrorCode.PROVIDER_NOT_READY ->
                        Pair(EvaluationReason.ERROR, EvaluationErrorCode.PROVIDER_NOT_READY)
                    else ->
                        Pair(EvaluationReason.ERROR, EvaluationErrorCode.GENERAL)
                }
            }
            return when (reason) {
                ResolveReason.RESOLVE_REASON_MATCH ->
                    Pair(EvaluationReason.TARGETING_MATCH, EvaluationErrorCode.UNSPECIFIED)
                ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH ->
                    Pair(EvaluationReason.DEFAULT, EvaluationErrorCode.UNSPECIFIED)
                ResolveReason.RESOLVE_REASON_NO_TREATMENT_MATCH ->
                    Pair(EvaluationReason.DEFAULT, EvaluationErrorCode.UNSPECIFIED)
                ResolveReason.RESOLVE_REASON_STALE ->
                    Pair(EvaluationReason.STALE, EvaluationErrorCode.UNSPECIFIED)
                ResolveReason.RESOLVE_REASON_FLAG_ARCHIVED ->
                    Pair(EvaluationReason.DISABLED, EvaluationErrorCode.UNSPECIFIED)
                ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR ->
                    Pair(EvaluationReason.ERROR, EvaluationErrorCode.TARGETING_KEY_MISSING)
                ResolveReason.ERROR ->
                    Pair(EvaluationReason.ERROR, EvaluationErrorCode.GENERAL)
                else ->
                    Pair(EvaluationReason.UNSPECIFIED, EvaluationErrorCode.UNSPECIFIED)
            }
        }

        private fun ByteArrayOutputStream.writeTag(fieldNumber: Int, wireType: Int) {
            writeVarint(((fieldNumber shl 3) or wireType).toLong())
        }

        private fun ByteArrayOutputStream.writeVarint(value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            write(v.toInt())
        }
    }

    enum class Platform(val value: Int) {
        KOTLIN(2)
    }

    enum class Library(val value: Int) {
        CONFIDENCE(1),
        OPEN_FEATURE(2)
    }

    enum class TraceId(val value: Int) {
        RESOLVE_LATENCY(1),
        FLAG_EVALUATION(3)
    }

    enum class RequestStatus(val value: Int) {
        UNSPECIFIED(0),
        SUCCESS(1),
        ERROR(2),
        TIMEOUT(3)
    }

    enum class EvaluationReason(val value: Int) {
        UNSPECIFIED(0),
        TARGETING_MATCH(1),
        DEFAULT(2),
        STALE(3),
        DISABLED(4),
        CACHED(5),
        STATIC(6),
        SPLIT(7),
        ERROR(8)
    }

    enum class EvaluationErrorCode(val value: Int) {
        UNSPECIFIED(0),
        PROVIDER_NOT_READY(1),
        FLAG_NOT_FOUND(2),
        PARSE_ERROR(3),
        TYPE_MISMATCH(4),
        TARGETING_KEY_MISSING(5),
        INVALID_CONTEXT(6),
        PROVIDER_FATAL(7),
        GENERAL(8)
    }

    private data class EvaluationTrace(
        val reason: EvaluationReason,
        val errorCode: EvaluationErrorCode
    )

    private data class ResolveLatencyTrace(
        val durationMs: Long,
        val status: RequestStatus
    )
}
