import { test } from 'node:test';
import assert from 'node:assert/strict';
import { analyzeSantaliText, containsOlChiki, MAX_TEXT_LENGTH } from '../src/olchiki.mjs';

test('accepts Ol Chiki text and reports letter counts', () => {
  const r = analyzeSantaliText('ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹ');
  assert.equal(r.ok, true);
  assert.equal(r.code, 'OK');
  assert.equal(r.olChikiLetters, 12);
  assert.equal(r.otherLetters, 0);
});

test('accepts mixed Ol Chiki and Latin, counting both', () => {
  const r = analyzeSantaliText('ᱡᱚᱦᱟᱨ Johar');
  assert.equal(r.ok, true);
  assert.equal(r.olChikiLetters, 5);
  assert.equal(r.otherLetters, 5);
});

test('accepts Ol Chiki punctuation and multiline text', () => {
  const r = analyzeSantaliText('ᱡᱚᱦᱟᱨ ᱾\n\nᱥᱟᱱᱛᱟᱲᱤ ᱾');
  assert.equal(r.ok, true);
});

test('rejects Devanagari, Latin and empty text as lacking Ol Chiki', () => {
  for (const sample of ['नमस्ते', 'hello world', '123 !!!']) {
    const r = analyzeSantaliText(sample);
    assert.equal(r.ok, false, sample);
    assert.equal(r.code, 'NO_OLCHIKI', sample);
  }
});

test('rejects empty and whitespace-only text', () => {
  assert.equal(analyzeSantaliText('').code, 'EMPTY');
  assert.equal(analyzeSantaliText('   \n\t ').code, 'EMPTY');
});

test('rejects non-string input', () => {
  assert.equal(analyzeSantaliText(null).code, 'NOT_STRING');
  assert.equal(analyzeSantaliText(42).code, 'NOT_STRING');
  assert.equal(analyzeSantaliText(['ᱡᱚᱦᱟᱨ']).code, 'NOT_STRING');
});

test('rejects text longer than the limit', () => {
  const r = analyzeSantaliText('ᱡ'.repeat(MAX_TEXT_LENGTH + 1));
  assert.equal(r.code, 'TOO_LONG');
  assert.equal(analyzeSantaliText('ᱡ'.repeat(MAX_TEXT_LENGTH)).ok, true);
});

test('rejects disallowed control characters but allows tab and newline', () => {
  assert.equal(analyzeSantaliText('ᱡᱚᱦᱟᱨ\u0000').code, 'CONTROL_CHARACTERS');
  assert.equal(analyzeSantaliText('ᱡᱚᱦᱟᱨ\u0007').code, 'CONTROL_CHARACTERS');
  assert.equal(analyzeSantaliText('ᱡᱚᱦᱟᱨ\tᱜᱤᱫᱽ\nᱟᱯᱱᱟᱨ').ok, true);
});

test('never alters the original text', () => {
  const original = 'ᱡᱚᱦᱟᱨ  ᱜᱤᱫᱽᱨᱟᱹ\r\n\r\n᱾ Test ';
  const copy = original.slice();
  analyzeSantaliText(original);
  assert.equal(original, copy);
});

test('containsOlChiki is false for non-strings and for plain text', () => {
  assert.equal(containsOlChiki(undefined), false);
  assert.equal(containsOlChiki('abc'), false);
  assert.equal(containsOlChiki('ᱚ'), true);
});

test('Ol Chiki code points U+1C50 and U+1C7F are both recognised', () => {
  assert.equal(containsOlChiki('\u1C50'), true);
  assert.equal(containsOlChiki('\u1C7F'), true);
  assert.equal(containsOlChiki('\u1C4F'), false);
});

test('Ol Chiki punctuation alone is not accepted as Santali text', () => {
  const r = analyzeSantaliText('᱾᱿');
  assert.equal(r.ok, false);
  assert.equal(r.code, 'NO_OLCHIKI');
});
