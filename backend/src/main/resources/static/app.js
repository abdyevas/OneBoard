'use strict';

// ---------- helpers ----------
const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtDate = (iso) => (iso ? new Date(iso).toLocaleDateString('ru-RU') : '');
const scoreClass = (s) => (s >= 70 ? 'hi' : s >= 40 ? 'mid' : 'lo');

async function api(path, opts = {}) {
  const init = { ...opts, headers: { ...(opts.headers || {}) } };
  if (opts.json !== undefined) {
    init.body = JSON.stringify(opts.json);
    init.headers['Content-Type'] = 'application/json';
  }
  const res = await fetch('/api' + path, init);
  if (res.status === 204) return null;
  const body = res.headers.get('content-type')?.includes('json') ? await res.json() : await res.text();
  if (!res.ok) {
    const msg = body?.detail || body?.title || body || res.statusText;
    const fields = body?.errors ? ' ' + JSON.stringify(body.errors) : '';
    throw new Error(msg + fields);
  }
  return body;
}

function setStatus(el, text, isError = false) {
  el.textContent = text || '';
  el.classList.toggle('error', isError);
}

async function busy(button, fn) {
  button.disabled = true;
  try { return await fn(); } finally { button.disabled = false; }
}

// ---------- tabs ----------
document.querySelectorAll('.tab').forEach((t) =>
  t.addEventListener('click', () => showTab(t.dataset.tab)));

function showTab(name) {
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === name));
  document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === 'tab-' + name));
  if (name === 'cvs') loadCvs();
  if (name === 'adapt') loadAdaptTab();
}

// ---------- vacancies ----------
const vState = { page: 0, totalPages: 0, selected: null };

let sourcesInfo = [];

async function loadSources() {
  sourcesInfo = await api('/aggregation/sources');
  const sel = $('#v-source');
  for (const s of [...sourcesInfo, { id: 'manual', name: 'Добавлены вручную', enabled: true }]) {
    sel.insertAdjacentHTML('beforeend',
      `<option value="${esc(s.id)}">${esc(s.name)}${s.enabled ? '' : ' — нужен API-ключ'}</option>`);
  }
}

// Azerbaijani job boards whose terms forbid automated collection (hellojob.az, busy.az, boss.az,
// Glorri). We don't load their listings — we only send the user to the board's own search.
// jobsearch.az and smartjob.az are loaded directly (see backend sources).
// `search` = URL template with the query prefilled; null = the board can't be prefilled by URL,
// so we open its list and copy the query to the clipboard.
const AZ_BOARDS = [
  { name: 'boss.az', list: 'https://boss.az/search/vacancies', search: null },
  { name: 'hellojob.az', list: 'https://www.hellojob.az/vakansiyalar', search: 'https://www.hellojob.az/vakansiyalar?q={q}' },
  { name: 'Glorri', list: 'https://jobs.glorri.az/', search: 'https://jobs.glorri.az/?keyword={q}' },
  { name: 'busy.az', list: 'https://busy.az/vacancies', search: null },
];

function renderBoards() {
  $('#v-boards').innerHTML = '<span class="label" title="Эти сайты запрещают автоматическую загрузку вакансий — открываем их поиск">Ещё искать на (вручную):</span>' + AZ_BOARDS.map((b, i) =>
    `<a href="${esc(b.list)}" target="_blank" rel="noopener" data-board="${i}"
        title="${b.search ? 'Откроет поиск на сайте с вашим запросом' : 'Откроет список вакансий; запрос будет скопирован — вставьте его в поиск сайта'}">${esc(b.name)} ↗</a>`).join('');
}

$('#v-boards').addEventListener('click', async (e) => {
  const a = e.target.closest('a[data-board]');
  if (!a) return;
  const board = AZ_BOARDS[Number(a.dataset.board)];
  const q = $('#v-query').value.trim();
  if (!q) return; // plain link to the list
  if (board.search) {
    a.href = board.search.replace('{q}', encodeURIComponent(q));
    return; // let the browser follow the updated link
  }
  let copied = false;
  try {
    await navigator.clipboard.writeText(q);
    copied = true;
  } catch { /* clipboard may be blocked; the list still opens */ }
  setStatus($('#v-status'), copied
    ? `Запрос «${q}» скопирован — вставьте его в поиск на ${board.name} (Ctrl+V).`
    : `${board.name} не принимает запрос в ссылке — введите «${q}» в поиск на сайте.`);
});
renderBoards();

