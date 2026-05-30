const API_BASE = 'http://localhost:8080/api';
const token = sessionStorage.getItem('token');

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
    }).format(amount || 0);
}

document.addEventListener('DOMContentLoaded', async () => {
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
    document.getElementById('slip-loading').style.display = 'none';
    document.getElementById('slip-card').style.display = 'block';

    const isSuccess = data.status === 'COMPLETED';

    const icon = document.getElementById('slip-icon');
    icon.textContent = isSuccess ? '✓' : '✗';
    icon.className = `slip-status-icon ${isSuccess ? 'success' : 'failed'}`;

    const amtEl = document.getElementById('slip-amount');
    amtEl.textContent = fmt(data.amount);
    amtEl.className = `slip-amount ${isSuccess ? 'success' : 'failed'}`;

    if (!isSuccess && data.failureReason) {
        document.getElementById('slip-failure-reason').textContent = data.failureReason;
    }

    document.getElementById('slip-status-badge').innerHTML =
        `<span class="badge badge-${isSuccess ? 'success' : 'danger'}">${data.status}</span>`;

    document.getElementById('slip-ref').textContent = data.referenceNumber || '—';

    document.getElementById('slip-date').textContent = data.createdAt
        ? new Date(data.createdAt).toLocaleString('en-US', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        })
        : '—';

    document.getElementById('slip-from-name').textContent = data.fromWalletName || '—';
    document.getElementById('slip-from-number').textContent = data.fromWalletNumber || '—';
    document.getElementById('slip-from-user').textContent = data.fromOwnerUsername ? `@${data.fromOwnerUsername}` : '';

    document.getElementById('slip-to-name').textContent = data.toWalletName || '—';
    document.getElementById('slip-to-number').textContent = data.toWalletNumber || '—';
    document.getElementById('slip-to-user').textContent = data.toOwnerUsername ? `@${data.toOwnerUsername}` : '';

    document.getElementById('slip-desc').textContent = data.description || 'Transfer';

    document.getElementById('slip-balance-after').textContent =
        data.balanceAfterTransfer != null ? fmt(data.balanceAfterTransfer) : '—';

    if (data.fraudScore && data.fraudScore > 0) {
        const row = document.getElementById('slip-fraud-row');
        row.style.display = 'flex';

        const score = document.getElementById('slip-fraud-score');
        score.innerHTML = `<span class="badge badge-${
            data.fraudScore >= 70 ? 'danger' : data.fraudScore >= 40 ? 'warning' : 'info'
        }">${data.fraudScore}/100</span>`;
    }
}