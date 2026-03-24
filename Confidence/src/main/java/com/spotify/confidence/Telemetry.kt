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

    fun encodedHeaderValue(): String? {
        val (evalSnapshot, resolveSnapshot) = synchronized(lock) {
            val evals = evaluationTraces.toList()
            val resolves = resolveLatencyTraces.toList()
            evaluationTraces.clear()
            resolveLatencyTraces.clear()
            Pair(evals, resolves)
        }

        if (evalSnapshot.isEmpty() && resolveSnapshot.isEmpty()) return null

        val bytes = encodeMonitoring(evalSnapshot, resolveSnapshot)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun encodeMonitoring(
        evals: List<EvaluationTrace>,
        resolves: List<ResolveLatencyTrace>
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // field 1: LibraryTraces (length-delimited)
        val libraryTracesBytes = encodeLibraryTraces(evals, resolves)
        out.writeTag(1, WIRE_TYPE_LENGTH_DELIMITED)
        out.writeVarint(libraryTracesBytes.size)
        out.write(libraryTracesBytes)

        // field 2: platform (varint) - KOTLIN = 2
        out.writeTag(2, WIRE_TYPE_VARINT)
        out.writeVarint(Platform.KOTLIN.value)

        return out.toByteArray()
    }

    private fun encodeLibraryTraces(
        evals: List<EvaluationTrace>,
        resolves: List<ResolveLatencyTrace>
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // field 1: library (varint)
        out.writeTag(1, WIRE_TYPE_VARINT)
        out.writeVarint(library.value)

        // field 2: library_version (string)
        out.writeTag(2, WIRE_TYPE_LENGTH_DELIMITED)
        val versionBytes = sdkVersion.toByteArray(Charsets.UTF_8)
        out.writeVarint(versionBytes.size)
        out.write(versionBytes)

        // field 3: traces (repeated)
        for (resolve in resolves) {
            val traceBytes = encodeResolveLatencyTrace(resolve)
            out.writeTag(3, WIRE_TYPE_LENGTH_DELIMITED)
            out.writeVarint(traceBytes.size)
            out.write(traceBytes)
        }
        for (eval in evals) {
            val traceBytes = encodeEvaluationTrace(eval)
            out.writeTag(3, WIRE_TYPE_LENGTH_DELIMITED)
            out.writeVarint(traceBytes.size)
            out.write(traceBytes)
        }

        return out.toByteArray()
    }

    private fun encodeResolveLatencyTrace(trace: ResolveLatencyTrace): ByteArray {
        val out = ByteArrayOutputStream()

        // field 1: id (varint) - RESOLVE_LATENCY = 1
        out.writeTag(1, WIRE_TYPE_VARINT)
        out.writeVarint(TraceId.RESOLVE_LATENCY.value)

        // field 3: request_trace (length-delimited) - oneof field 3
        val requestTraceBytes = encodeRequestTrace(trace)
        out.writeTag(3, WIRE_TYPE_LENGTH_DELIMITED)
        out.writeVarint(requestTraceBytes.size)
        out.write(requestTraceBytes)

        return out.toByteArray()
    }

    private fun encodeRequestTrace(trace: ResolveLatencyTrace): ByteArray {
        val out = ByteArrayOutputStream()

        // field 1: latency_ms (varint)
        out.writeTag(1, WIRE_TYPE_VARINT)
        out.writeVarint(trace.durationMs.toInt())

        // field 2: status (varint)
        out.writeTag(2, WIRE_TYPE_VARINT)
        out.writeVarint(trace.status.value)

        return out.toByteArray()
    }

    private fun encodeEvaluationTrace(trace: EvaluationTrace): ByteArray {
        val out = ByteArrayOutputStream()

        // field 1: id (varint) - FLAG_EVALUATION = 3
        out.writeTag(1, WIRE_TYPE_VARINT)
        out.writeVarint(TraceId.FLAG_EVALUATION.value)

        // field 5: evaluation_trace (length-delimited) - oneof field 5
        val evalTraceBytes = encodeEvalTraceBody(trace)
        out.writeTag(5, WIRE_TYPE_LENGTH_DELIMITED)
        out.writeVarint(evalTraceBytes.size)
        out.write(evalTraceBytes)

        return out.toByteArray()
    }

    private fun encodeEvalTraceBody(trace: EvaluationTrace): ByteArray {
        val out = ByteArrayOutputStream()

        // field 1: reason (varint)
        out.writeTag(1, WIRE_TYPE_VARINT)
        out.writeVarint(trace.reason.value)

        // field 2: error_code (varint)
        out.writeTag(2, WIRE_TYPE_VARINT)
        out.writeVarint(trace.errorCode.value)

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
            writeVarint((fieldNumber shl 3) or wireType)
        }

        private fun ByteArrayOutputStream.writeVarint(value: Int) {
            var v = value
            while (v and 0x7F.inv() != 0) {
                write((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            write(v)
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
        ERROR(8)
    }

    enum class EvaluationErrorCode(val value: Int) {
        UNSPECIFIED(0),
        PROVIDER_NOT_READY(1),
        FLAG_NOT_FOUND(2),
        PARSE_ERROR(3),
        TARGETING_KEY_MISSING(5),
        INVALID_CONTEXT(6),
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