async function searchVacancies(page = 0) {
  const params = new URLSearchParams({ page, size: 20 });
  const q = $('#v-query').value.trim();
  if (q) params.set('q', q);
  if ($('#v-source').value) params.set('source', $('#v-source').value);
  if ($('#v-remote').checked) params.set('remote', 'true');
  try {
    const data = await api('/vacancies?' + params);
    vState.page = data.page;
    vState.totalPages = data.totalPages;
    $('#v-page').textContent = data.totalPages ? `${data.page + 1} / ${data.totalPages} (всего ${data.total})` : '';
    $('#v-prev').disabled = data.page <= 0;
    $('#v-next').disabled = data.page + 1 >= data.totalPages;
    $('#v-list').innerHTML = data.items.map((v) => `
      <li data-id="${v.id}" data-title="${esc(v.title)}" class="${v.id === vState.selected ? 'selected' : ''}">
        <div class="title">${listTitle(v.title, v.applicationStatus)}</div>
        <div class="meta">${esc([v.company || '—', v.location, v.remote ? 'remote' : null].filter(Boolean).join(' · '))}</div>
        <div class="meta">${esc(v.source === 'manual' ? 'вручную' : sourceName(v.source))} · ${fmtDate(v.publishedAt)} ${v.salary ? '· ' + esc(v.salary) : ''}</div>
      </li>`).join('') || `<li class="muted">${emptyListHint()}</li>`;
    return true;
  } catch (e) {
    $('#v-list').innerHTML = `<li class="muted">Не удалось загрузить вакансии: ${esc(e.message)}
      <div style="margin-top:6px"><button class="secondary" id="v-retry">Повторить</button></div></li>`;
    $('#v-retry').onclick = () => searchVacancies(page);
    return false;
  }
}

function emptyListHint() {
  if ($('#v-query').value.trim() || $('#v-source').value || $('#v-remote').checked) {
    return 'Ничего не найдено по этим условиям.';
  }
  if (sourcesInfo.length && !sourcesInfo.some((s) => s.enabled)) {
    return 'Вакансий пока нет: ни один источник не подключён. Подключите Jooble (нужен JOOBLE_API_KEY) '
      + 'или добавьте вакансию вручную.';
  }
  return 'Вакансий пока нет. Нажмите «Обновить из источников», чтобы загрузить их.';
}

$('#v-list').addEventListener('click', (e) => {
  const li = e.target.closest('li[data-id]');
  if (li) showVacancy(Number(li.dataset.id));
});

