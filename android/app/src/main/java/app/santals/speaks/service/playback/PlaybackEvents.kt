package app.santals.speaks.service.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide signals from the playback service (for example, the Stop button in the media
 * notification) to the reading session, so that generation also stops.
 */
object PlaybackEvents {
    private val stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val stopRequested: SharedFlow<Unit> = stopRequests.asSharedFlow()

    fun requestStop() {
        stopRequests.tryEmit(Unit)
    }
}
