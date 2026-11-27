package app.santals.speaks.domain.model

/** Categories of speech failure. The UI maps each kind to a localized, understandable message. */
enum class SpeechFailureKind(val retryable: Boolean) {
    NO_NETWORK(retryable = true),
    TIMEOUT(retryable = true),
    SERVICE_UNAVAILABLE(retryable = true),
    RATE_LIMITED(retryable = true),
    BAD_RESPONSE(retryable = true),
    GENERATION_FAILED(retryable = true),
    SERVICE_NOT_CONFIGURED(retryable = false),
    MODEL_UNAVAILABLE(retryable = false),
    AUTHENTICATION_FAILED(retryable = false),
    UNSUPPORTED_INPUT(retryable = false),
    INVALID_TEXT(retryable = false),
    TEXT_TOO_LONG(retryable = false),
    UNSUPPORTED_AUDIO_FORMAT(retryable = false),
    UNSUPPORTED_LANGUAGE(retryable = false),
    STORAGE_FULL(retryable = false),
    UNKNOWN(retryable = false),
}

/**
 * A speech failure that can cross layer boundaries without carrying raw server details to the UI.
 * [technicalDetail] is for diagnostics only and must never contain user text or credentials.
 */
class SpeechException(
    val kind: SpeechFailureKind,
    val technicalDetail: String? = null,
) : Exception(kind.name) {
    val retryable: Boolean get() = kind.retryable
}

/** Playback speeds offered to the user. */
enum class PlaybackSpeed(val factor: Float, val label: String) {
    SLOW_075(0.75f, "0.75×"),
    NORMAL(1.0f, "1.0×"),
    FAST_125(1.25f, "1.25×"),
    FAST_150(1.5f, "1.5×"),
    FAST_200(2.0f, "2.0×");

    companion object {
        fun fromFactor(factor: Float): PlaybackSpeed =
            entries.firstOrNull { it.factor == factor } ?: NORMAL
    }
}

/** Lifecycle of one chunk of a long reading. */
enum class ChunkStatus { PENDING, GENERATING, READY, FAILED }

/** Reading history record status. */
enum class HistoryStatus { READY, PENDING, FAILED }