async function showVacancy(id) {
  vState.selected = id;
  document.querySelectorAll('#v-list li').forEach((li) => li.classList.toggle('selected', Number(li.dataset.id) === id));
  const el = $('#v-detail');
  let v;
  try {
    v = await api('/vacancies/' + id);
  } catch (e) {
    el.classList.remove('empty');
    el.innerHTML = `<p class="muted">Не удалось открыть вакансию: ${esc(e.message)}</p>`;
    return;
  }
  if (vState.selected !== id) return; // user already clicked another vacancy
  el.classList.remove('empty');
  el.scrollTop = 0;
  const srcName = v.source === 'manual' ? 'добавлена вручную' : sourceName(v.source);
  const info = [
    ['Компания', v.company],
    ['Локация', v.location],
    ['Формат', v.remote ? 'Удалённо' : (v.location ? 'Офис / на месте' : null)],
    ['Занятость', v.employmentType],
    ['Зарплата', v.salary || (v.detailsPending ? null : 'не указана')],
    ['Опубликовано', v.publishedAt ? `${fmtDate(v.publishedAt)} (${ago(v.publishedAt)})` : null],
    ['Подать до', v.deadlineAt ? `${fmtDate(v.deadlineAt)} (${untilDeadline(v.deadlineAt)})` : null],
    ['Источник', srcName],
  ].filter(([, value]) => value);

  el.innerHTML = `
    <h2>${esc(v.title)}</h2>
    ${v.company ? `<div class="company">${esc(v.company)}</div>` : ''}

    <div class="apply-bar">
      ${v.url
        ? `<a id="v-apply" class="button apply" href="${esc(v.url)}" target="_blank" rel="noopener">Откликнуться на сайте ↗</a>`
        : '<span class="muted">У вакансии нет ссылки для отклика — свяжитесь с работодателем напрямую.</span>'}
      <button id="v-adapt" class="secondary">Адаптировать CV</button>
      <span class="spacer"></span>
      <label class="status-select">Статус
        <select id="v-app-status">
          ${Object.entries(APP_STATUS).map(([k, label]) => `<option value="${k}">${label}</option>`).join('')}
        </select>
      </label>
    </div>
    <div id="v-apply-confirm" class="notice" hidden>
      Подали заявку на сайте?
      <button id="v-apply-yes">Да, отметить</button>
      <button id="v-apply-no" class="secondary">Нет</button>
    </div>
    <div id="v-app-info" class="muted small"></div>

    <dl class="info-grid">${info.map(([k, val]) => `<dt>${k}</dt><dd>${esc(val)}</dd>`).join('')}</dl>

    <section id="v-match" class="block"></section>
    <section id="v-adaptations" class="block" hidden></section>

    ${v.tags.length ? `<div class="chips">${v.tags.map((t) => `<span class="chip">${esc(t)}</span>`).join('')}</div>` : ''}

    <h3 class="section-title">Описание вакансии</h3>
    ${v.source === 'jooble' ? '<p class="muted small">Jooble передаёт только краткое описание — полный текст по кнопке «Откликнуться на сайте».</p>' : ''}
    <div class="desc">${v.detailsPending
      ? `<p class="muted"><span class="spinner"></span> Загружаю полное описание с ${esc(srcName)}… Сайт разрешает один запрос в 30 секунд, поэтому это может занять до минуты. Страница обновится сама.</p>`
      : formatDescription(v.description)}</div>

    <div class="detail-footer muted small">
      ID ${v.id}${v.url ? ` · <a href="${esc(v.url)}" target="_blank" rel="noopener">${esc(hostOf(v.url))}</a>` : ''}
      ${v.source === 'manual' ? ' · <button id="v-delete" class="link danger-text">Удалить вакансию</button>' : ''}
    </div>`;

  renderApplication(v.id, v.application);
  $('#v-adapt').onclick = () => openAdapt({ vacancyId: v.id });
  $('#v-app-status').onchange = (e) => saveApplication(v.id, e.target.value);
  const apply = $('#v-apply');
  if (apply) apply.addEventListener('click', () => {
    if (!$('#v-app-status').value) $('#v-apply-confirm').hidden = false; // ask once they come back
  });
  $('#v-apply-yes').onclick = () => saveApplication(v.id, 'APPLIED');
  $('#v-apply-no').onclick = () => { $('#v-apply-confirm').hidden = true; };
  const del = $('#v-delete');
  if (del) del.onclick = async () => {
    if (!confirm('Удалить вакансию?')) return;
    await api('/vacancies/' + v.id, { method: 'DELETE' });
    el.classList.add('empty'); el.textContent = 'Удалено';
    searchVacancies(vState.page);
  };
  if (v.detailsPending) {
    $('#v-match').innerHTML = '<span class="muted small">Совпадение с CV посчитается, когда загрузится описание.</span>';
    waitForDetails(v.id);
  } else {
    loadVacancyMatch(v.id);
  }
  loadVacancyAdaptations(v.id);
}

/** Re-render the vacancy once the background worker has loaded its full text. */
async function waitForDetails(id) {
  for (let i = 0; i < 60; i++) { // up to ~4 minutes
    await new Promise((r) => setTimeout(r, 4000));
    if (vState.selected !== id) return;
    try {
      const v = await api('/vacancies/' + id);
      if (!v.detailsPending) {
        if (vState.selected === id) showVacancy(id);
        return;
      }
    } catch { /* transient: keep waiting */ }
  }
}

function untilDeadline(iso) {
  const days = Math.ceil((new Date(iso) - Date.now()) / 86_400_000);
  if (days < 0) return 'срок истёк';
  if (days === 0) return 'сегодня последний день';
  if (days === 1) return 'остался 1 день';
  return `осталось ${days} дн.`;
}

const APP_STATUS = {
  '': 'Не откликалась',
  APPLIED: 'Заявка подана',
  INTERVIEW: 'Собеседование',
  OFFER: 'Оффер',
  REJECTED: 'Отказ',
};

function ago(iso) {
  const days = Math.floor((Date.now() - new Date(iso)) / 86_400_000);
  if (days <= 0) return 'сегодня';
  if (days === 1) return 'вчера';
  return `${days} дн. назад`;
}

function hostOf(url) {
  try { return new URL(url).host; } catch { return url; }
}

