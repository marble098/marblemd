#!/usr/bin/env bash
# TEMPORARY: expose failures through check annotations when Actions logs are
# unreachable. No commits, pushes, or changes to the actual build workspace.
set -uo pipefail

WORKSPACE="${GITHUB_WORKSPACE:-$(pwd)}"
RUN_TEMP="${RUNNER_TEMP:-/tmp}"
SCRATCH="$RUN_TEMP/marblemd-diagnostics"
LOG="$RUN_TEMP/marblemd-diagnostics.log"
MARKER="$RUN_TEMP/marblemd-diagnostics-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"

# The workflow invokes Gradle several times; diagnose just once per run.
[[ -f "$MARKER" ]] && exit 0
touch "$MARKER"

GRADLE_BIN="$(command -v gradle || true)"
[[ -n "$GRADLE_BIN" ]] || GRADLE_BIN="$WORKSPACE/gradlew"

cd "$WORKSPACE" || exit 0
git worktree remove --force "$SCRATCH" >/dev/null 2>&1 || true
if ! git worktree add --detach "$SCRATCH" HEAD >/dev/null 2>&1; then
  echo "::warning title=CI diagnostics::Could not create the diagnostic worktree."
  exit 0
fi
trap 'cd "$WORKSPACE"; git worktree remove --force "$SCRATCH" >/dev/null 2>&1 || true' EXIT

(
  cd "$SCRATCH" || exit 1
  timeout 20m env MARBLEMD_DIAGNOSTICS_CHILD=1 "$GRADLE_BIN" \
    --no-daemon --console=plain --continue --stacktrace \
    testDebugUnitTest lintDebug assembleRelease
) >"$LOG" 2>&1
RESULT=$?

python3 - "$SCRATCH" "$LOG" "$RESULT" <<'PY'
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root, log_path, result = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), int(sys.argv[3])
log = log_path.read_text(errors="replace")

def annotate(title, text, level="notice"):
    # Workflow command messages are limited in size and require these escapes.
    text = text[:40000].replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::{level} title={title}::{text}", flush=True)

compiler = [line for line in log.splitlines() if re.search(r"^e: |error:|ERROR:|Unresolved reference|Could not resolve", line)]
if compiler:
    annotate("Compiler and resource diagnostics", "\n".join(compiler), "error")

tests = failures = errors = 0
for path in sorted(root.glob("app/build/test-results/testDebugUnitTest/TEST-*.xml")):
    suite = ET.parse(path).getroot()
    tests += int(suite.get("tests", 0))
    failures += int(suite.get("failures", 0))
    errors += int(suite.get("errors", 0))
    for case in suite.findall("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            details = "\n".join((problem.text or "").splitlines()[:14])
            annotate("Unit test failure", f"{case.get('classname')}.{case.get('name')}\n{problem.get('message', '')}\n{details}", "error")
if tests:
    annotate("Unit test totals", f"Tests: {tests}; failures: {failures}; errors: {errors}")

lint = list(root.glob("app/build/reports/lint-results-debug.txt"))
if not lint:
    lint = list(root.glob("app/build/intermediates/lint_intermediate_text_report/**/lint-results-debug.txt"))
for path in lint[:1]:
    annotate("Android lint diagnostics", path.read_text(errors="replace"))

if result:
    annotate("Diagnostic build failure", f"Exit code: {result}\n" + "\n".join(log.splitlines()[-75:]), "error")
else:
    annotate("Diagnostic build passed", "Unit tests, lintDebug and assembleRelease all succeeded.\n" + "\n".join(log.splitlines()[-15:]))
PY

# Diagnostics must not change the result of the real workflow commands.
exit 0
