const API_BASE = window.FINFLOW_API_BASE;
const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');

if (!token) {
    window.location.href = 'index.html';
}

let wallets = [];

document.addEventListener('DOMContentLoaded', async () => {
    document.getElementById('nav-username').textContent = username || '';

    if (!await loadWallets()) {
        return;
    }

    const walletParam = new URLSearchParams(window.location.search).get('wallet');
    const walletSelect = document.getElementById('wallet-select');
    if (walletParam && wallets.some(wallet => String(wallet.id) === walletParam)) {
        walletSelect.value = walletParam;
    }

    setDefaultDates();
    loadHistory();
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
            return false;
        }

        const data = await readJson(res);
        if (!res.ok || !Array.isArray(data)) {
            throw new Error(data.message || 'Unable to load wallets');
        }

        wallets = data;
        const select = document.getElementById('wallet-select');
        select.innerHTML = '';

        wallets.forEach(wallet => {
            const option = document.createElement('option');
            option.value = wallet.id;
            option.textContent = `${wallet.walletName} (${wallet.walletNumber})`;
            select.appendChild(option);
        });

        if (!wallets.length) {
            showHistoryMessage('Create a wallet before viewing transaction history.');
            return false;
        }

        return true;
    } catch (e) {
        showHistoryMessage(e.message || 'Failed to load wallets. Is the server running?');
        return false;
    }
}

function setDefaultDates() {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 30);

    document.getElementById('from-date').value = toDateTimeInput(from);
    document.getElementById('to-date').value = toDateTimeInput(to);
}

function toDateTimeInput(date) {
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}

async function loadHistory() {
    const filters = getFilters();
    if (!filters) return;

    setLoading(true);
    try {
        const res = await fetch(
            `${API_BASE}/transactions/wallet/${filters.walletId}/filter?from=${encodeURIComponent(filters.from)}&to=${encodeURIComponent(filters.to)}`,
            { headers: authHeaders() }
        );

        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }

        const transactions = await readJson(res);
        if (!res.ok || !Array.isArray(transactions)) {
            throw new Error(transactions.message || 'Unable to load transactions');
        }

        renderTransactions(transactions);
    } catch (e) {
        showHistoryMessage(e.message || 'Failed to load transaction history.');
    }
}

function getFilters() {
    const walletId = document.getElementById('wallet-select').value;
    const from = document.getElementById('from-date').value;
    const to = document.getElementById('to-date').value;

    if (!walletId || !from || !to) {
        showHistoryMessage('Choose a wallet and date range first.');
        return null;
    }

    if (new Date(to) < new Date(from)) {
        showHistoryMessage('The end date must be after the start date.');
        return null;
    }

    return {
        walletId,
        from: `${from}:00`,
        to: `${to}:00`
    };
}

function renderTransactions(transactions) {
    setLoading(false);
    document.getElementById('history-table').hidden = false;
    document.getElementById('txn-count').textContent = `${transactions.length} transactions`;

    const body = document.getElementById('history-body');
    body.innerHTML = '';

    if (!transactions.length) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 7;
        cell.textContent = 'No transactions found for this date range.';
        cell.style.textAlign = 'center';
        cell.style.padding = '2rem';
        cell.style.color = 'var(--text-muted)';
        row.appendChild(cell);
        body.appendChild(row);
        return;
    }

    transactions.forEach(transaction => {
        const row = document.createElement('tr');
        const isDebit = transaction.type === 'DEBIT';

        appendCell(row, formatDate(transaction.createdAt));
        appendCell(row, transaction.referenceNumber || '—', 'history-reference');
        appendCell(row, transaction.type || '—');

        const amountCell = appendCell(
            row,
            `${isDebit ? '-' : '+'}${fmt(transaction.amount)}`,
            `txn-amount ${isDebit ? 'debit' : 'credit'}`
        );
        amountCell.style.whiteSpace = 'nowrap';

        const statusCell = document.createElement('td');
        statusCell.appendChild(createBadge(
            transaction.status || 'UNKNOWN',
            transaction.status === 'COMPLETED' ? 'success' : 'danger'
        ));
        row.appendChild(statusCell);

        const fraudCell = document.createElement('td');
        const fraudScore = Number(transaction.fraudScore || 0);
        fraudCell.appendChild(createBadge(
            String(fraudScore),
            fraudScore > 70 ? 'danger' : fraudScore > 0 ? 'warning' : 'success'
        ));
        row.appendChild(fraudCell);

        const actionCell = document.createElement('td');
        const link = document.createElement('a');
        link.className = 'btn btn-secondary btn-sm';
        link.innerHTML = '<i data-lucide="arrow-up-right"></i><span>View</span>';
        link.href = `slip.html?txn=${encodeURIComponent(transaction.transactionId)}`;
        actionCell.appendChild(link);
        row.appendChild(actionCell);

        body.appendChild(row);
    });

    window.FinFlowUI?.refreshIcons();
}

async function loadStatement() {
    const filters = getFilters();
    if (!filters) return;

    try {
        const res = await fetch(
            `${API_BASE}/transactions/wallet/${filters.walletId}/statement?from=${encodeURIComponent(filters.from)}&to=${encodeURIComponent(filters.to)}`,
            { headers: authHeaders() }
        );

        if (res.status === 401 || res.status === 403) {
            logout();
            return;
        }

        const data = await readJson(res);
        if (!res.ok) {
            throw new Error(data.message || 'Unable to load statement');
        }

        renderStatement(data);
    } catch (e) {
        showHistoryMessage(e.message || 'Failed to load statement.');
    }
}

function renderStatement(data) {
    const box = document.getElementById('statement-box');
    box.hidden = false;
    box.innerHTML = '';

    const heading = document.createElement('h3');
    heading.textContent = 'Statement Summary';
    box.appendChild(heading);

    appendStatementRow(box, 'Wallet', data.walletName || '—');
    appendStatementRow(box, 'Wallet Number', data.walletNumber || '—');
    appendStatementRow(box, 'Current Balance', fmt(data.currentBalance));
    appendStatementRow(box, 'Total Credited', fmt(data.totalCredited), 'var(--success)');
    appendStatementRow(box, 'Total Debited', fmt(data.totalDebited), 'var(--danger)');
    appendStatementRow(box, 'Total Transactions', String(data.totalTransactions || 0));
}

function appendStatementRow(container, label, value, color) {
    const row = document.createElement('div');
    row.className = 'statement-row';

    const labelElement = document.createElement('span');
    labelElement.textContent = label;
    const valueElement = document.createElement('strong');
    valueElement.textContent = value;
    if (color) valueElement.style.color = color;

    row.append(labelElement, valueElement);
    container.appendChild(row);
}

function appendCell(row, value, className) {
    const cell = document.createElement('td');
    cell.textContent = value;
    if (className) cell.className = className;
    row.appendChild(cell);
    return cell;
}

function createBadge(label, type) {
    const badge = document.createElement('span');
    badge.className = `badge badge-${type}`;
    badge.textContent = label;
    return badge;
}

function setLoading(isLoading) {
    const loading = document.getElementById('history-loading');
    loading.hidden = !isLoading;
    if (isLoading) loading.innerHTML = '<span class="spinner"></span>';
}

function showHistoryMessage(message) {
    const loading = document.getElementById('history-loading');
    loading.hidden = false;
    loading.textContent = message;
    document.getElementById('history-table').hidden = true;
    document.getElementById('txn-count').textContent = '';
}

function formatDate(date) {
    const parsed = new Date(date);
    if (Number.isNaN(parsed.getTime())) return '—';

    return parsed.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
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
