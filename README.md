# Santali Speaks

Santali Speaks reads **Ol Chiki** Santali text aloud. It is an Android app (Kotlin, Jetpack Compose,
Material 3) backed by a small Node.js proxy that forwards requests to the BHASHINI platform.
Website: https://speaks.santals.in

## Status — read this first

* **Ol Chiki speech is not yet verified end-to-end.** The official BHASHINI model listing reviewed
  so far shows Santali text-to-speech only in Devanagari script. Ol Chiki input has not been
  confirmed against a live BHASHINI service. The app never converts Ol Chiki to Devanagari or Latin
  to make it work, and it never uses another Indian language's voice in place of Santali.
  Until verification is done, the app reports speech as unavailable when the server cannot
  produce Santali audio, rather than playing a substitute.
* `backend/scripts/verify-santali-tts.mjs` performs the live check once BHASHINI credentials are
  configured. Its output is written to `backend/verification-output/` (git-ignored).
* The Android project has not been compiled in the authoring environment (no Gradle or Google Maven
  access). The Gradle wrapper JAR is not committed, so run `gradle wrapper` once locally first.
  Pure-Kotlin domain logic is tested with `android/tools/jvm-domain-tests/run.sh`.

## Repository layout

| Path | Contents |
| --- | --- |
| `android/` | Android app (Gradle KTS, minSdk 26, targetSdk 35) |
| `backend/` | Node.js 20+ proxy: `/api/health`, `/api/status`, `POST /api/v1/tts`, static website |
| `android/tools/jvm-domain-tests/` | Offline JVM harness for domain and mapper logic |

## Backend

```sh
cd backend
npm test          # node:test suites
npm run check     # syntax check of every module
npm start         # requires the environment variables below
```

Required environment (never commit values): BHASHINI user and inference credentials, and
`PORT`, `TRUST_PROXY`, `WEBSITE_DIR` as needed. The app itself contains **no** BHASHINI credentials;
it only knows the HTTPS base URL (`santali.apiBaseUrl` in `android/gradle.properties`).

## Privacy

Text is processed on the device where possible. Saved readings and audio stay in app-private
storage. Text is sent to the Santali Speaks server only when audio must be generated, and the server
does not persist it. The app does not monitor the clipboard; it reads it only when the user taps
Paste. Ol Chiki font: Noto Sans Ol Chiki, SIL Open Font License 1.1 (`android/app/src/main/assets/licenses`).

## Licence

See `LICENSE`.
