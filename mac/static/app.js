'use strict';
const $ = (id) => document.getElementById(id);
let token = '', movie = null, status = { connected: false }, connecting = false, pendingCast = false;
let movieRequest = 0, catalogRequest = 0, pollTimer;
const names = { PLAYING: 'მიმდინარეობს ჩვენება', PAUSED: 'დაპაუზებულია', BUFFERING: 'იტვირთება…', IDLE: 'მზადაა გასაშვებად', UNKNOWN: 'დაკავშირებულია' };
function showMessage(text, error = false) { $('message').textContent = text; $('message').hidden = !text; $('message').className = 'message' + (error ? ' error' : ''); }
async function api(path, data) {
  const options = data ? { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Kadri-Token': token }, body: JSON.stringify(data) } : {};
  const response = await fetch('/api/' + path, options);
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || 'მოქმედება ვერ დასრულდა.');
  return result;
}
function option(value, text) { const el = document.createElement('option'); el.value = value; el.textContent = text; return el; }
function refreshSources() {
  const sources = movie.sources.filter(s => s.season === $('season').value && s.language === $('language').value);
  $('source').replaceChildren(...sources.map(s => option(s.id, s.episode || s.quality || 'ფილმი')));
  $('cast-button').disabled = !sources.length;
}
function refreshLanguages() {
  const langs = [...new Set(movie.sources.filter(s => s.season === $('season').value).map(s => s.language))];
  $('language').replaceChildren(...langs.map(l => option(l, l)));
  refreshSources();
}
async function openMovie(url) {
  const request = ++movieRequest;
  $('open-button').disabled = true; $('open-button').textContent = 'იტვირთება…'; showMessage('');
  try {
    const result = await api('resolve', { url });
    if (request !== movieRequest) return;
    movie = result;
    $('movie-title').textContent = movie.title; $('movie-english').textContent = movie.englishTitle;
    $('movie-description').textContent = movie.description.split(/\s\d{4}\s/)[0];
    $('movie-image').src = movie.image; $('movie-image').hidden = !movie.image;
    $('original-link').href = movie.url; $('source-notice').textContent = movie.notice;
    const seasons = [...new Set(movie.sources.map(s => s.season))];
    $('season').replaceChildren(...seasons.map(s => option(s, s ? 'სეზონი ' + s : 'ფილმი')));
    refreshLanguages(); $('movie-panel').hidden = false;
    $('movie-url').value = movie.url;
    addRecent(movie);
    $('movie-panel').scrollIntoView({ behavior: 'smooth', block: 'center' });
  } catch (error) { if (request === movieRequest) showMessage(error.message, true); }
  finally { if (request === movieRequest) { $('open-button').disabled = false; $('open-button').textContent = 'გახსნა ↗'; } }
}
function addRecent(item) {
  try { const previous = JSON.parse(localStorage.getItem('kadri.recent') || '[]'); localStorage.setItem('kadri.recent', JSON.stringify([item.url, ...previous.filter(u => u !== item.url)].slice(0, 20))); } catch {}
}
async function catalog(query = '') {
  const request = ++catalogRequest; $('catalog').textContent = 'ფილმები იტვირთება…';
  try {
    const result = await api('catalog?q=' + encodeURIComponent(query));
    if (request !== catalogRequest) return;
    $('catalog-title').textContent = query ? 'ძიების შედეგები' : 'შეარჩიე შემდეგი კადრი';
    $('catalog').replaceChildren();
    for (const item of result.items.slice(0, 18)) {
      const card = document.createElement('button'); card.className = 'movie-card';
      const poster = document.createElement('div'); poster.className = 'poster';
      const image = document.createElement('img'); if (item.image) image.src = item.image; image.alt = ''; image.loading = 'lazy';
      poster.append(image); const title = document.createElement('strong'); title.textContent = item.title;
      const caption = document.createElement('small'); caption.textContent = 'MYKADRI · ქართულად';
      card.append(poster, title, caption); card.addEventListener('click', () => openMovie(item.url)); $('catalog').append(card);
    }
    if (!result.items.length) $('catalog').textContent = 'ფილმი ვერ მოიძებნა. სცადე სხვა სახელი ან ჩასვი ფილმის ბმული.';
  } catch (error) { if (request === catalogRequest) $('catalog').textContent = error.message; }
}
function formatTime(seconds) { if (!Number.isFinite(seconds)) return '0:00'; const s = Math.max(0, Math.floor(seconds)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }
function renderStatus(result) {
  status = result; const connected = !!status.connected;
  $('device-label').textContent = connected ? status.device : 'ტელევიზორის არჩევა';
  $('device-button').querySelector('.status-dot').classList.toggle('connected', connected);
  $('disconnect').hidden = !connected; $('remote').hidden = !connected || !status.active;
  if (connected) {
    $('remote-title').textContent = status.active?.title || status.device;
    $('remote-state').textContent = status.idleReason === 'ERROR' ? 'ვიდეოს ჩატვირთვა ვერ მოხერხდა' : (names[status.state] || 'დაკავშირებულია');
    $('pause').textContent = status.state === 'PLAYING' ? 'Ⅱ' : '▶';
    $('pause').setAttribute('aria-label', status.state === 'PLAYING' ? 'პაუზა' : 'გაგრძელება');
    $('current-time').textContent = formatTime(status.time); $('duration').textContent = formatTime(status.duration);
    if (document.activeElement !== $('seek')) { $('seek').max = status.duration || 100; $('seek').value = status.time || 0; }
    if (document.activeElement !== $('volume')) $('volume').value = Math.round((status.volume || 0) * 100);
  }
}
async function poll() {
  try { renderStatus(await api('status')); } catch {} finally { pollTimer = setTimeout(poll, 2000); }
}
async function listDevices() {
  $('devices').textContent = 'ტელევიზორების ძებნა…'; $('device-message').textContent = '';
  try {
    const { devices, error } = await api('devices'); $('devices').replaceChildren();
    for (const device of devices) {
      const el = document.createElement('button'); el.className = 'device-item';
      const name = document.createElement('strong'); name.textContent = device.name;
      const detail = document.createElement('small'); detail.textContent = device.model + ' · ' + device.host;
      const wrap = document.createElement('div'); wrap.append(name, detail); el.append(wrap);
      el.addEventListener('click', () => connect({ id: device.id })); $('devices').append(el);
    }
    if (!devices.length) $('devices').textContent = 'TV ჯერ არ ჩანს. რამდენიმე წამში განაახლე სია ან ჩაწერე IP მისამართი.';
    if (error) $('device-message').textContent = error;
  } catch (error) { $('device-message').textContent = error.message; }
}
function showDevices() { if (!$('devices-dialog').open) $('devices-dialog').showModal(); listDevices(); }
async function connect(data) {
  if (connecting) return; connecting = true;
  $('device-message').textContent = 'ტელევიზორს ვუკავშირდები…';
  $('ip-form').querySelector('button').disabled = true;
  try {
    renderStatus(await api('connect', data)); $('devices-dialog').close();
    if (pendingCast) { pendingCast = false; await castMovie(); }
  } catch (error) { $('device-message').textContent = error.message; }
  finally { connecting = false; $('ip-form').querySelector('button').disabled = false; }
}
async function castMovie() {
  if (!movie || !$('source').value) return;
  if (!status.connected) { pendingCast = true; showDevices(); return; }
  $('cast-button').disabled = true; showMessage('ვიდეოს ტელევიზორში ვტვირთავ…');
  try { renderStatus(await api('cast', { url: movie.url, sourceId: $('source').value })); showMessage('ფილმი გაგზავნილია ტელევიზორში. მართვის ღილაკები ქვემოთაა.'); }
  catch (error) { showMessage(error.message, true); }
  finally { $('cast-button').disabled = false; }
}
async function control(action, value) { try { renderStatus(await api('control', { action, value })); } catch (error) { showMessage(error.message, true); } }
$('link-form').addEventListener('submit', e => { e.preventDefault(); openMovie($('movie-url').value); });
$('example-button').addEventListener('click', () => openMovie('https://mykadri.tv/11881-vijenqvesti-visionquest-2.html'));
$('season').addEventListener('change', refreshLanguages); $('language').addEventListener('change', refreshSources);
$('cast-button').addEventListener('click', castMovie); $('device-button').addEventListener('click', showDevices);
$('close-dialog').addEventListener('click', () => { pendingCast = false; $('devices-dialog').close(); });
$('devices-dialog').addEventListener('cancel', () => { pendingCast = false; });
$('refresh-devices').addEventListener('click', listDevices);
$('ip-form').addEventListener('submit', e => { e.preventDefault(); connect({ host: $('tv-ip').value.trim() }); });
$('disconnect').addEventListener('click', async () => { await control('disconnect'); $('devices-dialog').close(); });
$('search-form').addEventListener('submit', e => { e.preventDefault(); catalog($('search').value.trim()); });
$('pause').addEventListener('click', () => control(status.state === 'PLAYING' ? 'pause' : 'play'));
$('stop').addEventListener('click', () => control('stop')); $('backward').addEventListener('click', () => control('seek', Math.max(0, (status.time || 0) - 10)));
$('forward').addEventListener('click', () => control('seek', (status.time || 0) + 10));
$('seek').addEventListener('change', () => control('seek', Number($('seek').value)));
$('volume').addEventListener('change', () => control('volume', Number($('volume').value) / 100));
async function init() { try { const bootstrap = await api('bootstrap'); token = bootstrap.token; await Promise.all([catalog(), poll()]); } catch (error) { showMessage(error.message, true); } }
init();
