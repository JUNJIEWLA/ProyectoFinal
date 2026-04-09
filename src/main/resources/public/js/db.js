const OfflineDB = (() => {
    let db;

    function init() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open('EncuestasDB', 1);

            request.onupgradeneeded = (event) => {
                const upgradeDb = event.target.result;
                if (!upgradeDb.objectStoreNames.contains('formularios')) {
                    const store = upgradeDb.createObjectStore('formularios', { keyPath: 'id' });
                    store.createIndex('sincronizado', 'sincronizado', { unique: false });
                }
            };

            request.onsuccess = (event) => {
                db = event.target.result;
                resolve();
            };
            request.onerror = () => reject(request.error);
        });
    }

    function saveFormulario(formulario) {
        return withStore('readwrite', (store) => store.put(formulario));
    }

    function deleteFormulario(id) {
        return withStore('readwrite', (store) => store.delete(id));
    }

    function getAll() {
        return withStore('readonly', (store) => store.getAll());
    }

    async function getPending() {
        const all = await getAll();
        return all.filter((f) => !f.sincronizado);
    }

    async function markSynced(ids) {
        const all = await getAll();
        const syncSet = new Set(ids);
        const pendingWrites = all
            .filter((item) => syncSet.has(item.id))
            .map((item) => saveFormulario({ ...item, sincronizado: true }));
        return Promise.all(pendingWrites);
    }

    function withStore(mode, operation) {
        return new Promise((resolve, reject) => {
            const tx = db.transaction('formularios', mode);
            const store = tx.objectStore('formularios');
            const request = operation(store);
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }

    return { init, saveFormulario, deleteFormulario, getAll, getPending, markSynced };
})();

