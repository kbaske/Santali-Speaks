package app.santals.speaks.service.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.santals.speaks.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the player screen needs to draw. Immutable snapshot published on every change. */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val playerState: Int = Player.STATE_IDLE,
    val itemCount: Int = 0,
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    /** Text of the part that is currently playing (stored in the media item, never re-derived). */
    val currentText: String? = null,
    /** True when the user wants audio to play (survives buffering and end-of-queue). */
    val wantPlaying: Boolean = false,
) {
    val hasQueue: Boolean get() = itemCount > 0
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * App-side handle to the playback service. Owns the MediaController connection and turns
 * player events into a [PlaybackUiState] stream. All methods are called on the main thread.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectLock = Mutex()
    private var controller: MediaController? = null
    private var wantPlaying = false

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val errors: SharedFlow<Unit> = _errors.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = publish()
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = publish()

        override fun onPlayerError(error: PlaybackException) {
            // Do not auto-retry playback: the user decides, so audio never starts unexpectedly.
            wantPlaying = false
            publish()
            _errors.tryEmit(Unit)
        }
    }

    init {
        // Position updates while playing (drives the progress bar).
        scope.launch {
            while (isActive) {
                if (controller?.isPlaying == true) publish()
                delay(POSITION_TICK_MS)
            }
        }
    }

    /** Connects to the playback service once. Safe to call repeatedly and concurrently. */
    suspend fun connect(): MediaController = connectLock.withLock {
        controller?.let { return@withLock it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val connected = MediaController.Builder(context, token).buildAsync().await()
        connected.addListener(listener)
        controller = connected
        publish()
        connected
    }

    /** Adds one generated part to the end of the queue, keeping the original text for display. */
    suspend fun append(filePath: String, cacheKey: String, partText: String, title: String) {
        val c = connect()
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(context.getString(R.string.app_name))
            .setDescription(partText)
            .build()
        val item = MediaItem.Builder()
            .setMediaId(cacheKey)
            .setUri(Uri.fromFile(File(filePath)))
            .setMediaMetadata(metadata)
            .build()

        // ExoPlayer stops at the end of the queue. If the listener is still waiting for more parts,
        // continue with the new part instead of stopping.
        val wasEnded = c.playbackState == Player.STATE_ENDED
        c.addMediaItem(item)
        if (wasEnded && wantPlaying) {
            c.seekTo(c.mediaItemCount - 1, 0L)
            c.play()
        }
        publish()
    }

    fun play() = scope.launch {
        val c = connect()
        wantPlaying = true
        if (c.mediaItemCount > 0) c.play()
        publish()
    }

    fun pause() = scope.launch {
        val c = connect()
        wantPlaying = false
        c.pause()
        publish()
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekBack() = scope.launch { connect().seekBack() }

    fun seekForward() = scope.launch { connect().seekForward() }

    /** Seeks within the current part to a fraction (0..1) of its length. */
    fun seekToFraction(fraction: Float) = scope.launch {
        val c = connect()
        val duration = c.duration
        if (duration != C.TIME_UNSET && duration > 0) {
            c.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
            publish()
        }
    }

    fun setSpeed(factor: Float) = scope.launch {
        // Pitch is left at 1.0 so speech stays natural (time-stretching, not chipmunk-style).
        connect().setPlaybackSpeed(factor)
        publish()
    }

    /** Stops playback and empties the queue. */
    suspend fun resetQueue() {
        val c = connect()
        wantPlaying = false
        c.stop()
        c.clearMediaItems()
        publish()
    }

    private fun publish() {
        val c = controller
        val current = _state.value
        _state.value = if (c == null) {
            current.copy(isPlaying = false, itemCount = 0, positionMs = 0L, durationMs = 0L)
        } else {
            PlaybackUiState(
                isPlaying = c.isPlaying,
                playerState = c.playbackState,
                itemCount = c.mediaItemCount,
                currentIndex = if (c.mediaItemCount > 0) c.currentMediaItemIndex else 0,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                speed = c.playbackParameters.speed,
                currentText = c.currentMediaItem?.mediaMetadata?.description?.toString(),
                wantPlaying = wantPlaying,
            )
        }
    }

    private companion object {
        const val POSITION_TICK_MS = 250L
    }
}
