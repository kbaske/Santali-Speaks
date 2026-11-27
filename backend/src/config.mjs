// Environment configuration with strict validation. Invalid values fail fast at startup
// with a message that names the variable, instead of failing later on a user request.

const DEFAULTS = Object.freeze({
  PORT: 8080,
  HOST: '0.0.0.0',
  BHASHINI_SOURCE_LANGUAGE: 'sat',
  BHASHINI_TTS_GENDER: 'female',
  BHASHINI_SAMPLING_RATE: 22050,
  BHASHINI_TIMEOUT_MS: 20000,
  RATE_LIMIT_PER_MINUTE: 30,
});

/**
 * @param {Record<string, string|undefined>} env
 * @returns {{
 *   port: number,
 *   host: string,
 *   bhashini: {
 *     configured: boolean,
 *     userId: string,
 *     ulcaApiKey: string,
 *     sourceLanguage: string,
 *     gender: 'female'|'male',
 *     samplingRate: number,
 *     serviceId: string|null,
 *     timeoutMs: number,
 *   },
 *   rateLimitPerMinute: number,
 *   websiteDir: string|null,
 *   trustProxy: boolean,
 * }}
 */
export function loadConfig(env = process.env) {
  const errors = [];

  const port = parseIntEnv(env, 'PORT', DEFAULTS.PORT, 1, 65535, errors);
  const host = (env.HOST || DEFAULTS.HOST).trim();

  const userId = (env.BHASHINI_USER_ID || '').trim();
  const ulcaApiKey = (env.BHASHINI_ULCA_API_KEY || '').trim();
  const configured = userId.length > 0 && ulcaApiKey.length > 0;
  if (!configured && (userId.length > 0 || ulcaApiKey.length > 0)) {
    errors.push('BHASHINI_USER_ID and BHASHINI_ULCA_API_KEY must both be set (or both left empty).');
  }

  const sourceLanguage = (env.BHASHINI_SOURCE_LANGUAGE || DEFAULTS.BHASHINI_SOURCE_LANGUAGE).trim();
  if (!/^[a-z]{2,3}$/.test(sourceLanguage)) {
    errors.push('BHASHINI_SOURCE_LANGUAGE must be an ISO 639 language code such as "sat".');
  }

  const gender = (env.BHASHINI_TTS_GENDER || DEFAULTS.BHASHINI_TTS_GENDER).trim();
  if (gender !== 'female' && gender !== 'male') {
    errors.push('BHASHINI_TTS_GENDER must be "female" or "male".');
  }

  const samplingRate = parseIntEnv(env, 'BHASHINI_SAMPLING_RATE', DEFAULTS.BHASHINI_SAMPLING_RATE, 8000, 48000, errors);
  const timeoutMs = parseIntEnv(env, 'BHASHINI_TIMEOUT_MS', DEFAULTS.BHASHINI_TIMEOUT_MS, 1000, 120000, errors);
  const rateLimitPerMinute = parseIntEnv(env, 'RATE_LIMIT_PER_MINUTE', DEFAULTS.RATE_LIMIT_PER_MINUTE, 1, 10000, errors);

  const serviceIdRaw = (env.BHASHINI_TTS_SERVICE_ID || '').trim();
  const serviceId = serviceIdRaw.length > 0 ? serviceIdRaw : null;

  const websiteDir = (env.WEBSITE_DIR || '').trim() || null;

  const trustProxyRaw = (env.TRUST_PROXY || 'false').trim().toLowerCase();
  if (trustProxyRaw !== 'true' && trustProxyRaw !== 'false') {
    errors.push('TRUST_PROXY must be "true" or "false".');
  }
  const trustProxy = trustProxyRaw === 'true';

  if (errors.length > 0) {
    throw new ConfigError(errors);
  }

  return {
    port,
    host,
    bhashini: {
      configured,
      userId,
      ulcaApiKey,
      sourceLanguage,
      gender,
      samplingRate,
      serviceId,
      timeoutMs,
    },
    rateLimitPerMinute,
    websiteDir,
    trustProxy,
  };
}

export class ConfigError extends Error {
  /** @param {string[]} problems */
  constructor(problems) {
    super(`Invalid configuration:\n - ${problems.join('\n - ')}`);
    this.name = 'ConfigError';
    this.problems = problems;
  }
}

function parseIntEnv(env, name, fallback, min, max, errors) {
  const raw = env[name];
  if (raw === undefined || raw.trim() === '') {
    return fallback;
  }
  const value = Number(raw.trim());
  if (!Number.isInteger(value) || value < min || value > max) {
    errors.push(`${name} must be an integer between ${min} and ${max}.`);
    return fallback;
  }
  return value;
}
