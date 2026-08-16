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
    ensurePanel();
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

  function setStatus(t) {
    ensurePanel();
    panel.querySelector("#psr-status").textContent = t || "";
  }

  function ensurePanel() {
    if (panel && document.body.contains(panel)) return;
    panel = document.createElement("div");
    panel.id = "psr-hq-panel";
    panel.style.cssText =
      "position:fixed;right:12px;top:72px;bottom:12px;width:min(340px,92vw);z-index:5000;background:rgba(22,22,22,.94);color:#eee;border:1px solid #444;border-radius:10px;padding:12px;overflow:auto;font:13px system-ui,sans-serif";
    panel.innerHTML =
      "<div style='display:flex;justify-content:space-between;align-items:center;margin-bottom:8px'>" +
      "<strong>Секторы ПСР</strong>" +
      "<a href='/downloads/psr-operation.html' style='color:#8cf;font-size:12px'>Операция…</a></div>" +
      "<div class='hint' style='opacity:.75;margin-bottom:8px;font-size:12px'>Полигон — кнопка слева на карте. Точки ПСР фильтруются сверху.</div>" +
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
    panel.querySelector("#psr-sector-form").onsubmit = saveSector;
  }

  function ensureBar() {
    if (!location.pathname.includes("map") && !document.querySelector(".leaflet-container")) return;
    if (!bar || !document.body.contains(bar)) {
      bar = document.createElement("div");
      bar.id = "psr-filter-bar";
      bar.style.cssText =
        "position:fixed;top:64px;left:12px;right:360px;z-index:5000;display:flex;gap:6px;flex-wrap:wrap;background:rgba(20,20,20,.85);padding:6px 10px;border-radius:8px;max-width:calc(100vw - 380px)";
      FILTERS.forEach((f) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.textContent = f.label;
        btn.dataset.code = f.code;
        btn.style.cssText =
          "border:1px solid #555;background:#333;color:#eee;padding:4px 8px;border-radius:6px;cursor:pointer;font-size:12px";
        btn.onclick = () => {
          activeFilter = f.code;
          [...bar.querySelectorAll("button")].forEach((b) => {
            b.style.background = b.dataset.code === activeFilter ? "#2a6" : "#333";
          });
          applyFilter();
        };
        bar.appendChild(btn);
      });
      document.body.appendChild(bar);
      bar.querySelector('button[data-code="all"]').style.background = "#2a6";
    }
    ensurePanel();
  }

  async function tick() {
    if (!document.querySelector(".leaflet-container")) return;
    ensureBar();
    if (window.__OTS_MAP__ && window.L) {
      await ensureDraw();
      if (!missionsCache.length) await loadMissions();
      await loadSectors();
    }
    if (activeFilter !== "all") applyFilter();
  }

  setInterval(tick, 3000);
  document.addEventListener("DOMContentLoaded", () => setTimeout(tick, 800));
})();
