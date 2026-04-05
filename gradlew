#!/bin/sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
PROPERTIES_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "Missing $PROPERTIES_FILE"
  exit 1
fi

DISTRIBUTION_URL=$(grep '^distributionUrl=' "$PROPERTIES_FILE" | cut -d= -f2- | sed 's#\\:##g')

if [ -z "$DISTRIBUTION_URL" ]; then
  echo "distributionUrl not found in $PROPERTIES_FILE"
  exit 1
fi

DIST_DIR="$APP_HOME/.gradle/wrapper/dists"
ZIP_PATH="$DIST_DIR/gradle-dist.zip"
UNPACK_DIR="$DIST_DIR/unpacked"

mkdir -p "$DIST_DIR"

find_gradle() {
  find "$UNPACK_DIR" -type f -path '*/bin/gradle' | head -n 1
}

GRADLE_BIN=""
if [ -d "$UNPACK_DIR" ]; then
  GRADLE_BIN=$(find_gradle || true)
fi

if [ -z "$GRADLE_BIN" ]; then
  echo "Downloading Gradle distribution..."
  rm -rf "$UNPACK_DIR"
  mkdir -p "$UNPACK_DIR"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DISTRIBUTION_URL" -o "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DISTRIBUTION_URL" -O "$ZIP_PATH"
  else
    echo "curl or wget is required to download Gradle."
    exit 1
  fi

  unzip -q -o "$ZIP_PATH" -d "$UNPACK_DIR"
  GRADLE_BIN=$(find_gradle || true)
fi

if [ -z "$GRADLE_BIN" ]; then
  echo "Unable to locate the Gradle binary after download."
  exit 1
fi

exec "$GRADLE_BIN" --project-dir "$APP_HOME" "$@"
