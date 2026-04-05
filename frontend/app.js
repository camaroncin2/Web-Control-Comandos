/* ============================================================
   app.js — MC Command Panel
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
let apiUrl     = localStorage.getItem('mcpanel_api') || '';
let token      = localStorage.getItem('mcpanel_tok') || '';
let cmdData    = null;  // parsed /api/commands response
let rulesData  = {};    // parsed /api/rules response  { cmdName: CommandRule }
let levelFilter = 'all';

// ── API helper ─────────────────────────────────────────────
async function api(method, path, body) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    return fetch(apiUrl + path, {
        method,
        headers,
        body: body != null ? JSON.stringify(body) : undefined
    });
}

// ── Init ───────────────────────────────────────────────────
function init() {
    if (!apiUrl)  { showSetup();  return; }
    if (!token)   { showLogin();  return; }
    showPanel();
}

// ── Setup modal ────────────────────────────────────────────
function showSetup() {
    el('modal-setup').classList.remove('hidden');
    el('overlay').classList.remove('hidden');
    el('input-api-url').value = apiUrl;
}

el('btn-save-url').addEventListener('click', () => {
    const val = el('input-api-url').value.trim().replace(/\/+$/, '');
    const errEl = el('setup-error');
    if (!val.match(/^https?:\/\/.+/)) {
        showErr(errEl, 'Ingresa una URL válida, ej: http://tuservidor.com:8080');
        return;
    }
    hideErr(errEl);
    apiUrl = val;
    localStorage.setItem('mcpanel_api', apiUrl);
    el('modal-setup').classList.add('hidden');
    el('overlay').classList.add('hidden');
    showLogin();
});

// Allow Enter key in URL input
el('input-api-url').addEventListener('keydown', e => {
    if (e.key === 'Enter') el('btn-save-url').click();
});

// ── Login ──────────────────────────────────────────────────
function showLogin() {
    el('view-panel').classList.add('hidden');
    el('view-login').classList.remove('hidden');
    hideErr(el('login-error'));
}

el('form-login').addEventListener('submit', async e => {
    e.preventDefault();
    const username = el('input-user').value.trim();
    const password = el('input-pass').value;
    const btn      = el('btn-login');
    const errEl    = el('login-error');

    btn.disabled    = true;
    btn.textContent = 'Ingresando...';
    hideErr(errEl);

    try {
        const res = await api('POST', '/api/login', { username, password });
        if (res.ok) {
            const data = await res.json();
            token = data.token;
            localStorage.setItem('mcpanel_tok', token);
            el('view-login').classList.add('hidden');
            showPanel();
        } else {
            showErr(errEl, 'Usuario o contraseña incorrectos.');
        }
    } catch {
        showErr(errEl, 'No se pudo conectar al servidor. Verifica la URL.');
    } finally {
        btn.disabled    = false;
        btn.textContent = 'Ingresar';
    }
});

el('btn-change-url').addEventListener('click', () => {
    el('view-login').classList.add('hidden');
    showSetup();
});

// ── Panel ──────────────────────────────────────────────────
function showPanel() {
    el('view-login').classList.add('hidden');
    el('view-panel').classList.remove('hidden');
    el('api-url-badge').textContent = apiUrl;
    loadAll();
}

async function loadAll() {
    await Promise.all([ loadCommands(), loadRules() ]);
}

async function loadCommands() {
    el('command-tree').innerHTML = '<div class="loading-msg">Cargando comandos...</div>';
    try {
        const res = await api('GET', '/api/commands');
        if (res.status === 401) { onUnauthorized(); return; }
        cmdData = await res.json();
        renderTree();
    } catch {
        el('command-tree').innerHTML =
            '<div class="loading-msg">&#9888; No se pudo conectar al servidor.</div>';
    }
}

async function loadRules() {
    try {
        const res = await api('GET', '/api/rules');
        if (res.status === 401) { onUnauthorized(); return; }
        rulesData = await res.json();
        renderRules();
    } catch {
        el('rules-container').innerHTML =
            '<div class="loading-msg">&#9888; Error al cargar reglas.</div>';
    }
}

function onUnauthorized() {
    token = '';
    localStorage.removeItem('mcpanel_tok');
    showLogin();
}

// Logout
el('btn-logout').addEventListener('click', async () => {
    try { await api('POST', '/api/logout'); } catch {}
    token = '';
    localStorage.removeItem('mcpanel_tok');
    showLogin();
});

// Refresh
el('btn-refresh').addEventListener('click', loadAll);

// ── Tab switching ──────────────────────────────────────────
qsa('.tab').forEach(btn => {
    btn.addEventListener('click', () => {
        qsa('.tab').forEach(t => t.classList.remove('active'));
        qsa('.tab-content').forEach(c => c.classList.add('hidden'));
        btn.classList.add('active');
        el(btn.dataset.tab).classList.remove('hidden');
    });
});

// ── Level filters ──────────────────────────────────────────
qsa('.filter-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        qsa('.filter-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        levelFilter = btn.dataset.level;
        renderTree();
    });
});

// Search
el('search-bar').addEventListener('input', renderTree);
el('rules-search').addEventListener('input', renderRules);

// ── Tree rendering ─────────────────────────────────────────
function levelLabel(n) {
    return ({ '-1': 'Custom', 0: 'Sin OP', 1: 'Nivel 1', 2: 'GM', 3: 'Admin', 4: 'Owner' })[n] ?? n;
}

function renderTree() {
    if (!cmdData) return;
    const commands  = cmdData.commands || [];
    const searchVal = el('search-bar').value.toLowerCase().trim();

    // Stats
    const statsEl = el('stats-bar');
    statsEl.classList.remove('hidden');
    statsEl.innerHTML = buildStats(commands);

    // Filter root commands only (search + level filter)
    const filtered = commands.filter(cmd => {
        const matchLevel  = levelFilter === 'all' || String(cmd.minLevel) === levelFilter;
        const matchSearch = !searchVal  || cmd.name.toLowerCase().includes(searchVal)
                                        || cmd.path.toLowerCase().includes(searchVal);
        return matchLevel && matchSearch;
    });

    if (filtered.length === 0) {
        el('command-tree').innerHTML = '<div class="loading-msg">Sin resultados para este filtro.</div>';
        return;
    }

    el('command-tree').innerHTML = filtered.map(cmd => nodeHtml(cmd, 0)).join('');
    attachTreeEvents();
}

/** Recursively build the HTML for a command node and its children. */
function nodeHtml(node, depth) {
    const isArg     = node.type === 'argument';
    const hasChild  = node.children && node.children.length > 0;
    const rule      = rulesData[node.name] || null;
    const blocked   = rule && (rule.blockedForAll || rule.blockedForOp
                               || (rule.blockedUsernames && rule.blockedUsernames.length > 0));

    const indent   = depth * 18; // px left padding per depth
    const chevron  = hasChild
        ? `<span class="tree-chevron">&#9654;</span>`
        : `<span class="no-chevron"></span>`;

    const slashHtml = depth === 0 ? `<span class="cmd-slash">/</span>` : '';
    const displayName = isArg ? `&lt;${esc(node.name)}&gt;` : esc(node.name);
    const nameHtml  = `<span class="cmd-name${isArg ? ' arg' : ''}">${slashHtml}${displayName}</span>`;
    const lvlHtml   = `<span class="lvl-badge lvl-${node.minLevel}">${levelLabel(node.minLevel)}</span>`;
    const blkHtml   = blocked ? `<span class="blocked-badge">BLOQUEADO</span>` : '';
    // Quick-rule button only on root level nodes
    const ruleBtn   = depth === 0
        ? `<button class="btn-quick-rule" data-cmd="${esc(node.name)}">Editar regla</button>`
        : '';

    let html = `<div class="tree-node" style="padding-left:${indent}px">`;
    html    += `<div class="tree-row" data-has-children="${hasChild}">`;
    html    += chevron + nameHtml + lvlHtml + blkHtml + ruleBtn;
    html    += `</div>`;

    if (hasChild) {
        html += `<div class="tree-children hidden">`;
        html += node.children.map(c => nodeHtml(c, depth + 1)).join('');
        html += `</div>`;
    }
    html += `</div>`;
    return html;
}

