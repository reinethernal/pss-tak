#!/usr/bin/env bash
# Copy live OTS ПСС patches into this overlay mirror (for git commit).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SITE_PKG="${OTS_SITE_PKG:-/home/ots/.opentakserver_venv/lib/python3.11/site-packages/opentakserver}"
WWW="${OTS_WWW:-/var/www/html/opentakserver}"

mkdir -p "$ROOT/site-packages/blueprints/ots_api" "$ROOT/site-packages/models" \
  "$ROOT/site-packages/forms" "$ROOT/site-packages/blueprints" \
  "$ROOT/www/assets/js" "$ROOT/www/downloads"

cp -a "$SITE_PKG/blueprints/ots_api/mission_ops_api.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/blueprints/ots_api/search_sector_api.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/blueprints/ots_api/track_api.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/blueprints/ots_api/psr_invite_api.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/blueprints/ots_api/psr_crm_api.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/blueprints/ots_api/mediamtx_api.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/blueprints/ots_api/psr_field_auth.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/blueprints/ots_api/__init__.py" "$ROOT/site-packages/blueprints/ots_api/"
cp -a "$SITE_PKG/psr_acl.py" "$ROOT/site-packages/psr_acl.py"
cp -a "$SITE_PKG/models/MissionTask.py" "$ROOT/site-packages/models/"
cp -a "$SITE_PKG/models/SearchSector.py" "$ROOT/site-packages/models/"
cp -a "$SITE_PKG/models/CrmModels.py" "$ROOT/site-packages/models/"
cp -a "$SITE_PKG/forms/MediaMTXPathConfig.py" "$ROOT/site-packages/forms/"
cp -a "$SITE_PKG/models/VideoStream.py" "$ROOT/site-packages/models/"
cp -a "$SITE_PKG/blueprints/scheduled_jobs.py" "$ROOT/site-packages/blueprints/"
cp -a "$WWW/assets/js/psr-map-ext.js" "$ROOT/www/assets/js/"
[[ -f "$WWW/assets/js/psr-hq-nav.js" ]] && cp -a "$WWW/assets/js/psr-hq-nav.js" "$ROOT/www/assets/js/"
CUR=$(ls "$WWW/assets/js"/DefaultLayout-*.js 2>/dev/null | head -1 || true)
[[ -n "${CUR:-}" ]] && cp -a "$CUR" "$ROOT/www/assets/js/"
[[ -f "$WWW/assets/js/Map-CpwYNoVG.js" ]] && cp -a "$WWW/assets/js/Map-CpwYNoVG.js" "$ROOT/www/assets/js/"
[[ -f "$WWW/assets/js/Users-CVA3KzTZ.js" ]] && cp -a "$WWW/assets/js/Users-CVA3KzTZ.js" "$ROOT/www/assets/js/"
[[ -f "$WWW/assets/js/ClientApps-psr.js" ]] && cp -a "$WWW/assets/js/ClientApps-psr.js" "$ROOT/www/assets/js/"
for f in psr-operation.html psr-sectors.html psr-start.txt psr-invite.html psr-invite-admin.html \
         psr-crm.html psr-report.html НАЧНИТЕ-ЗДЕСЬ.txt; do
  [[ -f "$WWW/downloads/$f" ]] && cp -a "$WWW/downloads/$f" "$ROOT/www/downloads/"
done
echo "OK: synced live → $ROOT"

[[ -d "$WWW/assets/pss-brand" ]] && mkdir -p "$ROOT/www/assets/pss-brand" && cp -a "$WWW/assets/pss-brand/." "$ROOT/www/assets/pss-brand/"
