#!/usr/bin/env bash
# Copy APKs produced by GitHub Actions into the HQ /downloads folder.
# Does not compile anything.
set -euo pipefail
TOKEN_FILE="${GITHUB_TOKEN_FILE:-/root/.secrets/github-pss-tak.token}"
DEST_DIR="${DEST_DIR:-/var/www/html/opentakserver/downloads}"
REPO="${GITHUB_REPOSITORY:-reinethernal/pss-tak}"

test -f "$TOKEN_FILE" || { echo "missing $TOKEN_FILE"; exit 1; }
TOKEN="$(tr -d '\n' < "$TOKEN_FILE")"
API="https://api.github.com/repos/${REPO}"
AUTH=(-H "Authorization: Bearer ${TOKEN}" -H "Accept: application/vnd.github+json")

if [[ -n "${RUN_ID:-}" ]]; then
  run_id="$RUN_ID"
else
  run_id="$(curl -sS "${AUTH[@]}" "${API}/actions/runs?branch=main&status=success&per_page=20" | python3 -c '
import json, sys
data = json.load(sys.stdin)
for r in data.get("workflow_runs") or []:
    if r.get("name") == "CI" and r.get("head_branch") == "main" and r.get("conclusion") == "success":
        print(r["id"])
        raise SystemExit(0)
raise SystemExit("no successful CI run on main")
')"
fi

echo "CI run $run_id"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -sS -L "${AUTH[@]}" "${API}/actions/runs/${run_id}/artifacts" | python3 -c '
import json, sys
wanted = {"apk-pss-tak", "apk-opentak-icu"}
data = json.load(sys.stdin)
found = []
for a in data.get("artifacts") or []:
    if a["name"] in wanted and not a.get("expired"):
        found.append((a["id"], a["name"]))
if {n for _, n in found} != wanted:
    raise SystemExit("missing APK artifacts on CI run")
for aid, name in found:
    print(f"{aid} {name}")
' | while read -r id name; do
  zip="$tmp/${name}.zip"
  curl -sS -L "${AUTH[@]}" "${API}/actions/artifacts/${id}/zip" -o "$zip"
  mkdir -p "$tmp/$name"
  python3 - "$zip" "$tmp/$name" <<'PY'
import sys, zipfile
zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])
PY
done

tak="$(find "$tmp/apk-pss-tak" -name '*.apk' -type f | head -1)"
icu="$(find "$tmp/apk-opentak-icu" -name '*.apk' -type f | head -1)"
test -n "$tak" && test -n "$icu" || { echo "missing APKs in artifacts"; find "$tmp" -type f; exit 1; }

mkdir -p "$DEST_DIR"
install -m 664 "$tak" "$DEST_DIR/PSS-TAK-latest.apk"
install -m 664 "$icu" "$DEST_DIR/OpenTAK_ICU-PSR-latest.apk"
python3 - "$tak" "$icu" "$DEST_DIR" <<'PY'
import os, re, shutil, subprocess, sys
tak, icu, dest = sys.argv[1:4]
aapt = None
for cand in (
    os.environ.get("AAPT"),
    "/opt/android-sdk/build-tools/35.0.0/aapt",
):
    if cand and os.path.isfile(cand):
        aapt = cand
        break
if not aapt:
    raise SystemExit(0)
def vn(apk):
    out = subprocess.check_output([aapt, "dump", "badging", apk], text=True, stderr=subprocess.DEVNULL)
    m = re.search(r"versionName='([^']+)'", out)
    return m.group(1) if m else "unknown"
shutil.copy2(tak, os.path.join(dest, f"PSS-TAK-{vn(tak)}.apk"))
shutil.copy2(icu, os.path.join(dest, f"OpenTAK_ICU-PSR-{vn(icu)}.apk"))
PY
chown www-data:www-data "$DEST_DIR/PSS-TAK-latest.apk" "$DEST_DIR/OpenTAK_ICU-PSR-latest.apk" 2>/dev/null || true
ls -lh "$DEST_DIR/PSS-TAK-latest.apk" "$DEST_DIR/OpenTAK_ICU-PSR-latest.apk"
echo "OK: /downloads refreshed from GitHub Actions (no local Gradle)"
