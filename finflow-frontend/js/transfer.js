const API_BASE = 'http://localhost:8080/api';
const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');

if (!token) window.location.href = 'index.html';

let wallets = [];
let resolvedToId = null;

document.addEventListener('DOMContentLoaded', () => {
    const navUser = document.getElementById('nav-username');
    if (navUser) navUser.textContent = username || '';

    loadWallets();

    const params = new URLSearchParams(window.location.search);
    const fromId = params.get('from');
    if (fromId) {
        setTimeout(() => {
            const sel = document.getElementById('from-wallet');
            if (sel) sel.value = fromId;
            updateFromBalance();
        }, 600);
    }

    document.getElementById('transfer-amount').addEventListener('input', updatePreview);
    document.getElementById('to-wallet-number').addEventListener('input', debounce(lookupWallet, 500));
    document.getElementById('from-wallet').addEventListener('change', updateFromBalance);
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

        wallets = await res.json();

        const sel = document.getElementById('from-wallet');
        sel.innerHTML = '<option value="">Select source wallet</option>';

        wallets.forEach(w => {
            const opt = document.createElement('option');
            opt.value = w.id;
            opt.textContent = `${w.walletName} — ${fmt(w.balance)}`;
            sel.appendChild(opt);
        });
    } catch (e) {
        showAlert('transfer-alert', 'Failed to load wallets', 'error');
    }
}

function updateFromBalance() {
    const id = document.getElementById('from-wallet').value;
    const w = wallets.find(x => x.id == id);
    document.getElementById('from-balance').textContent = w ? `Available: ${fmt(w.balance)}` : '';
    updatePreview();
}

async function lookupWallet() {
    const number = document.getElementById('to-wallet-number').value.trim();
    const status = document.getElementById('wallet-lookup-status');
    resolvedToId = null;
    updatePreview();

    if (!number || number.length < 5) {
        status.textContent = '';
        return;
    }

    status.style.color = 'var(--text-muted)';
    status.textContent = 'Looking up...';

    try {
        const res = await fetch(
            `${API_BASE}/wallets/lookup?walletNumber=${encodeURIComponent(number)}`,
            { headers: authHeaders() }
        );

        if (res.ok) {
            const data = await res.json();
            resolvedToId = data.id;
            status.style.color = 'var(--success)';
            status.textContent = `✓ ${data.walletName} (${data.ownerUsername})`;
            document.getElementById('slip-to').textContent = `${data.walletName} · ${data.ownerUsername}`;
        } else {
            status.style.color = 'var(--danger)';
            status.textContent = '✗ Wallet not found';
        }
    } catch (e) {
        status.style.color = 'var(--danger)';
        status.textContent = 'Lookup failed';
    }
}

function updatePreview() {
    const id = document.getElementById('from-wallet').value;
    const w = wallets.find(x => x.id == id);
    const amount = parseFloat(document.getElementById('transfer-amount').value) || 0;

    document.getElementById('slip-from').textContent = w ? w.walletName : '—';
    document.getElementById('slip-amount').textContent = amount > 0 ? fmt(amount) : '—';
}

async function submitTransfer() {
    const fromId = document.getElementById('from-wallet').value;
    const amount = parseFloat(document.getElementById('transfer-amount').value);
    const desc = document.getElementById('transfer-desc').value.trim();
    const number = document.getElementById('to-wallet-number').value.trim();

    if (!fromId) {
        showAlert('transfer-alert', 'Select a source wallet', 'error');
        return;
    }
    if (!number) {
        showAlert('transfer-alert', 'Enter destination wallet number', 'error');
        return;
    }
    if (!resolvedToId) {
        showAlert('transfer-alert', 'Destination wallet not found or not verified', 'error');
        return;
    }
    if (!amount || amount <= 0) {
        showAlert('transfer-alert', 'Enter a valid amount', 'error');
        return;
    }
    if (fromId == resolvedToId) {
        showAlert('transfer-alert', 'Cannot transfer to the same wallet', 'error');
        return;
    }

    const btn = document.getElementById('transfer-btn-text');
    btn.innerHTML = '<span class="spinner"></span>';

    const idempotencyKey = `${username}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

    try {
        const res = await fetch(`${API_BASE}/transfers`, {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({
                fromWalletId: parseInt(fromId),
                toWalletId: resolvedToId,
                amount: amount,
                idempotencyKey: idempotencyKey,
                description: desc || 'Transfer'
            })
        });

        const data = await res.json();

        if (!res.ok) {
            showAlert('transfer-alert', data.error || data.message || 'Transfer failed', 'error');
            return;
        }

        window.location.href = `slip.html?txn=${data.transactionId}`;
    } catch (e) {
        showAlert('transfer-alert', 'Connection error', 'error');
    } finally {
        btn.textContent = 'Send Money';
    }
}

function showAlert(id, msg, type) {
    const el = document.getElementById(id);
    el.className = `alert alert-${type} show`;
    el.textContent = msg;
    setTimeout(() => el.classList.remove('show'), 5000);
}

function debounce(fn, delay) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
}