#!/usr/bin/env bash
# TEMPORARY CI diagnostics helper.
#
# Re-runs the Kotlin compilation in a scratch copy of the project and commits
# the captured console output as `ci-diagnostics.txt`, so a build failure can be
# inspected even when the GitHub Actions log host is unreachable.
#
# This file and the hook in build.gradle.kts are removed once the branch is green.
set -uo pipefail

WORKSPACE="${GITHUB_WORKSPACE:-$(pwd)}"
SCRATCH="${RUNNER_TEMP:-/tmp}/marblemd-diagnostics"
LOG="$WORKSPACE/ci-diagnostics.txt"

rm -rf "$SCRATCH"
mkdir -p "$SCRATCH"

copy_tree() {
  local src="$1" dst="$2"
  (
    cd "$src" || exit 0
    find . -mindepth 1 \
      \( -name .git -o -name build -o -name .gradle -o -name .kotlin -o -name node_modules \) -prune -o \
      -print0 |
      while IFS= read -r -d '' path; do
        if [[ -d "$path" ]]; then
          mkdir -p "$dst/$path"
        else
          mkdir -p "$dst/$(dirname "$path")"
          cp -p "$path" "$dst/$path"
        fi
      done
  )
}

copy_tree "$WORKSPACE" "$SCRATCH"

GRADLE_BIN="$(command -v gradle || true)"
if [[ -z "$GRADLE_BIN" ]]; then
  GRADLE_BIN="$WORKSPACE/gradlew"
fi

{
  echo "MarbleMD CI diagnostics"
  echo "date: $(date -u)"
  echo "event: ${GITHUB_EVENT_NAME:-unknown}"
  echo "branch: ${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-unknown}}"
  echo "gradle: $GRADLE_BIN"
  echo
  echo "===== nested Kotlin compilation ====="
  (
    cd "$SCRATCH" || exit 1
    "$GRADLE_BIN" --no-daemon --console=plain --stacktrace :app:compileDebugUnitTestKotlin 2>&1 |
      grep -vE '^(Download |Welcome to Gradle|Starting a Gradle Daemon|Daemon will be stopped)' |
      tail -500
  )
  echo "nested exit=${PIPESTATUS[0]}"
} >"$LOG" 2>&1

if grep -qE "^e: |error:" "$LOG"; then
  {
    echo "===== filtered compiler errors ====="
    grep -nE "^e: |^w: .*deprecat|error:|Execution failed for task|FAILURE: Build failed" "$LOG" | head -150
    echo
    echo "===== raw tail ====="
    tail -150 "$LOG"
  } >"$LOG.tmp"
  mv "$LOG.tmp" "$LOG"
fi

cd "$WORKSPACE" || exit 0
git config user.email "ci-diagnostics@marblemd.invalid"
git config user.name "MarbleMD CI diagnostics"
git add -f ci-diagnostics.txt

if ! git diff --cached --quiet; then
  git commit -m "[skip ci] ci: capture build diagnostics" >/dev/null 2>&1 || true
  BRANCH="${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-}}"
  if [[ -n "$BRANCH" ]]; then
    git push origin "HEAD:refs/heads/$BRANCH" >/dev/null 2>&1 || echo "diagnostics push failed"
  fi
fi
