package app.santals.speaks.domain.model

/** One reading saved in local history. The text stays on the device. */
data class HistoryItem(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val lastPlayedAt: Long?,
    val status: HistoryStatus,
    val favorite: Boolean,
    val chunkCount: Int,
)
