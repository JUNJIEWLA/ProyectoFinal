function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js');
    }
}

function resolveStatusContainer() {
    return document.getElementById('loginError') || document.getElementById('loginStatus');
}

function showStatus(message, type) {
    const status = resolveStatusContainer();
    if (!status) {
        return;
    }

    status.textContent = message;
    status.style.display = 'block';

    if (status.id === 'loginError') {
        if (type === 'success') {
            status.style.background = 'rgba(16,185,129,0.12)';
            status.style.border = '1px solid rgba(16,185,129,0.35)';
            status.style.color = '#6ee7b7';
            return;
        }
        status.style.background = 'rgba(239,68,68,0.12)';
        status.style.border = '1px solid rgba(239,68,68,0.3)';
        status.style.color = '#fca5a5';
        return;
    }

    status.className = type === 'success' ? 'alert alert-success mt-2' : 'alert alert-danger mt-2';
}

function hideStatus() {
    const status = resolveStatusContainer();
    if (!status) {
        return;
    }
    status.textContent = '';
    status.style.display = 'none';
}

function getLoginButton() {
    return document.getElementById('btnLogin');
}

async function submitLogin() {
    const email = document.getElementById('email');
    const password = document.getElementById('password');
    const button = getLoginButton();

    if (!email || !password) {
        return;
    }

    hideStatus();

    const emailValue = email.value.trim();
    const passwordValue = password.value;

    if (!emailValue || !passwordValue) {
        showStatus('Completa correo y contrasena para continuar.', 'error');
        return;
    }

    if (button) {
        button.disabled = true;
        button.textContent = 'Validando...';
    }

    const result = await Auth.login(emailValue, passwordValue);
    if (!result.ok) {
        showStatus(result.message, 'error');
        if (button) {
            button.disabled = false;
            button.textContent = 'Entrar';
        }
        return;
    }

    showStatus(result.offline ? 'Sesion iniciada en modo offline.' : 'Sesion iniciada correctamente. Redirigiendo...', 'success');
    setTimeout(() => {
        location.href = '/encuestas.html';
    }, 450);
}

function bindLoginPage() {
    const form = document.getElementById('formLogin');
    const button = getLoginButton();
    const email = document.getElementById('email');
    const password = document.getElementById('password');

    if (!email || !password) {
        return;
    }

    if (form) {
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            await submitLogin();
        });
    }

    if (button) {
        button.addEventListener('click', submitLogin);
    }

    [email, password].forEach((input) => {
        input.addEventListener('keydown', async (event) => {
            if (event.key !== 'Enter' || form) {
                return;
            }
            event.preventDefault();
            await submitLogin();
        });
    });
}

(function initLoginPage() {
    registerServiceWorker();
    if (Auth.isAuthenticated()) {
        location.href = '/encuestas.html';
        return;
    }
    bindLoginPage();
})();