/** Plain-text description -> paragraphs and bullet lists (everything escaped). */
function formatDescription(text) {
  if (!text) return '<p class="muted">Описание отсутствует.</p>';
  return text.split(/\n{2,}/).map((block) => {
    const lines = block.split('\n').map((l) => l.trim()).filter(Boolean);
    const bullet = /^([-•*·]|\d+[.)])\s+/;
    if (lines.length && lines.every((l) => bullet.test(l))) {
      return `<ul>${lines.map((l) => `<li>${esc(l.replace(bullet, ''))}</li>`).join('')}</ul>`;
    }
    return lines.map((l) => bullet.test(l)
      ? `<ul><li>${esc(l.replace(bullet, ''))}</li></ul>`
      : `<p>${esc(l)}</p>`).join('');
  }).join('');
}

function renderApplication(vacancyId, app) {
  $('#v-app-status').value = app?.status || '';
  $('#v-app-status').classList.toggle('active', !!app);
  $('#v-apply-confirm').hidden = true;
  $('#v-app-info').textContent = app
    ? `Отклик отмечен ${new Date(app.appliedAt).toLocaleDateString('ru-RU')}` +
      (app.updatedAt !== app.appliedAt ? `, статус обновлён ${new Date(app.updatedAt).toLocaleDateString('ru-RU')}` : '')
    : '';
  // keep the list badge in sync without reloading the page
  const li = document.querySelector(`#v-list li[data-id="${vacancyId}"]`);
  if (li) li.querySelector('.title').innerHTML = listTitle(li.dataset.title, app?.status);
}

async function saveApplication(vacancyId, status) {
  try {
    const app = status
      ? await api('/applications/' + vacancyId, { method: 'PUT', json: { status } })
      : (await api('/applications/' + vacancyId, { method: 'DELETE' }), null);
    if (vState.selected === vacancyId) renderApplication(vacancyId, app);
  } catch (e) {
    alert('Не удалось сохранить статус: ' + e.message);
  }
}

function listTitle(title, status) {
  return esc(title) + (status ? ` <span class="badge ${status.toLowerCase()}">${APP_STATUS[status]}</span>` : '');
}

// ----- match with the user's CV, shown inside the vacancy -----
const PREFERRED_CV_KEY = 'oneboard.matchCv';

async function loadVacancyMatch(vacancyId) {
  const box = $('#v-match');
  let cvs;
  try { cvs = await api('/cvs'); } catch { box.hidden = true; return; }
  if (vState.selected !== vacancyId) return;
  if (!cvs.length) {
    box.innerHTML = '<span class="muted small">Добавьте CV на вкладке «Мои CV», чтобы увидеть совпадение навыков.</span>';
    return;
  }
  let preferred = null;
  try { preferred = localStorage.getItem(PREFERRED_CV_KEY); } catch { /* storage may be blocked */ }
  const cvId = cvs.some((c) => String(c.id) === preferred) ? preferred : String(cvs[0].id);
  box.innerHTML = `
    <div class="row">
      <h3 class="section-title" style="margin:0;flex:1">Совпадение с CV</h3>
      ${cvs.length > 1
        ? `<select id="v-match-cv">${cvs.map((c) => `<option value="${c.id}">${esc(c.title)}</option>`).join('')}</select>`
        : `<span class="muted small">${esc(cvs[0].title)}</span>`}
    </div>
    <div id="v-match-body" class="muted small">Считаю…</div>`;
  const sel = $('#v-match-cv');
  if (sel) {
    sel.value = cvId;
    sel.onchange = () => {
      try { localStorage.setItem(PREFERRED_CV_KEY, sel.value); } catch { /* ignore */ }
      renderMatch(vacancyId, sel.value);
    };
  }
  renderMatch(vacancyId, cvId);
}

async function renderMatch(vacancyId, cvId) {
  const body = $('#v-match-body');
  try {
    const m = await api(`/match?cvId=${cvId}&vacancyId=${vacancyId}`);
    if (vState.selected !== vacancyId) return;
    if (!m.vacancySkills.length) {
      body.innerHTML = 'В описании не найдено известных навыков — сравните требования вручную.';
      return;
    }
    body.classList.remove('muted', 'small');
    body.innerHTML = `
      <div><span class="score ${scoreClass(m.score)}">${m.score}%</span>
        <span class="muted small">есть ${m.matched.length} из ${m.vacancySkills.length} навыков из вакансии</span></div>
      <div class="chips">${m.matched.map((s) => `<span class="chip ok">✓ ${esc(s)}</span>`).join('')}${m.missing.map((s) => `<span class="chip miss">✗ ${esc(s)}</span>`).join('')}</div>`;
  } catch (e) {
    body.textContent = 'Не удалось посчитать совпадение: ' + e.message;
  }
}

