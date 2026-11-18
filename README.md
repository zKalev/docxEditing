# ONLYOFFICE .docx editor — PoC

This project opens, edits and saves `.docx` files in the browser using ONLYOFFICE Document Server. It has three parts:

| Part | Tech | Default URL |
|---|---|---|
| `backend/` | Spring Boot 4.1 (Maven, Java 21) | http://localhost:8080 |
| `frontend/` | Vue 3 + Vite 4 + Vue Router, `@onlyoffice/document-editor-vue` | http://localhost:5173 |
| Document Server | Docker image `onlyoffice/documentserver` | http://localhost:80 |

## How it works

```
Browser (Vue)                      Spring Boot                         Document Server (container)
 │ GET /api/documents  ───────────▶ list storage/*.docx
 │ GET /api/documents/{id}/config ▶ build config + sign JWT
 │ load {DS}/web-apps/.../api.js  ─────────────────────────────────────▶
 │                                  ◀── GET /api/documents/{id}/file ──── downloads the file
 │  (user edits; changes sync to Document Server)
 │                                  ◀── POST /api/documents/track?id= ─── status 2 (all editors closed, ~10 s)
 │                                      download edited file from "url"     or 6 (Ctrl+S / force save)
 │                                      and overwrite storage/{id}
```

There are two kinds of address to keep apart:

- **What the browser uses:** `onlyoffice.document-server-url` (where `api.js` loads from) and `VITE_API_BASE_URL` (the backend).
- **What the container uses:** `onlyoffice.backend-url-for-document-server`. Document Server uses this address to download the file and post callbacks. Inside the container, `localhost` means the container itself, so this must be `host.docker.internal` (Docker) or `host.containers.internal` (Podman).

The document `key` is a hash of the file name, size and modification time. It changes after every save, so Document Server never reopens a stale cached copy.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/documents` | List `.docx` files (`id` = file name) |
| GET | `/api/documents/{id}/config?userId=&userName=` | `{ documentServerUrl, config }`, where `config` carries a JWT `token` |
| GET | `/api/documents/{id}/file` | Raw file; Document Server downloads it from here |
| POST | `/api/documents/track?id={id}` | Document Server callback. Saves on status 2 and 6, verifies the JWT, and answers `{"error":0}` |

## Running it

### 1. Start ONLYOFFICE Document Server

The basic `docker run -i -t -d -p 80:80 onlyoffice/documentserver` is not enough for current versions (7.2+). This command adds what's missing:

```bash
docker run -i -t -d -p 80:80 --name onlyoffice \
  -e JWT_SECRET=poc-onlyoffice-jwt-secret-change-me-0123456789 \
  -e ALLOW_PRIVATE_IP_ADDRESS=true \
  --add-host=host.docker.internal:host-gateway \
  onlyoffice/documentserver
```

- `JWT_SECRET`: JWT is on by default and the secret is random. It must equal `onlyoffice.jwt-secret` in `backend/src/main/resources/application.properties`.
- `ALLOW_PRIVATE_IP_ADDRESS=true`: without it, Document Server refuses to download from the host's private IP and the editor shows "Download failed".
- `--add-host=host.docker.internal:host-gateway`: lets the container reach the backend on the host (needed on Linux; Docker Desktop has it built in).

The first start takes 1–2 minutes. It's ready when http://localhost/healthcheck returns `true`.

**Rootless Podman** can't publish port 80. Publish another port, but not 8080, because the backend uses it:

```bash
podman run -i -t -d -p 8081:80 --name onlyoffice \
  -e JWT_SECRET=poc-onlyoffice-jwt-secret-change-me-0123456789 \
  -e ALLOW_PRIVATE_IP_ADDRESS=true \
  onlyoffice/documentserver
```

Then start the backend with `ONLYOFFICE_URL=http://localhost:8081`. Podman resolves `host.docker.internal` and `host.containers.internal` by itself.

### 2. Start the backend

```bash
cd backend
mvn spring-boot:run          # http://localhost:8080
mvn test                     # API tests (list, signed config, callback save, JWT rejection, path traversal)
mvn test -Dtest=DocumentApiTest#forceSaveCallbackOverwritesFileAndChangesKey   # a single test
```

Files are stored in `backend/storage/`, and a `sample.docx` is created there if the folder is empty. To add documents, drop more `.docx` files into it.

You can override any setting with an environment variable: `ONLYOFFICE_URL`, `BACKEND_URL_FOR_DS`, `ONLYOFFICE_JWT_SECRET`, `APP_STORAGE_DIR`, `APP_FRONTEND_ORIGIN`.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173
```

Vite 4 is pinned so the project runs on Node 16. On Node 18+ you can upgrade to Vite 5+ and `@vitejs/plugin-vue` 5+.

### 4. Use it

1. Open http://localhost:5173 and click **Edit** next to a document.
2. Edit the document. The status bar shows *Editing…*, then *Changes synced to Document Server*.
3. Press **Ctrl+S**. This sends callback status 6 and the file is written right away. Or close the editor (**Close** button or **← Documents**): about 10 s after the last editor closes, status 2 arrives and the file is written then.
4. When the backend has written the file, the status bar shows *Saved to storage at …*. The view polls the file's modification time to detect this.

Opening the same document in two tabs gives two guest users editing together.

## Troubleshooting

| Symptom | Cause |
|---|---|
| "Could not load the editor (-2)" | Document Server isn't running, or `ONLYOFFICE_URL` is wrong |
| "The document security token is not correctly formed" | The `JWT_SECRET` in the container and `onlyoffice.jwt-secret` differ |
| "Download failed" | The container can't reach `BACKEND_URL_FOR_DS`, or it was started without `ALLOW_PRIVATE_IP_ADDRESS=true` |
| Edits are never saved | Callbacks can't reach the backend. Check the backend log for `Callback for …` lines |
