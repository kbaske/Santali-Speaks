package app.santals.speaks.presentation.common

import androidx.annotation.StringRes
import app.santals.speaks.R
import app.santals.speaks.domain.model.SpeechFailureKind

/** Maps every failure kind to a user-facing message. Exhaustive, so a new kind cannot be missed. */
@StringRes
fun SpeechFailureKind.messageRes(): Int = when (this) {
    SpeechFailureKind.NO_NETWORK -> R.string.error_no_network
    SpeechFailureKind.TIMEOUT -> R.string.error_timeout
    SpeechFailureKind.SERVICE_UNAVAILABLE -> R.string.error_service_unavailable
    SpeechFailureKind.RATE_LIMITED -> R.string.error_rate_limited
    SpeechFailureKind.BAD_RESPONSE -> R.string.error_bad_response
    SpeechFailureKind.GENERATION_FAILED -> R.string.error_generation_failed
    SpeechFailureKind.SERVICE_NOT_CONFIGURED -> R.string.error_not_configured
    SpeechFailureKind.MODEL_UNAVAILABLE -> R.string.error_model_unavailable
    SpeechFailureKind.AUTHENTICATION_FAILED -> R.string.error_authentication
    SpeechFailureKind.UNSUPPORTED_INPUT -> R.string.error_unsupported_input
    SpeechFailureKind.INVALID_TEXT -> R.string.error_invalid_text
    SpeechFailureKind.TEXT_TOO_LONG -> R.string.error_text_too_long
    SpeechFailureKind.UNSUPPORTED_AUDIO_FORMAT -> R.string.error_unsupported_audio
    SpeechFailureKind.UNSUPPORTED_LANGUAGE -> R.string.error_unsupported_language
    SpeechFailureKind.STORAGE_FULL -> R.string.error_storage_full
    SpeechFailureKind.UNKNOWN -> R.string.error_unknown
}