// ----- CVs already adapted for this vacancy: handy to attach when applying -----
async function loadVacancyAdaptations(vacancyId) {
  const box = $('#v-adaptations');
  let items;
  try { items = await api('/adaptations?vacancyId=' + vacancyId); } catch { return; }
  if (vState.selected !== vacancyId || !items.length) return;
  box.hidden = false;
  box.innerHTML = `<h3 class="section-title">CV, адаптированные под эту вакансию</h3>
    <ul class="plain">${items.map((a, i) => `
      <li><span class="score ${scoreClass(a.matchScore)}">${a.matchScore}%</span>
        ${esc(a.cvTitle)} <span class="muted small">· ${esc(a.method)} · ${new Date(a.createdAt).toLocaleDateString('ru-RU')}</span>
        <button class="link" data-dl="${i}">Скачать .md</button>
        <button class="link" data-open="${a.id}">Открыть</button></li>`).join('')}</ul>`;
  box.querySelectorAll('[data-dl]').forEach((b) => b.onclick = () => downloadAdaptation(items[Number(b.dataset.dl)]));
  box.querySelectorAll('[data-open]').forEach((b) => b.onclick = async () => {
    showTab('adapt');
    renderAdaptation(await api('/adaptations/' + b.dataset.open));
  });
}

$('#v-search').onclick = () => searchVacancies(0);
$('#v-query').addEventListener('keydown', (e) => { if (e.key === 'Enter') searchVacancies(0); });
$('#v-prev').onclick = () => searchVacancies(vState.page - 1);
$('#v-next').onclick = () => searchVacancies(vState.page + 1);

const sourceName = (id) => sourcesInfo.find((s) => s.id === id)?.name || id;

// Sites ask us to wait between requests (jobsearch.az: 30 s), so a refresh runs in the background
// and we poll its progress.
$('#v-refresh').onclick = (e) => busy(e.target, async () => {
  const query = $('#v-query').value.trim();
  try {
    let st = await api('/aggregation/refresh', { method: 'POST', json: { query } });
    const started = Date.now();
    while (st.running) {
      const secs = Math.round((Date.now() - started) / 1000);
      const done = st.results.map((r) => sourceName(r.source)).join(', ');
      setStatus($('#v-status'),
        `Загружаю вакансии${query ? ` по запросу «${query}»` : ''}… ${secs} c` +
        (st.currentSource ? `\nСейчас: ${sourceName(st.currentSource)} (сайты просят делать паузы между запросами)` : '') +
        (done ? `\nГотово: ${done}` : ''));
      if (done) searchVacancies(0); // show what has arrived so far
      await new Promise((r) => setTimeout(r, 2000));
      st = await api('/aggregation/status');
    }
    const lines = st.results.map((r) => r.error
      ? `${sourceName(r.source)}: ошибка — ${r.error}`
      : `${sourceName(r.source)}: получено ${r.fetched}, новых ${r.created}, обновлено ${r.updated}` +
        (sourcesInfo.find((s) => s.id === r.source)?.supportsQuery === false && query
          ? ' (сайт не поддерживает поиск — загружены свежие вакансии, ищите в списке)' : ''));
    for (const s of sourcesInfo.filter((x) => !x.enabled)) {
      lines.push(`${s.name}: не подключён — нужен API-ключ (см. README)`);
    }
    if (st.results.some((r) => r.source === 'jobsearch' && !r.error && r.created)) {
      lines.push('Описания с jobsearch.az подгружаются в фоне — по одной вакансии в 30 секунд (так требует сайт). Открытые вами вакансии загружаются первыми.');
    }
    setStatus($('#v-status'), lines.join('\n'), st.results.some((r) => r.error));
    searchVacancies(0);
  } catch (err) {
    setStatus($('#v-status'), err.message, true);
  }
});

const addDialog = $('#add-dialog');
$('#v-add').onclick = () => { $('#add-form').reset(); addDialog.showModal(); };
$('#add-cancel').onclick = () => addDialog.close();
$('#add-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  try {
    const v = await api('/vacancies', {
      method: 'POST',
      json: {
        title: f.get('title'), company: f.get('company'), location: f.get('location'),
        remote: f.get('remote') === 'on', salary: f.get('salary'), url: f.get('url') || null,
        description: f.get('description'),
        tags: String(f.get('tags') || '').split(',').map((t) => t.trim()).filter(Boolean),
      },
    });
    addDialog.close();
    await searchVacancies(0);
    showVacancy(v.id);
  } catch (err) {
    alert(err.message);
  }
});

