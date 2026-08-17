#!/usr/bin/env bash
# Bump minor versionName + versionCode when this app tree changed on main.
# Used by GitHub Actions only. Does not assemble.
set -euo pipefail
APP="${1:?usage: ci-bump-if-apps-changed.sh pss-tak|opentak-icu}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GIT=(git -c "safe.directory=$ROOT" -C "$ROOT")
case "$APP" in
  pss-tak)
    PREFIX="apps/pss-tak/"
    GRADLE="$ROOT/apps/pss-tak/app/build.gradle.kts"
    ;;
  opentak-icu)
    PREFIX="apps/opentak-icu/"
    GRADLE="$ROOT/apps/opentak-icu/app/build.gradle.kts"
    ;;
  *)
    echo "unknown app: $APP" >&2
    exit 2
    ;;
esac

if [[ "${GITHUB_EVENT_NAME:-}" != "push" || "${GITHUB_REF:-}" != "refs/heads/main" ]]; then
  echo "skip bump ($APP): not a push to main"
  exit 0
fi

msg="$("${GIT[@]}" log -1 --format=%s)"
if [[ "$msg" == "chore(release):"* ]]; then
  echo "skip bump ($APP): release commit"
  exit 0
fi

BEFORE="${GITHUB_EVENT_BEFORE:-}"
SHA="${GITHUB_SHA:-HEAD}"
changed=0
if [[ -z "$BEFORE" || "$BEFORE" =~ ^0+$ ]]; then
  changed=1
elif "${GIT[@]}" diff --name-only "$BEFORE" "$SHA" | grep -q "^${PREFIX}"; then
  changed=1
fi

if [[ "$changed" -eq 0 ]]; then
  echo "skip bump ($APP): ${PREFIX} unchanged"
  exit 0
fi

python3 "$ROOT/scripts/bump-android-version.py" "$GRADLE"
