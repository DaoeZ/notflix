package com.nuvio.tv.core.server

/** Phone page that lets the user type the provider keys with the phone keyboard. */
internal object NotflixKeysWebPage {

    fun html(labels: Map<String, String>): String {
        fun text(key: String): String = escape(labels[key].orEmpty())
        return """<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${text("title")}</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: #0b0b0d; color: #f4f4f5; font-family: system-ui, -apple-system, Roboto, sans-serif; }
  main { max-width: 520px; margin: 0 auto; padding: 24px 18px 48px; }
  h1 { font-size: 26px; letter-spacing: .04em; color: #e50914; margin: 0 0 8px; }
  p.sub { color: #c4c4cc; line-height: 1.45; margin: 0 0 22px; }
  label { display: block; font-weight: 600; margin: 18px 0 6px; }
  .current { color: #9a9aa6; font-size: 13px; margin-top: 4px; }
  .row { display: flex; gap: 8px; align-items: center; }
  input[type=text], input[type=password], input[type=url] {
    flex: 1; width: 100%; box-sizing: border-box; padding: 12px; border-radius: 8px;
    border: 1px solid #33333d; background: #18181d; color: #f4f4f5; font-size: 16px;
  }
  .check { color: #c4c4cc; font-size: 14px; display: flex; gap: 6px; align-items: center; white-space: nowrap; }
  button { margin-top: 26px; width: 100%; padding: 14px; border: 0; border-radius: 10px;
    background: #e50914; color: #fff; font-size: 17px; font-weight: 700; }
  button:disabled { opacity: .5; }
  #status { margin-top: 16px; min-height: 22px; color: #c4c4cc; }
  #status.ok { color: #7cff9b; }
  #status.err { color: #ff8a8a; }
</style>
</head>
<body>
<main>
  <h1>${text("title")}</h1>
  <p class="sub">${text("subtitle")}</p>

  <label for="tmdb">${text("tmdb")}</label>
  <div class="row">
    <input id="tmdb" type="text" autocomplete="off" autocapitalize="off" spellcheck="false">
    <label class="check"><input id="clearTmdb" type="checkbox"> ${text("clear")}</label>
  </div>
  <div class="current" id="currentTmdb"></div>

  <label for="traktId">${text("traktId")}</label>
  <div class="row">
    <input id="traktId" type="text" autocomplete="off" autocapitalize="off" spellcheck="false">
    <label class="check"><input id="clearTrakt" type="checkbox"> ${text("clear")}</label>
  </div>
  <div class="current" id="currentTrakt"></div>

  <label for="traktSecret">${text("traktSecret")}</label>
  <div class="row">
    <input id="traktSecret" type="password" autocomplete="off" autocapitalize="off" spellcheck="false">
  </div>
  <label class="check" style="margin-top:6px"><input id="showSecret" type="checkbox"> ${text("show")}</label>

  <label for="introDb">${text("introDb")}</label>
  <div class="row">
    <input id="introDb" type="url" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="https://">
    <label class="check"><input id="clearIntroDb" type="checkbox"> ${text("clear")}</label>
  </div>
  <div class="current" id="currentIntroDb"></div>

  <button id="save" type="button">${text("save")}</button>
  <div id="status"></div>
</main>
<script>
(function () {
  var token = new URLSearchParams(window.location.search).get('t') || '';
  var labels = {
    none: '${text("none")}',
    current: '${text("current")}',
    saved: '${text("saved")}',
    error: '${text("error")}',
    forbidden: '${text("forbidden")}'
  };
  var status = document.getElementById('status');
  function setStatus(message, kind) {
    status.textContent = message;
    status.className = kind || '';
  }
  function describe(entry) {
    if (!entry || !entry.configured) return labels.current + ' ' + labels.none;
    return labels.current + ' ' + entry.hint;
  }
  function load() {
    fetch('/api/keys?t=' + encodeURIComponent(token), { cache: 'no-store' })
      .then(function (r) { if (!r.ok) throw new Error(String(r.status)); return r.json(); })
      .then(function (data) {
        document.getElementById('currentTmdb').textContent = describe(data.tmdb);
        document.getElementById('currentTrakt').textContent = describe(data.trakt);
        document.getElementById('currentIntroDb').textContent = data.introDb && data.introDb.value
          ? labels.current + ' ' + data.introDb.value : labels.current + ' ' + labels.none;
      })
      .catch(function () { setStatus(labels.forbidden, 'err'); });
  }
  document.getElementById('showSecret').addEventListener('change', function (e) {
    document.getElementById('traktSecret').type = e.target.checked ? 'text' : 'password';
  });
  document.getElementById('save').addEventListener('click', function () {
    var button = document.getElementById('save');
    button.disabled = true;
    setStatus('…');
    var body = {
      tmdbApiKey: document.getElementById('tmdb').value,
      clearTmdb: document.getElementById('clearTmdb').checked,
      traktClientId: document.getElementById('traktId').value,
      traktClientSecret: document.getElementById('traktSecret').value,
      clearTrakt: document.getElementById('clearTrakt').checked,
      introDbBaseUrl: document.getElementById('introDb').value,
      clearIntroDb: document.getElementById('clearIntroDb').checked
    };
    fetch('/api/keys?t=' + encodeURIComponent(token), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (r) {
      if (!r.ok) throw new Error(String(r.status));
      setStatus(labels.saved, 'ok');
      ['tmdb', 'traktId', 'traktSecret', 'introDb'].forEach(function (id) { document.getElementById(id).value = ''; });
      ['clearTmdb', 'clearTrakt', 'clearIntroDb'].forEach(function (id) { document.getElementById(id).checked = false; });
      load();
    }).catch(function () {
      setStatus(labels.error, 'err');
    }).then(function () { button.disabled = false; });
  });
  load();
})();
</script>
</body>
</html>
"""
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
