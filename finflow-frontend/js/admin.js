const API_BASE =
    'http://localhost:8080/api';

const token =
    sessionStorage.getItem('token');

const username =
    sessionStorage.getItem('username');

const role =
    sessionStorage.getItem('role');

if (!token) {
    window.location.href =
        '../index.html';
}

if (role !== 'ADMIN') {
    window.location.href =
        '../dashboard.html';
}

document.addEventListener(
    'DOMContentLoaded',
    () => {

        document.getElementById(
            'nav-username'
        ).textContent =
            username || '';

        loadDashboard();
        if (
            window.location.pathname.includes(
                'admin-fraud.html'
            )
        ) {
            loadFraudPage();
        }
    }
);

function authHeaders() {

    return {
        'Content-Type':
            'application/json',

        'Authorization':
            `Bearer ${token}`
    };
}

function logout() {

    sessionStorage.clear();

    window.location.href =
        '../index.html';
}

async function loadDashboard() {

    try {

        const [
            alertsRes,
            queueRes
        ] = await Promise.all([

            fetch(
                `${API_BASE}/fraud/alerts`,
                {
                    headers:
                        authHeaders()
                }
            ),

            fetch(
                `${API_BASE}/fraud/queue/size`,
                {
                    headers:
                        authHeaders()
                }
            )
        ]);

        const alerts =
            await alertsRes.json();

        const queue =
            await queueRes.json();

        renderStats(alerts, queue);

        renderAlerts(alerts);

    } catch (e) {

        console.error(e);
    }
}

function renderStats(alerts, queue) {

    document.getElementById(
        'total-alerts'
    ).textContent =
        alerts.length;

    document.getElementById(
        'pending-alerts'
    ).textContent =
        alerts.filter(
            a => a.alertStatus === 'PENDING'
        ).length;

    document.getElementById(
        'high-risk'
    ).textContent =
        alerts.filter(
            a => a.fraudScore >= 70
        ).length;

    document.getElementById(
        'queue-size'
    ).textContent =
        queue.pendingInQueue || 0;
}

function renderAlerts(alerts) {

    const container =
        document.getElementById(
            'alerts-container'
        );

    if (!alerts.length) {

        container.innerHTML = `

            <div style="
                text-align:center;
                color:var(--text-muted);
                padding:2rem;
            ">

                No alerts found

            </div>
        `;

        return;
    }

    const recent =
        alerts.slice(0, 5);

    container.innerHTML =
        recent.map(alert => `

            <div style="
                border:1px solid var(--border);
                border-radius:var(--radius-sm);
                padding:1rem;
                margin-bottom:1rem;
                background:var(--bg-input);
            ">

                <div style="
                    display:flex;
                    justify-content:space-between;
                    margin-bottom:0.75rem;
                ">

                    <strong>
                        ${alert.transactionReference}
                    </strong>

                    <span class="
                        badge badge-${
            alert.fraudScore >= 70
                ? 'danger'
                : alert.fraudScore >= 40
                    ? 'warning'
                    : 'info'
        }
                    ">

                        ${alert.fraudScore}

                    </span>

                </div>

                <div style="
                    color:var(--text-muted);
                    font-size:0.85rem;
                    margin-bottom:0.75rem;
                ">

                    ${
            alert.triggeredRules ||
            'No rules'
        }

                </div>

                <div style="
                    display:flex;
                    justify-content:space-between;
                    align-items:center;
                ">

                    <span class="
                        badge badge-${
            alert.alertStatus ===
            'PENDING'
                ? 'warning'
                : alert.alertStatus ===
                'REVIEWED'
                    ? 'success'
                    : 'info'
        }
                    ">

                        ${alert.alertStatus}

                    </span>

                    <small style="
                        color:var(--text-muted)
                    ">

                        ${
            formatDate(
                alert.createdAt
            )
        }

                    </small>

                </div>

            </div>

        `).join('');
}