function attachTreeEvents() {
    // Expand / collapse
    qsa('#command-tree .tree-row').forEach(row => {
        row.addEventListener('click', e => {
            if (e.target.closest('.btn-quick-rule')) return;
            const children = row.nextElementSibling;
            if (children && children.classList.contains('tree-children')) {
                const expanded = row.classList.toggle('expanded');
                children.classList.toggle('hidden', !expanded);
            }
        });
    });
    // Quick rule buttons
    qsa('#command-tree .btn-quick-rule').forEach(btn => {
        btn.addEventListener('click', e => {
            e.stopPropagation();
            openRuleModal(btn.dataset.cmd);
        });
    });
}

function buildStats(cmds) {
    const count = { '-1': 0, 0: 0, 1: 0, 2: 0, 3: 0, 4: 0 };
    cmds.forEach(c => { count[c.minLevel] = (count[c.minLevel] || 0) + 1; });
    let parts = [`Total: <strong>${cmds.length}</strong>`];
    [0,1,2,3,4,-1].forEach(l => {
        if (count[l]) parts.push(`${levelLabel(l)}: <strong>${count[l]}</strong>`);
    });
    return parts.join(' &nbsp;|&nbsp; ');
}

// ── Rules rendering ────────────────────────────────────────
function renderRules() {
    const search  = el('rules-search').value.toLowerCase().trim();
    const entries = Object.entries(rulesData)
        .filter(([k]) => !search || k.includes(search));

    if (entries.length === 0) {
        el('rules-container').innerHTML =
            `<div class="rules-empty">No hay reglas activas.<br>
             <small>Usa "Editar regla" en el árbol de comandos para crear una.</small></div>`;
        return;
    }

    let html = `<table class="rules-table">
      <thead><tr>
        <th>Comando</th>
        <th>Todos</th>
        <th>OP</th>
        <th>Usuarios bloqueados</th>
        <th></th>
      </tr></thead><tbody>`;

    entries.forEach(([cmd, rule]) => {
        const userTags = (rule.blockedUsernames || [])
            .map(u => `<span class="user-tag">${esc(u)}</span>`).join('');
        html += `<tr>
          <td>/${esc(cmd)}</td>
          <td class="${rule.blockedForAll ? 'tick' : 'dash'}">${rule.blockedForAll ? '&#10003; Sí' : '—'}</td>
          <td class="${rule.blockedForOp  ? 'tick' : 'dash'}">${rule.blockedForOp  ? '&#10003; Sí' : '—'}</td>
          <td>${userTags || '<span class="dash">—</span>'}</td>
          <td><button class="btn-edit-rule" data-cmd="${esc(cmd)}">Editar</button></td>
        </tr>`;
    });

    html += '</tbody></table>';
    el('rules-container').innerHTML = html;

    qsa('.btn-edit-rule').forEach(btn =>
        btn.addEventListener('click', () => openRuleModal(btn.dataset.cmd)));
}

