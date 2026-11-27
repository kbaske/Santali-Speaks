package app.santals.speaks.domain.audio

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CacheKeyTest {

    @Test
    fun keyIsStableAndHex64() {
        val a = CacheKey.of("ᱡᱚᱦᱟᱨ")
        assertEquals(a, CacheKey.of("ᱡᱚᱦᱟᱨ"))
        assertEquals(64, a.length)
        assertEquals(true, a.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun differentTextOrVoiceGivesDifferentKey() {
        assertNotEquals(CacheKey.of("ᱡᱚᱦᱟᱨ"), CacheKey.of("ᱡᱚᱦᱟᱨ "))
        assertNotEquals(CacheKey.of("ᱡᱚᱦᱟᱨ"), CacheKey.of("ᱡᱚᱦᱟᱨ", gender = "male"))
        assertNotEquals(CacheKey.of("ᱡᱚᱦᱟᱨ"), CacheKey.of("ᱡᱚᱦᱟᱨ", language = "hi"))
    }

    @Test
    fun keyPreservesScriptBytes() {
        // The same visible Santali written with different code point sequences must not collide silently.
        assertNotEquals(CacheKey.of("ᱟ"), CacheKey.of("ᱟ\u200B"))
    }
}
