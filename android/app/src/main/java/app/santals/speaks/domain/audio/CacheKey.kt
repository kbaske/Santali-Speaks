package app.santals.speaks.domain.audio

import java.security.MessageDigest

/**
 * Stable identifier for generated audio. It is derived from the exact chunk text and the speech
 * settings, so changing the voice or language produces a different key and never reuses old audio.
 *
 * [SCHEMA] must be bumped if the audio pipeline changes in a way that makes old cache entries wrong.
 */
object CacheKey {
    const val SCHEMA = "sat-v1"

    fun of(text: String, language: String = "sat", gender: String = "female"): String {
        val material = "$SCHEMA|$language|$gender|$text".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
