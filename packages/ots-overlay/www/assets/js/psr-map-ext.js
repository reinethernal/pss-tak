/**
 * ПСР штаб на основной карте: фильтры точек, Leaflet.Draw секторов, список CRUD.
 * Загружается из index.html после OTS UI. Слетает при апдейте OTS.
 */
(function () {
  const FILTERS = [
    { code: "all", label: "Все" },
    { code: "lkp", label: "Последнее известное" },
    { code: "pls", label: "Где видели" },
    { code: "ipp", label: "Старт планирования" },
    { code: "checked", label: "Проверено" },
    { code: "danger", label: "Опасность" },
    { code: "rally", label: "Сборка" },
  ];

  let activeFilter = "all";
  let bar = null;
  let panel = null;
  let drawReady = false;
  let drawnGroup = null;
  let pendingLayer = null;
  let missionsCache = [];
  let sectorsCache = [];
  let tracksEnabled = false;
  let tracksWindow = "6h";
  let tracksFilterUid = "";
  let trackLayerGroup = null;
  let trackPolylines = {}; // device_uid -> { layer, callsign, lastTsMs }
  let tracksRefreshTimer = null;
  let socketHooked = false;
  let dueBanner = null;
  let duePollTimer = null;
  let panelOpen = false;
  let toggleBtn = null;
  let historyHooked = false;
  const TRACK_MIN_INTERVAL_MS = 8000;

  function api(path, opts) {
    return fetch(path, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...(opts && opts.headers) },
      ...opts,
    });
  }

  function detectPsr(text) {
    if (!text) return null;
    const m = String(text).match(/psr:([a-z]+)/i);
    if (m) return m[1].toLowerCase();
    const up = String(text).toUpperCase();
    for (const c of ["LKP", "PLS", "IPP", "CHECKED", "DANGER", "RALLY"]) {
      if (up === c || up.startsWith(c + " ") || up.includes(c)) return c.toLowerCase();
    }
    return null;
  }

  function applyFilter() {
    const map = window.__OTS_MAP__;
    if (!map || !map.eachLayer) return;
    map.eachLayer((layer) => {
      if (!(layer.getLatLng && !layer.getLatLngs)) return;
      const tip = layer.getTooltip && layer.getTooltip();
      const content = tip && (tip.getContent ? tip.getContent() : tip._content);
      const text = typeof content === "string" ? content : (content && content.textContent) || "";
      const code = detectPsr(text);
      const el = layer.getElement && layer.getElement();
      if (activeFilter === "all") {
        if (layer.setOpacity) layer.setOpacity(1);
        if (el) el.style.display = "";
      } else if (code === activeFilter) {
        if (layer.setOpacity) layer.setOpacity(1);
        if (el) el.style.display = "";
      } else {
        if (layer.setOpacity) layer.setOpacity(0.08);
        if (el) el.style.display = code ? "none" : "";
        if (!code && layer.setOpacity) layer.setOpacity(0.2);
      }
    });
  }

  function loadScript(src) {
    return new Promise((resolve, reject) => {
      if (document.querySelector('script[src="' + src + '"]')) return resolve();
      const s = document.createElement("script");
      s.src = src;
      s.onload = resolve;
      s.onerror = reject;
      document.head.appendChild(s);
    });
  }

  function loadCss(href) {
    if (document.querySelector('link[href="' + href + '"]')) return;
    const l = document.createElement("link");
    l.rel = "stylesheet";
    l.href = href;
    document.head.appendChild(l);
  }

  async function ensureDraw() {
    const map = window.__OTS_MAP__;
    if (!map || !window.L || drawReady) return drawReady;
    if (!L.Control || !L.Control.Draw) {
      loadCss("https://unpkg.com/leaflet-draw@1.0.4/dist/leaflet.draw.css");
      await loadScript("https://unpkg.com/leaflet-draw@1.0.4/dist/leaflet.draw.js");
    }
    if (!L.Control.Draw) return false;
    drawnGroup = new L.FeatureGroup();
    map.addLayer(drawnGroup);
    const ctrl = new L.Control.Draw({
      position: "topleft",
      draw: {
        polygon: { allowIntersection: false, showArea: true, shapeOptions: { color: "#00bcd4" } },
        polyline: false,
        rectangle: false,
        circle: false,
        marker: false,
        circlemarker: false,
      },
      edit: { featureGroup: drawnGroup, remove: true },
    });
    map.addControl(ctrl);
    map.on(L.Draw.Event.CREATED, (e) => {
      drawnGroup.clearLayers();
      pendingLayer = e.layer;
      drawnGroup.addLayer(pendingLayer);
      openSaveForm();
    });
    drawReady = true;
    return true;
  }

  function ringFromLayer(layer) {
    const latlngs = layer.getLatLngs()[0];
    return latlngs.map((ll) => [ll.lat, ll.lng]);
  }

  async function loadMissions() {
    try {
      const res = await api("/api/missions");
      if (!res.ok) return;
      const data = await res.json();
      missionsCache = data.results || data.missions || data || [];
      if (!Array.isArray(missionsCache)) missionsCache = [];
    } catch (_) {
      missionsCache = [];
    }
  }

  function colorForCallsign(cs) {
    let h = 0;
    const s = String(cs || "");
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    const hue = h % 360;
    return "hsl(" + hue + ",70%,55%)";
  }

  function sinceIsoForWindow() {
    const now = Date.now();
    if (tracksWindow === "1h") return new Date(now - 3600e3).toISOString();
    if (tracksWindow === "24h") return new Date(now - 86400e3).toISOString();
    if (tracksWindow === "shift") {
      const inp = panel && panel.querySelector("#psr-track-shift");
      const v = inp && inp.value;
      if (v) {
        const d = new Date(v);
        if (!isNaN(d.getTime())) return d.toISOString();
      }
      const start = new Date();
      start.setHours(8, 0, 0, 0);
      if (start.getTime() > now) start.setDate(start.getDate() - 1);
      return start.toISOString();
    }
    return new Date(now - 6 * 3600e3).toISOString();
  }

  function ensureTrackLayer() {
    const map = window.__OTS_MAP__;
    if (!map || !window.L) return null;
    if (!trackLayerGroup) {
      trackLayerGroup = L.layerGroup();
    }
    if (tracksEnabled && !map.hasLayer(trackLayerGroup)) map.addLayer(trackLayerGroup);
    if (!tracksEnabled && map.hasLayer(trackLayerGroup)) map.removeLayer(trackLayerGroup);
    return trackLayerGroup;
  }

  function clearTracks() {
    Object.keys(trackPolylines).forEach((uid) => {
      const t = trackPolylines[uid];
      if (trackLayerGroup && t && t.layer) trackLayerGroup.removeLayer(t.layer);
    });
    trackPolylines = {};
  }

  function bindTrackTooltip(layer, callsign, pts) {
    const first = pts[0] && pts[0].ts ? pts[0].ts : "";
    const last = pts[pts.length - 1] && pts[pts.length - 1].ts ? pts[pts.length - 1].ts : "";
    layer.bindTooltip(
      (callsign || "EUD") + "<br/><small>" + first + " → " + last + "</small>",
      { sticky: true }
    );
  }

  async function loadTracks() {
    if (!tracksEnabled) return;
    const map = window.__OTS_MAP__;
    if (!map || !window.L) return;
    ensureTrackLayer();
    const since = sinceIsoForWindow();
    let url = "/api/tracks?since=" + encodeURIComponent(since) + "&min_interval_s=8&max_points=2000";
    if (tracksFilterUid) url += "&uid=" + encodeURIComponent(tracksFilterUid);
    try {
      const res = await api(url);
      if (res.status === 401) {
        setStatus("Войдите в кабинет, чтобы видеть треки");
        return;
      }
      if (!res.ok) return;
      const data = await res.json();
      clearTracks();
      (data.results || []).forEach((tr) => {
        const latlngs = (tr.points || [])
          .filter((p) => p.lat != null && p.lon != null)
          .map((p) => [p.lat, p.lon]);
        if (latlngs.length < 2) return;
        const color = colorForCallsign(tr.callsign || tr.device_uid);
        const layer = L.polyline(latlngs, { color, weight: 3, opacity: 0.85 });
        bindTrackTooltip(layer, tr.callsign, tr.points);
        trackLayerGroup.addLayer(layer);
        const last = tr.points[tr.points.length - 1];
        const lastTs = last && last.ts ? Date.parse(last.ts) : 0;
        trackPolylines[tr.device_uid] = {
          layer,
          callsign: tr.callsign,
          lastTsMs: isNaN(lastTs) ? 0 : lastTs,
        };
      });
      const n = Object.keys(trackPolylines).length;
      setStatus(n ? "Треки: " + n + " (с " + since.slice(0, 16) + ")" : "Нет треков за окно");
      updateKmlLink(since);
    } catch (_) {
      setStatus("Ошибка загрузки треков");
    }
  }

  function updateKmlLink(since) {
    const a = panel && panel.querySelector("#psr-track-kml");
    if (!a) return;
    const uid = tracksFilterUid || Object.keys(trackPolylines)[0] || "";
    if (!uid) {
      a.href = "#";
      a.style.opacity = "0.5";
      a.onclick = (e) => {
        e.preventDefault();
        setStatus("Выберите uid в фильтре или дождитесь треков");
      };
      return;
    }
    a.style.opacity = "1";
    a.onclick = null;
    a.href =
      "/Marti/ExportMissionKML?uid=" +
      encodeURIComponent(uid) +
      "&startTime=" +
      encodeURIComponent(since) +
      "&format=kml";
  }

  function appendLivePoint(payload) {
    if (!tracksEnabled || !payload) return;
    const uid = payload.device_uid || (payload.eud && payload.eud.uid) || payload.uid;
    const lat = payload.latitude != null ? payload.latitude : payload.lat;
    const lon = payload.longitude != null ? payload.longitude : payload.lon;
    if (!uid || lat == null || lon == null) return;
    if (tracksFilterUid && uid !== tracksFilterUid) return;
    ensureTrackLayer();
    const now = Date.now();
    let entry = trackPolylines[uid];
    if (!entry) {
      if (!window.L || !trackLayerGroup) return;
      const cs = (payload.eud && payload.eud.callsign) || payload.callsign || uid;
      const layer = L.polyline([[lat, lon]], {
        color: colorForCallsign(cs),
        weight: 3,
        opacity: 0.85,
      });
      layer.bindTooltip(cs);
      trackLayerGroup.addLayer(layer);
      entry = { layer, callsign: cs, lastTsMs: now };
      trackPolylines[uid] = entry;
      return;
    }
    if (entry.lastTsMs && now - entry.lastTsMs < TRACK_MIN_INTERVAL_MS) return;
    entry.layer.addLatLng([lat, lon]);
    entry.lastTsMs = now;
  }

  function hookSocketPoints() {
    if (socketHooked) return;
    const sock = window.__OTS_SOCKET__ || window.socket || (window.io && window.io.socket);
    if (sock && typeof sock.on === "function") {
      sock.on("point", (p) => appendLivePoint(p));
      socketHooked = true;
      return;
    }
    // Fallback: poll while enabled
  }

  function setTracksEnabled(on) {
    tracksEnabled = !!on;
    ensureTrackLayer();
    if (!tracksEnabled) {
      clearTracks();
      if (tracksRefreshTimer) {
        clearInterval(tracksRefreshTimer);
        tracksRefreshTimer = null;
      }
      setStatus("");
      return;
    }
    hookSocketPoints();
    loadTracks();
    if (!tracksRefreshTimer) {
      tracksRefreshTimer = setInterval(() => {
        if (tracksEnabled) loadTracks();
      }, 45000);
    }
  }

  async function loadSectors() {
    try {
      const res = await api("/api/search_sectors");
      if (!res.ok) return;
      const data = await res.json();
      sectorsCache = data.results || [];
      renderSectorList();
    } catch (_) {}
  }

  function openSaveForm(existing) {
    ensurePanel(true);
    setPanelOpen(true);
    const form = panel.querySelector("#psr-sector-form");
    form.style.display = "block";
    form.dataset.uid = existing && existing.uid ? existing.uid : "";
    form.querySelector("[name=name]").value = (existing && existing.name) || "Сектор";
    form.querySelector("[name=assigned_to]").value = (existing && existing.assigned_to) || "";
    form.querySelector("[name=status]").value = (existing && existing.status) || "active";
    const sel = form.querySelector("[name=mission_name]");
    sel.innerHTML = '<option value="">— операция —</option>';
    missionsCache.forEach((m) => {
      const name = m.name || m;
      const o = document.createElement("option");
      o.value = name;
      o.textContent = name;
      if (existing && existing.mission_name === name) o.selected = true;
      sel.appendChild(o);
    });
    panel.querySelector("#psr-form-title").textContent = existing ? "Изменить сектор" : "Новый сектор";
  }

  async function saveSector(ev) {
    ev.preventDefault();
    const form = panel.querySelector("#psr-sector-form");
    const uid = form.dataset.uid;
    const body = {
      name: form.querySelector("[name=name]").value || "Сектор",
      assigned_to: form.querySelector("[name=assigned_to]").value || null,
      mission_name: form.querySelector("[name=mission_name]").value || null,
      status: form.querySelector("[name=status]").value || "active",
    };
    if (!uid && pendingLayer) {
      body.coordinates = ringFromLayer(pendingLayer);
    } else if (uid) {
      const prev = sectorsCache.find((s) => s.uid === uid);
      if (pendingLayer) body.coordinates = ringFromLayer(pendingLayer);
      else if (prev) body.coordinates = prev.coordinates;
    }
    if (!body.coordinates || body.coordinates.length < 3) {
      setStatus("Нужен полигон (≥3 точек)");
      return;
    }
    setStatus("Сохранение…");
    try {
      const res = await api(uid ? "/api/search_sectors/" + encodeURIComponent(uid) : "/api/search_sectors", {
        method: uid ? "PATCH" : "POST",
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok || data.success === false) {
        setStatus(data.error || "Ошибка");
        return;
      }
      setStatus("Сохранено");
      form.style.display = "none";
      if (drawnGroup) drawnGroup.clearLayers();
      pendingLayer = null;
      loadSectors();
    } catch (_) {
      setStatus("Ошибка сети / войдите в кабинет");
    }
  }

  function renderSectorList() {
    ensurePanel();
    const list = panel.querySelector("#psr-sector-list");
    list.innerHTML = "";
    if (!sectorsCache.length) {
      list.innerHTML = '<div style="opacity:.7;font-size:13px">Секторов пока нет — нарисуйте полигон инструментом слева</div>';
      return;
    }
    sectorsCache.forEach((s) => {
      const row = document.createElement("div");
      row.style.cssText = "border:1px solid #444;border-radius:6px;padding:8px;margin-bottom:6px;background:#2a2a2a";
      const st = s.status === "cleared" ? "пройден" : "активен";
      row.innerHTML =
        "<div style='font-weight:600'>" +
        (s.name || "Сектор") +
        "</div><div style='font-size:12px;opacity:.85'>" +
        [s.assigned_to ? "→ " + s.assigned_to : "", s.mission_name ? "оп: " + s.mission_name : "", st]
          .filter(Boolean)
          .join(" · ") +
        "</div>";
      const actions = document.createElement("div");
      actions.style.cssText = "display:flex;gap:6px;margin-top:6px;flex-wrap:wrap";
      const btnShow = document.createElement("button");
      btnShow.textContent = "На карте";
      btnShow.onclick = () => {
        const map = window.__OTS_MAP__;
        const layer = window.__OTS_PSR_SEC_MAP__ && window.__OTS_PSR_SEC_MAP__[s.uid];
        if (map && layer && layer.getBounds) map.fitBounds(layer.getBounds(), { padding: [40, 40] });
      };
      const btnEdit = document.createElement("button");
      btnEdit.textContent = "Изменить";
      btnEdit.onclick = () => openSaveForm(s);
      const btnClear = document.createElement("button");
      btnClear.textContent = s.status === "cleared" ? "Активен" : "Пройден";
      btnClear.onclick = async () => {
        await api("/api/search_sectors/" + encodeURIComponent(s.uid), {
          method: "PATCH",
          body: JSON.stringify({ status: s.status === "cleared" ? "active" : "cleared" }),
        });
        loadSectors();
      };
      const btnDel = document.createElement("button");
      btnDel.textContent = "Удалить";
      btnDel.style.background = "#a33";
      btnDel.onclick = async () => {
        if (!confirm("Удалить сектор «" + s.name + "»?")) return;
        await api("/api/search_sectors/" + encodeURIComponent(s.uid), { method: "DELETE" });
        loadSectors();
      };
      [btnShow, btnEdit, btnClear, btnDel].forEach((b) => {
        b.type = "button";
        b.style.cssText +=
          ";border:1px solid #555;background:#333;color:#eee;padding:4px 8px;border-radius:4px;cursor:pointer;font-size:12px";
        if (b === btnDel) b.style.background = "#a33";
        actions.appendChild(b);
      });
      row.appendChild(actions);
      list.appendChild(row);
    });
  }

  function formatCountdown(secs) {
    if (secs == null) return "";
    const neg = secs < 0;
    let s = Math.abs(Math.floor(secs));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    let t = h > 0 ? h + "ч " + m + "м" : m + "м";
    return neg ? "просрочено " + t : "осталось " + t;
  }

  async function refreshDueBanner() {
    try {
      const res = await api("/api/psr/tasks/due");
      if (!res.ok) return;
      const data = await res.json();
      const overdue = data.overdue || [];
      const warning = data.warning || [];
      if (!dueBanner || !document.body.contains(dueBanner)) {
        dueBanner = document.createElement("div");
        dueBanner.id = "psr-due-banner";
        document.body.appendChild(dueBanner);
      }
      if (!overdue.length && !warning.length) {
        dueBanner.style.display = "none";
        return;
      }
      const isOd = overdue.length > 0;
      const rows = (isOd ? overdue : warning).slice(0, 4);
      dueBanner.style.cssText =
        "position:fixed;left:12px;right:360px;bottom:16px;top:auto;z-index:900;padding:8px 12px;border-radius:8px;font:13px system-ui,sans-serif;pointer-events:auto;" +
        (isOd
          ? "background:rgba(80,20,20,.95);border:1px solid #c44;color:#fee;"
          : "background:rgba(70,55,10,.95);border:1px solid #c90;color:#ffe;");
      dueBanner.style.display = "block";
      dueBanner.innerHTML =
        "<strong>" +
        (isOd ? "Просрочен возврат" : "Скоро срок возврата") +
        " (" +
        (isOd ? overdue.length : warning.length) +
        "):</strong> " +
        rows
          .map(
            (t) =>
              (t.mission_name ? "[" + t.mission_name + "] " : "") +
              (t.title || "") +
              (t.assignee ? " → " + t.assignee : "") +
              " · " +
              formatCountdown(t.seconds_to_return)
          )
          .join(" · ") +
        " <a href='/downloads/psr-operation.html' style='color:#8cf;margin-left:8px'>задания →</a>";
    } catch (_) {}
  }

  function startDuePoll() {
    refreshDueBanner();
    if (duePollTimer) clearInterval(duePollTimer);
    duePollTimer = setInterval(refreshDueBanner, 30000);
  }

  function isMapPage() {
    const p = (location.pathname || "").toLowerCase();
    return p === "/map" || p.endsWith("/map") || /\/map(\/|$|\?)/.test(p);
  }

  function teardownHqUi() {
    const leftover = document.getElementById("psr-filter-bar");
    if (leftover) leftover.remove();
    bar = null;
    if (panel) {
      panel.remove();
      panel = null;
    }
    const orphanPanel = document.getElementById("psr-hq-panel");
    if (orphanPanel) orphanPanel.remove();
    if (toggleBtn) {
      toggleBtn.remove();
      toggleBtn = null;
    }
    const orphanToggle = document.getElementById("psr-hq-toggle");
    if (orphanToggle) orphanToggle.remove();
    if (dueBanner) {
      dueBanner.remove();
      dueBanner = null;
    }
    const orphanDue = document.getElementById("psr-due-banner");
    if (orphanDue) orphanDue.remove();
    if (duePollTimer) {
      clearInterval(duePollTimer);
      duePollTimer = null;
    }
    if (tracksRefreshTimer) {
      clearInterval(tracksRefreshTimer);
      tracksRefreshTimer = null;
    }
    tracksEnabled = false;
    panelOpen = false;
  }

  function setPanelOpen(open) {
    panelOpen = !!open;
    if (panel) panel.style.display = panelOpen ? "block" : "none";
    if (toggleBtn) {
      toggleBtn.textContent = panelOpen ? "✕ ПСР" : "ПСР";
      toggleBtn.setAttribute("aria-expanded", panelOpen ? "true" : "false");
    }
  }

  function setStatus(t) {
    if (!isMapPage() || !panel) return;
    const el = panel.querySelector("#psr-status");
    if (el) el.textContent = t || "";
  }

  function ensureToggle() {
    if (toggleBtn && document.body.contains(toggleBtn)) return;
    const orphan = document.getElementById("psr-hq-toggle");
    if (orphan) orphan.remove();
    toggleBtn = document.createElement("button");
    toggleBtn.id = "psr-hq-toggle";
    toggleBtn.type = "button";
    toggleBtn.textContent = "ПСР";
    toggleBtn.title = "Панель секторов и точек ПСР";
    toggleBtn.setAttribute("aria-expanded", "false");
    // Below Mantine AppShell overlays (~200–300 navbar, ~1000 drawer/modal)
    toggleBtn.style.cssText =
      "position:fixed;right:14px;bottom:18px;z-index:400;padding:10px 14px;border-radius:999px;" +
      "border:1px solid #555;background:#1e7a4a;color:#fff;font:600 13px system-ui,sans-serif;" +
      "cursor:pointer;box-shadow:0 2px 10px rgba(0,0,0,.35)";
    toggleBtn.onclick = (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (!panel) ensurePanel(true);
      setPanelOpen(!panelOpen);
    };
    document.body.appendChild(toggleBtn);
  }

  function ensurePanel(forceBuild) {
    if (!isMapPage() || !document.querySelector(".leaflet-container")) {
      teardownHqUi();
      return;
    }
    const leftover = document.getElementById("psr-filter-bar");
    if (leftover) leftover.remove();
    bar = null;
    ensureToggle();
    if (panel && document.body.contains(panel) && !panel.querySelector("#psr-point-filters")) {
      panel.remove();
      panel = null;
    }
    if (panel && document.body.contains(panel)) {
      if (forceBuild) setPanelOpen(true);
      return;
    }
    panel = document.createElement("div");
    panel.id = "psr-hq-panel";
    panel.style.cssText =
      "position:fixed;right:12px;bottom:64px;top:auto;max-height:min(70vh,640px);width:min(340px,92vw);" +
      "z-index:400;background:rgba(22,22,22,.96);color:#eee;border:1px solid #444;border-radius:10px;" +
      "padding:12px;overflow:auto;font:13px system-ui,sans-serif;display:none;" +
      "box-shadow:0 8px 28px rgba(0,0,0,.45)";
    panel.innerHTML =
      "<div style='display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;gap:8px'>" +
      "<strong>Секторы ПСР</strong>" +
      "<div style='display:flex;gap:8px;align-items:center'>" +
      "<a href='/downloads/psr-operation.html' style='color:#8cf;font-size:12px'>Операция…</a>" +
      "<button type='button' id='psr-hq-close' style='border:1px solid #555;background:#333;color:#eee;border-radius:6px;padding:2px 8px;cursor:pointer'>✕</button>" +
      "</div></div>" +
      "<div class='hint' style='opacity:.75;margin-bottom:8px;font-size:12px'>Полигон — слева. Треки = покрытие; «пройден» — вручную.</div>" +
      "<div id='psr-point-filters' style='margin-bottom:12px;padding:8px;border:1px solid #555;border-radius:8px'>" +
      "<div style='font-weight:600;margin-bottom:6px'>Точки ПСР</div>" +
      "<div id='psr-filter-btns' style='display:flex;gap:6px;flex-wrap:wrap'></div></div>" +
      "<div id='psr-export-box' style='margin-bottom:12px;padding:8px;border:1px solid #555;border-radius:8px'>" +
      "<div style='font-weight:600;margin-bottom:6px'>Экспорт</div>" +
      "<div style='display:flex;gap:10px;flex-wrap:wrap'>" +
      "<a href='/api/search_sectors/export.gpx' target='_blank' style='color:#8cf'>GPX секторов</a>" +
      "<a href='/api/search_sectors/export.kml' target='_blank' style='color:#8cf'>KML секторов</a>" +
      "<a id='psr-track-kml' href='#' target='_blank' style='color:#8cf;font-size:12px'>KML миссии</a>" +
      "</div></div>" +
      "<div id='psr-tracks-box' style='margin-bottom:12px;padding:8px;border:1px solid #555;border-radius:8px'>" +
      "<label style='display:flex;align-items:center;gap:8px;font-weight:600'>" +
      "<input type='checkbox' id='psr-tracks-on'/> Треки</label>" +
      "<div style='display:flex;gap:6px;flex-wrap:wrap;margin-top:6px'>" +
      "<select id='psr-tracks-window' style='flex:1;padding:4px;border-radius:4px;border:1px solid #555;background:#333;color:#eee'>" +
      "<option value='1h'>1 час</option><option value='6h' selected>6 часов</option>" +
      "<option value='24h'>24 часа</option><option value='shift'>смена с…</option></select>" +
      "<input type='datetime-local' id='psr-track-shift' style='flex:1;padding:4px;border-radius:4px;border:1px solid #555;background:#333;color:#eee'/>" +
      "</div>" +
      "<input id='psr-track-uid' placeholder='Фильтр uid (пусто = все)' style='width:100%;margin-top:6px;padding:4px;border-radius:4px;border:1px solid #555;background:#333;color:#eee'/>" +
      "<div style='margin-top:6px;display:flex;gap:8px;align-items:center;flex-wrap:wrap'>" +
      "<button type='button' id='psr-tracks-reload' style='padding:4px 8px;border:1px solid #555;background:#333;color:#eee;border-radius:4px;cursor:pointer'>Обновить</button>" +
      "</div></div>" +
      "<form id='psr-sector-form' style='display:none;margin-bottom:12px;padding:8px;border:1px solid #555;border-radius:8px'>" +
      "<div id='psr-form-title' style='font-weight:600;margin-bottom:6px'>Новый сектор</div>" +
      "<input name='name' placeholder='Имя сектора' style='width:100%;margin-bottom:6px;padding:6px;border-radius:4px;border:1px solid #555;background:#333;color:#eee'/>" +
      "<input name='assigned_to' placeholder='Назначено (группа / позывной)' style='width:100%;margin-bottom:6px;padding:6px;border-radius:4px;border:1px solid #555;background:#333;color:#eee'/>" +
      "<select name='mission_name' style='width:100%;margin-bottom:6px;padding:6px;border-radius:4px;border:1px solid #555;background:#333;color:#eee'></select>" +
      "<select name='status' style='width:100%;margin-bottom:6px;padding:6px;border-radius:4px;border:1px solid #555;background:#333;color:#eee'>" +
      "<option value='active'>активен</option><option value='cleared'>пройден</option></select>" +
      "<button type='submit' style='width:100%;padding:8px;border:0;border-radius:6px;background:#2a6;color:#fff;font-weight:600;cursor:pointer'>Сохранить</button></form>" +
      "<div id='psr-status' style='font-size:12px;opacity:.85;min-height:1.2em;margin-bottom:6px'></div>" +
      "<div id='psr-sector-list'></div>";
    document.body.appendChild(panel);
    panel.querySelector("#psr-hq-close").onclick = () => setPanelOpen(false);
    const btnBox = panel.querySelector("#psr-filter-btns");
    FILTERS.forEach((f) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.textContent = f.label;
      btn.dataset.code = f.code;
      btn.style.cssText =
        "border:1px solid #555;background:#333;color:#eee;padding:4px 8px;border-radius:6px;cursor:pointer;font-size:12px";
      btn.onclick = () => {
        activeFilter = f.code;
        [...btnBox.querySelectorAll("button")].forEach((b) => {
          b.style.background = b.dataset.code === activeFilter ? "#2a6" : "#333";
        });
        applyFilter();
      };
      btnBox.appendChild(btn);
    });
    const allBtn = btnBox.querySelector('button[data-code="all"]');
    if (allBtn) allBtn.style.background = "#2a6";
    panel.querySelector("#psr-sector-form").onsubmit = saveSector;
    panel.querySelector("#psr-tracks-on").onchange = (e) => setTracksEnabled(e.target.checked);
    panel.querySelector("#psr-tracks-window").onchange = (e) => {
      tracksWindow = e.target.value;
      if (tracksEnabled) loadTracks();
    };
    panel.querySelector("#psr-track-uid").onchange = (e) => {
      tracksFilterUid = (e.target.value || "").trim();
      if (tracksEnabled) loadTracks();
    };
    panel.querySelector("#psr-tracks-reload").onclick = () => loadTracks();
    setPanelOpen(!!forceBuild);
  }

  function ensureBar() {
    if (!isMapPage() || !document.querySelector(".leaflet-container")) {
      teardownHqUi();
      return;
    }
    ensurePanel(false);
  }

  function hookSpaNavigation() {
    if (historyHooked) return;
    historyHooked = true;
    const wrap = (name) => {
      const orig = history[name];
      if (typeof orig !== "function") return;
      history[name] = function () {
        const r = orig.apply(this, arguments);
        setTimeout(tick, 50);
        return r;
      };
    };
    wrap("pushState");
    wrap("replaceState");
    window.addEventListener("popstate", () => setTimeout(tick, 50));
    // Opening the main OTS nav should not fight a full-height side panel
    document.addEventListener(
      "click",
      (e) => {
        const t = e.target;
        if (!t || !t.closest) return;
        if (t.closest(".mantine-Burger-root, [data-burger], header, nav, .mantine-AppShell-navbar")) {
          if (panelOpen) setPanelOpen(false);
        }
      },
      true
    );
  }

  async function tick() {
    if (!isMapPage() || !document.querySelector(".leaflet-container")) {
      teardownHqUi();
      return;
    }
    ensureBar();
    if (!duePollTimer) startDuePoll();
    if (window.__OTS_MAP__ && window.L) {
      await ensureDraw();
      if (!missionsCache.length) await loadMissions();
      await loadSectors();
    }
    if (activeFilter !== "all") applyFilter();
  }

  hookSpaNavigation();
  setInterval(tick, 2000);
  document.addEventListener("DOMContentLoaded", () => setTimeout(tick, 800));
})();
