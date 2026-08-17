#!/usr/bin/env bash
# Create or reuse the stable PSR upload keystore (same cert as fts downloads APKs).
set -euo pipefail
KS="${PSR_UPLOAD_KEYSTORE:-$HOME/.android/debug.keystore}"
mkdir -p "$(dirname "$KS")"

if [[ -n "${PSR_UPLOAD_KEYSTORE_B64:-}" ]]; then
  echo "$PSR_UPLOAD_KEYSTORE_B64" | base64 -d > "$KS"
  chmod 600 "$KS"
  echo "OK: decoded upload keystore from PSR_UPLOAD_KEYSTORE_B64 → $KS"
  keytool -list -keystore "$KS" -storepass android 2>/dev/null | head -5 || true
  exit 0
fi

if [[ -f "$KS" ]]; then
  echo "OK: upload keystore already exists: $KS"
  keytool -list -keystore "$KS" -storepass android 2>/dev/null | head -5 || true
  exit 0
fi

echo "ERROR: no upload keystore at $KS and PSR_UPLOAD_KEYSTORE_B64 is unset." >&2
echo "Copy ~/.android/debug.keystore from the build server or set PSR_UPLOAD_KEYSTORE_B64." >&2
exit 1