// ---------- CVs ----------
let cvCache = [];
let cvCreating = false; // user clicked "New CV": don't auto-select an existing one

async function loadCvs(selectId) {
  cvCache = await api('/cvs');
  $('#cv-list').innerHTML = cvCache.map((c) => `
    <li data-id="${c.id}">
      <div class="title">${esc(c.title)}</div>
      <div class="meta">обновлено ${fmtDate(c.updatedAt)} · ${c.content.length} символов</div>
    </li>`).join('') || '<li class="muted">Пока нет CV — создайте новое или загрузите файл (.pdf/.txt/.md)</li>';
  const id = selectId ?? Number($('#cv-id').value);
  const cv = cvCache.find((c) => c.id === id) ?? (cvCreating ? null : cvCache[0]);
  if (cv) editCv(cv); else newCv();
}

function newCv() {
  cvCreating = true;
  $('#cv-id').value = '';
  $('#cv-title').value = '';
  $('#cv-content').value = '';
  $('#cv-delete').disabled = true;
  $('#cv-matches').disabled = true;
  $('#cv-match-list').innerHTML = '';
  document.querySelectorAll('#cv-list li').forEach((li) => li.classList.remove('selected'));
}

function editCv(cv) {
  cvCreating = false;
  $('#cv-id').value = cv.id;
  $('#cv-title').value = cv.title;
  $('#cv-content').value = cv.content;
  $('#cv-delete').disabled = false;
  $('#cv-matches').disabled = false;
  $('#cv-match-list').innerHTML = '';
  document.querySelectorAll('#cv-list li').forEach((li) => li.classList.toggle('selected', Number(li.dataset.id) === cv.id));
}

$('#cv-list').addEventListener('click', (e) => {
  const li = e.target.closest('li[data-id]');
  if (li) editCv(cvCache.find((c) => c.id === Number(li.dataset.id)));
});
$('#cv-new').onclick = newCv;

$('#cv-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = $('#cv-id').value;
  const json = { title: $('#cv-title').value, content: $('#cv-content').value };
  try {
    const saved = await api(id ? '/cvs/' + id : '/cvs', { method: id ? 'PUT' : 'POST', json });
    await loadCvs(saved.id);
  } catch (err) { alert(err.message); }
});

$('#cv-delete').onclick = async () => {
  const id = $('#cv-id').value;
  if (!id || !confirm('Удалить это CV?')) return;
  await api('/cvs/' + id, { method: 'DELETE' });
  $('#cv-id').value = '';
  cvCreating = false;
  loadCvs();
};

$('#cv-file').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const fd = new FormData();
  fd.append('file', file);
  try {
    const cv = await api('/cvs/upload', { method: 'POST', body: fd });
    await loadCvs(cv.id);
  } catch (err) { alert(err.message); }
  e.target.value = '';
});

$('#cv-matches').onclick = (e) => busy(e.target, async () => {
  const id = $('#cv-id').value;
  const list = await api(`/cvs/${id}/matches?limit=15`);
  $('#cv-match-list').innerHTML = `<h3 style="margin-top:16px">Лучшие совпадения</h3>` + (list.length
    ? `<ul class="list">${list.map((r) => `
        <li data-vid="${r.vacancyId}">
          <span class="score ${scoreClass(r.score)}">${r.score}%</span>
          <span class="title">${esc(r.title)}</span> <span class="meta">${esc(r.company || '')} · ${esc(r.source)}</span>
          <div class="chips">${r.matched.map((s) => `<span class="chip ok">${esc(s)}</span>`).join('')}${r.missing.map((s) => `<span class="chip miss">${esc(s)}</span>`).join('')}</div>
        </li>`).join('')}</ul>`
    : '<p class="muted">Нет вакансий с совпадениями. Загрузите вакансии на вкладке «Вакансии».</p>');
  $('#cv-match-list').querySelectorAll('li[data-vid]').forEach((li) => li.onclick = () =>
    openAdapt({ cvId: Number(id), vacancyId: Number(li.dataset.vid) }));
});

// ---------- adaptation ----------
let methodsLoaded = false;
let pendingAdapt = null; // {cvId?, vacancyId?} to preselect when the tab opens

/** Switch to the Adapt tab with a CV and/or vacancy preselected. */
function openAdapt(selection) {
  pendingAdapt = selection;
  showTab('adapt');
}

