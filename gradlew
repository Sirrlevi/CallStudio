#!/usr/bin/env sh
# Lightweight Gradle bootstrap for environments where the standard wrapper JAR is unavailable.
set -eu
VERSION="8.13"
BASE="${GRADLE_USER_HOME:-${HOME:-/tmp}/.gradle}/callstudio-bootstrap"
DIST="$BASE/gradle-$VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  else
    echo "curl or wget is required to download Gradle $VERSION" >&2; exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$ZIP" -d "$BASE"
  else echo "unzip is required to extract Gradle" >&2; exit 1; fi
  rm -f "$ZIP"
fi
exec "$DIST/bin/gradle" "$@"
