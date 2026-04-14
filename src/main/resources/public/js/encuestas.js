let fotoBase64 = null;
let mapa;
let marcadores = [];
let formularioEnEdicion = null;
let webcamReady = false;
let ultimaGeo = null;

async function initEncuestasPage() {
    if (!Auth.requireAuth('/login.html')) {
        return;
    }

    await OfflineDB.init();
    registerServiceWorker();
    bindEvents();
    updateSessionUI();
    initMap();
    await renderPendientes();
    await loadMarkers();
}

function bindEvents() {
    const formEncuesta = document.getElementById('formEncuesta');
    const btnSync = document.getElementById('btnSync');
    const btnFoto = document.getElementById('btnFoto');
    const btnAbrirModal = document.getElementById('btnAbrirModal');
    const btnCerrarModal = document.getElementById('btnCerrarModal');
    const modal = document.getElementById('modalEncuesta');

    formEncuesta.addEventListener('submit', guardarFormularioLocal);
    btnSync.addEventListener('click', sincronizar);
    btnFoto.addEventListener('click', tomarFoto);
    btnAbrirModal.addEventListener('click', () => abrirModalEncuesta());
    btnCerrarModal.addEventListener('click', cerrarModalEncuesta);
    modal.addEventListener('click', (event) => {
        if (event.target === modal) {
            cerrarModalEncuesta();
        }
    });

    ['nombre', 'sector', 'nivelEscolar'].forEach((id) => {
        const input = document.getElementById(id);
        input.addEventListener('input', () => clearFieldError(id));
        input.addEventListener('change', () => clearFieldError(id));
    });

    Auth.attachLogoutButton('btnLogout', '/login.html');
}

function updateSessionUI() {
    Auth.applySessionText('sessionStatus');

    const navAdmin = document.getElementById('navAdmin');
    if (navAdmin) {
        navAdmin.classList.toggle('d-none', !Auth.isAdmin());
    }
}

function abrirModalEncuesta(formulario = null) {
    formularioEnEdicion = formulario;

    const modal = document.getElementById('modalEncuesta');
    const titulo = modal.querySelector('.modal-head h5');
    const form = document.getElementById('formEncuesta');

    if (formulario) {
        titulo.textContent = 'Editar encuesta';
        form.nombre.value = formulario.nombre || '';
        form.sector.value = formulario.sector || '';
        form.nivelEscolar.value = formulario.nivelEscolar || '';
        fotoBase64 = formulario.fotografia || null;
        ultimaGeo = {
            latitud: formulario.latitud || 0,
            longitud: formulario.longitud || 0,
        };
        if (fotoBase64) {
            mostrarPreviewFoto(fotoBase64);
        } else {
            limpiarPreviewFoto();
        }
        if (ultimaGeo.latitud && ultimaGeo.longitud) {
            setGeoStatusOk(ultimaGeo.latitud, ultimaGeo.longitud);
        } else {
            setGeoStatusIdle();
        }
    } else {
        titulo.textContent = 'Nueva encuesta';
        form.reset();
        fotoBase64 = null;
        ultimaGeo = null;
        limpiarPreviewFoto();
        setGeoStatusIdle();
    }

    modal.classList.add('open');
    startCamera();
    clearValidationErrors();
    if (!formulario) {
        requestGeoPreview();
    }
}

function cerrarModalEncuesta() {
    const modal = document.getElementById('modalEncuesta');
    modal.classList.remove('open');
    stopCamera();
}

async function guardarFormularioLocal(event) {
    event.preventDefault();

    const usuario = Auth.getUsuarioActual();
    if (!usuario) {
        alert('Debes iniciar sesion para registrar formularios.');
        return;
    }

    const nombre = document.getElementById('nombre').value.trim();
    const sector = document.getElementById('sector').value.trim();
    const nivelEscolar = document.getElementById('nivelEscolar').value;

    if (!validateFormulario({ nombre, sector, nivelEscolar, fotoBase64 })) {
        return;
    }

    try {
        const geo = formularioEnEdicion
            ? {
                latitud: formularioEnEdicion.latitud || 0,
                longitud: formularioEnEdicion.longitud || 0,
            }
            : await obtenerGeoParaGuardar();

        const formulario = {
            id: formularioEnEdicion ? formularioEnEdicion.id : crypto.randomUUID(),
            nombre,
            sector,
            nivelEscolar,
            usuarioRegistro: usuario.email,
            latitud: geo.latitud,
            longitud: geo.longitud,
            fotografia: fotoBase64,
            fechaRegistro: formularioEnEdicion?.fechaRegistro || new Date().toISOString(),
            sincronizado: false,
            updatedAt: Date.now(),
        };

        const wasEditing = Boolean(formularioEnEdicion);

        await OfflineDB.saveFormulario(formulario);

        await renderPendientes();
        await loadMarkers();

        showToastEncuestas(wasEditing ? 'Actualizado correctamente' : 'Guardado correctamente');

        cerrarModalEncuesta();
        formularioEnEdicion = null;
        fotoBase64 = null;
        ultimaGeo = null;
    } catch (error) {
        alert(`No se pudo guardar la encuesta: ${error.message}`);
    }
}