async function loadAdaptTab() {
  const pending = pendingAdapt;
  pendingAdapt = null;
  const current = pending?.cvId ? String(pending.cvId) : $('#a-cv').value;
  const cvs = await api('/cvs');
  $('#a-cv').innerHTML = cvs.map((c) => `<option value="${c.id}">${esc(c.title)}</option>`).join('')
    || '<option value="">— нет CV —</option>';
  if (current && cvs.some((c) => String(c.id) === current)) $('#a-cv').value = current;
  if (pending?.vacancyId) await selectAdaptVacancy(pending.vacancyId);
  if (!methodsLoaded) {
    const methods = await api('/adaptations/methods');
    for (const m of methods) {
      $('#a-method').insertAdjacentHTML('beforeend',
        `<option value="${esc(m.id)}" ${m.available ? '' : 'disabled'}>${esc(m.id)}${m.available ? '' : ' (не настроен)'}</option>`);
    }
    methodsLoaded = true;
  }
  loadHistory();
}

// ----- vacancy picker -----
const picker = { label: '', options: [], active: -1, timer: null, seq: 0 };
const pickerInput = $('#a-vacancy-search');
const pickerList = $('#a-vacancy-options');
const vacancyLabel = (title, company) => (company ? `${title} — ${company}` : title);

async function selectAdaptVacancy(id) {
  try {
    const v = await api('/vacancies/' + id);
    setPickerValue(v.id, vacancyLabel(v.title, v.company));
  } catch {
    setPickerValue('', '');
  }
}

function setPickerValue(id, label) {
  $('#a-vacancy').value = id;
  picker.label = label;
  pickerInput.value = label;
  closePicker();
}

function closePicker() {
  pickerList.hidden = true;
  picker.active = -1;
}

async function refreshPicker() {
  const q = pickerInput.value.trim();
  const seq = ++picker.seq; // ignore out-of-order responses
  let header;
  let options;
  try {
    if (!q || q === picker.label) {
      const cvId = $('#a-cv').value;
      if (cvId) {
        header = 'Лучше всего подходят к выбранному CV';
        options = (await api(`/cvs/${cvId}/matches?limit=10`))
          .map((r) => ({ id: r.vacancyId, title: r.title, company: r.company, score: r.score }));
      } else {
        header = 'Последние вакансии';
        options = (await api('/vacancies?size=10')).items;
      }
    } else {
      header = 'Результаты поиска';
      options = (await api('/vacancies?size=10&q=' + encodeURIComponent(q))).items;
    }
  } catch (e) {
    header = 'Ошибка: ' + e.message;
    options = [];
  }
  if (seq !== picker.seq) return;
  picker.options = options;
  picker.active = -1;
  pickerList.innerHTML = `<li class="header">${esc(header)}</li>` + (options.map((o, i) => `
    <li data-i="${i}">
      ${o.score !== undefined ? `<span class="score ${scoreClass(o.score)}">${o.score}%</span>` : ''}
      <span class="grow">${esc(o.title)} <span class="muted">${esc(o.company || '')}</span></span>
    </li>`).join('') || '<li class="header">Ничего не найдено</li>');
  pickerList.hidden = false;
}

function highlightPicker(i) {
  const items = pickerList.querySelectorAll('li[data-i]');
  if (!items.length) return;
  picker.active = (i + items.length) % items.length;
  items.forEach((li, j) => li.classList.toggle('active', j === picker.active));
  items[picker.active].scrollIntoView({ block: 'nearest' });
}

function choosePicker(i) {
  const o = picker.options[i];
  if (o) setPickerValue(o.id, vacancyLabel(o.title, o.company));
}

pickerInput.addEventListener('focus', () => { pickerInput.select(); refreshPicker(); });
pickerInput.addEventListener('input', () => {
  clearTimeout(picker.timer);
  picker.timer = setTimeout(refreshPicker, 250);
});
pickerInput.addEventListener('keydown', (e) => {
  if (e.key === 'ArrowDown') { e.preventDefault(); if (pickerList.hidden) refreshPicker(); else highlightPicker(picker.active + 1); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); highlightPicker(picker.active - 1); }
  else if (e.key === 'Enter' && !pickerList.hidden && picker.active >= 0) { e.preventDefault(); choosePicker(picker.active); }
  else if (e.key === 'Escape') { closePicker(); pickerInput.value = picker.label; }
});
// mousedown fires before the input's blur, so the click still lands
pickerList.addEventListener('mousedown', (e) => {
  const li = e.target.closest('li[data-i]');
  if (li) { e.preventDefault(); choosePicker(Number(li.dataset.i)); }
});
pickerInput.addEventListener('blur', () => {
  closePicker();
  pickerInput.value = picker.label; // discard half-typed text: the selection didn't change
});

