// BHASHINI (MeitY) pipeline client for Santali text-to-speech.
//
// Protocol (from the official BHASHINI API docs, "Pipeline Config Call" and "Pipeline Compute Call"):
//   1. Config call  : POST {configUrl} with headers userID + ulcaApiKey and a pipelineTasks payload.
//                     The response lists the serviceId for the requested language and the
//                     inference endpoint (callbackUrl) plus the auth header (name/value) to use.
//   2. Compute call : POST {callbackUrl} with the auth header and a TTS pipeline payload.
//                     The base64 audio comes back in pipelineResponse[].audio[0].audioContent.
//
// Language: Santali uses the ISO 639-3 code "sat" by default. The config call is the source of
// truth: if BHASHINI does not provision TTS for that code, we fail with MODEL_UNAVAILABLE.
// The app never substitutes another language's model.
//
// SECURITY: userID / ulcaApiKey / inference key values are never logged or returned to clients.

import { decodeAndIdentifyAudio, AudioFormatError } from './audio.mjs';

export const BHASHINI_CONFIG_URL = 'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline';

const DEFAULT_TIMEOUT_MS = 20_000;
const DEFAULT_MAX_ATTEMPTS = 3;
const DEFAULT_CACHE_TTL_MS = 6 * 60 * 60 * 1000;
const BACKOFF_BASE_MS = 400;

/** Error with a stable machine-readable code that the HTTP layer maps to a user message. */
export class BhashiniError extends Error {
  /**
   * @param {'NOT_CONFIGURED'|'AUTH_FAILED'|'SERVICE_UNAVAILABLE'|'TIMEOUT'|'MODEL_UNAVAILABLE'|'GENERATION_FAILED'|'BAD_RESPONSE'|'UNSUPPORTED_AUDIO_FORMAT'} code
   * @param {string} message
   * @param {{retryable?: boolean, status?: number|null}} [options]
   */
  constructor(code, message, { retryable = false, status = null } = {}) {
    super(message);
    this.name = 'BhashiniError';
    this.code = code;
    this.retryable = retryable;
    this.status = status;
  }
}

/**
 * @typedef {object} BhashiniClientOptions
 * @property {string} userId
 * @property {string} ulcaApiKey
 * @property {string} [sourceLanguage='sat']
 * @property {'female'|'male'} [gender='female']
 * @property {number} [samplingRate=22050]
 * @property {string|null} [serviceId=null]  Pin a specific serviceId instead of discovering it.
 * @property {string} [configUrl]
 * @property {number} [timeoutMs]
 * @property {number} [maxAttempts]
 * @property {number} [cacheTtlMs]
 * @property {typeof fetch} [fetchImpl]
 * @property {(ms: number) => Promise<void>} [sleep]
 * @property {{info?: Function, warn?: Function, error?: Function}} [logger]
 * @property {() => number} [now]
 */

export class BhashiniClient {
  /** @param {BhashiniClientOptions} options */
  constructor(options) {
    if (!options || !options.userId || !options.ulcaApiKey) {
      throw new BhashiniError('NOT_CONFIGURED', 'BHASHINI credentials are not configured.');
    }
    this.userId = options.userId;
    this.ulcaApiKey = options.ulcaApiKey;
    this.sourceLanguage = options.sourceLanguage ?? 'sat';
    this.gender = options.gender ?? 'female';
    this.samplingRate = options.samplingRate ?? 22050;
    this.pinnedServiceId = options.serviceId ?? null;
    this.configUrl = options.configUrl ?? BHASHINI_CONFIG_URL;
    this.timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.maxAttempts = Math.max(1, options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS);
    this.cacheTtlMs = options.cacheTtlMs ?? DEFAULT_CACHE_TTL_MS;
    this.fetchImpl = options.fetchImpl ?? globalThis.fetch;
    this.sleep = options.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
    this.logger = options.logger ?? {};
    this.now = options.now ?? (() => Date.now());

    /** @type {null | {serviceId: string, callbackUrl: string, authName: string, authValue: string, expiresAt: number}} */
    this.pipeline = null;
    /** @type {Promise<void> | null} */
    this.discovering = null;
    this.lastSuccessAt = null;
  }

