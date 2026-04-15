const Auth = (() => {
    function normalizeEmail(email) {
        return String(email || '').trim().toLowerCase();
    }

    function getToken() {
        return localStorage.getItem('token');
    }

    function getUsuarioActual() {
        try {
            return JSON.parse(localStorage.getItem('usuario') || 'null');
        } catch {
            return null;
        }
    }

    function isAuthenticated() {
        return Boolean(getToken() && getUsuarioActual());
    }

    function isAdmin() {
        const usuario = getUsuarioActual();
        const rol = usuario ? String(usuario.rol || '').trim().toUpperCase() : '';
        return Boolean(rol === 'ADMIN');
    }

    function tryOfflineLogin(email) {
        const token = getToken();
        const usuario = getUsuarioActual();
        if (!token || !usuario) {
            return false;
        }
        return normalizeEmail(usuario.email) === normalizeEmail(email);
    }

    async function login(email, password) {
        try {
            const resp = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password }),
            });

            if (!resp.ok) {
                const errorBody = await safeJson(resp);
                if (resp.status === 401) {
                    return {
                        ok: false,
                        code: 'INVALID_CREDENTIALS',
                        message: 'Correo o contrasena incorrectos. Verifica tus datos e intenta nuevamente.',
                    };
                }
                return {
                    ok: false,
                    code: 'LOGIN_ERROR',
                    message: (errorBody && errorBody.error) || 'No se pudo iniciar sesion en este momento.',
                };
            }

            const data = await resp.json();
            if (!data || !data.token) {
                return {
                    ok: false,
                    code: 'INVALID_RESPONSE',
                    message: 'Respuesta inesperada del servidor al iniciar sesion.',
                };
            }

            localStorage.setItem('token', data.token);

            let usuario = data.usuario || null;
            if (!usuario) {
                usuario = await fetchCurrentUser(data.token);
            }

            if (usuario) {
                localStorage.setItem('usuario', JSON.stringify(usuario));
            } else {
                localStorage.removeItem('usuario');
            }

            return { ok: true, offline: false, data: { token: data.token, usuario } };
        } catch {
            if (tryOfflineLogin(email)) {
                return { ok: true, offline: true };
            }
            return {
                ok: false,
                code: 'OFFLINE_NO_SESSION',
                message: 'Sin conexion y sin sesion previa guardada en este dispositivo.',
            };
        }
    }

    async function fetchCurrentUser(token) {
        try {
            const resp = await fetch('/api/users/me', {
                headers: { Authorization: `Bearer ${token}` },
            });
            if (!resp.ok) {
                return null;
            }
            return await resp.json();
        } catch {
            return null;
        }
    }

    async function safeJson(resp) {
        try {
            return await resp.json();
        } catch {
            return null;
        }
    }

    function logout(redirectTo) {
        localStorage.removeItem('token');
        localStorage.removeItem('usuario');
        if (redirectTo) {
            location.href = redirectTo;
        }
    }

    function requireAuth(redirectTo = '/login.html') {
        if (!isAuthenticated()) {
            location.href = redirectTo;
            return false;
        }
        return true;
    }

    function requireAdmin(redirectTo = '/encuestas.html') {
        if (!requireAuth('/login.html')) {
            return false;
        }
        if (!isAdmin()) {
            location.href = redirectTo;
            return false;
        }
        return true;
    }

    function attachLogoutButton(buttonId, redirectTo = '/login.html') {
        const button = document.getElementById(buttonId);
        if (!button) {
            return;
        }
        button.addEventListener('click', () => logout(redirectTo));
    }

    function applySessionText(targetId) {
        const target = document.getElementById(targetId);
        if (!target) {
            return;
        }
        const usuario = getUsuarioActual();
        if (!usuario || !getToken()) {
            target.textContent = 'No autenticado';
            return;
        }
        const rol = String(usuario.rol || 'SIN_ROL').trim();
        target.textContent = `Autenticado: ${usuario.email} (${rol})`;
    }

    function authHeader() {
        const token = getToken();
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    return {
        getToken,
        getUsuarioActual,
        isAuthenticated,
        isAdmin,
        login,
        logout,
        requireAuth,
        requireAdmin,
        attachLogoutButton,
        applySessionText,
        authHeader,
    };
})();

