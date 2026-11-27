package app.santals.speaks.service.reading

import android.content.Context
import app.santals.speaks.R
import app.santals.speaks.data.network.NetworkMonitor
import app.santals.speaks.domain.model.ChunkStatus
import app.santals.speaks.domain.model.HistoryStatus
import app.santals.speaks.domain.model.SpeechException
import app.santals.speaks.domain.model.SpeechFailureKind
import app.santals.speaks.domain.repository.HistoryRepository
import app.santals.speaks.domain.repository.SpeechRepository
import app.santals.speaks.domain.text.OlChikiText
import app.santals.speaks.domain.text.TextChunker
import app.santals.speaks.domain.text.ValidationCode
import app.santals.speaks.service.playback.PlaybackController
import app.santals.speaks.service.playback.PlaybackEvents
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a long reading from start to finish:
 *  - splits the text into parts (never inside a character sequence);
 *  - generates each part in order, using cache when available;
 *  - appends each part to the player queue as soon as it is ready, so playback starts early and
 *    continues automatically;
 *  - saves progress to history and resumes automatically when the network returns.
 *
 * Every mutation is tagged with a generation number, so a cancelled job can never overwrite the
 * state of a newer reading.
 */
@Singleton
class ReadingSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speech: SpeechRepository,
    private val history: HistoryRepository,
    private val playback: PlaybackController,
    private val network: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _session = MutableStateFlow<ReadingSession?>(null)
    val session: StateFlow<ReadingSession?> = _session.asStateFlow()

    private val _messages = MutableSharedFlow<SpeechFailureKind>(extraBufferCapacity = 8)
    val messages: SharedFlow<SpeechFailureKind> = _messages.asSharedFlow()

    private var job: Job? = null
    private var generation = 0L

    init {
        scope.launch {
            PlaybackEvents.stopRequested.collect { stop() }
        }
        scope.launch {
            network.isOnline.collect { online ->
                val current = _session.value
                if (online && current?.status == SessionStatus.WAITING_NETWORK && job?.isActive != true) {
                    resume()
                }
            }
        }
    }

    /**
     * Starts reading [text]. Returns false (and emits a message) when the text is not Ol Chiki.
     * Pass [existingHistoryId] when replaying a saved reading, to avoid duplicate history entries.
     */
    fun start(text: String, existingHistoryId: Long? = null): Boolean {
        val analysis = OlChikiText.analyze(text)
        if (!analysis.ok) {
            _messages.tryEmit(
                when (analysis.code) {
                    ValidationCode.NO_OLCHIKI -> SpeechFailureKind.UNSUPPORTED_INPUT
                    ValidationCode.TOO_LONG -> SpeechFailureKind.TEXT_TOO_LONG
                    else -> SpeechFailureKind.INVALID_TEXT
                },
            )
            return false
        }

        job?.cancel()
        val gen = ++generation
        job = scope.launch {
            playback.resetQueue()
            val parts = TextChunker.chunk(text)
            val historyId = existingHistoryId ?: runCatching {
                history.create(text, parts.size, System.currentTimeMillis())
            }.getOrNull()
            if (existingHistoryId != null) {
                runCatching { history.markPlayed(existingHistoryId, System.currentTimeMillis()) }
            }
            _session.value = ReadingSession(
                historyId = historyId,
                fullText = text,
                chunks = parts.mapIndexed { i, part -> ChunkState(index = i, text = part) },
                status = SessionStatus.PREPARING,
                failure = null,
            )
            generateFrom(gen, 0)
        }
        return true
    }

    /** Continues a reading that stopped (offline, or after a failed part). */
    fun resume() {
        val current = _session.value ?: return
        if (current.isComplete) return
        job?.cancel()
        val gen = ++generation
        job = scope.launch { generateFrom(gen, current.firstNotReady) }
    }

    /** Stops generation and playback. Partially prepared readings stay in history. */
    fun stop() {
        job?.cancel()
        job = null
        generation++
        val finished = _session.value
        _session.value = null
        scope.launch {
            playback.resetQueue()
            val id = finished?.historyId ?: return@launch
            val status = if (finished.isComplete) HistoryStatus.READY else HistoryStatus.PENDING
            runCatching { history.updateStatus(id, status) }
        }
    }

    private suspend fun generateFrom(gen: Long, startIndex: Int) {
        updateSession(gen) { it.copy(status = SessionStatus.PREPARING, failure = null) }

        var failure: SpeechFailureKind? = null
        val total = _session.value?.chunks?.size ?: return
        for (index in startIndex until total) {
            val chunkText = _session.value?.chunks?.getOrNull(index)?.text ?: return
            setChunk(gen, index, ChunkStatus.GENERATING, null)
            try {
                val clip = speech.speak(chunkText)
                setChunk(gen, index, ChunkStatus.READY, null)
                if (gen != generation) return
                playback.append(
                    filePath = clip.filePath,
                    cacheKey = clip.cacheKey,
                    partText = chunkText,
                    title = context.getString(R.string.player_part, index + 1, total),
                )
                if (index == 0) playback.play()
            } catch (e: CancellationException) {
                setChunk(gen, index, ChunkStatus.PENDING, null)
                throw e
            } catch (e: SpeechException) {
                failure = e.kind
                setChunk(gen, index, ChunkStatus.FAILED, e.kind)
                break
            } catch (e: Exception) {
                failure = SpeechFailureKind.UNKNOWN
                setChunk(gen, index, ChunkStatus.FAILED, SpeechFailureKind.UNKNOWN)
                break
            }
        }
        finish(gen, failure)
    }

    private suspend fun finish(gen: Long, failure: SpeechFailureKind?) {
        if (gen != generation) return
        val current = _session.value ?: return
        val complete = current.isComplete

        val status = when {
            complete -> SessionStatus.COMPLETE
            failure == SpeechFailureKind.NO_NETWORK -> SessionStatus.WAITING_NETWORK
            else -> SessionStatus.FAILED
        }
        updateSession(gen) { it.copy(status = status, failure = failure) }

        current.historyId?.let { id ->
            val historyStatus = when (status) {
                SessionStatus.COMPLETE -> HistoryStatus.READY
                SessionStatus.WAITING_NETWORK -> HistoryStatus.PENDING
                else -> HistoryStatus.FAILED
            }
            runCatching { history.updateStatus(id, historyStatus) }
        }

        if (failure != null && failure != SpeechFailureKind.NO_NETWORK) {
            _messages.tryEmit(failure)
        }
    }

    private fun setChunk(gen: Long, index: Int, status: ChunkStatus, failure: SpeechFailureKind?) {
        updateSession(gen) { session ->
            session.copy(
                chunks = session.chunks.mapIndexed { i, chunk ->
                    if (i == index) chunk.copy(status = status, failure = failure) else chunk
                },
            )
        }
    }

    private inline fun updateSession(gen: Long, transform: (ReadingSession) -> ReadingSession) {
        if (gen != generation) return
        val current = _session.value ?: return
        _session.value = transform(current)
    }
}
