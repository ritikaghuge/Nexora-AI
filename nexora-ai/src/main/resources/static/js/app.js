'use strict';
/* NEXORA AI front-end (no build step). Talks to the Spring Boot REST + SSE API. */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const MODES = [
  { id: 'SMART', label: '✦ Smart' }, { id: 'GENERAL', label: '💬 Chat' }, { id: 'PROGRAMMING', label: '</> Code' },
  { id: 'DOCUMENT', label: '📄 Documents' }, { id: 'RESEARCH', label: '🌐 Web research' }, { id: 'DEEP_RESEARCH', label: '🔬 Deep research' },
  { id: 'WRITING', label: '✍ Writing' }, { id: 'MATH', label: '∑ Math' }, { id: 'IMAGE', label: '🖼 Image' },
  { id: 'LEARNING', label: '🎓 Learning' }, { id: 'PROJECT_BUILDER', label: '🧱 Project builder' }
];
const MODE_LABEL = Object.fromEntries(MODES.map(m => [m.id, m.label.replace(/^\S+\s/, '')]));
const SUGGESTIONS = {
  SMART: ['Explain how Spring Security sessions work', 'Latest news about open-source LLMs', 'Solve 3x^2 - 12x + 9 = 0', 'Write a leave application email'],
  GENERAL: ['Explain quantum computing simply', 'Give me a 3-day study plan for DBMS', 'Pros and cons of microservices'],
  PROGRAMMING: ['Write a Java REST controller with validation', 'Explain this error: NullPointerException', 'Write a MySQL query for top 5 customers by revenue', 'Review my code for bugs'],
  DOCUMENT: ['Attach a PDF/DOCX/TXT with the 📎 button, then ask questions about it'],
  RESEARCH: ['Latest stable Java release features', 'Who won the most recent Nobel Prize in Physics?'],
  DEEP_RESEARCH: ['Compare Ollama, LM Studio and vLLM for local LLM hosting', 'Research the impact of RAG on LLM accuracy'],
  WRITING: ['Write a professional resume summary for a Computer Engineering student', 'Fix the grammar: "he go to school yesterday"', 'Translate "Good morning, how are you?" into Hindi and Marathi'],
  MATH: ['(12.5 * 4 + sqrt(144)) / 2', 'Mean and standard deviation of 4, 8, 15, 16, 23, 42', 'Explain the quadratic formula'],
  IMAGE: ['Attach an image with 📎, then ask: "Describe this image"'],
  LEARNING: ['Create 5 MCQs on SQL joins with answers', 'Explain recursion at beginner, intermediate and advanced levels', 'Give me 5 Java interview questions'],
  PROJECT_BUILDER: ['Build a to-do list web app with Node.js and SQLite', 'Create a Spring Boot library management REST API with MySQL']
};
const DOC_ACTIONS = [
  ['Summarize', 'Summarize the attached document(s) clearly.'], ['Key information', 'Extract the most important information, dates, names and numbers from the attached document(s).'],
  ['Study notes', 'Generate concise study notes from the attached document(s).'], ['Q&A set', 'Generate 10 question-and-answer pairs from the attached document(s).']
];

const state = {
  user: null, convs: [], convId: null, mode: 'SMART', messages: [], files: [], streaming: false, abort: null,
  settings: null, providers: [], voiceChat: false, recog: null, listening: false, editingProvider: null, searchTimer: null
};

