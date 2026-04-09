const SyncClient = (() => {
    let worker;

    function initWorker() {
        worker = new Worker('/js/worker.js');
        return worker;
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
            worker.onmessage = async (event) => {
                if (event.data.status === 'ok') {
                    await OfflineDB.markSynced(event.data.ids);
                    resolve({ synced: event.data.ids.length });
                    return;
                }
                if (event.data.status === 'empty') {
                    resolve({ synced: 0 });
                    return;
                }
                reject(new Error(event.data.error || 'No se pudo sincronizar'));
            };

            worker.postMessage({
                type: 'sync',
                payload: {
                    wsUrl: `${location.origin.replace('http', 'ws')}/sync`,
                    restUrl: `${location.origin}/api/formularios/sync`,
                    token,
                    formularios: pending,
                },
            });
        });
    }

    return { syncPending };
})();

