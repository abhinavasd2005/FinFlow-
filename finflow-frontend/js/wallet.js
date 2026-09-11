const API_BASE = window.FINFLOW_API_BASE;

const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');
let wallets = [];
let aiSummaryEnabled = false;
let chartResizeTimer;

if (!token) {
    window.location.href = 'index.html';
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('nav-username').textContent = username || '';
    document.getElementById('greeting').textContent = username
        ? `Good to see you, ${username}`
        : 'Your money overview';
    loadWallets();
    setDefaultSummaryDates();
    checkAiStatus();
    window.addEventListener('resize', () => {
        window.clearTimeout(chartResizeTimer);
        chartResizeTimer = window.setTimeout(() => renderBalanceChart(wallets), 120);
    });
});

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
}

function logout() {
    sessionStorage.clear();
    window.location.href = 'index.html';
}

function fmt(amount) {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD'
    }).format(Number(amount || 0));
}

async function loadWallets() {
    try {
        const res = await fetch(`${API_BASE}/wallets/my-wallets`, {
            headers: authHeaders()
        });

        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }

        if (!res.ok) {
            throw new Error('Failed to load wallets');
        }

        wallets = await res.json();
        document.getElementById('wallets-loading').style.display = 'none';
        renderStats(wallets);
        renderWallets(wallets);
        populateSummaryWallets(wallets);
    } catch (e) {
        document.getElementById('wallets-loading').style.display = 'none';
        showDashAlert('Failed to load wallets. Is the server running?', 'error');
    }
}

function renderStats(wallets) {
    const totalBalance = wallets.reduce((sum, w) => sum + Number(w.balance || 0), 0);
    const totalLimit = wallets.reduce((sum, w) => sum + Number(w.dailyLimit || 0), 0);
    const activeWallets = wallets.filter(w => (w.status || 'ACTIVE') === 'ACTIVE').length;

    animateNumber('total-balance', totalBalance, fmt);
    animateNumber('wallet-count', wallets.length, value => String(Math.round(value)));
    animateNumber('total-limit', totalLimit, fmt);
    animateNumber('wallet-status', activeWallets, value => String(Math.round(value)));
    renderBalanceChart(wallets);
}

function animateNumber(id, target, formatter) {
    const element = document.getElementById(id);
    if (!element) return;

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion || !Number.isFinite(target)) {
        element.textContent = formatter(target);
        return;
    }

    const start = Number(element.dataset.value || 0);
    const startedAt = performance.now();
    const duration = 420;
    const update = now => {
        const progress = Math.min((now - startedAt) / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = start + (target - start) * eased;
        element.textContent = formatter(current);
        if (progress < 1) window.requestAnimationFrame(update);
        else element.dataset.value = String(target);
    };

    window.requestAnimationFrame(update);
}

function renderWallets(wallets) {
    const grid = document.getElementById('wallets-grid');
    grid.innerHTML = '';

    if (!wallets || wallets.length === 0) {
        grid.innerHTML = `
      <div class="empty-state" style="grid-column:1/-1">
        <div class="empty-icon"><i data-lucide="wallet"></i></div>
        <h3>No wallets yet</h3>
        <p>Create your first wallet to start moving money.</p>
      </div>`;
        window.FinFlowUI?.refreshIcons();
        return;
    }

    wallets.forEach(w => {
        const card = document.createElement('div');
        card.className = 'wallet-card';
        const status = String(w.status || 'ACTIVE').toUpperCase();
        card.dataset.status = status;
        card.innerHTML = `
            <div class="wallet-card-top">
                <div>
                    <div class="wallet-card-label">Wallet</div>
                    <div class="wallet-name">${escHtml(w.walletName)}</div>
                </div>
                <span class="badge wallet-status-${status.toLowerCase()}">${escHtml(status)}</span>
            </div>
            <div class="wallet-balance">${fmt(w.balance)}</div>
            <div class="wallet-card-meta">
                <span class="wallet-number">${escHtml(w.walletNumber)}</span>
                <span class="wallet-limit">Daily limit ${fmt(w.dailyLimit)}</span>
            </div>
            ${status === 'FROZEN' && w.freezeReason ? `<p class="wallet-freeze-note">${escHtml(w.freezeReason)}</p>` : ''}
            <div class="wallet-card-actions">
                <a href="transfer.html?from=${encodeURIComponent(w.id)}" class="btn btn-primary btn-sm"><i data-lucide="send"></i><span>Transfer</span></a>
                <a href="history.html?wallet=${encodeURIComponent(w.id)}" class="btn btn-secondary btn-sm"><i data-lucide="receipt-text"></i><span>Activity</span></a>
            </div>`;
        grid.appendChild(card);
    });
    window.FinFlowUI?.refreshIcons();
}

