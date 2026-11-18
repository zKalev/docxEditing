# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Proof of concept: browser editing of `.docx` files via ONLYOFFICE Document Server. `backend/` is Spring Boot 4.1 (Maven, Java 21); `frontend/` is Vue 3 + Vite. See README.md for the run guide and troubleshooting.

## Commands

```bash
# backend (from backend/)
mvn spring-boot:run                              # :8080
mvn test                                         # all tests
mvn test -Dtest=DocumentApiTest#methodName       # single test
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8090

# frontend (from frontend/)
npm install && npm run dev                       # :5173 (strictPort — CORS allows exactly this origin)
npm run build
```

## Environment constraints

- Local Node is v16, so the frontend pins Vite 4.5 / `@vitejs/plugin-vue` 4.x. Don't bump to Vite 5+ without upgrading Node.
- Spring Boot 4 uses **Jackson 3** (`tools.jackson.*` packages; inject `JsonMapper`). Annotations are still `com.fasterxml.jackson.annotation.*`. jjwt-jackson pulls Jackson 2 separately for its own (de)serialization.
- The developer runs ONLYOFFICE with rootless **Podman**, and an existing container may already publish host port 8080, which clashes with the backend. Rootless Podman can't bind port 80.

## Architecture

Three parties, two address spaces. The browser reaches the backend (`VITE_API_BASE_URL`) and Document Server (`onlyoffice.document-server-url`). The Document Server *container* reaches the backend through `onlyoffice.backend-url-for-document-server` (`host.docker.internal` / `host.containers.internal`, never `localhost`). That URL is baked into both `document.url` and `editorConfig.callbackUrl` in the generated config.

Flow: `EditorConfigService` builds the `EditorConfig` record → converts it to a Map with Jackson → signs it with `JwtService` (HS256 is forced; jjwt would pick HS384+ for long keys, and Document Server rejects that) → the frontend spreads `config` and adds `events` (events are not part of the signed payload). Document Server downloads from `/api/documents/{id}/file` and later POSTs to `/api/documents/track?id=`. `CallbackService.authenticate` verifies the token (from the body `token`, or the `Authorization` header whose claims wrap the data in `payload`) and acts on the **verified claims**, not the raw body. Status 2 or 6 → download `url` → `DocumentStorageService.overwrite` (temp file + atomic move).

Invariants:
- Document ids are bare file names in a flat storage dir; `DocumentStorageService.resolve` is the single path-traversal guard. Route all file access through it.
- The document `key` = hash(name, size, mtime), so it changes after every save. Anything that alters the file outside `overwrite` still gets a fresh key automatically.
- The callback must always answer HTTP 200 with `{"error":0|1}`. Non-zero tells Document Server the save failed.
- `jwt-secret` must match the container's `JWT_SECRET`. A blank secret disables JWT (only valid with `JWT_ENABLED=false`).

`DocumentApiTest` runs the real server on a random port and simulates Document Server: it signs a status-6 callback whose `url` points at the app's own `/file` endpoint for another document.
