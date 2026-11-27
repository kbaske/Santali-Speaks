package app.santals.speaks.domain.audio

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioSnifferTest {

    private fun bytes(prefix: String, size: Int = 32): ByteArray {
        val out = ByteArray(size)
        prefix.forEachIndexed { i, c -> out[i] = c.code.toByte() }
        return out
    }

    @Test
    fun identifiesWav() {
        val wav = bytes("RIFF").also { it.setAscii(8, "WAVE") }
        assertEquals(AudioContainer.WAV, AudioSniffer.identify(wav))
    }

    @Test
    fun identifiesMp3ByTagAndFrameSync() {
        assertEquals(AudioContainer.MP3, AudioSniffer.identify(bytes("ID3")))
        val sync = ByteArray(32)
        sync[0] = 0xFF.toByte()
        sync[1] = 0xFB.toByte()
        assertEquals(AudioContainer.MP3, AudioSniffer.identify(sync))
    }

    @Test
    fun identifiesOggAndFlac() {
        assertEquals(AudioContainer.OGG, AudioSniffer.identify(bytes("OggS")))
        assertEquals(AudioContainer.FLAC, AudioSniffer.identify(bytes("fLaC")))
    }

    @Test
    fun rejectsUnknownAndTruncated() {
        assertNull(AudioSniffer.identify(bytes("ABCD")))
        assertNull(AudioSniffer.identify(ByteArray(4)))
        assertNull(AudioSniffer.identify(ByteArray(0)))
    }

    private fun ByteArray.setAscii(offset: Int, s: String) {
        s.forEachIndexed { i, c -> this[offset + i] = c.code.toByte() }
    }
}
