// Entry point: loads configuration, builds the speech engine and starts the HTTP server.
// Run with: node src/main.mjs   (reads environment variables; see .env.example)

import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { BhashiniClient } from './bhashini.mjs';
import { ConfigError, loadConfig } from './config.mjs';
import { createServer, SERVICE_NAME, defaultWebsiteDir } from './server.mjs';

const logger = {
  info: (fields) => writeLog('info', fields),
  warn: (fields) => writeLog('warn', fields),
  error: (fields) => writeLog('error', fields),
};

/** Structured JSON logs. Callers must never pass user text or credentials into these fields. */
function writeLog(level, fields) {
  const line = JSON.stringify({ time: new Date().toISOString(), level, service: SERVICE_NAME, ...fields });
  if (level === 'error') {
    process.stderr.write(`${line}\n`);
  } else {
    process.stdout.write(`${line}\n`);
  }
}

export function buildSpeechEngine(config) {
  if (!config.bhashini.configured) {
    return null;
  }
  return new BhashiniClient({
    userId: config.bhashini.userId,
    ulcaApiKey: config.bhashini.ulcaApiKey,
    sourceLanguage: config.bhashini.sourceLanguage,
    gender: config.bhashini.gender,
    samplingRate: config.bhashini.samplingRate,
    serviceId: config.bhashini.serviceId,
    timeoutMs: config.bhashini.timeoutMs,
    logger,
  });
}

export function main(env = process.env) {
  let config;
  try {
    config = loadConfig(env);
  } catch (error) {
    if (error instanceof ConfigError) {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
      return null;
    }
    throw error;
  }

  const speech = buildSpeechEngine(config);
  if (!speech) {
    logger.warn({ msg: 'speech_not_configured', hint: 'Set BHASHINI_USER_ID and BHASHINI_ULCA_API_KEY.' });
  }

  const websiteDir = config.websiteDir ?? defaultWebsiteDir();
  const server = createServer({ config: { ...config, websiteDir }, speech, logger });
  server.listen(config.port, config.host, () => {
    logger.info({ msg: 'listening', host: config.host, port: config.port, speechConfigured: Boolean(speech) });
  });

  const shutdown = (signal) => {
    logger.info({ msg: 'shutting_down', signal });
    server.close((err) => {
      if (err) {
        logger.error({ msg: 'shutdown_error', errorName: err.name });
        process.exitCode = 1;
      }
      process.exit();
    });
    // Force exit if connections linger.
    setTimeout(() => process.exit(process.exitCode ?? 0), 10_000).unref();
  };
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('unhandledRejection', (reason) => {
    logger.error({ msg: 'unhandled_rejection', errorName: reason?.name ?? 'unknown' });
  });
  process.on('uncaughtException', (error) => {
    logger.error({ msg: 'uncaught_exception', errorName: error?.name ?? 'unknown' });
    shutdown('uncaughtException');
  });

  return server;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main();
}
