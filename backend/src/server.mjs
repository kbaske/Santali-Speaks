// HTTP server for Santali Speaks.
//
//   POST /api/v1/tts     Ol Chiki text in, base64 audio out. Used by the Android app.
//   GET  /api/health     Liveness probe.
//   GET  /api/status     Non-sensitive configuration status (no credentials, no text).
//   GET  /*              Static website (speaks.santals.in), when a website directory exists.
//
// Every failure path returns a JSON error body {error:{code,message,retryable}} with no stack trace.

import { createReadStream, promises as fsp } from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { analyzeSantaliText } from './olchiki.mjs';
import { BhashiniError } from './bhashini.mjs';
import { RateLimiter } from './ratelimit.mjs';

export const SERVICE_NAME = 'santali-speaks-api';
export const API_VERSION = '1';
export const MAX_BODY_BYTES = 64 * 1024;
const SUPPORTED_LANGUAGE = 'sat';

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8',
  '.webmanifest': 'application/manifest+json',
};

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'X-Frame-Options': 'DENY',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
};

/**
 * @typedef {object} SpeechEngine
 * @property {(text: string) => Promise<{audioBase64: string, mimeType: string, extension: string, samplingRate: number}>} synthesize
 */

/**
 * @param {object} options
 * @param {{bhashini: {configured: boolean, sourceLanguage: string}, rateLimitPerMinute: number, websiteDir: string, trustProxy?: boolean}} options.config
 * @param {SpeechEngine | null} options.speech  null when BHASHINI credentials are not configured.
 * @param {{info?: Function, warn?: Function, error?: Function}} [options.logger]
 * @param {() => number} [options.now]
 * @returns {http.Server}
 */
export function createServer({ config, speech, logger = {}, now = () => Date.now() }) {
  const rateLimiter = new RateLimiter({ limit: config.rateLimitPerMinute, now });
  const websiteRoot = path.resolve(config.websiteDir);
  const started = now();
  const stats = { requests: 0, ttsOk: 0, ttsFailed: 0, lastTtsSuccessAt: null };

  const log = (level, msg, fields = {}) => {
    const fn = logger[level];
    if (typeof fn === 'function') {
      fn({ msg, ...fields });
    }
  };

  const server = http.createServer(async (req, res) => {
    const requestStarted = now();
    stats.requests += 1;
    res.on('finish', () => {
      log('info', 'request', {
        method: req.method,
        path: safePathForLog(req.url),
        status: res.statusCode,
        durationMs: now() - requestStarted,
      });
    });

    for (const [name, value] of Object.entries(SECURITY_HEADERS)) {
      res.setHeader(name, value);
    }

    try {
      const url = new URL(req.url ?? '/', 'http://localhost');
      const pathname = url.pathname;

      if (pathname.startsWith('/api/')) {
        await routeApi(req, res, url, { config, speech, rateLimiter, stats, now, started, log });
        return;
      }
      if (req.method !== 'GET' && req.method !== 'HEAD') {
        sendError(res, 405, 'METHOD_NOT_ALLOWED', 'Only GET and HEAD are allowed for this page.', false);
        return;
      }
      await serveStatic(req, res, pathname, websiteRoot);
    } catch (error) {
      // Last line of defence: never leak internals to the client.
      log('error', 'unhandled_request_error', { errorName: error?.name ?? 'unknown' });
      if (!res.headersSent) {
        sendError(res, 500, 'INTERNAL_ERROR', 'Something went wrong on the server. Please try again.', true);
      } else {
        res.end();
      }
    }
  });

  server.requestTimeout = 60_000;
  server.headersTimeout = 15_000;
  server.keepAliveTimeout = 5_000;
  return server;
}

async function routeApi(req, res, url, ctx) {
  const { config, speech, rateLimiter, stats, now, started, log } = ctx;
  const pathname = url.pathname;

  if (pathname === '/api/health') {
    return methodOnly(req, res, 'GET', () => sendJson(res, 200, { status: 'ok', service: SERVICE_NAME, apiVersion: API_VERSION }));
  }

  if (pathname === '/api/status') {
    return methodOnly(req, res, 'GET', () =>
      sendJson(res, 200, {
        service: SERVICE_NAME,
        apiVersion: API_VERSION,
        language: SUPPORTED_LANGUAGE,
        script: 'Ol Chiki',
        engine: 'BHASHINI',
        speechConfigured: Boolean(speech),
        uptimeSeconds: Math.floor((now() - started) / 1000),
        lastSpeechSuccessAt: stats.lastTtsSuccessAt ? new Date(stats.lastTtsSuccessAt).toISOString() : null,
      }),
    );
  }

  if (pathname === '/api/v1/tts') {
    if (req.method !== 'POST') {
      return sendError(res, 405, 'METHOD_NOT_ALLOWED', 'Use POST for speech requests.', false, { Allow: 'POST' });
    }
    return handleTts(req, res, { config, speech, rateLimiter, stats, now, log });
  }

  return sendError(res, 404, 'NOT_FOUND', 'Unknown API endpoint.', false);
}

