package app.santals.speaks.domain.audio

/** Audio containers the app can play. Anything else is rejected before it reaches the player. */
enum class AudioContainer(val mimeType: String, val extension: String) {
    WAV("audio/wav", "wav"),
    MP3("audio/mpeg", "mp3"),
    OGG("audio/ogg", "ogg"),
    FLAC("audio/flac", "flac"),
}

/** Identifies the audio container from its header bytes. */
object AudioSniffer {

    /** Returns the container, or null when the bytes are not a supported audio format. */
    fun identify(bytes: ByteArray): AudioContainer? {
        if (bytes.size < 12) return null
        return when {
            ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WAVE" -> AudioContainer.WAV
            ascii(bytes, 0, 3) == "ID3" -> AudioContainer.MP3
            bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> AudioContainer.MP3
            ascii(bytes, 0, 4) == "OggS" -> AudioContainer.OGG
            ascii(bytes, 0, 4) == "fLaC" -> AudioContainer.FLAC
            else -> null
        }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder(length)
        for (i in offset until offset + length) {
            sb.append((bytes[i].toInt() and 0xFF).toChar())
        }
        return sb.toString()
    }
}