async function loadHistory() {
  const items = await api('/adaptations');
  $('#a-history').innerHTML = items.map((a) => `
    <li data-id="${a.id}">
      <span class="score ${scoreClass(a.matchScore)}">${a.matchScore}%</span>
      <span class="title">${esc(a.vacancyTitle)}</span>
      <div class="meta">${esc(a.cvTitle)} · ${esc(a.method)} · ${new Date(a.createdAt).toLocaleString('ru-RU')}</div>
    </li>`).join('') || '<li class="muted">Пока пусто</li>';
}

$('#a-history').addEventListener('click', async (e) => {
  const li = e.target.closest('li[data-id]');
  if (li) renderAdaptation(await api('/adaptations/' + li.dataset.id));
});

$('#a-run').onclick = (e) => busy(e.target, async () => {
  const cvId = Number($('#a-cv').value);
  const vacancyId = Number($('#a-vacancy').value);
  if (!cvId || !vacancyId) { setStatus($('#a-status'), 'Выберите CV и вакансию', true); return; }
  setStatus($('#a-status'), 'Адаптирую… (с Claude это может занять до минуты)');
  try {
    const a = await api('/adaptations', { method: 'POST', json: { cvId, vacancyId, method: $('#a-method').value } });
    setStatus($('#a-status'), '');
    renderAdaptation(a);
    loadHistory();
  } catch (err) {
    setStatus($('#a-status'), err.message, true);
  }
});

function renderAdaptation(a) {
  const el = $('#a-result');
  el.classList.remove('empty');
  el.innerHTML = `
    <div class="row"><h2 style="flex:1">${esc(a.vacancyTitle)}</h2><span class="score ${scoreClass(a.matchScore)}">${a.matchScore}%</span></div>
    <div class="muted">${esc(a.company || '')} · CV «${esc(a.cvTitle)}» · ${esc(a.method)} (${esc(a.model)})</div>
    <div class="chips">${a.matchedSkills.map((s) => `<span class="chip ok">✓ ${esc(s)}</span>`).join('')}${a.missingSkills.map((s) => `<span class="chip miss">✗ ${esc(s)}</span>`).join('')}</div>
    ${a.notes.length ? `<h3>Комментарии</h3><ul>${a.notes.map((n) => `<li>${esc(n)}</li>`).join('')}</ul>` : ''}
    <div class="row"><h3 style="flex:1">Адаптированное CV</h3>
      <button class="secondary" id="a-copy">Копировать</button>
      <button class="secondary" id="a-download">Скачать .md</button>
      <button class="danger" id="a-del">Удалить</button></div>
    <pre class="cv">${esc(a.content)}</pre>`;
  $('#a-copy').onclick = () => navigator.clipboard.writeText(a.content);
  $('#a-download').onclick = () => downloadAdaptation(a);
  $('#a-del').onclick = async () => {
    if (!confirm('Удалить эту адаптацию?')) return;
    await api('/adaptations/' + a.id, { method: 'DELETE' });
    el.classList.add('empty'); el.textContent = 'Удалено';
    loadHistory();
  };
}

function downloadAdaptation(a) {
  const blob = new Blob([a.content], { type: 'text/markdown' });
  const link = Object.assign(document.createElement('a'), {
    href: URL.createObjectURL(blob),
    download: `cv-${(a.company || 'company').replace(/[^\w-]+/g, '_')}-${a.vacancyId}.md`,
  });
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

// ---------- init ----------
// The backend may still be starting when the page opens: wait for it instead of showing an empty page.
async function waitForServer(maxAttempts = 30) {
  for (let i = 0; i < maxAttempts; i++) {
    try {
      await api('/aggregation/sources');
      return true;
    } catch {
      setStatus($('#v-status'), 'Сервер запускается, подождите…');
      await new Promise((r) => setTimeout(r, 1000));
    }
  }
  return false;
}

(async () => {
  if (!(await waitForServer())) {
    setStatus($('#v-status'), 'Сервер не отвечает. Проверьте, что backend запущен, и обновите страницу.', true);
    return;
  }
  setStatus($('#v-status'), '');
  await loadSources().catch((e) => setStatus($('#v-status'), e.message, true));
  await searchVacancies(0);
})();