async function editarFormulario(id) {
    const formulario = await OfflineDB.getById(id);
    if (!formulario) {
        alert('No se encontro la encuesta seleccionada.');
        return;
    }

    abrirModalEncuesta(formulario);
}

async function sincronizar() {
    const btnSync = document.getElementById('btnSync');
    const originalText = btnSync.textContent;

    try {
        btnSync.disabled = true;
        btnSync.textContent = 'Sincronizando...';

        const result = await SyncClient.syncPending();
        await renderPendientes();
        await loadMarkers();

        btnSync.textContent = `Sincronizados: ${result.synced}`;
    } catch (err) {
        alert(`Error al sincronizar: ${err.message}`);
    } finally {
        setTimeout(() => {
            btnSync.textContent = originalText;
            btnSync.disabled = false;
        }, 1200);
    }
}

async function renderPendientes() {
    const lista = document.getElementById('listaPendientes');
    const pendientes = await OfflineDB.getPending();
    lista.innerHTML = '';

    if (pendientes.length === 0) {
        lista.innerHTML = '<li class="item-empty">No hay formularios pendientes</li>';
    } else {
        pendientes.forEach((p) => {
            const li = document.createElement('li');
            li.className = 'list-group-item d-flex justify-content-between align-items-center gap-2 flex-wrap';

            const thumbnail = (typeof p.fotografia === 'string' && p.fotografia.startsWith('data:image'))
                ? `<img src="${p.fotografia}" alt="Foto" style="width:40px;height:40px;border-radius:6px;object-fit:cover;">`
                : '<div style="width:40px;height:40px;border-radius:6px;background:rgba(255,255,255,0.06);"></div>';

            li.innerHTML = `
                <div style="display:flex;gap:.75rem;align-items:center;flex:1;min-width:0;">
                    ${thumbnail}
                    <div style="flex:1;min-width:0;">
                        <div style="font-weight:500;font-size:.88rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(p.nombre || 'Sin nombre')}</div>
                        <div style="font-size:.75rem;color:#94a3b8;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(p.sector || 'Sin sector')} · ${escapeHtml(p.nivelEscolar || 'N/A')}</div>
                    </div>
                </div>
                <div style="display:flex;gap:.5rem;flex-wrap:wrap;">
                    <button class="btn btn-sm btn-outline-primary btn-editar" data-id="${p.id}">Editar</button>
                    <button class="btn btn-sm btn-outline-danger btn-borrar" data-id="${p.id}">Borrar</button>
                </div>
            `;

            li.querySelector('.btn-editar').addEventListener('click', () => editarFormulario(p.id));
            li.querySelector('.btn-borrar').addEventListener('click', async () => {
                const confirmed = await confirmDeleteEncuesta(`Se eliminara la encuesta de ${p.nombre}. Esta accion no se puede deshacer.`);
                if (!confirmed) {
                    return;
                }
                await OfflineDB.deleteFormulario(p.id);
                await renderPendientes();
                await loadMarkers();
                showToastEncuestas('Encuesta eliminada correctamente');
            });

            lista.appendChild(li);
        });
    }

    document.getElementById('statPendientes').textContent = String(pendientes.length);

    try {
        const resp = await fetch('/api/formularios', { headers: Auth.authHeader() });
        if (resp.ok) {
            const serverForms = await resp.json();
            document.getElementById('statTotal').textContent = String(serverForms.length + pendientes.length);
            document.getElementById('statSync').textContent = String(serverForms.length);
            return;
        }
    } catch {
        // Sin conexion: se muestran solo datos locales.
    }

    document.getElementById('statTotal').textContent = String(pendientes.length);
    document.getElementById('statSync').textContent = '0';
}

function initMap() {
    mapa = L.map('mapa').setView([19.4, -70.6], 8);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors',
    }).addTo(mapa);
}

