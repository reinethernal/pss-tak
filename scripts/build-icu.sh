#!/usr/bin/env bash
# Build ПСР Видео (OpenTAK ICU) from the monorepo and publish to downloads.
set -euo pipefail
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

ROOT=/opt/psr-client-build/pss-tak/apps/opentak-icu
APP_GRADLE="$ROOT/app/build.gradle.kts"
DEST_DIR=/var/www/html/opentakserver/downloads
TRUST=/home/ots/ots/ca/truststore-root.p12

[[ -f "$APP_GRADLE" ]] || { echo "missing $APP_GRADLE"; exit 1; }
[[ -f "$TRUST" ]] || { echo "missing truststore $TRUST"; exit 1; }

python3 /opt/psr-client-build/scripts/bump-android-version.py "$APP_GRADLE"
VN=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$APP_GRADLE" | head -1)

cd "$ROOT"
mkdir -p app/src/main/assets
cp -f "$TRUST" app/src/main/assets/truststore-root.p12
printf 'sdk.dir=/opt/android-sdk\n' > local.properties
./gradlew --no-daemon assembleDebug
OUT=$(ls -t app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
test -n "${OUT:-}"
# Verify package id
if command -v aapt >/dev/null 2>&1 || [[ -x "$ANDROID_HOME/build-tools" ]]; then
  AAPT=$(ls -d "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1 || true)
  if [[ -n "${AAPT:-}" ]]; then
    PKG=$("$AAPT" dump badging "$OUT" | awk -F"'" '/package: name=/{print $2; exit}')
    [[ "$PKG" == "ru.plasmadancer.psr.icu" ]] || {
      echo "ERROR: unexpected packageId='$PKG' (want ru.plasmadancer.psr.icu)"
      exit 1
    }
  fi
fi
OUT_NAME="OpenTAK_ICU-PSR-${VN}.apk"
cp -f "$OUT" "$DEST_DIR/$OUT_NAME"
ln -sfn "$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-latest.apk"
cp -f "$OUT" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk"
chown www-data:www-data "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk" 2>/dev/null || true
chmod 664 "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk" 2>/dev/null || true
ls -lh "$DEST_DIR/$OUT_NAME"
echo "OK: $PKG published as $OUT_NAME"
