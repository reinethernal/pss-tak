#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

ROOT=/opt/psr-client-build/OpenTAK_ICU
APP_GRADLE="$ROOT/app/build.gradle.kts"
DEST_DIR=/var/www/html/opentakserver/downloads

python3 /opt/psr-client-build/scripts/bump-android-version.py "$APP_GRADLE"
# keep monorepo in sync if present
MONO=/opt/psr-client-build/pss-tak/apps/opentak-icu/app/build.gradle.kts
if [[ -f "$MONO" ]]; then
  VC=$(grep -oP 'versionCode\s*=\s*\K\d+' "$APP_GRADLE" | head -1)
  VN=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$APP_GRADLE" | head -1)
  sed -i -E "s/versionCode\s*=\s*[0-9]+/versionCode = $VC/" "$MONO"
  sed -i -E "s/versionName\s*=\s*\"[^\"]+\"/versionName = \"$VN\"/" "$MONO"
fi
VN=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$APP_GRADLE" | head -1)

cd "$ROOT"
mkdir -p app/src/main/assets
cp -f /home/ots/ots/ca/truststore-root.p12 app/src/main/assets/truststore-root.p12
printf 'sdk.dir=/opt/android-sdk\n' > local.properties
./gradlew --no-daemon assembleDebug
OUT=$(find app/build/outputs/apk -name '*.apk' | head -1)
test -n "$OUT"
OUT_NAME="OpenTAK_ICU-PSR-${VN}.apk"
cp -f "$OUT" "$DEST_DIR/$OUT_NAME"
ln -sfn "$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-latest.apk"
cp -f "$OUT" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk"
chown www-data:www-data "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk" 2>/dev/null || true
chmod 664 "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk" 2>/dev/null || true
ls -lh "$DEST_DIR/$OUT_NAME"
