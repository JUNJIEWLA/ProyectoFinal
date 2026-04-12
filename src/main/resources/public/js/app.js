let fotoBase64 = null;
let mapa;
let marcadores = [];

async function initApp() {
    await OfflineDB.init();
    registerServiceWorker();
    initMap();
    initWebcam();
    bindEvents();
    updateSessionUI();
    await renderPendientes();
    await loadMarkers();
    await maybeLoadUsers();
}

function bindEvents() {
    document.getElementById('btnLogin').addEventListener('click', login);
    document.getElementById('btnLogout').addEventListener('click', logout);
    document.getElementById('formUsuario').addEventListener('submit', crearUsuario);
    document.getElementById('formEncuesta').addEventListener('submit', guardarFormularioLocal);
    document.getElementById('btnSync').addEventListener('click', sincronizar);
    document.getElementById('btnFoto').addEventListener('click', tomarFoto);
}

async function login() {
    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;

    let resp;
    try {
        resp = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        });
    } catch (err) {
        if (tryOfflineLogin(email)) {
            alert('Sesion offline (sin conexion)');
            updateSessionUI();
            return;
        }
        alert('Sin conexion y sin sesion previa guardada');
        return;
    }

    if (!resp.ok) {
        alert('Login invalido');
        return;
    }

    const data = await resp.json();
    localStorage.setItem('token', data.token);
    localStorage.setItem('usuario', JSON.stringify(data.usuario));
    alert('Sesion iniciada');
    updateSessionUI();
    await maybeLoadUsers();
    await loadMarkers();
}

function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    updateSessionUI();
}

function tryOfflineLogin(email) {
    const token = localStorage.getItem('token');
    const usuario = getUsuarioActual();
    if (!token || !usuario) {
        return false;
    }
    if (!email || usuario.email !== String(email).trim().toLowerCase()) {
        return false;
    }
    return true;
}

function getUsuarioActual() {
    try {
        return JSON.parse(localStorage.getItem('usuario') || 'null');
    } catch {
        return null;
    }
}

function updateSessionUI() {
    const usuario = getUsuarioActual();
    const token = localStorage.getItem('token');

    const status = document.getElementById('sessionStatus');
    const btnLogout = document.getElementById('btnLogout');
    const adminCard = document.getElementById('adminUsuariosCard');

    if (usuario && token) {
        status.textContent = `Autenticado: ${usuario.email} (${usuario.rol || 'SIN_ROL'})`;
        btnLogout.classList.remove('d-none');
    } else {
        status.textContent = 'No autenticado';
        btnLogout.classList.add('d-none');
    }

    if (usuario && usuario.rol === 'ADMIN') {
        adminCard.classList.remove('d-none');
    } else {
        adminCard.classList.add('d-none');
    }
}

async function guardarFormularioLocal(event) {
    event.preventDefault();

    const usuario = getUsuarioActual();
    if (!usuario) {
        alert('Debes iniciar sesion para registrar formularios');
        return;
    }

    const form = {
        id: crypto.randomUUID(),
        nombre: document.getElementById('nombre').value,
        sector: document.getElementById('sector').value,
        nivelEscolar: document.getElementById('nivelEscolar').value,
        usuarioRegistro: usuario.email,
        latitud: 0,
        longitud: 0,
        fotografia: fotoBase64,
        fechaRegistro: new Date().toISOString(),
        sincronizado: false,
        updatedAt: Date.now(),
    };

    await completarGeo(form);
    await OfflineDB.saveFormulario(form);
    await renderPendientes();
    document.getElementById('formEncuesta').reset();
    fotoBase64 = null;
}

function completarGeo(formulario) {
    return new Promise((resolve) => {
        if (!navigator.geolocation) {
            return resolve();
        }

        navigator.geolocation.getCurrentPosition(
            (pos) => {
                formulario.latitud = pos.coords.latitude;
                formulario.longitud = pos.coords.longitude;
                document.getElementById('geoStatus').textContent = 'Geo OK';
                document.getElementById('geoStatus').className = 'badge text-bg-success';
                resolve();
            },
            () => resolve(),
            { timeout: 5000 }
        );
    });
}

async function sincronizar() {
    try {
        const result = await SyncClient.syncPending();
        await renderPendientes();
        await loadMarkers();
        alert(`Sincronizados: ${result.synced}`);
    } catch (err) {
        alert(err.message);
    }
}

async function renderPendientes() {
    const lista = document.getElementById('listaPendientes');
    const pendientes = await OfflineDB.getPending();
    lista.innerHTML = '';

    pendientes.forEach((p) => {
        const li = document.createElement('li');
        li.className = 'list-group-item d-flex justify-content-between align-items-center';
        li.innerHTML = `<span>${p.nombre} - ${p.sector}</span><button class="btn btn-sm btn-outline-danger">Borrar</button>`;
        li.querySelector('button').addEventListener('click', async () => {
            await OfflineDB.deleteFormulario(p.id);
            await renderPendientes();
        });
        lista.appendChild(li);
    });
}

async function maybeLoadUsers() {
    const usuario = getUsuarioActual();
    if (!usuario || usuario.rol !== 'ADMIN') {
        return;
    }
    await loadUsers();
}

async function loadUsers() {
    const token = localStorage.getItem('token');
    if (!token) {
        return;
    }

    const resp = await fetch('/api/users', {
        headers: { Authorization: `Bearer ${token}` },
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
                <small class="text-muted">${u.email}</small>
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
    const token = localStorage.getItem('token');
    if (!token) {
        alert('Debes iniciar sesion');
        return;
    }

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
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
    });

    if (!resp.ok) {
        const err = await safeJson(resp);
        alert(err?.error || 'No se pudo crear el usuario');
        return;
    }

    document.getElementById('formUsuario').reset();
    await loadUsers();
}

async function eliminarUsuario(id) {
    const token = localStorage.getItem('token');
    if (!token) {
        return;
    }

    await fetch(`/api/users/${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
    });
}

async function actualizarUsuario(id, payload) {
    const token = localStorage.getItem('token');
    if (!token) {
        return;
    }

    const resp = await fetch(`/api/users/${encodeURIComponent(id)}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
    });

    if (!resp.ok) {
        const err = await safeJson(resp);
        alert(err?.error || 'No se pudo actualizar el usuario');
    }
}

async function safeJson(resp) {
    try {
        return await resp.json();
    } catch {
        return null;
    }
}

function initMap() {
    mapa = L.map('mapa').setView([19.4, -70.6], 8);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(mapa);
}

async function loadMarkers() {
    marcadores.forEach((m) => mapa.removeLayer(m));
    marcadores = [];

    const token = localStorage.getItem('token');
    if (!token) {
        return;
    }

    const resp = await fetch('/api/formularios', {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (!resp.ok) {
        return;
    }

    const formularios = await resp.json();
    formularios.forEach((f) => {
        if (typeof f.latitud !== 'number' || typeof f.longitud !== 'number') {
            return;
        }
        const marker = L.marker([f.latitud, f.longitud]).addTo(mapa);
        marker.bindPopup(`<b>${f.nombre || 'Sin nombre'}</b><br>${f.sector || 'Sin sector'}`);
        marcadores.push(marker);
    });
}

function initWebcam() {
    const webcamEl = document.getElementById('webcam');
    const canvasEl = document.getElementById('canvas');
    window.webcam = new Webcam(webcamEl, 'user', canvasEl);
    webcam.start().catch(() => {
        // Silencioso para no bloquear el formulario en equipos sin camara.
    });
}

function tomarFoto() {
    if (window.webcam) {
        fotoBase64 = webcam.snap();
    }
}

function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js');
    }
}

initApp();

