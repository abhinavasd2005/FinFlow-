const API_BASE = 'https://finflow-backendapp.onrender.com/api';

const token = sessionStorage.getItem('token');
const username = sessionStorage.getItem('username');

if (!token) {
    window.location.href = 'index.html';
}

let wallets = [];

document.addEventListener('DOMContentLoaded', async () => {

    document.getElementById('nav-username').textContent =
        username || '';

    await loadWallets();
    const params =
        new URLSearchParams(
            window.location.search
        );

    const walletParam =
        params.get('wallet');

    if (walletParam) {

        document.getElementById(
            'wallet-select'
        ).value = walletParam;
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

        const res = await fetch(
            `${API_BASE}/wallets/my-wallets`,
            { headers: authHeaders() }
        );

        wallets = await res.json();

        const select =
            document.getElementById('wallet-select');

        wallets.forEach(w => {

            const option =
                document.createElement('option');

            option.value = w.id;
            option.textContent =
                `${w.walletName} (${w.walletNumber})`;

            select.appendChild(option);
        });

    } catch (e) {
        console.error(e);
    }
}

function setDefaultDates() {

    const now = new Date();

    const before = new Date();
    before.setDate(before.getDate() - 30);

    document.getElementById('from-date').value =
        before.toISOString().slice(0,16);

    document.getElementById('to-date').value =
        now.toISOString().slice(0,16);
}

async function loadHistory() {

    const walletId =
        document.getElementById('wallet-select').value;

    const from =
        document.getElementById('from-date').value;

    const to =
        document.getElementById('to-date').value;

    try {

        const res = await fetch(
            `${API_BASE}/transactions/wallet/${walletId}/filter?from=${from}:00&to=${to}:00`,
            {
                headers: authHeaders()
            }
        );

        const txns = await res.json();

        renderTransactions(txns);

    } catch (e) {

        console.error(e);
    }
}

function renderTransactions(txns) {

    document.getElementById('history-loading')
        .style.display = 'none';

    document.getElementById('history-table')
        .style.display = 'table';

    document.getElementById('txn-count')
        .textContent = `${txns.length} transactions`;

    const body =
        document.getElementById('history-body');

    body.innerHTML = '';

    txns.forEach(txn => {

        const row = document.createElement('tr');

        const isDebit =
            txn.transactionType === 'TRANSFER';

        const fraudBadge =
            txn.fraudScore > 70
                ? `<span class="badge badge-danger">${txn.fraudScore}</span>`
                : txn.fraudScore > 0
                    ? `<span class="badge badge-warning">${txn.fraudScore}</span>`
                    : `<span class="badge badge-success">0</span>`;

        row.innerHTML = `

            <td>
                ${formatDate(txn.createdAt)}
            </td>

            <td style="
                font-family:monospace;
                font-size:0.78rem
            ">
                ${txn.referenceNumber}
            </td>

            <td>
                ${txn.transactionType}
            </td>

            <td class="
                txn-amount ${isDebit ? 'debit' : 'credit'}
            ">
                ${isDebit ? '-' : '+'}
                ${fmt(txn.amount)}
            </td>

            <td>
                <span class="
                    badge badge-${
            txn.status === 'COMPLETED'
                ? 'success'
                : 'danger'
        }
                ">
                    ${txn.status}
                </span>
            </td>

            <td>
                ${fraudBadge}
            </td>

            <td>
                <a href="
                    slip.html?txn=${txn.transactionId || txn.id}
                "
                   class="btn btn-secondary btn-sm">
                    View
                </a>
            </td>
        `;

        body.appendChild(row);
    });
}

async function loadStatement() {

    const walletId =
        document.getElementById('wallet-select').value;

    const from =
        document.getElementById('from-date').value;

    const to =
        document.getElementById('to-date').value;

    try {

        const res = await fetch(
            `${API_BASE}/transactions/wallet/${walletId}/statement?from=${from}:00&to=${to}:00`,
            {
                headers: authHeaders()
            }
        );

        const data = await res.json();

        renderStatement(data);

    } catch (e) {
        console.error(e);
    }
}

function renderStatement(data) {

    const box =
        document.getElementById('statement-box');

    box.style.display = 'block';

    box.innerHTML = `

        <h3 style="margin-bottom:1rem">
            Statement Summary
        </h3>

        <div class="statement-row">
            <span>Wallet</span>
            <strong>${data.walletName}</strong>
        </div>

        <div class="statement-row">
            <span>Wallet Number</span>
            <strong>${data.walletNumber}</strong>
        </div>

        <div class="statement-row">
            <span>Current Balance</span>
            <strong>${fmt(data.currentBalance)}</strong>
        </div>

        <div class="statement-row">
            <span>Total Credited</span>
            <strong style="color:var(--success)">
                ${fmt(data.totalCredited)}
            </strong>
        </div>

        <div class="statement-row">
            <span>Total Debited</span>
            <strong style="color:var(--danger)">
                ${fmt(data.totalDebited)}
            </strong>
        </div>

        <div class="statement-row">
            <span>Total Transactions</span>
            <strong>${data.totalTransactions}</strong>
        </div>
    `;
}

function formatDate(date) {

    return new Date(date)
        .toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
}