async function handleTts(req, res, { config, speech, rateLimiter, stats, now, log }) {
  // 1. Client address and rate limit.
  const clientKey = clientAddress(req, config.trustProxy === true);
  const limit = rateLimiter.hit(clientKey);
  if (!limit.allowed) {
    return sendError(
      res,
      429,
      'RATE_LIMITED',
      'Too many requests. Please wait a moment and try again.',
      true,
      { 'Retry-After': String(limit.retryAfterSeconds) },
    );
  }

  // 2. Content type.
  const contentType = (req.headers['content-type'] ?? '').toLowerCase();
  if (!contentType.startsWith('application/json')) {
    return sendError(res, 415, 'UNSUPPORTED_MEDIA_TYPE', 'Send the request as application/json.', false);
  }

  // 3. Body size and JSON.
  let body;
  try {
    body = await readJsonBody(req, MAX_BODY_BYTES);
  } catch (error) {
    if (error.code === 'BODY_TOO_LARGE') {
      return sendError(res, 413, 'PAYLOAD_TOO_LARGE', 'The text is too large for one request.', false);
    }
    return sendError(res, 400, 'INVALID_JSON', 'The request body is not valid JSON.', false);
  }
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    return sendError(res, 400, 'INVALID_TEXT', 'The request must be a JSON object with a "text" field.', false);
  }

  // 4. Language, if supplied, must be Santali.
  if (body.language !== undefined && body.language !== SUPPORTED_LANGUAGE) {
    return sendError(
      res,
      400,
      'UNSUPPORTED_LANGUAGE',
      'This service only speaks Santali (language code "sat").',
      false,
    );
  }

  // 5. Script-aware text validation. The text itself is not modified.
  const analysis = analyzeSantaliText(body.text);
  if (!analysis.ok) {
    const status = analysis.code === 'NO_OLCHIKI' ? 422 : 400;
    const code = analysis.code === 'NO_OLCHIKI' ? 'UNSUPPORTED_INPUT' : 'INVALID_TEXT';
    return sendError(res, status, code, analysis.message, false);
  }

  // 6. Speech engine availability.
  if (!speech) {
    return sendError(
      res,
      503,
      'SERVICE_NOT_CONFIGURED',
      'Santali speech is not configured on this server yet.',
      false,
    );
  }

  // 7. Synthesis.
  try {
    const audio = await speech.synthesize(body.text);
    stats.ttsOk += 1;
    stats.lastTtsSuccessAt = now();
    log('info', 'tts_ok', { textLength: analysis.length, olChikiLetters: analysis.olChikiLetters });
    return sendJson(res, 200, {
      audioBase64: audio.audioBase64,
      mimeType: audio.mimeType,
      extension: audio.extension,
      samplingRate: audio.samplingRate,
      textLength: analysis.length,
    });
  } catch (error) {
    stats.ttsFailed += 1;
    if (error instanceof BhashiniError) {
      log('warn', 'tts_failed', { code: error.code });
      return sendBhashiniError(res, error);
    }
    log('error', 'tts_unexpected_error', { errorName: error?.name ?? 'unknown' });
    return sendError(res, 500, 'INTERNAL_ERROR', 'Speech generation failed unexpectedly. Please try again.', true);
  }
}

function sendBhashiniError(res, error) {
  switch (error.code) {
    case 'NOT_CONFIGURED':
      return sendError(res, 503, 'SERVICE_NOT_CONFIGURED', 'Santali speech is not configured on this server yet.', false);
    case 'AUTH_FAILED':
      return sendError(res, 502, 'AUTHENTICATION_FAILED', 'The server could not authenticate with the speech service.', false);
    case 'SERVICE_UNAVAILABLE':
      return sendError(res, 503, 'SERVICE_UNAVAILABLE', 'The speech service is unavailable right now. Please try again shortly.', true);
    case 'TIMEOUT':
      return sendError(res, 504, 'TIMEOUT', 'The speech service took too long. Please try again.', true);
    case 'MODEL_UNAVAILABLE':
      return sendError(res, 503, 'MODEL_UNAVAILABLE', 'The Santali speech model is not available right now.', false);
    case 'UNSUPPORTED_AUDIO_FORMAT':
      return sendError(res, 502, 'UNSUPPORTED_AUDIO_FORMAT', 'The speech service returned audio in an unsupported format.', false);
    case 'GENERATION_FAILED':
      return sendError(res, 422, 'GENERATION_FAILED', 'The speech service could not generate audio for this text.', error.retryable);
    case 'BAD_RESPONSE':
    default:
      return sendError(res, 502, 'BAD_RESPONSE', 'The speech service sent an unexpected response. Please try again.', true);
  }
}

