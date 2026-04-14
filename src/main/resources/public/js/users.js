function initUsuariosPage() {
    if (!Auth.requireAdmin('/encuestas.html')) {
        return;
    }

    bindUsersEvents();
    updateUsersSessionUI();
    loadUsers();
}

function bindUsersEvents() {
    document.getElementById('formUsuario').addEventListener('submit', crearUsuario);
    Auth.attachLogoutButton('btnLogout', '/login.html');
}

function updateUsersSessionUI() {
    Auth.applySessionText('sessionStatus');
}

async function loadUsers() {
    const token = Auth.getToken();
    if (!token) {
        return;
    }

    const resp = await fetch('/api/users', {
        headers: Auth.authHeader(),
    });

    if (!resp.ok) {
        return;
    }

    const users = await resp.json();
    renderUsers(users);
}

function renderUsers(users) {
    const lista = document.getElementById('listaUsuarios');
    lista.innerHTML = '';

    users.forEach((u) => {
        const li = document.createElement('li');
        li.className = 'list-group-item d-flex justify-content-between align-items-center gap-2 flex-wrap';
        li.innerHTML = `
            <span class="me-auto">
                ${u.nombre || ''}
                <small style="color: #94a3b8;">${u.email}</small>
            </span>
            <select class="form-select form-select-sm w-auto" aria-label="Rol">
                <option value="OPERADOR">OPERADOR</option>
                <option value="ADMIN">ADMIN</option>
            </select>
            <button class="btn btn-sm btn-outline-primary">Actualizar</button>
            <button class="btn btn-sm btn-outline-danger">Eliminar</button>
        `;

        const selectRol = li.querySelector('select');
        selectRol.value = u.rol || 'OPERADOR';

        const btnActualizar = li.querySelectorAll('button')[0];
        btnActualizar.addEventListener('click', async () => {
            await actualizarUsuario(u.id, { rol: selectRol.value });
            await loadUsers();
        });

        const btnEliminar = li.querySelectorAll('button')[1];
        btnEliminar.addEventListener('click', async () => {
            if (!confirm(`Eliminar usuario ${u.email}?`)) {
                return;
            }
            await eliminarUsuario(u.id);
            await loadUsers();
        });

        lista.appendChild(li);
    });
}

async function crearUsuario(event) {
    event.preventDefault();

    const payload = {
        nombre: document.getElementById('uNombre').value,
        email: document.getElementById('uEmail').value,
        password: document.getElementById('uPassword').value,
        rol: document.getElementById('uRol').value,
    };

    const resp = await fetch('/api/users', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...Auth.authHeader(),
        },
        body: JSON.stringify(payload),
    });

    if (!resp.ok) {
        const err = await safeJson(resp);
        alert((err && err.error) || 'No se pudo crear el usuario');
        return;
    }

    document.getElementById('formUsuario').reset();
    await loadUsers();
}

async function eliminarUsuario(id) {
    await fetch(`/api/users/${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: Auth.authHeader(),
    });
}

async function actualizarUsuario(id, payload) {
    const resp = await fetch(`/api/users/${encodeURIComponent(id)}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            ...Auth.authHeader(),
        },
        body: JSON.stringify(payload),
    });

    if (!resp.ok) {
        const err = await safeJson(resp);
        alert((err && err.error) || 'No se pudo actualizar el usuario');
    }
}

async function safeJson(resp) {
    try {
        return await resp.json();
    } catch {
        return null;
    }
}

initUsuariosPage();

