const API_BASE = window.FINFLOW_API_BASE;
const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');
const role = sessionStorage.getItem('role');
let adminNoticeTimer;

if (!token) {
    window.location.href = '../index.html';
}

if (role !== 'ADMIN') {
    window.location.href = '../dashboard.html';
}

document.addEventListener('DOMContentLoaded', () => {
    const navUsername = document.getElementById('nav-username');
    if (navUsername) navUsername.textContent = username || '';

    if (document.getElementById('alerts-container')) {
        loadDashboard();
    }

    if (document.getElementById('fraud-body')) {
        loadFraudPage();
    }
});

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
}

function logout() {
    sessionStorage.clear();
    window.location.href = '../index.html';
}

async function loadDashboard() {
    try {
        const [alertsRes, queueRes] = await Promise.all([
            fetch(`${API_BASE}/fraud/alerts`, { headers: authHeaders() }),
            fetch(`${API_BASE}/fraud/queue/size`, { headers: authHeaders() })
        ]);

        if (handleAuthFailure(alertsRes) || handleAuthFailure(queueRes)) return;

        const [alerts, queue] = await Promise.all([
            readJson(alertsRes),
            readJson(queueRes)
        ]);

        if (!alertsRes.ok || !queueRes.ok || !Array.isArray(alerts)) {
            throw new Error(alerts.message || queue.message || 'Unable to load fraud data');
        }

        renderStats(alerts, queue);
        renderAlerts(alerts);
    } catch (e) {
        renderDashboardError(e.message || 'Unable to load fraud data.');
    }
}

function renderStats(alerts, queue) {
    setText('total-alerts', alerts.length);
    setText('pending-alerts', alerts.filter(alert => alert.alertStatus === 'PENDING').length);
    setText('high-risk', alerts.filter(alert => Number(alert.fraudScore) >= 70).length);
    setText('queue-size', queue.pendingInQueue || 0);
}

function renderAlerts(alerts) {
    const container = document.getElementById('alerts-container');
    if (!container) return;

    container.innerHTML = '';
    if (!alerts.length) {
        container.appendChild(emptyMessage('No alerts found'));
        return;
    }

    alerts.slice(0, 5).forEach(alert => {
        const card = document.createElement('div');
        card.className = 'fraud-alert-card';

        const header = document.createElement('div');
        header.className = 'fraud-alert-header';
        const reference = document.createElement('strong');
        reference.textContent = alert.transactionReference || 'Unknown transaction';
        header.append(reference, createBadge(
            String(alert.fraudScore || 0),
            scoreBadgeType(alert.fraudScore)
        ));

        const reasons = document.createElement('div');
        reasons.className = 'fraud-alert-reasons';
        reasons.textContent = alert.triggeredRules || 'No rules';

        const footer = document.createElement('div');
        footer.className = 'fraud-alert-footer';
        footer.append(
            createBadge(alert.alertStatus || 'PENDING', statusBadgeType(alert.alertStatus)),
            smallText(formatDate(alert.createdAt))
        );

        card.append(header, reasons, footer);
        container.appendChild(card);
    });
}

async function loadFraudPage() {
    const body = document.getElementById('fraud-body');
    try {
        const res = await fetch(`${API_BASE}/fraud/alerts`, {
            headers: authHeaders()
        });

        if (handleAuthFailure(res)) return;

        const alerts = await readJson(res);
        if (!res.ok || !Array.isArray(alerts)) {
            throw new Error(alerts.message || 'Unable to load fraud alerts');
        }

        renderFraudTable(alerts);
    } catch (e) {
        if (body) {
            body.innerHTML = '';
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 7;
            cell.textContent = e.message || 'Unable to load fraud alerts.';
            cell.className = 'table-message';
            row.appendChild(cell);
            body.appendChild(row);
        }
    }
}

function renderFraudTable(alerts) {
    const body = document.getElementById('fraud-body');
    if (!body) return;

    body.innerHTML = '';
    if (!alerts.length) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 7;
        cell.textContent = 'No alerts found';
        cell.className = 'table-message';
        row.appendChild(cell);
        body.appendChild(row);
        return;
    }

    alerts.forEach(alert => {
        const row = document.createElement('tr');
        appendCell(row, alert.id || '—');
        appendCell(row, alert.transactionReference || '—', 'reference-cell');

        const scoreCell = document.createElement('td');
        scoreCell.appendChild(createBadge(String(alert.fraudScore || 0), scoreBadgeType(alert.fraudScore)));
        row.appendChild(scoreCell);

        const statusCell = document.createElement('td');
        statusCell.appendChild(createBadge(alert.alertStatus || 'PENDING', statusBadgeType(alert.alertStatus)));
        row.appendChild(statusCell);

        appendCell(row, alert.triggeredRules || 'No rules', 'rules-cell');
        appendCell(row, formatDate(alert.createdAt));

        const actions = document.createElement('td');
        const actionGroup = document.createElement('div');
        actionGroup.className = 'fraud-actions';
        actionGroup.append(
            createActionButton('Review', 'eye', 'btn btn-secondary btn-sm', () => reviewAlert(alert.id)),
            createActionButton('Dismiss', 'circle-x', 'btn btn-danger btn-sm', () => dismissAlert(alert.id))
        );
        actions.appendChild(actionGroup);
        row.appendChild(actions);

        body.appendChild(row);
    });

    window.FinFlowUI?.refreshIcons();
}