async function serveStatic(req, res, pathname, websiteRoot) {
  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return sendError(res, 400, 'BAD_PATH', 'The requested path is malformed.', false);
  }
  const relative = decoded === '/' ? 'index.html' : decoded.replace(/^\/+/, '');
  const candidate = path.resolve(websiteRoot, relative);
  if (candidate !== websiteRoot && !candidate.startsWith(websiteRoot + path.sep)) {
    return sendError(res, 400, 'BAD_PATH', 'The requested path is not allowed.', false);
  }

  let target = candidate;
  let stat;
  try {
    stat = await fsp.stat(target);
    if (stat.isDirectory()) {
      target = path.join(target, 'index.html');
      stat = await fsp.stat(target);
    }
  } catch {
    return sendNotFoundPage(req, res, websiteRoot);
  }
  if (!stat.isFile()) {
    return sendNotFoundPage(req, res, websiteRoot);
  }

  const ext = path.extname(target).toLowerCase();
  res.statusCode = 200;
  res.setHeader('Content-Type', CONTENT_TYPES[ext] ?? 'application/octet-stream');
  res.setHeader('Content-Length', String(stat.size));
  res.setHeader('Cache-Control', ext === '.html' ? 'no-cache' : 'public, max-age=3600');
  if (ext === '.html') {
    res.setHeader(
      'Content-Security-Policy',
      "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; script-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
    );
  }
  if (req.method === 'HEAD') {
    return res.end();
  }
  const stream = createReadStream(target);
  stream.on('error', () => {
    if (!res.headersSent) {
      sendError(res, 500, 'INTERNAL_ERROR', 'The page could not be read.', true);
    } else {
      res.destroy();
    }
  });
  stream.pipe(res);
  return undefined;
}

async function sendNotFoundPage(req, res, websiteRoot) {
  const notFound = path.join(websiteRoot, '404.html');
  try {
    const html = await fsp.readFile(notFound);
    res.statusCode = 404;
    res.setHeader('Content-Type', CONTENT_TYPES['.html']);
    res.setHeader('Content-Length', String(html.length));
    return res.end(req.method === 'HEAD' ? undefined : html);
  } catch {
    return sendError(res, 404, 'NOT_FOUND', 'Page not found.', false);
  }
}

function methodOnly(req, res, method, handler) {
  if (req.method !== method) {
    return sendError(res, 405, 'METHOD_NOT_ALLOWED', `Use ${method} for this endpoint.`, false, { Allow: method });
  }
  return handler();
}

/**
 * Read and parse a JSON body with a hard size cap.
 * @returns {Promise<unknown>}
 */
function readJsonBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    const declared = Number(req.headers['content-length'] ?? '0');
    if (Number.isFinite(declared) && declared > maxBytes) {
      req.resume();
      const err = new Error('too large');
      err.code = 'BODY_TOO_LARGE';
      return reject(err);
    }
    const chunks = [];
    let size = 0;
    let aborted = false;
    req.on('data', (chunk) => {
      if (aborted) return;
      size += chunk.length;
      if (size > maxBytes) {
        aborted = true;
        const err = new Error('too large');
        err.code = 'BODY_TOO_LARGE';
        reject(err);
        req.resume();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (aborted) return;
      try {
        const text = Buffer.concat(chunks).toString('utf8');
        resolve(JSON.parse(text));
      } catch {
        const err = new Error('invalid json');
        err.code = 'INVALID_JSON';
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, status, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Content-Length', String(Buffer.byteLength(body)));
  res.setHeader('Cache-Control', 'no-store');
  for (const [name, value] of Object.entries(extraHeaders)) {
    res.setHeader(name, value);
  }
  res.end(body);
}

function sendError(res, status, code, message, retryable, extraHeaders = {}) {
  sendJson(res, status, { error: { code, message, retryable } }, extraHeaders);
}

function clientAddress(req, trustProxy) {
  if (trustProxy) {
    const forwarded = req.headers['x-forwarded-for'];
    if (typeof forwarded === 'string' && forwarded.length > 0) {
      return forwarded.split(',')[0].trim();
    }
  }
  return req.socket.remoteAddress ?? 'unknown';
}

/** Only the path is logged, never the query string (which could hold user data). */
function safePathForLog(rawUrl) {
  try {
    return new URL(rawUrl ?? '/', 'http://localhost').pathname.slice(0, 120);
  } catch {
    return '/invalid';
  }
}

/** Resolve the default website directory relative to this file. */
export function defaultWebsiteDir() {
  return fileURLToPath(new URL('../../website', import.meta.url));
}