function renderBalanceChart(items) {
    const canvas = document.getElementById('balance-chart');
    if (!canvas || !canvas.getContext) return;

    const rect = canvas.getBoundingClientRect();
    const width = Math.max(1, rect.width);
    const height = Math.max(1, rect.height);
    const scale = Math.min(window.devicePixelRatio || 1, 2);
    const context = canvas.getContext('2d');

    canvas.width = Math.round(width * scale);
    canvas.height = Math.round(height * scale);
    context.setTransform(scale, 0, 0, scale, 0, 0);
    context.clearRect(0, 0, width, height);

    const balances = items.length
        ? items.map(wallet => Math.max(Number(wallet.balance) || 0, 0))
        : [0, 0];
    const values = balances.length === 1 ? [balances[0], balances[0]] : balances;
    const padding = { top: 48, right: 18, bottom: 26, left: 18 };
    const chartWidth = Math.max(1, width - padding.left - padding.right);
    const chartHeight = Math.max(1, height - padding.top - padding.bottom);
    const maxValue = Math.max(...values, 1);

    context.setLineDash([4, 6]);
    context.lineWidth = 1;
    context.strokeStyle = 'rgba(216, 238, 228, 0.18)';
    for (let index = 0; index < 3; index += 1) {
        const y = padding.top + (chartHeight / 2) * index;
        context.beginPath();
        context.moveTo(padding.left, y);
        context.lineTo(width - padding.right, y);
        context.stroke();
    }

    context.setLineDash([]);
    context.beginPath();
    values.forEach((value, index) => {
        const x = padding.left + (chartWidth / (values.length - 1)) * index;
        const y = padding.top + chartHeight - (value / maxValue) * chartHeight;
        if (index === 0) context.moveTo(x, y);
        else context.lineTo(x, y);
    });
    context.lineWidth = 2.25;
    context.strokeStyle = '#7ee0c9';
    context.stroke();

    values.forEach((value, index) => {
        const x = padding.left + (chartWidth / (values.length - 1)) * index;
        const y = padding.top + chartHeight - (value / maxValue) * chartHeight;
        context.beginPath();
        context.arc(x, y, 4, 0, Math.PI * 2);
        context.fillStyle = '#ffffff';
        context.fill();
        context.lineWidth = 2;
        context.strokeStyle = '#7ee0c9';
        context.stroke();
    });
}

function populateSummaryWallets(items) {
    const select = document.getElementById('ai-summary-wallet');
    if (!select) return;

    select.innerHTML = items.length
        ? items.map(w => `<option value="${w.id}">${escHtml(w.walletName)} · ${escHtml(w.walletNumber)}</option>`).join('')
        : '<option value="">No wallets available</option>';
}

function setDefaultSummaryDates() {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 30);
    document.getElementById('ai-summary-from').value = toDateTimeInput(from);
    document.getElementById('ai-summary-to').value = toDateTimeInput(to);
}

function toDateTimeInput(date) {
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}

