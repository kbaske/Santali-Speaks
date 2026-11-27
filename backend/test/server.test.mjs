import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, mkdir, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createServer } from '../src/server.mjs';
import { BhashiniError } from '../src/bhashini.mjs';

const WAV_B64 = Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WAVE'), Buffer.alloc(32)]).toString('base64');
const OLCHIKI = 'ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹ';

let siteDir;
before(async () => {
  siteDir = await mkdtemp(path.join(os.tmpdir(), 'site-'));
  await writeFile(path.join(siteDir, 'index.html'), '<!doctype html><title>home</title>');
  await writeFile(path.join(siteDir, '404.html'), '<!doctype html><title>missing</title>');
  await mkdir(path.join(siteDir, 'privacy'));
  await writeFile(path.join(siteDir, 'privacy', 'index.html'), '<title>privacy</title>');
  await writeFile(path.join(siteDir, 'app.js'), 'console.log(1)');
});
after(async () => {
  await rm(siteDir, { recursive: true, force: true });
});

function baseConfig(overrides = {}) {
  return {
    bhashini: { configured: true, sourceLanguage: 'sat' },
    rateLimitPerMinute: 1000,
    websiteDir: siteDir,
    trustProxy: false,
    ...overrides,
  };
}

async function start({ speech, config } = {}) {
  const speechEngine =
    speech === undefined
      ? { synthesize: async (text) => ({ audioBase64: WAV_B64, mimeType: 'audio/wav', extension: 'wav', samplingRate: 22050, text: undefined }) }
      : speech;
  const server = createServer({ config: config ?? baseConfig(), speech: speechEngine, logger: {} });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  return { server, base, close: () => new Promise((r) => server.close(r)) };
}

