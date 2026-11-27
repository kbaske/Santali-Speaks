package app.santals.speaks.domain.model

/**
 * Audio generated (or found in the local cache) for one chunk of text.
 * [filePath] points to a file in app-private storage; the domain layer treats it as an opaque string.
 */
data class SpeechClip(
    val cacheKey: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val fromCache: Boolean,
)