/* ---------------- helpers ---------------- */
async function api(path, { method = 'GET', body, form } = {}) {
  const opt = { method, headers: {}, credentials: 'same-origin' };
  if (body !== undefined) { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(body); }
  if (form) opt.body = form;
  const r = await fetch(path, opt);
  if (r.status === 401 && !path.startsWith('/api/auth/login') && !path.startsWith('/api/auth/register')) { showAuth(); throw new Error('Please sign in'); }
  if (!r.ok) {
    let m = r.statusText;
    try { const j = await r.json(); m = j.message || m; } catch { /* keep status text */ }
    throw new Error(m);
  }
  if (r.status === 204) return null;
  return (r.headers.get('content-type') || '').includes('json') ? r.json() : r.text();
}
function toast(msg, bad) {
  const t = $('#toast'); t.textContent = msg; t.className = 'toast' + (bad ? ' bad' : '');
  clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add('hidden'), 3800);
}
function md(text) {
  if (window.marked && window.DOMPurify) return DOMPurify.sanitize(marked.parse(text, { breaks: true, gfm: true }));
  return '<p>' + esc(text).replace(/\n/g, '<br>') + '</p>';
}
function enhance(el) {
  $$('pre', el).forEach(pre => {
    if (pre.dataset.done) return; pre.dataset.done = '1';
    const code = $('code', pre);
    if (window.hljs && code) { try { hljs.highlightElement(code); } catch { /* ignore */ } }
    const lang = ((code && code.className.match(/language-([\w-]+)/)) || [])[1] || 'code';
    const bar = document.createElement('div'); bar.className = 'codebar';
    bar.innerHTML = `<span>${esc(lang)}</span><button type="button">Copy</button>`;
    $('button', bar).addEventListener('click', e => { navigator.clipboard.writeText(code ? code.innerText : pre.innerText); e.target.textContent = 'Copied'; setTimeout(() => e.target.textContent = 'Copy', 1200); });
    pre.prepend(bar);
  });
}
function plain(text) { return text.replace(/```[\s\S]*?```/g, ' code block omitted. ').replace(/[#*_`>|~-]+/g, ' ').replace(/\[(\d+)\]/g, '').replace(/\s+/g, ' ').trim(); }
function show(el, on) { el.classList.toggle('hidden', !on); }
function initials(n) { return (n || '?').trim().split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase(); }

/* ---------------- theme ---------------- */
function applyTheme(t) {
  const eff = t === 'system' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : (t || 'dark');
  document.documentElement.dataset.theme = eff; localStorage.setItem('nexora-theme', t || 'dark');
}
applyTheme(localStorage.getItem('nexora-theme') || 'dark');

/* ---------------- auth ---------------- */
function showAuth() { show($('#auth'), true); show($('#app'), false); }
function authError(m) { const b = $('#authError'); b.textContent = m; show(b, !!m); }
$$('#authTabs button').forEach(b => b.addEventListener('click', () => {
  $$('#authTabs button').forEach(x => x.classList.toggle('active', x === b));
  show($('#loginForm'), b.dataset.tab === 'login'); show($('#registerForm'), b.dataset.tab === 'register'); authError('');
}));
$('#loginForm').addEventListener('submit', async e => {
  e.preventDefault(); authError('');
  try { state.user = await api('/api/auth/login', { method: 'POST', body: { email: $('#loginEmail').value, password: $('#loginPass').value } }); $('#loginPass').value = ''; await boot(); }
  catch (err) { authError(err.message); }
});
$('#registerForm').addEventListener('submit', async e => {
  e.preventDefault(); authError('');
  try {
    const email = $('#regEmail').value, password = $('#regPass').value;
    await api('/api/auth/register', { method: 'POST', body: { displayName: $('#regName').value, email, password } });
    state.user = await api('/api/auth/login', { method: 'POST', body: { email, password } }); $('#regPass').value = ''; await boot();
  } catch (err) { authError(err.message); }
});

/* ---------------- boot ---------------- */
async function boot() {
  show($('#auth'), false); show($('#app'), true);
  $('#avatar').textContent = initials(state.user.displayName); $('#pname').textContent = state.user.displayName;
  state.settings = await api('/api/settings'); applyTheme(state.settings.theme);
  state.providers = await api('/api/providers');
  renderModes(); await loadConvs(); newChat();
}
function renderModes() {
  $('#modes').innerHTML = MODES.map(m => `<button type="button" class="mode ${m.id === state.mode ? 'active' : ''}" data-mode="${m.id}">${esc(m.label)}</button>`).join('');
  $$('#modes .mode').forEach(b => b.addEventListener('click', () => setMode(b.dataset.mode)));
  renderSuggestions();
}
function setMode(m) { state.mode = m; renderModes(); $('#input').focus(); }
function renderSuggestions() {
  $('#suggestions').innerHTML = (SUGGESTIONS[state.mode] || []).map(s => `<button type="button" class="sug">${esc(s)}</button>`).join('');
  $$('#suggestions .sug').forEach(b => b.addEventListener('click', () => { $('#input').value = b.textContent; autosize(); $('#input').focus(); }));
}
$$('.fcard').forEach(c => c.addEventListener('click', () => setMode(c.dataset.mode)));

/* ---------------- conversations ---------------- */
async function loadConvs(q) {
  state.convs = await api('/api/conversations' + (q ? '?q=' + encodeURIComponent(q) : ''));
  const list = $('#convList');
  if (!state.convs.length) { list.innerHTML = '<div class="muted" style="padding:10px">No conversations yet</div>'; return; }
  list.innerHTML = state.convs.map(c => `<div class="conv ${c.id === state.convId ? 'active' : ''}" data-id="${c.id}"><span class="t" title="${esc(c.title)}">${esc(c.title)}</span><span class="acts"><button type="button" data-a="rename" title="Rename">✎</button><button type="button" data-a="delete" title="Delete">🗑</button></span></div>`).join('');
  $$('.conv', list).forEach(el => {
    const id = Number(el.dataset.id);
    el.addEventListener('click', e => {
      const a = e.target.dataset.a;
      if (a === 'rename') return renameConv(id);
      if (a === 'delete') return deleteConv(id);
      openConv(id); $('#sidebar').classList.remove('open'); show($('#scrim'), false);
    });
  });
}
async function renameConv(id) {
  const cur = state.convs.find(c => c.id === id); const t = prompt('Rename conversation', cur ? cur.title : '');
  if (!t || !t.trim()) return;
  try { await api('/api/conversations/' + id, { method: 'PATCH', body: { title: t.trim() } }); await loadConvs($('#convSearch').value); } catch (e) { toast(e.message, true); }
}
async function deleteConv(id) {
  if (!confirm('Delete this conversation?')) return;
  try { await api('/api/conversations/' + id, { method: 'DELETE' }); if (state.convId === id) newChat(); await loadConvs($('#convSearch').value); } catch (e) { toast(e.message, true); }
}
function newChat() {
  if (state.streaming) stopGen();
  state.convId = null; state.messages = []; state.files = []; renderFiles(); renderMessages(); loadConvs($('#convSearch').value).catch(() => {});
}
async function openConv(id) {
  if (state.streaming) stopGen();
  try { state.messages = await api(`/api/conversations/${id}/messages`); state.convId = id; state.files = []; renderFiles(); renderMessages(); await loadConvs($('#convSearch').value); }
  catch (e) { toast(e.message, true); }
}
$('#newChat').addEventListener('click', newChat);
$('#convSearch').addEventListener('input', e => { clearTimeout(state.searchTimer); state.searchTimer = setTimeout(() => loadConvs(e.target.value).catch(() => {}), 250); });
$('#menuBtn').addEventListener('click', () => { $('#sidebar').classList.add('open'); show($('#scrim'), true); });
$('#scrim').addEventListener('click', () => { $('#sidebar').classList.remove('open'); show($('#scrim'), false); });

/* ---------------- rendering messages ---------------- */
function renderMessages() {
  const box = $('#messages'); show($('#welcome'), state.messages.length === 0);
  box.innerHTML = '';
  state.messages.forEach((m, i) => box.appendChild(messageEl(m, i)));
  scrollDown(true);
}
function messageEl(m, i) {
  const el = document.createElement('div'); const isUser = m.role === 'USER';
  el.className = 'msg ' + (isUser ? 'user' : 'assistant'); el.dataset.i = i;
  if (isUser) {
    const imgs = (m.fileIds || []).map(id => `<img src="/api/files/${id}/content" alt="" style="max-height:70px;border-radius:8px;margin-right:6px">`).join('');
    el.innerHTML = `<div class="av">${esc(initials(state.user.displayName))}</div><div class="body"><div class="bubble">${esc(m.content)}</div><div>${imgs}</div></div>`;
    $$('img', el).forEach(im => im.addEventListener('error', () => im.remove()));
    return el;
  }
  el.innerHTML = `<div class="av">N</div><div class="body"><div class="meta-line"></div><div class="status"></div><div class="md"></div><div class="extra"></div><div class="actions"></div></div>`;
  paintAssistant(el, m); return el;
}
function paintAssistant(el, m) {
  const meta = $('.meta-line', el), md_ = $('.md', el), extra = $('.extra', el), acts = $('.actions', el), st = $('.status', el);
  let chips = '';
  if (m.mode) chips += `<span class="chip">${esc(MODE_LABEL[m.mode] || m.mode)}</span>`;
  if (m.model) chips += `<span class="chip">${esc(m.model)}</span>`;
  if (m.routeReason) chips += `<span class="chip" title="Smart Mode decision">✦ Smart → ${esc(m.routeReason)}</span>`;
  meta.innerHTML = chips;
  st.textContent = m.status || ''; show(st, !!m.status);
  if (m.pending && !m.content) md_.innerHTML = '<div class="typing"><i></i><i></i><i></i></div>';
  else { md_.innerHTML = md(m.content || ''); enhance(md_); }
  let x = '';
  if (m.sources && m.sources.length) {
    x += `<div class="sources"><h4>Retrieved sources (the analysis above is AI-generated, not verified)</h4>` +
      m.sources.map((s, i) => `<a href="${esc(s.url)}" target="_blank" rel="noopener noreferrer" title="${esc(s.snippet)}">[${i + 1}] ${esc(s.title)} — ${esc(s.url)}</a>`).join('') + '</div>';
  }
  if (m.error) x += `<div class="err">⚠ ${esc(m.error)}</div>` + (/provider|model|connect|Ollama/i.test(m.error) ? '<div class="note">Tip: open Settings → AI Providers to test the connection or switch provider.</div>' : '');
  if (!m.pending) x += '<div class="note">AI-generated · may contain mistakes · not verified</div>';
  extra.innerHTML = x;
  acts.innerHTML = '';
  if (!m.pending && m.content) {
    const add = (label, fn) => { const b = document.createElement('button'); b.type = 'button'; b.className = 'icon-btn'; b.textContent = label; b.addEventListener('click', fn); acts.appendChild(b); };
    add('📋 Copy', () => { navigator.clipboard.writeText(m.content); toast('Copied'); });
    add('🔊 Speak', () => speak(m.content));
    if (el.dataset.i == state.messages.length - 1) add('↻ Regenerate', () => send({ regenerate: true }));
    if (m.id && /(^|\n)[#*\s]*(File|Path)\s*:/i.test(m.content)) add('⬇ Download project ZIP', () => { location.href = `/api/messages/${m.id}/project.zip`; });
  }
}
function scrollDown(force) {
  const c = $('#chat'); const near = c.scrollHeight - c.scrollTop - c.clientHeight < 160;
  if (force || near) c.scrollTop = c.scrollHeight;
}

/* ---------------- sending / streaming ---------------- */
function autosize() { const t = $('#input'); t.style.height = 'auto'; t.style.height = Math.min(t.scrollHeight, 200) + 'px'; }
$('#input').addEventListener('input', autosize);
$('#input').addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); onSendClick(); } });
$('#sendBtn').addEventListener('click', onSendClick);
function onSendClick() { if (state.streaming) stopGen(); else send(); }
function stopGen() { if (state.abort) state.abort.abort(); }
function setStreaming(on) {
  state.streaming = on; const b = $('#sendBtn'); b.textContent = on ? '■' : '➤'; b.classList.toggle('stop', on); b.title = on ? 'Stop generating' : 'Send';
}

async function send(opts = {}) {
  if (state.streaming) return;
  const input = $('#input'); let text = input.value.trim();
  if (!opts.regenerate) {
    if (!text && state.files.length) text = 'Please summarize the attached file(s).';
    if (!text) return;
  }
  if (opts.regenerate) { while (state.messages.length && state.messages[state.messages.length - 1].role === 'ASSISTANT') state.messages.pop(); }
  else state.messages.push({ role: 'USER', content: text, fileIds: state.files.map(f => f.id) });
  const ast = { role: 'ASSISTANT', content: '', pending: true, sources: [], mode: null, model: null, status: '' };
  state.messages.push(ast); renderMessages();
  const body = { conversationId: state.convId, message: opts.regenerate ? null : text, mode: state.mode, fileIds: state.files.map(f => f.id), regenerate: !!opts.regenerate };
  if (!opts.regenerate) { input.value = ''; autosize(); state.files = []; renderFiles(); }
  const ctrl = new AbortController(); state.abort = ctrl; setStreaming(true);
  const el = () => $(`#messages .msg[data-i="${state.messages.length - 1}"]`);
  let raf = 0; const repaint = () => { if (!raf) raf = requestAnimationFrame(() => { raf = 0; const e = el(); if (e) { paintAssistant(e, ast); scrollDown(false); } }); };
  try {
    const resp = await fetch('/api/chat/stream', { method: 'POST', credentials: 'same-origin', signal: ctrl.signal, headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' }, body: JSON.stringify(body) });
    if (resp.status === 401) { showAuth(); throw new Error('Please sign in'); }
    if (!resp.ok) { let m = resp.statusText; try { m = (await resp.json()).message || m; } catch { /* ignore */ } throw new Error(m); }
    const reader = resp.body.getReader(); const dec = new TextDecoder(); let buf = '';
    for (;;) {
      const { value, done } = await reader.read(); if (done) break;
      buf += dec.decode(value, { stream: true });
      let idx;
      while ((idx = buf.indexOf('\n\n')) >= 0) { const frame = buf.slice(0, idx); buf = buf.slice(idx + 2); handleFrame(frame, ast); repaint(); }
    }
  } catch (e) { if (e.name !== 'AbortError') ast.error = ast.error || e.message; }
  ast.pending = false; ast.status = ''; setStreaming(false); state.abort = null;
  const e2 = el(); if (e2) paintAssistant(e2, ast);
  if (ast.content && state.settings && state.voiceChat) speak(ast.content, () => startListening());
  await new Promise(r => setTimeout(r, ctrlWasAborted(ctrl) ? 700 : 150));
  await loadConvs($('#convSearch').value).catch(() => {});
  if (state.convId) {
    try { const fresh = await api(`/api/conversations/${state.convId}/messages`); const keepErr = ast.error; state.messages = fresh; if (keepErr && fresh.length) { /* error shown below */ } renderMessages(); if (keepErr) { const last = fresh[fresh.length - 1]; if (last && last.role === 'USER') { state.messages.push({ role: 'ASSISTANT', content: '', error: keepErr }); renderMessages(); } } } catch { /* keep local view */ }
  }
}
function ctrlWasAborted(c) { return c.signal.aborted; }
function handleFrame(frame, ast) {
  let ev = 'message', data = '';
  frame.split('\n').forEach(l => { if (l.startsWith('event:')) ev = l.slice(6).trim(); else if (l.startsWith('data:')) data += l.slice(5).trim(); });
  if (!data) return; let j; try { j = JSON.parse(data); } catch { return; }
  switch (ev) {
    case 'meta': state.convId = j.conversationId; ast.mode = j.mode; ast.model = j.model; ast.routeReason = j.requestedMode === 'SMART' ? j.routeReason : ''; break;
    case 'status': ast.status = j.s; break;
    case 'sources': ast.sources = j; break;
    case 'token': ast.content += j.t; ast.status = ''; break;
    case 'error': ast.error = j.message; break;
    case 'done': ast.id = j.messageId || undefined; break;
    default: break;
  }
}

/* ---------------- files ---------------- */
$('#attachBtn').addEventListener('click', () => $('#fileInput').click());
$('#fileInput').addEventListener('change', async e => {
  for (const f of e.target.files) {
    const fd = new FormData(); fd.append('file', f);
    try { const dto = await api('/api/files', { method: 'POST', form: fd }); state.files.push(dto); if (dto.warning) toast(dto.warning); if (dto.kind === 'DOCUMENT' && state.mode === 'SMART') {/* smart mode will detect */} }
    catch (err) { toast(`${f.name}: ${err.message}`, true); }
  }
  e.target.value = ''; renderFiles();
});
function renderFiles() {
  $('#attachments').innerHTML = state.files.map((f, i) => `<div class="att">${f.kind === 'IMAGE' ? `<img src="/api/files/${f.id}/content" alt="">` : '📄'}<span>${esc(f.name)}</span><button type="button" data-i="${i}" title="Remove">✕</button></div>`).join('');
  $$('#attachments button').forEach(b => b.addEventListener('click', async () => { const f = state.files[Number(b.dataset.i)]; state.files.splice(Number(b.dataset.i), 1); renderFiles(); try { await api('/api/files/' + f.id, { method: 'DELETE' }); } catch { /* ignore */ } }));
  const hasDoc = state.files.some(f => f.kind === 'DOCUMENT') || (state.convId && state.messages.some(m => (m.fileIds || []).length));
  const da = $('#docActions'); show(da, !!hasDoc);
  da.innerHTML = DOC_ACTIONS.map(([l]) => `<button type="button" class="btn small">${esc(l)}</button>`).join('');
  $$('button', da).forEach((b, i) => b.addEventListener('click', () => { $('#input').value = DOC_ACTIONS[i][1]; send(); }));
}

/* ---------------- voice ---------------- */
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
function startListening() {
  if (!SR) { toast('Speech recognition is not supported in this browser. Use Chrome or Edge.', true); return; }
  if (state.listening || state.streaming) return;
  const r = new SR(); r.lang = navigator.language || 'en-US'; r.interimResults = true; r.continuous = false; state.recog = r; state.listening = true; $('#micBtn').classList.add('on');
  let finalText = '';
  r.onresult = e => { let t = ''; for (const res of e.results) t += res[0].transcript; finalText = t; $('#input').value = t; autosize(); };
  r.onerror = e => { if (e.error !== 'no-speech' && e.error !== 'aborted') toast('Microphone error: ' + e.error, true); };
  r.onend = () => { state.listening = false; $('#micBtn').classList.remove('on'); if (state.voiceChat && finalText.trim()) send(); };
  try { r.start(); } catch { state.listening = false; $('#micBtn').classList.remove('on'); }
}
$('#micBtn').addEventListener('click', () => { if (state.listening && state.recog) state.recog.stop(); else startListening(); });
function speak(text, onend) {
  if (!('speechSynthesis' in window)) { toast('Text-to-speech is not supported in this browser', true); return; }
  speechSynthesis.cancel(); const u = new SpeechSynthesisUtterance(plain(text).slice(0, 4000)); u.lang = navigator.language || 'en-US';
  if (onend) u.onend = onend; speechSynthesis.speak(u);
}
$('#voiceChatBtn').addEventListener('click', () => {
  state.voiceChat = !state.voiceChat; $('#voiceChatBtn').classList.toggle('on', state.voiceChat);
  if (state.voiceChat) { toast('Voice chat on: speak your question; answers are read aloud'); startListening(); } else { speechSynthesis.cancel(); if (state.recog) state.recog.abort(); }
});

/* ---------------- theme button ---------------- */
$('#themeBtn').addEventListener('click', async () => {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; applyTheme(next);
  if (state.settings) { state.settings.theme = next; try { await api('/api/settings', { method: 'PUT', body: state.settings }); } catch { /* ignore */ } }
});

/* ---------------- settings ---------------- */
function openSettings() { show($('#settings'), true); fillPrefs(); renderProviders(); loadMemory(); $('#setName').value = state.user.displayName; }
$('#openSettings').addEventListener('click', openSettings);
$('#closeSettings').addEventListener('click', () => show($('#settings'), false));
$('#settings').addEventListener('click', e => { if (e.target.id === 'settings') show($('#settings'), false); });
$$('#setTabs button').forEach(b => b.addEventListener('click', () => {
  $$('#setTabs button').forEach(x => x.classList.toggle('active', x === b)); $$('.pane').forEach(p => show(p, p.dataset.pane === b.dataset.tab));
}));
$('#saveName').addEventListener('click', async () => { try { state.user = await api('/api/auth/profile', { method: 'PUT', body: { displayName: $('#setName').value } }); $('#pname').textContent = state.user.displayName; $('#avatar').textContent = initials(state.user.displayName); toast('Name updated'); } catch (e) { toast(e.message, true); } });
$('#savePw').addEventListener('click', async () => { try { await api('/api/auth/password', { method: 'POST', body: { currentPassword: $('#pwCur').value, newPassword: $('#pwNew').value } }); $('#pwCur').value = $('#pwNew').value = ''; toast('Password changed'); } catch (e) { toast(e.message, true); } });
$('#logoutBtn').addEventListener('click', async () => { try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* ignore */ } show($('#settings'), false); state.user = null; showAuth(); });
$('#setTemp').addEventListener('input', e => { $('#tempVal').textContent = e.target.value; });
function fillPrefs() {
  const s = state.settings; $('#setTheme').value = s.theme; $('#setModel').value = s.defaultModel || ''; $('#setTemp').value = s.temperature; $('#tempVal').textContent = s.temperature;
  $('#setCustom').value = s.customInstructions || ''; $('#setMemory').checked = s.memoryEnabled;
  $('#setProvider').innerHTML = '<option value="">(use the provider marked default)</option>' + state.providers.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('');
  $('#setProvider').value = s.defaultProviderId || '';
}
$('#savePrefs').addEventListener('click', async () => {
  const body = { theme: $('#setTheme').value, memoryEnabled: $('#setMemory').checked, defaultProviderId: $('#setProvider').value ? Number($('#setProvider').value) : null, defaultModel: $('#setModel').value, temperature: Number($('#setTemp').value), customInstructions: $('#setCustom').value };
  try { state.settings = await api('/api/settings', { method: 'PUT', body }); applyTheme(state.settings.theme); toast('Preferences saved'); } catch (e) { toast(e.message, true); }
});

/* providers */
const PRESETS = {
  ollama: { name: 'Local Ollama', type: 'OLLAMA', url: 'http://localhost:11434', model: 'llama3.2' },
  lmstudio: { name: 'LM Studio', type: 'OPENAI_COMPATIBLE', url: 'http://localhost:1234/v1', model: '' },
  groq: { name: 'Groq', type: 'OPENAI_COMPATIBLE', url: 'https://api.groq.com/openai/v1', model: 'llama-3.3-70b-versatile' },
  openrouter: { name: 'OpenRouter', type: 'OPENAI_COMPATIBLE', url: 'https://openrouter.ai/api/v1', model: '' }
};
$('#provPreset').addEventListener('change', e => { const p = PRESETS[e.target.value]; if (!p) return; $('#provName').value = p.name; $('#provType').value = p.type; $('#provUrl').value = p.url; $('#provModel').value = p.model; });
function renderProviders() {
  const box = $('#providerList');
  box.innerHTML = state.providers.map(p => `<div class="prov" data-id="${p.id}"><div class="info"><b>${esc(p.name)}</b> ${p.preferred ? '<span class="chip">default</span>' : ''}<small>${esc(p.type)} · ${esc(p.baseUrl)} · ${esc(p.defaultModel || 'no model set')}${p.hasApiKey ? ' · 🔑 key saved' : ''}</small><small class="res"></small></div><button type="button" class="btn small" data-a="test">Test</button><button type="button" class="btn small" data-a="edit">Edit</button><button type="button" class="btn small danger" data-a="del">Delete</button></div>`).join('') || '<p class="muted">No providers yet.</p>';
  $$('.prov', box).forEach(el => {
    const id = Number(el.dataset.id); const p = state.providers.find(x => x.id === id);
    $('[data-a=test]', el).addEventListener('click', async () => { const r = $('.res', el); r.textContent = 'Testing…'; try { const t = await api(`/api/providers/${id}/test`, { method: 'POST' }); r.textContent = (t.ok ? '✅ ' : '❌ ') + t.message + (t.models.length ? ' Models: ' + t.models.slice(0, 12).join(', ') : ''); if (t.ok) $('#modelList').innerHTML = t.models.map(m => `<option value="${esc(m)}">`).join(''); } catch (e) { r.textContent = '❌ ' + e.message; } });
    $('[data-a=edit]', el).addEventListener('click', () => { state.editingProvider = id; $('#provFormTitle').textContent = 'Edit provider'; $('#provName').value = p.name; $('#provType').value = p.type; $('#provUrl').value = p.baseUrl; $('#provKey').value = ''; $('#provModel').value = p.defaultModel || ''; $('#provVision').value = p.visionModel || ''; $('#provCode').value = p.codeModel || ''; $('#provPreferred').checked = p.preferred; });
    $('[data-a=del]', el).addEventListener('click', async () => { if (!confirm('Delete this provider?')) return; try { await api('/api/providers/' + id, { method: 'DELETE' }); state.providers = await api('/api/providers'); renderProviders(); fillPrefs(); } catch (e) { toast(e.message, true); } });
  });
}
function resetProvForm() { state.editingProvider = null; $('#provFormTitle').textContent = 'Add provider'; ['provName', 'provUrl', 'provKey', 'provModel', 'provVision', 'provCode'].forEach(i => $('#' + i).value = ''); $('#provPreferred').checked = false; $('#provPreset').value = ''; }
$('#resetProv').addEventListener('click', resetProvForm);
$('#saveProv').addEventListener('click', async () => {
  const body = { name: $('#provName').value, type: $('#provType').value, baseUrl: $('#provUrl').value, apiKey: $('#provKey').value, defaultModel: $('#provModel').value, visionModel: $('#provVision').value, codeModel: $('#provCode').value, preferred: $('#provPreferred').checked };
  try {
    if (state.editingProvider) await api('/api/providers/' + state.editingProvider, { method: 'PUT', body }); else await api('/api/providers', { method: 'POST', body });
    state.providers = await api('/api/providers'); resetProvForm(); renderProviders(); fillPrefs(); toast('Provider saved');
  } catch (e) { toast(e.message, true); }
});

/* memory + data */
async function loadMemory() {
  try {
    const list = await api('/api/memory');
    $('#memList').innerHTML = list.map(m => `<div class="mem" data-id="${m.id}"><div class="info">${esc(m.content)}</div><button type="button" class="btn small danger">Delete</button></div>`).join('') || '<p class="muted">Nothing remembered yet.</p>';
    $$('#memList .mem').forEach(el => $('button', el).addEventListener('click', async () => { await api('/api/memory/' + el.dataset.id, { method: 'DELETE' }); loadMemory(); }));
  } catch (e) { toast(e.message, true); }
}
$('#addMem').addEventListener('click', async () => { const v = $('#memInput').value.trim(); if (!v) return; try { await api('/api/memory', { method: 'POST', body: { content: v } }); $('#memInput').value = ''; loadMemory(); } catch (e) { toast(e.message, true); } });
$('#clearMem').addEventListener('click', async () => { if (!confirm('Delete all memories?')) return; await api('/api/memory', { method: 'DELETE' }); loadMemory(); });
$('#clearAll').addEventListener('click', async () => { if (!confirm('Delete ALL conversations? This cannot be undone.')) return; try { await api('/api/conversations', { method: 'DELETE' }); newChat(); toast('History cleared'); } catch (e) { toast(e.message, true); } });

/* ---------------- start ---------------- */
(async () => {
  try { state.user = await api('/api/auth/me'); await boot(); } catch { showAuth(); }
})();
