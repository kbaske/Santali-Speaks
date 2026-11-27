package app.santals.speaks.domain.text

/**
 * Script-aware checks for Santali text written in Ol Chiki (Unicode block U+1C50..U+1C7F).
 *
 * These functions only inspect text. They never change it: the caller keeps the original
 * string and sends exactly that string to the speech service.
 */
object OlChikiText {

    /** First and last code points of the Ol Chiki block. */
    const val BLOCK_START = 0x1C50
    const val BLOCK_END = 0x1C7F

    /** Upper bound accepted by the server in one request. Chunking keeps requests below this. */
    const val SERVER_MAX_CHARS = 1000

    /** Returns true when the code point is in the Ol Chiki block. */
    fun isOlChikiCodePoint(codePoint: Int): Boolean = codePoint in BLOCK_START..BLOCK_END

    /** Returns true when the text contains at least one Ol Chiki letter. */
    fun containsOlChiki(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        return countLetters(text).first > 0
    }

    /**
     * Inspect [text] without modifying it.
     *
     * Letters are counted by code point, so surrogate pairs (not used by Ol Chiki, but possible in
     * pasted text) are handled correctly.
     */
    fun analyze(text: String?): TextAnalysis {
        if (text == null) {
            return TextAnalysis.invalid(ValidationCode.EMPTY, "No text provided.", 0)
        }
        if (text.isBlank()) {
            return TextAnalysis.invalid(ValidationCode.EMPTY, "The text is empty.", text.length)
        }
        if (text.length > SERVER_MAX_CHARS) {
            return TextAnalysis.invalid(
                ValidationCode.TOO_LONG,
                "The text is longer than $SERVER_MAX_CHARS characters. Split it into smaller parts.",
                text.length,
            )
        }
        if (containsDisallowedControl(text)) {
            return TextAnalysis.invalid(
                ValidationCode.CONTROL_CHARACTERS,
                "The text contains control characters that cannot be spoken.",
                text.length,
            )
        }

        val (olChiki, other) = countLetters(text)

        if (olChiki == 0) {
            return TextAnalysis(
                ok = false,
                code = ValidationCode.NO_OLCHIKI,
                message = "No Ol Chiki (ᱚᱞ ᱪᱤᱠᱤ) characters were found. Santali speech needs Ol Chiki text.",
                olChikiLetters = olChiki,
                otherLetters = other,
                length = text.length,
            )
        }
        return TextAnalysis(true, ValidationCode.OK, "Text accepted.", olChiki, other, text.length)
    }

    /** Returns (Ol Chiki letters, other letters), counting by code point. */
    private fun countLetters(text: String): Pair<Int, Int> {
        var olChiki = 0
        var other = 0
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            if (Character.isLetter(cp)) {
                if (isOlChikiCodePoint(cp)) olChiki++ else other++
            }
            index += Character.charCount(cp)
        }
        return olChiki to other
    }

    private fun containsDisallowedControl(text: String): Boolean {
        for (ch in text) {
            val c = ch.code
            val isDisallowed = (c in 0x00..0x08) || c == 0x0B || c == 0x0C || (c in 0x0E..0x1F) || c == 0x7F
            if (isDisallowed) return true
        }
        return false
    }
}

enum class ValidationCode { OK, EMPTY, TOO_LONG, CONTROL_CHARACTERS, NO_OLCHIKI }

data class TextAnalysis(
    val ok: Boolean,
    val code: ValidationCode,
    val message: String,
    val olChikiLetters: Int,
    val otherLetters: Int,
    val length: Int,
) {
    companion object {
        fun invalid(code: ValidationCode, message: String, length: Int) =
            TextAnalysis(false, code, message, 0, 0, length)
    }
}
