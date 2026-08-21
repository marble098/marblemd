#!/usr/bin/env bash
set -Eeuo pipefail

REPO_NAME="${REPO_NAME:-marblemd}"
API_ROOT="https://api.github.com"
API_VERSION="2022-11-28"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d 2>/dev/null || mktemp -d -t marblemd)"
RESP_BODY="$TMP/response.json"
SOURCE_DIR="${SOURCE_DIR:-}"
PRIVATE_JSON=false
[[ "${MARBLEMD_PRIVATE:-0}" == "1" || "${MARBLEMD_PRIVATE:-false}" == "true" ]] && PRIVATE_JSON=true

cleanup() {
  unset GH_TOKEN || true
  rm -rf "$TMP"
}
trap cleanup EXIT
trap 'echo >&2 "[FAIL] line $LINENO: command failed"' ERR

say() { printf '\033[1;36m[MarbleMD]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m[ OK ]\033[0m %s\n' "$*"; }
warn(){ printf '\033[1;33m[WARN]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }

install_tools_if_needed() {
  local missing=()
  for c in curl jq unzip base64 find; do
    command -v "$c" >/dev/null 2>&1 || missing+=("$c")
  done
  ((${#missing[@]} == 0)) && return 0
  warn "Missing tools: ${missing[*]}"
  if command -v pkg >/dev/null 2>&1; then
    pkg update -y >/dev/null || true
    pkg install -y curl jq unzip coreutils findutils
  elif command -v apt-get >/dev/null 2>&1; then
    if [[ "$(id -u)" == "0" ]]; then
      apt-get update -y && apt-get install -y curl jq unzip coreutils findutils
    elif command -v sudo >/dev/null 2>&1; then
      sudo apt-get update -y && sudo apt-get install -y curl jq unzip coreutils findutils
    else
      die "Install curl jq unzip coreutils findutils, then rerun."
    fi
  elif command -v apk >/dev/null 2>&1; then
    [[ "$(id -u)" == "0" ]] || die "Run as a user allowed to install curl jq unzip coreutils findutils."
    apk add --no-cache curl jq unzip coreutils findutils
  else
    die "Please install: curl jq unzip base64 find"
  fi
}

request() {
  local method="$1" endpoint="$2" data_file="${3:-}"
  local args=(
    -sS -X "$method"
    -H "Accept: application/vnd.github+json"
    -H "Authorization: Bearer $GH_TOKEN"
    -H "X-GitHub-Api-Version: $API_VERSION"
    -H "User-Agent: MarbleMD-cloud-publisher"
    -o "$RESP_BODY"
    -w "%{http_code}"
  )
  if [[ -n "$data_file" ]]; then
    args+=( -H "Content-Type: application/json" --data-binary "@$data_file" )
  fi
  RESP_CODE="$(curl "${args[@]}" "$API_ROOT$endpoint")"
}

expect_2xx() {
  [[ "$RESP_CODE" =~ ^2 ]] && return 0
  local msg
  msg="$(jq -r '.message // empty' "$RESP_BODY" 2>/dev/null || true)"
  [[ -n "$msg" ]] || msg="GitHub API returned HTTP $RESP_CODE"
  if [[ "$RESP_CODE" == "403" && "$msg" == *workflow* ]]; then
    msg+=". Your PAT may need workflow/Actions write permission because this source contains .github/workflows/android.yml"
  fi
  die "$msg (HTTP $RESP_CODE)"
}

resolve_source() {
  if [[ -n "$SOURCE_DIR" ]]; then
    SOURCE_DIR="$(cd "$SOURCE_DIR" && pwd)"
    [[ -f "$SOURCE_DIR/settings.gradle.kts" ]] || die "SOURCE_DIR is not a MarbleMD source root."
    return
  fi

  if [[ -d "$SCRIPT_DIR/marblemd-src" && -f "$SCRIPT_DIR/marblemd-src/settings.gradle.kts" ]]; then
    SOURCE_DIR="$SCRIPT_DIR/marblemd-src"
    return
  fi

  if [[ -f "$SCRIPT_DIR/marblemd-source.zip" ]]; then
    say "Extracting marblemd-source.zip to a temporary workspace..."
    mkdir -p "$TMP/src"
    unzip -q "$SCRIPT_DIR/marblemd-source.zip" -d "$TMP/src"
    if [[ -f "$TMP/src/settings.gradle.kts" ]]; then
      SOURCE_DIR="$TMP/src"
    else
      local candidate
      candidate="$(find "$TMP/src" -mindepth 1 -maxdepth 2 -type f -name settings.gradle.kts -print -quit | xargs -r dirname)"
      [[ -n "$candidate" ]] || die "ZIP does not contain a MarbleMD source tree."
      SOURCE_DIR="$candidate"
    fi
    return
  fi

  if [[ -f "$PWD/settings.gradle.kts" ]]; then
    SOURCE_DIR="$PWD"
    return
  fi

  die "Put this script beside marblemd-source.zip, beside marblemd-src/, or run it from the extracted project root."
}

is_excluded() {
  local rel="$1"
  case "$rel" in
    .git/*|.gradle/*|.idea/*|*/build/*|build/*|*.iml|local.properties|*.apk|*.aab|*.jks|*.keystore) return 0 ;;
    app/src/main/assets/fonts/*.ttf|app/src/main/assets/fonts/*.otf) return 0 ;;
  esac
  return 1
}

install_tools_if_needed
resolve_source

printf 'GitHub Personal Access Token (input hidden): '
IFS= read -r -s GH_TOKEN
printf '\n'
[[ -n "$GH_TOKEN" ]] || die "No token entered."

say "Authenticating..."
request GET "/user"
expect_2xx
LOGIN="$(jq -r '.login // empty' "$RESP_BODY")"
[[ -n "$LOGIN" ]] || die "Could not resolve authenticated GitHub login."
ok "Authenticated as $LOGIN"

say "Checking repository $LOGIN/$REPO_NAME..."
request GET "/repos/$LOGIN/$REPO_NAME"
if [[ "$RESP_CODE" == "404" ]]; then
  jq -n \
    --arg name "$REPO_NAME" \
    --arg desc "MarbleMD — multilingual RTL/LTR Markdown reader for Android" \
    --argjson private "$PRIVATE_JSON" \
    '{name:$name,description:$desc,private:$private,auto_init:false,has_issues:true,has_projects:false,has_wiki:false}' \
    > "$TMP/repo.json"
  request POST "/user/repos" "$TMP/repo.json"
  expect_2xx
  ok "Created repository $LOGIN/$REPO_NAME"
elif [[ "$RESP_CODE" =~ ^2 ]]; then
  ok "Repository already exists; main will be replaced with this source tree in one commit"
else
  expect_2xx
fi

# Refresh repo metadata after create/existing lookup.
request GET "/repos/$LOGIN/$REPO_NAME"
expect_2xx
DEFAULT_BRANCH="$(jq -r '.default_branch // "main"' "$RESP_BODY")"

PARENT_SHA=""
MAIN_EXISTS=0
request GET "/repos/$LOGIN/$REPO_NAME/git/ref/heads/main"
if [[ "$RESP_CODE" =~ ^2 ]]; then
  MAIN_EXISTS=1
  PARENT_SHA="$(jq -r '.object.sha' "$RESP_BODY")"
elif [[ "$RESP_CODE" != "404" && "$RESP_CODE" != "409" ]]; then
  expect_2xx
fi

if [[ -z "$PARENT_SHA" && "$DEFAULT_BRANCH" != "main" ]]; then
  request GET "/repos/$LOGIN/$REPO_NAME/git/ref/heads/$DEFAULT_BRANCH"
  if [[ "$RESP_CODE" =~ ^2 ]]; then
    PARENT_SHA="$(jq -r '.object.sha' "$RESP_BODY")"
  elif [[ "$RESP_CODE" != "404" && "$RESP_CODE" != "409" ]]; then
    expect_2xx
  fi
fi

say "Creating Git blobs directly from source (no git clone, no local repo history)..."
ENTRIES="$TMP/tree.ndjson"
: > "$ENTRIES"
COUNT=0
while IFS= read -r -d '' file; do
  rel="${file#"$SOURCE_DIR"/}"
  is_excluded "$rel" && continue

  # Do not publish the downloaded archive if the script is run from Downloads.
  [[ "$rel" == "marblemd-source.zip" ]] && continue

  b64="$TMP/blob.b64"
  payload="$TMP/blob.json"
  base64 < "$file" | tr -d '\r\n' > "$b64"
  jq -Rs '{content:.,encoding:"base64"}' "$b64" > "$payload"
  request POST "/repos/$LOGIN/$REPO_NAME/git/blobs" "$payload"
  expect_2xx
  blob_sha="$(jq -r '.sha // empty' "$RESP_BODY")"
  [[ -n "$blob_sha" ]] || die "GitHub did not return a blob SHA for $rel"

  mode="100644"
  [[ -x "$file" ]] && mode="100755"
  jq -cn --arg path "$rel" --arg mode "$mode" --arg sha "$blob_sha" \
    '{path:$path,mode:$mode,type:"blob",sha:$sha}' >> "$ENTRIES"
  COUNT=$((COUNT + 1))
  printf '\r[MarbleMD] uploaded %d files • %s\033[K' "$COUNT" "$rel"
done < <(
  find "$SOURCE_DIR" \
    \( -type d \( -name .git -o -name .gradle -o -name .idea -o -name build \) -prune \) -o \
    -type f -print0
)
printf '\n'
(( COUNT > 0 )) || die "No source files found."

jq -s '{tree:.}' "$ENTRIES" > "$TMP/tree.json"
request POST "/repos/$LOGIN/$REPO_NAME/git/trees" "$TMP/tree.json"
expect_2xx
TREE_SHA="$(jq -r '.sha // empty' "$RESP_BODY")"
[[ -n "$TREE_SHA" ]] || die "GitHub did not return a tree SHA."
ok "Created complete source tree ($COUNT files)"

COMMIT_MESSAGE="feat: bootstrap MarbleMD Android Markdown reader"
if [[ -n "$PARENT_SHA" ]]; then
  jq -n --arg message "$COMMIT_MESSAGE" --arg tree "$TREE_SHA" --arg parent "$PARENT_SHA" \
    '{message:$message,tree:$tree,parents:[$parent]}' > "$TMP/commit.json"
else
  jq -n --arg message "$COMMIT_MESSAGE" --arg tree "$TREE_SHA" \
    '{message:$message,tree:$tree,parents:[]}' > "$TMP/commit.json"
fi
request POST "/repos/$LOGIN/$REPO_NAME/git/commits" "$TMP/commit.json"
expect_2xx
COMMIT_SHA="$(jq -r '.sha // empty' "$RESP_BODY")"
[[ -n "$COMMIT_SHA" ]] || die "GitHub did not return a commit SHA."

if (( MAIN_EXISTS )); then
  jq -n --arg sha "$COMMIT_SHA" '{sha:$sha,force:true}' > "$TMP/ref.json"
  request PATCH "/repos/$LOGIN/$REPO_NAME/git/refs/heads/main" "$TMP/ref.json"
  expect_2xx
else
  jq -n --arg sha "$COMMIT_SHA" '{ref:"refs/heads/main",sha:$sha}' > "$TMP/ref.json"
  request POST "/repos/$LOGIN/$REPO_NAME/git/refs" "$TMP/ref.json"
  expect_2xx
fi
ok "main -> ${COMMIT_SHA:0:12}"

jq -n '{default_branch:"main"}' > "$TMP/default.json"
request PATCH "/repos/$LOGIN/$REPO_NAME" "$TMP/default.json"
expect_2xx

printf '\n'
ok "Published MarbleMD completely cloud-side; no clone was created."
printf 'Repository: https://github.com/%s/%s\n' "$LOGIN" "$REPO_NAME"
printf 'Actions:    https://github.com/%s/%s/actions\n' "$LOGIN" "$REPO_NAME"
printf 'Commit:     https://github.com/%s/%s/commit/%s\n' "$LOGIN" "$REPO_NAME" "$COMMIT_SHA"
printf '\nThe Android CI workflow will build the installable APK artifacts on GitHub Actions.\n'
