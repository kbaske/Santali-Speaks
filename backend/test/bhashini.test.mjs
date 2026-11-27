// Unit tests for the BHASHINI client using an injected fake transport.
// These verify our request/response handling ONLY. They are not evidence that BHASHINI
// produces Santali speech; use scripts/verify-santali-tts.mjs with real credentials for that.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { BhashiniClient, BhashiniError, BHASHINI_CONFIG_URL } from '../src/bhashini.mjs';

const WAV_B64 = Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WAVE'), Buffer.alloc(32)]).toString('base64');
const CALLBACK = 'https://dhruva-api.bhashini.gov.in/services/inference/pipeline';

function configResponse({ sourceLanguage = 'sat', serviceId = 'svc-sat-tts' } = {}) {
  return {
    pipelineResponseConfig: [
      {
        taskType: 'tts',
        config: [{ serviceId, modelId: 'm1', language: { sourceLanguage } }],
      },
    ],
    pipelineInferenceAPIEndPoint: {
      callbackUrl: CALLBACK,
      inferenceApiKey: { name: 'Authorization', value: 'INFERENCE-KEY' },
      isSyncApi: true,
    },
  };
}

function computeResponse(audio = WAV_B64) {
  return { pipelineResponse: [{ taskType: 'tts', config: {}, output: null, audio: [{ audioContent: audio }] }] };
}

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** Scripted fake transport: each call consumes the next scripted response. */
function fakeFetch(script) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init, body: init?.body ? JSON.parse(init.body) : null });
    const next = script.shift();
    if (!next) throw new Error('unexpected extra fetch call');
    if (next instanceof Error) throw next;
    return next;
  };
  fn.calls = calls;
  return fn;
}

function client(fetchImpl, extra = {}) {
  return new BhashiniClient({
    userId: 'user-1',
    ulcaApiKey: 'ulca-key',
    fetchImpl,
    sleep: async () => {},
    ...extra,
  });
}

test('constructor refuses missing credentials', () => {
  assert.throws(() => new BhashiniClient({ userId: '', ulcaApiKey: '' }), (e) => e.code === 'NOT_CONFIGURED');
});

test('discovers the Santali TTS serviceId then synthesizes, sending text unchanged', async () => {
  const text = 'ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹ, ᱟᱯᱱᱟᱨ ᱯᱚᱛᱚᱵ ᱡᱷᱤᱡᱽ ᱢᱮ';
  const f = fakeFetch([jsonResponse(200, configResponse()), jsonResponse(200, computeResponse())]);
  const result = await client(f).synthesize(text);

  assert.equal(result.mimeType, 'audio/wav');
  assert.equal(result.serviceId, 'svc-sat-tts');
  assert.equal(result.audioBase64, WAV_B64);

  const [configCall, computeCall] = f.calls;
  assert.equal(configCall.url, BHASHINI_CONFIG_URL);
  assert.equal(configCall.init.headers.userID, 'user-1');
  assert.equal(configCall.init.headers.ulcaApiKey, 'ulca-key');
  assert.equal(configCall.body.pipelineTasks[0].taskType, 'tts');
  assert.equal(configCall.body.pipelineTasks[0].config.language.sourceLanguage, 'sat');

  assert.equal(computeCall.url, CALLBACK);
  assert.equal(computeCall.init.headers.Authorization, 'INFERENCE-KEY');
  const task = computeCall.body.pipelineTasks[0];
  assert.equal(task.taskType, 'tts');
  assert.equal(task.config.serviceId, 'svc-sat-tts');
  assert.equal(task.config.language.sourceLanguage, 'sat');
  assert.equal(computeCall.body.inputData.input[0].source, text, 'Ol Chiki text must be forwarded byte-for-byte');
});

test('reuses cached pipeline on subsequent calls', async () => {
  const f = fakeFetch([jsonResponse(200, configResponse()), jsonResponse(200, computeResponse()), jsonResponse(200, computeResponse())]);
  const c = client(f);
  await c.synthesize('ᱡᱚᱦᱟᱨ');
  await c.synthesize('ᱡᱚᱦᱟᱨ');
  assert.equal(f.calls.length, 3, 'one config call + two compute calls');
});

test('MODEL_UNAVAILABLE when BHASHINI has no TTS config for the language', async () => {
  const f = fakeFetch([jsonResponse(200, { pipelineResponseConfig: [{ taskType: 'tts', config: [] }] })]);
  await assert.rejects(client(f).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e instanceof BhashiniError && e.code === 'MODEL_UNAVAILABLE');
});

test('MODEL_UNAVAILABLE when the response has no tts task at all', async () => {
  const f = fakeFetch([jsonResponse(200, { pipelineResponseConfig: [] })]);
  await assert.rejects(client(f).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'MODEL_UNAVAILABLE');
});

