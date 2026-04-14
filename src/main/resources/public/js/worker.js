self.onmessage = (event) => {
    if (event.data.type !== 'sync') {
        return;
    }

    const { wsUrl, restUrl, token, formularios } = event.data.payload;
    if (!formularios || formularios.length === 0) {
        self.postMessage({ status: 'empty' });
        return;
    }

    tryWebSocket(wsUrl, token, formularios, restUrl);
};

function tryWebSocket(wsUrl, token, formularios, restUrl) {
    try {
        const ws = new WebSocket(`${wsUrl}?token=${encodeURIComponent(token)}`);
        let settled = false;
        let serverAck = false;

        const finishWithRest = () => {
            if (settled) {
                return;
            }
            settled = true;
            tryRest(restUrl, token, formularios);
        };

        // Evita que el flujo quede colgado si Render cierra el socket sin responder.
        const socketTimeout = setTimeout(() => {
            try {
                ws.close();
            } catch {
                // Ignora errores al cerrar.
            }
            finishWithRest();
        }, 8000);

        ws.onopen = () => {
            ws.send(JSON.stringify(formularios));
        };

        ws.onmessage = () => {
            if (settled) {
                return;
            }
            settled = true;
            serverAck = true;
            clearTimeout(socketTimeout);
            self.postMessage({ status: 'ok', ids: formularios.map((f) => f.id) });
            ws.close();
        };

        ws.onerror = () => {
            clearTimeout(socketTimeout);
            try {
                ws.close();
            } catch {
                // Ignora errores al cerrar.
            }
            finishWithRest();
        };

        ws.onclose = () => {
            clearTimeout(socketTimeout);
            if (!serverAck) {
                finishWithRest();
            }
        };
    } catch (error) {
        tryRest(restUrl, token, formularios);
    }
}

function tryRest(restUrl, token, formularios) {
    fetch(restUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(formularios),
    })
        .then((resp) => {
            if (!resp.ok) {
                throw new Error('Error sincronizando por REST');
            }
            return resp.json();
        })
        .then(() => {
            self.postMessage({ status: 'ok', ids: formularios.map((f) => f.id) });
        })
        .catch((err) => {
            self.postMessage({ status: 'error', error: err.message });
        });
}