async function loadMarkers() {
    marcadores.forEach((marker) => {
        if (mapa.hasLayer(marker)) {
            mapa.removeLayer(marker);
        }
    });
    marcadores = [];

    let formulariosServidor = [];
    try {
        const resp = await fetch('/api/formularios', { headers: Auth.authHeader() });
        if (resp.ok) {
            formulariosServidor = await resp.json();
        }
    } catch {
        // Sin conexion al servidor.
    }

    let formulariosPendientes = [];
    try {
        formulariosPendientes = await OfflineDB.getPending();
    } catch {
        formulariosPendientes = [];
    }

    const formularios = [...formulariosServidor, ...formulariosPendientes];

    try {
        formularios.forEach((f) => {
            if (typeof f.latitud !== 'number' || typeof f.longitud !== 'number') {
                return;
            }
            if (f.latitud === 0 && f.longitud === 0) {
                return;
            }

            const marker = L.marker([f.latitud, f.longitud]).addTo(mapa);
            let popup = `<b>${escapeHtml(f.nombre || 'Sin nombre')}</b><br>${escapeHtml(f.sector || 'Sin sector')}`;
            if (typeof f.fotografia === 'string' && f.fotografia.startsWith('data:image')) {
                popup += `<br><img src="${f.fotografia}" alt="Foto de encuesta" style="width:100px;height:100px;object-fit:cover;border-radius:6px;">`;
            }
            marker.bindPopup(popup);
            marcadores.push(marker);
        });
    } catch {
        // Error inesperado renderizando marcadores.
    }
}

async function startCamera() {
    if (webcamReady && window.webcam) {
        return;
    }

    const webcamEl = document.getElementById('webcam');
    const canvasEl = document.getElementById('canvas');
    if (!webcamEl || !canvasEl) {
        return;
    }

    try {
        window.webcam = new Webcam(webcamEl, 'user', canvasEl);
        await window.webcam.start();
        webcamReady = true;
    } catch {
        webcamReady = false;
    }
}

function stopCamera() {
    if (!window.webcam) {
        return;
    }
    try {
        window.webcam.stop();
    } catch {
        // Ignora errores al cerrar stream.
    }
    webcamReady = false;
}

async function tomarFoto() {
    if (!webcamReady) {
        await startCamera();
    }
    if (!webcamReady || !window.webcam) {
        alert('No fue posible iniciar la camara.');
        return;
    }

    try {
        fotoBase64 = window.webcam.snap();
        mostrarPreviewFoto(fotoBase64);
        clearFieldError('foto');
    } catch (err) {
        alert(`Error al capturar foto: ${err.message}`);
    }
}

function mostrarPreviewFoto(base64) {
    limpiarPreviewFoto();

    const preview = document.createElement('div');
    preview.id = 'fotoPreview';
    preview.style.cssText = 'margin:0.5rem 0;padding:0.5rem;border:1px solid rgba(16,185,129,0.4);border-radius:8px;';

    const img = document.createElement('img');
    img.src = base64;
    img.style.cssText = 'width:100%;max-height:200px;object-fit:contain;border-radius:6px;';

    preview.appendChild(img);

    const webcam = document.getElementById('webcam');
    webcam.parentNode.insertBefore(preview, webcam.nextSibling);
}

function limpiarPreviewFoto() {
    const preview = document.getElementById('fotoPreview');
    if (preview) {
        preview.remove();
    }
}

function setGeoStatusIdle() {
    const geoStatus = document.getElementById('geoStatus');
    geoStatus.textContent = 'Sin geolocalizacion';
    geoStatus.className = 'badge-geo';
}

function setGeoStatusLoading() {
    const geoStatus = document.getElementById('geoStatus');
    geoStatus.textContent = 'Obteniendo ubicacion...';
    geoStatus.className = 'badge-geo';
}

