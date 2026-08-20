/**
 * ПСР HQ nav: inject «Обращения» + operation links into OTS shell menu.
 * Loaded from index.html; no-op if UI not ready.
 */
(function () {
  function inject() {
    if (document.getElementById('psr-hq-nav-links')) return;
    const bar = document.createElement('div');
    bar.id = 'psr-hq-nav-links';
    bar.setAttribute('style',
      'position:fixed;z-index:9999;left:12px;bottom:12px;display:flex;gap:8px;flex-wrap:wrap;' +
      'font:13px/1.2 system-ui,sans-serif;pointer-events:auto;');
    const mk = (href, label) => {
      const a = document.createElement('a');
      a.href = href;
      a.textContent = label;
      a.setAttribute('style',
        'background:#1e2a24;color:#b6f0c8;border:1px solid #2a6;border-radius:6px;' +
        'padding:6px 10px;text-decoration:none;box-shadow:0 2px 8px rgba(0,0,0,.35);');
      return a;
    };
    bar.appendChild(mk('/downloads/psr-crm.html', 'Обращения'));
    bar.appendChild(mk('/downloads/psr-operation.html', 'Операция'));
    bar.appendChild(mk('/downloads/psr-report.html', 'Форма гражданина'));
    document.body.appendChild(bar);
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => setTimeout(inject, 800));
  } else {
    setTimeout(inject, 800);
  }
})();
