// Script-aware validation for Santali text written in Ol Chiki (Unicode block U+1C50–U+1C7F).
//
// IMPORTANT: these functions never modify the text. They only inspect it.
// Santali text is always forwarded to BHASHINI exactly as the user supplied it.

/** Maximum number of UTF-16 code units accepted by the proxy in one request. */
export const MAX_TEXT_LENGTH = 1000;

/** Ol Chiki script, as Unicode property escape (Unicode 6.1+). */
const OL_CHIKI_RE = /\p{Script=Ol_Chiki}/u;
const OL_CHIKI_LETTER_RE = OL_CHIKI_RE;
const LETTER_RE = /\p{L}/u;
/** Control characters other than the whitespace we allow in running text. */
const DISALLOWED_CONTROL_RE = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/u;

/**
 * Inspect text for Ol Chiki content.
 * @param {unknown} text
 * @returns {{
 *   ok: boolean,
 *   code: 'OK'|'NOT_STRING'|'EMPTY'|'TOO_LONG'|'CONTROL_CHARACTERS'|'NO_OLCHIKI',
 *   message: string,
 *   olChikiLetters: number,
 *   otherLetters: number,
 *   length: number,
 * }}
 */
export function analyzeSantaliText(text) {
  if (typeof text !== 'string') {
    return result(false, 'NOT_STRING', 'Text must be a string.', 0, 0, 0);
  }
  if (text.trim().length === 0) {
    return result(false, 'EMPTY', 'Text is empty.', 0, 0, text.length);
  }
  if (text.length > MAX_TEXT_LENGTH) {
    return result(
      false,
      'TOO_LONG',
      `Text is longer than ${MAX_TEXT_LENGTH} characters. Split it into smaller parts.`,
      0,
      0,
      text.length,
    );
  }
  if (DISALLOWED_CONTROL_RE.test(text)) {
    return result(
      false,
      'CONTROL_CHARACTERS',
      'Text contains control characters that cannot be spoken.',
      0,
      0,
      text.length,
    );
  }

  let olChikiLetters = 0;
  let otherLetters = 0;
  for (const ch of text) {
    if (!LETTER_RE.test(ch)) {
      continue; // digits, punctuation (including Ol Chiki punctuation) and spaces
    }
    if (OL_CHIKI_RE.test(ch)) {
      olChikiLetters += 1;
    } else {
      otherLetters += 1;
    }
  }

  if (olChikiLetters === 0) {
    return result(
      false,
      'NO_OLCHIKI',
      'No Ol Chiki (ᱚᱞ ᱪᱤᱠᱤ) characters were found. Santali speech needs Ol Chiki text.',
      olChikiLetters,
      otherLetters,
      text.length,
    );
  }

  return result(true, 'OK', 'Text accepted.', olChikiLetters, otherLetters, text.length);
}

/** True when the string contains at least one Ol Chiki code point. */
export function containsOlChiki(text) {
  return typeof text === 'string' && OL_CHIKI_LETTER_RE.test(text);
}

function result(ok, code, message, olChikiLetters, otherLetters, length) {
  return { ok, code, message, olChikiLetters, otherLetters, length };
}
