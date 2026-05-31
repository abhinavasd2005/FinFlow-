const API_BASE = 'https://finflow-backendapp.onrender.com/api';

const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');

if (!token) {
    window.location.href = 'index.html';
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('nav-username').textContent = username || '';
    document.getElementById('greeting').textContent = username
        ? `Welcome back, ${username}`
        : 'Welcome back';
    loadWallets();
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

        if (res.status === 401) {
            logout();
            return;
        }

        const wallets = await res.json();
        document.getElementById('wallets-loading').style.display = 'none';
        renderStats(wallets);
        renderWallets(wallets);
    } catch (e) {
        document.getElementById('wallets-loading').style.display = 'none';
        showDashAlert('Failed to load wallets. Is the server running?', 'error');
    }
}

function renderStats(wallets) {
    const totalBalance = wallets.reduce((sum, w) => sum + Number(w.balance || 0), 0);
    const totalLimit = wallets.reduce((sum, w) => sum + Number(w.dailyLimit || 0), 0);

    document.getElementById('total-balance').textContent = fmt(totalBalance);
    document.getElementById('wallet-count').textContent = wallets.length;
    document.getElementById('total-limit').textContent = fmt(totalLimit);
}

function renderWallets(wallets) {
    const grid = document.getElementById('wallets-grid');
    grid.innerHTML = '';

    if (!wallets || wallets.length === 0) {
        grid.innerHTML = `
      <div class="empty-state" style="grid-column:1/-1">
        <div class="empty-icon">💳</div>
        <h3>No wallets yet</h3>
        <p>Create your first wallet to get started</p>
      </div>`;
        return;
    }

    wallets.forEach(w => {
        const card = document.createElement('div');
        card.className = 'wallet-card';
        card.innerHTML = `

    <div class="wallet-name">
        ${escHtml(w.walletName)}
    </div>

    <div class="wallet-balance">
        ${fmt(w.balance)}
    </div>

    <div class="wallet-number">
        ${escHtml(w.walletNumber)}
    </div>

    <div class="wallet-limit">
        Daily limit:
        ${fmt(w.dailyLimit)}
    </div>

    <div style="
        display:flex;
        gap:0.75rem;
        margin-top:1.25rem;
        flex-wrap:wrap;
    ">

        <a href="
            transfer.html?from=${w.id}
        "
        class="btn btn-primary btn-sm">

            Transfer

        </a>

        <a href="
            history.html?wallet=${w.id}
        "
        class="btn btn-secondary btn-sm">

            History

        </a>

    </div>
`;
        grid.appendChild(card);
    });
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

        const data = await res.json();

        if (!res.ok) {
            showCreateAlert(data.error || data.message || 'Failed to create wallet', 'error');
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