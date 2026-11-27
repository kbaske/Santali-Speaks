import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decodeAndIdentifyAudio, AudioFormatError } from '../src/audio.mjs';

const b64 = (buf) => Buffer.from(buf).toString('base64');

function wavBytes() {
  const b = Buffer.alloc(44);
  b.write('RIFF', 0, 'ascii');
  b.writeUInt32LE(36, 4);
  b.write('WAVE', 8, 'ascii');
  b.write('fmt ', 12, 'ascii');
  return b;
}

test('identifies WAV', () => {
  const r = decodeAndIdentifyAudio(b64(wavBytes()));
  assert.equal(r.mimeType, 'audio/wav');
  assert.equal(r.extension, 'wav');
  assert.equal(r.bytes.length, 44);
});

test('identifies MP3 with ID3 tag and with raw frame sync', () => {
  const id3 = Buffer.concat([Buffer.from('ID3\x04\x00\x00\x00\x00\x00\x00', 'latin1'), Buffer.alloc(8)]);
  assert.equal(decodeAndIdentifyAudio(b64(id3)).mimeType, 'audio/mpeg');
  const sync = Buffer.concat([Buffer.from([0xff, 0xfb, 0x90, 0x00]), Buffer.alloc(12)]);
  assert.equal(decodeAndIdentifyAudio(b64(sync)).mimeType, 'audio/mpeg');
});

test('identifies OGG and FLAC', () => {
  assert.equal(decodeAndIdentifyAudio(b64(Buffer.concat([Buffer.from('OggS'), Buffer.alloc(12)]))).extension, 'ogg');
  assert.equal(decodeAndIdentifyAudio(b64(Buffer.concat([Buffer.from('fLaC'), Buffer.alloc(12)]))).extension, 'flac');
});

test('rejects empty, malformed and unknown payloads with UNSUPPORTED_AUDIO_FORMAT or clear errors', () => {
  assert.throws(() => decodeAndIdentifyAudio(''), AudioFormatError);
  assert.throws(() => decodeAndIdentifyAudio(undefined), AudioFormatError);
  assert.throws(() => decodeAndIdentifyAudio('not base64!!'), AudioFormatError);
  assert.throws(() => decodeAndIdentifyAudio('QUJD'), AudioFormatError); // too short (3 bytes)
  assert.throws(() => decodeAndIdentifyAudio(b64(Buffer.alloc(32, 0x41))), AudioFormatError);
});

test('error code is stable', () => {
  try {
    decodeAndIdentifyAudio(b64(Buffer.alloc(32, 0x41)));
    assert.fail('should throw');
  } catch (e) {
    assert.equal(e.code, 'UNSUPPORTED_AUDIO_FORMAT');
  }
});
