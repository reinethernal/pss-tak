#!/usr/bin/env bash
# Restore ПСС patches after an official OpenTAKServer upgrade.
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
install -m 644 "$ROOT/site-packages/blueprints/ots_api/psr_crm_api.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/mediamtx_api.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/psr_field_auth.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/blueprints/ots_api/__init__.py" "$SITE_PKG/blueprints/ots_api/"
install -m 644 "$ROOT/site-packages/psr_acl.py" "$SITE_PKG/psr_acl.py"
install -m 644 "$ROOT/site-packages/models/MissionTask.py" "$SITE_PKG/models/"
install -m 644 "$ROOT/site-packages/models/SearchSector.py" "$SITE_PKG/models/"
install -m 644 "$ROOT/site-packages/models/CrmModels.py" "$SITE_PKG/models/"
if [[ -f "$ROOT/site-packages/forms/MediaMTXPathConfig.py" ]]; then
  install -d "$SITE_PKG/forms"
  install -m 644 "$ROOT/site-packages/forms/MediaMTXPathConfig.py" "$SITE_PKG/forms/"
fi
if [[ -f "$ROOT/site-packages/models/VideoStream.py" ]]; then
  install -m 644 "$ROOT/site-packages/models/VideoStream.py" "$SITE_PKG/models/"
fi
if [[ -f "$ROOT/site-packages/blueprints/scheduled_jobs.py" ]]; then
  install -m 644 "$ROOT/site-packages/blueprints/scheduled_jobs.py" "$SITE_PKG/blueprints/"
fi

echo "==> Web assets / downloads"
install -d "$WWW/assets/js" "$WWW/downloads"
install -m 644 "$ROOT/www/assets/js/psr-map-ext.js" "$WWW/assets/js/"
[[ -f "$ROOT/www/assets/js/psr-hq-nav.js" ]] && install -m 644 "$ROOT/www/assets/js/psr-hq-nav.js" "$WWW/assets/js/"
# Sidebar «Обращения» (hash may change on UI upgrade)
DL=$(ls "$ROOT/www/assets/js"/DefaultLayout-*.js 2>/dev/null | head -1 || true)
if [[ -n "${DL:-}" ]]; then
  base=$(basename "$DL")
  if [[ -f "$WWW/assets/js/$base" ]]; then
    install -m 644 "$DL" "$WWW/assets/js/$base"
    echo "    $base restored (Обращения menu)"
  else
    CUR=$(ls "$WWW/assets/js"/DefaultLayout-*.js 2>/dev/null | head -1 || true)
    if [[ -n "${CUR:-}" ]]; then
      echo "WARN: DefaultLayout is $(basename "$CUR") — re-apply Обращения patch manually"
    fi
  fi
fi
for f in Users-CVA3KzTZ.js ClientApps-psr.js; do
  [[ -f "$ROOT/www/assets/js/$f" ]] && install -m 644 "$ROOT/www/assets/js/$f" "$WWW/assets/js/"
done
for f in psr-operation.html psr-sectors.html psr-start.txt psr-invite.html psr-invite-admin.html \
         psr-crm.html psr-report.html НАЧНИТЕ-ЗДЕСЬ.txt; do
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

# Ensure index loads psr-map-ext.js + psr-hq-nav.js if present
INDEX="$WWW/index.html"
if [[ -f "$INDEX" ]]; then
  if ! grep -q 'psr-map-ext.js' "$INDEX"; then
    if grep -q '</body>' "$INDEX"; then
      sed -i 's|</body>|<script src="/assets/js/psr-map-ext.js" defer></script>\n</body>|' "$INDEX"
      echo "    injected psr-map-ext.js into index.html"
    fi
  fi
  if ! grep -q 'psr-hq-nav.js' "$INDEX"; then
    if grep -q 'psr-map-ext.js' "$INDEX"; then
      sed -i 's|psr-map-ext.js\([^"]*\)" defer></script>|psr-map-ext.js\1" defer></script>\n    <script src="/assets/js/psr-hq-nav.js?v=1" defer></script>|' "$INDEX"
      echo "    injected psr-hq-nav.js into index.html"
    elif grep -q '</body>' "$INDEX"; then
      sed -i 's|</body>|<script src="/assets/js/psr-hq-nav.js?v=1" defer></script>\n</body>|' "$INDEX"
      echo "    injected psr-hq-nav.js into index.html"
    fi
  fi
fi

# truststore must be world-readable for nginx downloads
if [[ -f "$WWW/downloads/truststore-root.p12" ]]; then
  chmod 644 "$WWW/downloads/truststore-root.p12" || true
  chown www-data:www-data "$WWW/downloads/truststore-root.p12" 2>/dev/null || true
fi

chown -R ots:ots "$SITE_PKG/blueprints/ots_api/mission_ops_api.py" \
  "$SITE_PKG/blueprints/ots_api/search_sector_api.py" \
  "$SITE_PKG/blueprints/ots_api/track_api.py" \
  "$SITE_PKG/blueprints/ots_api/psr_crm_api.py" \
  "$SITE_PKG/psr_acl.py" \
  "$SITE_PKG/models/MissionTask.py" \
  "$SITE_PKG/models/SearchSector.py" \
  "$SITE_PKG/models/CrmModels.py" 2>/dev/null || true
chown www-data:www-data "$WWW/assets/js/psr-map-ext.js" \
  "$WWW/assets/js/psr-hq-nav.js" \
  "$WWW/downloads/psr-operation.html" \
  "$WWW/downloads/psr-crm.html" \
  "$WWW/downloads/psr-report.html" 2>/dev/null || true


# ПСС brand icons / favicons
if [[ -d "$ROOT/www/assets/pss-brand" ]]; then
  install -d "$WWW/assets/pss-brand"
  cp -a "$ROOT/www/assets/pss-brand/." "$WWW/assets/pss-brand/"
  if [[ -f "$ROOT/www/assets/pss-brand/app/favicon.ico" ]]; then
    install -m 644 "$ROOT/www/assets/pss-brand/app/favicon.ico" "$WWW/favicon.ico"
    install -m 644 "$ROOT/www/assets/pss-brand/app/icon-16.png" "$WWW/favicon-16x16.png"
    install -m 644 "$ROOT/www/assets/pss-brand/app/icon-32.png" "$WWW/favicon-32x32.png"
    install -m 644 "$ROOT/www/assets/pss-brand/app/icon-180.png" "$WWW/apple-touch-icon.png"
    install -m 644 "$ROOT/www/assets/pss-brand/app/icon-192.png" "$WWW/android-chrome-192x192.png"
    install -m 644 "$ROOT/www/assets/pss-brand/app/icon-512.png" "$WWW/android-chrome-512x512.png"
  fi
  chown -R www-data:www-data "$WWW/assets/pss-brand" 2>/dev/null || true
  echo "    pss-brand icons applied"
fi


# Login / shared OTS logo + Login title
for f in Login-BMT7lUyW.js apiRoutes-BhHdpDjR.js; do
  [[ -f "$ROOT/www/assets/js/$f" ]] && install -m 644 "$ROOT/www/assets/js/$f" "$WWW/assets/js/$f" && echo "    $f restored"
done
if [[ -f "$ROOT/www/assets/images/ots-logo-BovW17cF.png" ]]; then
  install -d "$WWW/assets/images"
  install -m 644 "$ROOT/www/assets/images/ots-logo-BovW17cF.png" "$WWW/assets/images/"
fi

echo "OK: overlay applied. Restart: systemctl restart opentakserver cot_parser"
