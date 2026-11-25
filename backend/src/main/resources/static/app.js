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

async function loadSources() {
  const sources = await api('/aggregation/sources');
  const sel = $('#v-source');
  for (const s of [...sources, { id: 'manual', name: 'Добавлены вручную' }]) {
    sel.insertAdjacentHTML('beforeend', `<option value="${esc(s.id)}">${esc(s.name)}</option>`);
  }
}

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
      <li data-id="${v.id}" class="${v.id === vState.selected ? 'selected' : ''}">
        <div class="title">${esc(v.title)}</div>
        <div class="meta">${esc(v.company || '—')} · ${esc(v.location || '')} ${v.remote ? '· remote' : ''}</div>
        <div class="meta">${esc(v.source)} · ${fmtDate(v.publishedAt)} ${v.salary ? '· ' + esc(v.salary) : ''}</div>
      </li>`).join('') || '<li class="muted">Ничего не найдено. Нажмите «Обновить из источников», чтобы загрузить вакансии.</li>';
    return true;
  } catch (e) {
    $('#v-list').innerHTML = `<li class="muted">Не удалось загрузить вакансии: ${esc(e.message)}
      <div style="margin-top:6px"><button class="secondary" id="v-retry">Повторить</button></div></li>`;
    $('#v-retry').onclick = () => searchVacancies(page);
    return false;
  }
}

$('#v-list').addEventListener('click', (e) => {
  const li = e.target.closest('li[data-id]');
  if (li) showVacancy(Number(li.dataset.id));
});

async function showVacancy(id) {
  vState.selected = id;
  document.querySelectorAll('#v-list li').forEach((li) => li.classList.toggle('selected', Number(li.dataset.id) === id));
  const v = await api('/vacancies/' + id);
  const el = $('#v-detail');
  el.classList.remove('empty');
  el.innerHTML = `
    <h2>${esc(v.title)}</h2>
    <div class="muted">${esc(v.company || '')} · ${esc(v.location || '')} ${v.remote ? '· remote' : ''} ${v.employmentType ? '· ' + esc(v.employmentType) : ''}</div>
    ${v.salary ? `<div><b>${esc(v.salary)}</b></div>` : ''}
    <div class="chips">${v.tags.map((t) => `<span class="chip">${esc(t)}</span>`).join('')}</div>
    <div class="row" style="margin:10px 0">
      ${v.url ? `<a href="${esc(v.url)}" target="_blank" rel="noopener">Открыть оригинал ↗</a>` : ''}
      <span class="spacer"></span>
      <button id="v-adapt">Адаптировать CV под эту вакансию</button>
      ${v.source === 'manual' ? '<button id="v-delete" class="danger">Удалить</button>' : ''}
    </div>
    <div class="muted">ID ${v.id} · источник ${esc(v.source)} · опубликовано ${fmtDate(v.publishedAt)}</div>
    <hr>
    <div class="desc">${esc(v.description)}</div>`;
  $('#v-adapt').onclick = () => openAdapt({ vacancyId: v.id });
  const del = $('#v-delete');
  if (del) del.onclick = async () => {
    if (!confirm('Удалить вакансию?')) return;
    await api('/vacancies/' + v.id, { method: 'DELETE' });
    el.classList.add('empty'); el.textContent = 'Удалено';
    searchVacancies(vState.page);
  };
}

$('#v-search').onclick = () => searchVacancies(0);
$('#v-query').addEventListener('keydown', (e) => { if (e.key === 'Enter') searchVacancies(0); });
$('#v-prev').onclick = () => searchVacancies(vState.page - 1);
$('#v-next').onclick = () => searchVacancies(vState.page + 1);

$('#v-refresh').onclick = (e) => busy(e.target, async () => {
  const query = $('#v-query').value.trim();
  setStatus($('#v-status'), `Загружаю вакансии${query ? ' по запросу «' + query + '»' : ''}…`);
  try {
    const report = await api('/aggregation/refresh', { method: 'POST', json: { query } });
    const lines = report.results.map((r) => r.error
      ? `${r.source}: ошибка — ${r.error}`
      : `${r.source}: получено ${r.fetched}, новых ${r.created}, обновлено ${r.updated} (${(r.durationMs / 1000).toFixed(1)} c)`);
    setStatus($('#v-status'), lines.join('\n'), report.results.some((r) => r.error));
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
  $('#a-download').onclick = () => {
    const blob = new Blob([a.content], { type: 'text/markdown' });
    const link = Object.assign(document.createElement('a'), {
      href: URL.createObjectURL(blob),
      download: `cv-${(a.company || 'company').replace(/[^\w-]+/g, '_')}-${a.vacancyId}.md`,
    });
    link.click();
    URL.revokeObjectURL(link.href);
  };
  $('#a-del').onclick = async () => {
    if (!confirm('Удалить эту адаптацию?')) return;
    await api('/adaptations/' + a.id, { method: 'DELETE' });
    el.classList.add('empty'); el.textContent = 'Удалено';
    loadHistory();
  };
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
