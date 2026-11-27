#!/usr/bin/env node
// LIVE verification of BHASHINI Santali text-to-speech with Ol Chiki input.
//
// This is the Phase 1 feasibility check from the project specification. It makes real calls to
// BHASHINI and therefore needs valid credentials. It is NOT part of the automated test suite,
// because mocked responses do not prove that speech is produced.
//
// Usage (from the backend/ directory):
//   BHASHINI_USER_ID=... BHASHINI_ULCA_API_KEY=... node scripts/verify-santali-tts.mjs [phrases-file]
//
// Output:
//   verification-output/<timestamp>/<n>.<ext>   one audio file per phrase (keep out of git)
//   verification-output/<timestamp>/report.json  summary, never containing credentials
//
// Audio must still be judged by a native Santali speaker before the app is released.

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { BhashiniClient } from '../src/bhashini.mjs';
import { ConfigError, loadConfig } from '../src/config.mjs';
import { analyzeSantaliText } from '../src/olchiki.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const defaultPhrases = path.join(here, '..', 'data', 'sample-phrases.txt');

async function main() {
  let config;
  try {
    config = loadConfig(process.env);
  } catch (error) {
    if (error instanceof ConfigError) {
      console.error(error.message);
      process.exit(2);
    }
    throw error;
  }
  if (!config.bhashini.configured) {
    console.error('Set BHASHINI_USER_ID and BHASHINI_ULCA_API_KEY before running this check.');
    process.exit(2);
  }

  const phrasesPath = process.argv[2] ?? defaultPhrases;
  const phrases = (await readFile(phrasesPath, 'utf8'))
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('#'));
  if (phrases.length === 0) {
    console.error(`No phrases found in ${phrasesPath}`);
    process.exit(2);
  }

  const runDir = path.join(here, '..', 'verification-output', new Date().toISOString().replace(/[:.]/g, '-'));
  await mkdir(runDir, { recursive: true });

  const client = new BhashiniClient({
    userId: config.bhashini.userId,
    ulcaApiKey: config.bhashini.ulcaApiKey,
    sourceLanguage: config.bhashini.sourceLanguage,
    gender: config.bhashini.gender,
    samplingRate: config.bhashini.samplingRate,
    serviceId: config.bhashini.serviceId,
    timeoutMs: config.bhashini.timeoutMs,
    logger: { warn: (f) => console.warn(JSON.stringify(f)) },
  });

  const report = {
    checkedAt: new Date().toISOString(),
    languageCode: config.bhashini.sourceLanguage,
    gender: config.bhashini.gender,
    pinnedServiceId: config.bhashini.serviceId,
    steps: [],
    phrases: [],
    verdict: 'FAILED',
  };

  // Step 1: pipeline discovery (config call).
  try {
    const pipeline = await client.discover();
    report.steps.push({ step: 'discover_tts_pipeline', ok: true, serviceId: pipeline.serviceId });
    console.log(`[ok] TTS pipeline discovered for "${config.bhashini.sourceLanguage}": serviceId=${pipeline.serviceId}`);
  } catch (error) {
    report.steps.push({ step: 'discover_tts_pipeline', ok: false, code: error.code ?? 'UNKNOWN', message: error.message });
    console.error(`[fail] discovery: ${error.code ?? 'UNKNOWN'} - ${error.message}`);
    await finish(runDir, report);
    return;
  }

  // Step 2: speech generation for each phrase, submitted exactly as written.
  let allOk = true;
  for (const [index, phrase] of phrases.entries()) {
    const analysis = analyzeSantaliText(phrase);
    const entry = {
      index: index + 1,
      characters: phrase.length,
      olChikiLetters: analysis.olChikiLetters,
      otherLetters: analysis.otherLetters,
      ok: false,
    };
    if (!analysis.ok) {
      entry.code = analysis.code;
      entry.message = analysis.message;
      allOk = false;
      report.phrases.push(entry);
      console.error(`[fail] phrase ${entry.index}: ${analysis.message}`);
      continue;
    }
    const started = Date.now();
    try {
      const audio = await client.synthesize(phrase);
      const file = path.join(runDir, `${entry.index}.${audio.extension}`);
      await writeFile(file, Buffer.from(audio.audioBase64, 'base64'));
      Object.assign(entry, {
        ok: true,
        mimeType: audio.mimeType,
        serviceId: audio.serviceId,
        audioBytes: Buffer.from(audio.audioBase64, 'base64').length,
        latencyMs: Date.now() - started,
        file: path.relative(path.join(here, '..'), file),
      });
      console.log(`[ok] phrase ${entry.index}: ${entry.audioBytes} bytes ${audio.mimeType} in ${entry.latencyMs} ms -> ${entry.file}`);
    } catch (error) {
      allOk = false;
      entry.code = error.code ?? 'UNKNOWN';
      entry.message = error.message;
      entry.latencyMs = Date.now() - started;
      console.error(`[fail] phrase ${entry.index}: ${entry.code} - ${error.message}`);
    }
    report.phrases.push(entry);
  }

  report.verdict = allOk ? 'PASSED_NEEDS_NATIVE_REVIEW' : 'FAILED';
  await finish(runDir, report);
}

async function finish(runDir, report) {
  const file = path.join(runDir, 'report.json');
  await writeFile(file, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  console.log(`\nVerdict: ${report.verdict}\nReport: ${path.relative(process.cwd(), file)}`);
  process.exitCode = report.verdict === 'FAILED' ? 1 : 0;
}

main().catch((error) => {
  // Never print credentials; only the error name and message.
  console.error(`Unexpected error: ${error.name}: ${error.message}`);
  process.exit(1);
});
