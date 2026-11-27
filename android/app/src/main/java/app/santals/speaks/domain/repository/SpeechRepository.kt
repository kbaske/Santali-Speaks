package app.santals.speaks.domain.repository

import app.santals.speaks.domain.model.SpeechClip

/**
 * Turns one chunk of Ol Chiki text into playable audio.
 *
 * Implementations: cache first (works offline), then the Santali Speaks server, which forwards
 * to BHASHINI. Failures are reported as [app.santals.speaks.domain.model.SpeechException].
 */
interface SpeechRepository {
    /** Returns audio for [text], generating it if it is not cached. */
    suspend fun speak(text: String): SpeechClip

    /** Returns cached audio for [text] without touching the network, or null. */
    suspend fun findCached(text: String): SpeechClip?
}
