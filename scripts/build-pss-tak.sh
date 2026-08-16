#!/usr/bin/env bash
# Build PSS TAK (monorepo) debug APK with auto minor version bump; publish to /downloads/
set -euo pipefail
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

ROOT=/opt/psr-client-build/pss-tak
APP_GRADLE="$ROOT/apps/pss-tak/app/build.gradle.kts"
DEST_DIR=/var/www/html/opentakserver/downloads

python3 /opt/psr-client-build/scripts/bump-android-version.py "$APP_GRADLE"
VN=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$APP_GRADLE" | head -1)
VC=$(grep -oP 'versionCode\s*=\s*\K\d+' "$APP_GRADLE" | head -1)

cd "$ROOT"
printf 'sdk.dir=/opt/android-sdk\n' > local.properties
# monorepo may use settings at root
./gradlew --no-daemon :apps:pss-tak:app:assembleDebug 2>/dev/null || \
  (cd apps/pss-tak && ./gradlew --no-daemon assembleDebug)

OUT=$(find apps/pss-tak/app/build/outputs/apk -name '*.apk' 2>/dev/null | head -1)
if [[ -z "${OUT:-}" ]]; then
  OUT=$(find "$ROOT" -path '*/pss-tak/app/build/outputs/apk/*.apk' | head -1)
fi
test -n "$OUT" && test -f "$OUT"

OUT_NAME="PSS-TAK-${VN}.apk"
cp -f "$OUT" "$DEST_DIR/$OUT_NAME"
ln -sfn "$OUT_NAME" "$DEST_DIR/PSS-TAK-latest.apk"
# Keep legacy filename for old links
cp -f "$OUT" "$DEST_DIR/OmniTAK-PSR-debug.apk"
chown www-data:www-data "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OmniTAK-PSR-debug.apk" 2>/dev/null || true
chmod 664 "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OmniTAK-PSR-debug.apk" 2>/dev/null || true
mkdir -p /opt/psr-client-build/out
cp -f "$DEST_DIR/$OUT_NAME" /opt/psr-client-build/out/
echo "OK: $DEST_DIR/$OUT_NAME (vc $VC)"
ls -lh "$DEST_DIR/$OUT_NAME"
