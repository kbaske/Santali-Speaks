package app.santals.speaks.data.repository

import app.santals.speaks.data.local.AudioCache
import app.santals.speaks.data.local.HistoryDao
import app.santals.speaks.data.local.HistoryEntity
import app.santals.speaks.domain.audio.CacheKey
import app.santals.speaks.domain.model.HistoryItem
import app.santals.speaks.domain.model.HistoryStatus
import app.santals.speaks.domain.repository.HistoryRepository
import app.santals.speaks.domain.text.TextChunker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Room-backed reading history. Deleting a record also deletes its generated audio. */
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao,
    private val cache: AudioCache,
) : HistoryRepository {

    override fun observeAll(): Flow<List<HistoryItem>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): HistoryItem? = dao.get(id)?.toDomain()

    override suspend fun create(text: String, chunkCount: Int, now: Long): Long =
        dao.insert(
            HistoryEntity(
                text = text,
                createdAt = now,
                status = HistoryStatus.PENDING.name,
                chunkCount = chunkCount,
            ),
        )

    override suspend fun updateStatus(id: Long, status: HistoryStatus) =
        dao.updateStatus(id, status.name)

    override suspend fun markPlayed(id: Long, now: Long) = dao.markPlayed(id, now)

    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun delete(id: Long) {
        val entity = dao.get(id) ?: return
        withContext(Dispatchers.IO) {
            // Same chunking as ReadingSessionManager, so the cache keys match.
            TextChunker.chunk(entity.text).forEach { cache.delete(CacheKey.of(it)) }
        }
        dao.delete(id)
    }

    override suspend fun clearAll() {
        dao.clearAll()
        withContext(Dispatchers.IO) { cache.clear() }
    }
}

private fun HistoryEntity.toDomain(): HistoryItem = HistoryItem(
    id = id,
    text = text,
    createdAt = createdAt,
    lastPlayedAt = lastPlayedAt,
    status = runCatching { HistoryStatus.valueOf(status) }.getOrDefault(HistoryStatus.PENDING),
    favorite = favorite,
    chunkCount = chunkCount,
)
