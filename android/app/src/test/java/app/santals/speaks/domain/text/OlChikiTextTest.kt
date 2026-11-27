package app.santals.speaks.domain.text

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OlChikiTextTest {

    @Test
    fun acceptsOlChikiAndCountsLetters() {
        val r = OlChikiText.analyze("ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹ")
        assertTrue(r.ok)
        assertEquals(ValidationCode.OK, r.code)
        assertEquals(12, r.olChikiLetters)
        assertEquals(0, r.otherLetters)
    }

    @Test
    fun acceptsMixedScriptAndCountsBoth() {
        val r = OlChikiText.analyze("ᱡᱚᱦᱟᱨ Johar")
        assertTrue(r.ok)
        assertEquals(5, r.olChikiLetters)
        assertEquals(5, r.otherLetters)
    }

    @Test
    fun acceptsOlChikiPunctuationAndMultiline() {
        assertTrue(OlChikiText.analyze("ᱡᱚᱦᱟᱨ ᱾\n\nᱥᱟᱱᱛᱟᱲᱤ ᱾").ok)
    }

    @Test
    fun rejectsTextWithoutOlChiki() {
        for (sample in listOf("नमस्ते", "hello world", "123 !!!")) {
            val r = OlChikiText.analyze(sample)
            assertFalse(r.ok, sample)
            assertEquals(ValidationCode.NO_OLCHIKI, r.code, sample)
        }
    }

    @Test
    fun olChikiPunctuationAloneIsNotSantaliText() {
        assertEquals(ValidationCode.NO_OLCHIKI, OlChikiText.analyze("᱾᱿").code)
    }

    @Test
    fun rejectsEmptyNullAndWhitespace() {
        assertEquals(ValidationCode.EMPTY, OlChikiText.analyze("").code)
        assertEquals(ValidationCode.EMPTY, OlChikiText.analyze("  \n\t ").code)
        assertEquals(ValidationCode.EMPTY, OlChikiText.analyze(null).code)
    }

    @Test
    fun rejectsTooLongText() {
        assertEquals(ValidationCode.TOO_LONG, OlChikiText.analyze("ᱡ".repeat(OlChikiText.SERVER_MAX_CHARS + 1)).code)
        assertTrue(OlChikiText.analyze("ᱡ".repeat(OlChikiText.SERVER_MAX_CHARS)).ok)
    }

    @Test
    fun rejectsControlCharactersButAllowsTabAndNewline() {
        assertEquals(ValidationCode.CONTROL_CHARACTERS, OlChikiText.analyze("ᱡᱚᱦᱟᱨ\u0000").code)
        assertEquals(ValidationCode.CONTROL_CHARACTERS, OlChikiText.analyze("ᱡᱚᱦᱟᱨ\u0007").code)
        assertTrue(OlChikiText.analyze("ᱡᱚᱦᱟᱨ\tᱜᱤᱫᱽ\nᱟᱯᱱᱟᱨ").ok)
    }

    @Test
    fun analysisNeverModifiesInput() {
        val original = "ᱡᱚᱦᱟᱨ  ᱜᱤᱫᱽᱨᱟᱹ\r\n\r\n᱾ Test "
        val copy = String(original.toCharArray())
        OlChikiText.analyze(original)
        assertEquals(copy, original)
    }

    @Test
    fun containsOlChikiWorksForLongOrControlText() {
        assertTrue(OlChikiText.containsOlChiki("ᱚ"))
        assertTrue(OlChikiText.containsOlChiki("ᱚ".repeat(2000)))
        assertTrue(OlChikiText.containsOlChiki("ᱚ\u0000"))
        assertFalse(OlChikiText.containsOlChiki("abc"))
        assertFalse(OlChikiText.containsOlChiki(null))
        assertFalse(OlChikiText.containsOlChiki(""))
    }

    @Test
    fun blockBoundariesAreRecognised() {
        assertTrue(OlChikiText.isOlChikiCodePoint(0x1C50))
        assertTrue(OlChikiText.isOlChikiCodePoint(0x1C7F))
        assertFalse(OlChikiText.isOlChikiCodePoint(0x1C4F))
        assertFalse(OlChikiText.isOlChikiCodePoint(0x1C80))
    }
}
