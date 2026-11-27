package app.santals.speaks.data.remote.dto

// Wire formats for the Santali Speaks server (backend/src/server.mjs).
// Field names are part of the API contract. Every field is nullable so that a missing or
// unexpected field produces a clear app error instead of a crash.

data class TtsRequestDto(
    val text: String? = null,
    val language: String? = null,
)

data class TtsResponseDto(
    val audioBase64: String? = null,
    val mimeType: String? = null,
    val extension: String? = null,
    val samplingRate: Int? = null,
    val textLength: Int? = null,
)

data class ErrorEnvelopeDto(
    val error: ErrorBodyDto? = null,
)

data class ErrorBodyDto(
    val code: String? = null,
    val message: String? = null,
    val retryable: Boolean? = null,
)

data class StatusDto(
    val speechConfigured: Boolean? = null,
    val apiVersion: String? = null,
)
