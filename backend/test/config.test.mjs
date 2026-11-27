import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadConfig, ConfigError } from '../src/config.mjs';

test('defaults are safe and speech is not configured without credentials', () => {
  const c = loadConfig({});
  assert.equal(c.port, 8080);
  assert.equal(c.bhashini.configured, false);
  assert.equal(c.bhashini.sourceLanguage, 'sat');
  assert.equal(c.trustProxy, false);
  assert.equal(c.websiteDir, null);
});

test('credentials are read when both are provided', () => {
  const c = loadConfig({ BHASHINI_USER_ID: ' u ', BHASHINI_ULCA_API_KEY: ' k ' });
  assert.equal(c.bhashini.configured, true);
  assert.equal(c.bhashini.userId, 'u');
  assert.equal(c.bhashini.ulcaApiKey, 'k');
});

test('half-configured credentials are rejected with a clear message', () => {
  assert.throws(() => loadConfig({ BHASHINI_USER_ID: 'u' }), (e) => e instanceof ConfigError && /both/.test(e.message));
});

test('invalid numbers name the variable', () => {
  assert.throws(() => loadConfig({ PORT: 'abc' }), (e) => /PORT/.test(e.message));
  assert.throws(() => loadConfig({ PORT: '70000' }), (e) => /PORT/.test(e.message));
  assert.throws(() => loadConfig({ BHASHINI_SAMPLING_RATE: '100' }), (e) => /BHASHINI_SAMPLING_RATE/.test(e.message));
  assert.throws(() => loadConfig({ RATE_LIMIT_PER_MINUTE: '0' }), (e) => /RATE_LIMIT_PER_MINUTE/.test(e.message));
});

test('multiple problems are reported together', () => {
  try {
    loadConfig({ PORT: 'x', TRUST_PROXY: 'yes', BHASHINI_TTS_GENDER: 'robot' });
    assert.fail('expected ConfigError');
  } catch (e) {
    assert.ok(e instanceof ConfigError);
    assert.equal(e.problems.length, 3);
  }
});

test('language code and gender are validated', () => {
  assert.throws(() => loadConfig({ BHASHINI_SOURCE_LANGUAGE: 'Santali!' }), /BHASHINI_SOURCE_LANGUAGE/);
  assert.throws(() => loadConfig({ BHASHINI_TTS_GENDER: 'other' }), /BHASHINI_TTS_GENDER/);
});
