const API_BASE = window.FINFLOW_API_BASE;
const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');

if (!token) {
    window.location.href = 'index.html';
}

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
}

function fmt(amount) {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD'
    }).format(Number(amount || 0));
}

function logout() {
    sessionStorage.clear();
    window.location.href = 'index.html';
}

document.addEventListener('DOMContentLoaded', async () => {
    const navUsername = document.getElementById('nav-username');
    if (navUsername) navUsername.textContent = username || '';

    const params = new URLSearchParams(window.location.search);
    const txnId = params.get('txn');

    if (!txnId) {
        window.location.href = 'dashboard.html';
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/transactions/${txnId}/slip`, {
            headers: authHeaders()
        });

        if (res.status === 401) {
            sessionStorage.clear();
            window.location.href = 'index.html';
            return;
        }

        if (res.status === 403) {
            document.getElementById('slip-loading').textContent = 'You do not have access to this receipt';
            return;
        }

        if (!res.ok) {
            document.getElementById('slip-loading').textContent = 'Receipt not found';
            return;
        }

        const data = await res.json();
        renderSlip(data);
    } catch (e) {
        document.getElementById('slip-loading').textContent = 'Failed to load receipt';
    }
});

function renderSlip(data) {
    document.getElementById('slip-loading').hidden = true;
    document.getElementById('slip-card').hidden = false;

    const isSuccess = data.status === 'COMPLETED';

    const icon = document.getElementById('slip-icon');
    icon.innerHTML = `<i data-lucide="${isSuccess ? 'check' : 'x'}"></i>`;
    icon.className = `slip-status-icon ${isSuccess ? 'success' : 'failed'}`;

    const amtEl = document.getElementById('slip-amount');
    amtEl.textContent = fmt(data.amount);
    amtEl.className = `slip-amount ${isSuccess ? 'success' : 'failed'}`;

    const failureReason = document.getElementById('slip-failure-reason');
    failureReason.textContent = !isSuccess && data.failureReason ? data.failureReason : '';
    failureReason.hidden = !failureReason.textContent;

    replaceWithBadge('slip-status-badge', data.status || 'UNKNOWN', isSuccess ? 'success' : 'danger');

    document.getElementById('slip-ref').textContent = data.referenceNumber || 'Not available';

    document.getElementById('slip-date').textContent = data.createdAt
        ? new Date(data.createdAt).toLocaleString('en-US', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        })
        : 'Not available';

    document.getElementById('slip-from-name').textContent = data.fromWalletName || 'Not available';
    document.getElementById('slip-from-number').textContent = data.fromWalletNumber || 'Not available';
    document.getElementById('slip-from-user').textContent = data.fromOwnerUsername ? `@${data.fromOwnerUsername}` : '';

    document.getElementById('slip-to-name').textContent = data.toWalletName || 'Not available';
    document.getElementById('slip-to-number').textContent = data.toWalletNumber || 'Not available';
    document.getElementById('slip-to-user').textContent = data.toOwnerUsername ? `@${data.toOwnerUsername}` : '';

    document.getElementById('slip-desc').textContent = data.description || 'Transfer';

    document.getElementById('slip-balance-after').textContent =
        data.balanceAfterTransfer != null ? fmt(data.balanceAfterTransfer) : 'Not available';

    if (data.fraudScore && data.fraudScore > 0) {
        const row = document.getElementById('slip-fraud-row');
        row.hidden = false;
        replaceWithBadge(
            'slip-fraud-score',
            `${data.fraudScore}/100`,
            data.fraudScore >= 70 ? 'danger' : data.fraudScore >= 40 ? 'warning' : 'info'
        );
    }

    window.FinFlowUI?.refreshIcons();
}

function replaceWithBadge(id, label, type) {
    const container = document.getElementById(id);
    const badge = document.createElement('span');
    badge.className = `badge badge-${type}`;
    badge.textContent = label;
    container.replaceChildren(badge);
}
