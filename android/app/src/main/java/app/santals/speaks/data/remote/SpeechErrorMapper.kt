package app.santals.speaks.data.remote

import app.santals.speaks.domain.model.SpeechFailureKind

/**
 * Maps the Santali Speaks server's error codes (see backend/src/server.mjs) and HTTP statuses to
 * app-level failure kinds. Kept free of Android types so it can be unit tested on a plain JVM.
 */
object SpeechErrorMapper {

    fun fromServerCode(code: String?, httpStatus: Int): SpeechFailureKind {
        return when (code) {
            "SERVICE_NOT_CONFIGURED" -> SpeechFailureKind.SERVICE_NOT_CONFIGURED
            "SERVICE_UNAVAILABLE" -> SpeechFailureKind.SERVICE_UNAVAILABLE
            "TIMEOUT" -> SpeechFailureKind.TIMEOUT
            "RATE_LIMITED" -> SpeechFailureKind.RATE_LIMITED
            "MODEL_UNAVAILABLE" -> SpeechFailureKind.MODEL_UNAVAILABLE
            "AUTHENTICATION_FAILED" -> SpeechFailureKind.AUTHENTICATION_FAILED
            "UNSUPPORTED_INPUT" -> SpeechFailureKind.UNSUPPORTED_INPUT
            "INVALID_TEXT" -> SpeechFailureKind.INVALID_TEXT
            "PAYLOAD_TOO_LARGE" -> SpeechFailureKind.TEXT_TOO_LONG
            "UNSUPPORTED_LANGUAGE" -> SpeechFailureKind.UNSUPPORTED_LANGUAGE
            "UNSUPPORTED_AUDIO_FORMAT" -> SpeechFailureKind.UNSUPPORTED_AUDIO_FORMAT
            "GENERATION_FAILED" -> SpeechFailureKind.GENERATION_FAILED
            "BAD_RESPONSE" -> SpeechFailureKind.BAD_RESPONSE
            "INTERNAL_ERROR" -> SpeechFailureKind.SERVICE_UNAVAILABLE
            else -> fromHttpStatus(httpStatus)
        }
    }

    /** Fallback when the body is missing or unreadable. */
    fun fromHttpStatus(status: Int): SpeechFailureKind = when (status) {
        400 -> SpeechFailureKind.INVALID_TEXT
        401, 403 -> SpeechFailureKind.AUTHENTICATION_FAILED
        413 -> SpeechFailureKind.TEXT_TOO_LONG
        415 -> SpeechFailureKind.BAD_RESPONSE
        422 -> SpeechFailureKind.UNSUPPORTED_INPUT
        429 -> SpeechFailureKind.RATE_LIMITED
        in 500..599 -> SpeechFailureKind.SERVICE_UNAVAILABLE
        else -> SpeechFailureKind.UNKNOWN
    }
}