function formatDate(date) {

    return new Date(date)
        .toLocaleString(
            'en-US',
            {
                month:'short',
                day:'numeric',
                hour:'2-digit',
                minute:'2-digit'
            }
        );
}
async function loadFraudPage() {

    try {

        const res =
            await fetch(
                `${API_BASE}/fraud/alerts`,
                {
                    headers:
                        authHeaders()
                }
            );

        const alerts =
            await res.json();

        renderFraudTable(alerts);

    } catch (e) {

        console.error(e);
    }
}

function renderFraudTable(alerts) {

    const body =
        document.getElementById(
            'fraud-body'
        );

    if (!body) {
        return;
    }

    if (!alerts.length) {

        body.innerHTML = `

            <tr>

                <td colspan="7"
                    style="
                        text-align:center;
                        padding:2rem;
                        color:var(--text-muted);
                    ">

                    No alerts found

                </td>

            </tr>
        `;

        return;
    }

    body.innerHTML =
        alerts.map(alert => `

            <tr>

                <td>
                    ${alert.id}
                </td>

                <td style="
                    font-family:monospace;
                    font-size:0.8rem;
                ">

                    ${alert.transactionReference}

                </td>

                <td>

                    <span class="
                        badge badge-${
            alert.fraudScore >= 70
                ? 'danger'
                : alert.fraudScore >= 40
                    ? 'warning'
                    : 'info'
        }
                    ">

                        ${alert.fraudScore}

                    </span>

                </td>

                <td>

                    <span class="
                        badge badge-${
            alert.alertStatus ===
            'PENDING'
                ? 'warning'
                : alert.alertStatus ===
                'REVIEWED'
                    ? 'success'
                    : 'info'
        }
                    ">

                        ${alert.alertStatus}

                    </span>

                </td>

                <td style="
                    max-width:240px;
                    color:var(--text-muted);
                    font-size:0.8rem;
                ">

                    ${
            alert.triggeredRules ||
            'No rules'
        }

                </td>

                <td>

                    ${
            formatDate(
                alert.createdAt
            )
        }

                </td>

                <td>

                    <div style="
                        display:flex;
                        gap:0.5rem;
                        flex-wrap:wrap;
                    ">

                        <button class="
                            btn btn-secondary btn-sm
                        "
                        onclick="
                            reviewAlert(${alert.id})
                        ">

                            Review

                        </button>

                        <button class="
                            btn btn-danger btn-sm
                        "
                        onclick="
                            dismissAlert(${alert.id})
                        ">

                            Dismiss

                        </button>

                    </div>

                </td>

            </tr>

        `).join('');
}

async function reviewAlert(alertId) {

    try {

        await fetch(
            `${API_BASE}/fraud/alerts/${alertId}/review`,
            {
                method:'PATCH',
                headers:
                    authHeaders()
            }
        );

        loadFraudPage();

    } catch (e) {

        console.error(e);
    }
}

async function dismissAlert(alertId) {

    try {

        await fetch(
            `${API_BASE}/fraud/alerts/${alertId}/dismiss`,
            {
                method:'PATCH',
                headers:
                    authHeaders()
            }
        );

        loadFraudPage();

    } catch (e) {

        console.error(e);
    }
}

async function freezeWallet() {

    const walletId =
        document.getElementById(
            'wallet-id'
        ).value;

    const reason =
        document.getElementById(
            'freeze-reason'
        ).value;

    if (!walletId || !reason) {
        return;
    }

    try {

        await fetch(
            `${API_BASE}/fraud/freeze/${walletId}?reason=${encodeURIComponent(reason)}`,
            {
                method:'POST',
                headers:
                    authHeaders()
            }
        );

        alert('Wallet frozen');

    } catch (e) {

        console.error(e);
    }
}

async function unfreezeWallet() {

    const walletId =
        document.getElementById(
            'wallet-id'
        ).value;

    if (!walletId) {
        return;
    }

    try {

        await fetch(
            `${API_BASE}/fraud/unfreeze/${walletId}`,
            {
                method:'POST',
                headers:
                    authHeaders()
            }
        );

        alert('Wallet unfrozen');

    } catch (e) {

        console.error(e);
    }
}