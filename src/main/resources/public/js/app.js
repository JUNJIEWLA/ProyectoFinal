let fotoBase64 = null;
let mapa;
let marcadores = [];

async function initApp() {
    await OfflineDB.init();
    registerServiceWorker();
    initMap();
    initWebcam();
    bindEvents();
    await renderPendientes();
    await loadMarkers();
}

function bindEvents() {
    document.getElementById('btnLogin').addEventListener('click', login);
    document.getElementById('formEncuesta').addEventListener('submit', guardarFormularioLocal);
    document.getElementById('btnSync').addEventListener('click', sincronizar);
    document.getElementById('btnFoto').addEventListener('click', tomarFoto);
}

async function login() {
    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;

    const resp = await fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
    });

    if (!resp.ok) {
        alert('Login invalido');
        return;
    }

    const data = await resp.json();
    localStorage.setItem('token', data.token);
    localStorage.setItem('usuario', JSON.stringify(data.usuario));
    alert('Sesion iniciada');
}

async function guardarFormularioLocal(event) {
    event.preventDefault();

    const usuario = JSON.parse(localStorage.getItem('usuario') || '{"email":"anonimo"}');
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