// ── Rule modal ─────────────────────────────────────────────
el('btn-add-rule').addEventListener('click', () => openRuleModal(''));

function openRuleModal(command) {
    const rule = rulesData[command] || {};
    el('rule-command').value   = command;
    el('rule-command').disabled = !!command;
    el('rule-all').checked      = !!rule.blockedForAll;
    el('rule-op').checked       = !!rule.blockedForOp;
    el('rule-users').value      = (rule.blockedUsernames || []).join(', ');
    hideErr(el('rule-error'));
    updateWarn();
    el('modal-rule').classList.remove('hidden');
    el('overlay').classList.remove('hidden');
}

function closeRuleModal() {
    el('modal-rule').classList.add('hidden');
    el('overlay').classList.add('hidden');
}

el('btn-rule-cancel').addEventListener('click', closeRuleModal);
el('overlay').addEventListener('click', () => {
    if (!el('modal-rule').classList.contains('hidden')) closeRuleModal();
});

el('rule-all').addEventListener('change', updateWarn);
function updateWarn() {
    el('rule-warn').classList.toggle('hidden', !el('rule-all').checked);
}

el('btn-rule-delete').addEventListener('click', () =>
    saveRule(getModalCmd(), { blockedForAll: false, blockedForOp: false, blockedUsernames: [] })
);

el('btn-rule-save').addEventListener('click', () => {
    const command = getModalCmd();
    if (!command) { showErr(el('rule-error'), 'Ingresa el nombre del comando.'); return; }
    saveRule(command, {
        blockedForAll: el('rule-all').checked,
        blockedForOp:  el('rule-op').checked,
        blockedUsernames: el('rule-users').value.split(',')
            .map(s => s.trim()).filter(s => s.length > 0)
    });
});

function getModalCmd() {
    return el('rule-command').value.trim().toLowerCase().replace(/^\//, '');
}

async function saveRule(command, rule) {
    const btn = el('btn-rule-save');
    btn.disabled = true;
    try {
        const res = await api('PUT', '/api/rules', { command, ...rule });
        if (res.ok) {
            // Update local state
            if (rule.blockedForAll || rule.blockedForOp || rule.blockedUsernames.length > 0) {
                rulesData[command] = rule;
            } else {
                delete rulesData[command];
            }
            closeRuleModal();
            renderTree();
            renderRules();
        } else if (res.status === 401) {
            onUnauthorized();
        } else {
            showErr(el('rule-error'), 'Error al guardar la regla.');
        }
    } catch {
        showErr(el('rule-error'), 'No se pudo conectar al servidor.');
    } finally {
        btn.disabled = false;
    }
}

// ── Utilities ──────────────────────────────────────────────
function el(id)    { return document.getElementById(id); }
function qsa(sel)  { return document.querySelectorAll(sel); }
function esc(str)  {
    return String(str)
        .replace(/&/g,  '&amp;')
        .replace(/</g,  '&lt;')
        .replace(/>/g,  '&gt;')
        .replace(/"/g,  '&quot;')
        .replace(/'/g,  '&#39;');
}
function showErr(el, msg) { el.textContent = msg; el.classList.remove('hidden'); }
function hideErr(el)      { el.classList.add('hidden'); }

// ── Start ──────────────────────────────────────────────────
init();
