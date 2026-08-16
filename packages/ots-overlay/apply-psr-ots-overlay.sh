#!/usr/bin/env bash
# Restore ПСР patches after an official OpenTAKServer upgrade.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SITE_PKG="${OTS_SITE_PKG:-/home/ots/.opentakserver_venv/lib/python3.11/site-packages/opentakserver}"
WWW="${OTS_WWW:-/var/www/html/opentakserver}"

die() { echo "ERROR: $*" >&2; exit 1; }
[[ -d "$SITE_PKG" ]] || die "site-packages not found: $SITE_PKG"
[[ -d "$WWW" ]] || die "www not found: $WWW"

echo "==> Python blueprints / models"
install -d "$SITE_PKG/blueprints/ots_api" "$SITE_PKG/models"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/mission_ops_api.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/search_sector_api.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/track_api.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/psr_invite_api.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/__init__.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/models/MissionTask.py" "$SITE_PKG/models/"
install -m 644 "$ROOT/site-packages/models/SearchSector.py" "$SITE_PKG/models/"
if [[ -f "$ROOT/site-packages/blueprints/scheduled_jobs.py" ]]; then
  install -m 644 "$ROOT/site-packages/blueprints/scheduled_jobs.py" "$SITE_PKG/blueprints/"
fi

echo "==> Web assets / downloads"
install -d "$WWW/assets/js" "$WWW/downloads"
install -m 644 "$ROOT/www/assets/js/psr-map-ext.js" "$WWW/assets/js/"
for f in Users-CVA3KzTZ.js ClientApps-psr.js; do
  [[ -f "$ROOT/www/assets/js/$f" ]] && install -m 644 "$ROOT/www/assets/js/$f" "$WWW/assets/js/"
done
for f in psr-operation.html psr-sectors.html psr-start.txt psr-invite.html psr-invite-admin.html НАЧНИТЕ-ЗДЕСЬ.txt; do
  [[ -f "$ROOT/www/downloads/$f" ]] && install -m 644 "$ROOT/www/downloads/$f" "$WWW/downloads/"
done

MAP_SRC="$ROOT/www/assets/js/Map-CpwYNoVG.js"
if [[ -f "$MAP_SRC" ]]; then
  if [[ -f "$WWW/assets/js/Map-CpwYNoVG.js" ]]; then
    install -m 644 "$MAP_SRC" "$WWW/assets/js/Map-CpwYNoVG.js"
    echo "    Map-CpwYNoVG.js restored"
  else
    # UI rename: try to inject hooks into the current Map chunk
    CUR=$(ls "$WWW/assets/js"/Map-*.js 2>/dev/null | head -1 || true)
    if [[ -n "${CUR:-}" ]] && ! grep -q '__OTS_MAP__' "$CUR"; then
      echo "WARN: Map chunk is $(basename "$CUR") — apply map hooks manually from README / previous Map-CpwYNoVG.js"
    elif [[ -n "${CUR:-}" ]]; then
      echo "    $(basename "$CUR") already has __OTS_MAP__"
    fi
  fi
fi

# Ensure index loads psr-map-ext.js if present
INDEX="$WWW/index.html"
if [[ -f "$INDEX" ]] && ! grep -q 'psr-map-ext.js' "$INDEX"; then
  if grep -q '</body>' "$INDEX"; then
    sed -i 's|</body>|<script src="/assets/js/psr-map-ext.js" defer></script>\n</body>|' "$INDEX"
    echo "    injected psr-map-ext.js into index.html"
  fi
fi

chown -R ots:ots "$SITE_PKG/blueprints/ots_api/mission_ops_api.py" \
  "$SITE_PKG/blueprints/ots_api/search_sector_api.py" \
  "$SITE_PKG/blueprints/ots_api/track_api.py" \
  "$SITE_PKG/models/MissionTask.py" \
  "$SITE_PKG/models/SearchSector.py" 2>/dev/null || true
chown www-data:www-data "$WWW/assets/js/psr-map-ext.js" "$WWW/downloads/psr-operation.html" 2>/dev/null || true

echo "OK: overlay applied. Restart: systemctl restart opentakserver cot_parser"