function setGeoStatusOk(lat, lon) {
    const geoStatus = document.getElementById('geoStatus');
    geoStatus.textContent = `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
    geoStatus.className = 'badge-geo active';
}

function setGeoStatusFallback(reason) {
    const geoStatus = document.getElementById('geoStatus');
    geoStatus.textContent = reason;
    geoStatus.className = 'badge-geo';
}

function requestGeoPreview() {
    if (!('geolocation' in navigator)) {
        setGeoStatusFallback('Geolocalizacion no disponible en este navegador.');
        return;
    }

    if (!window.isSecureContext) {
        setGeoStatusFallback('GPS requiere HTTPS o localhost.');
        return;
    }

    setGeoStatusLoading();

    navigator.geolocation.getCurrentPosition(
        (pos) => {
            ultimaGeo = {
                latitud: pos.coords.latitude,
                longitud: pos.coords.longitude,
            };
            setGeoStatusOk(ultimaGeo.latitud, ultimaGeo.longitud);
        },
        (err) => {
            if (err.code === 1) {
                setGeoStatusFallback('Permiso de ubicacion denegado.');
            } else if (err.code === 2) {
                setGeoStatusFallback('Ubicacion no disponible en este momento.');
            } else {
                setGeoStatusFallback('Tiempo de espera agotado para GPS.');
            }
        },
        { enableHighAccuracy: false, timeout: 15000, maximumAge: 60000 }
    );
}

function obtenerGeoParaGuardar() {
    return new Promise((resolve) => {
        if (!('geolocation' in navigator) || !window.isSecureContext) {
            resolve(ultimaGeo || { latitud: 0, longitud: 0 });
            return;
        }

        navigator.geolocation.getCurrentPosition(
            (pos) => {
                ultimaGeo = {
                    latitud: pos.coords.latitude,
                    longitud: pos.coords.longitude,
                };
                setGeoStatusOk(ultimaGeo.latitud, ultimaGeo.longitud);
                resolve(ultimaGeo);
            },
            () => {
                resolve(ultimaGeo || { latitud: 0, longitud: 0 });
            },
            { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 }
        );
    });
}

function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(() => {
            // Ignora error de SW en desarrollo.
        });
    }
}

function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return String(text || '').replace(/[&<>"']/g, (m) => map[m]);
}

function validateFormulario(values) {
    clearValidationErrors();
    let valid = true;

    if (!values.nombre) {
        setFieldError('nombre', 'El nombre es obligatorio.');
        valid = false;
    }
    if (!values.sector) {
        setFieldError('sector', 'El sector es obligatorio.');
        valid = false;
    }
    if (!values.nivelEscolar) {
        setFieldError('nivelEscolar', 'El grado es obligatorio.');
        valid = false;
    }
    if (!values.fotoBase64) {
        setFieldError('foto', 'Debes capturar una foto antes de guardar.');
        valid = false;
    }

    return valid;
}

function clearValidationErrors() {
    ['nombre', 'sector', 'nivelEscolar', 'foto'].forEach((id) => clearFieldError(id));
}

function setFieldError(field, message) {
    const errorEl = document.getElementById(getErrorId(field));
    if (errorEl) {
        errorEl.textContent = message;
    }

    if (field === 'foto') {
        return;
    }

    const input = document.getElementById(field);
    if (input) {
        input.classList.add('is-invalid');
    }
}

function clearFieldError(field) {
    const errorEl = document.getElementById(getErrorId(field));
    if (errorEl) {
        errorEl.textContent = '';
    }
    if (field === 'foto') {
        return;
    }
    const input = document.getElementById(field);
    if (input) {
        input.classList.remove('is-invalid');
    }
}

function capitalize(text) {
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function getErrorId(field) {
    const map = {
        nombre: 'errorNombre',
        sector: 'errorSector',
        nivelEscolar: 'errorNivel',
        foto: 'errorFoto',
    };
    return map[field] || `error${capitalize(field)}`;
}

function confirmDeleteEncuesta(message) {
    const modal = document.getElementById('confirmModalEncuesta');
    const text = document.getElementById('confirmModalEncuestaText');
    const btnCancel = document.getElementById('confirmModalEncuestaCancel');
    const btnOk = document.getElementById('confirmModalEncuestaOk');

    return new Promise((resolve) => {
        text.textContent = message;
        modal.classList.add('open');

        const close = (value) => {
            modal.classList.remove('open');
            btnCancel.removeEventListener('click', onCancel);
            btnOk.removeEventListener('click', onOk);
            modal.removeEventListener('click', onBackdrop);
            resolve(value);
        };

        const onCancel = () => close(false);
        const onOk = () => close(true);
        const onBackdrop = (event) => {
            if (event.target === modal) {
                close(false);
            }
        };

        btnCancel.addEventListener('click', onCancel);
        btnOk.addEventListener('click', onOk);
        modal.addEventListener('click', onBackdrop);
    });
}

function showToastEncuestas(message) {
    const toast = document.getElementById('toastEncuestas');
    if (!toast) {
        return;
    }
    toast.textContent = message;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 1800);
}

initEncuestasPage();
