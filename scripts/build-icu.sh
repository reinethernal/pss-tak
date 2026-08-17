#!/usr/bin/env bash
# Build ПСР Видео (psr + legacy compat flavors) and publish to /downloads/
set -euo pipefail
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
export PSR_UPLOAD_KEYSTORE="${PSR_UPLOAD_KEYSTORE:-$HOME/.android/debug.keystore}"

ROOT=/opt/psr-client-build/pss-tak/apps/opentak-icu
APP_GRADLE="$ROOT/app/build.gradle.kts"
DEST_DIR=/var/www/html/opentakserver/downloads
AAPT=$(ls -d "$ANDROID_HOME"/build-tools/*/aapt | sort -V | tail -1)

bash /opt/psr-client-build/pss-tak/scripts/psr-upload-keystore.sh

[[ -f "$APP_GRADLE" ]] || { echo "missing $APP_GRADLE"; exit 1; }

python3 /opt/psr-client-build/scripts/bump-android-version.py "$APP_GRADLE"
VN=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$APP_GRADLE" | head -1)
VC=$(grep -oP 'versionCode\s*=\s*\K\d+' "$APP_GRADLE" | head -1)

cd "$ROOT"
printf 'sdk.dir=/opt/android-sdk\n' > local.properties
./gradlew --no-daemon assemblePsrDebug assembleCompatDebug

PSR_APK=$(ls -t app/build/outputs/apk/psr/debug/*.apk | head -1)
COMPAT_APK=$(ls -t app/build/outputs/apk/compat/debug/*.apk | head -1)
test -f "$PSR_APK" && test -f "$COMPAT_APK"

"$AAPT" dump badging "$PSR_APK" | grep "package: name='ru.plasmadancer.psr.icu'"
"$AAPT" dump badging "$COMPAT_APK" | grep "package: name='io.opentakserver.opentakicu.debug'"

OUT_NAME="OpenTAK_ICU-PSR-${VN}.apk"
cp -f "$PSR_APK" "$DEST_DIR/$OUT_NAME"
ln -sfn "$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-latest.apk"
cp -f "$COMPAT_APK" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk"
chown www-data:www-data "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk" 2>/dev/null || true
chmod 664 "$DEST_DIR/$OUT_NAME" "$DEST_DIR/OpenTAK_ICU-PSR-debug.apk" 2>/dev/null || true
ls -lh "$DEST_DIR/$OUT_NAME"
echo "OK: psr=$VN compat vc=$VC published"
