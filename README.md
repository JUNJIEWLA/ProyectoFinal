# ProyectoFinalWeb - Encuestas Offline First

Aplicacion web de encuestas con backend Java (Javalin), almacenamiento local en IndexedDB y sincronizacion por REST/WebSocket.

## Stack
- Java 17 + Gradle
- Javalin
- gRPC + Protobuf
- MongoDB (Atlas o local)
- JWT (autenticacion)
- Frontend HTML5 + Bootstrap + Leaflet + IndexedDB + Web Worker + Service Worker

## Credenciales demo
- `admin@encuestas.local` / `admin123`
- `digitador@encuestas.local` / `digitador123`

## Ejecutar local
```powershell
cd "C:\Users\Willian\Documents\PUCMM\ProyectoWeb\ProyectoFinalWeb"
.\gradlew.bat run
```

Abre: `http://localhost:7000`

## Variables de entorno opcionales
- `JWT_SECRET` secreto para firmar JWT.
- `MONGO_URI` ejemplo `mongodb://localhost:27017` o cadena Atlas.
- `MONGO_DB` nombre de base de datos (default: `encuestas`).
- `PORT` puerto HTTP (default: `7000`).
- `GRPC_PORT` puerto gRPC (default: `50051`).

Si no defines `MONGO_URI`, el backend usa almacenamiento en memoria (util para pruebas rapidas).

## Probar build
```powershell
cd "C:\Users\Willian\Documents\PUCMM\ProyectoWeb\ProyectoFinalWeb"
.\gradlew.bat clean test
```

## Roles por endpoint
- `ADMIN` y `OPERADOR`: `GET/POST/PUT /api/formularios`, `POST /api/formularios/sync`, `/sync` (WebSocket).
- `ADMIN` solamente: `DELETE /api/formularios/{id}`.

## API REST (JWT)
Usa `Authorization: Bearer <token>` (se obtiene en `POST /api/login`).
- Listar formularios por usuario: `GET /api/formularios?usuario=<email|id>` o `GET /api/formularios/usuario/<email|id>`.
- Listar mis formularios: `GET /api/formularios/mine`.
- Crear formulario para el usuario autenticado: `POST /api/formularios/mine` (incluye `fotografia` en base64 si aplica).

## Docker
```powershell
cd "C:\Users\Willian\Documents\PUCMM\ProyectoWeb\ProyectoFinalWeb"
.\gradlew.bat clean jar
docker compose up --build
```

## gRPC
El contrato base esta en `src/main/proto/encuesta.proto` y el servidor gRPC corre junto al backend HTTP.
Operaciones:
- `ListarFormularios(UsuarioRequest)` filtra por usuario (`usuario_id` acepta `email` o `id`).
- `CrearFormulario(FormularioRequest)` soporta `fotografia` en base64.