  /** Discover (or reuse cached) TTS pipeline details for the configured language. */
  async discover() {
    if (this.pipeline && this.pipeline.expiresAt > this.now()) {
      return this.pipeline;
    }
    if (!this.discovering) {
      this.discovering = this.#runDiscovery().finally(() => {
        this.discovering = null;
      });
    }
    await this.discovering;
    return this.pipeline;
  }

  /**
   * Convert Ol Chiki text to speech.
   * @param {string} text  Original Ol Chiki text. It is sent unchanged.
   * @returns {Promise<{audioBase64: string, mimeType: string, extension: string, samplingRate: number, serviceId: string}>}
   */
  async synthesize(text) {
    if (typeof text !== 'string' || text.length === 0) {
      throw new BhashiniError('GENERATION_FAILED', 'Text to speak is empty.');
    }
    let authRetried = false;
    let lastError = null;

    for (let attempt = 1; attempt <= this.maxAttempts; attempt += 1) {
      try {
        const pipeline = await this.discover();
        const audio = await this.#compute(pipeline, text);
        this.lastSuccessAt = this.now();
        return { ...audio, serviceId: pipeline.serviceId };
      } catch (error) {
        lastError = error;
        if (!(error instanceof BhashiniError)) {
          // Programming errors must not be hidden behind a retry loop.
          throw error;
        }
        // An expired inference key shows up as 401/403 on compute. Re-discover once.
        if (error.code === 'AUTH_FAILED' && !authRetried && this.pipeline) {
          authRetried = true;
          this.pipeline = null;
          this.#log('warn', 'bhashini_auth_refresh', { attempt });
          continue;
        }
        if (!error.retryable || attempt === this.maxAttempts) {
          throw error;
        }
        this.#log('warn', 'bhashini_retry', { attempt, code: error.code });
        await this.sleep(BACKOFF_BASE_MS * 2 ** (attempt - 1));
      }
    }
    // Unreachable in practice; keeps the function total.
    throw lastError ?? new BhashiniError('SERVICE_UNAVAILABLE', 'Speech service did not respond.', { retryable: true });
  }

  async #runDiscovery() {
    const body = {
      pipelineTasks: [
        {
          taskType: 'tts',
          config: { language: { sourceLanguage: this.sourceLanguage } },
        },
      ],
    };
    const json = await this.#postJson(
      this.configUrl,
      body,
      { userID: this.userId, ulcaApiKey: this.ulcaApiKey },
      'config',
    );

    const configs = Array.isArray(json?.pipelineResponseConfig)
      ? json.pipelineResponseConfig.find((entry) => entry?.taskType === 'tts')?.config
      : null;
    if (!Array.isArray(configs) || configs.length === 0) {
      throw new BhashiniError(
        'MODEL_UNAVAILABLE',
        `BHASHINI does not currently provide text-to-speech for language code "${this.sourceLanguage}".`,
      );
    }

    const match = configs.find((c) => {
      if (this.pinnedServiceId) {
        return c?.serviceId === this.pinnedServiceId;
      }
      return c?.language?.sourceLanguage === this.sourceLanguage;
    });
    if (!match || typeof match.serviceId !== 'string' || match.serviceId.length === 0) {
      throw new BhashiniError(
        'MODEL_UNAVAILABLE',
        this.pinnedServiceId
          ? 'The configured BHASHINI TTS service is not available on this pipeline.'
          : `No BHASHINI TTS model matched language code "${this.sourceLanguage}".`,
      );
    }

    const endpoint = json.pipelineInferenceAPIEndPoint;
    const callbackUrl = endpoint?.callbackUrl;
    const authName = endpoint?.inferenceApiKey?.name;
    const authValue = endpoint?.inferenceApiKey?.value;
    if (
      typeof callbackUrl !== 'string' ||
      !callbackUrl.startsWith('https://') ||
      typeof authName !== 'string' ||
      typeof authValue !== 'string' ||
      authValue.length === 0
    ) {
      throw new BhashiniError('BAD_RESPONSE', 'BHASHINI config response did not contain an inference endpoint.', {
        retryable: true,
      });
    }

    this.pipeline = {
      serviceId: match.serviceId,
      callbackUrl,
      authName,
      authValue,
      expiresAt: this.now() + this.cacheTtlMs,
    };
    this.#log('info', 'bhashini_pipeline_discovered', { serviceId: match.serviceId, language: this.sourceLanguage });
  }

  async #compute(pipeline, text) {
    const body = {
      pipelineTasks: [
        {
          taskType: 'tts',
          config: {
            language: { sourceLanguage: this.sourceLanguage },
            serviceId: pipeline.serviceId,
            gender: this.gender,
            samplingRate: this.samplingRate,
          },
        },
      ],
      inputData: {
        input: [{ source: text }],
      },
    };

    const json = await this.#postJson(
      pipeline.callbackUrl,
      body,
      { [pipeline.authName]: pipeline.authValue },
      'compute',
    );

    const ttsEntry = Array.isArray(json?.pipelineResponse)
      ? json.pipelineResponse.find((entry) => entry?.taskType === 'tts')
      : null;
    const audioContent = ttsEntry?.audio?.[0]?.audioContent;
    if (typeof audioContent !== 'string' || audioContent.length === 0) {
      throw new BhashiniError('GENERATION_FAILED', 'The speech engine returned no audio for this text.', {
        retryable: true,
      });
    }

    let identified;
    try {
      identified = decodeAndIdentifyAudio(audioContent);
    } catch (error) {
      if (error instanceof AudioFormatError) {
        throw new BhashiniError('UNSUPPORTED_AUDIO_FORMAT', error.message);
      }
      throw error;
    }

    return {
      audioBase64: audioContent,
      mimeType: identified.mimeType,
      extension: identified.extension,
      samplingRate: this.samplingRate,
    };
  }

  /**
   * POST JSON and translate transport/HTTP failures into BhashiniError.
   * @param {string} url
   * @param {unknown} body
   * @param {Record<string,string>} headers
   * @param {'config'|'compute'} phase
   */
  async #postJson(url, body, headers, phase) {
    let response;
    try {
      response = await this.fetchImpl(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(this.timeoutMs),
        redirect: 'error',
      });
    } catch (error) {
      if (error?.name === 'TimeoutError' || error?.name === 'AbortError') {
        this.#log('warn', 'bhashini_timeout', { phase });
        throw new BhashiniError('TIMEOUT', 'The speech service took too long to respond.', { retryable: true });
      }
      this.#log('warn', 'bhashini_network_error', { phase, errorName: error?.name ?? 'unknown' });
      throw new BhashiniError('SERVICE_UNAVAILABLE', 'Could not reach the speech service.', { retryable: true });
    }

    const status = response.status;
    if (status === 401 || status === 403) {
      this.#log('warn', 'bhashini_auth_rejected', { phase, status });
      throw new BhashiniError('AUTH_FAILED', 'The speech service rejected the server credentials.', { status });
    }
    if (status === 429 || status >= 500) {
      this.#log('warn', 'bhashini_unavailable', { phase, status });
      throw new BhashiniError('SERVICE_UNAVAILABLE', 'The speech service is busy or unavailable.', {
        retryable: true,
        status,
      });
    }
    if (status >= 400) {
      this.#log('warn', 'bhashini_rejected_request', { phase, status });
      throw new BhashiniError('GENERATION_FAILED', 'The speech service could not process this text.', { status });
    }

    let json;
    try {
      json = await response.json();
    } catch {
      throw new BhashiniError('BAD_RESPONSE', 'The speech service returned an unreadable response.', {
        retryable: true,
        status,
      });
    }
    return json;
  }

  #log(level, msg, fields = {}) {
    const fn = this.logger[level];
    if (typeof fn === 'function') {
      fn({ msg, ...fields });
    }
  }
}