async function post(base, body, headers = { 'Content-Type': 'application/json' }) {
  const res = await fetch(`${base}/api/v1/tts`, {
    method: 'POST',
    headers,
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
  return { status: res.status, headers: res.headers, json: await res.json().catch(() => null) };
}

test('health and status endpoints', async () => {
  const s = await start();
  try {
    const h = await fetch(`${s.base}/api/health`);
    assert.equal(h.status, 200);
    assert.equal((await h.json()).status, 'ok');

    const st = await fetch(`${s.base}/api/status`);
    const body = await st.json();
    assert.equal(st.status, 200);
    assert.equal(body.speechConfigured, true);
    assert.equal(body.language, 'sat');
    assert.equal(body.script, 'Ol Chiki');
    assert.equal(JSON.stringify(body).includes('KEY'), false);
  } finally {
    await s.close();
  }
});

test('TTS success returns audio and does not echo the text', async () => {
  let received = null;
  const s = await start({
    speech: {
      synthesize: async (text) => {
        received = text;
        return { audioBase64: WAV_B64, mimeType: 'audio/wav', extension: 'wav', samplingRate: 22050 };
      },
    },
  });
  try {
    const r = await post(s.base, { text: OLCHIKI, language: 'sat' });
    assert.equal(r.status, 200);
    assert.equal(r.json.audioBase64, WAV_B64);
    assert.equal(r.json.mimeType, 'audio/wav');
    assert.equal(r.json.textLength, OLCHIKI.length);
    assert.equal(received, OLCHIKI, 'the original Ol Chiki text is passed through unchanged');
    assert.equal(JSON.stringify(r.json).includes('ᱡᱚᱦᱟᱨ'), false);
    assert.equal(r.headers.get('cache-control'), 'no-store');
    assert.equal(r.headers.get('x-content-type-options'), 'nosniff');
  } finally {
    await s.close();
  }
});

test('wrong content type is rejected with 415', async () => {
  const s = await start();
  try {
    const r = await post(s.base, `{"text":"${OLCHIKI}"}`, { 'Content-Type': 'text/plain' });
    assert.equal(r.status, 415);
    assert.equal(r.json.error.code, 'UNSUPPORTED_MEDIA_TYPE');
  } finally {
    await s.close();
  }
});

test('invalid JSON is rejected with 400 and no stack trace', async () => {
  const s = await start();
  try {
    const r = await post(s.base, '{not json');
    assert.equal(r.status, 400);
    assert.equal(r.json.error.code, 'INVALID_JSON');
    assert.equal(JSON.stringify(r.json).includes('at '), false);
  } finally {
    await s.close();
  }
});

test('non-object bodies are rejected', async () => {
  const s = await start();
  try {
    assert.equal((await post(s.base, '[1,2]')).status, 400);
    assert.equal((await post(s.base, 'null')).status, 400);
  } finally {
    await s.close();
  }
});

test('oversized bodies are rejected with 413', async () => {
  const s = await start();
  try {
    const big = JSON.stringify({ text: 'ᱡ'.repeat(70_000) });
    const r = await post(s.base, big);
    assert.equal(r.status, 413);
    assert.equal(r.json.error.code, 'PAYLOAD_TOO_LARGE');
  } finally {
    await s.close();
  }
});

test('text validation errors map to 400 and 422 with stable codes', async () => {
  const s = await start();
  try {
    const empty = await post(s.base, { text: '' });
    assert.equal(empty.status, 400);
    assert.equal(empty.json.error.code, 'INVALID_TEXT');

    const latin = await post(s.base, { text: 'hello' });
    assert.equal(latin.status, 422);
    assert.equal(latin.json.error.code, 'UNSUPPORTED_INPUT');

    const missing = await post(s.base, {});
    assert.equal(missing.status, 400);

    const otherLang = await post(s.base, { text: OLCHIKI, language: 'hi' });
    assert.equal(otherLang.status, 400);
    assert.equal(otherLang.json.error.code, 'UNSUPPORTED_LANGUAGE');
  } finally {
    await s.close();
  }
});

test('not configured returns 503 SERVICE_NOT_CONFIGURED', async () => {
  const s = await start({ speech: null, config: baseConfig({ bhashini: { configured: false, sourceLanguage: 'sat' } }) });
  try {
    const r = await post(s.base, { text: OLCHIKI });
    assert.equal(r.status, 503);
    assert.equal(r.json.error.code, 'SERVICE_NOT_CONFIGURED');
    assert.equal(r.json.error.retryable, false);
  } finally {
    await s.close();
  }
});

test('BHASHINI errors map to documented status codes and retryable flags', async () => {
  const cases = [
    ['AUTH_FAILED', 502, 'AUTHENTICATION_FAILED', false],
    ['SERVICE_UNAVAILABLE', 503, 'SERVICE_UNAVAILABLE', true],
    ['TIMEOUT', 504, 'TIMEOUT', true],
    ['MODEL_UNAVAILABLE', 503, 'MODEL_UNAVAILABLE', false],
    ['UNSUPPORTED_AUDIO_FORMAT', 502, 'UNSUPPORTED_AUDIO_FORMAT', false],
    ['GENERATION_FAILED', 422, 'GENERATION_FAILED', false],
    ['BAD_RESPONSE', 502, 'BAD_RESPONSE', true],
  ];
  for (const [code, status, expectedCode, retryable] of cases) {
    const s = await start({
      speech: {
        synthesize: async () => {
          throw new BhashiniError(code, 'internal detail that must not leak', { retryable });
        },
      },
    });
    try {
      const r = await post(s.base, { text: OLCHIKI });
      assert.equal(r.status, status, code);
      assert.equal(r.json.error.code, expectedCode, code);
      assert.equal(r.json.error.retryable, retryable, code);
      assert.equal(JSON.stringify(r.json).includes('must not leak'), false, code);
    } finally {
      await s.close();
    }
  }
});

test('unexpected engine errors become a generic 500 without leaking details', async () => {
  const s = await start({
    speech: {
      synthesize: async () => {
        throw new Error('database password is hunter2');
      },
    },
  });
  try {
    const r = await post(s.base, { text: OLCHIKI });
    assert.equal(r.status, 500);
    assert.equal(r.json.error.code, 'INTERNAL_ERROR');
    assert.equal(JSON.stringify(r.json).includes('hunter2'), false);
  } finally {
    await s.close();
  }
});

test('rate limiting returns 429 with Retry-After', async () => {
  const s = await start({ config: baseConfig({ rateLimitPerMinute: 2 }) });
  try {
    assert.equal((await post(s.base, { text: OLCHIKI })).status, 200);
    assert.equal((await post(s.base, { text: OLCHIKI })).status, 200);
    const r = await post(s.base, { text: OLCHIKI });
    assert.equal(r.status, 429);
    assert.equal(r.json.error.code, 'RATE_LIMITED');
    assert.ok(Number(r.headers.get('retry-after')) >= 1);
  } finally {
    await s.close();
  }
});

test('GET on the TTS endpoint is 405 with Allow header', async () => {
  const s = await start();
  try {
    const r = await fetch(`${s.base}/api/v1/tts`);
    assert.equal(r.status, 405);
    assert.equal(r.headers.get('allow'), 'POST');
  } finally {
    await s.close();
  }
});

test('unknown API route is 404 JSON', async () => {
  const s = await start();
  try {
    const r = await fetch(`${s.base}/api/nope`);
    assert.equal(r.status, 404);
    assert.equal((await r.json()).error.code, 'NOT_FOUND');
  } finally {
    await s.close();
  }
});

test('static site: index, nested index, assets and 404 page', async () => {
  const s = await start();
  try {
    const home = await fetch(`${s.base}/`);
    assert.equal(home.status, 200);
    assert.match(home.headers.get('content-type'), /text\/html/);
    assert.match(home.headers.get('content-security-policy'), /default-src 'self'/);
    assert.match(await home.text(), /home/);

    const nested = await fetch(`${s.base}/privacy/`);
    assert.equal(nested.status, 200);
    assert.match(await nested.text(), /privacy/);

    const js = await fetch(`${s.base}/app.js`);
    assert.match(js.headers.get('content-type'), /javascript/);

    const missing = await fetch(`${s.base}/nope`);
    assert.equal(missing.status, 404);
    assert.match(await missing.text(), /missing/);
  } finally {
    await s.close();
  }
});

test('path traversal is blocked', async () => {
  const s = await start();
  try {
    const r = await fetch(`${s.base}/..%2F..%2Fetc%2Fpasswd`);
    assert.notEqual(r.status, 200);
    const r2 = await fetch(`${s.base}/%2e%2e/%2e%2e/etc/passwd`);
    assert.notEqual(r2.status, 200);
  } finally {
    await s.close();
  }
});

test('malformed percent-encoding returns 400', async () => {
  const s = await start();
  try {
    const r = await fetch(`${s.base}/%E0%A4%A`);
    assert.equal(r.status, 400);
  } finally {
    await s.close();
  }
});

test('non-GET methods on static pages are rejected', async () => {
  const s = await start();
  try {
    const r = await fetch(`${s.base}/`, { method: 'POST', body: 'x' });
    assert.equal(r.status, 405);
  } finally {
    await s.close();
  }
});

test('HEAD on a static page returns headers without a body', async () => {
  const s = await start();
  try {
    const r = await fetch(`${s.base}/`, { method: 'HEAD' });
    assert.equal(r.status, 200);
    assert.equal(await r.text(), '');
  } finally {
    await s.close();
  }
});