test('a pinned serviceId must exist in the discovered list', async () => {
  const f = fakeFetch([jsonResponse(200, configResponse({ serviceId: 'other' }))]);
  await assert.rejects(client(f, { serviceId: 'pinned-id' }).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'MODEL_UNAVAILABLE');
});

test('rejects a config response without an https inference endpoint', async () => {
  const bad = configResponse();
  bad.pipelineInferenceAPIEndPoint.callbackUrl = 'http://insecure.example';
  const f = fakeFetch([jsonResponse(200, bad)]);
  await assert.rejects(client(f, { maxAttempts: 1 }).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'BAD_RESPONSE');
});

test('401 on config is AUTH_FAILED and not retried', async () => {
  const f = fakeFetch([jsonResponse(401, {})]);
  await assert.rejects(client(f).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'AUTH_FAILED');
  assert.equal(f.calls.length, 1);
});

test('expired inference key on compute triggers one re-discovery, then succeeds', async () => {
  const f = fakeFetch([
    jsonResponse(200, configResponse()),
    jsonResponse(403, {}),
    jsonResponse(200, configResponse()),
    jsonResponse(200, computeResponse()),
  ]);
  const r = await client(f).synthesize('ᱡᱚᱦᱟᱨ');
  assert.equal(r.mimeType, 'audio/wav');
  assert.equal(f.calls.length, 4);
});

test('5xx is retried with backoff up to maxAttempts, then SERVICE_UNAVAILABLE', async () => {
  const sleeps = [];
  const f = fakeFetch([
    jsonResponse(200, configResponse()),
    jsonResponse(503, {}),
    jsonResponse(503, {}),
    jsonResponse(503, {}),
  ]);
  const c = client(f, { sleep: async (ms) => sleeps.push(ms), maxAttempts: 3 });
  await assert.rejects(c.synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'SERVICE_UNAVAILABLE' && e.retryable === true);
  assert.deepEqual(sleeps, [400, 800]);
});

test('a transient failure followed by success is recovered', async () => {
  const f = fakeFetch([
    jsonResponse(200, configResponse()),
    new TypeError('fetch failed'),
    jsonResponse(200, computeResponse()),
  ]);
  const r = await client(f).synthesize('ᱡᱚᱦᱟᱨ');
  assert.equal(r.mimeType, 'audio/wav');
});

test('timeouts are reported as TIMEOUT and are retryable', async () => {
  const timeout = Object.assign(new Error('timed out'), { name: 'TimeoutError' });
  const f = fakeFetch([jsonResponse(200, configResponse()), timeout, timeout, timeout]);
  await assert.rejects(client(f).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'TIMEOUT' && e.retryable === true);
});

test('400 from compute is GENERATION_FAILED and not retried', async () => {
  const f = fakeFetch([jsonResponse(200, configResponse()), jsonResponse(400, { message: 'bad input' })]);
  await assert.rejects(client(f).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'GENERATION_FAILED' && e.retryable === false);
  assert.equal(f.calls.length, 2);
});

test('empty audio is GENERATION_FAILED', async () => {
  const f = fakeFetch([jsonResponse(200, configResponse()), jsonResponse(200, computeResponse(''))]);
  await assert.rejects(client(f, { maxAttempts: 1 }).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'GENERATION_FAILED');
});

test('unknown audio format is UNSUPPORTED_AUDIO_FORMAT', async () => {
  const junk = Buffer.alloc(64, 0x41).toString('base64');
  const f = fakeFetch([jsonResponse(200, configResponse()), jsonResponse(200, computeResponse(junk))]);
  await assert.rejects(client(f).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'UNSUPPORTED_AUDIO_FORMAT');
});

test('unreadable JSON is BAD_RESPONSE', async () => {
  const f = fakeFetch([new Response('<html>oops</html>', { status: 200 })]);
  await assert.rejects(client(f, { maxAttempts: 1 }).synthesize('ᱡᱚᱦᱟᱨ'), (e) => e.code === 'BAD_RESPONSE');
});

test('logs never contain the text or credentials', async () => {
  const lines = [];
  const logger = { info: (f) => lines.push(JSON.stringify(f)), warn: (f) => lines.push(JSON.stringify(f)) };
  const f = fakeFetch([jsonResponse(200, configResponse()), jsonResponse(200, computeResponse())]);
  await client(f, { logger }).synthesize('ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹ');
  const joined = lines.join('\n');
  assert.ok(!joined.includes('ᱡᱚᱦᱟᱨ'), 'text must not be logged');
  assert.ok(!joined.includes('ulca-key'), 'ULCA key must not be logged');
  assert.ok(!joined.includes('INFERENCE-KEY'), 'inference key must not be logged');
});
