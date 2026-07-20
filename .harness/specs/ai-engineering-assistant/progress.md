# Progress: Phase 1 Foundation

## 2026-07-20

- Implemented backend Spring Boot Phase 1 scaffold: auth, JWT access tokens, persisted refresh tokens, profile update, password change, workspace CRUD, repository ZIP upload metadata/versioning/status, global API errors, and H2-backed tests.
- Implemented ZIP upload safety checks for type, compressed size, expanded size, per-file size, file count, path traversal, directory depth, nested archive rejection, supported source file filtering, and compression ratio.
- Implemented frontend React/Vite Phase 1 scaffold for login/register, workspace creation/list selection, repository upload, and upload status display.
- Added local run notes and Phase 1 API surface to `README.md`.

## Verification Evidence

- `cd backend; mvn clean test` -> PASS, 34 source files compiled, 4 tests, 0 failures, 0 errors, 0 skipped.
- `cd frontend; npm.cmd run build` -> PASS, TypeScript no-emit check and Vite production build succeeded.
- `cd frontend; npm.cmd test -- --run` -> PASS, 1 test file, 1 test.
- `hs-reviewer` initial pass found ZIP/JWT/storage/polling issues; fixes were applied and focused final pass reported: "No remaining findings."
- `hs-shipper` prepared ship-readiness and drafted a conventional commit message; no commit or push was performed.
- Dev smoke servers started: backend `mvn spring-boot:run -Dspring-boot.run.profiles=dev` on `localhost:8080` returned `403` for protected `/api/v1/workspaces` without auth; frontend `npm.cmd run dev` on `http://127.0.0.1:5173/` returned `200`.

## Known Limitations

- `PROJECT_CONTEXT.md` is referenced by `README.md` but does not exist in the workspace.
- `git status --short` fails because this workspace is not currently recognized as a Git repository, despite a `.git` path being present.
- Frontend token storage is MVP-grade localStorage. Backend refresh rotation/revocation is implemented, but browser refresh-token hardening with HttpOnly cookies remains a production hardening item.
- Phase 1 intentionally stops at repository upload metadata and `INDEXING` boundary; parser, chunking, embeddings, vector storage, RAG, and AI features belong to later roadmap phases.
- `npm install` reported 5 dependency audit findings in transitive packages; no automatic `npm audit fix --force` was run because it can introduce breaking changes.