async function checkAiStatus() {
    const status = document.getElementById('ai-summary-status');
    const button = document.getElementById('ai-summary-btn');
    if (!status || !button) return;

    try {
        const res = await fetch(`${API_BASE}/ai/status`, { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }
        if (!res.ok) throw new Error('AI status unavailable');
        const data = await res.json();
        aiSummaryEnabled = Boolean(data.enabled);
        status.textContent = aiSummaryEnabled ? 'Ready' : 'Not configured';
        status.className = `feature-status ${aiSummaryEnabled ? 'feature-status-ready' : 'feature-status-error'}`;
        button.disabled = !aiSummaryEnabled;
    } catch (e) {
        status.textContent = 'Unavailable';
        status.className = 'feature-status feature-status-error';
    }
}

async function generateFinancialSummary() {
    const output = document.getElementById('ai-summary-output');
    const button = document.getElementById('ai-summary-btn');
    const walletId = document.getElementById('ai-summary-wallet').value;
    const from = document.getElementById('ai-summary-from').value;
    const to = document.getElementById('ai-summary-to').value;

    if (!walletId || !from || !to) {
        output.className = 'ai-output ai-error';
        output.textContent = 'Choose a wallet and date range first.';
        return;
    }

    button.disabled = true;
    output.className = 'ai-output ai-loading';
    output.textContent = 'Generating summary...';

    try {
        const res = await fetch(`${API_BASE}/ai/financial-summary`, {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ walletId: Number(walletId), from, to })
        });
        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || data.error || 'Unable to generate summary');

        output.className = 'ai-output ai-success';
        output.innerHTML = `
            <p class="ai-summary-copy">${escHtml(data.summary)}</p>
            <div class="ai-columns">
                <div><strong>Highlights</strong>${renderInsightList(data.highlights)}</div>
                <div><strong>Recommendations</strong>${renderInsightList(data.recommendations)}</div>
            </div>`;
    } catch (e) {
        output.className = 'ai-output ai-error';
        output.textContent = e.message;
    } finally {
        button.disabled = !aiSummaryEnabled;
    }
}

function renderInsightList(items) {
    if (!items || !items.length) return '<p class="ai-muted">None available.</p>';
    return `<ul class="ai-list">${items.map(item => `<li>${escHtml(item)}</li>`).join('')}</ul>`;
}

async function createWallet() {
    const name = document.getElementById('wallet-name').value.trim();
    const balance = document.getElementById('wallet-balance').value;
    const limit = document.getElementById('wallet-limit').value;

    if (!name) {
        showCreateAlert('Wallet name is required', 'error');
        return;
    }

    const btn = document.getElementById('create-wallet-btn-text');
    btn.innerHTML = '<span class="spinner"></span>';

    try {
        const res = await fetch(`${API_BASE}/wallets`, {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({
                walletName: name,
                initialBalance: balance ? parseFloat(balance) : 0,
                dailyLimit: limit ? parseFloat(limit) : 100000
            })
        });
        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }

        const data = await res.json();

        if (!res.ok) {
            showCreateAlert(data.message || data.error || 'Failed to create wallet', 'error');
            return;
        }

        document.getElementById('create-wallet-modal').classList.remove('show');
        document.getElementById('wallet-name').value = '';
        document.getElementById('wallet-balance').value = '';
        document.getElementById('wallet-limit').value = '';
        showDashAlert('Wallet created successfully!', 'success');
        loadWallets();
    } catch (e) {
        showCreateAlert('Connection error', 'error');
    } finally {
        btn.textContent = 'Create Wallet';
    }
}

function showDashAlert(msg, type) {
    const el = document.getElementById('alert-dashboard');
    el.className = `alert alert-${type} show`;
    el.textContent = msg;
    setTimeout(() => el.classList.remove('show'), 4000);
}

function showCreateAlert(msg, type) {
    const el = document.getElementById('create-wallet-alert');
    el.className = `alert alert-${type} show`;
    el.textContent = msg;
    setTimeout(() => el.classList.remove('show'), 4000);
}

function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
