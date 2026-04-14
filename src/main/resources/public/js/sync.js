const SyncClient = (() => {
    let worker;

    function initWorker() {
        worker = new Worker('/js/worker.js');
        return worker;
    }

    function buildWsUrl() {
        const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${wsProtocol}//${location.host}/sync`;
    }

    async function syncPending() {
        const token = localStorage.getItem('token');
        if (!token) {
            throw new Error('Debes iniciar sesion');
        }

        const pending = await OfflineDB.getPending();
        if (pending.length === 0) {
            return { synced: 0 };
        }

        if (!worker) {
            initWorker();
        }

        return new Promise((resolve, reject) => {
            let settled = false;

            const finish = (fn) => {
                if (settled) {
                    return;
                }
                settled = true;
                clearTimeout(timeoutId);
                fn();
            };

            const timeoutId = setTimeout(() => {
                finish(() => reject(new Error('Tiempo de espera agotado al sincronizar.')));
            }, 15000);

            worker.onmessage = async (event) => {
                if (event.data.status === 'ok') {
                    finish(async () => {
                        await OfflineDB.markSynced(event.data.ids);
                        resolve({ synced: event.data.ids.length });
                    });
                    return;
                }
                if (event.data.status === 'empty') {
                    finish(() => resolve({ synced: 0 }));
                    return;
                }
                finish(() => reject(new Error(event.data.error || 'No se pudo sincronizar')));
            };

            worker.onerror = () => {
                finish(() => reject(new Error('El proceso de sincronizacion fallo en segundo plano.')));
            };

            worker.postMessage({
                type: 'sync',
                payload: {
                    wsUrl: buildWsUrl(),
                    restUrl: `${location.origin}/api/formularios/sync`,
                    token,
                    formularios: pending,
                },
            });
        });
    }

    return { syncPending };
})();

