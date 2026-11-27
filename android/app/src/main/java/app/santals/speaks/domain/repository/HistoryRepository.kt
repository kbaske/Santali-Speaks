package app.santals.speaks.domain.repository

import app.santals.speaks.domain.model.HistoryItem
import app.santals.speaks.domain.model.HistoryStatus
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeAll(): Flow<List<HistoryItem>>
    suspend fun get(id: Long): HistoryItem?
    suspend fun create(text: String, chunkCount: Int, now: Long): Long
    suspend fun updateStatus(id: Long, status: HistoryStatus)
    suspend fun markPlayed(id: Long, now: Long)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    /** Deletes the record and the cached audio that belongs to it. */
    suspend fun delete(id: Long)
    /** Deletes every record and all history-related cached audio. */
    suspend fun clearAll()
}
