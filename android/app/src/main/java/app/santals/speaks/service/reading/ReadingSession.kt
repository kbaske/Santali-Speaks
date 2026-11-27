package app.santals.speaks.service.reading

import app.santals.speaks.domain.model.ChunkStatus
import app.santals.speaks.domain.model.SpeechFailureKind

enum class SessionStatus {
    /** Parts are being generated (or will be shortly). */
    PREPARING,

    /** Stopped because the device is offline. Resumes automatically when online. */
    WAITING_NETWORK,

    /** A part failed for a reason other than being offline. The user can retry. */
    FAILED,

    /** Every part has audio. */
    COMPLETE,
}

data class ChunkState(
    val index: Int,
    val text: String,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val failure: SpeechFailureKind? = null,
)

/** A long-form reading: the full text split into ordered parts, each generated and played in turn. */
data class ReadingSession(
    val historyId: Long?,
    val fullText: String,
    val chunks: List<ChunkState>,
    val status: SessionStatus,
    val failure: SpeechFailureKind?,
) {
    val readyCount: Int get() = chunks.count { it.status == ChunkStatus.READY }
    val isComplete: Boolean get() = chunks.isNotEmpty() && readyCount == chunks.size

    /** Index of the first part that still needs audio (chunks.size when everything is ready). */
    val firstNotReady: Int
        get() {
            val index = chunks.indexOfFirst { it.status != ChunkStatus.READY }
            return if (index < 0) chunks.size else index
        }
}