async function reviewAlert(alertId) {
    await updateAlert(alertId, 'review', 'Alert marked reviewed');
}

async function dismissAlert(alertId) {
    await updateAlert(alertId, 'dismiss', 'Alert dismissed');
}

async function updateAlert(alertId, action, successMessage) {
    try {
        const res = await fetch(`${API_BASE}/fraud/alerts/${alertId}/${action}`, {
            method: 'PATCH',
            headers: authHeaders()
        });

        if (handleAuthFailure(res)) return;
        const data = await readJson(res);
        if (!res.ok) throw new Error(data.message || `Unable to ${action} alert`);

        showAdminNotice(data.message || successMessage, 'success');
        loadFraudPage();
    } catch (e) {
        showAdminNotice(e.message || 'Unable to update alert.', 'error');
    }
}

async function freezeWallet() {
    const walletId = document.getElementById('wallet-id').value.trim();
    const reason = document.getElementById('freeze-reason').value.trim();

    if (!walletId || !reason) {
        showAdminNotice('Enter a wallet ID and a reason.', 'error');
        return;
    }

    try {
        const res = await fetch(
            `${API_BASE}/fraud/freeze/${encodeURIComponent(walletId)}?reason=${encodeURIComponent(reason)}`,
            { method: 'POST', headers: authHeaders() }
        );

        if (handleAuthFailure(res)) return;
        const data = await readJson(res);
        if (!res.ok) throw new Error(data.message || 'Unable to freeze wallet');

        document.getElementById('freeze-reason').value = '';
        showAdminNotice(data.message || 'Wallet frozen', 'success');
    } catch (e) {
        showAdminNotice(e.message || 'Unable to freeze wallet.', 'error');
    }
}

async function unfreezeWallet() {
    const walletId = document.getElementById('wallet-id').value.trim();
    if (!walletId) {
        showAdminNotice('Enter a wallet ID.', 'error');
        return;
    }

    try {
        const res = await fetch(
            `${API_BASE}/fraud/unfreeze/${encodeURIComponent(walletId)}`,
            { method: 'POST', headers: authHeaders() }
        );

        if (handleAuthFailure(res)) return;
        const data = await readJson(res);
        if (!res.ok) throw new Error(data.message || 'Unable to unfreeze wallet');

        document.getElementById('freeze-reason').value = '';
        showAdminNotice(data.message || 'Wallet unfrozen', 'success');
    } catch (e) {
        showAdminNotice(e.message || 'Unable to unfreeze wallet.', 'error');
    }
}

function handleAuthFailure(res) {
    if (res.status === 401) {
        logout();
        return true;
    }

    if (res.status === 403) {
        window.location.href = '../dashboard.html';
        return true;
    }

    return false;
}

function renderDashboardError(message) {
    const container = document.getElementById('alerts-container');
    if (!container) return;
    container.innerHTML = '';
    container.appendChild(emptyMessage(message));
}

function emptyMessage(message) {
    const element = document.createElement('div');
    element.className = 'table-message';
    element.textContent = message;
    return element;
}

function appendCell(row, value, className) {
    const cell = document.createElement('td');
    cell.textContent = value;
    if (className) cell.className = className;
    row.appendChild(cell);
}

function createBadge(label, type) {
    const badge = document.createElement('span');
    badge.className = `badge badge-${type}`;
    badge.textContent = label;
    return badge;
}

function createActionButton(label, iconName, className, handler) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = className;
    button.innerHTML = `<i data-lucide="${iconName}"></i><span>${label}</span>`;
    button.addEventListener('click', handler);
    return button;
}

function smallText(value) {
    const element = document.createElement('small');
    element.textContent = value;
    return element;
}

function scoreBadgeType(score) {
    const value = Number(score || 0);
    return value >= 70 ? 'danger' : value >= 40 ? 'warning' : 'info';
}

function statusBadgeType(status) {
    return status === 'PENDING' ? 'warning' : status === 'REVIEWED' ? 'success' : 'info';
}

function formatDate(date) {
    const parsed = new Date(date);
    if (Number.isNaN(parsed.getTime())) return '—';

    return parsed.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value;
}

function showAdminNotice(message, type = 'success') {
    const notice = document.getElementById('admin-notice');
    if (!notice) return;

    window.clearTimeout(adminNoticeTimer);
    notice.className = `admin-notice ${type} show`;
    notice.replaceChildren();

    const icon = document.createElement('i');
    icon.setAttribute('data-lucide', type === 'error' ? 'circle-alert' : 'circle-check');
    notice.append(icon, document.createTextNode(message));
    window.FinFlowUI?.refreshIcons();

    adminNoticeTimer = window.setTimeout(() => notice.classList.remove('show'), 5000);
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
