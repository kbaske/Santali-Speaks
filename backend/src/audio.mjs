// Audio format detection for base64 audio returned by the speech engine.
// We sniff magic bytes instead of trusting the upstream "audioFormat" field alone,
// so that an unexpected payload is rejected instead of being played as garbage.

/**
 * Decode base64 and identify the container format from its header bytes.
 * @param {string} base64
 * @returns {{ mimeType: 'audio/wav'|'audio/mpeg'|'audio/ogg'|'audio/flac', extension: 'wav'|'mp3'|'ogg'|'flac', bytes: Buffer }}
 * @throws {AudioFormatError} when the payload is empty, malformed, or an unknown format.
 */
export function decodeAndIdentifyAudio(base64) {
  if (typeof base64 !== 'string' || base64.length === 0) {
    throw new AudioFormatError('Speech engine returned no audio data.');
  }
  // Strict base64 check: standard alphabet, padded to a multiple of 4 characters.
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(base64) || base64.length % 4 !== 0) {
    throw new AudioFormatError('Speech engine returned malformed base64 audio.');
  }
  const bytes = Buffer.from(base64, 'base64');
  if (bytes.length < 12) {
    throw new AudioFormatError('Speech engine returned an audio payload that is too short.');
  }

  if (bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WAVE') {
    return { mimeType: 'audio/wav', extension: 'wav', bytes };
  }
  if (bytes.toString('ascii', 0, 3) === 'ID3' || (bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0)) {
    return { mimeType: 'audio/mpeg', extension: 'mp3', bytes };
  }
  if (bytes.toString('ascii', 0, 4) === 'OggS') {
    return { mimeType: 'audio/ogg', extension: 'ogg', bytes };
  }
  if (bytes.toString('ascii', 0, 4) === 'fLaC') {
    return { mimeType: 'audio/flac', extension: 'flac', bytes };
  }
  throw new AudioFormatError('Speech engine returned an audio format that this app does not support.');
}

export class AudioFormatError extends Error {
  constructor(message) {
    super(message);
    this.name = 'AudioFormatError';
    this.code = 'UNSUPPORTED_AUDIO_FORMAT';
  }
}
