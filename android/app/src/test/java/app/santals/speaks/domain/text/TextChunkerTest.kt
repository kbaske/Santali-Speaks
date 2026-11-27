package app.santals.speaks.domain.text

import kotlin.random.Random
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {

    private val olChikiSample =
        "ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹ ᱾ ᱟᱯᱱᱟᱨ ᱯᱚᱛᱚᱵ ᱡᱷᱤᱡᱽ ᱢᱮ ᱾\n\nᱥᱟᱱᱛᱟᱲᱤ ᱨᱮ ᱠᱷᱚᱱ ᱥᱟᱦᱟ ᱠᱟᱛᱷᱟ ᱾ "

    @Test
    fun emptyTextGivesNoChunks() {
        assertEquals(emptyList(), TextChunker.chunk(""))
    }

    @Test
    fun shortTextIsOneChunkUnchanged() {
        assertEquals(listOf(olChikiSample.take(50)), TextChunker.chunk(olChikiSample.take(50)))
    }

    @Test
    fun joiningChunksReproducesOriginalExactly() {
        val text = olChikiSample.repeat(30)
        for (max in listOf(8, 20, 57, 120, 400)) {
            val chunks = TextChunker.chunk(text, max)
            assertEquals(text, chunks.joinToString(""), "max=$max")
        }
    }

    @Test
    fun chunksDoNotExceedLimitForNormalText() {
        val text = olChikiSample.repeat(40)
        val chunks = TextChunker.chunk(text, 120)
        assertTrue(chunks.all { it.length <= 120 }, chunks.maxOf { it.length }.toString())
        assertTrue(chunks.size > 1)
    }

    @Test
    fun prefersParagraphAndSentenceBoundaries() {
        val para1 = "ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹ ᱾ ᱟᱯᱱᱟᱨ ᱯᱚᱛᱚᱵ ᱡᱷᱤᱡᱽ ᱢᱮ ᱾\n"
        val para2 = "ᱥᱟᱱᱛᱟᱲᱤ ᱨᱮ ᱠᱷᱚᱱ ᱥᱟᱦᱟ ᱠᱟᱛᱷᱟ ᱾\n"
        val text = para1 + para2
        val chunks = TextChunker.chunk(text, para1.length + 3)
        assertEquals(para1, chunks[0], "first chunk should end at the paragraph break")
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun splitsAtWordBoundaryWhenNoSentenceEnd() {
        val text = "ᱡᱚᱦᱟᱨ ".repeat(50)
        val chunks = TextChunker.chunk(text, 30)
        assertTrue(chunks.all { it.endsWith(" ") || it == chunks.last() }, "chunks should end on spaces")
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun neverSplitsSurrogatePairs() {
        // Emoji are outside Ol Chiki but may appear in pasted text. They are surrogate pairs.
        val text = "😀".repeat(200)
        for (max in listOf(8, 9, 15, 33)) {
            val chunks = TextChunker.chunk(text, max)
            assertEquals(text, chunks.joinToString(""), "max=$max")
            for (chunk in chunks) {
                assertTrue(chunk.codePointCount(0, chunk.length) * 2 == chunk.length, "broken pair in '$chunk'")
            }
        }
    }

    @Test
    fun neverStartsChunkWithCombiningMark() {
        // U+0901 (Devanagari candrabindu) is a combining mark; it must stay with its base letter.
        val base = "ᱡᱚᱦᱟᱨ"
        val text = (base + "\u0901").repeat(60)
        val chunks = TextChunker.chunk(text, 8)
        assertEquals(text, chunks.joinToString(""))
        for (chunk in chunks.drop(1)) {
            val first = chunk.codePointAt(0)
            assertTrue(Character.getType(first) != Character.NON_SPACING_MARK.toInt(), "chunk starts with a mark: '$chunk'")
        }
    }

    @Test
    fun longWordWithoutSpacesIsHardSplitWithinLimit() {
        val text = "ᱡ".repeat(100)
        val chunks = TextChunker.chunk(text, 16)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 16 })
    }

    @Test
    fun whitespaceOnlyPiecesAreMergedNotEmitted() {
        val text = "ᱡᱚᱦᱟᱨ" + " ".repeat(40) + "ᱜᱤᱫᱽ"
        val chunks = TextChunker.chunk(text, 10)
        assertTrue(chunks.none { it.isBlank() }, chunks.toString())
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun preservesNewlinesAndPunctuationExactly() {
        val text = "ᱡᱚᱦᱟᱨ ᱾\r\n\r\nᱥᱟᱱᱛᱟᱲᱤ ᱾\tTest?!"
        val chunks = TextChunker.chunk(text, 9)
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun randomizedRoundTripProperty() {
        val alphabet = listOf("ᱡ", "ᱚ", "ᱦ", " ", "\n", "\t", ".", "᱾", "a", "😀", "\u0901", "।")
        val rnd = Random(12345)
        repeat(300) {
            val sb = StringBuilder()
            val len = rnd.nextInt(0, 400)
            repeat(len) { sb.append(alphabet[rnd.nextInt(alphabet.size)]) }
            val text = sb.toString()
            val max = rnd.nextInt(8, 80)
            val chunks = TextChunker.chunk(text, max)
            assertEquals(text, chunks.joinToString(""), "round-trip failed for max=$max")
            assertTrue(chunks.none { it.isEmpty() }, "empty chunk produced")
            for (chunk in chunks) {
                assertTrue(
                    Character.getType(chunk.codePointAt(0)) != Character.NON_SPACING_MARK.toInt() || chunk === chunks[0],
                    "mark at chunk start",
                )
            }
        }
    }

    @Test
    fun rejectsTooSmallLimit() {
        var thrown = false
        try {
            TextChunker.chunk("ᱡᱚᱦᱟᱨ", 4)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
