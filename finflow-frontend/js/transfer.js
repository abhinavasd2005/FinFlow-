const API_BASE = window.FINFLOW_API_BASE;
const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');
let aiDraftEnabled = false;

if (!token) window.location.href = 'index.html';

let wallets = [];
let resolvedToId = null;
let walletLookupVersion = 0;
const debouncedLookupWallet = debounce(lookupWallet, 500);

document.addEventListener('DOMContentLoaded', () => {
    const navUser = document.getElementById('nav-username');
    if (navUser) navUser.textContent = username || '';

    const params = new URLSearchParams(window.location.search);
    loadWallets(params.get('from'));
    checkAiStatus();

    document.getElementById('transfer-amount').addEventListener('input', updatePreview);
    document.getElementById('to-wallet-number').addEventListener('input', () => {
        invalidateWalletLookup();
        debouncedLookupWallet();
    });
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

async function loadWallets(preselectedWalletId) {
    try {
        const res = await fetch(`${API_BASE}/wallets/my-wallets`, {
            headers: authHeaders()
        });

        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }

        if (!res.ok) {
            throw new Error('Unable to load wallets');
        }

        wallets = await res.json();

        const sel = document.getElementById('from-wallet');
        sel.innerHTML = '<option value="">Select source wallet</option>';

        wallets.forEach(w => {
            const opt = document.createElement('option');
            opt.value = w.id;
            const status = w.status || 'ACTIVE';
            opt.disabled = status !== 'ACTIVE';
            opt.textContent = status === 'ACTIVE'
                ? `${w.walletName} - ${fmt(w.balance)}`
                : `${w.walletName} - ${status.toLowerCase()}`;
            sel.appendChild(opt);
        });

        if (preselectedWalletId && wallets.some(w => String(w.id) === String(preselectedWalletId))) {
            sel.value = preselectedWalletId;
            updateFromBalance();
        }
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
    const input = document.getElementById('to-wallet-number');
    const number = input.value.trim();
    const status = document.getElementById('wallet-lookup-status');
    const requestVersion = walletLookupVersion;

    if (!number || number.length < 5) {
        status.className = 'lookup-status';
        status.textContent = '';
        return;
    }

    status.className = 'lookup-status';
    status.textContent = 'Looking up...';

    try {
        const res = await fetch(
            `${API_BASE}/wallets/lookup?walletNumber=${encodeURIComponent(number)}`,
            { headers: authHeaders() }
        );

        if (requestVersion !== walletLookupVersion || number !== input.value.trim()) {
            return;
        }

        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }

        if (res.ok) {
            const data = await readJson(res);
            resolvedToId = data.id;
            status.className = 'lookup-status is-success';
            status.textContent = `Wallet found: ${data.walletName} (${data.ownerUsername})`;
            document.getElementById('slip-to').textContent = `${data.walletName} (${data.ownerUsername})`;
        } else {
            status.className = 'lookup-status is-error';
            status.textContent = 'Wallet not found';
        }
    } catch (e) {
        status.className = 'lookup-status is-error';
        status.textContent = 'Lookup failed';
    }
}

function updatePreview() {
    const id = document.getElementById('from-wallet').value;
    const w = wallets.find(x => x.id == id);
    const amount = parseFloat(document.getElementById('transfer-amount').value) || 0;

    document.getElementById('slip-from').textContent = w ? w.walletName : 'Not selected';
    document.getElementById('slip-amount').textContent = amount > 0 ? fmt(amount) : 'Not set';
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
    if (String(fromId) === String(resolvedToId)) {
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

        const data = await readJson(res);

        if (!res.ok) {
            showAlert('transfer-alert', data.message || data.error || 'Transfer failed', 'error');
            return;
        }

        window.location.href = `slip.html?txn=${data.transactionId}`;
    } catch (e) {
        showAlert('transfer-alert', 'Connection error', 'error');
    } finally {
        btn.textContent = 'Send money';
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

async function checkAiStatus() {
    const status = document.getElementById('ai-draft-status');
    const button = document.getElementById('ai-draft-btn');
    if (!status || !button) return;

    try {
        const res = await fetch(`${API_BASE}/ai/status`, { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }
        if (!res.ok) throw new Error('AI status unavailable');
        const data = await readJson(res);
        aiDraftEnabled = Boolean(data.enabled);
        status.textContent = aiDraftEnabled ? 'Ready' : 'Not configured';
        status.className = `feature-status ${aiDraftEnabled ? 'feature-status-ready' : 'feature-status-error'}`;
        button.disabled = !aiDraftEnabled;
    } catch (e) {
        aiDraftEnabled = false;
        status.textContent = 'Unavailable';
        status.className = 'feature-status feature-status-error';
        button.disabled = true;
    }
}

function invalidateWalletLookup() {
    walletLookupVersion += 1;
    resolvedToId = null;
    const status = document.getElementById('wallet-lookup-status');
    status.className = 'lookup-status';
    status.textContent = '';
    document.getElementById('slip-to').textContent = 'Not selected';
    updatePreview();
}

async function createAiDraft() {
    const instruction = document.getElementById('ai-transfer-instruction').value.trim();
    const output = document.getElementById('ai-draft-output');
    const button = document.getElementById('ai-draft-btn');

    if (!instruction) {
        output.className = 'ai-output ai-error';
        output.textContent = 'Describe the transfer first.';
        return;
    }

    button.disabled = true;
    output.className = 'ai-output ai-loading';
    output.textContent = 'Preparing a draft...';

    try {
        const res = await fetch(`${API_BASE}/ai/transfer-draft`, {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ instruction })
        });
        const data = await readJson(res);
        if (!res.ok) throw new Error(data.message || data.error || 'Unable to create draft');

        if (data.needsClarification) {
            output.className = 'ai-output ai-error';
            output.textContent = data.message || 'Please clarify the transfer details.';
            return;
        }

        document.getElementById('from-wallet').value = data.fromWalletId || '';
        document.getElementById('to-wallet-number').value = data.destinationWalletNumber || '';
        document.getElementById('transfer-amount').value = data.amount || '';
        document.getElementById('transfer-desc').value = data.description || '';
        updateFromBalance();
        invalidateWalletLookup();
        await lookupWallet();
        output.className = 'ai-output ai-success';
        output.textContent = `${data.message || 'Draft ready.'} Review the form below before sending.`;
    } catch (e) {
        output.className = 'ai-output ai-error';
        output.textContent = e.message;
    } finally {
        button.disabled = !aiDraftEnabled;
    }
}

async function readJson(res) {
    const text = await res.text();
    if (!text) return {};

    try {
        return JSON.parse(text);
    } catch (e) {
        return {};
    }
}
