package app.santals.speaks.domain.text

/**
 * Splits long text into ordered chunks for sequential speech generation.
 *
 * Guarantees (all covered by unit tests):
 *  - Concatenating the chunks gives back the original text exactly (nothing is dropped or altered).
 *  - Chunks are in original order.
 *  - No chunk boundary splits a surrogate pair or lands directly before a combining mark, so
 *    Unicode character sequences such as Ol Chiki letters with marks stay intact.
 *  - Boundaries are preferred at paragraph breaks, then sentence ends, then word spaces.
 *  - Chunks never exceed [maxChars], except in the rare case where a single grapheme cluster is
 *    longer than the limit; then the cluster is kept whole.
 */
object TextChunker {

    const val DEFAULT_MAX_CHARS = 400

    /** Characters that end a sentence in Santali, Hindi/Devanagari and Ol Chiki writing. */
    private val SENTENCE_TERMINATORS = setOf(
        '.', '!', '?', ';', '…',
        '\u0964', // Devanagari danda
        '\u0965', // Devanagari double danda
        '\u1C7E', // Ol Chiki punctuation mucaad
        '\u1C7F', // Ol Chiki punctuation double mucaad
    )

    private const val SCORE_WORD = 1
    private const val SCORE_SENTENCE = 2
    private const val SCORE_PARAGRAPH = 3

    fun chunk(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        require(maxChars >= 8) { "maxChars must be at least 8" }
        if (text.isEmpty()) return emptyList()

        val chunks = ArrayList<String>()
        var start = 0
        val n = text.length
        while (start < n) {
            if (n - start <= maxChars) {
                chunks += text.substring(start)
                break
            }
            val limit = start + maxChars
            val cut = findCut(text, start, limit, SCORE_SENTENCE)
                ?: findCut(text, start, limit, SCORE_WORD)
                ?: hardCut(text, start, limit)
            chunks += text.substring(start, cut)
            start = cut
        }
        return absorbBlankChunks(chunks)
    }

    /** Largest valid boundary in (start, limit] whose score is at least [minScore]. */
    private fun findCut(text: String, start: Int, limit: Int, minScore: Int): Int? {
        var i = limit
        while (i > start) {
            if (isValidCut(text, i) && scoreAt(text, start, i) >= minScore) {
                return i
            }
            i--
        }
        return null
    }

    /** Boundary quality at index [end] (chunk = text[start until end]). */
    private fun scoreAt(text: String, start: Int, end: Int): Int {
        val last = text[end - 1]
        if (last == '\n' || last == '\r') return SCORE_PARAGRAPH
        if (!last.isWhitespace()) return 0
        var j = end - 1
        while (j > start && text[j - 1].isWhitespace()) j--
        if (j > start && text[j - 1] in SENTENCE_TERMINATORS) return SCORE_SENTENCE
        return SCORE_WORD
    }

    /** No usable space: cut at the limit, moving to the nearest safe position. */
    private fun hardCut(text: String, start: Int, limit: Int): Int {
        var i = limit
        while (i > start && !isValidCut(text, i)) i--
        if (i > start) return i
        // Forward search: keep a whole grapheme together even if it exceeds the limit.
        i = limit
        while (i < text.length && !isValidCut(text, i)) i++
        return i
    }

    /**
     * A cut at [index] is valid when it does not split a surrogate pair and the following code
     * point is not a combining mark (which must stay with its base character).
     */
    private fun isValidCut(text: String, index: Int): Boolean {
        if (index <= 0 || index >= text.length) return true
        if (Character.isLowSurrogate(text[index]) && Character.isHighSurrogate(text[index - 1])) return false
        val next = text.codePointAt(index)
        return when (Character.getType(next)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> false
            else -> true
        }
    }

    /** Whitespace-only pieces are merged into a neighbour so no empty speech requests are made. */
    private fun absorbBlankChunks(chunks: List<String>): List<String> {
        val out = ArrayList<String>()
        var carry = ""
        for (chunk in chunks) {
            if (chunk.isBlank()) {
                if (out.isEmpty()) carry += chunk else out[out.size - 1] = out.last() + chunk
            } else {
                out += carry + chunk
                carry = ""
            }
        }
        if (out.isEmpty() && carry.isNotEmpty()) out += carry
        return out
    }
}
