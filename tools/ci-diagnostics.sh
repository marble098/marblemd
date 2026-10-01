#!/usr/bin/env bash
# TEMPORARY CI diagnostics helper (removed before merge).
#
# Recompiles the project in a scratch git worktree and publishes the captured
# console output to the `marblemd-ci-diagnostics` branch, so a failing build can
# be diagnosed even when the Actions log host is unreachable.
set -uo pipefail

WORKSPACE="${GITHUB_WORKSPACE:-$(pwd)}"
SCRATCH="${RUNNER_TEMP:-/tmp}/marblemd-diagnostics"
LOG="$WORKSPACE/ci-diagnostics.txt"
DIAGNOSTICS_BRANCH="marblemd-ci-diagnostics"

publish() {
  cd "$WORKSPACE" || return 0
  git config user.email "ci-diagnostics@marblemd.invalid"
  git config user.name "MarbleMD CI diagnostics"
  git add -f ci-diagnostics.txt
  if ! git diff --cached --quiet; then
    git commit -q -m "[skip ci] ci: capture build diagnostics" || true
    git push -q --force origin "HEAD:refs/heads/$DIAGNOSTICS_BRANCH" \
      || echo "diagnostics push failed" >>"$LOG"
  fi
}

GRADLE_BIN="$(command -v gradle || true)"
if [[ -z "$GRADLE_BIN" ]]; then
  GRADLE_BIN="$WORKSPACE/gradlew"
fi

{
  echo "MarbleMD CI diagnostics"
  echo "date: $(date -u)"
  echo "event: ${GITHUB_EVENT_NAME:-unknown}"
  echo "branch: ${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-unknown}}"
  echo "workspace: $WORKSPACE"
  echo "gradle: $GRADLE_BIN"
  echo "java: $(command -v java || echo none)"
  echo "git: $(git --version)"
  echo "hook: reached settings evaluation"
} >"$LOG" 2>&1
publish

cd "$WORKSPACE" || exit 0

rm -rf "$SCRATCH"
git worktree remove --force "$SCRATCH" >/dev/null 2>&1 || true
git worktree add --detach "$SCRATCH" HEAD >/dev/null 2>&1

{
  echo
  echo "===== scratch worktree: $( [[ -d "$SCRATCH" ]] && echo ok || echo failed ) ====="
  echo "===== nested Kotlin compilation ====="
  (
    cd "$SCRATCH" || exit 1
    "$GRADLE_BIN" --no-daemon --console=plain --continue --stacktrace \
      :app:compileDebugKotlin :app:compileDebugUnitTestKotlin 2>&1 |
      grep -vE '^(Download |Welcome to Gradle|Starting a Gradle Daemon|Daemon will be stopped)' |
      tail -600
  )
  echo "nested exit=${PIPESTATUS[0]}"
} >>"$LOG" 2>&1

if grep -qE "^e: |error:" "$LOG"; then
  {
    echo "===== filtered compiler errors ====="
    grep -nE "^e: |error:|Execution failed for task|FAILURE: Build failed|Could not resolve|Unresolved reference" "$LOG" | head -120
    echo
    echo "===== raw tail ====="
    tail -120 "$LOG"
  } >"$LOG.tmp"
  mv "$LOG.tmp" "$LOG"
fi

publish
