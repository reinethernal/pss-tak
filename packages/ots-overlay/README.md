# PSR overlay for OpenTAKServer

Official OTS upgrade runs `pip install -U` (wipes `site-packages/opentakserver/`) and
`rm -fr /var/www/html/opentakserver/*` (wipes UI + `downloads/`). Postgres and
`~/ots/config.yml` survive.

This folder is the **source of truth** for ПСР patches. After every OTS upgrade:

```bash
sudo /opt/psr-client-build/pss-tak/packages/ots-overlay/apply-psr-ots-overlay.sh
sudo systemctl restart opentakserver cot_parser
```

Before committing client-repo changes that touch live patches:

```bash
/opt/psr-client-build/pss-tak/packages/ots-overlay/sync-from-live.sh
```

**Map chunk:** `Map-*.js` filename hash changes when OpenTAKServer-UI updates.
`apply-psr-ots-overlay.sh` copies `Map-CpwYNoVG.js` if present, then injects
`__OTS_MAP__` hooks into whichever `Map-*.js` is current when the exact file is missing.

**Included ПСР modules:** mission ops, search sectors, tracks, invite, CRM (`psr_crm_api` +
`CrmModels`), HQ ACL (`psr_acl`), mediamtx auto-record, web pages
(`psr-operation`, `psr-crm`, `psr-report`, invite), `psr-map-ext.js`, `psr-hq-nav.js`.

