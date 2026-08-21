#!/usr/bin/env sh
set -eu
VERSION="9.5.0"
CACHE_BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/marblemd-bootstrap"
DIST_DIR="$CACHE_BASE/gradle-$VERSION"
ZIP="$CACHE_BASE/gradle-$VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

mkdir -p "$CACHE_BASE"
if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  echo "[MarbleMD] Gradle $VERSION not found; bootstrapping it..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail --retry 3 -o "$ZIP" "$URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
  else
    echo "curl or wget is required to bootstrap Gradle." >&2
    exit 2
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP" -d "$CACHE_BASE"
  else
    echo "unzip is required to bootstrap Gradle." >&2
    exit 2
  fi
fi
exec "$DIST_DIR/bin/gradle" "$@"
