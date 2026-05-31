const API_BASE = 'https://finflow-backendapp.onrender.com/api';

const currentPage =
    window.location.pathname
        .split('/')
        .pop();

if (
    (currentPage === '' ||
        currentPage === 'index.html')
    &&
    sessionStorage.getItem('token')
) {

    const savedRole =
        sessionStorage.getItem('role');

    window.location.href =
        savedRole === 'ADMIN'
            ? 'admin/admin-dashboard.html'
            : 'dashboard.html';
}

function switchTab(tab) {

    document.querySelectorAll('.tab-btn')
        .forEach((b, i) => {

            b.classList.toggle(
                'active',
                (tab === 'login' && i === 0) ||
                (tab === 'register' && i === 1)
            );
        });

    document.getElementById('login-form')
        .style.display =
        tab === 'login'
            ? 'block'
            : 'none';

    document.getElementById('register-form')
        .style.display =
        tab === 'register'
            ? 'block'
            : 'none';
}

function showAlert(id, message, type='error') {

    const el =
        document.getElementById(id);

    el.className =
        `alert alert-${type} show`;

    el.textContent = message;

    setTimeout(() => {
        el.classList.remove('show');
    }, 4000);
}

function extractUser(data) {

    const source =
        data?.user || data || {};

    return {
        token:
            source.token ||
            data?.token ||
            '',

        username:
            source.username ||
            data?.username ||
            '',

        email:
            source.email ||
            data?.email ||
            '',

        role:
            source.role ||
            data?.role ||
            'USER'
    };
}

async function handleLogin() {

    const username =
        document.getElementById(
            'login-username'
        ).value.trim();

    const password =
        document.getElementById(
            'login-password'
        ).value.trim();

    if (!username || !password) {

        showAlert(
            'login-alert',
            'Please fill in all fields'
        );

        return;
    }

    const btn =
        document.getElementById(
            'login-btn-text'
        );

    btn.innerHTML =
        '<span class="spinner"></span>';

    try {

        const res =
            await fetch(
                `${API}/auth/login`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type':
                            'application/json'
                    },
                    body: JSON.stringify({
                        username,
                        password
                    })
                }
            );

        const data =
            await res.json();

        if (!res.ok) {

            showAlert(
                'login-alert',
                data.error ||
                data.message ||
                'Invalid credentials'
            );

            return;
        }

        const user =
            extractUser(data);

        sessionStorage.setItem(
            'token',
            user.token
        );

        sessionStorage.setItem(
            'username',
            user.username
        );

        sessionStorage.setItem(
            'email',
            user.email
        );

        sessionStorage.setItem(
            'role',
            user.role
        );

        window.location.href =
            user.role === 'ADMIN'
                ? 'admin/admin-dashboard.html'
                : 'dashboard.html';

    } catch (e) {

        showAlert(
            'login-alert',
            'Connection error'
        );

    } finally {

        btn.textContent = 'Sign In';
    }
}

async function handleRegister() {

    const username =
        document.getElementById(
            'reg-username'
        ).value.trim();

    const email =
        document.getElementById(
            'reg-email'
        ).value.trim();

    const password =
        document.getElementById(
            'reg-password'
        ).value.trim();

    if (
        !username ||
        !email ||
        !password
    ) {

        showAlert(
            'register-alert',
            'Please fill in all fields'
        );

        return;
    }

    if (password.length < 6) {

        showAlert(
            'register-alert',
            'Password must be at least 6 characters'
        );

        return;
    }

    const btn =
        document.getElementById(
            'reg-btn-text'
        );

    btn.innerHTML =
        '<span class="spinner"></span>';

    try {

        const res =
            await fetch(
                `${API}/auth/register`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type':
                            'application/json'
                    },
                    body: JSON.stringify({
                        username,
                        email,
                        password
                    })
                }
            );

        const data =
            await res.json();

        if (!res.ok) {

            showAlert(
                'register-alert',
                data.error ||
                data.message ||
                'Registration failed'
            );

            return;
        }

        const user =
            extractUser(data);

        sessionStorage.setItem(
            'token',
            user.token
        );

        sessionStorage.setItem(
            'username',
            user.username
        );

        sessionStorage.setItem(
            'email',
            user.email
        );

        sessionStorage.setItem(
            'role',
            user.role
        );

        window.location.href =
            user.role === 'ADMIN'
                ? 'admin/admin-dashboard.html'
                : 'dashboard.html';

    } catch (e) {

        showAlert(
            'register-alert',
            'Connection error'
        );

    } finally {

        btn.textContent =
            'Create Account';
    }
}

document.addEventListener(
    'keydown',
    e => {

        if (e.key !== 'Enter') {
            return;
        }

        const loginVisible =
            document.getElementById(
                'login-form'
            ).style.display !== 'none';

        loginVisible
            ? handleLogin()
            : handleRegister();
    }
);