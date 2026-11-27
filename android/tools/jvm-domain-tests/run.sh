#!/usr/bin/env bash
# Compiles the pure-Kotlin domain layer and its unit tests, then runs them on a plain JVM.
# Needs: kotlinc (Kotlin 2.x) and a JDK 17+ on PATH (or set KOTLINC and JAVA).
# This does not require Gradle, the Android SDK or network access.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
KOTLINC="${KOTLINC:-kotlinc}"
JAVA="${JAVA:-java}"
OUT="${OUT:-$(mktemp -d)}"

DOMAIN_SRC="$ROOT/app/src/main/java/app/santals/speaks/domain"
REMOTE_PURE_SRC="$ROOT/app/src/main/java/app/santals/speaks/data/remote/SpeechErrorMapper.kt"
TEST_SRC="$ROOT/app/src/test/java/app/santals/speaks/domain"
REMOTE_TEST_SRC="$ROOT/app/src/test/java/app/santals/speaks/data/remote"

# Stdlib + kotlin-test jars come from the compiler distribution.
KHOME="$(cd "$(dirname "$(command -v "$KOTLINC")")/.." && pwd)"
LIB="$KHOME/lib"
CP="$LIB/kotlin-stdlib.jar:$LIB/kotlin-test.jar"

# HistoryRepository.kt uses kotlinx.coroutines.Flow, which is not available offline. It is
# compiled by Gradle in the app; it is excluded here because no test depends on it.
DOMAIN_FILES=$(find "$DOMAIN_SRC" -name "*.kt" ! -name "HistoryRepository.kt" | sort)

"$KOTLINC" -jvm-target 17 -Werror -cp "$CP" \
  -d "$OUT/classes" \
  $DOMAIN_FILES "$REMOTE_PURE_SRC" "$TEST_SRC" "$REMOTE_TEST_SRC" \
  "$HERE/junit-stub/org/junit/Test.kt" "$HERE/DomainTestRunner.kt"

CLASSES=()
while IFS= read -r f; do
  pkg="$(grep -m1 '^package ' "$f" | sed 's/^package //')"
  CLASSES+=("$pkg.$(basename "$f" .kt)")
done < <(find "$TEST_SRC" "$REMOTE_TEST_SRC" -name "*Test.kt" | sort)

"$JAVA" -cp "$OUT/classes:$CP" DomainTestRunnerKt "${CLASSES[@]}"
