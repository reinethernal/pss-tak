#!/usr/bin/env bash
# APKs are produced only by GitHub Actions — do not assemble on this host.
set -euo pipefail
echo "Local Gradle builds are disabled."
echo "Push to main; GitHub Actions (.github/workflows/ci.yml) builds APKs and publishes F-Droid."
echo "To copy CI artifacts into /downloads:  bash scripts/sync-apks-from-ci.sh"
exit 1
